package com.hwanyu365.pagetune

import android.annotation.SuppressLint
import android.graphics.Rect
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.hwanyu365.pagetune.db.AppDatabase
import com.hwanyu365.pagetune.db.PluginAction
import com.hwanyu365.pagetune.db.WebPageWithPlugin
import com.hwanyu365.pagetune.util.JsScripts
import kotlinx.android.synthetic.main.activity_web_browser.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

class WebBrowserActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private val mainScope = CoroutineScope(Dispatchers.Main)
    
    private var webPageId: Long = -1
    private var webPageData: WebPageWithPlugin? = null
    
    private var mGestureDetector: GestureDetector? = null
    private val mHitRect = Rect()
    private var originalUrlHost: String? = null

    // Cached actions for quick access
    private val hiddenActions = mutableListOf<PluginAction>()
    private var gestureTopAction: PluginAction? = null
    private var gestureBottomAction: PluginAction? = null
    private var gnbPrevAction: PluginAction? = null
    private var gnbNextAction: PluginAction? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Extend layout into notch / status bar area (Android P+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = 
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        
        // Completely hide status bar and expand WebView layout to the screen edges
        window.decorView.systemUiVisibility = (android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)

        setContentView(R.layout.activity_web_browser)

        db = AppDatabase.getDatabase(this)
        webPageId = intent.getLongExtra("webpage_id", -1)

        if (webPageId == -1L) {
            Toast.makeText(this, "Invalid web page ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupWebView()
        setupListeners()
        loadWebPageData()
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun setupWebView() {
        val webSettings: WebSettings = wv_browser.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.databaseEnabled = true
        webSettings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
        webSettings.javaScriptCanOpenWindowsAutomatically = true
        webSettings.mediaPlaybackRequiresUserGesture = false
        webSettings.builtInZoomControls = false
        webSettings.displayZoomControls = false
        webSettings.useWideViewPort = true
        webSettings.loadWithOverviewMode = true

        // Anti-blocking: set User-Agent to standard mobile Chrome (Latest Android 13 version)
        webSettings.userAgentString = "Mozilla/5.0 (Linux; Android 13; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        
        // Mixed Content allow
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            webSettings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        // Enable cookies
        val cookieManager = android.webkit.CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(wv_browser, true)
        }

        wv_browser.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                // Bypass navigator.webdriver detection used by Cloudflare
                view?.evaluateJavascript("Object.defineProperty(navigator, 'webdriver', {get: () => undefined})", null)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                pb_browser_loading.visibility = View.GONE
                applyHiddenActions()
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                url?.let { newUrl ->
                    try {
                        val newHost = java.net.URL(newUrl).host
                        // 1. Cloudflare challenge domain protection
                        if (newHost.contains("cloudflare") || newHost.contains("challenges")) {
                            return
                        }
                        // 2. Prevent updating DB with external redirect domains
                        if (originalUrlHost != null && newHost != originalUrlHost) {
                            return
                        }
                    } catch (e: Exception) {
                        return
                    }

                    // Update URL in DB when navigation occurs
                    mainScope.launch {
                        withContext(Dispatchers.IO) {
                            db.webPageDao().updateUrl(webPageId, newUrl)
                        }
                    }
                }
            }
        }

        wv_browser.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                if (newProgress < 100) {
                    pb_browser_loading.visibility = View.VISIBLE
                } else {
                    pb_browser_loading.visibility = View.GONE
                }
            }
        }
    }

    private fun setupListeners() {
        btn_gnb_prev.setOnClickListener {
            triggerClickAction(gnbPrevAction)
        }

        btn_gnb_next.setOnClickListener {
            triggerClickAction(gnbNextAction)
        }
    }

    private fun loadWebPageData() {
        mainScope.launch {
            // Load page with plugin relations
            val data = withContext(Dispatchers.IO) {
                db.webPageDao().getWebPageById(webPageId)
            }
            if (data == null) {
                Toast.makeText(this@WebBrowserActivity, "Web page not found", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }

            // Get plugin actions if plugin is mapped
            val pluginId = data.pluginId
            if (pluginId != null) {
                val pluginWithActions = withContext(Dispatchers.IO) {
                    db.pluginDao().getPluginWithActions(pluginId)
                }
                
                if (pluginWithActions != null) {
                    classifyActions(pluginWithActions.actions)
                }
            }

            try {
                originalUrlHost = java.net.URL(data.url).host
            } catch (e: Exception) {
                originalUrlHost = null
            }
            wv_browser.loadUrl(data.url)
            
            setupGestureControls()
            setupGnbControls()
        }
    }

    private fun classifyActions(actions: List<PluginAction>) {
        hiddenActions.clear()
        gestureTopAction = null
        gestureBottomAction = null
        gnbPrevAction = null
        gnbNextAction = null

        actions.forEach { action ->
            if (action.actionType == "hidden") {
                hiddenActions.add(action)
            } else if (action.actionType == "click") {
                when (action.triggerType) {
                    "gesture_top" -> gestureTopAction = action
                    "gesture_bottom" -> gestureBottomAction = action
                    "gnb_prev" -> gnbPrevAction = action
                    "gnb_next" -> gnbNextAction = action
                }
            }
        }
    }

    private fun applyHiddenActions() {
        if (hiddenActions.isEmpty()) return
        
        // Build JSON array of all selectors details to hide
        val jsonArray = JSONArray()
        hiddenActions.forEach {
            jsonArray.put(it.targetSelector)
        }
        
        val script = JsScripts.getMutationObserverHideScript(jsonArray.toString())
        wv_browser.evaluateJavascript(script, null)
    }

    private fun triggerClickAction(action: PluginAction?) {
        if (action == null) return
        val script = JsScripts.getClickEventScript(action.targetSelector)
        wv_browser.evaluateJavascript(script, null)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupGestureControls() {
        val hasGesture = (gestureTopAction != null || gestureBottomAction != null)
        if (!hasGesture) {
            wv_browser.setOnTouchListener(null)
            return
        }

        // Determine area percent from active gesture actions
        val areaPercentVal = gestureTopAction?.gestureAreaPercent 
            ?: gestureBottomAction?.gestureAreaPercent 
            ?: 40

        mGestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                wv_browser.getHitRect(mHitRect)
                val areaPercent = areaPercentVal / 100f
                val prevHitY = mHitRect.bottom * areaPercent
                val nextHitY = mHitRect.bottom - prevHitY
                val touchY = e.y.toInt()

                if (touchY < prevHitY && gestureTopAction != null) {
                    triggerClickAction(gestureTopAction)
                    return true
                } else if (touchY > nextHitY && gestureBottomAction != null) {
                    triggerClickAction(gestureBottomAction)
                    return true
                }
                return super.onDoubleTap(e)
            }
        })

        wv_browser.setOnTouchListener { _, motionEvent ->
            mGestureDetector?.onTouchEvent(motionEvent) ?: false
        }
    }

    private fun setupGnbControls() {
        val hasGnb = (gnbPrevAction != null || gnbNextAction != null)
        if (hasGnb) {
            layout_floating_gnb.visibility = View.VISIBLE
            
            // Adjust buttons visibility
            btn_gnb_prev.visibility = if (gnbPrevAction != null) View.VISIBLE else View.GONE
            btn_gnb_next.visibility = if (gnbNextAction != null) View.VISIBLE else View.GONE
        } else {
            layout_floating_gnb.visibility = View.GONE
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
    }

}
