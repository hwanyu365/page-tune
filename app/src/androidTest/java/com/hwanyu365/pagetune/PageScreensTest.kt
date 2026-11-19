package com.hwanyu365.pagetune

import android.content.Intent
import android.content.res.Configuration
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.textfield.TextInputLayout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class PageScreensTest {

    private val problems = mutableListOf<String>()
    private val checked = mutableListOf<String>()

    @Before
    fun setUp() = resetApp()

    @After
    fun tearDown() = restoreDeviceSettings()

    @Test
    @JvmName("tc124")
    fun `TC#124 Pages 탭·다이얼로그·열람 화면·빠른 메뉴의 문구는 기기 언어의 문자열 리소스다`() = texts()

    @Test
    @JvmName("tc125")
    fun `TC#125 Pages 탭·다이얼로그·빠른 메뉴의 누를 수 있는 요소는 48dp 이상이고 아이콘만 있으면 라벨이 있으며 글꼴 200% 에서 잘리지 않는다`() = targets()

    private fun texts() = inLocales(listOf("1.0")) { tag, _, page -> visitScreens(page) { screen, root -> checkTexts(tag, screen, root) } }

    private fun targets() = inLocales(listOf("1.0", "2.0")) { tag, scale, page ->
        visitScreens(page) { screen, root ->
            if (scale == "1.0") checkTargets(tag, screen, root) else problems += layoutProblemsOnMain(root, "$tag ${scale}x $screen")
        }
    }

    private fun inLocales(scales: List<String>, check: (String, String, String) -> Unit) {
        val oldScale = shell("settings get system font_scale").trim()
        changeDeviceSetting("settings put system font_scale 1.0", restore = if (oldScale == "null") "settings delete system font_scale" else "settings put system font_scale $oldScale")
        val locales = "cmd locale set-app-locales ${targetContext.packageName} --user 0"
        changeDeviceSetting("$locales --locales en", restore = locales)
        LocalPage("<html><body style=\"background:#fff\"><p>page</p><a id=\"prev\" href=\"/p\">p</a></body></html>").use { page ->
            for (scale in scales) {
                shell("settings put system font_scale $scale")
                waitUntil { android.content.res.Resources.getSystem().configuration.fontScale == scale.toFloat() }
                for (tag in listOf("en", "ko")) {
                    shell("$locales --locales $tag")
                    waitUntil { targetContext.applicationContext.resources.configuration.locales[0].language == tag }
                    check(tag, scale, page.url)
                }
            }
        }
        assertEquals(checked.toString(), scales.size * 2 * SCREENS, checked.size)
        assertEquals(emptyList<String>(), problems)
    }

    private fun visitScreens(pageUrl: String, check: (String, View) -> Unit) {
        clearData()
        markIntroSeen()
        val tune = addTune(TUNE, listOf(clickRule("gnb_prev", """{"selector":"#prev","tagName":"a","id":"prev","attributes":{}}""")))
        val pageId = addWebPage(PAGE, pageUrl, tune)
        ActivityScenario.launch(MainActivity::class.java).use {
            eventually { onView(withText(PAGE)).check(matches(isDisplayed())) }
            check("pages", onMain { resumedActivity().window.decorView })
            checked += "pages"
            openItemMenu(withText(PAGE))
            check("item_menu", captureRoot(isPlatformPopup()))
            checked += "item_menu"
            onView(withText(R.string.list_edit)).inRoot(isPlatformPopup()).perform(click())
            check("edit_dialog", dialogRoot())
            checked += "edit_dialog"
            onView(withId(R.id.btn_dialog_cancel)).inRoot(isDialog()).perform(click())
            openItemMenu(withText(PAGE))
            onView(withText(R.string.delete)).inRoot(isPlatformPopup()).perform(click())
            check("delete_dialog", dialogRoot())
            checked += "delete_dialog"
            onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())
            onView(withId(R.id.fab_add_webpage)).perform(click())
            check("add_dialog", dialogRoot())
            checked += "add_dialog"
            onView(withId(R.id.btn_dialog_cancel)).inRoot(isDialog()).perform(click())
        }
        val viewer = Intent(targetContext, WebBrowserActivity::class.java).putExtra("webpage_id", pageId)
        ActivityScenario.launch<WebBrowserActivity>(viewer).use {
            eventually { onView(withId(R.id.layout_gnb_stack)).check(matches(isDisplayed())) }
            eventually { onView(withId(R.id.tv_tune_status)).check(matches(isDisplayed())) }
            tapQuickHandle()
            onView(withId(R.id.layout_quick_toolbar)).check(matches(isDisplayed()))
            check("viewer_quick_menu", onMain { resumedActivity().window.decorView })
            checked += "viewer_quick_menu"
        }
    }

    // 사용자가 넣은 제목·주소·Tune 이름과 그것으로 만든 아바타 글자는 문자열 리소스가 아니다.
    private fun isUserData(v: View): Boolean = v is EditText || v.id in listOf(R.id.tv_title, R.id.tv_host, R.id.tv_avatar) ||
        (v is TextView && v.text.toString() in listOf(PAGE, TUNE))

    private fun checkTexts(tag: String, screen: String, root: View) {
        val allowed = resourceTexts(tag)
        fun ok(text: String) = allowed.any { it.matches(text) }
        onMain {
            for (v in root.shownViews()) {
                val texts = mutableListOf<String>()
                if (v is TextView && !isUserData(v) && v.text.isNotBlank()) texts += v.text.toString()
                if (v is TextInputLayout) v.hint?.let { texts += it.toString() }
                v.contentDescription?.takeIf { it.isNotBlank() && !isUserData(v) }?.let { texts += it.toString() }
                for (text in texts) if (!ok(text)) problems += "$tag $screen ${name(v)} '$text' 이 $tag 문자열 리소스가 아님"
            }
        }
    }

    private fun checkTargets(tag: String, screen: String, root: View) = onMain {
        val min = 48 * root.resources.displayMetrics.density
        for (v in root.shownViews()) {
            // 팝업 메뉴 행은 목록이 터치를 받아 행 자체는 누를 수 있는 view 가 아니다.
            val listRow = v.parent is android.widget.AbsListView
            if (!v.isClickable && !v.isLongClickable && !listRow) continue
            if (v.width + 0.5f < min || v.height + 0.5f < min) problems += "$tag $screen ${name(v)} ${v.width}x${v.height}px < 48dp"
            // 입력칸은 TextInputLayout 의 힌트가 라벨이다.
            val iconOnly = v is ImageView || (v is android.widget.Button && v.text.isBlank())
            if (iconOnly && v.contentDescription.isNullOrBlank()) problems += "$tag $screen ${name(v)} 아이콘에 라벨 없음"
        }
    }

    private fun resourceTexts(tag: String): List<Regex> {
        val config = Configuration(targetContext.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
        val res = targetContext.createConfigurationContext(config).resources
        // 앱 R 에는 라이브러리 문자열이 없다 → 화면에 쓰이는 라이브러리(AppCompat·Material)의 R 도 읽는다.
        val strings = listOf(R.string::class.java, androidx.appcompat.R.string::class.java, com.google.android.material.R.string::class.java)
            .flatMap { it.fields.toList() }
            .mapNotNull { field -> runCatching { res.getString(field.getInt(null)) }.getOrNull() }
        val plurals = R.plurals::class.java.fields.flatMap { field ->
            listOf(1, 2, 5).mapNotNull { n -> runCatching { res.getQuantityString(field.getInt(null), n) }.getOrNull() }
        }
        return (strings + plurals).distinct().mapNotNull { value ->
            // 서식 자리(%s, %1$d 등)는 무엇이 들어와도 된다.
            val parts = value.split(Regex("""%(\d+\$)?[sd]"""))
            // 서식 자리만 있는 리소스(e.g. "%1$s %2$s")는 어떤 문구와도 맞아 언어를 가리지 못한다.
            if (parts.size > 1 && parts.none { part -> part.any(Char::isLetter) }) return@mapNotNull null
            Regex(parts.joinToString(".+") { Regex.escape(it) })
        }
    }

    private fun name(v: View) = if (v.id != View.NO_ID) runCatching { v.resources.getResourceEntryName(v.id) }.getOrDefault(v.javaClass.simpleName) else v.javaClass.simpleName

    private companion object {
        const val PAGE = "Page A"
        const val TUNE = "Tune A"
        const val SCREENS = 6
    }
}
