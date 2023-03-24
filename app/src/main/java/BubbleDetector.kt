package com.guessai.bubbler

import android.graphics.Rect
import android.util.Log
import android.util.Size
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.face.*
import com.google.mlkit.vision.common.InputImage
import org.opencv.core.CvType
import org.opencv.core.Mat

class BubbleDetector(private val bubbleTracker: BubbleTracker) {

    private val TAG = "BubbleDetector"

    private val faceDetector: FaceDetector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .build()
        FaceDetection.getClient(options)
    }

    @ExperimentalGetImage
    fun detect(image: ImageProxy): List<Bubble> {
        val mediaImage = image.image
        val bubbles = mutableListOf<Bubble>()
        if (mediaImage != null) {
            val inputImage = InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees)
            faceDetector.process(inputImage)
                .addOnSuccessListener { faces ->
                    for (face in faces) {
                        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
                        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position
                        val noseBase = face.getLandmark(FaceLandmark.NOSE_BASE)?.position
                        val mouthLeft = face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position
                        val mouthRight = face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position
                        val mouthBottom = face.getLandmark(FaceLandmark.MOUTH_BOTTOM)?.position
                        if (leftEye != null && rightEye != null && noseBase != null &&
                            mouthLeft != null && mouthRight != null && mouthBottom != null
                        ) {
                            val bubble = Bubble(
                                left = leftEye.x.toInt(),
                                top = noseBase.y.toInt(),
                                right = rightEye.x.toInt(),
                                bottom = mouthBottom.y.toInt()
                            )
                            bubbles.add(bubble)
                        }
                    }
                    bubbleTracker.onBubblesDetected(bubbles)
                    image.close()
                }
                .addOnFailureListener { exc ->
                    Log.e(TAG, "Failed to detect faces", exc)
                    image.close()
                }
        } else {
            image.close()
        }
        return bubbles
    }

    fun close() {
        faceDetector.close()
    }
    fun detectBubbles(image: ImageProxy) {
        val buffer = image.planes[0].buffer
        val data = ByteArray(buffer.remaining())
        buffer.get(data)
        val mat = Mat(
            image.height + image.height / 2,
            image.width,
            CvType.CV_8UC1
        )
        mat.put(0, 0, data)
        // Call the detect function with the mat object
        detect(image)
    }


    interface BubbleTracker {
        fun onBubblesDetected(bubbles: List<Bubble>)
    }

    data class Bubble(var left: Int, var top: Int, var right: Int, var bottom: Int) {
        var speedX: Int = (1..3).random()
        var speedY: Int = (1..3).random()
    }

}
