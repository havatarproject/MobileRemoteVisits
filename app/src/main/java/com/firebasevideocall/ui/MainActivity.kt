package com.firebasevideocall.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.firebasevideocall.adapters.ReservesRecyclerViewAdapter
import com.firebasevideocall.adapters.SlotsRecyclerViewAdapter
import com.firebasevideocall.databinding.ActivityMainBinding
import com.firebasevideocall.repository.MainRepository
import com.firebasevideocall.service.MainService
import com.firebasevideocall.service.MainServiceRepository
import com.firebasevideocall.utils.DataModel
import com.firebasevideocall.utils.DataModelType
import com.firebasevideocall.utils.Utils
import com.firebasevideocall.utils.getCameraAndMicPermission
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*


@AndroidEntryPoint
class MainActivity : AppCompatActivity(), ReservesRecyclerViewAdapter.Listener, MainService.Listener {
    private val TAG = "MainActivity"

    private var isFirstInstantiation = true
    private var listenerJob: Job? = null // To hold a reference to the coroutine job
    private var listenerRegistration: ListenerRegistration? = null


    private lateinit var views: ActivityMainBinding
    private var username: String? = null
    @Inject
    lateinit var mainRepository: MainRepository
    @Inject
    lateinit var mainServiceRepository: MainServiceRepository
    private var reservesAdapter: ReservesRecyclerViewAdapter? = null

