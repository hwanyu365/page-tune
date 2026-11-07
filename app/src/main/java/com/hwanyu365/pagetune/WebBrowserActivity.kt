package com.hwanyu365.pagetune

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.view.accessibility.AccessibilityManager
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.updatePadding
import androidx.core.view.WindowInsetsCompat
import com.hwanyu365.pagetune.db.AppDatabase
import com.hwanyu365.pagetune.db.PluginAction
import com.hwanyu365.pagetune.databinding.ActivityWebBrowserBinding
import com.hwanyu365.pagetune.util.JsScripts
import com.hwanyu365.pagetune.util.TuneRuleCounts
import com.hwanyu365.pagetune.util.padForInsets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class WebBrowserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWebBrowserBinding
    private lateinit var db: AppDatabase
    private val mainScope = CoroutineScope(Dispatchers.Main)

    private var webPageId: Long = -1

    private var mGestureDetector: GestureDetector? = null
    private val mHitRect = Rect()
    private var originalUrlHost: String? = null

    private var tuneEnabled = true
    private var hasTune = false
    private var ruleCounts = TuneRuleCounts()
    private var pluginId: Long? = null
    private val handler = Handler(Looper.getMainLooper())
    private val hideToolbar = Runnable { binding.layoutQuickToolbar.visibility = View.GONE }
    private val hideStatus = Runnable { binding.tvTuneStatus.visibility = View.GONE }
    private val editLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) onTuneEdited()
    }

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
            Toast.makeText(this, R.string.browser_error_invalid_id, Toast.LENGTH_SHORT).show()
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
                if (tuneEnabled) {
                    applyColorActions()
                    applyHiddenActions()
                    applyScrollAction()
                }
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

        setupQuickMenu()
    }

    private fun loadWebPageData() {
        mainScope.launch {
            val data = withContext(Dispatchers.IO) {
                db.webPageDao().getWebPageById(webPageId)
            }
            if (data == null) {
                Toast.makeText(this@WebBrowserActivity, R.string.browser_error_not_found, Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }

            readTune(data.pluginId)
            try {
                originalUrlHost = java.net.URL(data.url).host
            } catch (e: Exception) {
                originalUrlHost = null
            }
            binding.wvBrowser.loadUrl(data.url)
            showTuneStatus()
        }
    }

    /** 연결된 Tune 의 규칙을 읽어 분류하고, 규칙에 의존하는 화면 요소를 갱신한다. */
    private suspend fun readTune(linkedPluginId: Long?) {
        val pluginWithActions = linkedPluginId?.let {
            withContext(Dispatchers.IO) { db.pluginDao().getPluginWithActions(it) }
        }
        pluginId = pluginWithActions?.plugin?.id
        hasTune = pluginWithActions != null
        classifyActions(pluginWithActions?.actions.orEmpty())
        ruleCounts = TuneRuleCounts.of(pluginWithActions?.actions.orEmpty())
        setupGestureControls()
        refreshTuneUi()
    }

    private fun refreshTuneUi() {
        val hasGnb = tuneEnabled && (gnbPrevAction != null || gnbNextAction != null)
        binding.layoutFloatingGnb.visibility = if (hasGnb) View.VISIBLE else View.GONE
        binding.btnGnbPrev.visibility = if (gnbPrevAction != null) View.VISIBLE else View.GONE
        binding.btnGnbNext.visibility = if (gnbNextAction != null) View.VISIBLE else View.GONE

        binding.btnQuickToggle.isEnabled = hasTune
        binding.btnQuickToggle.alpha = if (hasTune) 1f else 0.4f
        binding.btnQuickToggle.contentDescription =
            getString(if (tuneEnabled) R.string.quick_tune_off else R.string.quick_tune_on)
        binding.btnQuickToggle.setImageResource(if (tuneEnabled) R.drawable.ic_toggle_on else R.drawable.ic_toggle_off)
        binding.btnQuickEdit.contentDescription =
            getString(if (hasTune) R.string.quick_edit_tune else R.string.quick_create_tune)
        binding.btnQuickEdit.setImageResource(if (hasTune) R.drawable.ic_edit else R.drawable.ic_add)
    }

    private fun showTuneStatus() {
        val text = when {
            !hasTune -> return
            !tuneEnabled -> getString(R.string.tune_status_off)
            else -> {
                val parts = ruleCounts.nonZero().map { (kind, n) ->
                    getString(
                        when (kind) {
                            TuneRuleCounts.Kind.HIDE -> R.string.tune_kind_hide
                            TuneRuleCounts.Kind.COLOR -> R.string.tune_kind_color
                            TuneRuleCounts.Kind.CLICK -> R.string.tune_kind_click
                            TuneRuleCounts.Kind.START -> R.string.tune_kind_start
                        }, n
                    )
                }
                if (parts.isEmpty()) getString(R.string.tune_status_no_rules)
                else getString(R.string.tune_status_on, parts.joinToString(", "))
            }
        }
        binding.tvTuneStatus.text = text
        binding.tvTuneStatus.visibility = View.VISIBLE
        handler.removeCallbacks(hideStatus)
        handler.postDelayed(hideStatus, STATUS_MILLIS)
    }

    private fun setupQuickMenu() {
        // 상태바는 숨겨도 컷아웃·둥근 모서리 안쪽에 둔다.
        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutQuickOverlay) { v, insets ->
            val safe = insets.getInsets(
                WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.systemBars()
            )
            v.updatePadding(left = safe.left, top = safe.top, right = safe.right)
            insets
        }
        binding.btnQuickHandle.setOnClickListener {
            if (binding.layoutQuickToolbar.visibility == View.VISIBLE) {
                handler.removeCallbacks(hideToolbar)
                binding.layoutQuickToolbar.visibility = View.GONE
            } else {
                binding.layoutQuickToolbar.visibility = View.VISIBLE
                scheduleToolbarHide()
            }
        }
        binding.btnQuickBack.setOnClickListener {
            scheduleToolbarHide()
            if (binding.wvBrowser.canGoBack()) binding.wvBrowser.goBack() else finish()
        }
        binding.btnQuickRefresh.setOnClickListener {
            scheduleToolbarHide()
            binding.wvBrowser.reload()
        }
        binding.btnQuickShare.setOnClickListener {
            scheduleToolbarHide()
            val url = binding.wvBrowser.url ?: return@setOnClickListener
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
            startActivity(Intent.createChooser(send, getString(R.string.quick_share)))
        }
        binding.btnQuickToggle.setOnClickListener {
            scheduleToolbarHide()
            tuneEnabled = !tuneEnabled
            refreshTuneUi()
            showTuneStatus()
            binding.wvBrowser.reload()
        }
        binding.btnQuickEdit.setOnClickListener {
            scheduleToolbarHide()
            openTuneEdit()
        }
    }

    private fun scheduleToolbarHide() {
        val timeout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getSystemService(AccessibilityManager::class.java)
                .getRecommendedTimeoutMillis(TOOLBAR_MILLIS, AccessibilityManager.FLAG_CONTENT_CONTROLS)
        } else {
            TOOLBAR_MILLIS
        }
        handler.removeCallbacks(hideToolbar)
        handler.postDelayed(hideToolbar, timeout.toLong())
    }

    private fun openTuneEdit() {
        val intent = Intent(this, PluginEditActivity::class.java)
            .putExtra(PluginEditActivity.EXTRA_LOAD_URL, binding.wvBrowser.url)
        val current = pluginId
        if (current != null) intent.putExtra("plugin_id", current)
        else intent.putExtra(PluginEditActivity.EXTRA_LINK_WEBPAGE_ID, webPageId)
        editLauncher.launch(intent)
    }

    /** 저장하고 돌아오면 웹페이지·Tune 을 DB 에서 다시 읽어 켠 상태로 적용한다. */
    private fun onTuneEdited() {
        mainScope.launch {
            val data = withContext(Dispatchers.IO) { db.webPageDao().getWebPageById(webPageId) } ?: return@launch
            tuneEnabled = true
            readTune(data.pluginId)
            binding.wvBrowser.reload()
            showTuneStatus()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private companion object {
        const val TOOLBAR_MILLIS = 4000
        const val STATUS_MILLIS = 3000L
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
        val script = JsScripts.getMutationObserverHideScript(hiddenActions.map { it.targetSelector })
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
                if (!tuneEnabled) return false
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

}
