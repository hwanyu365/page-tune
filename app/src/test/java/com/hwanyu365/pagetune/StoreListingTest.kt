package com.hwanyu365.pagetune

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreListingTest {

    private val listings = File("src/main/play/listings")
    private val locales = listOf("en-US", "ko-KR")
    private val limits = mapOf("title.txt" to 30, "short-description.txt" to 80, "full-description.txt" to 4000)

    @Test
    fun `TC#1 listing 파일은 모든 언어에 있고 글자 수 제한 이하다`() {
        for (locale in locales) for ((name, max) in limits) {
            val f = File(listings, "$locale/$name")
            assertTrue("$f 없음", f.isFile)
            val len = f.readText().trim().length
            assertTrue("$f ${len}자 (최대 $max)", len in 1..max)
        }
    }

    @Test
    fun `TC#2 listing 은 광고 차단을 문구로 쓰지 않는다`() {
        val banned = listOf("ad block", "adblock", "광고 차단", "광고차단")
        for (locale in locales) for (name in limits.keys) {
            val text = File(listings, "$locale/$name").readText().lowercase()
            for (word in banned) assertFalse("$locale/$name 에 '$word'", word in text)
        }
    }

    @Test
    fun `TC#3 개인정보 처리방침은 한국어·영어 본문과 이슈 URL 을 갖고 이메일이 없다`() {
        val text = File("../docs/privacy.md").readText()
        assertTrue("# PageTune 개인정보 처리방침" in text)
        assertTrue("# PageTune Privacy Policy" in text)
        assertTrue("https://github.com/hwanyu365/page-tune/issues" in text)
        val dates = Regex("""(?:시행일|Effective date): (\d{4}-\d{2}-\d{2})""").findAll(text).map { it.groupValues[1] }.toList()
        assertEquals(2, dates.size)
        assertEquals(1, dates.toSet().size)
        assertFalse(Regex("""[\w.+-]+@[\w-]+\.[\w.]+""").containsMatchIn(text))
    }

    @Test
    fun `TC#13 매니페스트의 권한·백업 설정이 방침 서술과 맞는다`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val permissions = Regex("""<uses-permission android:name="([^"]+)"""").findAll(manifest).map { it.groupValues[1] }.toList()
        assertEquals(listOf("android.permission.INTERNET"), permissions)

        assertTrue("allowBackup=\"true\"" in manifest)
        assertFalse("백업 제외 규칙이 생기면 방침 백업 항목을 고친다", "dataExtractionRules" in manifest || "fullBackupContent" in manifest)

        val policy = File("../docs/privacy.md").readText()
        assertTrue("Google 계정" in policy && "Google account" in policy)
        assertFalse("기기 밖으로 전송되지 않아요" in policy)
        assertFalse("not sent off your device" in policy)
    }

    @Test
    fun `TC#14 listing 은 백업과 어긋나는 기기에만 저장 표현을 쓰지 않는다`() {
        for (locale in locales) {
            val text = File(listings, "$locale/full-description.txt").readText()
            assertFalse("$locale", "기기에만" in text || "only on your device" in text)
        }
    }
}
