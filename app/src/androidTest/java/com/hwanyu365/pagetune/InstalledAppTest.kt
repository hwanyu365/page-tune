package com.hwanyu365.pagetune

import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** 라이브러리가 더한 선언은 앱 매니페스트에 없다 → 설치된 앱(병합 매니페스트)으로 확인한다. */
@RunWith(AndroidJUnit4::class)
class InstalledAppTest {

    private val pkg = targetContext.packageName

    @Test
    @JvmName("tc404")
    fun `TC#404 설치된 앱의 아이콘은 mipmap 의 ic_launcher 다`() {
        val icon = targetContext.packageManager.getApplicationInfo(pkg, 0).icon
        assertEquals("mipmap/ic_launcher", targetContext.resources.run { "${getResourceTypeName(icon)}/${getResourceEntryName(icon)}" })
    }

    @Test
    @JvmName("tc407")
    fun `TC#407 설치된 앱의 권한은 허용 목록 그대로다`() {
        val info = targetContext.packageManager.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
        // androidx.core 가 더하는 signature 권한이다 → 앱 안 브로드캐스트를 다른 앱이 보내지 못하게 하는 용도라 방침의 권한 서술과 무관하다.
        val internal = "$pkg.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
        assertEquals(PERMISSION_RULE, setOf("android.permission.INTERNET", internal), info.requestedPermissions.orEmpty().toSet())
        assertEquals(PERMISSION_RULE, listOf(internal to android.content.pm.PermissionInfo.PROTECTION_SIGNATURE), info.permissions.orEmpty().map { it.name to (it.protectionLevel and android.content.pm.PermissionInfo.PROTECTION_MASK_BASE) })
    }

    private companion object {
        const val PERMISSION_RULE = "권한이 바뀌면 개인정보 처리방침·스토어 문구가 맞는지 확인하고 이 목록을 고친다"
    }
}
