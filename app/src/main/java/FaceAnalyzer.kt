package com.guessai.bubbler

import android.app.Activity
import android.content.Context
import android.graphics.Point
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import com.guessai.bubbler.BubbleView
import com.guessai.bubbler.GazeListener
import com.guessai.bubbler.MainActivity

class FaceAnalyzer(
    private val context: Context,
    private val bubbleView: BubbleView,
    private val gazeListener: GazeListener
) : ImageAnalysis.Analyzer {
    private val realTimeOpts = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .enableTracking()
        .build()
    private fun mapGazePointToScreenCoordinates(gazeX: Float, gazeY: Float): Point {
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        // Assuming the camera is centered at the top of the screen
        val cameraY = 0f

        // You can experiment with this value to adjust the sensitivity of the gaze
        val scaleFactor = 2f

        val screenX = (gazeX * scaleFactor).coerceIn(0f, screenWidth.toFloat()).toInt()
        val screenY = ((gazeY - cameraY) * scaleFactor).coerceIn(0f, screenHeight.toFloat()).toInt()

        return Point(screenX, screenY)
    }
    private val detector = FaceDetection.getClient(realTimeOpts)

    override fun analyze(imageProxy: ImageProxy) {
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val mediaImage = imageProxy.image

        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, rotationDegrees)
            detector.process(image)
                .addOnSuccessListener { faces ->
                    for (face in faces) {
                        if (face.leftEyeOpenProbability?.compareTo(0.4) ?: 1 < 0 && face.rightEyeOpenProbability?.compareTo(0.4) ?: 1 < 0) {
                            // A blink is detected
                            Log.d("FaceAnalyzer", "Blink detected!")

                            val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)
                            val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)

                            if (leftEye != null && rightEye != null) {
                                val gazeX = (leftEye.position.x + rightEye.position.x) / 2f
                                val gazeY = (leftEye.position.y + rightEye.position.y) / 2f

                                // Map the gaze point to screen coordinates
                                val screenPoint = mapGazePointToScreenCoordinates(gazeX, gazeY)

                                Log.d("FaceAnalyzer", "Gaze coordinates: (${screenPoint.x}, ${screenPoint.y})")

                                // Notify the MainActivity with the updated gaze coordinates
                                gazeListener.onGazeChanged(screenPoint.x, screenPoint.y)
                            }
                        }
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("FaceAnalyzer", "Face detection failed", exception)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        }
    }
}
