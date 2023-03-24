package com.guessai.bubbler
import com.guessai.bubbler.BubbleDetector.Bubble

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.View

class BubbleView : View {
    private lateinit var bubbles: List<Bubble>
    private var viewWidth = 0
    private var viewHeight = 0
    private var bubbleRects: List<Rect> = emptyList()
    private val gazePoints = mutableListOf<Point>()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    private fun rectToBubble(rect: Rect): Bubble {
        return Bubble(rect.left, rect.top, rect.right, rect.bottom)
    }

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewWidth = w
        viewHeight = h
        Log.d("BubbleView", "View dimensions: ($viewWidth, $viewHeight)")
    }

    fun checkGazeAndPopBubble(gazeX: Int, gazeY: Int) {
        val normalizedGazeX = gazeX * viewWidth / 1000
        val normalizedGazeY = gazeY * viewHeight / 1000
        Log.d("BubbleView", "Processing gaze at coordinates: ($normalizedGazeX, $normalizedGazeY)")
        Log.d("BubbleView", "Processing gaze at coordinates: ($gazeX, $gazeY)")
        Log.d("BubbleView", "BubbleRects: $bubbleRects")

        gazePoints.add(Point(normalizedGazeX, normalizedGazeY))
        invalidate()
        var intersectionFound = false

        // Iterate through the bubbles and check if the gaze point is within the bubble
        for (bubbleRect in bubbleRects) {
            Log.d(
                "BubbleView",
                "Checking gaze intersection with bubble: $bubbleRect, gaze coordinates: ($gazeX, $gazeY)"
            )

            if (bubbleRect.contains(normalizedGazeX, normalizedGazeY)) {

                // The gaze point intersects the bubble, so we pop it
                Log.d("BubbleView", "Gaze intersects with bubble: $bubbleRect")
                popBubble(rectToBubble(bubbleRect))
                intersectionFound = true
                break
            } else {
                Log.d("BubbleView", "No intersection at gaze coordinates: ($gazeX, $gazeY)")
            }
        }

        if (!intersectionFound) {
            Log.d("BubbleView", "No intersection found for gaze coordinates: ($gazeX, $gazeY)")
        }
    }


    private fun RectToBubble(rect: Rect): Bubble {
        return Bubble(
            left = rect.left,
            top = rect.top,
            right = rect.right,
            bottom = rect.bottom
        )
    }


    fun setBubbles(bubbles: List<BubbleDetector.Bubble>) {
        bubbleRects = bubbles.map { bubble ->
            Rect(bubble.left, bubble.top, bubble.right, bubble.bottom)
        }
        invalidate()
    }

    fun getBubbleCenter(bubble: Rect): Point {
        val centerX = bubble.left + (bubble.width() / 2)
        val centerY = bubble.top + (bubble.height() / 2)
        return Point(centerX, centerY)
    }

    fun updateBubbles(bubbles: List<Bubble>) {
        post {
            this.bubbles = bubbles
            this.bubbleRects = bubbles.map { bubble ->
                Rect(bubble.left, bubble.top, bubble.right, bubble.bottom)
            }
            invalidate()
        }
    }

    fun getBubbleAtCenter(): Rect? {
        val centerX = width / 2
        val centerY = height / 2
        for (bubbleRect in bubbleRects) {
            if (bubbleRect.contains(centerX, centerY)) {
                return bubbleRect
            }
        }
        return null
    }


    fun popBubble(bubble: Bubble) {
        bubbleRects =
            bubbleRects.filter { it != Rect(bubble.left, bubble.top, bubble.right, bubble.bottom) }
        invalidate()
    }


    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (rect in bubbleRects) {
            canvas.drawRect(rect, paint)

            // Draw center points
            val centerX = rect.left + (rect.width() / 2)
            val centerY = rect.top + (rect.height() / 2)
            canvas.drawCircle(centerX.toFloat(), centerY.toFloat(), 10f, paint)
        }

        // Draw gaze points
        val gazePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLUE
            style = Paint.Style.FILL
        }
        for (gazePoint in gazePoints) {
            canvas.drawCircle(gazePoint.x.toFloat(), gazePoint.y.toFloat(), 10f, gazePaint)
        }
    }
}
