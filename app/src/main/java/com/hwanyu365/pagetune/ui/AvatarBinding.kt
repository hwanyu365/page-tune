package com.hwanyu365.pagetune.ui

import android.content.res.ColorStateList
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import com.hwanyu365.pagetune.R
import com.hwanyu365.pagetune.util.DomainAvatar

private val AVATAR_COLORS = intArrayOf(
    R.color.avatar1, R.color.avatar2, R.color.avatar3, R.color.avatar4,
    R.color.avatar5, R.color.avatar6, R.color.avatar7, R.color.avatar8,
)

fun bindAvatar(container: View, letter: TextView, webIcon: ImageView, avatar: DomainAvatar) {
    ViewCompat.setBackgroundTintList(container, ColorStateList.valueOf(ContextCompat.getColor(container.context, AVATAR_COLORS[avatar.colorIndex])))
    letter.text = avatar.letter
    letter.visibility = if (avatar.letter != null) View.VISIBLE else View.GONE
    webIcon.visibility = if (avatar.letter == null) View.VISIBLE else View.GONE
}
