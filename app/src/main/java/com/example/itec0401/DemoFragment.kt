package com.example.itec0401

import android.os.Bundle
import android.view.Gravity
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.itec0401.databinding.FragmentDemoBinding
import com.jiangdg.ausbc.MultiCameraClient
import com.jiangdg.ausbc.base.CameraFragment
import com.jiangdg.ausbc.callback.ICameraStateCallBack
import com.jiangdg.ausbc.camera.bean.CameraRequest
import com.jiangdg.ausbc.render.env.RotateType
import com.jiangdg.ausbc.utils.ToastUtils
import com.jiangdg.ausbc.utils.bus.BusKey
import com.jiangdg.ausbc.utils.bus.EventBus
import com.jiangdg.ausbc.widget.AspectRatioTextureView
import com.jiangdg.ausbc.widget.IAspectRatio

class DemoFragment: CameraFragment() {
    private var mViewBinding: FragmentDemoBinding? = null

    override fun getRootView(inflater: LayoutInflater, container: ViewGroup?): View? {
        if (mViewBinding == null) {
            mViewBinding = FragmentDemoBinding.inflate(inflater, container, false)
        }
        return mViewBinding!!.root
    }

    // if you want offscreen render
    // please return null
    override fun getCameraView(): IAspectRatio? {
        return AspectRatioTextureView(requireContext())
    }

    // if you want offscreen render
    // please return null, the same as getCameraView()
    override fun getCameraViewContainer(): ViewGroup? {
        return mViewBinding?.container
    }

    // camera open status callback
    override fun onCameraState(self: MultiCameraClient.ICamera,
                               code: ICameraStateCallBack.State,
                               msg: String?) {
        when (code) {
            ICameraStateCallBack.State.OPENED -> handleCameraOpened()
            ICameraStateCallBack.State.CLOSED -> handleCameraClosed()
            ICameraStateCallBack.State.ERROR -> handleCameraError(msg)
        }
    }

    private fun handleCameraError(msg: String?) {
        //mViewBinding.uvcLogoIv.visibility = View.VISIBLE
        mViewBinding!!.frameRateTv.visibility = View.GONE
        ToastUtils.show("camera opened error: $msg")
    }

    private fun handleCameraClosed() {
        mViewBinding!!.frameRateTv.visibility = View.GONE
        ToastUtils.show("camera closed success")
    }

    private fun handleCameraOpened() {
        mViewBinding!!.frameRateTv.visibility = View.VISIBLE
        ToastUtils.show("camera opened success")
    }

    override fun getGravity(): Int = Gravity.TOP

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initView()
        initData()
    }

    override fun initData() {
        super.initData()
        EventBus.with<Int>(BusKey.KEY_FRAME_RATE).observe(this, {
            mViewBinding!!.frameRateTv.text = "frame rate:  $it fps"
        })
    }
    override fun getCameraRequest(): CameraRequest {
        return CameraRequest.Builder()
            .setPreviewWidth(1300) // camera preview width
            .setPreviewHeight(1080) // camera preview height
            .setRenderMode(CameraRequest.RenderMode.OPENGL) // camera render mode
            .setDefaultRotateType(RotateType.ANGLE_0) // rotate camera image when opengl mode
            .setAudioSource(CameraRequest.AudioSource.SOURCE_AUTO) // set audio source
            .setAspectRatioShow(true) // aspect render,default is true
            .setCaptureRawImage(false) // capture raw image picture when opengl mode
            .setRawPreviewData(false)  // preview raw image when opengl mode
            .create()
    }
}