package com.nec.remote

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.ConsumerIrManager
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private val necFrequency = 38000 // 38 kHz

    // Danh sách toàn bộ mã BẬT (Power ON) của các dòng máy chiếu NEC
    private val necPowerOnCodes = longArrayOf(
        0x181810EFL, // NEC Standard ID1 - Power ON (MSB - NP/M/ME/MC/V/P/PA/UM series)
        0x181808F7L, // NEC Standard ID1 - Power ON (LSB)
        0x18E710EFL, // NEC Extended 18E7 - Power ON (MSB - VT/LT/NP cũ)
        0x18E708F7L, // NEC Extended 18E7 - Power ON (LSB)
        0x189810EFL, // NEC High-End PX/PH/ID2 - Power ON (MSB)
        0x189808F7L, // NEC High-End PX/PH/ID2 - Power ON (LSB)
        0x4EB110EFL, // NEC DLP / VE Series - Power ON (MSB)
        0x4EB108F7L, // NEC DLP / VE Series - Power ON (LSB)
        0x32CD40BFL, // NEC OEM DLP Series - Power Toggle/ON
        0x00FF40BFL  // NEC Mini / LED Series - Power Toggle/ON
    )

    // Danh sách toàn bộ mã TẮT (Power OFF) của các dòng máy chiếu NEC
    private val necPowerOffCodes = longArrayOf(
        0x181828D7L, // NEC Standard ID1 - Discrete OFF (MSB)
        0x181814EBL, // NEC Standard ID1 - Discrete OFF (LSB)
        0x18E728D7L, // NEC Extended 18E7 - Discrete OFF (MSB)
        0x18E714EBL, // NEC Extended 18E7 - Discrete OFF (LSB)
        0x181810EFL, // NEC Standard Power Toggle (dành cho model dùng chung nút Bật/Tắt)
        0x181808F7L, // NEC Standard Power Toggle (LSB)
        0x189828D7L, // NEC High-End PX/PH - Discrete OFF (MSB)
        0x189814EBL, // NEC High-End PX/PH - Discrete OFF (LSB)
        0x4EB128D7L, // NEC DLP / VE Series - Discrete OFF
        0x32CD40BFL  // NEC OEM DLP Series - Power OFF
    )

    private var irManager: ConsumerIrManager? = null
    private lateinit var statusText: TextView
    private lateinit var btnOn: Button
    private lateinit var btnOff: Button
    private lateinit var btnStop: Button

    @Volatile
    private var isTransmitting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        irManager = getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        val hasIr = irManager?.hasIrEmitter() == true

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#121826"))
            val pad = dpToPx(24)
            setPadding(pad, pad, pad, pad)
        }

        val title = TextView(this).apply {
            text = "ĐIỀU KHIỂN MÁY CHIẾU NEC"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        root.addView(title)

        val subTitle = TextView(this).apply {
            text = if (hasIr) {
                "Đã sẵn sàng: Điện thoại có hỗ trợ Hồng ngoại (IR)"
            } else {
                "Cảnh báo: Thiết bị này không có mắt Hồng ngoại (IR)!"
            }
            textSize = 14f
            setTextColor(if (hasIr) Color.parseColor("#4ADE80") else Color.parseColor("#F87171"))
            gravity = Gravity.CENTER
            setPadding(0, dpToPx(8), 0, dpToPx(32))
        }
        root.addView(subTitle)

        btnOn = createStyledButton("BẬT MÁY CHIẾU (ON)", "#16A34A")
        val paramsOn = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(90)
        ).apply { setMargins(0, 0, 0, dpToPx(20)) }
        root.addView(btnOn, paramsOn)

        btnOff = createStyledButton("TẮT MÁY CHIẾU (OFF)", "#DC2626")
        val paramsOff = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(90)
        ).apply { setMargins(0, 0, 0, dpToPx(20)) }
        root.addView(btnOff, paramsOff)

        btnStop = createStyledButton("DỪNG PHÁT", "#4B5563").apply {
            isEnabled = false
        }
        val paramsStop = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(56)
        ).apply { setMargins(0, 0, 0, dpToPx(28)) }
        root.addView(btnStop, paramsStop)

        statusText = TextView(this).apply {
            text = "Hướng đầu điện thoại về phía máy chiếu và nhấn nút."
            textSize = 15f
            setTextColor(Color.parseColor("#E5E7EB"))
            gravity = Gravity.CENTER
        }
        root.addView(statusText)

        setContentView(root)

        btnOn.setOnClickListener { startSendingCodes(necPowerOnCodes, isPowerOff = false) }
        btnOff.setOnClickListener { startSendingCodes(necPowerOffCodes, isPowerOff = true) }
        btnStop.setOnClickListener { isTransmitting = false }
    }

    private fun startSendingCodes(codes: LongArray, isPowerOff: Boolean) {
        val manager = irManager
        if (manager == null || !manager.hasIrEmitter()) {
            statusText.text = "Không thể phát: Điện thoại không có cổng Hồng ngoại (IR)!"
            return
        }
        if (isTransmitting) return

        isTransmitting = true
        setButtonsState(true)

        thread {
            val total = codes.size
            for (i in 0 until total) {
                if (!isTransmitting) break
                val index = i + 1
                val hex = String.format("0x%08X", codes[i])
                val pattern = buildNecPattern(codes[i])

                if (!isPowerOff) {
                    updateStatus("Đang phát mã BẬT ($index/$total)\nMã: $hex")
                    manager.transmit(necFrequency, pattern)
                    Thread.sleep(250)
                } else {
                    updateStatus("Đang phát mã TẮT ($index/$total) - Lần 1/2\nMã: $hex")
                    manager.transmit(necFrequency, pattern)
                    Thread.sleep(800)

                    if (!isTransmitting) break

                    updateStatus("Đang phát mã TẮT ($index/$total) - Lần 2/2 (Xác nhận)\nMã: $hex")
                    manager.transmit(necFrequency, pattern)
                    Thread.sleep(350)
                }
            }

            val completed = isTransmitting
            isTransmitting = false
            runOnUiThread {
                setButtonsState(false)
                statusText.text = if (completed) {
                    if (isPowerOff) "Đã phát xong toàn bộ mã TẮT NEC!" else "Đã phát xong toàn bộ mã BẬT NEC!"
                } else {
                    "Đã dừng phát tín hiệu."
                }
            }
        }
    }

    private fun buildNecPattern(code: Long): IntArray {
        val pattern = IntArray(68)
        var idx = 0
        // Header: 9000us Mark, 4500us Space
        pattern[idx++] = 9000
        pattern[idx++] = 4500

        // 32 bits dữ liệu
        for (bit in 31 downTo 0) {
            pattern[idx++] = 560
            pattern[idx++] = if (((code shr bit) and 1L) == 1L) 1690 else 560
        }

        // Stop bit + khoảng nghỉ cuối
        pattern[idx++] = 560
        pattern[idx] = 40000
        return pattern
    }

    private fun updateStatus(msg: String) {
        runOnUiThread { statusText.text = msg }
    }

    private fun setButtonsState(sending: Boolean) {
        btnOn.isEnabled = !sending
        btnOff.isEnabled = !sending
        btnStop.isEnabled = sending
        btnOn.alpha = if (sending) 0.5f else 1.0f
        btnOff.alpha = if (sending) 0.5f else 1.0f
    }

    private fun createStyledButton(label: String, hexColor: String): Button {
        return Button(this).apply {
            text = label
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(14).toFloat()
                setColor(Color.parseColor(hexColor))
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        return Math.round(dp * resources.displayMetrics.density)
    }
}
