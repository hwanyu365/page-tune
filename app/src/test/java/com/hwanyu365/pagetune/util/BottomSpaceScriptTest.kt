package com.hwanyu365.pagetune.util

import org.junit.Assert.assertEquals
import org.junit.Test

class BottomSpaceScriptTest {

    private val page = "<html><head></head><body><p>end</p></body></html>"

    @Test
    fun `TC#63 페이지 끝 여백은 dp 를 CSS px 로 바꾼 높이다`() {
        HtmlEngine(page).use { engine ->
            engine.eval(JsScripts.getBottomSpaceScript(heightDp = 136, viewWidthDp = 400f))
            val expected = engine.string("String(136 * window.innerWidth / 400)")
            assertEquals(
                "html::after{content:\"\";display:block;height:${expected}px}",
                engine.string("document.getElementById('pagetune-bottom-space').textContent")
            )
        }
    }

    @Test
    fun `TC#63 여러 번 적용해도 여백은 하나이고 마지막 높이를 쓴다`() {
        HtmlEngine(page).use { engine ->
            engine.eval(JsScripts.getBottomSpaceScript(heightDp = 136, viewWidthDp = 400f))
            engine.eval(JsScripts.getBottomSpaceScript(heightDp = 72, viewWidthDp = 400f))
            val expected = engine.string("String(72 * window.innerWidth / 400)")
            assertEquals(1, engine.int("document.querySelectorAll('#pagetune-bottom-space').length"))
            assertEquals(
                "html::after{content:\"\";display:block;height:${expected}px}",
                engine.string("document.getElementById('pagetune-bottom-space').textContent")
            )
        }
    }
}
