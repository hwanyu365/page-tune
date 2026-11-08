package com.hwanyu365.pagetune.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.core.net.toUri
import android.net.http.SslError
import android.os.Build
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import android.app.Dialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.hwanyu365.pagetune.R

open class SafeWebViewClient(
    private val activity: Activity,
    private val onLoadError: (kind: LoadErrorKind, failingUrl: String?) -> Unit,
) : WebViewClient() {

    private val tracker = MainDocumentTracker()
    private var prompt: Dialog? = null

    var lastLoadedUrl: String? = null
        private set

    /** 주 문서 로드를 시작해 끝나기 전까지의 주소. WebView.url 은 새 문서가 그려지기 전까지 이전 주소를 돌려준다. */
    var loadingUrl: String? = null
        private set

    fun loadChecked(view: WebView, input: String?, onRejected: () -> Unit): String? {
        val url = input?.let { UrlPolicy.normalizeInput(it) }
        if (url == null) {
            onRejected()
            return null
        }
        tracker.start(url)
        lastLoadedUrl = url
        loadingUrl = url
        view.loadUrl(url)
        return url
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        val decision = UrlPolicy.classifyLink(url, request.isForMainFrame, request.hasGesture(), request.isRedirect, activity.packageName)
        return when (decision) {
            LinkDecision.InWebView -> {
                if (request.isForMainFrame) {
                    if (request.isRedirect) tracker.add(url) else tracker.start(url)
                }
                false
            }
            LinkDecision.Block -> true
            is LinkDecision.External -> {
                confirmExternal(view, decision.target)
                true
            }
        }
    }

    /** 사용자가 직접 한 클릭 연결 조작이 새 창으로 열려던 주소는 WebView 이동 판정을 거치지 않으므로 같은 분류를 여기서 적용한다. */
    fun openUserLink(view: WebView, url: String) {
        when (val decision = UrlPolicy.classifyLink(url, isMainFrame = true, hasGesture = true, isRedirect = false, activity.packageName)) {
            LinkDecision.InWebView -> loadChecked(view, url) {}
            LinkDecision.Block -> Unit
            is LinkDecision.External -> confirmExternal(view, decision.target)
        }
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        url?.let { tracker.add(it) }
        loadingUrl = url
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        loadingUrl = null
    }

    override fun onPageCommitVisible(view: WebView?, url: String?) {
        super.onPageCommitVisible(view, url)
        tracker.commit()
    }

    override fun onReceivedError(view: WebView?, request: WebResourceRequest, error: WebResourceError) {
        super.onReceivedError(view, request, error)
        if (request.isForMainFrame) loadingUrl = null
        classifyLoadError(error.errorCode, request.isForMainFrame)?.let { onLoadError(it, request.url.toString()) }
    }

    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler, error: SslError) {
        handler.cancel()
        if (tracker.isMainDocument(error.url)) onLoadError(LoadErrorKind.SECURE_CONNECTION, error.url)
    }

    /** 떠 있는 창이 있으면 새 요청을 버린다 → 사용자가 보고 있는 대상이 누르기 직전에 바뀌지 않는다. */
    private fun confirmExternal(view: WebView, target: ExternalTarget) {
        if (!ExternalPrompt.canShow(prompt?.isShowing == true, activity.isFinishing, activity.isDestroyed)) return
        prompt = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.external_confirm_title)
            .setMessage(promptMessage(ExternalPrompt.describe(target, ::appLabel)))
            .setPositiveButton(R.string.external_confirm_open) { _, _ -> openExternal(view, target) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun appLabel(pkg: String): String? = try {
        activity.packageManager.run { getApplicationLabel(getApplicationInfo(pkg, 0)).toString() }
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    private fun promptMessage(prompt: ExternalPrompt): String {
        val target = when (prompt.kind) {
            ExternalPrompt.Kind.APP -> activity.getString(R.string.external_target_app, prompt.subject)
            ExternalPrompt.Kind.EMAIL -> activity.getString(R.string.external_target_email, prompt.subject)
            ExternalPrompt.Kind.PHONE -> activity.getString(R.string.external_target_phone, prompt.subject)
            ExternalPrompt.Kind.ADDRESS -> activity.getString(R.string.external_target_address, prompt.subject)
            ExternalPrompt.Kind.UNKNOWN -> activity.getString(R.string.external_target_unknown)
        }
        if (!prompt.intent) return target
        val address = prompt.address?.let { "\n" + activity.getString(R.string.external_target_address, it) }.orEmpty()
        val fallback = prompt.fallback?.let { activity.getString(R.string.external_fallback, it) }
            ?: activity.getString(R.string.external_no_fallback)
        return target + address + "\n" + fallback
    }

    private fun openExternal(view: WebView, target: ExternalTarget) {
        val intent = Intent(Intent.ACTION_VIEW).addCategory(Intent.CATEGORY_BROWSABLE)
        when (target) {
            is ExternalTarget.View -> intent.data = target.url.toUri()
            is ExternalTarget.IntentTarget -> {
                target.data?.let { intent.data = it.toUri() }
                intent.setPackage(target.pkg)
            }
        }
        try {
            activity.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            val fallback = (target as? ExternalTarget.IntentTarget)?.fallbackUrl
            val showNoApp = { Toast.makeText(activity, R.string.external_no_app, Toast.LENGTH_SHORT).show() }
            if (fallback == null) showNoApp() else loadChecked(view, fallback, showNoApp)
        }
    }
}

fun Activity.openInBrowser(url: String): Boolean = try {
    startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE))
    true
} catch (e: ActivityNotFoundException) {
    false
}

/** 다운로드 진입은 주 프레임·제스처 정보가 없어, 스낵바 버튼을 사용자가 누를 때만 브라우저로 넘긴다. */
fun WebView.installSafeDownloads(activity: Activity, anchor: View) {
    var current: Snackbar? = null
    setDownloadListener { url, _, _, _, _ ->
        current?.dismiss()
        current = null
        if (!UrlPolicy.isBrowserDownload(url)) {
            Toast.makeText(activity, R.string.download_unsupported, Toast.LENGTH_SHORT).show()
            return@setDownloadListener
        }
        current = Snackbar.make(anchor, R.string.download_prompt, Snackbar.LENGTH_INDEFINITE)
            .setDuration(activity.snackbarMillis())
            .setAction(R.string.download_open_in_browser) {
                if (!activity.openInBrowser(url)) Toast.makeText(activity, R.string.external_no_app, Toast.LENGTH_SHORT).show()
            }
            .also { it.show() }
    }
}

private fun Activity.snackbarMillis(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        getSystemService(AccessibilityManager::class.java).getRecommendedTimeoutMillis(
            SNACKBAR_MILLIS, AccessibilityManager.FLAG_CONTENT_TEXT or AccessibilityManager.FLAG_CONTENT_CONTROLS,
        )
    } else {
        SNACKBAR_MILLIS
    }

private const val SNACKBAR_MILLIS = 5000
