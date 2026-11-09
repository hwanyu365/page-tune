package com.hwanyu365.pagetune

import java.io.File
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IconSafeZoneTest {

    private val res = File("src/main/res")
    private val center = 54.0
    private val safeRadius = 33.0

    private fun attr(file: String, name: String): String =
        Regex("""android:$name="([^"]*)"""").find(File(res, file).readText())!!.groupValues[1]

    // 절대 좌표 M·L·C 만 지원한다. 다른 명령이 들어오면 실패시켜 계산이 조용히 틀어지지 않게 한다.
    private fun maxRadius(file: String): Double {
        val tokens = Regex("""[MLC]|-?\d+(\.\d+)?""").findAll(attr(file, "pathData")).map { it.value }.toList()
        assertEquals("지원하지 않는 path 명령", attr(file, "pathData").count { it.isLetter() }, tokens.count { it[0].isLetter() })
        val pts = mutableListOf<Pair<Double, Double>>()
        var i = 0
        var cmd = ""
        var cur = 0.0 to 0.0
        fun num() = tokens[i++].toDouble()
        while (i < tokens.size) {
            if (tokens[i][0].isLetter()) cmd = tokens[i++]
            when (cmd) {
                "M", "L" -> { cur = num() to num(); pts += cur }
                "C" -> {
                    val c1 = num() to num(); val c2 = num() to num(); val p3 = num() to num()
                    for (s in 1..50) {
                        val t = s / 50.0; val u = 1 - t
                        fun f(a: Double, b: Double, c: Double, d: Double) = u * u * u * a + 3 * u * u * t * b + 3 * u * t * t * c + t * t * t * d
                        pts += f(cur.first, c1.first, c2.first, p3.first) to f(cur.second, c1.second, c2.second, p3.second)
                    }
                    cur = p3
                }
            }
        }
        return pts.maxOf { hypot(it.first - center, it.second - center) } + attr(file, "strokeWidth").toDouble() / 2
    }

    @Test
    fun `TC#410 foreground·monochrome 는 획 끝까지 안전 영역 반지름 이내다`() {
        for (f in listOf("drawable/ic_launcher_foreground.xml", "drawable/ic_launcher_monochrome.xml")) {
            val r = maxRadius(f)
            assertTrue("$f 최대 반지름 $r > $safeRadius", r <= safeRadius)
        }
    }

    @Test
    fun `TC#411 foreground 와 monochrome 은 같은 도형이다`() {
        assertEquals(
            attr("drawable/ic_launcher_foreground.xml", "pathData"),
            attr("drawable/ic_launcher_monochrome.xml", "pathData"),
        )
    }

    @Test
    fun `TC#412 아이콘 배경은 전용 색 리소스이고 값이 171717 이다`() {
        val background = Regex("""<background android:drawable="([^"]*)"""")
            .find(File(res, "mipmap-anydpi-v26/ic_launcher.xml").readText())!!.groupValues[1]
        assertEquals("@color/ic_launcher_background", background)
        assertTrue(Regex("""<color name="ic_launcher_background">#171717</color>""").containsMatchIn(File(res, "values/ic_launcher_background.xml").readText()))
    }
}
