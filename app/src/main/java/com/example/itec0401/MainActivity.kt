package com.example.itec0401

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.PermissionChecker
import androidx.fragment.app.Fragment
import com.example.itec0401.databinding.ActivityMainBinding
import com.example.itec0401.facemesh.FaceMeshResultGlRenderer
import com.example.itec0401.facemesh.FaceMeshResultImageView
import com.google.mediapipe.components.TextureFrameConsumer
import com.google.mediapipe.framework.TextureFrame
import com.google.mediapipe.solutioncore.*
import com.google.mediapipe.solutions.facemesh.FaceMesh
import com.google.mediapipe.solutions.facemesh.FaceMeshOptions
import com.google.mediapipe.solutions.facemesh.FaceMeshResult
import com.jiangdg.ausbc.utils.ToastUtils


class MainActivity : AppCompatActivity() {

    /* ########################### NOTICE ########################################## */
    /* ########################### facemesh example code ########################### */
    /* ########################### variable declaration ############################ */
    private val TAG: String? = "MainActivity"

    private var facemesh: FaceMesh? = null

    // Run the pipeline and the model inference on GPU or CPU.
    private val RUN_ON_GPU = true

    private enum class InputSource {
        UNKNOWN, IMAGE, VIDEO, CAMERA
    }

    private var inputSource = InputSource.UNKNOWN

    // Image demo UI and image loader components.
    private val imageGetter: ActivityResultLauncher<Intent>? = null
    private val imageView: FaceMeshResultImageView? = null

    // Video demo UI and video loader components.
    private var videoInput: VideoInput? = null
    private val videoGetter: ActivityResultLauncher<Intent>? = null

    // Live camera demo UI and camera components.
    private var cameraInput: CameraInput? = null

    private var glSurfaceView: SolutionGlSurfaceView<FaceMeshResult>? = null

