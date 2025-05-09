package com.firebasevideocall.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.firebasevideocall.R
import com.firebasevideocall.repository.MainRepository
import com.firebasevideocall.service.MainServiceActions.*
import com.firebasevideocall.utils.AppointmentData
import com.firebasevideocall.utils.DataModel
import com.firebasevideocall.utils.DataModelType
import com.firebasevideocall.utils.isValid
import com.firebasevideocall.webrtc.RTCAudioManager
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.AndroidEntryPoint
import org.webrtc.SurfaceViewRenderer
import javax.inject.Inject

@AndroidEntryPoint
class MainService : Service(), MainRepository.Listener {

    private val TAG = "MainService"

    private var isServiceRunning = false
    private var username: String? = null

    @Inject
    lateinit var mainRepository: MainRepository

    private lateinit var notificationManager: NotificationManager
    private lateinit var rtcAudioManager: RTCAudioManager
    private var isPreviousCallStateVideo = true

    companion object {
        var listener: Listener? = null
        var endCallListener:EndCallListener?=null
        var localSurfaceView: SurfaceViewRenderer?=null
        var remoteSurfaceView: SurfaceViewRenderer?=null
    }

    override fun onCreate() {
        super.onCreate()
        rtcAudioManager = RTCAudioManager.create(this)
        rtcAudioManager.setDefaultAudioDevice(RTCAudioManager.AudioDevice.SPEAKER_PHONE)
        notificationManager = getSystemService(
            NotificationManager::class.java
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let { incomingIntent ->
            when (incomingIntent.action) {
                START_SERVICE.name -> handleStartService(incomingIntent)
                SETUP_VIEWS.name -> handleSetupViews(incomingIntent)
                END_CALL.name -> handleEndCall()
                SWITCH_CAMERA.name -> handleSwitchCamera()
                TOGGLE_AUDIO.name -> handleToggleAudio(incomingIntent)
                TOGGLE_VIDEO.name -> handleToggleVideo(incomingIntent)
                TOGGLE_AUDIO_DEVICE.name -> handleToggleAudioDevice(incomingIntent)
                STOP_SERVICE.name -> handleStopService()
                else -> Unit
            }
        }

        return START_STICKY
    }

    override fun onBind(p0: Intent?): IBinder? {
        TODO("Not yet implemented")
    }

    private fun handleStopService() {
        mainRepository.endCall()
        mainRepository.logOff {
            isServiceRunning = false
            stopSelf()
        }
    }

    private fun handleToggleAudioDevice(incomingIntent: Intent) {
        val type = when(incomingIntent.getStringExtra("type")){
            RTCAudioManager.AudioDevice.EARPIECE.name -> RTCAudioManager.AudioDevice.EARPIECE
            RTCAudioManager.AudioDevice.SPEAKER_PHONE.name -> RTCAudioManager.AudioDevice.SPEAKER_PHONE
            else -> null
        }

        type?.let {
            rtcAudioManager.setDefaultAudioDevice(it)
            rtcAudioManager.selectAudioDevice(it)
            Log.d(TAG, "handleToggleAudioDevice: $it")
        }
    }

    private fun handleToggleVideo(incomingIntent: Intent) {
        val shouldBeMuted = incomingIntent.getBooleanExtra("shouldBeMuted",true)
        this.isPreviousCallStateVideo = !shouldBeMuted
        mainRepository.toggleVideo(shouldBeMuted)
    }

    private fun handleToggleAudio(incomingIntent: Intent) {
        val shouldBeMuted = incomingIntent.getBooleanExtra("shouldBeMuted",true)
        mainRepository.toggleAudio(shouldBeMuted)
    }

    private fun handleSwitchCamera() {
        mainRepository.switchCamera()
    }

    private fun handleEndCall() {
        //1. we have to send a signal to other peer that call is ended
        mainRepository.sendEndCall()
        //2.end out call process and restart our webrtc client
        endCallAndRestartRepository()
    }

    private fun endCallAndRestartRepository() {
        mainRepository.endCall()
        endCallListener?.onCallEnded()
        username?.let {
            mainRepository.initWebrtcClient(it)
        } ?: Log.e(TAG, "Username is null in endCallAndRestartRepository")
    }

    private fun handleSetupViews(incomingIntent: Intent) {
        val isCaller = incomingIntent.getBooleanExtra("isCaller", false)
        val isVideoCall = incomingIntent.getBooleanExtra("isVideoCall", true)
        val target = incomingIntent.getStringExtra("target")
        this.isPreviousCallStateVideo = isVideoCall
        if (target == null) {
            Log.e(TAG, "Target is null in handleSetupViews")
            return
        }
        mainRepository.setTarget(target)

        localSurfaceView?.let { localView ->
            mainRepository.initLocalSurfaceView(localView, isVideoCall)
        } ?: Log.e(TAG, "localSurfaceView is null")

        remoteSurfaceView?.let { remoteView ->
            mainRepository.initRemoteSurfaceView(remoteView)
        } ?: Log.e(TAG, "remoteSurfaceView is null")

        if (!isCaller) {
            mainRepository.startCall()
        }
    }

    private fun handleStartService(incomingIntent: Intent) {
        //start our foreground service
        if (!isServiceRunning) {
            isServiceRunning = true
            username = incomingIntent.getStringExtra("username")
            startServiceWithNotification()

            mainRepository.listener = this
            mainRepository.initFirebase()
            mainRepository.initWebrtcClient(username!!)
        }
    }

    private fun startServiceWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                "channel1", "foreground", NotificationManager.IMPORTANCE_NONE
            )

            val intent = Intent(this, MainServiceReceiver::class.java).apply {
                action = "ACTION_EXIT"
            }
            val pendingIntent: PendingIntent =
                PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

            val notificationManager = getSystemService(   Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(notificationChannel)

            val notification = NotificationCompat.Builder(this, "channel1")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("")
                .setContentText("")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(R.drawable.ic_end_call, "Exit", pendingIntent)
                .build()

            startForeground(1, notification)
            notificationManager.cancel(1)

        }
    }

    override fun onLatestEventReceived(data: DataModel) {
        if (data.isValid()) {
            when (data.type) {
                DataModelType.StartVideoCall,
                DataModelType.StartAudioCall -> {
                        listener?.onCallReceived(data)
                }
                else -> Unit
            }
        }
    }

    override fun endCall() {
        endCallAndRestartRepository()
    }

    interface Listener {
        fun onCallReceived(model: DataModel)
    }

    interface EndCallListener {
        fun onCallEnded()
    }
}