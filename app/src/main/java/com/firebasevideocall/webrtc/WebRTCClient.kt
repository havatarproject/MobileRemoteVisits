package com.firebasevideocall.webrtc

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.firebasevideocall.utils.DataModel
import com.firebasevideocall.utils.DataModelType
import com.google.gson.Gson
import org.webrtc.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebRTCClient @Inject constructor(
    private val context: Context,
    private val gson: Gson
) {
    //class variables
    var listener: Listener? = null
    private lateinit var username: String

    //webrtc variables
    private val eglBaseContext = EglBase.create().eglBaseContext
    private val peerConnectionFactory by lazy { createPeerConnectionFactory() }
    private var peerConnection: PeerConnection? = null
    private var isReconnecting = false
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var target = "";

    private val iceServer = listOf(
        // STUN server
        PeerConnection.IceServer.builder("SERVER").createIceServer(),
        // TURN servers with username and password
        PeerConnection.IceServer.builder("SERVER")
            .setUsername("USERNAME")
            .setPassword("USERNAME")
            .createIceServer(),
        PeerConnection.IceServer.builder("SERVER")
            .setUsername("USERNAME")
            .setPassword("USERNAME+Id")
            .createIceServer()
    )
    private val localVideoSource by lazy { peerConnectionFactory.createVideoSource(false) }
    private val localAudioSource by lazy { peerConnectionFactory.createAudioSource(MediaConstraints())}
    private val videoCapturer = getVideoCapturer(context)
    private var surfaceTextureHelper:SurfaceTextureHelper?=null
    private val mediaConstraint = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo","true"))
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio","true"))
    }

    //call variables
    private lateinit var localSurfaceView: SurfaceViewRenderer
    private lateinit var remoteSurfaceView: SurfaceViewRenderer
    private var localStream: MediaStream? = null
    private var localTrackId = ""
    private var localStreamId = ""
    private var localAudioTrack:AudioTrack?=null
    private var localVideoTrack:VideoTrack?=null


    private fun initializeNetworkCallback() {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                Handler(Looper.getMainLooper()).post {
                    Log.d(TAG, "Network available: $network")
                    handleNetworkChange()
                }
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                Handler(Looper.getMainLooper()).post {
                    Log.d(TAG, "Network lost: $network")
                    isReconnecting = true
                }
            }
        }

        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback!!)
    }

    private fun handleNetworkChange() {
        if (!isReconnecting) return

        peerConnection?.let { pc ->
            if (pc.connectionState() == PeerConnection.PeerConnectionState.DISCONNECTED ||
                pc.connectionState() == PeerConnection.PeerConnectionState.FAILED) {

                Log.d(TAG, "Restarting ICE after network change")
                restartIce()
            }
        }
    }

    private fun restartIce() {
        try {
            // Update ICE configuration
            val rtcConfig = PeerConnection.RTCConfiguration(iceServer).apply {
                bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
                rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
                iceTransportsType = PeerConnection.IceTransportsType.ALL
                continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
                enableCpuOveruseDetection = true
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            }

            peerConnection?.setConfiguration(rtcConfig)

            // Recreate offer if we're the caller
            if (::username.isInitialized) {
                createAndSetLocalOffer()
            }

            isReconnecting = false
        } catch (e: Exception) {
            Log.e(TAG, "Error during ICE restart", e)
        }
    }

    private fun createAndSetLocalOffer() {
        peerConnection?.createOffer(object : MySdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                super.onCreateSuccess(desc)
                peerConnection?.setLocalDescription(object : MySdpObserver() {
                    override fun onSetSuccess() {
                        super.onSetSuccess()
                        // Send offer through your existing listener
                        desc?.description?.let { description ->
                            listener?.onTransferEventToSocket(
                                DataModel(
                                    type = DataModelType.Offer,
                                    sender = username,
                                    target = "", // Set appropriate target
                                    data = description
                                )
                            )
                        }
                    }
                }, desc)
            }
        }, mediaConstraint)
    }

    //installing requirements section
    init {
        initPeerConnectionFactory()
    }
    private fun initPeerConnectionFactory() {
        Log.d(TAG, "Initializing PeerConnectionFactory")
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true).setFieldTrials("WebRTC-H264HighProfile/Enabled/")
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)
    }
    private fun createPeerConnectionFactory(): PeerConnectionFactory {
        Log.d(TAG, "Creating PeerConnectionFactory")
        return PeerConnectionFactory.builder()
            .setVideoDecoderFactory(
                DefaultVideoDecoderFactory(eglBaseContext)
            ).setVideoEncoderFactory(
                DefaultVideoEncoderFactory(
                    eglBaseContext, true, true
                )
            ).setOptions(PeerConnectionFactory.Options().apply {
                disableNetworkMonitor = false
                disableEncryption = false
            }).createPeerConnectionFactory()
    }
    fun initializeWebrtcClient(
        username: String, observer: PeerConnection.Observer
    ) {
        Log.d(TAG, "Initializing WebRTC client")
        this.username = username
        Log.d(TAG, "username: $username")
        localTrackId = "${username}_track"
        Log.d(TAG, "localTrackId: $localTrackId")
        localStreamId = "${username}_stream"
        Log.d(TAG, "localStreamId: $localStreamId")
        peerConnection = createPeerConnection(observer)
        Log.d(TAG, "peerConnection: $peerConnection")

        initializeNetworkCallback()
    }

    private fun createPeerConnection(observer: PeerConnection.Observer): PeerConnection? {
        var retryCount = 0
        val maxRetries = 5

        while (retryCount < maxRetries) {
            try {
                return peerConnectionFactory.createPeerConnection(iceServer, observer)
            } catch (e: Exception) {
                Log.e(TAG, "Error creating peer connection (attempt ${retryCount + 1}/$maxRetries)", e)
                retryCount++
                if (retryCount < maxRetries) {
                    Thread.sleep(1000) // Wait 1 second before retrying
                }
            }
        }
        return null
    }

    //negotiation section
    fun call(target:String){
        Log.d(TAG, "Calling $target")
        this@WebRTCClient.target = target;
        peerConnection?.createOffer(object : MySdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                super.onCreateSuccess(desc)
                peerConnection?.setLocalDescription(object : MySdpObserver() {
                    override fun onSetSuccess() {
                        super.onSetSuccess()
                        listener?.onTransferEventToSocket(
                            DataModel(type = DataModelType.Offer,
                                sender = username,
                                target = target,
                                data = desc?.description)
                        )
                    }
                },desc)
            }
        },mediaConstraint)
    }

    fun answer(target:String){
        Log.d(TAG, "Answering $target")
        peerConnection?.createAnswer(object : MySdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                super.onCreateSuccess(desc)
                peerConnection?.setLocalDescription(object : MySdpObserver() {
                    override fun onSetSuccess() {
                        super.onSetSuccess()
                        listener?.onTransferEventToSocket(
                            DataModel(type = DataModelType.Answer,
                                sender = username,
                                target = target,
                                data = desc?.description)
                        )
                    }
                },desc)
            }
        },mediaConstraint)
    }

    fun onRemoteSessionReceived(sessionDescription: SessionDescription){
        Log.d(TAG, "onRemoteSessionReceived")
        peerConnection?.setRemoteDescription(MySdpObserver(),sessionDescription)
    }

    fun addIceCandidateToPeer(iceCandidate: IceCandidate){
        Log.d(TAG, "addIceCandidateToPeer")
        peerConnection?.addIceCandidate(iceCandidate)
    }

    fun sendIceCandidate(target: String,iceCandidate: IceCandidate){
        Log.d(TAG, "sendIceCandidate")
        addIceCandidateToPeer(iceCandidate)
        listener?.onTransferEventToSocket(
            DataModel(
                type = DataModelType.IceCandidates,
                sender = username,
                target = target,
                data = gson.toJson(iceCandidate)
            )
        )
    }

    fun closeConnection() {
        try {
            Log.d(TAG, "closeConnection")
            // Unregister network callback
            networkCallback?.let { callback ->
                val connectivityManager =
                    context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                connectivityManager.unregisterNetworkCallback(callback)
            }
            networkCallback = null

            // Your existing cleanup code
            videoCapturer.dispose()
            localStream?.dispose()
            peerConnection?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun switchCamera(){
        Log.d(TAG, "switchCamera")
        videoCapturer.switchCamera(null)
    }

    fun toggleAudio(shouldBeMuted:Boolean){
        if (shouldBeMuted){
            localStream?.removeTrack(localAudioTrack)
        }else{
            localStream?.addTrack(localAudioTrack)
        }
    }

    fun toggleVideo(shouldBeMuted: Boolean){
        try {
            if (shouldBeMuted){
                stopCapturingCamera()
            }else{
                startCapturingCamera(localSurfaceView)
            }
        }catch (e:Exception){
            e.printStackTrace()
        }
    }

    //streaming section
    private fun initSurfaceView(view: SurfaceViewRenderer) {
        view.run {
            setMirror(false)
            setEnableHardwareScaler(true)
            init(eglBaseContext, null)
        }
    }
    fun initRemoteSurfaceView(view:SurfaceViewRenderer){
        this.remoteSurfaceView = view
        initSurfaceView(view)
    }
    fun initLocalSurfaceView(localView: SurfaceViewRenderer, isVideoCall: Boolean) {
        this.localSurfaceView = localView
        initSurfaceView(localView)
        startLocalStreaming(localView, isVideoCall)
    }
    private fun startLocalStreaming(localView: SurfaceViewRenderer, isVideoCall: Boolean) {
        Log.d(TAG, "startLocalStreaming")
        localStream = peerConnectionFactory.createLocalMediaStream(localStreamId)
        if (isVideoCall){
            Log.d(TAG, "isVideoCall")
            startCapturingCamera(localView)
        }

        localAudioTrack = peerConnectionFactory.createAudioTrack(localTrackId+"_audio",localAudioSource)
        localStream?.addTrack(localAudioTrack)
        peerConnection?.addStream(localStream)
    }
    private fun startCapturingCamera(localView: SurfaceViewRenderer){
        surfaceTextureHelper = SurfaceTextureHelper.create(
            Thread.currentThread().name,eglBaseContext
        )

        videoCapturer.initialize(
            surfaceTextureHelper,context,localVideoSource.capturerObserver
        )

        videoCapturer.startCapture(
            720,480,20
        )

        localVideoTrack = peerConnectionFactory.createVideoTrack(localTrackId+"_video",localVideoSource)
        localVideoTrack?.addSink(localView)
        localStream?.addTrack(localVideoTrack)
    }
    private fun getVideoCapturer(context: Context): CameraVideoCapturer {
        // Check if Camera2 is supported
        if (Camera2Enumerator.isSupported(context)) {
            return Camera2Enumerator(context).run {
                deviceNames.find {
                    isFrontFacing(it)
                }?.let {
                    createCapturer(it, null)
                } ?: throw IllegalStateException("No front-facing camera found with Camera2.")
            }
        } else {
            // Fallback to Camera1 if Camera2 is not supported
            return Camera1Enumerator(true).run {
                deviceNames.find {
                    isFrontFacing(it)
                }?.let {
                    createCapturer(it, null)
                } ?: throw IllegalStateException("No front-facing camera found with Camera1.")
            }
        }
    }
    private fun stopCapturingCamera(){
        videoCapturer.dispose()
        localVideoTrack?.removeSink(localSurfaceView)
        localSurfaceView.clearImage()
        localStream?.removeTrack(localVideoTrack)
        localVideoTrack?.dispose()
    }


    interface Listener {
        fun onTransferEventToSocket(data: DataModel)
    }

    companion object {
        const val TAG: String = "WebRTCClient"
    }
}