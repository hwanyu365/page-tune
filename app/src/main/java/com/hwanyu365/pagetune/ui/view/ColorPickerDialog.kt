package com.hwanyu365.pagetune.ui.view

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.widget.SeekBar
import com.hwanyu365.pagetune.R
import kotlinx.android.synthetic.main.dialog_color_picker.view.*

/**
 * RGB 슬라이더 색상 선택 다이얼로그.
 * 슬라이더가 유일한 입력 경로라 결과는 항상 "#RRGGBB" 이거나, 색을 지웠을 때 null 이다.
 * 외부 컬러픽커 라이브러리를 쓰지 않는 이유는 이 프로젝트가 jcenter 기반이라 의존성 추가 비용이 크기 때문이다.
 */
object ColorPickerDialog {

    fun show(context: Context, title: String, initial: String?, onPick: (String?) -> Unit) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_color_picker, null)
        val start = runCatching { Color.parseColor(initial) }.getOrDefault(Color.BLACK)

        fun currentColor() = Color.rgb(view.sb_red.progress, view.sb_green.progress, view.sb_blue.progress)

        fun refreshPreview() {
            val color = currentColor()
            view.v_color_preview.setBackgroundColor(color)
            view.tv_color_hex.text = toHex(color)
        }

        val onChange = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = refreshPreview()
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
        listOf(view.sb_red, view.sb_green, view.sb_blue).forEach { it.setOnSeekBarChangeListener(onChange) }

        view.sb_red.progress = Color.red(start)
        view.sb_green.progress = Color.green(start)
        view.sb_blue.progress = Color.blue(start)
        refreshPreview()

        AlertDialog.Builder(context)
            .setTitle(title)
            .setView(view)
            .setPositiveButton(R.string.ok) { _, _ -> onPick(toHex(currentColor())) }
            // 색을 지우면 해당 속성은 CSS 선언에서 빠져 사이트 원래 색이 그대로 남는다.
            .setNeutralButton(R.string.clear) { _, _ -> onPick(null) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun toHex(color: Int) = String.format("#%06X", 0xFFFFFF and color)
}
