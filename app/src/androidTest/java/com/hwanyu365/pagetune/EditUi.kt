package com.hwanyu365.pagetune

import android.view.View
import android.webkit.WebView
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

// 시트 안 칸은 스크롤해야 보이고, 시트 밖 칸은 스크롤 대상이 아니다.
// 접힌 시트 안 칸은 화면에 없다 → 시트를 펼친 뒤 다시 누른다.
// 시트가 움직이는 동안의 상태로 판단하면 펼쳐지는 시트를 도로 접는다 → 멈춘 뒤에만 판단한다.
fun tap(id: Int) {
    val view = onView(withId(id))
    eventually {
        val state = onMain { sheetState() }
        check(state != BottomSheetBehavior.STATE_SETTLING && state != BottomSheetBehavior.STATE_DRAGGING) { "시트가 움직이는 중" }
        // 화면에 없는 칸을 누르면 Espresso 가 실패를 내기까지 수 초가 걸려 재시도 시간을 다 쓴다 → 누르기 전에 펼친다.
        if (state == BottomSheetBehavior.STATE_COLLAPSED && !onMain { resumedActivity().findViewById<View>(id).isShown }) {
            onView(withId(R.id.sheet_handle)).perform(click())
            error("시트를 펼치는 중")
        }
        val result = runCatching {
            runCatching { view.perform(scrollTo()) }
            // 시트의 스크롤 영역 끝에 걸치면 일부만 보인다 → 그 칸 전체가 보이게 스크롤을 요청한다.
            onMain { resumedActivity().findViewById<View>(id).let { it.requestRectangleOnScreen(android.graphics.Rect(0, 0, it.width, it.height), true) } }
            view.perform(click())
        }
        if (result.isFailure && state == BottomSheetBehavior.STATE_COLLAPSED) onView(withId(R.id.sheet_handle)).perform(click())
        result.getOrThrow()
    }
}

private fun sheetState(): Int? {
    val sheet = resumedActivity().findViewById<View?>(R.id.layout_bottom_config_container) ?: return null
    return BottomSheetBehavior.from(sheet).state
}

fun webView(id: Int): WebView = eventually { onMain { resumedActivity().findViewById<WebView>(id)!! } }

fun textOf(id: Int): String = onMain { resumedActivity().findViewById<TextView>(id).text.toString() }

// 접힌 시트 안의 규칙 목록은 화면에 없어도 내용은 있다 → 보이는지와 관계없이 읽는다.
fun allText(id: Int): String = onMain {
    fun texts(v: View): Sequence<String> = sequence {
        if (v is TextView) yield(v.text.toString())
        if (v is android.view.ViewGroup) for (i in 0 until v.childCount) yieldAll(texts(v.getChildAt(i)))
    }
    texts(resumedActivity().findViewById(id)).joinToString("\n")
}

fun ruleCount(): Int = onMain { resumedActivity().findViewById<android.view.ViewGroup>(R.id.layout_actions_list) }.let { list ->
    onMain { (0 until list.childCount).count { list.getChildAt(it).findViewById<View?>(R.id.btn_rule_delete) != null } }
}

/** 편집 화면 주소 칸에 넣고 불러온 뒤 그 페이지가 다 읽힐 때까지 기다린다. */
fun loadInEditor(url: String, ready: String = "document.body"): WebView {
    eventually { onView(withId(R.id.et_inp_taddr)).perform(replaceText(url)) }
    onView(withId(R.id.btn_load_url)).perform(click())
    val web = webView(R.id.wv_inspector)
    eventually { assertEquals("\"complete\"", web.js("($ready) ? document.readyState : ''")) }
    return web
}

fun inspectOn(web: WebView) {
    tap(R.id.btn_toggle_inspect)
    eventually { assertEquals("true", web.js("window.webPluginInspectEnabled === true")) }
}

// 사용자의 탭과 같은 click 이벤트를 요소에 보낸다 → 선택 스크립트와 앱 연결 경로를 그대로 탄다.
fun pick(web: WebView, id: String) {
    web.js("document.getElementById('$id').click()")
    eventually { onView(withId(R.id.layout_traversal_actions)).check(matches(isDisplayed())) }
    eventually { assertEquals("#$id", textOf(R.id.tv_selector_detail)) }
}

fun pickColor(button: Int, hex: String) {
    tap(button)
    onView(withContentDescription(hex)).inRoot(isDialog()).perform(click())
    onView(withText(R.string.ok)).inRoot(isDialog()).perform(click())
}

fun WebView.js(script: String): String {
    val latch = CountDownLatch(1)
    var result = ""
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
        evaluateJavascript(script) { result = it ?: ""; latch.countDown() }
    }
    assertTrue("스크립트 결과가 오지 않음: $script", latch.await(5, TimeUnit.SECONDS))
    return result
}
