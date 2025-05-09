package com.firebasevideocall.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.firebasevideocall.R
import com.firebasevideocall.databinding.ActivityAdminBinding
import com.firebasevideocall.databinding.ActivityMainBinding
import com.firebasevideocall.repository.MainRepository
import com.firebasevideocall.service.MainService
import com.firebasevideocall.service.MainServiceRepository
import com.firebasevideocall.utils.DataModel
import com.firebasevideocall.utils.Utils
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AdminActivity : AppCompatActivity(), MainService.Listener {
    private val TAG = "AdminActivity"

    private lateinit var binding: ActivityAdminBinding
    private var username: String? = null
    @Inject
    lateinit var mainRepository: MainRepository
    @Inject
    lateinit var mainServiceRepository: MainServiceRepository

    private val requestPermissionLauncher by lazy {
        Utils.registerForNotificationPermission(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        username = intent.getStringExtra("username")
        startMyService()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        binding = ActivityAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Utils.askNotificationPermission(this, requestPermissionLauncher)
        Utils.addLogoutOnBackPressedCallback(this, mainServiceRepository)
        setupUI()
    }

    private fun startMyService() {
        username?.let { Log.d(TAG, it) }
        mainServiceRepository.startService(username!!)
    }

    private fun setupUI() {
        binding.firstButton.setOnClickListener {
            val intent = Intent(this, ManagePermissionsActivity::class.java)
            intent.putExtra("username", username)
            startActivity(intent)
        }
        binding.secondButton.setOnClickListener {
            val intent = Intent(this, ManageCalendarActivity::class.java)
            intent.putExtra("username", username)
            startActivity(intent)
        }

        setupSeekBar()
    }

    fun setupSeekBar() {
        val seekBar = binding.seekBar // Assuming you have a SeekBar in your layout with id "seekBar"

        // observe minutes between appointments buffer
        mainRepository.observeMinutesBetweenAppointmentsBuffer() { buffer ->
            if (buffer.isNotEmpty()) {
                val bufferData = buffer[0].second // Get the Map
                val time = bufferData["time"] as? Int // Access "time" from the Map and cast to Int
                time?.let {
                    seekBar.progress = it
                }
            }
        }

        Log.d(TAG, "setupSeekBar: ${seekBar.progress} minutes")

        val textView = findViewById<TextView>(R.id.textView3) // If you still have the TextView
        textView.text = getString(R.string.minutos_entre_chamadas, seekBar.progress.toString())

        // Set listener for value changes
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Handle progress changes here
                Log.d(TAG, "(slider) Progress changed to $progress")
                textView.text = getString(R.string.minutos_entre_chamadas, progress.toString())
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // Called when the user starts interacting with the SeekBar
                Log.d(TAG, "(slider) Start tracking touch")
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Called when the user stops interacting with the SeekBar
                val progress = seekBar?.progress ?: 0
                mainRepository.updateBufferTime("minutesBetweenAppointments", progress)
                Log.d(TAG, "(slider) Stop tracking touch")
                Log.d(TAG, "(slider) value updated to $progress")
            }
        })
    }

    override fun onCallReceived(model: DataModel) {
        TODO("Not yet implemented")
    }

}