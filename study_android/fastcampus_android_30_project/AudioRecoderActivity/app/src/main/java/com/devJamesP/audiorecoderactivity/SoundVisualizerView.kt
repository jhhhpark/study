package com.devJamesP.audiorecoderactivity

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.random.Random

class SoundVisualizerView(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var onRequestCurrentAmplitude: (() -> Int)? = null

    // 곡선이 부드러워짐
    private val amplitudePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.purple_500)
        strokeWidth = LINE_WIDTH
        strokeCap = Paint.Cap.ROUND
    }

    private var drawingWidth: Int = 0
    private var drawingHeight: Int = 0
    private var drawingAmplitudes: List<Int> = (0..10).map{ Random.nextInt(MAX_AMPLITUDE.toInt())}
    private var isReplaying: Boolean = false
    private var replayingPosition: Int = 0

    private val visualizeRepeatAction: Runnable = object : Runnable {
        override fun run() {
            // Amplitude, Draw
            if (!isReplaying) {
                var currentAmplitude = onRequestCurrentAmplitude?.invoke() ?: 0
                drawingAmplitudes = listOf(currentAmplitude) + drawingAmplitudes
            } else {
                replayingPosition++
            }
            invalidate()

            handler?.postDelayed(this, ACTION_INTERVER)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        drawingWidth = w
        drawingHeight = h
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        canvas ?: return

        val centerY = drawingHeight / 2f
        var offsetX = drawingWidth.toFloat()

        drawingAmplitudes
            .let { amplitudes ->
                if (isReplaying) {
                    amplitudes.takeLast(replayingPosition)
                } else {
                    amplitudes
                }
            }
            .forEach { amplitude ->
            val lineLength = amplitude / MAX_AMPLITUDE * drawingHeight * 0.8f
            offsetX -= LINE_SPACE

            if (offsetX < 0) {
                return@forEach
            }

            canvas.drawLine(
                offsetX,
                centerY - lineLength / 2f,
                offsetX,
                centerY + lineLength / 2f,
                amplitudePaint
            )
        }

    }

    fun startVisualizing(isReplaying: Boolean) {
        this.isReplaying = isReplaying
        handler?.post(visualizeRepeatAction)
    }

    fun stopVisualizing() {
        replayingPosition = 0
        handler?.removeCallbacks(visualizeRepeatAction)
    }

    fun clear() {
        drawingAmplitudes = (0..10).map{ Random.nextInt(MAX_AMPLITUDE.toInt())}
        invalidate()
    }
    companion object {
        private const val LINE_WIDTH = 10f
        private const val LINE_SPACE = 15f
        private const val MAX_AMPLITUDE = Short.MAX_VALUE.toFloat()
        private const val ACTION_INTERVER = 20L
    }
}