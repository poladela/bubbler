package com.guessai.bubbler
import android.Manifest
import android.content.ContentValues.TAG
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.guessai.bubbler.R
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
interface GazeListener {
    fun onGazeChanged(x: Int, y: Int)
}

class MainActivity : AppCompatActivity(), GazeListener {
    private lateinit var sensorManager: SensorManager
    private lateinit var gyroscopeSensor: Sensor
    private lateinit var gyroscopeEventListener: SensorEventListener
    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var preview: Preview
    private lateinit var imageAnalyzer: ImageAnalysis
    private lateinit var previewView: PreviewView
    private lateinit var bubbleView: BubbleView
    private lateinit var rotationVectorSensor: Sensor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.preview_view)
        cameraExecutor = Executors.newSingleThreadExecutor()
        bubbleView = findViewById(R.id.bubble_view)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        if (gyroscopeSensor == null) {
            Toast.makeText(
                this,
                "Gyroscope sensor is required for this app to work properly.",
                Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }

        gyroscopeEventListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
                    // Convert rotation vector to orientation angles
                    val rotationMatrix = FloatArray(9)
                    val orientationAngles = FloatArray(3)
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    SensorManager.getOrientation(rotationMatrix, orientationAngles)

                    // Calculate gaze point based on orientation angles
                    // ...
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                // Handle accuracy changes here
            }
        }


        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
    }




    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(
                    this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    override fun onGazeChanged(x: Int, y: Int) {
        // Update the gaze point and check for intersections with bubbles
        bubbleView.checkGazeAndPopBubble(x, y)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all { permission: String ->
        ContextCompat.checkSelfPermission(baseContext, permission) == PackageManager.PERMISSION_GRANTED
    }

    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(gyroscopeEventListener, rotationVectorSensor, SensorManager.SENSOR_DELAY_GAME)
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(gyroscopeEventListener)
    }


    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            preview = Preview.Builder().build()
            imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            // Initialize FaceAnalyzer and set it as the analyzer
            val faceAnalyzer = FaceAnalyzer(this@MainActivity, bubbleView, this)
            imageAnalyzer.setAnalyzer(cameraExecutor, faceAnalyzer)
            generateBubbles()
            val cameraSelector =
                CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_FRONT).build()


            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this as LifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalyzer
                )
                preview.setSurfaceProvider(previewView.surfaceProvider)
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }
    private fun generateBubbles() {
        val bubbles = mutableListOf<BubbleDetector.Bubble>()
        val bubbleCount = 3 // Decrease the number of bubbles to 3
        val screenWidth = Resources.getSystem().displayMetrics.widthPixels
        val screenHeight = Resources.getSystem().displayMetrics.heightPixels

        for (i in 0 until bubbleCount) {
            val radius = (80..150).random() // Increase the size of the bubbles by changing the radius range to 80-150
            val centerX = (radius..screenWidth - radius).random()
            val centerY = (radius..screenHeight - radius).random()

            val bubble = BubbleDetector.Bubble(
                left = centerX - radius,
                top = centerY - radius,
                right = centerX + radius,
                bottom = centerY + radius
            )
            bubbles.add(bubble)
        }

        bubbleView.updateBubbles(bubbles)
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}