    //private var mWakeLock: PowerManager.WakeLock? = null
    private lateinit var viewBinding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)
        ToastUtils.show("setContentView:onCreate()")

        replaceDemoFragment(DemoFragment())
        ToastUtils.show("replaceDemoFragment(DemoFragment()):onCreate()")

        setupLiveDemoUiComponents();
    }

    override fun onResume() {
        super.onResume()
        if (inputSource === InputSource.CAMERA) {
            // Restarts the camera and the opengl surface rendering.
            cameraInput = CameraInput(this)
            cameraInput!!.setNewFrameListener(TextureFrameConsumer { textureFrame: TextureFrame? ->
                facemesh!!.send(
                    textureFrame
                )
            })
            glSurfaceView!!.post(this::startCamera)
            glSurfaceView!!.setVisibility(View.VISIBLE)
        } else if (inputSource === InputSource.VIDEO) {
            videoInput!!.resume()
        }
    }

    override fun onPause() {
        super.onPause()
        if (inputSource === InputSource.CAMERA) {
            glSurfaceView!!.visibility = View.GONE
            cameraInput!!.close()
        } else if (inputSource === InputSource.VIDEO) {
            videoInput!!.pause()
        }
    }

    /** Sets up the UI components for the live demo with camera input.  */
    private fun setupLiveDemoUiComponents() {
        val startCameraButton: Button = findViewById(R.id.button_start_camera)
        startCameraButton.setOnClickListener { v ->
            if (inputSource === InputSource.CAMERA) {
                return@setOnClickListener
            }
            stopCurrentPipeline()
            setupStreamingModePipeline(InputSource.CAMERA)

            var previewUvc = supportFragmentManager.findFragmentById(R.id.my_fragment) as DemoFragment?
            if (previewUvc != null) {
                previewUvc.setVisibility()
            }
        }
    }

    /** Sets up core workflow for streaming mode.  */
    private fun setupStreamingModePipeline(inputSource: InputSource) {
        this.inputSource = inputSource
        // Initializes a new MediaPipe Face Mesh solution instance in the streaming mode.
        facemesh = FaceMesh(
            this,
            FaceMeshOptions.builder()
                .setStaticImageMode(false)
                .setRefineLandmarks(true)
                .setRunOnGpu(RUN_ON_GPU)
                .build()
        )
        facemesh!!.setErrorListener(ErrorListener { message: String, e: RuntimeException? ->
            Log.e(
                TAG,
                "MediaPipe Face Mesh error:$message"
            )
        })
        if (inputSource === InputSource.CAMERA) {
            cameraInput = CameraInput(this)
            cameraInput!!.setNewFrameListener { textureFrame: TextureFrame? ->
                facemesh!!.send(
                    textureFrame
                )
            }
        } else if (inputSource === InputSource.VIDEO) {
            videoInput = VideoInput(this)
            videoInput!!.setNewFrameListener(TextureFrameConsumer { textureFrame: TextureFrame? ->
                facemesh!!.send(
                    textureFrame
                )
            })
        }

        // Initializes a new Gl surface view with a user-defined FaceMeshResultGlRenderer.
        glSurfaceView =
            SolutionGlSurfaceView(this, facemesh!!.getGlContext(), facemesh!!.getGlMajorVersion())
        glSurfaceView!!.setSolutionResultRenderer(FaceMeshResultGlRenderer())
        glSurfaceView!!.setRenderInputImage(true)
        facemesh!!.setResultListener(
            ResultListener<FaceMeshResult> { faceMeshResult: FaceMeshResult? ->
                logNoseLandmark(faceMeshResult,  /*showPixelValues=*/false)
                glSurfaceView!!.setRenderData(faceMeshResult)
                glSurfaceView!!.requestRender()
            })

        // The runnable to start camera after the gl surface view is attached.
        // For video input source, videoInput.start() will be called when the video uri is available.
        if (inputSource === InputSource.CAMERA) {
            glSurfaceView!!.post(Runnable { startCamera() })
        }

        // Updates the preview layout.
        val frameLayout = findViewById<FrameLayout>(R.id.preview_display_layout)
        //imageView!!.visibility = View.GONE
        frameLayout.removeAllViewsInLayout()
        frameLayout.addView(glSurfaceView)
        glSurfaceView!!.setVisibility(View.VISIBLE)
        frameLayout.requestLayout()
    }

    private fun startCamera() {
        cameraInput!!.start(
            this,
            facemesh!!.glContext,
            CameraInput.CameraFacing.FRONT,
            glSurfaceView!!.width,
            glSurfaceView!!.height
        )
    }

    private fun stopCurrentPipeline() {
        if (cameraInput != null) {
            cameraInput!!.setNewFrameListener(null)
            cameraInput!!.close()
        }
        if (videoInput != null) {
            videoInput!!.setNewFrameListener(null)
            videoInput!!.close()
        }
        if (glSurfaceView != null) {
            glSurfaceView!!.visibility = View.GONE
        }
        if (facemesh != null) {
            facemesh!!.close()
        }
    }

    private fun logNoseLandmark(result: FaceMeshResult?, showPixelValues: Boolean) {
        if (result == null || result.multiFaceLandmarks().isEmpty()) {
            return
        }
        val noseLandmark = result.multiFaceLandmarks()[0].landmarkList[1]
        // For Bitmaps, show the pixel values. For texture inputs, show the normalized coordinates.
        if (showPixelValues) {
            val width = result.inputBitmap().width
            val height = result.inputBitmap().height
            Log.i(
                TAG, String.format(
                    "MediaPipe Face Mesh nose coordinates (pixel values): x=%f, y=%f",
                    noseLandmark.x * width, noseLandmark.y * height
                )
            )
        } else {
            Log.i(
                TAG, String.format(
                    "MediaPipe Face Mesh nose normalized coordinates (value range: [0, 1]): x=%f, y=%f",
                    noseLandmark.x, noseLandmark.y
                )
            )
        }
    }

    private fun replaceDemoFragment(fragment: Fragment) {
        val hasCameraPermission = PermissionChecker.checkSelfPermission(this,
            Manifest.permission.CAMERA
        )
        //val hasStoragePermission =
        //    PermissionChecker.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (hasCameraPermission != PermissionChecker.PERMISSION_GRANTED /*|| hasStoragePermission != PermissionChecker.PERMISSION_GRANTED*/) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.CAMERA
                )) {
                ToastUtils.show(R.string.permission_tip)
            }
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.CAMERA/*,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.RECORD_AUDIO*/
                ),
                REQUEST_CAMERA
            )
            return
        }
        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(R.id.fragment_container, fragment)
        transaction.commitAllowingStateLoss()
    }
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CAMERA -> {
                val hasCameraPermission = PermissionChecker.checkSelfPermission(this,
                    Manifest.permission.CAMERA
                )
                if (hasCameraPermission == PermissionChecker.PERMISSION_DENIED) {
                    ToastUtils.show(R.string.permission_tip)
                    return
                }
//                replaceDemoFragment(DemoMultiCameraFragment())
                replaceDemoFragment(DemoFragment())
                ToastUtils.show("replaceDemoFragment(DemoFragment()):onRequest..()")
//                replaceDemoFragment(GlSurfaceFragment())
            }
            REQUEST_STORAGE -> {
                val hasCameraPermission =
                    PermissionChecker.checkSelfPermission(this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                if (hasCameraPermission == PermissionChecker.PERMISSION_DENIED) {
                    ToastUtils.show(R.string.permission_tip)
                    return
                }
                //
            }
            else -> {
            }
        }
    }

    companion object {
        private const val REQUEST_CAMERA = 0
        private const val REQUEST_STORAGE = 1
    }
}