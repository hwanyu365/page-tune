package com.hwanyu365.pagetune

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.CheckBox
import android.widget.Spinner
import android.widget.TextView
import android.view.accessibility.AccessibilityManager
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.longClick
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PagesTabTest {

    @Before
    fun setUp() {
        resetApp()
    }

    @After
    fun tearDown() = restoreDeviceSettings()

    @Test
    @JvmName("tc101")
    fun `TC#101 웹페이지 목록은 최근 등록 순 카드이고 Tune 칩·카드 열람·오버플로 편집이 동작한다`() = listCards()

    @Test
    @JvmName("tc102")
    fun `TC#102 등록 다이얼로그는 빈 제목과 http(s) 아닌 주소를 저장하지 않고 주소를 정규화한다`() = dialogValidation()

    @Test
    @JvmName("tc103")
    fun `TC#103 선택 삭제는 개수를 묻고 취소하면 선택 모드를 유지하며 확인하면 지우고 스낵바를 보인다`() = deleteSelection()

    @Test
    @JvmName("tc104")
    fun `TC#104 추가·편집 다이얼로그 제목이 영어·한국어로 배경과 구분돼 보인다`() = dialogTitles()

    @Test
    @JvmName("tc105")
    fun `TC#105 웹페이지가 없으면 설명과 웹페이지 추가 버튼이 보이고 버튼이 등록 다이얼로그를 연다`() = emptyState()

    @Test
    @JvmName("tc129")
    fun `TC#129 Pages 탭 체크박스를 직접 눌러 삭제하면 1개 삭제 확인 다이얼로그가 뜬다`() = checkboxDelete()

    @Test
    @JvmName("tc131")
    fun `TC#131 카드의 아바타 글자·색과 호스트 줄은 호스트로 정해진다`() = avatars()

    @Test
    @JvmName("tc132")
    fun `TC#132 길게 누르면 그 카드가 선택된 선택 모드가 되고 닫기·뒤로가기가 끝낸다`() = selectionMode()

    @Test
    @JvmName("tc135a")
    fun `TC#135 실행취소하면 지운 웹페이지가 같은 위치·값으로 돌아오고 다시 열어도 남는다`() = undoRestores()

    @Test
    @JvmName("tc135b")
    fun `TC#135 스낵바가 뜬 동안 삭제는 DB 에 반영돼 있고 실행취소 없이 다시 열면 삭제가 남는다`() = deleteCommitted()

    @Test
    @JvmName("tc135c")
    fun `TC#135 오버플로 삭제 1건도 되돌릴 수 있고 스낵바가 사라지면 되살릴 수 없다`() = overflowUndoAndTimeout()

    @Test
    @JvmName("tc135d")
    fun `TC#135 탭 전환과 열람 화면 열기는 스낵바를 닫는다`() = leavingClosesUndo()

    @Test
    @JvmName("tc135e")
    fun `TC#135 접근성 조치 시간을 2분으로 두면 스낵바가 5초 넘게 남는다`() = accessibilityTimeout()

    private fun launchPages(): ActivityScenario<MainActivity> = ActivityScenario.launch(MainActivity::class.java).also {
        onView(pagesTab).perform(click())
    }

    private fun card(title: String) = allOf(withId(R.id.tv_title), withText(title), isDescendantOfA(withId(R.id.rv_webpages)))

    private fun titles() = shownNames(R.id.rv_webpages, R.id.tv_title)

    private fun pagesInDb() = runBlocking { database.webPageDao().getAllWebPagesWithPlugin() }.map { it.webPage }.sortedBy { it.id }

    // Espresso 는 대화상자 창이 없으면 한참 기다린 뒤에야 실패한다 → 창이 없는 것은 화면 창이 포커스를 되찾았는지로 본다.
    private fun mainWindowFocused(): Boolean {
        androidx.test.espresso.Espresso.onIdle()
        return onMain { resumedActivity().window.decorView.hasWindowFocus() }
    }

    private fun assertNoDialogOpens() {
        android.os.SystemClock.sleep(500)
        assertTrue("확인 창이 뜸", mainWindowFocused())
    }

    private fun deleteMessage(count: Int) = targetContext.resources.getQuantityString(R.plurals.webpage_delete_message, count, count)

    private fun deletedMessage(count: Int) = targetContext.resources.getQuantityString(R.plurals.webpage_deleted, count, count)

    private fun listCards() {
        LocalPage("<html><body>second</body></html>").use { page ->
            val tune = addTune("Tune A")
            val gone = addTune("Gone Tune")
            val first = addWebPage("First", "https://first.example/", tune)
            val second = addWebPage("Second", page.url)
            addWebPage("Third", "https://third.example/", gone)
            runBlocking { database.pluginDao().deletePluginsByIds(listOf(gone)) }
            launchPages().use {
                eventually { assertEquals(listOf("Third", "Second", "First"), titles()) }
                onView(allOf(withId(R.id.tv_plugin), inRowWith("First"))).check(matches(withText("Tune A")))
                onView(allOf(withId(R.id.tv_plugin), inRowWith("Second"))).check(matches(withText(R.string.webpage_item_tune_none)))
                onView(allOf(withId(R.id.tv_plugin), inRowWith("Third"))).check(matches(withText(R.string.webpage_item_tune_none)))

                onView(card("Second")).perform(click())
                eventually { assertTrue(resumedActivity() is WebBrowserActivity) }
                assertEquals(second, onMain { resumedActivity().intent.getLongExtra("webpage_id", -1) })
                onMain { resumedActivity().finish() }
                eventually { assertTrue(resumedActivity() is MainActivity) }

                openItemMenu(withText("First"))
                for (item in listOf(R.string.list_edit, R.string.delete)) {
                    onView(withText(item)).inRoot(isPlatformPopup()).check(matches(isDisplayed()))
                }
                onView(withText(R.string.list_edit)).inRoot(isPlatformPopup()).perform(click())
                onView(withId(R.id.tv_dialog_title)).inRoot(isDialog()).check(matches(withText(R.string.webpage_dialog_edit_title)))
                onView(withId(R.id.et_title)).inRoot(isDialog()).check(matches(withText("First")))
                onView(withId(R.id.et_url)).inRoot(isDialog()).check(matches(withText("https://first.example/")))
                eventually { val root = dialogRoot(); assertEquals("Tune A", onMain { root.findViewById<Spinner>(R.id.sp_plugins).selectedItem.toString() }) }
                assertEquals(first, pagesInDb().first().id)
            }
        }
    }

    private fun dialogValidation() {
        launchPages().use {
            onView(withId(R.id.fab_add_webpage)).perform(click())
            for ((title, url) in listOf("" to "example.com", "Title" to "")) {
                fill(title, url)
                onView(withId(R.id.tv_dialog_title)).inRoot(isDialog()).check(matches(isDisplayed()))
                assertEquals(emptyList<Any>(), pagesInDb())
            }

            for (bad in listOf("javascript:alert(1)", "file:///sdcard/a")) {
                fill("Bad", bad)
                eventually { val root = dialogRoot(); assertEquals(targetContext.getString(R.string.url_input_error), onMain { root.findViewById<TextInputLayout>(R.id.til_url).error?.toString() }) }
                assertEquals(emptyList<Any>(), pagesInDb())
            }

            fill("Plain", "example.com")
            eventually { assertEquals(listOf("https://example.com"), pagesInDb().map { it.url }) }
            eventually { assertTrue(mainWindowFocused()) }

            onView(withId(R.id.fab_add_webpage)).perform(click())
            fill("Upper", "HTTPS://Example.com")
            eventually { assertEquals(listOf("https://example.com", "https://Example.com"), pagesInDb().map { it.url }) }
        }
    }

    private fun fill(title: String, url: String) {
        onView(withId(R.id.et_title)).inRoot(isDialog()).perform(replaceText(title))
        onView(withId(R.id.et_url)).inRoot(isDialog()).perform(replaceText(url))
        onView(withId(R.id.btn_dialog_save)).inRoot(isDialog()).perform(click())
    }

    private fun deleteSelection() {
        listOf("One", "Two", "Three").forEach { addWebPage(it, "https://${it.lowercase()}.example/") }
        launchPages().use {
            eventually { assertEquals(listOf("Three", "Two", "One"), titles()) }
            onView(withId(R.id.action_delete_mode)).perform(click())
            onView(withId(R.id.action_delete_selected)).perform(click())
            assertNoDialogOpens()
            assertEquals(3, pagesInDb().size)

            tapOnce(card("One"))
            tapOnce(card("Three"))
            eventually { assertSelectedCount(2) }
            onView(withId(R.id.action_delete_selected)).perform(click())
            onView(withText(deleteMessage(2))).inRoot(isDialog()).check(matches(isDisplayed()))
            onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())
            eventually { assertSelectedCount(2) }
            assertEquals(3, pagesInDb().size)

            onView(withId(R.id.action_delete_selected)).perform(click())
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
            eventually { assertEquals(listOf("Two"), titles()) }
            onView(withText(deletedMessage(2))).check(matches(isDisplayed()))
            assertEquals(listOf("Two"), pagesInDb().map { it.title })
        }
    }

    private fun dialogTitles() {
        addWebPage("Page", "https://page.example/")
        val locales = "cmd locale set-app-locales ${targetContext.packageName} --user 0"
        changeDeviceSetting("$locales --locales en", restore = locales)
        val seen = mutableListOf<String>()
        for (tag in listOf("en", "ko")) {
            shell("$locales --locales $tag")
            waitUntil { targetContext.applicationContext.resources.configuration.locales[0].language == tag }
            launchPages().use {
                onView(withId(R.id.fab_add_webpage)).perform(click())
                seen += readableTitle(R.string.webpage_dialog_add_title)
                onView(withId(R.id.btn_dialog_cancel)).inRoot(isDialog()).perform(click())
                eventually { openItemMenu(withText("Page")) }
                onView(withText(R.string.list_edit)).inRoot(isPlatformPopup()).perform(click())
                seen += readableTitle(R.string.webpage_dialog_edit_title)
                onView(withId(R.id.btn_dialog_cancel)).inRoot(isDialog()).perform(click())
            }
        }
        assertNotEquals("영어·한국어 제목이 같음", seen.take(2), seen.drop(2))
    }

    private fun readableTitle(title: Int): String = eventually {
        val root = dialogRoot()
        onMain {
            val view = root.findViewById<TextView>(R.id.tv_dialog_title)
            assertEquals(root.context.getString(title), view.text.toString())
            assertTrue("제목이 보이지 않음", view.isShown && view.width > 0)
            val shot = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(shot))
            val at = IntArray(2).also { view.getLocationInWindow(it) }
            val origin = IntArray(2).also { root.getLocationInWindow(it) }
            val gap = (8 * Resources.getSystem().displayMetrics.density).toInt()
            val background = shot.getPixel(at[0] - origin[0] + view.width + gap, at[1] - origin[1] + view.height / 2)
            val contrast = ColorUtils.calculateContrast(view.currentTextColor, background or 0xFF000000.toInt())
            assertTrue("제목 대비 $contrast", contrast >= 4.5)
            view.text.toString()
        }
    }

    private fun emptyState() {
        launchPages().use {
            eventually { onView(allOf(withId(R.id.layout_empty), isDisplayed())).check(matches(isDisplayed())) }
            onView(allOf(withText(R.string.empty_webpages_message), isDisplayed())).check(matches(isDisplayed()))
            onView(allOf(withId(R.id.btn_empty_action), isDisplayed())).check(matches(withText(R.string.empty_webpages_action))).perform(click())
            onView(withId(R.id.tv_dialog_title)).inRoot(isDialog()).check(matches(withText(R.string.webpage_dialog_add_title)))
            onView(withId(R.id.et_title)).inRoot(isDialog()).check(matches(withText("")))
        }
    }

    private fun checkboxDelete() {
        addWebPage("Only", "https://only.example/")
        addWebPage("Other", "https://other.example/")
        launchPages().use {
            eventually { onView(card("Only")).check(matches(isDisplayed())) }
            onView(withId(R.id.action_delete_mode)).perform(click())
            tapOnce(allOf(withId(R.id.chk_delete), inRowWith("Only"), isDisplayed()))
            eventually { assertSelectedCount(1) }
            onView(withId(R.id.action_delete_selected)).perform(click())
            onView(withText(deleteMessage(1))).inRoot(isDialog()).check(matches(isDisplayed()))
        }
    }

    private fun avatars() {
        addWebPage("News", "https://www.example.org/news/1")
        addWebPage("Blog", "https://example.org/b")
        addWebPage("Other", "https://other.example/")
        launchPages().use {
            eventually { assertEquals(3, titles().size) }
            val rows = onMain {
                listOf("News", "Blog", "Other").associateWith { title ->
                    val row = resumedActivity().findViewById<View>(R.id.rv_webpages).shownViews()
                        .first { it is TextView && it.id == R.id.tv_title && it.text.toString() == title }.let { rowOf(it) }
                    Triple(
                        row.findViewById<TextView>(R.id.tv_avatar).text.toString(),
                        row.findViewById<TextView>(R.id.tv_host).text.toString(),
                        ViewCompat.getBackgroundTintList(row.findViewById(R.id.avatar))!!.defaultColor,
                    )
                }
            }
            assertEquals(listOf("E", "example.org"), rows.getValue("News").toList().take(2))
            assertEquals(listOf("E", "example.org"), rows.getValue("Blog").toList().take(2))
            assertEquals(listOf("O", "other.example"), rows.getValue("Other").toList().take(2))
            assertEquals(rows.getValue("News").third, rows.getValue("Blog").third)
            val palette = listOf(R.color.avatar1, R.color.avatar2, R.color.avatar3, R.color.avatar4, R.color.avatar5, R.color.avatar6, R.color.avatar7, R.color.avatar8)
                .map { onMain { resumedActivity().getColor(it) } }
            assertTrue(rows.values.all { it.third in palette })
        }
    }

    private fun rowOf(view: View): View {
        var row = view
        while ((row.parent as View).id != R.id.rv_webpages) row = row.parent as View
        return row
    }

    private fun selectionMode() {
        listOf("One", "Two", "Three").forEach { addWebPage(it, "https://${it.lowercase()}.example/") }
        launchPages().use {
            eventually { assertEquals(listOf("Three", "Two", "One"), titles()) }
            onView(card("Two")).perform(longClick())
            eventually { assertSelectedCount(1) }
            assertTrue(onMain { checked("Two") })
            tapOnce(card("One"))
            eventually { assertSelectedCount(2) }
            selectAll()
            eventually { assertSelectedCount(3) }
            onView(withId(androidx.appcompat.R.id.action_mode_close_button)).perform(click())
            eventually { onView(withId(R.id.action_delete_selected)).check(doesNotExist()) }

            onView(withId(R.id.action_delete_mode)).perform(click())
            eventually { assertSelectedCount(0) }
            pressBack()
            eventually { onView(withId(R.id.action_delete_selected)).check(doesNotExist()) }
            assertTrue(resumedActivity() is MainActivity)
        }
    }

    // 상단 바에 자리가 모자라면 전체 선택은 오버플로 메뉴로 간다 (FR#3).
    private fun selectAll() {
        val shown = runCatching { onView(withId(R.id.action_select_all)).perform(click()) }
        if (shown.isFailure) {
            androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu(targetContext)
            onView(withText(R.string.list_select_all)).perform(click())
        }
    }

    private fun checked(title: String): Boolean {
        val label = resumedActivity().findViewById<View>(R.id.rv_webpages).shownViews().first { it is TextView && it.id == R.id.tv_title && it.text.toString() == title }
        return rowOf(label).findViewById<CheckBox>(R.id.chk_delete).isChecked
    }

    private fun seedThree(): List<Long> {
        val tune = addTune("Linked")
        return listOf(addWebPage("One", "https://one.example/"), addWebPage("Two", "https://two.example/", tune), addWebPage("Three", "https://three.example/"))
    }

    private fun deleteTwoAndThree() {
        eventually { assertEquals(listOf("Three", "Two", "One"), titles()) }
        onView(card("Two")).perform(longClick())
        tapOnce(card("Three"))
        eventually { assertSelectedCount(2) }
        onView(withId(R.id.action_delete_selected)).perform(click())
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
        eventually { onView(withText(R.string.list_undo)).check(matches(isDisplayed())) }
    }

    private fun undoRestores() {
        seedThree()
        val before = pagesInDb()
        launchPages().use {
            deleteTwoAndThree()
            onView(withText(R.string.list_undo)).perform(click())
            eventually { assertEquals(listOf("Three", "Two", "One"), titles()) }
            onView(allOf(withId(R.id.tv_plugin), inRowWith("Two"))).check(matches(withText("Linked")))
            assertEquals(before, pagesInDb())
        }
        launchPages().use {
            eventually { assertEquals(listOf("Three", "Two", "One"), titles()) }
            onView(allOf(withId(R.id.tv_plugin), inRowWith("Two"))).check(matches(withText("Linked")))
        }
        assertEquals(before, pagesInDb())
    }

    private fun deleteCommitted() {
        val ids = seedThree()
        launchPages().use {
            deleteTwoAndThree()
            assertEquals(listOf(ids[0]), pagesInDb().map { it.id })
        }
        launchPages().use {
            eventually { assertEquals(listOf("One"), titles()) }
            onView(withText(R.string.list_undo)).check(doesNotExist())
        }
        assertEquals(listOf(ids[0]), pagesInDb().map { it.id })
    }

    private fun overflowUndoAndTimeout() {
        seedThree()
        val before = pagesInDb()
        launchPages().use {
            eventually { assertEquals(3, titles().size) }
            openItemMenu(withText("Two"))
            onView(withText(R.string.delete)).inRoot(isPlatformPopup()).perform(click())
            onView(withText(deleteMessage(1))).inRoot(isDialog()).check(matches(isDisplayed()))
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
            eventually { onView(withText(deletedMessage(1))).check(matches(isDisplayed())) }
            assertEquals(listOf("One", "Three"), pagesInDb().map { it.title })
            onView(withText(R.string.list_undo)).perform(click())
            eventually { assertEquals(listOf("Three", "Two", "One"), titles()) }
            assertEquals(before, pagesInDb())
        }
        launchPages().use {
            eventually { assertEquals(listOf("Three", "Two", "One"), titles()) }
            assertEquals(before, pagesInDb())

            deleteFromItemMenu(withText("Two"))
            eventually { onView(withText(R.string.list_undo)).check(matches(isDisplayed())) }
            eventually(timeoutMs = 15_000) { assertTrue(snackbarGone()) }
            assertEquals(listOf("Three", "One"), titles())
            assertEquals(listOf("One", "Three"), pagesInDb().map { it.title })
        }
    }

    private fun leavingClosesUndo() {
        LocalPage("<html><body>page</body></html>").use { page ->
            addWebPage("One", page.url)
            addWebPage("Two", "https://two.example/")
            addWebPage("Three", "https://three.example/")
            launchPages().use {
                eventually { assertEquals(3, titles().size) }
                deleteFromItemMenu(withText("Three"))
                eventually { onView(withText(R.string.list_undo)).check(matches(isDisplayed())) }
                onView(tunesTab).perform(click())
                eventually { assertTrue(snackbarGone()) }
                onView(pagesTab).perform(click())
                eventually { assertEquals(listOf("Two", "One"), titles()) }
                assertTrue(snackbarGone())

                deleteFromItemMenu(withText("Two"))
                eventually { onView(withText(R.string.list_undo)).check(matches(isDisplayed())) }
                onView(card("One")).perform(click())
                eventually { assertTrue(resumedActivity() is WebBrowserActivity) }
                onMain { resumedActivity().finish() }
                eventually { assertTrue(resumedActivity() is MainActivity) }
                eventually { assertEquals(listOf("One"), titles()) }
                assertTrue(snackbarGone())
            }
        }
    }

    private fun accessibilityTimeout() {
        org.junit.Assume.assumeTrue("권장 시간 설정은 API 29 부터", android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q)
        val key = "accessibility_interactive_ui_timeout_ms"
        val old = shell("settings get secure $key").trim()
        changeDeviceSetting("settings put secure $key 120000", restore = if (old == "null") "settings delete secure $key" else "settings put secure $key $old")
        val manager = targetContext.getSystemService(AccessibilityManager::class.java)
        waitUntil { manager.getRecommendedTimeoutMillis(5000, AccessibilityManager.FLAG_CONTENT_TEXT or AccessibilityManager.FLAG_CONTENT_CONTROLS) == 120_000 }
        seedThree()
        launchPages().use {
            eventually { assertEquals(3, titles().size) }
            deleteFromItemMenu(withText("Two"))
            eventually { onView(withText(R.string.list_undo)).check(matches(isDisplayed())) }
            android.os.SystemClock.sleep(8_000)
            onView(withText(R.string.list_undo)).check(matches(isDisplayed()))
        }
    }
}
