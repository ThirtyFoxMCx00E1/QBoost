package com.example.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.view.View

/** Round FPS gauge for the in-game panel: a gradient ring with the value in the middle. */
class GaugeView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density

    var fraction: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    var valueText: String = "--"
        set(value) {
            field = value
            invalidate()
        }

    var labelText: String = "FPS"
        set(value) {
            field = value
            invalidate()
        }

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 7f * density
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#33FFFFFF")
    }

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 7f * density
        strokeCap = Paint.Cap.ROUND
    }

    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        textSize = 26f * density
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9EA3B0")
        textAlign = Paint.Align.CENTER
        textSize = 11f * density
    }

    private val oval = RectF()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val pad = 9f * density
        oval.set(pad, pad, w - pad, h - pad)
        val shader = SweepGradient(
            w / 2f,
            h / 2f,
            intArrayOf(
                Color.parseColor("#1746B8"),
                Color.parseColor("#2F80FF"),
                Color.parseColor("#00E5FF"),
                Color.parseColor("#1746B8")
            ),
            null
        )
        // The ring starts at the bottom-left (135 degrees), so rotate the gradient to match
        val matrix = Matrix()
        matrix.postRotate(135f, w / 2f, h / 2f)
        shader.setLocalMatrix(matrix)
        arcPaint.shader = shader
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawArc(oval, 135f, 270f, false, trackPaint)
        if (fraction > 0f) {
            canvas.drawArc(oval, 135f, 270f * fraction, false, arcPaint)
        }
        val cx = width / 2f
        val cy = height / 2f
        canvas.drawText(valueText, cx, cy + valuePaint.textSize * 0.28f, valuePaint)
        canvas.drawText(labelText, cx, cy + valuePaint.textSize * 0.28f + 16f * density, labelPaint)
    }
}

/** A small ">" drawn with lines (no emoji, no icon font) for the edge handle of the panel. */
class ChevronView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2.6f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val path = Path()
        path.moveTo(w * 0.36f, h * 0.36f)
        path.lineTo(w * 0.64f, h * 0.5f)
        path.lineTo(w * 0.36f, h * 0.64f)
        canvas.drawPath(path, paint)
    }
}

/** Resize grip for floating windows: three short diagonal lines in the bottom-right corner. */
class GripView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B3FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 1.8f * density
        strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val gap = 5f * density
        for (i in 1..3) {
            val offset = i * gap
            canvas.drawLine(w - 4f * density, h - offset, w - offset, h - 4f * density, paint)
        }
    }
}
