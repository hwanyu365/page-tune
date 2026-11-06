package com.hwanyu365.pagetune

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.hwanyu365.pagetune.db.AppDatabase
import com.hwanyu365.pagetune.db.Plugin
import com.hwanyu365.pagetune.db.PluginAction
import com.hwanyu365.pagetune.databinding.ActivityPluginEditBinding
import com.hwanyu365.pagetune.save.RoomTuneStore
import com.hwanyu365.pagetune.save.executeSave
import com.hwanyu365.pagetune.save.planSave
import com.hwanyu365.pagetune.ui.view.ColorPickerDialog
import com.hwanyu365.pagetune.util.EditSnapshot
import com.hwanyu365.pagetune.util.JsScripts
import com.hwanyu365.pagetune.util.SheetHeight
import com.hwanyu365.pagetune.util.TriggerOptions
import com.hwanyu365.pagetune.util.UnstableId
import com.hwanyu365.pagetune.util.enableDarkEdgeToEdge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class PluginEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPluginEditBinding
    private lateinit var db: AppDatabase
    private val mainScope = CoroutineScope(Dispatchers.Main)

    private var pluginId: Long = -1
    private var loadUrl: String? = null
    private var linkWebPageId: Long? = null
    private val tempActions = mutableListOf<PluginAction>()
    private var selectedElementJson: String? = null
    
    private var isInspectMode = false

    private lateinit var sheet: BottomSheetBehavior<View>
    private var initialSnapshot = EditSnapshot.of("", "", emptyList())

    // "color" 액션의 입력값. picker 만 이 값을 만들므로 항상 "#RRGGBB" 이거나 null 이다.
    private var colorBg: String? = null
    private var colorText: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableDarkEdgeToEdge()
        binding = ActivityPluginEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Programmatically exclude all views in this Activity from Android Autofill Service
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            window.decorView.importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }

        db = AppDatabase.getDatabase(this)
        pluginId = intent.getLongExtra("plugin_id", -1)
        loadUrl = intent.getStringExtra(EXTRA_LOAD_URL)?.takeIf { it.isNotBlank() }
        linkWebPageId = intent.getLongExtra(EXTRA_LINK_WEBPAGE_ID, -1).takeIf { it != -1L }

        setupWebView()
        setupListeners()
        setupBottomSheet()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = requestExit()
        })

        if (pluginId != -1L) {
            loadPluginData()
        } else {
            loadUrl?.let {
                binding.etInpTaddr.setText(it)
                binding.wvInspector.loadUrl(it)
            }
            initialSnapshot = currentSnapshot()
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    private fun setupWebView() {
        com.hwanyu365.pagetune.util.WebViewConfig.applyCommon(binding.wvInspector)

        binding.wvInspector.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Cloudflare 통과 쿠키(cf_clearance)는 앱이 죽으면 날아간다. 페이지마다 디스크로 내려 재방문 시 챌린지를 건너뛴다.
                android.webkit.CookieManager.getInstance().flush()
                // If inspect mode is on when page finishes loading, inject picker script again
                if (isInspectMode) {
                    injectPickerScript()
                }
                // Automatically re-apply all current hidden actions
                applyCurrentActions()
            }
        }
        binding.wvInspector.webChromeClient = WebChromeClient()
        binding.wvInspector.addJavascriptInterface(AndroidInspectorBridge(), "AndroidBridge")
    }

    private fun setupListeners() {
        binding.btnLoadUrl.setOnClickListener {
            var url = binding.etInpTaddr.text.toString().trim()
            if (url.isNotEmpty()) {
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    url = "https://$url"
                    binding.etInpTaddr.setText(url)
                }
                // If the target URL is already the current URL, force reload
                if (binding.wvInspector.url == url) {
                    binding.wvInspector.reload()
                } else {
                    binding.wvInspector.loadUrl(url)
                }
            }
        }

        binding.btnToggleInspect.setOnCheckedChangeListener { _, isChecked ->
            isInspectMode = isChecked
            if (isChecked) {
                binding.tvInspectorStatus.visibility = View.VISIBLE
                binding.wvInspector.evaluateJavascript("if (typeof window.webPluginInspectEnabled !== 'undefined') { window.webPluginInspectEnabled = true; 'enabled'; } else { 'inject'; }") { result ->
                    if (result == "\"inject\"") {
                        injectPickerScript()
                    }
                }
            } else {
                binding.tvInspectorStatus.visibility = View.GONE
                // Disable click listener in JS and clear highlights
                binding.wvInspector.evaluateJavascript("window.webPluginInspectEnabled = false; window.webPluginClearHighlight();", null)
                hideActionConfigPanel()
            }
        }

        binding.btnSelectParent.setOnClickListener {
            binding.wvInspector.evaluateJavascript("window.webPluginSelectParent();", null)
        }

        binding.btnSelectChild.setOnClickListener {
            binding.wvInspector.evaluateJavascript("window.webPluginSelectChild();", null)
        }

        binding.rgActionType.setOnCheckedChangeListener { _, checkedId ->
            binding.layoutClickTriggerSetup.visibility =
                if (checkedId == R.id.rb_action_click) View.VISIBLE else View.GONE
            binding.layoutColorSetup.visibility =
                if (checkedId == R.id.rb_action_color) View.VISIBLE else View.GONE
        }

        binding.spTriggerType.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, TriggerOptions.all.map { getString(it.labelRes) }
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        refreshColorButtons()

        binding.btnColorBg.setOnClickListener {
            ColorPickerDialog.show(this, getString(R.string.edit_color_bg_title), colorBg) { picked ->
                colorBg = picked
                refreshColorButtons()
            }
        }

        binding.btnColorText.setOnClickListener {
            ColorPickerDialog.show(this, getString(R.string.edit_color_text_title), colorText) { picked ->
                colorText = picked
                refreshColorButtons()
            }
        }

        binding.btnSetScroll.setOnClickListener {
            addScrollAction()
        }

        binding.btnAddAction.setOnClickListener {
            addAction()
        }

        binding.btnPluginSave.setOnClickListener {
            savePlugin()
        }

        binding.btnPluginCancel.setOnClickListener {
            requestExit()
        }
    }

    private fun currentSnapshot() = EditSnapshot.of(
        binding.etInpPname.text.toString(), binding.etInpTaddr.text.toString(), tempActions
    )

    private fun requestExit() {
        if (!currentSnapshot().differsFrom(initialSnapshot)) {
            finish()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.edit_leave_title)
            .setMessage(R.string.edit_leave_message)
            .setPositiveButton(R.string.edit_leave_discard) { _, _ -> finish() }
            .setNegativeButton(R.string.edit_leave_keep, null)
            .show()
    }

    private fun updateSheetMaxHeight() {
        val max = SheetHeight.max(binding.root.height, binding.layoutEditTop.bottom, sheet.peekHeight)
        if (sheet.maxHeight != max) sheet.maxHeight = max
    }

    // 시트 안과 WebView 의 스크롤이 시트 드래그로 바뀌지 않도록 기본 드래그를 끄고 핸들 제스처만 상태를 바꾼다.
    @SuppressLint("ClickableViewAccessibility")
    private fun setupBottomSheet() {
        val container = binding.layoutBottomConfigContainer
        sheet = BottomSheetBehavior.from(container)
        sheet.isDraggable = false
        sheet.state = BottomSheetBehavior.STATE_COLLAPSED

        // BottomSheetBehavior 는 부모 padding 을 반영하지 않으므로 시스템 바 inset 을 루트가 아닌 각 자식에 나눠 준다.
        val density = resources.displayMetrics.density
        val sheetSide = (12 * density).toInt()
        val sheetBottom = (8 * density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            binding.layoutEditMain.updatePadding(left = bars.left, top = bars.top, right = bars.right)
            container.updatePadding(left = sheetSide + bars.left, right = sheetSide + bars.right, bottom = sheetBottom + bars.bottom)
            insets
        }

        binding.layoutSheetPeekEnd.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, _ ->
            val peek = bottom + container.paddingBottom
            if (sheet.peekHeight != peek) {
                sheet.peekHeight = peek
                binding.layoutEditMain.updatePadding(bottom = peek)
                updateSheetMaxHeight()
            }
        }
        binding.root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateSheetMaxHeight() }
        binding.layoutEditTop.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateSheetMaxHeight() }

        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downY = 0f
        binding.sheetHandle.setOnClickListener {
            sheet.state = if (sheet.state == BottomSheetBehavior.STATE_EXPANDED)
                BottomSheetBehavior.STATE_COLLAPSED else BottomSheetBehavior.STATE_EXPANDED
        }
        binding.sheetHandle.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> downY = e.rawY
                MotionEvent.ACTION_UP -> {
                    val dy = e.rawY - downY
                    when {
                        dy > slop -> sheet.state = BottomSheetBehavior.STATE_COLLAPSED
                        dy < -slop -> sheet.state = BottomSheetBehavior.STATE_EXPANDED
                        else -> v.performClick()
                    }
                }
            }
            true
        }
    }

    private fun loadPluginData() {
        mainScope.launch {
            val pluginWithActions = withContext(Dispatchers.IO) {
                db.pluginDao().getPluginWithActions(pluginId)
            }
            if (pluginWithActions != null) {
                binding.etInpPname.setText(pluginWithActions.plugin.name)
                pluginWithActions.plugin.testUrl?.let { binding.etInpTaddr.setText(it) }
                (loadUrl ?: pluginWithActions.plugin.testUrl)?.let { binding.wvInspector.loadUrl(it) }
                tempActions.addAll(pluginWithActions.actions)
                refreshActionsListUI()
                initialSnapshot = currentSnapshot()
            }
        }
    }

    private fun injectPickerScript() {
        binding.wvInspector.evaluateJavascript(JsScripts.ELEMENT_PICKER_SCRIPT, null)
    }

    private fun onElementSelectedFromJs(detailsJson: String, matchCount: Int) {
        selectedElementJson = detailsJson
        try {
            val json = JSONObject(detailsJson)
            val selector = json.optString("selector")
            val textContent = json.optString("textContent")
            val displayText = if (textContent.isNotEmpty()) {
                getString(R.string.edit_selected_with_text, textContent, selector)
            } else {
                getString(R.string.edit_selected, selector)
            }
            val matchText = getString(
                when (matchCount) {
                    1 -> R.string.edit_match_count
                    0 -> R.string.edit_match_none
                    else -> R.string.edit_match_many
                },
                matchCount
            )
            binding.tvSelectedSelector.text = listOf(matchText, displayText).joinToString("\n")
            binding.layoutTraversalActions.visibility = View.VISIBLE
            binding.layoutActionConfig.visibility = View.VISIBLE
            sheet.state = BottomSheetBehavior.STATE_EXPANDED
        } catch (e: Exception) {
            binding.tvSelectedSelector.setText(R.string.edit_selected_error)
        }
    }

    private fun hideActionConfigPanel() {
        binding.layoutTraversalActions.visibility = View.GONE
        binding.layoutActionConfig.visibility = View.GONE
        binding.tvSelectedSelector.setText(R.string.edit_selected_none)
        selectedElementJson = null
        colorBg = null
        colorText = null
        refreshColorButtons()
    }

    private fun refreshColorButtons() {
        binding.btnColorBg.text = getString(R.string.edit_color_bg, colorBg ?: getString(R.string.edit_color_none))
        binding.btnColorText.text = getString(R.string.edit_color_text, colorText ?: getString(R.string.edit_color_none))
    }

    private fun applyCurrentActions() {
        // 색상은 CSS 규칙이라 hidden 유무와 무관하게 항상 갱신한다. 액션이 비면 규칙도 비워져 자동으로 풀린다.
        binding.wvInspector.evaluateJavascript(
            JsScripts.getColorScriptFor(tempActions.filter { it.actionType == "color" }), null
        )

        val hiddenActions = tempActions.filter { it.actionType == "hidden" }
        if (hiddenActions.isEmpty()) {
            // Disconnect old MutationObserver if no hidden actions
            binding.wvInspector.evaluateJavascript("(function() { if (window.webPluginObserver) { window.webPluginObserver.disconnect(); } })();", null)
            return
        }
        val script = JsScripts.getMutationObserverHideScript(hiddenActions.map { it.targetSelector })
        binding.wvInspector.evaluateJavascript(script, null)
    }

    /** 선택한 색상을 CSS 선언부로 만든다. 둘 다 비면 null 을 돌려 액션 추가를 막는다. */
    private fun buildStyleCss(): String? {
        val declarations = listOfNotNull(
            colorBg?.let { "background-color:$it" },
            colorText?.let { "color:$it" }
        )
        if (declarations.isEmpty()) {
            Toast.makeText(this, R.string.edit_color_required, Toast.LENGTH_SHORT).show()
            return null
        }
        return declarations.joinToString(";")
    }

    /**
     * 편집 중인 웹뷰의 현재 스크롤 위치를 시작 위치로 저장한다.
     * Tune 당 하나만 의미가 있으므로 기존 scroll 액션을 덮어쓴다.
     */
    private fun addScrollAction() {
        // WebView.scrollY 는 기기 밀도가 곱해진 값이라 CSS 픽셀과 어긋난다. 적용도 JS 로 하므로 읽는 쪽도 JS 기준으로 맞춘다.
        binding.wvInspector.evaluateJavascript("Math.round(window.pageYOffset)") { result ->
            val y = result?.trim('"')?.toDoubleOrNull()?.toInt()
            if (y == null) {
                Toast.makeText(this, R.string.edit_start_read_error, Toast.LENGTH_SHORT).show()
                return@evaluateJavascript
            }
            tempActions.removeAll { it.actionType == "scroll" }
            tempActions.add(
                PluginAction(
                    pluginId = pluginId,
                    actionType = "scroll",
                    targetSelector = JSONObject().put("y", y).toString()
                )
            )
            refreshActionsListUI()
            Toast.makeText(this, getString(R.string.edit_start_set, y), Toast.LENGTH_SHORT).show()
        }
    }

    private fun addAction() {
        val details = selectedElementJson ?: return
        val actionType = when {
            binding.rbActionClick.isChecked -> "click"
            binding.rbActionColor.isChecked -> "color"
            else -> "hidden"
        }

        var triggerType: String? = null
        var areaPercent = 40
        var styleCss: String? = null

        if (actionType == "color") {
            styleCss = buildStyleCss() ?: return
        }

        if (actionType == "click") {
            triggerType = TriggerOptions.typeAt(binding.spTriggerType.selectedItemPosition)
            
            if (triggerType?.startsWith("gesture") == true) {
                val areaText = binding.etGestureArea.text.toString().trim()
                if (areaText.isNotEmpty()) {
                    val value = areaText.toIntOrNull()
                    if (value == null || value <= 0 || value > 50) {
                        Toast.makeText(this, R.string.edit_area_error, Toast.LENGTH_SHORT).show()
                        return
                    }
                    areaPercent = value
                }
            }
        }

        val action = PluginAction(
            pluginId = pluginId,
            actionType = actionType,
            targetSelector = details,
            triggerType = triggerType,
            gestureAreaPercent = areaPercent,
            styleCss = styleCss
        )

        tempActions.add(action)
        refreshActionsListUI()
        
        // Clear selection highlights visually but keep pick mode active
        binding.wvInspector.evaluateJavascript("window.webPluginClearHighlight();", null)
        
        // Reset configuration layout but keep inspector mode on
        hideActionConfigPanel()
        
        // Apply instantly to the inspector WebView
        applyCurrentActions()
        
        Toast.makeText(this, R.string.edit_rule_added, Toast.LENGTH_SHORT).show()
    }

    private fun triggerLabel(triggerType: String?): String =
        TriggerOptions.labelResOf(triggerType)?.let { getString(it) } ?: triggerType.orEmpty()

    private fun refreshActionsListUI() {
        binding.layoutActionsList.removeAllViews()
        if (tempActions.isEmpty()) {
            val emptyTv = TextView(this).apply {
                setText(R.string.edit_rules_empty)
                textSize = 12f
                setTextColor(androidx.core.content.ContextCompat.getColor(this@PluginEditActivity, R.color.colorTextSecondary))
            }
            binding.layoutActionsList.addView(emptyTv)
            return
        }

        tempActions.forEachIndexed { index, action ->
            val view = layoutInflater.inflate(android.R.layout.simple_list_item_2, null)
            val text1 = view.findViewById<TextView>(android.R.id.text1)
            val text2 = view.findViewById<TextView>(android.R.id.text2)

            val textColorPrimary = androidx.core.content.ContextCompat.getColor(this, R.color.colorTextPrimary)
            val textColorSecondary = androidx.core.content.ContextCompat.getColor(this, R.color.colorTextSecondary)

            try {
                val json = JSONObject(action.targetSelector)
                val selector = json.optString("selector")
                val typeDesc = when (action.actionType) {
                    "hidden" -> getString(R.string.edit_rule_type_hide)
                    "color" -> getString(R.string.edit_rule_type_color, action.styleCss)
                    "scroll" -> getString(R.string.edit_rule_type_start, json.optInt("y"))
                    else -> getString(R.string.edit_rule_type_click, triggerLabel(action.triggerType))
                }
                text1.text = getString(R.string.edit_rule_title, index + 1, typeDesc)
                text1.setTextColor(textColorPrimary)
                text2.text = when {
                    action.actionType == "scroll" -> getString(R.string.edit_rule_start_note)
                    UnstableId.needsReselect(action.targetSelector) -> getString(R.string.edit_rule_select_again, selector)
                    else -> selector
                }
                text2.setTextColor(textColorSecondary)
                text2.textSize = 11f
            } catch (e: Exception) {
                text1.text = getString(R.string.edit_rule_title, index + 1, action.actionType)
                text1.setTextColor(textColorPrimary)
                text2.setText(R.string.edit_rule_broken)
                text2.setTextColor(textColorSecondary)
            }

            // Click to remove action
            view.setOnClickListener {
                tempActions.removeAt(index)
                refreshActionsListUI()
                binding.wvInspector.reload() // Reload page to restore deleted hidden item
                Toast.makeText(this, R.string.edit_rule_removed, Toast.LENGTH_SHORT).show()
            }

            binding.layoutActionsList.addView(view)
        }
    }

    private fun savePlugin() {
        val plan = planSave(
            binding.etInpPname.text.toString(), binding.etInpTaddr.text.toString(), tempActions,
            pluginId.takeIf { it != -1L }, linkWebPageId
        )
        if (plan == null) {
            Toast.makeText(this, R.string.edit_name_required, Toast.LENGTH_SHORT).show()
            return
        }

        mainScope.launch {
            val savedId = withContext(Dispatchers.IO) { executeSave(plan, RoomTuneStore(db)) }
            Toast.makeText(this@PluginEditActivity, R.string.edit_saved, Toast.LENGTH_SHORT).show()
            setResult(RESULT_OK, Intent().putExtra(EXTRA_SAVED_PLUGIN_ID, savedId))
            finish()
        }
    }

    companion object {
        const val EXTRA_LOAD_URL = "load_url"
        const val EXTRA_LINK_WEBPAGE_ID = "link_webpage_id"
        const val EXTRA_SAVED_PLUGIN_ID = "saved_plugin_id"
    }

    // Inner class bridge for element selection callback
    inner class AndroidInspectorBridge {
        @JavascriptInterface
        fun onElementSelected(detailsJson: String, matchCount: Int) {
            runOnUiThread {
                onElementSelectedFromJs(detailsJson, matchCount)
            }
        }
    }
}
