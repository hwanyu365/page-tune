package com.hwanyu365.pagetune

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class EditSheetTest {

    private val edit = File("src/main/java").walk().single { it.name == "PluginEditActivity.kt" }.readText()

    @Test
    fun `TC#55 접힌 시트는 스크롤 영역을 숨기고 펼침 진행도가 0 보다 클 때만 보인다`() {
        val setup = edit.substringAfter("private fun setupBottomSheet()").substringBefore("\n    }\n")
        val onSlide = setup.substringAfter("override fun onSlide(").substringBefore("\n            }")

        assertTrue(setup.contains("binding.scrollBottomConfig.visibility = View.INVISIBLE"))
        assertTrue(onSlide.contains("binding.scrollBottomConfig.visibility"))
        assertTrue(onSlide.contains("slideOffset > 0"))
    }

    @Test
    fun `TC#56 편집 화면은 진입 시 키보드를 띄우지 않는다`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val activity = manifest.substringAfter("android:name=\".PluginEditActivity\"").substringBefore("/>")

        assertTrue(Regex("""android:windowSoftInputMode="[^"]*stateHidden""").containsMatchIn(activity))
    }
}
