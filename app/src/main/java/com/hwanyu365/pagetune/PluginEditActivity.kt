package com.hwanyu365.pagetune

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.hwanyu365.pagetune.db.AppDatabase
import com.hwanyu365.pagetune.db.Plugin
import com.hwanyu365.pagetune.db.PluginAction
import com.hwanyu365.pagetune.databinding.ActivityPluginEditBinding
import com.hwanyu365.pagetune.ui.view.ColorPickerDialog
import com.hwanyu365.pagetune.util.JsScripts
import com.hwanyu365.pagetune.util.enableDarkEdgeToEdge
import com.hwanyu365.pagetune.util.padForInsets
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
    private val tempActions = mutableListOf<PluginAction>()
    private var selectedElementJson: String? = null
    
    private var isInspectMode = false

    // "color" 액션의 입력값. picker 만 이 값을 만들므로 항상 "#RRGGBB" 이거나 null 이다.
    private var colorBg: String? = null
    private var colorText: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableDarkEdgeToEdge()
        binding = ActivityPluginEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.padForInsets()

        // Programmatically exclude all views in this Activity from Android Autofill Service
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            window.decorView.importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }

        db = AppDatabase.getDatabase(this)
        pluginId = intent.getLongExtra("plugin_id", -1)

        setupWebView()
        setupListeners()

        if (pluginId != -1L) {
            loadPluginData()
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

        binding.btnColorBg.setOnClickListener {
            ColorPickerDialog.show(this, "Background", colorBg) { picked ->
                colorBg = picked
                refreshColorButtons()
            }
        }

        binding.btnColorText.setOnClickListener {
            ColorPickerDialog.show(this, "Text", colorText) { picked ->
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
            finish()
        }
    }

    private fun loadPluginData() {
        mainScope.launch {
            val pluginWithActions = withContext(Dispatchers.IO) {
                db.pluginDao().getPluginWithActions(pluginId)
            }
            if (pluginWithActions != null) {
                binding.etInpPname.setText(pluginWithActions.plugin.name)
                pluginWithActions.plugin.testUrl?.let { testUrl ->
                    binding.etInpTaddr.setText(testUrl)
                    binding.wvInspector.loadUrl(testUrl)
                }
                tempActions.addAll(pluginWithActions.actions)
                refreshActionsListUI()
            }
        }
    }

    private fun injectPickerScript() {
        binding.wvInspector.evaluateJavascript(JsScripts.ELEMENT_PICKER_SCRIPT, null)
    }

    private fun onElementSelectedFromJs(detailsJson: String) {
        selectedElementJson = detailsJson
        try {
            val json = JSONObject(detailsJson)
            val selector = json.optString("selector")
            val textContent = json.optString("textContent")
            val displayText = if (textContent.isNotEmpty()) {
                "Selected: \"$textContent\" ($selector)"
            } else {
                "Selected: $selector"
            }
            binding.tvSelectedSelector.text = displayText
            binding.layoutTraversalActions.visibility = View.VISIBLE
            binding.layoutActionConfig.visibility = View.VISIBLE
        } catch (e: Exception) {
            binding.tvSelectedSelector.text = "Error parsing element"
        }
    }

    private fun hideActionConfigPanel() {
        binding.layoutTraversalActions.visibility = View.GONE
        binding.layoutActionConfig.visibility = View.GONE
        binding.tvSelectedSelector.text = "Selected element: None"
        selectedElementJson = null
        colorBg = null
        colorText = null
        refreshColorButtons()
    }

    private fun refreshColorButtons() {
        binding.btnColorBg.text = "Background: ${colorBg ?: "none"}"
        binding.btnColorText.text = "Text: ${colorText ?: "none"}"
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
        val jsonArray = org.json.JSONArray()
        hiddenActions.forEach {
            jsonArray.put(it.targetSelector)
        }
        val script = JsScripts.getMutationObserverHideScript(jsonArray.toString())
        binding.wvInspector.evaluateJavascript(script, null)
    }

    /** 선택한 색상을 CSS 선언부로 만든다. 둘 다 비면 null 을 돌려 액션 추가를 막는다. */
    private fun buildStyleCss(): String? {
        val declarations = listOfNotNull(
            colorBg?.let { "background-color:$it" },
            colorText?.let { "color:$it" }
        )
        if (declarations.isEmpty()) {
            Toast.makeText(this, "Please pick at least one color", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, "Could not read scroll position", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "Start scroll position set to $y", Toast.LENGTH_SHORT).show()
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
            triggerType = when {
                binding.rbTriggerGestureTop.isChecked -> "gesture_top"
                binding.rbTriggerGestureBottom.isChecked -> "gesture_bottom"
                binding.rbTriggerGnbPrev.isChecked -> "gnb_prev"
                binding.rbTriggerGnbNext.isChecked -> "gnb_next"
                else -> null
            }
            
            if (triggerType?.startsWith("gesture") == true) {
                val areaText = binding.etGestureArea.text.toString().trim()
                if (areaText.isNotEmpty()) {
                    val value = areaText.toIntOrNull()
                    if (value == null || value <= 0 || value > 50) {
                        Toast.makeText(this, "Please enter valid value (0 < value <= 50)", Toast.LENGTH_SHORT).show()
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
        
        Toast.makeText(this, "Setup added successfully", Toast.LENGTH_SHORT).show()
    }

    private fun refreshActionsListUI() {
        binding.layoutActionsList.removeAllViews()
        if (tempActions.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "No setups added yet."
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
                    "hidden" -> "Hide"
                    "color" -> "Color (${action.styleCss})"
                    "scroll" -> "Start scroll at y=${json.optInt("y")}"
                    else -> "Click via ${action.triggerType}"
                }
                text1.text = "Setup #${index + 1}: $typeDesc"
                text1.setTextColor(textColorPrimary)
                text2.text = if (action.actionType == "scroll") "Applied on every page load" else selector
                text2.setTextColor(textColorSecondary)
                text2.textSize = 11f
            } catch (e: Exception) {
                text1.text = "Setup #${index + 1}: ${action.actionType}"
                text1.setTextColor(textColorPrimary)
                text2.text = "Broken Selector"
                text2.setTextColor(textColorSecondary)
            }

            // Click to remove action
            view.setOnClickListener {
                tempActions.removeAt(index)
                refreshActionsListUI()
                binding.wvInspector.reload() // Reload page to restore deleted hidden item
                Toast.makeText(this, "Setup removed", Toast.LENGTH_SHORT).show()
            }

            binding.layoutActionsList.addView(view)
        }
    }

    private fun savePlugin() {
        val name = binding.etInpPname.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter a Tune name", Toast.LENGTH_SHORT).show()
            return
        }
        val testUrl = binding.etInpTaddr.text.toString().trim()

        mainScope.launch {
            withContext(Dispatchers.IO) {
                if (pluginId == -1L) {
                    // Create new plugin
                    val newId = db.pluginDao().insertPlugin(Plugin(name = name, testUrl = testUrl))
                    // Re-assign new ID to actions and save
                    val actionsToSave = tempActions.map { it.copy(pluginId = newId) }
                    db.pluginDao().insertActions(actionsToSave)
                } else {
                    // Update existing plugin
                    db.pluginDao().updatePlugin(Plugin(id = pluginId, name = name, testUrl = testUrl))
                    // Delete old actions and insert new ones
                    db.pluginDao().deleteActionsByPluginId(pluginId)
                    val actionsToSave = tempActions.map { it.copy(pluginId = pluginId) }
                    db.pluginDao().insertActions(actionsToSave)
                }
            }
            Toast.makeText(this@PluginEditActivity, "Tune saved successfully", Toast.LENGTH_SHORT).show()
            finish()
        }
    }


    // Inner class bridge for element selection callback
    inner class AndroidInspectorBridge {
        @JavascriptInterface
        fun onElementSelected(detailsJson: String) {
            runOnUiThread {
                onElementSelectedFromJs(detailsJson)
            }
        }
    }
}
