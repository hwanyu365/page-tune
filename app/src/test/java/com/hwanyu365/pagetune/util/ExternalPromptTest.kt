package com.hwanyu365.pagetune.util

import com.hwanyu365.pagetune.util.ExternalPrompt.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalPromptTest {

    private fun intent(data: String?, pkg: String?, fallback: String?) = ExternalTarget.IntentTarget(data, pkg, fallback)

    @Test
    fun `TC#59 intent 는 앱 라벨을 우선하고 없으면 package 를 보이며 대체 주소 유무를 담는다`() {
        assertEquals(
            ExternalPrompt(Kind.APP, "Chrome", "https://f/", intent = true),
            ExternalPrompt.describe(intent("https://x", "com.android.chrome", "https://f/")) { "Chrome" },
        )
        assertEquals(
            ExternalPrompt(Kind.APP, "com.not.installed", null, intent = true),
            ExternalPrompt.describe(intent("myapp://x", "com.not.installed", null)) { null },
        )
    }

    @Test
    fun `TC#59 package 없는 intent 는 data 주소를 보이고 둘 다 없으면 대상 없음이다`() {
        assertEquals(ExternalPrompt(Kind.ADDRESS, "geo:1,2", null, intent = true), ExternalPrompt.describe(intent("geo:1,2", null, null)) { null })
        assertEquals(ExternalPrompt(Kind.UNKNOWN, null, "https://f/", intent = true), ExternalPrompt.describe(intent(null, null, "https://f/")) { null })
    }

    @Test
    fun `TC#59 mailto 는 받는 주소, tel 은 번호, 그 밖은 주소 전체를 보인다`() {
        fun view(url: String) = ExternalPrompt.describe(ExternalTarget.View(url)) { null }
        assertEquals(ExternalPrompt(Kind.EMAIL, "a@example.com", null), view("mailto:a@example.com?subject=hi"))
        assertEquals(ExternalPrompt(Kind.EMAIL, "a@example.com", null), view("MAILTO:a@example.com"))
        assertEquals(ExternalPrompt(Kind.PHONE, "010-1234", null), view("tel:010-1234"))
        assertEquals(ExternalPrompt(Kind.ADDRESS, "market://details?id=x", null), view("market://details?id=x"))
    }

    @Test
    fun `TC#59 긴 대상은 잘라 말줄임표를 붙인다`() {
        val long = "market://details?id=" + "x".repeat(200)
        val shown = ExternalPrompt.describe(ExternalTarget.View(long)) { null }.subject!!
        assertEquals(ExternalPrompt.MAX_LENGTH, shown.length)
        assertTrue(shown.endsWith("…"))
        assertTrue(shown.startsWith("market://details?id="))
        val fallback = ExternalPrompt.describe(intent("a://b", "com.x", "https://f/" + "y".repeat(200))) { null }.fallback!!
        assertEquals(ExternalPrompt.MAX_LENGTH, fallback.length)
        val exact = "tel:" + "1".repeat(ExternalPrompt.MAX_LENGTH)
        assertEquals("1".repeat(ExternalPrompt.MAX_LENGTH), ExternalPrompt.describe(ExternalTarget.View(exact)) { null }.subject)
    }

    @Test
    fun `TC#59 확인 창은 떠 있는 창이 없고 화면이 살아 있을 때만 띄운다`() {
        assertTrue(ExternalPrompt.canShow(showing = false, finishing = false, destroyed = false))
        assertFalse(ExternalPrompt.canShow(showing = true, finishing = false, destroyed = false))
        assertFalse(ExternalPrompt.canShow(showing = false, finishing = true, destroyed = false))
        assertFalse(ExternalPrompt.canShow(showing = false, finishing = false, destroyed = true))
    }
}
