package com.firebasevideocall.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.firebasevideocall.ui.CloseActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject


class MainServiceReceiver : BroadcastReceiver() {

    @Inject lateinit var serviceRepository: MainServiceRepository
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == "ACTION_EXIT"){
            serviceRepository.stopService()
            context?.startActivity(Intent(context,CloseActivity::class.java))
        }

    }
}