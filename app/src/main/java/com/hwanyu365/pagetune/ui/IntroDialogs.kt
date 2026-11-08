package com.hwanyu365.pagetune.ui

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hwanyu365.pagetune.R
import com.hwanyu365.pagetune.db.AppDatabase
import com.hwanyu365.pagetune.util.Intro
import com.hwanyu365.pagetune.util.decideIntro
import com.hwanyu365.pagetune.util.versionNameOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PREFS = "intro"
private const val KEY_LAST_SEEN = "last_seen_version_code"

private val GETTING_STARTED = listOf(
    R.string.intro_1_title to R.string.intro_1_body,
    R.string.intro_2_title to R.string.intro_2_body,
    R.string.intro_3_title to R.string.intro_3_body,
)

/** 메인 화면 위에 시작 안내 또는 새 소식을 띄운다. 닫힐 때만 기록하므로 닫기 전에 화면이 사라지면 다음 실행에 다시 보인다. */
fun showIntroIfNeeded(activity: AppCompatActivity) {
    val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val recorded = if (prefs.contains(KEY_LAST_SEEN)) prefs.getInt(KEY_LAST_SEEN, 0) else null
    val current = PackageInfoCompat.getLongVersionCode(activity.packageManager.getPackageInfo(activity.packageName, 0)).toInt()
    fun record() = prefs.edit { putInt(KEY_LAST_SEEN, current) }

    val scope = CoroutineScope(Dispatchers.Main)
    var dialog: Dialog? = null
    activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
        override fun onDestroy(owner: LifecycleOwner) {
            scope.cancel()
            dialog?.setOnDismissListener(null)
            dialog?.dismiss()
        }
    })

    scope.launch {
        val db = AppDatabase.getDatabase(activity)
        val hasData = withContext(Dispatchers.IO) { db.webPageDao().hasAny() || db.pluginDao().hasAny() }
        val decision = decideIntro(recorded, current, hasData, WHATS_NEW_NOTES.map { it.first })
        decision.recordNow?.let { record() }
        dialog = when (val show = decision.show) {
            Intro.GettingStarted -> gettingStarted(activity)
            is Intro.WhatsNew -> whatsNew(activity, show.versions)
            Intro.None -> null
        }?.apply {
            setOnDismissListener { record() }
            show()
        }
    }
}

private fun gettingStarted(context: Context): Dialog {
    var page = 0
    val dialog = MaterialAlertDialogBuilder(context)
        .setTitle(GETTING_STARTED[0].first)
        .setMessage(GETTING_STARTED[0].second)
        .setNegativeButton(R.string.intro_skip, null)
        .setPositiveButton(R.string.intro_next, null)
        .create()
    dialog.setCanceledOnTouchOutside(false)
    // 다음은 다이얼로그를 닫지 않고 장만 넘긴다. 마지막 장의 시작하기만 닫는다.
    dialog.setOnShowListener {
        val positive = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
        positive.setOnClickListener {
            if (page == GETTING_STARTED.lastIndex) return@setOnClickListener dialog.dismiss()
            page++
            dialog.setTitle(GETTING_STARTED[page].first)
            dialog.setMessage(context.getString(GETTING_STARTED[page].second))
            if (page == GETTING_STARTED.lastIndex) positive.setText(R.string.intro_start)
        }
    }
    return dialog
}

private fun whatsNew(context: Context, versions: List<Int>): Dialog {
    val notes = WHATS_NEW_NOTES.toMap()
    val text = versions.joinToString("\n\n") { code ->
        context.getString(R.string.whats_new_version, versionNameOf(code)) + "\n" + context.getString(notes.getValue(code))
    }
    return MaterialAlertDialogBuilder(context)
        .setTitle(R.string.whats_new_title)
        .setMessage(text)
        .setPositiveButton(R.string.ok, null)
        .create()
        .apply { setCanceledOnTouchOutside(false) }
}
