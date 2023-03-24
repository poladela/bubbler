package com.guessai.bubbler

import android.graphics.Point
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import com.guessai.bubbler.BubbleDetector.Bubble
import java.io.File
import kotlin.reflect.KFunction1


class BubbleAnalyzer(
    private val bubbleView: BubbleView,
    private val blinkListener: KFunction1<Mat, Unit>,
    private val faceCascadeFile: File,
    private val eyeCascadeFile: File
) : ImageAnalysis.Analyzer {

    private val TAG = "BubbleAnalyzer"
    private val isProcessing = AtomicBoolean(false)
    private lateinit var rotatedMat: Mat

    override fun analyze(image: ImageProxy) {

        isProcessing.set(true)

        // Convert image to Mat
        val buffer: ByteBuffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val rgbaMat = Mat(image.height, image.width, CvType.CV_8UC4)
        rgbaMat.put(0, 0, bytes)

        // Rotate the Mat if necessary
        val rotationDegrees = image.imageInfo.rotationDegrees
        rotatedMat = if (rotationDegrees == 0) {
            rgbaMat
        } else {
            val matrix = Imgproc.getRotationMatrix2D(
                Point(rgbaMat.cols() / 2.0, rgbaMat.rows() / 2.0),
                rotationDegrees.toDouble(), 1.0
            )
            val rotatedMat = Mat()
            Imgproc.warpAffine(rgbaMat, rotatedMat, matrix, rgbaMat.size())
            rgbaMat.release()
            rotatedMat
        }

        // Detect bubbles in the Mat
        val bubbles = findBubbles(rotatedMat)



        // Release resources
        if (rotationDegrees != 0) {
            rgbaMat.release()
        }
        image.close()
        isProcessing.set(false)


    }

    private fun findBubbles(mat: Mat): List<Bubble> {
        val bubbles = mutableListOf<Bubble>()

        // Convert the color space from RGBA to Grayscale
        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2GRAY)

        // Apply a Gaussian blur to reduce noise
        Imgproc.GaussianBlur(mat, mat, Size(5.0, 5.0), 0.0)

        // Detect circles using HoughCircles algorithm
        val circles = Mat()
        Imgproc.HoughCircles(mat, circles, Imgproc.HOUGH_GRADIENT, 1.0, 50.0, 100.0, 30.0, 10, 100)

        // Convert the circles Mat to a list of Bubble objects
        for (i in 0 until circles.cols()) {
            val circle = circles.get(0, i)
            if (circle != null) {
                val center = Point(circle[0].toInt(), circle[1].toInt())
                val radius = circle[2].toInt()
                val bubble = Bubble(
                    left = center.x - radius,
                    top = center.y - radius,
                    right = center.x + radius,
                    bottom = center.y + radius
                )
                bubbles.add(bubble)
            }
        }
        // Release resources
        circles.release()

        return bubbles
    }
}