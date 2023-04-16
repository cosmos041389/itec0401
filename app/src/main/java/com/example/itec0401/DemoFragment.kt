package com.example.itec0401

//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.launch
//import kotlinx.coroutines.withContext
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.itec0401.databinding.FragmentDemoBinding
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
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
import kotlinx.coroutines.*

class DemoFragment: CameraFragment(), CoroutineScope by MainScope(){
    private var mViewBinding: FragmentDemoBinding? = null
    private var wearableDeviceConnected: Boolean = false

    /* xml 레이아웃 파일에 Access 할 수 있게 함 */
    //private var _binding: FragmentDemoBinding? = null
    private val binding get() = mViewBinding!!

    private val wearableAppCheckPayload = "AppOpenWearable"
    private val wearableAppCheckPayloadReturnACK = "AppOpenWearableACK"

    private var currentAckFromWearForAppOpenCheck: String? = null
    private val APP_OPEN_WEARABLE_PAYLOAD_PATH = "/APP_OPEN_WEARABLE_PAYLOAD"

    private val TAG_GET_NODES: String = "getnodes1"
    private val TAG_MESSAGE_RECEIVED: String = "receive1"

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



    @SuppressLint("SuspiciousIndentation")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initView()
        initData()
        wearableDeviceConnected = false

