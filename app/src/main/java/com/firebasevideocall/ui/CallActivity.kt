package com.firebasevideocall.ui

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.isVisible
import com.firebasevideocall.R
import com.firebasevideocall.databinding.ActivityCallBinding
import com.firebasevideocall.service.MainService
import com.firebasevideocall.service.MainServiceRepository
import com.firebasevideocall.utils.convertToHumanTime
import com.firebasevideocall.webrtc.RTCAudioManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class CallActivity : AppCompatActivity(), MainService.EndCallListener {

    private var target:String?=null
    private var isVideoCall:Boolean= true
    private var isCaller:Boolean = true

    private var isMicrophoneMuted = false
    private var isCameraMuted = false
    private var isSpeakerMode = true

    @Inject lateinit var serviceRepository: MainServiceRepository

    private lateinit var views:ActivityCallBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        views = ActivityCallBinding.inflate(layoutInflater)
        onBackPressedDispatcher.addCallback(this, onBackInvokedCallback)
        setContentView(views.root)
        init()
    }

    private fun init(){
        intent.getStringExtra("target")?.let {
            this.target = it
        }?: kotlin.run {
            finish()
        }

        isVideoCall = intent.getBooleanExtra("isVideoCall",true)
        isCaller = intent.getBooleanExtra("isCaller",true)

        views.apply {
            callTitleTv.text = getString(R.string.in_call_with, target)
            CoroutineScope(Dispatchers.IO).launch {
                for (i in 0..3600){
                    delay(1000)
                    withContext(Dispatchers.Main){
                        //convert this int to human readable time
                        callTimerTv.text = i.convertToHumanTime()
                    }
                }
            }

            if (!isVideoCall){
                toggleCameraButton.isVisible = false
                switchCameraButton.isVisible = false
            }

            MainService.remoteSurfaceView = remoteView
            MainService.localSurfaceView = localView
            serviceRepository.setupViews(isVideoCall,isCaller,target!!)

            endCallButton.setOnClickListener {
                serviceRepository.sendEndCall()
            }

            switchCameraButton.setOnClickListener {
                serviceRepository.switchCamera()
            }
        }
        setupMicToggleClicked()
        setupCameraToggleClicked()
        setupToggleAudioDevice()
        MainService.endCallListener = this
    }

    private fun setupMicToggleClicked(){
        views.apply {
            toggleMicrophoneButton.setOnClickListener {
                if (!isMicrophoneMuted){
                    serviceRepository.toggleAudio(true)
                    toggleMicrophoneButton.setImageResource(R.drawable.ic_mic_on)
                }else{
                    serviceRepository.toggleAudio(false)
                    toggleMicrophoneButton.setImageResource(R.drawable.ic_mic_off)
                }
                isMicrophoneMuted = !isMicrophoneMuted
            }
        }
    }

    private val onBackInvokedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            serviceRepository.sendEndCall()
        }
    }

    private fun setupToggleAudioDevice(){
        views.apply {
            toggleAudioDevice.setOnClickListener {
                if (isSpeakerMode){
                    toggleAudioDevice.setImageResource(R.drawable.ic_speaker)
                    serviceRepository.toggleAudioDevice(RTCAudioManager.AudioDevice.EARPIECE.name)

                }else{
                    toggleAudioDevice.setImageResource(R.drawable.ic_ear)
                    serviceRepository.toggleAudioDevice(RTCAudioManager.AudioDevice.SPEAKER_PHONE.name)

                }
                isSpeakerMode = !isSpeakerMode
            }

        }
    }

    private fun setupCameraToggleClicked(){
        views.apply {
            toggleCameraButton.setOnClickListener {
                if (!isCameraMuted){
                    serviceRepository.toggleVideo(true)
                    toggleCameraButton.setImageResource(R.drawable.ic_camera_on)
                }else{
                    serviceRepository.toggleVideo(false)
                    toggleCameraButton.setImageResource(R.drawable.ic_camera_off)
                }

                isCameraMuted = !isCameraMuted
            }
        }
    }

    override fun onCallEnded() {
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        MainService.remoteSurfaceView?.release()
        MainService.remoteSurfaceView = null

        MainService.localSurfaceView?.release()
        MainService.localSurfaceView =null
    }
}