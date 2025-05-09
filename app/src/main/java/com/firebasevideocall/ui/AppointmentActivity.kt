package com.firebasevideocall.ui

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.firebasevideocall.adapters.SlotsRecyclerViewAdapter
import com.firebasevideocall.databinding.ActivityAppointmentBinding
import com.firebasevideocall.repository.MainRepository
import com.firebasevideocall.service.MainService
import com.firebasevideocall.service.MainServiceRepository
import com.firebasevideocall.utils.DataModel
import com.firebasevideocall.utils.Utils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import javax.inject.Inject

@AndroidEntryPoint
class AppointmentActivity : AppCompatActivity(), MainService.Listener, SlotsRecyclerViewAdapter.Listener {
    @Inject
    lateinit var mainRepository: MainRepository
    private var username: String? = null
    private var mainAdapter: SlotsRecyclerViewAdapter? = null
    private lateinit var firestore: FirebaseFirestore
    @Inject
    lateinit var mainServiceRepository: MainServiceRepository
    private lateinit var views: ActivityAppointmentBinding
    private var selectedDate: Date? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        views = ActivityAppointmentBinding.inflate(layoutInflater)
        setContentView(views.root)
        init()
    }

    private fun init() {
        username = FirebaseAuth.getInstance().currentUser?.email.toString()
        setupUI()
        startMyService()
        firestore = FirebaseFirestore.getInstance()
        Utils.addOnBackPressedCallback(this)
    }

    private fun setupUI() {
        views.btnSelectDate.setOnClickListener {
            showDatePickerDialog()
        }
    }

    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
            calendar.set(selectedYear, selectedMonth, selectedDay)
            selectedDate = calendar.time
            val format = SimpleDateFormat("dd/M/yyyy")
            views.txtAppHour.text = "Dia selecionado: " + format.format(selectedDate)
            subscribeObservers()
        }, year, month, day)
        datePickerDialog.show()
    }

    private fun startMyService() {
        mainServiceRepository.startService(username!!)
    }

    private fun setupRecyclerView() {
        mainAdapter = SlotsRecyclerViewAdapter(this)
        val layoutManager = LinearLayoutManager(this)
        views.mainRecyclerView.apply {
            setLayoutManager(layoutManager)
            adapter = mainAdapter
        }
    }

    private fun subscribeObservers() {
        setupRecyclerView()
        MainService.listener = this

        selectedDate?.let { date ->
            mainRepository.observeAppointments(date) {
                Log.d("TAG", "subscribeObservers: $it")
                mainAdapter?.updateList(it)
            }
        }
    }

    override fun onCallReceived(model: DataModel) {
        TODO("Not yet implemented")
    }

    override fun onReserveClicked(date: String, hour: String, note: String) {
        mainRepository.reserveSlot(date, hour, note)
        setResult(Activity.RESULT_OK) // Indicate success
        finish()
        //startActivity(Intent(this, MainActivity::class.java))
    }
}