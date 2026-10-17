package com.hwanyu365.pagetune

import android.annotation.SuppressLint
import android.graphics.Rect
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsCompat
import com.hwanyu365.pagetune.db.AppDatabase
import com.hwanyu365.pagetune.db.PluginAction
import com.hwanyu365.pagetune.databinding.ActivityWebBrowserBinding
import com.hwanyu365.pagetune.util.JsScripts
import com.hwanyu365.pagetune.util.padForInsets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class WebBrowserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWebBrowserBinding
    private lateinit var db: AppDatabase
    private val mainScope = CoroutineScope(Dispatchers.Main)

    private var webPageId: Long = -1

    private var mGestureDetector: GestureDetector? = null
    private val mHitRect = Rect()
    private var originalUrlHost: String? = null

    // Cached actions for quick access
    private val hiddenActions = mutableListOf<PluginAction>()
    private val colorActions = mutableListOf<PluginAction>()
    private var scrollAction: PluginAction? = null
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

        binding = ActivityWebBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // 상태바는 숨기고 노치까지 쓰는 화면이라 내비게이션 바만 피한다.
        binding.root.padForInsets(WindowInsetsCompat.Type.navigationBars())

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
        com.hwanyu365.pagetune.util.WebViewConfig.applyCommon(binding.wvBrowser)
        binding.wvBrowser.settings.apply {
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
        }

        binding.wvBrowser.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Cloudflare 통과 쿠키(cf_clearance)는 앱이 죽으면 날아간다. 페이지마다 디스크로 내려 재방문 시 챌린지를 건너뛴다.
                android.webkit.CookieManager.getInstance().flush()
                binding.pbBrowserLoading.visibility = View.GONE
                applyColorActions()
                applyHiddenActions()
                applyScrollAction()
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

        binding.wvBrowser.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                if (newProgress < 100) {
                    binding.pbBrowserLoading.visibility = View.VISIBLE
                } else {
                    binding.pbBrowserLoading.visibility = View.GONE
                }
            }
        }
    }

    private fun setupListeners() {
        binding.btnGnbPrev.setOnClickListener {
            triggerClickAction(gnbPrevAction)
        }

        binding.btnGnbNext.setOnClickListener {
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
            binding.wvBrowser.loadUrl(data.url)
            
            setupGestureControls()
            setupGnbControls()
        }
    }

    private fun classifyActions(actions: List<PluginAction>) {
        hiddenActions.clear()
        colorActions.clear()
        scrollAction = null
        gestureTopAction = null
        gestureBottomAction = null
        gnbPrevAction = null
        gnbNextAction = null

        actions.forEach { action ->
            if (action.actionType == "hidden") {
                hiddenActions.add(action)
            } else if (action.actionType == "color") {
                colorActions.add(action)
            } else if (action.actionType == "scroll") {
                scrollAction = action
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

    private fun applyColorActions() {
        if (colorActions.isEmpty()) return
        binding.wvBrowser.evaluateJavascript(JsScripts.getColorScriptFor(colorActions), null)
    }

    private fun applyScrollAction() {
        val action = scrollAction ?: return
        val y = runCatching { JSONObject(action.targetSelector).getInt("y") }.getOrNull() ?: return
        binding.wvBrowser.evaluateJavascript(JsScripts.getScrollScript(y), null)
    }

    private fun applyHiddenActions() {
        if (hiddenActions.isEmpty()) return
        
        // Build JSON array of all selectors details to hide
        val jsonArray = JSONArray()
        hiddenActions.forEach {
            jsonArray.put(it.targetSelector)
        }
        
        val script = JsScripts.getMutationObserverHideScript(jsonArray.toString())
        binding.wvBrowser.evaluateJavascript(script, null)
    }

    private fun triggerClickAction(action: PluginAction?) {
        if (action == null) return
        val script = JsScripts.getClickEventScript(action.targetSelector)
        binding.wvBrowser.evaluateJavascript(script, null)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupGestureControls() {
        val hasGesture = (gestureTopAction != null || gestureBottomAction != null)
        if (!hasGesture) {
            binding.wvBrowser.setOnTouchListener(null)
            return
        }

        // Determine area percent from active gesture actions
        val areaPercentVal = gestureTopAction?.gestureAreaPercent 
            ?: gestureBottomAction?.gestureAreaPercent 
            ?: 40

        mGestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                binding.wvBrowser.getHitRect(mHitRect)
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

        binding.wvBrowser.setOnTouchListener { _, motionEvent ->
            mGestureDetector?.onTouchEvent(motionEvent) ?: false
        }
    }

    private fun setupGnbControls() {
        val hasGnb = (gnbPrevAction != null || gnbNextAction != null)
        if (hasGnb) {
            binding.layoutFloatingGnb.visibility = View.VISIBLE
            
            // Adjust buttons visibility
            binding.btnGnbPrev.visibility = if (gnbPrevAction != null) View.VISIBLE else View.GONE
            binding.btnGnbNext.visibility = if (gnbNextAction != null) View.VISIBLE else View.GONE
        } else {
            binding.layoutFloatingGnb.visibility = View.GONE
        }
    }

}
