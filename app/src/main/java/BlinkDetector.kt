package com.guessai.bubbler

import android.util.Log
import com.guessai.bubbler.MainActivity
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.CascadeClassifier
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

class BlinkDetector(
    private val listener: Listener,
    private val context: MainActivity
) {

    interface Listener {
        fun onBlink()
        fun onBlinkAt(x: Float, y: Float)

    }

    private val BLINK_THRESHOLD = 10
    private val EYE_ROI_RATIO = 0.25f
    private val EYE_ROI_TOP_RATIO = 0.35f
    private val EYE_ROI_BOTTOM_RATIO = 0.65f

    private val faceClassifier: CascadeClassifier
    private val eyeClassifier: CascadeClassifier

    init {
        val faceCascadePath = loadCascadeFile(context.assets.open("haarcascade_frontalface_default.xml"))
        val eyeCascadePath = loadCascadeFile(context.assets.open("haarcascade_eye.xml"))

        faceClassifier = CascadeClassifier(faceCascadePath)
        eyeClassifier = CascadeClassifier(eyeCascadePath)

        if (faceClassifier.empty()) {
            Log.e("BlinkDetector", "Failed to load face cascade")
            throw RuntimeException("Failed to load face cascade")
        }

        if (eyeClassifier.empty()) {
            Log.e("BlinkDetector", "Failed to load eye cascade")
            throw RuntimeException("Failed to load eye cascade")
        }
    }

    private var isBlinking = false
    private var blinkCounter = 0

    private fun loadCascadeFile(inputStream: InputStream): String {
        return try {
            val cascadeFile = File.createTempFile("cascade", ".xml", context.cacheDir)
            FileOutputStream(cascadeFile).use { output ->
                inputStream.copyTo(output)
            }
            cascadeFile.absolutePath
        } catch (e: IOException) {
            e.printStackTrace()
            throw RuntimeException("Failed to load cascade file", e)
        } finally {
            inputStream.close()
        }
    }
    private fun onBlink(x: Float, y: Float) {
        listener.onBlinkAt(x, y)

    }
    fun processFrame(frame: Mat) {
        Log.d("BlinkDetector", "Processing frame") // Add this log statement
        if (frame.channels() != 3 && frame.channels() != 4) return
        val gray = Mat()
        Imgproc.cvtColor(frame, gray, Imgproc.COLOR_RGB2GRAY)

        // Detect faces
        val faces = MatOfRect()
        faceClassifier.detectMultiScale(gray, faces,1.3, 3, 0, Size(50.0, 50.0))
        Log.d("BlinkDetector", "Faces detected: ${faces.toArray().size}")

        // For each face, detect eyes and check for blinks
        for (face in faces.toArray()) {
            val eyes = MatOfRect()
            val eyeRegionWidth = (face.width * this.EYE_ROI_RATIO).toInt()
            val eyeRegionHeight = (face.height * (this.EYE_ROI_BOTTOM_RATIO - this.EYE_ROI_TOP_RATIO)).toInt()
            val eyeRegionX = face.x + (face.width - eyeRegionWidth) / 2
            val eyeRegionY = face.y + (face.height * this.EYE_ROI_TOP_RATIO).toInt()
            val eyeRegion = Rect(eyeRegionX, eyeRegionY, eyeRegionWidth, eyeRegionHeight)
            val eyesFrame = gray.submat(eyeRegion)
            val equalizedGray = Mat()
            Imgproc.equalizeHist(gray, equalizedGray)
            eyeClassifier.detectMultiScale(equalizedGray, eyes, 1.1, 3, 0, Size(20.0, 20.0), Size(60.0, 60.0))



            Log.d("BlinkDetector", "Eyes detected: ${eyes.toArray().size}")

            // Check for blinks
            if (eyes.toArray().size == 2) {
                val leftEye = eyes.toArray()[0]
                val rightEye = eyes.toArray()[1]
                if (this.isBlinking) {
                    // Eyes are closed, check if they have opened again
                    if (leftEye.y + leftEye.height > rightEye.y && rightEye.y + rightEye.height > leftEye.y) {
                        this.blinkCounter++
                        if (this.blinkCounter >= this.BLINK_THRESHOLD) {
                            // Blink detected
                            this.listener.onBlink()
                            this.isBlinking = false
                            this.blinkCounter = 0
                        }
                    }
                } else {
                    // Eyes are open, check if they have closed
                    if (leftEye.y > rightEye.y + rightEye.height || rightEye.y > leftEye.y + leftEye.height) {
                        this.isBlinking = true
                        Log.d("BlinkDetector", "Closed eyes detected")
                    }
                }
            }
        }
        // Release resources
        gray.release()
    }
}