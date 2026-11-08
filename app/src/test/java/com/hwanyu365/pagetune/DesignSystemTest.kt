package com.hwanyu365.pagetune

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import org.w3c.dom.Node

class DesignSystemTest {

    private val res = File("src/main/res")
    private val kotlinSources = File("src/main/java").walk().filter { it.extension == "kt" }.toList()

    private fun parse(file: File): Element =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }.newDocumentBuilder().parse(file).documentElement

    private fun Element.descendants(): Sequence<Element> = sequence {
        val children = childNodes
        for (i in 0 until children.length) {
            val n = children.item(i)
            if (n.nodeType == Node.ELEMENT_NODE) {
                yield(n as Element)
                yieldAll(n.descendants())
            }
        }
    }

    private fun Element.attrs(): List<Pair<String, String>> =
        (0 until attributes.length).map { attributes.item(it).nodeName to attributes.item(it).nodeValue }

    private fun xmlFiles(dir: String) = File(res, dir).listFiles().orEmpty().filter { it.extension == "xml" }

    private val layouts get() = xmlFiles("layout")

    private val tokens = mapOf(
        "primary" to "#ff4103",
        "onPrimary" to "#171717",
        "brandOnSurface" to "#ff7347",
        "background" to "#171717",
        "surface" to "#171717",
        "surfaceContainerLow" to "#1e1e1e",
        "surfaceContainer" to "#242424",
        "surfaceContainerHigh" to "#2a2a2a",
        "onSurface" to "#E0E0E0",
        "onSurfaceVariant" to "#9E9E9E",
        "outline" to "#8a8a8a",
        "outlineVariant" to "#2a2a2a",
        "error" to "#ffb4ab",
        "onError" to "#690005",
        "scrim" to "#D9171717",
        "avatar1" to "#ff7347",
        "avatar2" to "#7cb7ff",
        "avatar3" to "#6fd3a8",
        "avatar4" to "#f5c451",
        "avatar5" to "#c79bff",
        "avatar6" to "#ff8fb1",
        "avatar7" to "#5fd0e0",
        "avatar8" to "#b5d96b",
    )

    private fun colorResources(): Map<String, String> =
        File(res, "values").listFiles().orEmpty().filter { it.extension == "xml" }.flatMap { f ->
            parse(f).descendants().filter { it.tagName == "color" }.map { it.getAttribute("name") to it.textContent.trim() }.toList()
        }.toMap()

    private val styleElements = File(res, "values").listFiles().orEmpty().filter { it.extension == "xml" }
        .flatMap { f -> parse(f).descendants().filter { it.tagName == "style" }.toList() }
        .associateBy { it.getAttribute("name") }

    private val styles = styleElements.mapValues { it.value.getAttribute("parent") }

    // parent="" 는 부모가 없다는 뜻이고, parent 속성이 없으면 이름의 점 앞부분이 부모다.
    private fun parentOf(name: String): String {
        val e = styleElements.getValue(name)
        return if (e.hasAttribute("parent")) e.getAttribute("parent").removePrefix("@style/") else name.substringBeforeLast('.', "")
    }

    private fun rootOf(name: String): String {
        var cur = name
        while (cur in styles) cur = parentOf(cur)
        return cur
    }

    @Test
    fun `TC#1 모든 화면 테마는 Theme_Material3_Dark 를 최상위 부모로 둔 앱 테마 계열이다`() {
        val manifest = parse(File("src/main/AndroidManifest.xml"))
        val application = manifest.descendants().first { it.tagName == "application" }
        val appTheme = application.getAttribute("android:theme").removePrefix("@style/")
        val themes = listOf(appTheme) + manifest.descendants().filter { it.tagName == "activity" }
            .map { it.getAttribute("android:theme").removePrefix("@style/").ifEmpty { appTheme } }

        for (theme in themes) {
            assertTrue("$theme 는 앱 테마 계열이 아니다", theme == appTheme || theme.startsWith("$appTheme."))
            assertTrue("$theme 의 최상위 부모 ${rootOf(theme)}", rootOf(theme).startsWith("Theme.Material3.Dark"))
        }
        assertEquals(emptyList<String>(), styles.filterValues { it.removePrefix("@style/").startsWith("Theme.AppCompat") }.keys.toList())
    }

    @Test
    fun `TC#2 색 리소스는 토큰과 아이콘 배경색뿐이고 값이 스펙과 같다`() {
        assertEquals(tokens + ("ic_launcher_background" to "#171717"), colorResources())
        assertEquals("color 상태 목록도 색 리소스다", emptyList<File>(), res.listFiles().orEmpty().filter { it.name.startsWith("color") })
    }

    @Test
    fun `TC#2 레이아웃·메뉴·drawable 에 색 리터럴이 없다`() {
        val drawables = xmlFiles("drawable").filter { !it.name.startsWith("ic_launcher") && parse(it).tagName != "vector" }
        val offenders = (layouts + xmlFiles("menu") + drawables).flatMap { f ->
            listOf(parse(f)).plus(parse(f).descendants()).flatMap { it.attrs() }
                .filter { (_, v) -> Regex("""#[0-9A-Fa-f]{3,8}\b""").containsMatchIn(v) }
                .map { (k, v) -> "${f.name} $k=$v" }
        }

        assertEquals(emptyList<String>(), offenders)
    }

    private val colorRules = mapOf(
        "parseColor" to Regex("""Color\.parseColor\(\s*"#"""),
        "rgb" to Regex("""Color\.rgb\("""),
        "argb" to Regex("""Color\.argb\("""),
        "intLiteral" to Regex("""\b0x[0-9A-Fa-f]{8}\b"""),
        "constant" to Regex("""\bColor\.[A-Z][A-Z_]*\b"""),
        "hexString" to Regex("""#[0-9A-Fa-f]{6}\b"""),
    )

    // 주입 스크립트는 페이지 안의 색이고, 색 선택 다이얼로그는 사용자가 고른 색을 계산하고, 프리셋은 페이지에 칠할 색이다 → 앱 화면 색이 아니다.
    private val colorAllowlist = mapOf(
        "JsScripts.kt" to colorRules.keys,
        "ColorPickerDialog.kt" to setOf("rgb", "constant"),
        "ColorPresets.kt" to setOf("hexString"),
    )

    @Test
    fun `TC#2 Kotlin 은 허용 목록 밖에서 색을 직접 지정하지 않는다`() {
        val offenders = kotlinSources.flatMap { f ->
            val allowed = colorAllowlist[f.name].orEmpty()
            f.readLines().withIndex().flatMap { (i, line) ->
                val code = if (line.trimStart().startsWith("*")) "" else line.replace(Regex("""(^|\s)//.*$"""), "")
                colorRules.filterKeys { it !in allowed }.filterValues { it.containsMatchIn(code) }.keys.map { "${f.name}:${i + 1} $it" }
            }
        }

        assertEquals(emptyList<String>(), offenders)
    }

    private fun channel(c: Int): Double = (c / 255.0).let { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }

    private fun rgb(hex: String): Triple<Int, Int, Int> {
        val h = hex.removePrefix("#").takeLast(6)
        return Triple(h.substring(0, 2).toInt(16), h.substring(2, 4).toInt(16), h.substring(4, 6).toInt(16))
    }

    private fun luminance(c: Triple<Int, Int, Int>) = 0.2126 * channel(c.first) + 0.7152 * channel(c.second) + 0.0722 * channel(c.third)

    private fun ratio(a: Triple<Int, Int, Int>, b: Triple<Int, Int, Int>): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun overWhite(argb: String): Triple<Int, Int, Int> {
        val h = argb.removePrefix("#")
        assertEquals("scrim 은 ARGB 8자리", 8, h.length)
        val a = h.substring(0, 2).toInt(16) / 255.0
        val (r, g, b) = rgb(h)
        fun mix(c: Int) = Math.round(c * a + 255 * (1 - a)).toInt()
        return Triple(mix(r), mix(g), mix(b))
    }

    @Test
    fun `TC#3 토큰 조합의 대비가 글자 4_5, 비텍스트 3 이상이다`() {
        val c = colorResources()
        fun t(name: String) = rgb(c.getValue(name))
        val surfaces = listOf("surface", "surfaceContainerLow", "surfaceContainer", "surfaceContainerHigh")
        val pairs = buildList {
            add(Triple("onPrimary", "primary", 4.5))
            for (fg in listOf("onSurface", "onSurfaceVariant", "brandOnSurface", "error")) for (bg in surfaces) add(Triple(fg, bg, 4.5))
            for (bg in surfaces) add(Triple("outline", bg, 3.0))
            add(Triple("onError", "error", 4.5))
            for (bg in listOf("surface", "surfaceContainerHigh")) add(Triple("primary", bg, 3.0))
            for (i in 1..8) add(Triple("onPrimary", "avatar$i", 4.5))
        }

        val failures = pairs.map { (fg, bg, min) -> Triple("$fg/$bg", ratio(t(fg), t(bg)), min) }.filter { it.second < it.third } +
            listOf(Triple("onSurface/scrim over white", ratio(t("onSurface"), overWhite(c.getValue("scrim"))), 4.5)).filter { it.second < it.third }

        assertEquals(emptyList<Triple<String, Double, Double>>(), failures)
    }

    @Test
    fun `TC#3 스낵바 액션·메시지 글자는 스낵바 배경 대비 4_5 이상이다`() {
        val c = colorResources()
        val theme = styleElements.getValue("AppTheme").descendants().filter { it.tagName == "item" }
            .associate { it.getAttribute("name") to it.textContent.trim().removePrefix("@color/") }
        fun token(attr: String) = rgb(c.getValue(theme.getValue(attr)))
        val background = token("colorSurfaceInverse")

        for (fg in listOf("colorPrimaryInverse", "colorOnSurfaceInverse")) {
            val r = ratio(token(fg), background)
            assertTrue("$fg / colorSurfaceInverse = $r", r >= 4.5)
        }
    }

    @Test
    fun `TC#4 레이아웃과 Kotlin 은 글자 크기를 직접 지정하지 않는다`() {
        val inLayouts = layouts.filter { "android:textSize" in it.readText() }.map { it.name }
        val inKotlin = kotlinSources.filter { Regex("""\btextSize\s*=|setTextSize\(""").containsMatchIn(it.readText()) }.map { it.name }

        assertEquals(emptyList<String>(), inLayouts + inKotlin)
    }

    @Test
    fun `TC#5 레이아웃 여백은 0dp 또는 간격 스케일 값이다`() {
        val scale = setOf("0dp", "4dp", "8dp", "12dp", "16dp", "24dp")
        val offenders = layouts.flatMap { f ->
            parse(f).let { listOf(it) + it.descendants() }.flatMap { it.attrs() }
                .filter { (k, v) -> (k.startsWith("android:layout_margin") || k.startsWith("android:padding")) && v !in scale }
                .map { (k, v) -> "${f.name} $k=$v" }
        }

        assertEquals(emptyList<String>(), offenders)
    }

    @Test
    fun `TC#6 레이아웃은 교체 대상 위젯과 단독 편집칸을 쓰지 않는다`() {
        val banned = setOf("androidx.appcompat.widget.Toolbar", "androidx.cardview.widget.CardView")
        val editTexts = setOf("EditText", "com.hwanyu365.pagetune.ui.view.NoAutofillEditText", "com.google.android.material.textfield.TextInputEditText")
        val offenders = layouts.flatMap { f ->
            val root = parse(f)
            (listOf(root) + root.descendants()).mapNotNull { e ->
                val parentTag = (e.parentNode as? Element)?.tagName
                when {
                    e.tagName in banned -> "${f.name} ${e.tagName}"
                    e.tagName in editTexts && parentTag != "com.google.android.material.textfield.TextInputLayout" -> "${f.name} 단독 ${e.tagName}"
                    else -> null
                }
            }
        }

        assertEquals(emptyList<String>(), offenders)
    }

    @Test
    fun `TC#6 Kotlin 은 플랫폼·AppCompat AlertDialog 를 쓰지 않는다`() {
        val offenders = kotlinSources.filter { Regex("""android\.app\.AlertDialog|androidx\.appcompat\.app\.AlertDialog""").containsMatchIn(it.readText()) }.map { it.name }

        assertEquals(emptyList<String>(), offenders)
    }

    @Test
    fun `TC#13 위젯 스타일의 materialThemeOverlay 는 그 위젯의 Material3 overlay 를 부모로 둔다`() {
        val expected = mapOf(
            "Widget.Material3.Button" to "ThemeOverlay.Material3.Button",
            "Widget.Material3.TextInputLayout" to "ThemeOverlay.Material3.TextInputEditText",
            "Widget.Material3.TabLayout" to "ThemeOverlay.Material3.TabLayout",
        )
        val checked = mutableListOf<String>()
        val offenders = styleElements.mapNotNull { (name, e) ->
            val overlay = e.descendants().firstOrNull { it.tagName == "item" && it.getAttribute("name") == "materialThemeOverlay" }
                ?.textContent?.trim()?.removePrefix("@style/") ?: return@mapNotNull null
            val widget = expected.keys.firstOrNull { rootOf(name).startsWith(it) } ?: return@mapNotNull null
            checked += name
            val root = rootOf(overlay)
            "$name → $overlay → '$root'".takeUnless { root.startsWith(expected.getValue(widget)) }
        }

        assertTrue("검사 대상 스타일이 없다", checked.isNotEmpty())
        assertEquals(emptyList<String>(), offenders)
    }
}
