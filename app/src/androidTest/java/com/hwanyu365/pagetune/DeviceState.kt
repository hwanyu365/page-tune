package com.hwanyu365.pagetune

import android.content.Context
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.core.content.edit
import androidx.core.content.pm.PackageInfoCompat
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue

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

// 앱을 지워도 남는 자리여야 한다 → 테스트가 되돌리기 전에 죽어도 다음 실행의 restoreDeviceSettings 가 찾는다.
private const val RESTORE_SCRIPT = "/data/local/tmp/pagetune-test-restore.sh"

fun changeDeviceSetting(change: String, restore: String) {
    // executeShellCommand 는 셸을 거치지 않아 리디렉션을 쓸 수 없다 → tee 의 표준 입력으로 쓴다.
    val (output, input) = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommandRw("tee -a $RESTORE_SCRIPT")
    ParcelFileDescriptor.AutoCloseOutputStream(input).use { it.write("$restore\n".toByteArray()) }
    ParcelFileDescriptor.AutoCloseInputStream(output).use { it.readBytes() }
    shell(change)
}

fun restoreDeviceSettings() {
    shell("sh $RESTORE_SCRIPT")
    shell("rm -f $RESTORE_SCRIPT")
}

fun waitUntil(timeoutMs: Long = 5_000, condition: () -> Boolean) {
    val deadline = SystemClock.uptimeMillis() + timeoutMs
    while (!condition()) {
        assertTrue("조건을 ${timeoutMs}ms 안에 만족하지 못함", SystemClock.uptimeMillis() < deadline)
        SystemClock.sleep(100)
    }
}

fun <T> eventually(timeoutMs: Long = 10_000, block: () -> T): T {
    val deadline = SystemClock.uptimeMillis() + timeoutMs
    while (true) {
        try {
            return block()
        } catch (e: Throwable) {
            if (SystemClock.uptimeMillis() > deadline) throw e
            SystemClock.sleep(200)
        }
    }
}

fun resetApp() {
    restoreDeviceSettings()
    clearData()
    markIntroSeen()
}
