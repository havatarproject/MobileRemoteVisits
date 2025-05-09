package com.firebasevideocall.ui

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.LinearLayoutManager
import com.firebasevideocall.adapters.NotificationsRecyclerViewAdapter
import com.firebasevideocall.databinding.ActivityNotificationUserBinding
import com.firebasevideocall.repository.MainRepository
import com.firebasevideocall.service.MainService
import com.firebasevideocall.service.MainServiceRepository
import com.firebasevideocall.utils.DataModel
import com.firebasevideocall.utils.Utils
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class NotificationsActivity : AppCompatActivity(), MainService.Listener {
    private val TAG = "NotificationsActivity"

    private lateinit var views: ActivityNotificationUserBinding
    private var username: String? = null
    @Inject
    lateinit var mainRepository: MainRepository
    @Inject
    lateinit var mainServiceRepository: MainServiceRepository
    private var notificationsAdapter: NotificationsRecyclerViewAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        views = ActivityNotificationUserBinding.inflate(layoutInflater)
        setContentView(views.root)
        Log.d("Notifications", "NotificationsActivity - onCreate")
        init()
    }

    private fun init() {
        Log.d("Notifications", "NotificationsActivity - init")
        // Get the current user's email
        username = FirebaseAuth.getInstance().currentUser?.email.toString()
        startMyService()
        subscribeObservers()
        Utils.addOnBackPressedCallback(this)
    }

    private fun setupRecyclerView() {
        notificationsAdapter = NotificationsRecyclerViewAdapter()
        val layoutManager = LinearLayoutManager(this)
        views.mainRecyclerView.apply {
            this.layoutManager = layoutManager
            adapter = notificationsAdapter
        }
    }

    private fun startMyService() {
        Log.d("Notifications", "NotificationsActivity - startMyService")
        mainServiceRepository.startService(username!!)
    }

    private fun subscribeObservers() {
        setupRecyclerView()
        MainService.listener = this
        username?.let { it ->
            mainRepository.observeNotifications(it) {
                Log.d(TAG, "subscribeObservers: $it")
                notificationsAdapter?.updateList(it)
            }
        }
    }

    override fun onCallReceived(model: DataModel) {
        //TODO("Not yet implemented") does nothign
    }

}