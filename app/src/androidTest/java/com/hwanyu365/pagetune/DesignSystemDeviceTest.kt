package com.hwanyu365.pagetune

import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.RadioButton
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.longClick
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.radiobutton.MaterialRadioButton
import com.hwanyu365.pagetune.ui.view.NoAutofillEditText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DesignSystemDeviceTest {

    private val found = mutableMapOf<String, Int>()
    private val offenders = mutableListOf<String>()

    @Before
    fun setUp() {
        resetApp()
    }

    @Test
    @JvmName("tc207")
    fun `TC#207 입력칸은 자동완성 대상이 아니고 버튼·체크박스는 Material 위젯이다`() {
        ActivityScenario.launch(PluginEditActivity::class.java).use { scenario ->
            onView(withId(R.id.sheet_handle)).perform(click())
            onView(withId(R.id.btn_plugin_save)).check(matches(isDisplayed()))
            scenario.onActivity { inspect(it.window.decorView, "edit") }
        }

        addWebPage("Example", "https://example.com/")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.fab_add_webpage)).perform(click())
            inspectOnMain(dialogRoot(), "webpage_dialog")
            pressBack()
            onView(withText("Example")).perform(longClick())
            onView(withId(R.id.chk_delete)).check(matches(isDisplayed()))
            scenario.onActivity { inspect(it.window.decorView, "delete_mode") }
        }

        assertEquals(emptyList<String>(), offenders)
        for (key in listOf("edit input", "edit button", "webpage_dialog input", "webpage_dialog button", "delete_mode checkbox")) {
            assertTrue("$key 를 찾지 못함: $found", (found[key] ?: 0) > 0)
        }
    }

    private fun inspectOnMain(root: View, screen: String) =
        InstrumentationRegistry.getInstrumentation().runOnMainSync { inspect(root, screen) }

    private fun inspect(root: View, screen: String) {
        for (v in root.shownViews()) {
            val kind = when {
                v is EditText -> "input".also { if (v !is NoAutofillEditText || v.autofillType != View.AUTOFILL_TYPE_NONE) offenders += "$screen ${v.javaClass.name} autofill=${v.autofillType}" }
                v is CheckBox -> "checkbox".also { if (v !is MaterialCheckBox) offenders += "$screen ${v.javaClass.name}" }
                v is RadioButton -> "radio".also { if (v !is MaterialRadioButton) offenders += "$screen ${v.javaClass.name}" }
                v is Button && v !is CompoundButton -> "button".also { if (v !is MaterialButton) offenders += "$screen ${v.javaClass.name}" }
                else -> null
            } ?: continue
            found["$screen $kind"] = (found["$screen $kind"] ?: 0) + 1
        }
    }
}
