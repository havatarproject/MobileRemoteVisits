package com.firebasevideocall.webrtc

import android.util.Log
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

open class MySdpObserver : SdpObserver {
    override fun onCreateSuccess(desc: SessionDescription?) {
        Log.d(WebRTCClient.TAG, "SDP created successfully")
    }

    override fun onSetSuccess() {
        Log.d(WebRTCClient.TAG, "SDP set successfully")
    }

    override fun onCreateFailure(error: String?) {
        Log.e(WebRTCClient.TAG, "SDP creation failed: $error")
    }

    override fun onSetFailure(error: String?) {
        Log.e(WebRTCClient.TAG, "SDP setting failed: $error")
    }
}