        /* wearable device가 연결되었는지 확인하는 버튼 */
        binding.startBtn.setOnClickListener {
            Log.d("연결 확인 버튼","클릭")
            if (!wearableDeviceConnected) {
                val tempAct: Activity = requireActivity() as AppCompatActivity
                //Couroutine
                initialiseDevicePairing(tempAct)
            }
        }
    }
    /* ADD WatchConnectButton Code Function */
    @SuppressLint("SetTextI18n")
    private fun initialiseDevicePairing(tempAct: Activity) {
        var cardColor = ContextCompat.getColor(requireContext(), R.color.line_primary)

        /* Coroutine */
        launch(Dispatchers.Default) {
            var getNodesResBool: BooleanArray? = null
            try {
                Log.d("try", "try get nodes res bool")
                getNodesResBool =
                    getNodes(tempAct.applicationContext)
            } catch (e: Exception) {
                Log.d("try", "try exception")
                e.printStackTrace()
            }

            // UI Thread
            withContext(Dispatchers.Main) {
                getNodesResBool?.get(1)?.let { Log.d("getnodesresbool : ", it.toString()) }
                if (getNodesResBool!![0]) {
                    // if message Acknowlegement Received
                    if (getNodesResBool[1]) {
                        //워치와 연결, 앱이 열려있음
                        wearableDeviceConnected = true
                        DevicePairing.wearableDeviceConnected = true
                        cardColor = ContextCompat.getColor(requireContext(), R.color.primary)
                        binding.checkBtn.setCardBackgroundColor(cardColor)
                        binding.check.setText("성공")
                    /* */
                    } else {
                        //워치와 연결, 앱이 닫혀있음
                        wearableDeviceConnected = false
                        DevicePairing.wearableDeviceConnected = false
                        //binding.sendmessageButton.visibility = View.GONE
                        cardColor = ContextCompat.getColor(requireContext(), R.color.purple_700)
                        binding.checkBtn.setCardBackgroundColor(cardColor)
                        binding.check.setText("성공.")
                    }
                } else {
                    //워치와 연결되지 않음
                    wearableDeviceConnected = false
                    DevicePairing.wearableDeviceConnected = false
                    //binding.sendmessageButton.visibility = View.GONE
                    cardColor = ContextCompat.getColor(requireContext(), R.color.grey)
                    binding.checkBtn.setCardBackgroundColor(cardColor)
                    binding.check.setText("실패")
                }
            }
        }
    }

    /* 워치 연결 상태에 따른 결과를 반환해주는 함수 */
    private fun getNodes(context: Context): BooleanArray {
        val nodeResults = HashSet<String>()
        val resBool = BooleanArray(2)
        resBool[0] = false //nodePresent : true이면 연결되어있다
        resBool[1] = false //wearableReturnAckR eceived : true이면 워치에 앱이 열려있다
        val nodeListTask =
            Wearable.getNodeClient(context).connectedNodes
        try {
            // Block on a task and get the result synchronously (because this is on a background thread).
            val nodes =
                Tasks.await(
                    nodeListTask
                )
            //Log.e(TAG_GET_NODES, "Task fetched nodes")
            for (node in nodes) {
                //Log.e(TAG_GET_NODES, "inside loop")
                nodeResults.add(node.id)
                try {
                    val nodeId = node.id
                    // Set the data of the message to be the bytes of the Uri.
                    val payload: ByteArray = wearableAppCheckPayload.toByteArray()
                    DevicePairing.nodeId = nodeId
                    //DevicePairing.wearableDeviceConnected=wearableDeviceConnected
                    // Send the rpc
                    // Instantiates clients without member variables, as clients are inexpensive to
                    // create. (They are cached and shared between GoogleApi instances.)
                    val sendMessageTask =
                        Wearable.getMessageClient(context)
                            .sendMessage(nodeId, APP_OPEN_WEARABLE_PAYLOAD_PATH, payload)
                    Log.d("sendMessageToWearable", "send message")
                    //워치로 메시지를 보내고 5번동안 받으면 앱이 열려있고 안받으면 앱이 없다
                    try {
                        // Block on a task and get the result synchronously (because this is on a background thread).
                        val result = Tasks.await(sendMessageTask)
                        Log.d(TAG_GET_NODES, "send message result : $result")
                        resBool[0] = true
                        //Wait for 1000 ms/1 sec for the acknowledgement message
                        //Wait 1
                        if (currentAckFromWearForAppOpenCheck != wearableAppCheckPayloadReturnACK) {
                            Thread.sleep(100)
                            Log.d(TAG_GET_NODES, "ACK thread sleep 1")
                        }
                        if (currentAckFromWearForAppOpenCheck == wearableAppCheckPayloadReturnACK) {
                            resBool[1] = true
                            return resBool
                        }
                        //Wait 2
                        if (currentAckFromWearForAppOpenCheck != wearableAppCheckPayloadReturnACK) {
                            Thread.sleep(150)
                            Log.d(TAG_GET_NODES, "ACK thread sleep 2")
                        }
                        if (currentAckFromWearForAppOpenCheck == wearableAppCheckPayloadReturnACK) {
                            resBool[1] = true
                            return resBool
                        }
                        //Wait 3
                        if (currentAckFromWearForAppOpenCheck != wearableAppCheckPayloadReturnACK) {
                            Thread.sleep(200)
                            Log.d(TAG_GET_NODES, "ACK thread sleep 3")
                        }
                        if (currentAckFromWearForAppOpenCheck == wearableAppCheckPayloadReturnACK) {
                            resBool[1] = true
                            return resBool
                        }
                        //Wait 4
                        if (currentAckFromWearForAppOpenCheck != wearableAppCheckPayloadReturnACK) {
                            Thread.sleep(250)
                            Log.d(TAG_GET_NODES, "ACK thread sleep 4")
                        }
                        if (currentAckFromWearForAppOpenCheck == wearableAppCheckPayloadReturnACK) {
                            resBool[1] = true
                            return resBool
                        }
                        //Wait 5
                        if (currentAckFromWearForAppOpenCheck != wearableAppCheckPayloadReturnACK) {
                            Thread.sleep(350)
                            Log.d(TAG_GET_NODES, "ACK thread sleep 5")
                        }
                        if (currentAckFromWearForAppOpenCheck == wearableAppCheckPayloadReturnACK) {
                            resBool[1] = true
                            return resBool
                        }
                        resBool[1] = false
                        Log.d(
                            TAG_GET_NODES,
                            "ACK thread timeout, no message received from the wearable "
                        )
                    } catch (exception: Exception) {
                        exception.printStackTrace()
                    }
                } catch (e1: Exception) {
                    Log.d(TAG_GET_NODES, "send message exception")
                    e1.printStackTrace()
                }
            } //end of for loop
        } catch (exception: Exception) {
            Log.e(TAG_GET_NODES, "Task failed: $exception")
            exception.printStackTrace()
        }
        return resBool
    }
    /* ADD onDestroyView */
    override fun onDestroyView() {
        super.onDestroyView()
        //_binding = null
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

    fun setVisibility(){
        mViewBinding?.container?.visibility = View.INVISIBLE
    }
}