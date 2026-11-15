package com.hwanyu365.pagetune

import org.junit.runner.Result
import org.junit.runner.notification.RunListener

// resetApp() 이 테스트마다 기기 설정을 바꾸지만 되돌리는 tearDown 은 클래스마다 다르다 → 실행이 끝날 때 한 번 모두 되돌린다.
class RestoreDeviceSettingsListener : RunListener() {
    override fun testRunFinished(result: Result) = restoreDeviceSettings()
}
