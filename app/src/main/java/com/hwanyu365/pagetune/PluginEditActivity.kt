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
import com.hwanyu365.pagetune.util.JsScripts
import kotlinx.android.synthetic.main.activity_plugin_edit.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class PluginEditActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private val mainScope = CoroutineScope(Dispatchers.Main)
    
    private var pluginId: Long = -1
    private val tempActions = mutableListOf<PluginAction>()
    private var selectedElementJson: String? = null
    
    private var isInspectMode = false
    private var retryCount = 0
    private val MAX_RETRY = 3

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_plugin_edit)

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
        com.hwanyu365.pagetune.util.WebViewConfig.applyCommon(wv_inspector)

        wv_inspector.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                // Bypass navigator.webdriver detection used by Cloudflare
                view?.evaluateJavascript("Object.defineProperty(navigator, 'webdriver', {get: () => undefined})", null)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Reset retry count on successful load
                retryCount = 0
                // If inspect mode is on when page finishes loading, inject picker script again
                if (isInspectMode) {
                    injectPickerScript()
                }
                // Automatically re-apply all current hidden actions
                applyCurrentHiddenActions()
            }

            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                // Auto-retry on connection reset (Cloudflare challenge needs time)
                if (errorCode == ERROR_CONNECT && retryCount < MAX_RETRY) {
                    retryCount++
                    view?.postDelayed({ view.reload() }, 3000)
                }
            }
        }
        wv_inspector.webChromeClient = WebChromeClient()
        wv_inspector.addJavascriptInterface(AndroidInspectorBridge(), "AndroidBridge")
    }

    private fun setupListeners() {
        btn_load_url.setOnClickListener {
            var url = et_inp_taddr.text.toString().trim()
            if (url.isNotEmpty()) {
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    url = "https://$url"
                    et_inp_taddr.setText(url)
                }
                // If the target URL is already the current URL, force reload
                if (wv_inspector.url == url) {
                    wv_inspector.reload()
                } else {
                    wv_inspector.loadUrl(url)
                }
            }
        }

        btn_toggle_inspect.setOnCheckedChangeListener { _, isChecked ->
            isInspectMode = isChecked
            if (isChecked) {
                tv_inspector_status.visibility = View.VISIBLE
                wv_inspector.evaluateJavascript("if (typeof window.webPluginInspectEnabled !== 'undefined') { window.webPluginInspectEnabled = true; 'enabled'; } else { 'inject'; }") { result ->
                    if (result == "\"inject\"") {
                        injectPickerScript()
                    }
                }
            } else {
                tv_inspector_status.visibility = View.GONE
                // Disable click listener in JS and clear highlights
                wv_inspector.evaluateJavascript("window.webPluginInspectEnabled = false; window.webPluginClearHighlight();", null)
                hideActionConfigPanel()
            }
        }

        btn_select_parent.setOnClickListener {
            wv_inspector.evaluateJavascript("window.webPluginSelectParent();", null)
        }

        btn_select_child.setOnClickListener {
            wv_inspector.evaluateJavascript("window.webPluginSelectChild();", null)
        }

        rg_action_type.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rb_action_click) {
                layout_click_trigger_setup.visibility = View.VISIBLE
            } else {
                layout_click_trigger_setup.visibility = View.GONE
            }
        }

        btn_add_action.setOnClickListener {
            addAction()
        }

        btn_plugin_save.setOnClickListener {
            savePlugin()
        }

        btn_plugin_cancel.setOnClickListener {
            finish()
        }
    }

    private fun loadPluginData() {
        mainScope.launch {
            val pluginWithActions = withContext(Dispatchers.IO) {
                db.pluginDao().getPluginWithActions(pluginId)
            }
            if (pluginWithActions != null) {
                et_inp_pname.setText(pluginWithActions.plugin.name)
                pluginWithActions.plugin.testUrl?.let { testUrl ->
                    et_inp_taddr.setText(testUrl)
                    wv_inspector.loadUrl(testUrl)
                }
                tempActions.addAll(pluginWithActions.actions)
                refreshActionsListUI()
            }
        }
    }

    private fun injectPickerScript() {
        wv_inspector.evaluateJavascript(JsScripts.ELEMENT_PICKER_SCRIPT, null)
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
            tv_selected_selector.text = displayText
            layout_traversal_actions.visibility = View.VISIBLE
            layout_action_config.visibility = View.VISIBLE
        } catch (e: Exception) {
            tv_selected_selector.text = "Error parsing element"
        }
    }

    private fun hideActionConfigPanel() {
        layout_traversal_actions.visibility = View.GONE
        layout_action_config.visibility = View.GONE
        tv_selected_selector.text = "Selected element: None"
        selectedElementJson = null
    }

    private fun applyCurrentHiddenActions() {
        val hiddenActions = tempActions.filter { it.actionType == "hidden" }
        if (hiddenActions.isEmpty()) {
            // Disconnect old MutationObserver if no hidden actions
            wv_inspector.evaluateJavascript("(function() { if (window.webPluginObserver) { window.webPluginObserver.disconnect(); } })();", null)
            return
        }
        val jsonArray = org.json.JSONArray()
        hiddenActions.forEach {
            jsonArray.put(it.targetSelector)
        }
        val script = JsScripts.getMutationObserverHideScript(jsonArray.toString())
        wv_inspector.evaluateJavascript(script, null)
    }

    private fun addAction() {
        val details = selectedElementJson ?: return
        val actionType = if (rb_action_hidden.isChecked) "hidden" else "click"
        
        var triggerType: String? = null
        var areaPercent = 40

        if (actionType == "click") {
            triggerType = when {
                rb_trigger_gesture_top.isChecked -> "gesture_top"
                rb_trigger_gesture_bottom.isChecked -> "gesture_bottom"
                rb_trigger_gnb_prev.isChecked -> "gnb_prev"
                rb_trigger_gnb_next.isChecked -> "gnb_next"
                else -> null
            }
            
            if (triggerType?.startsWith("gesture") == true) {
                val areaText = et_gesture_area.text.toString().trim()
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
            gestureAreaPercent = areaPercent
        )

        tempActions.add(action)
        refreshActionsListUI()
        
        // Clear selection highlights visually but keep pick mode active
        wv_inspector.evaluateJavascript("window.webPluginClearHighlight();", null)
        
        // Reset configuration layout but keep inspector mode on
        hideActionConfigPanel()
        
        // Apply instantly to the inspector WebView
        applyCurrentHiddenActions()
        
        Toast.makeText(this, "Setup added successfully", Toast.LENGTH_SHORT).show()
    }

    private fun refreshActionsListUI() {
        layout_actions_list.removeAllViews()
        if (tempActions.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "No setups added yet."
                textSize = 12f
                setTextColor(androidx.core.content.ContextCompat.getColor(this@PluginEditActivity, R.color.colorTextSecondary))
            }
            layout_actions_list.addView(emptyTv)
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
                val typeDesc = if (action.actionType == "hidden") "Hide" else "Click via ${action.triggerType}"
                text1.text = "Setup #${index + 1}: $typeDesc"
                text1.setTextColor(textColorPrimary)
                text2.text = selector
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
                wv_inspector.reload() // Reload page to restore deleted hidden item
                Toast.makeText(this, "Setup removed", Toast.LENGTH_SHORT).show()
            }

            layout_actions_list.addView(view)
        }
    }

    private fun savePlugin() {
        val name = et_inp_pname.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter a Tune name", Toast.LENGTH_SHORT).show()
            return
        }
        val testUrl = et_inp_taddr.text.toString().trim()

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