    val startForResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Perform the desired startActivity() call here to refresh MainActivity
            val refreshIntent = Intent(this, MainActivity::class.java)
            startActivity(refreshIntent)
        }
    }


    private val requestPermissionLauncher by lazy {
        Utils.registerForNotificationPermission(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate")
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        views = ActivityMainBinding.inflate(layoutInflater)
        setContentView(views.root)
        Utils.askNotificationPermission(this, requestPermissionLauncher)
        updateToken()

        // Initialize the listener only once
        Log.d(TAG, "onCreate: $isFirstInstantiation")
        if (isFirstInstantiation) {
            Log.d(TAG, "onCreate: instatiating listener")
            isFirstInstantiation = false
            startListeningForUpdates()
            Log.d(TAG, "onCreate: listener instantiated")
            if (listenerJob?.isCancelled == true) {
                Log.e(TAG, "Listener coroutine was canceled")
            }
        }

        createNotificationChannel()

        init()
    }

    private fun startListeningForUpdates() {
        Log.d(TAG, "startListeningForUpdates") // Log 1

        // cancel the previous listener
        if (listenerRegistration != null) {
            Log.d(TAG, "startListeningForUpdates: removing previous listener")
            listenerRegistration?.remove()
            listenerRegistration = null
            Log.d(TAG, "startListeningForUpdates: listener removed")
        }

        // Launch a new coroutine for the periodic checks
        listenerJob = CoroutineScope(Dispatchers.IO).launch {
            Log.d(TAG, "startListeningForUpdates: launch thread") // Log 2
            val db = Firebase.firestore
            val collectionRef = db.collection("appointmentSlots")

            // check collection is non empty
            //val snapshot = collectionRef.get().await()
            //if (snapshot.isEmpty) {
               // Log.d(TAG, "startListeningForUpdates: collection is empty")
              //  return@launch
            //}

            Log.d(TAG, "startListeningForUpdates: registering listener") // Log 3
            listenerRegistration = collectionRef.addSnapshotListener { querySnapshot, error ->
                Log.d(TAG, "startListeningForUpdates: querySnapshot $querySnapshot")
                if (error != null) {
                    Log.d(TAG, "Listen failed.", error) // Log 4
                    Log.e("Firestore", "Listen failed.", error)
                    return@addSnapshotListener
                }

                Log.d(TAG, "startListeningForUpdates: querySnapshot $querySnapshot")
                if (querySnapshot != null) {
                    Log.d(TAG, "Data received: ${querySnapshot.documents}") // Log 5
                    // Launch a new coroutine for the periodic checks
                    launch {
                        Log.d(TAG, "startListeningForUpdates: launching coroutine") // Log 6
                        try {
                            while (isActive) {
                                Log.d(TAG, "startListeningForUpdates: Coroutine still active")
                                Log.d(
                                    TAG,
                                    "startListeningForUpdates: checking for updates"
                                ) // Log 7
                                val filteredDocuments = querySnapshot.documents.filter { document ->
                                    document.getString("userEmail") == username &&
                                            document.getString("status") == "ACCEPTED"
                                }

                                for (document in filteredDocuments) {
                                    val appointmentHour = document.getString("hour")
                                    if (appointmentHour != null && isAppointmentWithinTenMinutes(
                                            appointmentHour
                                        )
                                    ) {
                                        val timeToAppointment = computeTimeDifference(appointmentHour)
                                        launch(Dispatchers.Main) {
                                            Toast.makeText(
                                                this@MainActivity,
                                                "Marcação dentro de $timeToAppointment minutos. Hora agendada para $appointmentHour",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            Log.d(TAG, "Marcação dentro de $timeToAppointment minutos. Hora agendada para $appointmentHour")
                                            // add notification to firebase
                                            updateNotification(timeToAppointment, appointmentHour)
                                            mainRepository.addNotification(
                                                username!!,
                                                "Marcação dentro de $timeToAppointment minutos. Hora agendada para $appointmentHour"
                                            )
                                        }
                                    }
                                }
                                Log.d(TAG, "startListeningForUpdates: Sleeping for 5 minutes")
                                delay(5 * 60 * 1000)
                                Log.d(TAG, "startListeningForUpdates: Waking up")
                            }
                        }
                        catch (e: CancellationException) {
                            Log.d(TAG, "startListeningForUpdates: Coroutine canceled.")
                        }
                    }
                }
                else {
                    Log.d(TAG, "startListeningForUpdates: No data received.")
                }
            }

            // Remove the listener when the coroutine is canceled
            if (listenerJob?.isCancelled == true) {
                listenerRegistration?.remove()
            }

            // Keep the listener active until the Activity is destroyed
            awaitCancellation()
        }
    }

    private fun isAppointmentWithinTenMinutes(appointmentHour: String): Boolean {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val currentTime = Calendar.getInstance()
        val appointmentTime = Calendar.getInstance().apply {
            time = sdf.parse(appointmentHour) ?: return false // Handle parsing errors
            // Set the year, month, and day to match the current date
            set(Calendar.YEAR, currentTime.get(Calendar.YEAR))
            set(Calendar.MONTH, currentTime.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, currentTime.get(Calendar.DAY_OF_MONTH))
        }

        val timeDifference = appointmentTime.timeInMillis - currentTime.timeInMillis
        return timeDifference in 0..10 * 60 * 1000 // Check if within 10 minutes
    }

    private fun computeTimeDifference(appointmentHour: String): Int {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val currentTime = Calendar.getInstance()
        
        // convert to minutes
        val currentTimeInMinutes = currentTime.get(Calendar.HOUR_OF_DAY) * 60 + currentTime.get(Calendar.MINUTE)
        
        // convert appointment hour to minutes
        val appointmentTimeInMinutes = sdf.parse(appointmentHour)?.let {
            Calendar.getInstance().apply { time = it }.get(Calendar.HOUR_OF_DAY) * 60 +
                    Calendar.getInstance().apply { time = it }.get(Calendar.MINUTE)
        }
        
        val difference = appointmentTimeInMinutes?.minus(currentTimeInMinutes) ?: 0

        return difference
    }

    private fun init() {
        username = FirebaseAuth.getInstance().currentUser?.email.toString()
        subscribeObservers()
        startMyService()
        setUpScheduleButton()
        setUpNotificationsButton()
        Utils.addLogoutOnBackPressedCallback(this, mainServiceRepository)
    }

    private fun subscribeObservers() {
        setupRecyclerView()
        Log.d(TAG, "subscribeObservers")
        MainService.listener = this
        mainRepository.observeUserAppointments() {
            Log.d(TAG, "subscribeObservers: $it")
            reservesAdapter?.updateList(it)
        }
    }


    private fun setupRecyclerView() {
        reservesAdapter = ReservesRecyclerViewAdapter(this)
        val layoutManager = LinearLayoutManager(this)
        views.mainRecyclerView.apply {
            setLayoutManager(layoutManager)
            adapter = reservesAdapter
        }
    }

    private fun startMyService() {
        mainServiceRepository.startService(username!!)
    }
/*
    override fun onVideoCallClicked(username: String) {
        //check if permission of mic and camera is taken
        getCameraAndMicPermission {
            mainRepository.sendConnectionRequest(username, true) {
                if (it){
                    //we have to start video call
                    //we wanna create an intent to move to call activity
                    startActivity(Intent(this,CallActivity::class.java).apply {
                        putExtra("target",username)
                        putExtra("isVideoCall",true)
                        putExtra("isCaller",true)
                    })
                }
            }
        }
    }

    override fun onAudioCallClicked(username: String) {
        getCameraAndMicPermission {
            mainRepository.sendConnectionRequest(username, false) {
                if (it){
                    //we have to start audio call
                    //we wanna create an intent to move to call activity
                    startActivity(Intent(this,CallActivity::class.java).apply {
                        putExtra("target",username)
                        putExtra("isVideoCall",false)
                        putExtra("isCaller",true)
                    })
                }
            }
        }
    }
*/
    @SuppressLint("SetTextI18n")
    override fun onCallReceived(model: DataModel) {
        runOnUiThread {
            views.apply {
                val isVideoCall = model.type == DataModelType.StartVideoCall
                val isVideoCallText = if (isVideoCall) "Video" else "Audio"
                //incomingCallTitleTv.text = "${model.sender} is $isVideoCallText Calling you"
                incomingCallLayout.isVisible = true
                acceptButton.setOnClickListener {
                    getCameraAndMicPermission {
                        incomingCallLayout.isVisible = false
                        //create an intent to go to video call activity
                        startActivity(Intent(this@MainActivity,CallActivity::class.java).apply {
                            putExtra("target",model.sender)
                            putExtra("isVideoCall",isVideoCall)
                            putExtra("isCaller",false)
                        })
                    }
                }
                declineButton.setOnClickListener {
                    incomingCallLayout.isVisible = false
                }
            }
        }
    }

    private fun updateToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("FCM", "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }

            val token = task.result
            mainRepository.updateToken(
                username,token
            ){ isDone, reason ->
                if (!isDone){
                    Toast.makeText(this@MainActivity, reason, Toast.LENGTH_SHORT).show()
                }
            }

            Log.d("FCM", token)
        }
    }

    private fun setUpScheduleButton() {
        views.apply {
            scheduleCallButton.setOnClickListener {
                val intent = Intent(this@MainActivity, AppointmentActivity::class.java)
                startForResult.launch(intent)
            }
        }
    }

    private fun setUpNotificationsButton() {
        views.apply {
            notificationsButton.setOnClickListener {
                startActivity(Intent(this@MainActivity, NotificationsActivity::class.java).apply {
                })
            }
        }
    }

    override fun cancelClicked(date: String, hour: String) {
        mainRepository.cancelReservation(date, hour)
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        )
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java) // Replace with your main activity
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, "appointment_channel")
            .setContentTitle("A monitorizar as marcações")
            .setContentText("Procurando por novas marcações...")
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Replace with your icon
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW) // Use a lower priority for ongoing notifications

        return builder.build()
    }

    private fun updateNotification(timeToAppointment: Int, appointmentHour: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, "appointment_channel")
            .setContentTitle("Chamada Remota - Temi Luz")
            .setContentText("Tem uma chamada agendada daqui a $timeToAppointment minutos para as $appointmentHour")
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Replace with your icon
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Increase priority for immediate notifications

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "appointment_channel",
                "Appointment Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 1
    }

    override fun onDestroy() {
        super.onDestroy()
        listenerJob?.cancel() // Cancel the coroutine when the Activity is destroyed
    }
}