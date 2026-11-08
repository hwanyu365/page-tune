package com.hwanyu365.pagetune.ui.view

import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.GridLayout
import android.widget.SeekBar
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hwanyu365.pagetune.R
import com.hwanyu365.pagetune.databinding.DialogColorPickerBinding
import com.hwanyu365.pagetune.util.ColorPresets
import com.hwanyu365.pagetune.util.HexColor

/** 프리셋·헥스 칸·RGB 슬라이더가 같은 현재 색을 공유한다. 결과는 항상 "#RRGGBB" 이거나, 색을 지웠을 때 null 이다. */
object ColorPickerDialog {

    fun show(context: Context, title: String, initial: String?, onPick: (String?) -> Unit) {
        val binding = DialogColorPickerBinding.inflate(LayoutInflater.from(context))
        val density = context.resources.displayMetrics.density
        val outline = ContextCompat.getColor(context, R.color.outline)
        var current = initial?.let(HexColor::parse) ?: ColorPresets.ALL.first()
        var syncing = false
        var setOkEnabled: (Boolean) -> Unit = {}

        fun swatch(color: Int) = GradientDrawable().apply {
            setColor(color)
            setStroke(density.toInt().coerceAtLeast(1), outline)
            cornerRadius = 8 * density
        }

        // 헥스 칸에서 온 변경이면 칸을 다시 쓰지 않는다 → 입력 중인 글자를 덮어쓰지 않는다.
        fun select(hex: String, fromHexInput: Boolean = false) {
            current = hex
            val color = Color.parseColor(hex)
            syncing = true
            binding.vColorPreview.background = swatch(color)
            binding.sbRed.progress = Color.red(color)
            binding.sbGreen.progress = Color.green(color)
            binding.sbBlue.progress = Color.blue(color)
            if (!fromHexInput) binding.etColorHex.setText(hex)
            syncing = false
            binding.tilColorHex.error = null
            setOkEnabled(true)
        }

        val swatchSize = (48 * density).toInt()
        val swatchMargin = (4 * density).toInt()
        ColorPresets.ALL.forEach { hex ->
            val view = View(context).apply {
                background = swatch(Color.parseColor(hex))
                contentDescription = hex
                isClickable = true
                isFocusable = true
                setOnClickListener { select(hex) }
            }
            binding.gridColorPresets.addView(view, GridLayout.LayoutParams().apply {
                width = swatchSize
                height = swatchSize
                setMargins(swatchMargin, swatchMargin, swatchMargin, swatchMargin)
            })
        }

        binding.etColorHex.doAfterTextChanged { text ->
            if (syncing) return@doAfterTextChanged
            val parsed = HexColor.parse(text?.toString().orEmpty())
            if (parsed != null) {
                select(parsed, fromHexInput = true)
            } else {
                binding.tilColorHex.error = context.getString(R.string.color_hex_error)
                setOkEnabled(false)
            }
        }

        val onSlide = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (syncing) return
                val rgb = Color.rgb(binding.sbRed.progress, binding.sbGreen.progress, binding.sbBlue.progress)
                select(String.format("#%06X", 0xFFFFFF and rgb))
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
        listOf(binding.sbRed, binding.sbGreen, binding.sbBlue).forEach { it.setOnSeekBarChangeListener(onSlide) }

        binding.btnColorAdvanced.setOnClickListener {
            val open = binding.layoutColorAdvanced.visibility != View.VISIBLE
            binding.layoutColorAdvanced.visibility = if (open) View.VISIBLE else View.GONE
            binding.btnColorAdvanced.setText(if (open) R.string.color_advanced_hide else R.string.color_advanced_show)
        }

        select(current)

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(binding.root)
            .setPositiveButton(R.string.ok) { _, _ -> onPick(current) }
            // 색을 지우면 해당 속성은 CSS 선언에서 빠져 사이트 원래 색이 그대로 남는다.
            .setNeutralButton(R.string.clear) { _, _ -> onPick(null) }
            .setNegativeButton(R.string.cancel, null)
            .show()
        setOkEnabled = { dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.isEnabled = it }
    }
}
