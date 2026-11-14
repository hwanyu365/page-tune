package com.hwanyu365.pagetune

import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.core.content.edit
import androidx.core.content.pm.PackageInfoCompat
import androidx.test.platform.app.InstrumentationRegistry

val targetContext: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

// 새로 설치한 기기는 시작 안내 다이얼로그가 메인 화면을 덮는다 → 이 버전의 안내를 본 상태로 시작한다.
fun markIntroSeen() {
    val context = targetContext
    val code = PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0)).toInt()
    context.getSharedPreferences("intro", Context.MODE_PRIVATE).edit(commit = true) { putInt("last_seen_version_code", code) }
}

fun shell(command: String): String {
    val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    return ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().use { it.readText() }
}
