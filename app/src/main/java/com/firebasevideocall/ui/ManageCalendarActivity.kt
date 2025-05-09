package com.firebasevideocall.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.motion.widget.MotionLayout
import com.firebasevideocall.R
import com.firebasevideocall.service.MainService
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*
import com.firebasevideocall.utils.AppointmentData
import com.firebasevideocall.utils.DataModel
import com.firebasevideocall.utils.Status
import com.firebasevideocall.repository.MainRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ManageCalendarActivity : AppCompatActivity(), MainService.Listener {

    private lateinit var selectDateButton: Button
    private lateinit var selectedDateTextView: TextView
    private lateinit var selectTimeButton: Button
    private lateinit var selectedTimeTextView: TextView
    private lateinit var numberOfSlots: EditText
    private lateinit var timePerSlot: EditText
    private lateinit var createSlotsButton: Button
    private lateinit var slotsTextView: TextView
    private lateinit var slotsListView: ListView
    private lateinit var deleteSlotsButton: Button
    private lateinit var firestore: FirebaseFirestore

    private val slotsList = mutableListOf<AppointmentData>()
    private lateinit var adapter: ArrayAdapter<String>

    private var selectedDate: Date? = Calendar.getInstance().time
    private var selectedTime: Date? = null

    private val TIME_PER_SLOT = 3

    @Inject
    lateinit var mainRepository: MainRepository

    interface Listener {
        fun onRejectClicked(userEmail : String, notification : String,
                            position: Int)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_calendar)

        selectDateButton = findViewById(R.id.selectDateButton)
        selectedDateTextView = findViewById(R.id.selectedDateTextView)
        selectTimeButton = findViewById(R.id.selectTimeButton)
        selectedTimeTextView = findViewById(R.id.selectedTimeTextView)
        numberOfSlots = findViewById(R.id.numberOfSlots)
        timePerSlot = findViewById(R.id.timePerSlot)
        createSlotsButton = findViewById(R.id.createSlotsButton)
        slotsTextView = findViewById(R.id.slotsTextView)
        slotsListView = findViewById(R.id.slotsListView)
        deleteSlotsButton = findViewById(R.id.deleteSlotsButton)

        firestore = FirebaseFirestore.getInstance()

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_multiple_choice, mutableListOf())
        slotsListView.adapter = adapter

        selectDateButton.setOnClickListener {
            showDatePickerDialog()
            selectedDate?.let {
                loadExistingSlots()
            }
        }
        selectTimeButton.setOnClickListener {
            showTimePickerDialog()
        }

        val motionLayout = findViewById<MotionLayout>(R.id.motionLayout)

        createSlotsButton.setOnClickListener {
            val numberOfSlotsText = numberOfSlots.text.toString()
            val timePerSlotText = timePerSlot.text.toString()
            Log.d("TIME_PER_SLOT", "chosen time is $timePerSlotText")

            if (selectedDate != null && selectedTime != null && numberOfSlotsText.isNotEmpty() && timePerSlotText.isNotEmpty()) {
                val numberOfSlots = numberOfSlotsText.toInt()
                val timePerSlot = timePerSlotText.toInt()
                if (motionLayout.currentState == motionLayout.endState) {
                    motionLayout.transitionToStart()
                }

                //if timePerSlotText larger than TIME_PER_SLOT then show error
                if (timePerSlot < TIME_PER_SLOT) {
                    Toast.makeText(this, "Tempo por slot não pode ser menor que $TIME_PER_SLOT minutos", Toast.LENGTH_SHORT).show()
                    Log.d("TIME_PER_SLOT", "chosen time is $timePerSlotText so the slot can't be created")
                //return@setOnClickListener
                }
                else {
                    Log.d("TIME_PER_SLOT", "chosen time is $timePerSlotText so the slot can be created")
                    createSlots(numberOfSlots, timePerSlot)
                }
            } else {
                Toast.makeText(this, "Por favor, seleciona uma data e preencha todos os campos", Toast.LENGTH_SHORT).show()
            }

        }

        deleteSlotsButton.setOnClickListener {
            deleteSelectedSlots()
        }

        loadExistingSlots()

        // use listener interface
        val listener = object : Listener {
            override fun onRejectClicked(userEmail : String, notification : String,
                                         position: Int) {
                // Handle reject button click

                mainRepository.addNotification(userEmail, notification)
                freeSlot(position)
            }
        }

        slotsListView.setOnItemLongClickListener { parent, view, position, id ->
            val slot = slotsList[position]
            val slotDate = slot.date
            when (slot.status) {
                Status.REQUESTED -> {
                    val existingLocations = mutableListOf<String>()

                    firestore.collection("locations")
                        .get()
                        .addOnSuccessListener { documents ->
                            // for each document, add the location field to the list
                            // as a string
                            for (document in documents) {
                                val location = document.getString("location")
                                location?.let { existingLocations.add(it) }
                            }

                            // Create and show the dialog here
                            val builder = AlertDialog.Builder(this) // Store the builder
                            val dialogo: AlertDialog

                            val LocationsNamesArray = existingLocations.toTypedArray()

                            var selectedLocationIndex = -1
                            builder.setSingleChoiceItems(LocationsNamesArray, -1) { _, which ->
                                selectedLocationIndex = which
                            }

                            val input = EditText(this).apply {
                                inputType = InputType.TYPE_CLASS_TEXT
                                hint = "Introduza o motivo, se for rejeitar"
                                setText("")
                            }

                            with(builder) {
                                // Longer title because message and single choice items are
                                // mutually exclusive
                                setTitle("Gerir Marcação\nNota: ${slot.note}\nUtilizador: ${slot.userEmail}")
                                builder.setPositiveButton("Aceitar") { _, _ ->
                                    if (selectedLocationIndex != -1) {
                                        val selectedLocation = LocationsNamesArray[selectedLocationIndex]
                                        updateSlotLocation(position, selectedLocation)
                                        handleSlotRequest(
                                            position,
                                            Status.ACCEPTED
                                        )

                                        // Send notification to the user
                                        mainRepository.addNotification(
                                            slot.userEmail!!,
                                            "A sua marcação a $slotDate foi aceite."
                                        )
                                    } else {
                                        // Handle case where no location is selected, if necessary
                                    }
                                }
                                setNegativeButton("Recusar") { dialog, _ ->
                                    dialog.dismiss()
                                }
                                builder.setNeutralButton("Fechar") { dialogInterface, _ ->
                                    dialogInterface.dismiss()
                                }

                                setView(input)
                                dialogo = show() // Store the dialog instance
                            }

                            Toast.makeText(this, "Escolha a cama do utente", Toast.LENGTH_SHORT).show()

                            // Add TextWatcher to enable/disable positive button
                            var textChanged = false // Flag to track text changes
                            input.addTextChangedListener(object : TextWatcher {
                                override fun afterTextChanged(s: Editable?) {
                                    dialogo.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = !s.isNullOrEmpty() &&
                                            s.toString().isNotEmpty()
                                    textChanged = dialogo.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled // Set the flag when text changes
                                }

                                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                            })

                            // Set positive button click listener after showing the dialog
                            dialogo.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                                if (textChanged) { // Check if the text has been modified
                                    val note = input.text.toString()
                                    listener.onRejectClicked(slot.userEmail!!, note, position)
                                    dialogo.dismiss()
                                } else {
                                    // Optionally show a message to the user indicating they need to modify the text
                                    Toast.makeText(this, "Introduza o nome do utente a visitar", Toast.LENGTH_SHORT).show()
                                }
                            }
                            true
                        }
                        .addOnFailureListener { e ->
                            // Handle potential errors
                        }
                }
                Status.ACCEPTED -> {
                    val dialog = AlertDialog.Builder(this)
                    dialog.setTitle("Cancelar Marcação")
                    dialog.setMessage("Utilizador: ${slot.userEmail}\nNota: ${slot.note}")
                    dialog.setPositiveButton("Cancelar") { _, _ ->
                        freeSlot(position)
                    }
                    dialog.setNegativeButton("Fechar", null)
                    dialog.show()
                }
                Status.AVAILABLE -> {
                    val dialog = AlertDialog.Builder(this)
                    dialog.setTitle("Gerir Horário")
                    dialog.setPositiveButton("Apagar") { _, _ ->
                        deleteSlot(position)
                    }
                    dialog.setNegativeButton("Desativar") { _, _ ->
                        disableSlot(position)
                    }
                    dialog.setNeutralButton("Fechar", null)
                    dialog.show()
                }
                Status.UNAVAILABLE -> {
                    val dialog = AlertDialog.Builder(this)
                    dialog.setTitle("Gerir Horário")
                    dialog.setPositiveButton("Ativar") { _, _ -> enableSlot(position) }
                    dialog.setNeutralButton("Fechar", null)
                    dialog.show()
                }
                else -> {
                    Toast.makeText(this, "Não há ações disponíveis para esta marcação", Toast.LENGTH_SHORT).show()
                }
            }
            true
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
            val formattedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(selectedDate)
            selectedDateTextView.text = getString(R.string.selected_date, formattedDate)
            loadExistingSlots()
        }, year, month, day)

        datePickerDialog.show()
    }


    private fun showTimePickerDialog() {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        val timePickerDialog = TimePickerDialog(this, { _, selectedHour, selectedMinute ->
            calendar.set(Calendar.HOUR_OF_DAY, selectedHour)
            calendar.set(Calendar.MINUTE, selectedMinute)
            calendar.set(Calendar.SECOND, 0)  // Ensure seconds are always set to 00
            selectedTime = calendar.time
            val formattedTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(selectedTime)
            selectedTimeTextView.text = getString(R.string.selected_time, formattedTime)
        }, hour, minute, true)

        timePickerDialog.show()
    }


    private fun createSlots(numberOfSlots: Int, inputSlotTime: Int) {
        val calendar = Calendar.getInstance()
        calendar.time = selectedDate!!
        selectedTime?.let {
            val timeCalendar = Calendar.getInstance()
            timeCalendar.time = it
            calendar.set(Calendar.HOUR_OF_DAY, timeCalendar.get(Calendar.HOUR_OF_DAY))
            calendar.set(Calendar.MINUTE, timeCalendar.get(Calendar.MINUTE))
        }

        var endCallBuffer = 0
        var timePerSlot = 0
         mainRepository.getBufferTime("minutesBetweenAppointments") { bufferTime ->
            if (bufferTime != null) {
                // Use the time value
                endCallBuffer = bufferTime
                timePerSlot = inputSlotTime - endCallBuffer
                val formattedSelectedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(selectedDate!!)

                firestore.collection("appointmentSlots")
                    .whereEqualTo("date", formattedSelectedDate)
                    .get()
                    .addOnSuccessListener { documents ->
                        val existingSlots = documents.map { document ->
                            val appointment = document.toObject(AppointmentData::class.java)
                            appointment.id = document.id
                            appointment
                        }

                        val newSlots = mutableListOf<AppointmentData>()
                        var slotsOverlap = false

                        for (i in 0 until numberOfSlots) {
                            calendar.set(Calendar.SECOND, 0)
                            val slotTime = calendar.time
                            val formattedTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(slotTime)
                            val formattedTimePerSlot = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(timePerSlot)
                            // Check if slot time is in the past
                            val status = if (slotTime.before(Calendar.getInstance().time)) {
                                Status.PASSED
                            } else {
                                Status.AVAILABLE
                            }

                            if (existingSlots.any { existingSlot ->
                                    val existingSlotStart = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                                        .parse("${existingSlot.date} ${existingSlot.hour}")
                                    val existingSlotEnd = Calendar.getInstance().apply {
                                        time = existingSlotStart!!
                                        existingSlot.time?.let { add(Calendar.MINUTE, it.toInt()) }
                                    }.time
                                    val newSlotEnd = Calendar.getInstance().apply {
                                        time = slotTime
                                        add(Calendar.MINUTE, timePerSlot + endCallBuffer)
                                    }.time

                                    (slotTime.before(existingSlotEnd) && newSlotEnd.after(existingSlotStart))
                                }) {
                                slotsOverlap = true
                                break
                            }

                            val appointment = AppointmentData(
                                id = UUID.randomUUID().toString(),
                                userEmail = null,
                                note = null,
                                date = formattedSelectedDate,
                                hour = formattedTime,
                                time = timePerSlot.toString(),
                                status = status
                            )

                            newSlots.add(appointment)
                            calendar.add(Calendar.MINUTE, timePerSlot + endCallBuffer)
                        }

                        if (slotsOverlap) {
                            Toast.makeText(this, "Não pode criar horários sobrepostos a horários existentes", Toast.LENGTH_SHORT).show()
                        } else {
                            newSlots.forEach { appointment ->
                                firestore.collection("appointmentSlots").add(appointment)
                                slotsList.add(appointment)
                            }
                            updateSlotsListView()
                        }
                    }
            } else {
                // Handle case where time is not found or there's an error
            }
        }
    }


    private fun deleteSelectedSlots() {
        val checkedPositions = slotsListView.checkedItemPositions
        for (i in (slotsList.size - 1) downTo 0) {
            if (checkedPositions.get(i)) {
                val slot = slotsList[i]
                firestore.collection("appointmentSlots")
                    .whereEqualTo("date", slot.date)
                    .whereEqualTo("hour", slot.hour)
                    .get()
                    .addOnSuccessListener { documents ->
                        for (document in documents) {
                            firestore.collection("appointmentSlots").document(document.id).delete()
                        }
                    }
                slotsList.removeAt(i)
            }
        }
        adapter.notifyDataSetChanged()
        updateSlotsListView()
    }

    private fun loadExistingSlots() {
        //if (selectedDate == null) return
        val formattedSelectedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(selectedDate ?: Calendar.getInstance().time)
        //val formattedSelectedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(selectedDate)
        firestore.collection("appointmentSlots")
            .whereEqualTo("date", formattedSelectedDate)
            .addSnapshotListener { documents, error ->
                if (error != null) {
                    // Handle error
                    return@addSnapshotListener
                }

                slotsList.clear()
                for (document in documents!!) {
                    val appointment = document.toObject(AppointmentData::class.java)
                    appointment.id = document.id

                    // Check if appointment is passed and update if necessary
                    val slotTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse("${appointment.date} ${appointment.hour}")
                    if (slotTime != null && slotTime.before(Calendar.getInstance().time) && appointment.status != Status.PASSED) {
                        appointment.status = Status.PASSED
                        firestore.collection("appointmentSlots").document(document.id).set(appointment)
                    }
                    slotsList.add(appointment)
                }

                // Sort slotsList by the hour field
                slotsList.sortWith(compareBy {
                    it.hour?.let { it1 -> SimpleDateFormat("HH:mm:ss", Locale.getDefault()).parse(it1) }
                })

                updateSlotsListView()
            }
    }

    private fun disableSlot(position: Int) {
        val slot = slotsList[position]
        val slotDate = slot.date
        if (slot.status == Status.AVAILABLE) {
            updateSlotStatus(position, Status.UNAVAILABLE)
            updateSlotEmail(position, "")
        }
        else if (slot.status == Status.ACCEPTED || slot.status == Status.REQUESTED) {
            mainRepository.addNotification(slot.userEmail!!, "A sua marcação a $slotDate foi cancelada. " +
                    "Para mais informações, contacte o Hospital da Luz")
        }
    }

    private fun enableSlot(position: Int) {
        val slot = slotsList[position]
        if (slot.status == Status.UNAVAILABLE) {
            updateSlotStatus(position, Status.AVAILABLE)
        }
    }

    private fun freeSlot(position: Int) {
        val slot = slotsList[position]
        val slotDate = slot.date
        if (slot.status == Status.ACCEPTED || slot.status == Status.REQUESTED) {
            mainRepository.addNotification(slot.userEmail!!, "A sua marcação a $slotDate foi cancelada. " +
                    "Para mais informações, contacte o Hospital da Luz")
            updateSlotStatus(position, Status.AVAILABLE)
            updateSlotEmail(position, "")
            updateSlotLocation(position, "")
            updateSlotNote(position, "")
        }
    }

    private fun updateSlotEmail(position: Int, email: String) {
        val slot = slotsList[position]

        firestore.collection("appointmentSlots")
            .document(slot.id!!)
            .update("userEmail", email)
            .addOnSuccessListener {
                slot.userEmail = email
                adapter.notifyDataSetChanged()
                updateSlotsListView()
                Toast.makeText(this, "Email atualizado", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateSlotNote(position: Int, note: String) {
        val slot = slotsList[position]

        firestore.collection("appointmentSlots")
            .document(slot.id!!)
            .update("note", note)
            .addOnSuccessListener {
                slot.note = note
                adapter.notifyDataSetChanged()
                updateSlotsListView()
                Toast.makeText(this, "Dados da marcação atualizados", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateSlotStatus(position: Int, status: Status) {
        val slot = slotsList[position]

        firestore.collection("appointmentSlots")
            .document(slot.id!!)
            .update("status", status)
            .addOnSuccessListener {
                slot.status = status
                adapter.notifyDataSetChanged()
                updateSlotsListView()
                Toast.makeText(this, "Estado da marcação atualizado", Toast.LENGTH_SHORT).show()
            }
    }

    private fun handleSlotRequest(position: Int, status: Status) {
        val slot = slotsList[position]
        if (slot.status == Status.REQUESTED && (status == Status.ACCEPTED || status == Status.DENIED)) {
            updateSlotStatus(position, status)
        }
        else {
            Toast.makeText(this, "Impossível atualizar o estado da marcação", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateSlotLocation(position: Int, locationId: String) {
        val slot = slotsList[position]

        val updates = hashMapOf<String, Any>(
            "location" to locationId // Add or update the 'location' field with the provided LocationId
        )

        firestore.collection("appointmentSlots")
            .document(slot.id!!)
            .update(updates)
            .addOnSuccessListener {
                // Update the local slot object if needed
                slot.location = locationId

                adapter.notifyDataSetChanged()
                updateSlotsListView()
                Toast.makeText(this, "Localização atribuída à marcação", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                // Handle potential errors during the update operation
                Toast.makeText(this, "Erro a atualizar a localização: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun deleteSlot(position: Int) {
        val slot = slotsList[position]
        val currentDateTime = Calendar.getInstance().time
        val slotDateTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse("${slot.date} ${slot.hour}")

        if (slotDateTime != null && currentDateTime.before(slotDateTime)) {
            mainRepository.addNotification(slot.userEmail!!, "A sua marcação a ${slot.date} foi cancelada." +
                    " Para mais informações, contacte Hospital da Luz")
            slot.id?.let {
                firestore.collection("appointmentSlots")
                    .document(it)
                    .delete()
                    .addOnSuccessListener {
                        slotsList.removeAt(position)
                        adapter.notifyDataSetChanged()
                        Toast.makeText(this, "Marcação Cancelada", Toast.LENGTH_SHORT).show()
                        updateSlotsListView()
                    }
            } ?: run {
                Toast.makeText(this, "Erro: ID da marcação não existe", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Não pode cancelar marcações já terminadas", Toast.LENGTH_SHORT).show()
        }
    }



    private fun updateSlotsListView() {
        val formattedSelectedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(selectedDate)
        val slotsDisplayList = slotsList
            .filter { it.date == formattedSelectedDate }
            .map { "${it.date} ${it.hour} \nduração: ${it.time} minutos (${it.status.status})" }
        adapter.clear()
        adapter.addAll(slotsDisplayList)
        adapter.notifyDataSetChanged()
    }


    override fun onCallReceived(model: DataModel) {
        TODO("Not yet implemented")
    }


}
