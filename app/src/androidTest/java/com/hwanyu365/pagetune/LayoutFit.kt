package com.hwanyu365.pagetune

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.children
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Root
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matcher
import org.hamcrest.Matchers.any

// WebView 안은 앱이 아니라 페이지가 그린 것이다 → 들어가지 않는다.
fun View.shownViews(): Sequence<View> = sequence {
    if (!isShown) return@sequence
    yield(this@shownViews)
    if (this@shownViews is ViewGroup && this@shownViews !is WebView) children.forEach { yieldAll(it.shownViews()) }
}

fun layoutProblems(root: View, screen: String): List<String> {
    val problems = mutableListOf<String>()
    fun name(v: View) = "$screen ${if (v.id != View.NO_ID) runCatching { v.resources.getResourceEntryName(v.id) }.getOrDefault(v.javaClass.simpleName) else v.javaClass.simpleName}"
    for (v in root.shownViews()) {
        if (v is TextView && v !is EditText && v.text.isNotEmpty() && v.width > 0) {
            val layout = v.layout
            if (layout == null) {
                problems += "${name(v)} 글자 배치 없음"
                continue
            }
            val innerWidth = v.width - v.totalPaddingLeft - v.totalPaddingRight
            val innerHeight = v.height - v.totalPaddingTop - v.totalPaddingBottom
            val ink = Rect()
            for (line in 0 until layout.lineCount) {
                if (layout.getEllipsisCount(line) > 0) problems += "${name(v)} 말줄임 '${v.text}'"
                val start = layout.getLineStart(line)
                val end = layout.getLineVisibleEnd(line)
                if (end <= start) continue
                // 줄 폭은 글자 진행 폭의 합이라 그려진 모양보다 넓을 수 있다 → 그려진 글자 모양의 폭으로 판정한다.
                // 버튼처럼 배치 폭을 넓게 잡고 가운데 맞추는 칸은 줄 시작 위치가 칸 좌표가 아니다 → 위치는 보지 않는다.
                v.paint.getTextBounds(v.text, start, end, ink)
                if (ink.width() > innerWidth + 1) problems += "${name(v)} 가로 넘침 '${v.text}' ${ink.width()}>$innerWidth"
            }
            if (layout.height > innerHeight + 1) problems += "${name(v)} 세로 잘림 '${v.text}' ${layout.height}>$innerHeight"
        }
        if (v is LinearLayout) {
            val rects = v.children.filter { it.isShown && it.width > 0 && it.height > 0 }.map { c -> c to Rect(c.left, c.top, c.right, c.bottom) }.toList()
            for (i in rects.indices) for (j in i + 1 until rects.size) {
                val a = rects[i].second
                val b = rects[j].second
                if (Rect.intersects(a, b)) problems += "${name(v)} 겹침 ${name(rects[i].first)} / ${name(rects[j].first)}"
            }
        }
    }
    return problems
}

fun layoutProblemsOnMain(root: View, screen: String): List<String> {
    var problems = emptyList<String>()
    InstrumentationRegistry.getInstrumentation().runOnMainSync { problems = layoutProblems(root, screen) }
    return problems
}

fun dialogRoot(): View = captureRoot(isDialog())

fun captureRoot(root: Matcher<Root>): View {
    var found: View? = null
    onView(isRoot()).inRoot(root).perform(object : ViewAction {
        override fun getConstraints(): Matcher<View> = any(View::class.java)
        override fun getDescription() = "최상위 view 를 꺼낸다"
        override fun perform(uiController: UiController, view: View) {
            found = view
        }
    })
    return found!!
}
