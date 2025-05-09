package com.firebasevideocall.repository

import android.util.Log
import com.firebasevideocall.firebaseClient.FirebaseClient
import com.firebasevideocall.utils.AppointmentData
import com.firebasevideocall.utils.DataModel
import com.firebasevideocall.utils.DataModelType.*
import com.firebasevideocall.utils.UserStatus
import com.firebasevideocall.webrtc.MyPeerObserver
import com.firebasevideocall.webrtc.WebRTCClient
import com.google.gson.Gson
import org.webrtc.*
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MainRepository @Inject constructor(
    private val firebaseClient: FirebaseClient,
    private val webRTCClient: WebRTCClient,
    private val gson: Gson
) : WebRTCClient.Listener {

    private var target: String? = null
    var listener: Listener? = null
    private var remoteView:SurfaceViewRenderer?=null

    fun login(username: String?, id: String?, isDone:(Boolean, String?)-> Unit){
        firebaseClient.login(username, id, isDone)
    }

    fun register(username: String?, id: String?, isDone:(Boolean, String?)-> Unit) {
        firebaseClient.register(username, id, isDone)
    }

    fun updateToken(username: String?, token: String?, isDone:(Boolean, String?)-> Unit) {
        firebaseClient.updateToken(username, token, isDone)
    }

    fun updateIsAdmin(username: String?, isAdmin: String?, isDone:(Boolean, String?)-> Unit) {
        firebaseClient.updateIsAdmin(username, isAdmin, isDone)
    }

    fun observeUsersStatus(status: (List<Pair<String, String>>) -> Unit) {
        firebaseClient.observeUsersStatus(status)
    }

    fun observePermissions(status: (List<Pair<String, Pair<String, String>>>) -> Unit) {
        firebaseClient.observePermissionsStatus(status)
    }

    fun reserveSlot(date: String, hour: String, note: String) {
        firebaseClient.reserveSlot(date, hour, note)
    }

    fun cancelReservation(date: String, hour: String) {
        firebaseClient.cancelReservation(date, hour)
    }

    fun initFirebase() {
        firebaseClient.subscribeForLatestEvent(object : FirebaseClient.Listener {
            override fun onLatestEventReceived(event: DataModel) {
                listener?.onLatestEventReceived(event)
                Log.d("WebRTCClient", "(MainRepository - initFirebase) : event received")
                when (event.type) {
                    Offer->{
                        Log.d("WebRTCClient", "(MainRepository - initFirebase) : Offer received")
                        Log.d("WebRTCClient", "(MainRepository - initFirebase) : Offer received ${event.data.toString()}"
                        )
                        Log.d("WebRTCClient", "(MainRepository - initFirebase) : DataModelType.Offer received")
                        Log.d("WebRTCClient", "(MainRepository - initFirebase) : calling webRTCClient.onRemoteSessionReceived")
                        webRTCClient.onRemoteSessionReceived(
                            SessionDescription(
                                SessionDescription.Type.OFFER,
                                event.data.toString()
                            )
                        )
                        Log.d("WebRTCClient", "(MainRepository - initFirebase) : called webRTCClient.onRemoteSessionReceived")
                        Log.d("WebRTCClient", "(MainRepository - initFirebase) : target is $target")
                        Log.d("WebRTCClient", "(MainRepository - initFirebase) : calling webRTCClient.answer()")
                        target?.let {
                            webRTCClient.answer(it)
                        } ?: run {
                            // Handle the case when target is null
                            Log.e("WebRTCClient", "(MainRepository - initFirebase) : Target is null during Offer")
                            Log.e("initFirebase", "Target is null during Offer")
                        }
                    }
                    Answer->{
                        Log.d("WebRTCClient", "(MainRepository - initFirebase) : Answer received")
                        Log.d("WebRTCClient", "(MainRepository - initFirebase) : Answer received ${event.data.toString()}")
                        Log.d("WebRTCClient", "(MainRepository - initFirebase) : DataModelType.Answer received")
                        webRTCClient.onRemoteSessionReceived(
                            SessionDescription(
                                SessionDescription.Type.ANSWER,
                                event.data.toString()
                            )
                        )
                    }
                    IceCandidates->{
                        val candidate: IceCandidate? = try {
                            gson.fromJson(event.data.toString(), IceCandidate::class.java)
                        }catch (e:Exception){
                            null
                        }
                        candidate?.let {
                            webRTCClient.addIceCandidateToPeer(it)
                        }
                    }
                    EndCall->{
                        listener?.endCall()
                    }
                    else -> {
                        Log.d("WebRTCClient", "(MainRepository - initFirebase) : else received")
                        Log.d("WebRTCClient", "(MainRepository - initFirebase) : else received ${event.data.toString()}")
                        Log.d("WebRTCClient", "(MainRepository - initFirebase) : DataModelType = ${event.type}")
                    }
                }
            }

        })
    }

    fun addAppointmentToFirestore(username: String, appointment: AppointmentData) {
        firebaseClient.addAppointment(username, appointment)
    }

    fun addNotification(userEmail: String, notification: String) {
        firebaseClient.addNotification(userEmail, notification)
    }

    fun deleteNotificationsByUserEmail(userEmail: String) {
        firebaseClient.deleteNotificationsByUserEmail(userEmail)
    }

    fun observeNotificationsByUserEmail(userEmail: String, status: (List<Pair<String, String>>) -> Unit) {
        firebaseClient.observeNotificationsByUserEmail(userEmail, status)
    }

    fun observeNotifications(userEmail: String, status: (List<Pair<String, String>>) -> Unit) {
        firebaseClient.observeNotifications(userEmail, status)
    }

    fun observeAppointments(selectedDate : Date, status: (List<Pair<String, Pair<String, String>>>) -> Unit) {
        firebaseClient.observeAppointments(selectedDate, status)
    }

    fun observeUserAppointments(status: (List<Pair<String, Pair<String, String>>>) -> Unit) {
        firebaseClient.observeUserAppointments(status)
    }

    fun sendConnectionRequest(target: String, isVideoCall: Boolean, success: (Boolean) -> Unit) {
        firebaseClient.sendMessageToOtherClient(
            DataModel(
                type = if (isVideoCall) StartVideoCall else StartAudioCall,
                target = target
            ), success
        )
    }

    fun setTarget(target: String) {
        this.target = target
    }

    interface Listener {
        fun onLatestEventReceived(data: DataModel)
        fun endCall()
    }

    fun initWebrtcClient(username: String) {
        webRTCClient.listener = this
        webRTCClient.initializeWebrtcClient(username, object : MyPeerObserver() {

            override fun onAddStream(p0: MediaStream?) {
                super.onAddStream(p0)
                try {
                    p0?.videoTracks?.get(0)?.addSink(remoteView)
                }catch (e:Exception){
                    e.printStackTrace()
                }

            }

            override fun onIceCandidate(p0: IceCandidate?) {
                super.onIceCandidate(p0)
                p0?.let {
                    webRTCClient.sendIceCandidate(target!!, it)
                }
            }

            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                super.onConnectionChange(newState)
                if (newState == PeerConnection.PeerConnectionState.CONNECTED) {
                    // 1. change my status to in call
                    changeMyStatus(UserStatus.IN_CALL)
                    // 2. clear latest event inside my user section in firebase database
                    firebaseClient.clearLatestEvent()
                }
            }
        })
    }


    fun initLocalSurfaceView(view: SurfaceViewRenderer, isVideoCall: Boolean) {
        webRTCClient.initLocalSurfaceView(view, isVideoCall)
    }

    fun initRemoteSurfaceView(view: SurfaceViewRenderer) {
        webRTCClient.initRemoteSurfaceView(view)
        this.remoteView = view
    }

    fun startCall() {
        webRTCClient.call(target!!)
    }

    @Synchronized
    fun endCall() {
        //webRTCClient.dispose()
        webRTCClient.closeConnection()
        changeMyStatus(UserStatus.ONLINE)
    }

    fun sendEndCall() {
        onTransferEventToSocket(
            DataModel(
                type = EndCall,
                target = target!!
            )
        )
    }

    private fun changeMyStatus(status: UserStatus) {
        firebaseClient.changeMyStatus(status)
    }

    fun toggleAudio(shouldBeMuted: Boolean) {
        webRTCClient.toggleAudio(shouldBeMuted)
    }

    fun toggleVideo(shouldBeMuted: Boolean) {
        webRTCClient.toggleVideo(shouldBeMuted)
    }

    fun switchCamera() {
        webRTCClient.switchCamera()
    }

    override fun onTransferEventToSocket(data: DataModel) {
        firebaseClient.sendMessageToOtherClient(data) {}
    }

    fun observePatients(status: (List<Pair<String, Map<String, Any?>>>) -> Unit) {
        firebaseClient.observePatients(status)
    }

    fun observeBeds(status: (List<Pair<String, Map<String, Any?>>>) -> Unit) {
        firebaseClient.observeBeds(status)
    }

    fun observePatientsByName(name: String, status: (List<Pair<String, Map<String, Any?>>>) -> Unit) {
        firebaseClient.observePatientsByName(name, status)
    }

    fun observePatientsByHealthNumber(healthNum: Int, status: (List<Pair<String, Map<String, Any?>>>) -> Unit) {
        firebaseClient.observePatientsByHealthNumber(healthNum, status)
    }

    private fun observeBuffers(status: (List<Pair<String, Map<String, Any?>>>) -> Unit) {
        firebaseClient.observeBuffers(status)
    }

    fun observeMinutesBetweenAppointmentsBuffer(status: (List<Pair<String, Map<String, Any?>>>) -> Unit) {
        observeBuffers { buffers ->
            status(buffers.filter { it.first == "minutesBetweenAppointments" })
        }
    }

    fun getBufferField(id: String, field: String): Any? {
        val bufferData = firebaseClient.getBufferDocument(id)
        return bufferData?.get(field)
    }

    fun getBufferTime(id: String, callback: (Int?) -> Unit) {
        firebaseClient.getBufferTime(id, callback)
    }

    fun updateBufferTime(id: String, time: Int) {
        firebaseClient.updateBufferTime(id, time)
    }

    fun logOff(function: () -> Unit) = firebaseClient.logOff(function)

}