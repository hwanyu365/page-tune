package com.hwanyu365.webplugin.ui.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.AppCompatEditText

class NoAutofillEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {

    // Overriding this method to return AUTOFILL_TYPE_NONE completely blocks
    // the Android Autofill framework (including Samsung Pass) from recognizing this field.
    override fun getAutofillType(): Int {
        return View.AUTOFILL_TYPE_NONE
    }
}
