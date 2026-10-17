package com.hwanyu365.pagetune.ui.view

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.widget.SeekBar
import com.hwanyu365.pagetune.R
import com.hwanyu365.pagetune.databinding.DialogColorPickerBinding

/**
 * RGB 슬라이더 색상 선택 다이얼로그.
 * 슬라이더가 유일한 입력 경로라 결과는 항상 "#RRGGBB" 이거나, 색을 지웠을 때 null 이다.
 */
object ColorPickerDialog {

    fun show(context: Context, title: String, initial: String?, onPick: (String?) -> Unit) {
        val binding = DialogColorPickerBinding.inflate(LayoutInflater.from(context))
        val start = runCatching { Color.parseColor(initial) }.getOrDefault(Color.BLACK)

        fun currentColor() = Color.rgb(binding.sbRed.progress, binding.sbGreen.progress, binding.sbBlue.progress)

        fun refreshPreview() {
            val color = currentColor()
            binding.vColorPreview.setBackgroundColor(color)
            binding.tvColorHex.text = toHex(color)
        }

        val onChange = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = refreshPreview()
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
        listOf(binding.sbRed, binding.sbGreen, binding.sbBlue).forEach { it.setOnSeekBarChangeListener(onChange) }

        binding.sbRed.progress = Color.red(start)
        binding.sbGreen.progress = Color.green(start)
        binding.sbBlue.progress = Color.blue(start)
        refreshPreview()

        AlertDialog.Builder(context)
            .setTitle(title)
            .setView(binding.root)
            .setPositiveButton(R.string.ok) { _, _ -> onPick(toHex(currentColor())) }
            // 색을 지우면 해당 속성은 CSS 선언에서 빠져 사이트 원래 색이 그대로 남는다.
            .setNeutralButton(R.string.clear) { _, _ -> onPick(null) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun toHex(color: Int) = String.format("#%06X", 0xFFFFFF and color)
}
