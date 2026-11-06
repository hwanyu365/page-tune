package com.hwanyu365.pagetune

import java.io.File
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
        assertFalse(Regex("""[\w.+-]+@[\w-]+\.[\w.]+""").containsMatchIn(text))
    }
}
