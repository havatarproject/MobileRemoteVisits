package com.firebasevideocall.firebaseClient

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.firebasevideocall.utils.AppointmentData
import com.firebasevideocall.utils.DataModel
import com.firebasevideocall.utils.FirebaseFieldNames.ID
import com.firebasevideocall.utils.FirebaseFieldNames.LATEST_EVENT
import com.firebasevideocall.utils.FirebaseFieldNames.STATUS
import com.firebasevideocall.utils.FirebaseFieldNames.TOKEN
import com.firebasevideocall.utils.MyEventListener
import com.firebasevideocall.utils.Status
import com.firebasevideocall.utils.UserStatus
import com.firebasevideocall.utils.Utils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.gson.Gson
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

    class FirebaseClient @Inject constructor(
        private val dbRef:DatabaseReference,
        private val gson: Gson,
        private val context: Context

    ) {
        private var currentUsername:String?=null
        private var currentId:String?=null
        private var token:String?=null

        private fun setUsername(username: String){
            this.currentUsername = username
        }
        private fun setID(id: String){
            this.currentId = id
        }

        private fun setToken(token: String) {
            this.token = token
        }

        data class UserInfo(val id: String, val email: String, val isAdmin: Boolean)

        fun login(username: String?, id: String?, done: (Boolean, String?) -> Unit) {
            dbRef.addListenerForSingleValueEvent(object  : MyEventListener(){
                override fun onDataChange(snapshot: DataSnapshot) {
                    //if the current user exists
                    val user = Utils.encodeEmail(username)
                    if (snapshot.hasChild(user)){
                        dbRef.child(user).child(STATUS).setValue(UserStatus.ONLINE)
                            .addOnCompleteListener {
                                setUsername(user)
                                done(true,null)
                            }.addOnFailureListener {
                                done(false,"${it.message}")
                            }
                    }else{
                        //register the user
                        dbRef.child(user).child(STATUS).setValue(UserStatus.OFFLINE)
                            .addOnCompleteListener { statusSetTask ->
                                if (statusSetTask.isSuccessful) {
                                    dbRef.child(user).child(ID).setValue(id)
                                        .addOnCompleteListener { idSetTask ->
                                            if (idSetTask.isSuccessful) {
                                                setUsername(user)
                                                if (id != null) {
                                                    setID(id)
                                                }
                                                done(true, null)
                                            } else {
                                                done(false, idSetTask.exception?.message)
                                            }
                                        }.addOnFailureListener { exception ->
                                            done(false, exception.message)
                                        }
                                } else {
                                    done(false, statusSetTask.exception?.message)
                                }
                            }.addOnFailureListener { exception ->
                                done(false, exception.message)
                            }

                    }
                }
            })
        }

        fun register(username: String?, id: String?, done: (Boolean, String?) -> Unit) {
            val user = Utils.encodeEmail(username)
            dbRef.child(user).child(STATUS).setValue(UserStatus.OFFLINE)
                .addOnCompleteListener { statusSetTask ->
                    if (statusSetTask.isSuccessful) {
                        dbRef.child(user).child(ID).setValue(id)
                            .addOnCompleteListener { idSetTask ->
                                if (idSetTask.isSuccessful) {
                                    setUsername(user)
                                    if (id != null) {
                                        setID(id)
                                    }
                                    done(true, null)
                                } else {
                                    done(false, idSetTask.exception?.message)
                                }
                            }.addOnFailureListener { exception ->
                                done(false, exception.message)
                            }
                    } else {
                        done(false, statusSetTask.exception?.message)
                    }
                }.addOnFailureListener { exception ->
                    done(false, exception.message)
                }
        }



        fun updateToken(username: String?, token: String?, done: (Boolean, String?) -> Unit) {
            val user = Utils.encodeEmail(username)
            dbRef.child(user).child(TOKEN).setValue(token)
                .addOnCompleteListener {
                    setToken(user)
                    done(true, null)
                }.addOnFailureListener { exception ->
                    done(false, exception.message)
                }
        }


    fun observeUsersStatus(status: (List<Pair<String, String>>) -> Unit) {
        dbRef.addValueEventListener(object : MyEventListener() {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children.filter { it.key !=currentUsername }.map {
                    it.key!! to it.child(STATUS).value.toString()
                }
                status(list)
            }
        })
    }

    fun observeAppointments(selectedDate : Date, callback: (List<Pair<String, Pair<String, String>>>) -> Unit): ListenerRegistration {
        val db = FirebaseFirestore.getInstance()
        val appointmentsRef = db.collection("appointmentSlots")
        Log.d("selectedDate", selectedDate.toString())
        val calendar = Calendar.getInstance()
        calendar.time = selectedDate
        selectedDate.let {
            val timeCalendar = Calendar.getInstance()
            timeCalendar.time = it
            calendar.set(Calendar.HOUR_OF_DAY, timeCalendar.get(Calendar.HOUR_OF_DAY))
            calendar.set(Calendar.MINUTE, timeCalendar.get(Calendar.MINUTE))
        }
        val formattedSelectedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(
            selectedDate //TODO not the best way to change the appointments passed status
        )

        updatePassedSlots(formattedSelectedDate)
        Log.d("formattedDate", formattedSelectedDate)
        return appointmentsRef.whereEqualTo("date", formattedSelectedDate)
            // Ignore past or denied appointments
            .whereNotIn("status", listOf(Status.PASSED.status,
                Status.DENIED.status,
                Status.UNAVAILABLE.status))
            .addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.w("observeAppointments", "Listen failed.", e)
                return@addSnapshotListener
            }

            if (snapshot != null) {
                Log.d("Snapshot", snapshot.documents.toString())
                val appointmentList = snapshot.documents.mapNotNull { document ->
                    val day = document.getString("date") ?: ""
                    val hour = document.getString("hour") ?: ""
                    val status = document.getString("status") ?: ""

                    if (day.isNotEmpty() && hour.isNotEmpty() && status.isNotEmpty()) {
                        Pair(day, Pair(hour, status))
                    } else {
                        null
                    }
                }
                Log.d("EventAppointList", appointmentList.toString())
                callback(appointmentList)
            }
        }
    }

    fun observeAppointments2(callback: (List<Pair<String, Pair<String, String>>>) -> Unit): ListenerRegistration {
        val currentUser = FirebaseAuth.getInstance().currentUser
        val username = currentUser!!.email

        val db = FirebaseFirestore.getInstance()
        val userRef = db.collection("appointments").document(username!!)
        val appointmentsRef = userRef.collection("clientAppointments")

        return appointmentsRef.addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.w("observeAppointments", "Listen failed.", e)
                return@addSnapshotListener
            }

            if (snapshot != null) {
                Log.d("Snapshot", snapshot.documents.toString())
                val appointmentList = snapshot.documents.mapNotNull { document ->
                    val day = document.getString("date") ?: ""
                    val hour = document.getString("hour") ?: ""
                    val status = document.getString("status") ?: ""

                    Pair(day, Pair(hour, status))
                }
                Log.d("EventAppointList", appointmentList.toString())
                callback(appointmentList)
            }
        }
    }

        fun reserveSlot(date: String, hour: String, note: String) {
            val db = FirebaseFirestore.getInstance()
            val appointmentsRef = db.collection("appointmentSlots")

            observeNumberAppointments { appointments ->
                if (appointments > 2) {
                    Toast.makeText(context, "Já possui 3 reservas.", Toast.LENGTH_SHORT).show()
                    return@observeNumberAppointments
                }

                appointmentsRef
                    .whereEqualTo("date", date)
                    .whereEqualTo("hour", hour)
                    .get()
                    .addOnSuccessListener { documents ->
                        if (!documents.isEmpty) {
                            for (document in documents) {
                                val appointmentId = document.id
                                val updates = hashMapOf(
                                    "status" to Status.REQUESTED.toString(),
                                    "userEmail" to Utils.decodeEmail(currentUsername!!),
                                    "note" to note
                                )

                                // Update the document
                                appointmentsRef.document(appointmentId)
                                    .update(updates as Map<String, Any>)
                                    .addOnSuccessListener {
                                        Toast.makeText(
                                            context,
                                            "Reserva confirmada!",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        Log.d("reserveSlot", "Appointment successfully updated!")
                                    }
                                    .addOnFailureListener { e ->
                                        Toast.makeText(
                                            context,
                                            "Erro na reserva. Tente mais tarde!",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        Log.w("reserveSlot", "Error updating appointment", e)
                                    }
                            }
                        } else {
                            Log.d("reserveSlot", "No matching appointment found")
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.w("reserveSlot", "Error getting documents: ", e)
                    }
            }
        }

        private fun observeNumberAppointments(callback: (Int) -> Unit) {
            val currentUser = FirebaseAuth.getInstance().currentUser
            val userEmail = currentUser?.email ?: ""

            val db = FirebaseFirestore.getInstance()
            val appointmentsRef = db.collection("appointmentSlots")

            appointmentsRef.whereEqualTo("userEmail", userEmail)
                .whereNotEqualTo("status", "PASSED")
                .get()
                .addOnSuccessListener { documents ->
                    val appointmentCount = documents.size()
                    callback(appointmentCount)
                }
                .addOnFailureListener { e ->
                    Log.w("observeNumberAppointments", "Error fetching appointments", e)
                    callback(-1)
                }
        }

        private fun updatePassedSlots(selectedDate: String) {
            val db = FirebaseFirestore.getInstance()
            val appointmentsRef = db.collection("appointmentSlots")

            val currentTime = Date(System.currentTimeMillis())
            val currentDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(currentTime)
            val currentTimeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(currentTime)

            Log.d("updateCurrentTime", currentTime.toString())
            Log.d("currentDateStr", currentDateStr)
            Log.d("status", Status.AVAILABLE.status)

            appointmentsRef
                .whereEqualTo("status", Status.AVAILABLE.status)
                .whereEqualTo("date", selectedDate)
                .get()
                .addOnSuccessListener { querySnapshot ->
                    val batch = db.batch()
                    querySnapshot.documents.forEach { document ->
                        val hourStr = document.getString("hour") ?: ""
                        val dateStr = document.getString("date") ?: ""

                        Log.d("currentDateStr", currentDateStr.toString())
                        Log.d("dateStr", dateStr)
                        Log.d("hourStr", hourStr)
                        Log.d("currentTimeStr", currentTimeStr)

                        Log.d("hourStr", (currentDateStr > dateStr).toString())
                        Log.d("currentTimeStr", (hourStr < currentTimeStr).toString())
                        Log.d("second", (currentDateStr == dateStr && hourStr < currentTimeStr).toString())

                        if (currentDateStr > dateStr || (currentDateStr == dateStr && hourStr < currentTimeStr)) {
                            batch.update(document.reference, "status", Status.PASSED.status)
                            println("Slots updated successfully")
                        }
                    }
                    batch.commit()
                        .addOnSuccessListener {
                            println("Slots updated successfully")
                        }
                        .addOnFailureListener { e ->
                            println("Error updating slots: ${e.message}")
                        }
                }
                .addOnFailureListener { e ->
                    println("Error fetching slots: ${e.message}")
                }
        }


        fun observeUserAppointments(callback: (List<Pair<String, Pair<String, String>>>) -> Unit): ListenerRegistration {
        val currentUser = FirebaseAuth.getInstance().currentUser
        val userEmail = currentUser?.email ?: ""

        val db = FirebaseFirestore.getInstance()
        val appointmentsRef = db.collection("appointmentSlots")

        return appointmentsRef.whereEqualTo("userEmail", userEmail)
            // Ignore past or denied appointments
            .whereNotIn("status", listOf(Status.PASSED.status,
                                                Status.DENIED.status,
                                                Status.UNAVAILABLE.status))
            .addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.w("observeAppointments", "Listen failed.", e)
                return@addSnapshotListener
            }

            if (snapshot != null) {
                val appointmentList = snapshot.documents.mapNotNull { document ->
                    val day = document.getString("date") ?: ""
                    val hour = document.getString("hour") ?: ""
                    val status = document.getString("status") ?: ""

                    if (day.isNotEmpty() && hour.isNotEmpty() && status.isNotEmpty()) {
                        Pair(day, Pair(hour, status))
                    } else {
                        null
                    }
                }
                callback(appointmentList)
            }
        }
    }

    fun cancelReservation(date: String, hour: String) {
        val db = FirebaseFirestore.getInstance()
        val appointmentsRef = db.collection("appointmentSlots")
        appointmentsRef
            .whereEqualTo("date", date)
            .whereEqualTo("hour", hour)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    for (document in documents) {
                        val appointmentId = document.id
                        val updates = hashMapOf(
                            "status" to Status.AVAILABLE.toString(),
                            "userEmail" to ""
                        )

                        appointmentsRef.document(appointmentId).update(updates as Map<String, String>)
                            .addOnSuccessListener {
                                Toast.makeText(context, "Reserva cancelada com sucesso!", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(context, "Erro ao cancelar a reserva!", Toast.LENGTH_SHORT).show()
                                Log.w("updateAppointment", "Error updating appointment", e)
                            }
                    }
                } else {
                }
            }
            .addOnFailureListener { e ->
                Log.w("updateAppointment", "Error getting documents: ", e)
            }
    }

    fun subscribeForLatestEvent(listener:Listener){
        try {
            dbRef.child(currentUsername!!).child(LATEST_EVENT).addValueEventListener(
                object : MyEventListener() {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        super.onDataChange(snapshot)
                        val event = try {
                            gson.fromJson(snapshot.value.toString(),DataModel::class.java)
                        }catch (e:Exception){
                            e.printStackTrace()
                            null
                        }
                        event?.let {
                            listener.onLatestEventReceived(it)
                        }
                    }
                }
            )
        }catch (e:Exception){
            //e.printStackTrace()
        }
    }
    fun sendMessageToOtherClient(message: DataModel, success:(Boolean) -> Unit){
        val convertedMessage = gson.toJson(message.copy(sender = currentUsername))
        dbRef.child(message.target).child(LATEST_EVENT).setValue(convertedMessage)
            .addOnCompleteListener {
                success(true)
            }.addOnFailureListener {
                success(false)
            }
    }

    fun changeMyStatus(status: UserStatus) {
        currentUsername?.let { Log.d("currentuser", it) }
        dbRef.child(currentUsername!!).child(STATUS).setValue(status.name)
    }

    fun clearLatestEvent() {
        dbRef.child(currentUsername!!).child(LATEST_EVENT).setValue(null)
    }

    fun logOff(function:()->Unit) {
        dbRef.child(currentUsername!!).child(STATUS).setValue(UserStatus.OFFLINE)
            .addOnCompleteListener { function() }
    }


    interface Listener {
        fun onLatestEventReceived(event:DataModel)
    }

    fun updateIsAdmin(userId: String?, isAdmin: String?, done: (Boolean, String?) -> Unit) {
        if (userId == null) {
            done(false, "User ID is null")
            return
        }

        if (isAdmin != null) {
        }

        val firebaseStore = FirebaseFirestore.getInstance()

        val updates = isAdmin?.let {
            mapOf("isAdmin" to it)
        } ?: emptyMap()

        if (updates.isEmpty()) {
            done(false, "isAdmin is null")
            return
        }

        firebaseStore.collection("Users").document(userId)
            .update(updates)
            .addOnSuccessListener {
                done(true, null)
            }
            .addOnFailureListener { e ->
                done(false, e.message)
            }
    }

    fun subscribeForLatestPermissionsEvent(listener: Listener) {
        try {
            val db = FirebaseFirestore.getInstance()
            val userDocRef = db.collection("Users").document(currentUsername!!)

            userDocRef.collection("events").document(LATEST_EVENT)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        e.printStackTrace()
                        return@addSnapshotListener
                    }

                    if (snapshot != null && snapshot.exists()) {
                        val event = try {
                            gson.fromJson(snapshot.data.toString(), DataModel::class.java)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            null
                        }
                        event?.let {
                            listener.onLatestEventReceived(it)
                        }
                    }
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun observePermissionsStatus(status: (List<Pair<String, Pair<String, String>>>) -> Unit): ListenerRegistration {
        val db = FirebaseFirestore.getInstance()

        return db.collection("Users")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    e.printStackTrace()
                    return@addSnapshotListener
                }

                val list = snapshots?.documents?.map { doc ->
                    doc.getString("isAdmin")?.let { Log.d("observe:", it) }
                    val email = doc.getString("email") ?: ""
                    val isAdmin = (doc.getString("isAdmin") ?: "0")
                    doc.id to (email to isAdmin)
                } ?: emptyList()

                status(list)
            }
    }

    fun observeNotifications(username: String, status: (List<Pair<String, String>>) -> Unit): ListenerRegistration {
        val db = FirebaseFirestore.getInstance()
        val notificationsRef = db.collection("notifications").whereEqualTo("userEmail", username)

        return notificationsRef.addSnapshotListener { snapshots, e ->
            if (e != null) {
                Log.e("observeNotifs", "Listen failed.", e)
                return@addSnapshotListener
            }

            val list = snapshots?.documents?.map { doc ->
                val message = doc.getString("message") ?: ""
                val date = doc.getTimestamp("date")?.toDate()?.toString() ?: ""
                message to date
            } ?: emptyList()

            status(list)
        }
    }


    fun addAppointment(username: String, appointment: AppointmentData) {
        val db = FirebaseFirestore.getInstance()
        val userRef = db.collection("appointments").document(username)
        val newAppointmentRef = userRef.collection("clientAppointments").document()
        val temiRef = db.collection("temiAppointments").document("Temi")
        val newTemiAppointment = temiRef.collection("appointments").document(newAppointmentRef.id)

        newAppointmentRef.set(appointment)
            .addOnSuccessListener {
                newTemiAppointment.set(appointment)
                    .addOnSuccessListener {
                        Log.d("AppointmentActivity", "Agendamento adicionado com sucesso.")
                    }
                    .addOnFailureListener { e ->
                        Log.w("AppointmentActivity", "Ocorreu um erro. Tente novamente.", e)
                    }
            }
            .addOnFailureListener { e ->
                Log.w("AppointmentActivity", "Erro ao agendar visita remota.", e)
            }
    }

    fun addNotification(userEmail: String, notification: String) {
        // create a new document in the notifications collection of the firestore
        // database with the current user's email for the userEmail field, the
        // notification message for the message field, and the current timestamp for the date field

        val db = FirebaseFirestore.getInstance()
        val notificationsRef = db.collection("notifications")
        val newNotificationRef = notificationsRef.document()
        val newNotification = hashMapOf(
            "userEmail" to userEmail,
            "message" to notification,
            "date" to FieldValue.serverTimestamp()
        )

        newNotificationRef.set(newNotification)
            .addOnSuccessListener {
                Log.d("addNotification", "Notification added successfully")
            }
            .addOnFailureListener { e ->
                Log.w("addNotification", "Error adding notification", e)
            }
    }

    fun deleteNotificationsByUserEmail(userEmail: String) {
        val db = FirebaseFirestore.getInstance()
        val notificationsRef = db.collection("notifications")
            .whereEqualTo("userEmail", userEmail)

        notificationsRef.get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    document.reference.delete()
                }
                Log.d("deleteNotifications", "Notifications deleted successfully")
            }
            .addOnFailureListener { e ->
                Log.w("deleteNotifications", "Error deleting notifications", e)
            }
    }

    fun observeNotificationsByUserEmail(userEmail: String, status: (List<Pair<String, String>>) -> Unit): ListenerRegistration {
        val db = FirebaseFirestore.getInstance()
        val notificationsRef = db.collection("notifications")
            .whereEqualTo("userEmail", userEmail)
        Log.d("observeNotifs", userEmail)
        return notificationsRef.addSnapshotListener { snapshots, e ->
            if (e != null) {
                e.printStackTrace()
                return@addSnapshotListener
            }

            // get a list of documents from the snapshot
            // where each element has the message and date fields
            val list = snapshots?.documents?.map { doc ->
                (doc.getString("message") ?: "") to (doc.getTimestamp("date")?.toDate()?.toString() ?: "")
            } ?: emptyList()

            status(list)
        }
    }

    fun observeBeds(status: (List<Pair<String, Map<String, Any?>>>) -> Unit): ListenerRegistration {
        val db = FirebaseFirestore.getInstance()

        return db.collection("beds")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    e.printStackTrace()
                    return@addSnapshotListener
                }

                val list = snapshots?.documents?.map { doc ->
                    val bedData = doc.data ?: emptyMap<String, Any?>()
                    doc.id to bedData
                } ?: emptyList()

                status(list)
            }
    }

    fun observePatients(status: (List<Pair<String, Map<String, Any?>>>) -> Unit): ListenerRegistration {
         val db = FirebaseFirestore.getInstance()

         return db.collection("patients")
             .addSnapshotListener { snapshots, e ->
                 if (e != null) {
                     e.printStackTrace()
                     return@addSnapshotListener
                 }

                 val list = snapshots?.documents?.map { doc ->
                     val patientData = doc.data ?: emptyMap<String, Any?>()
                     doc.id to patientData
                 } ?: emptyList()

                 status(list)
             }
    }

    fun observePatientsByHealthNumber(healthNum: Int, status: (List<Pair<String, Map<String, Any?>>>) -> Unit): ListenerRegistration {
        val db = FirebaseFirestore.getInstance()

        return db.collection("patients")
            .whereEqualTo("health_number", healthNum)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    e.printStackTrace()
                    return@addSnapshotListener
                }

                val list = snapshots?.documents?.map { doc ->
                    val patientData = doc.data ?: emptyMap<String, Any?>()
                    doc.id to patientData
                } ?: emptyList()

                status(list)
            }
    }

    fun observePatientsByName(name: String, status: (List<Pair<String, Map<String, Any?>>>) -> Unit): ListenerRegistration {
        val db = FirebaseFirestore.getInstance()

        return db.collection("patients")
            .whereEqualTo("name", name)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    e.printStackTrace()
                    return@addSnapshotListener
                }

                val list = snapshots?.documents?.map { doc ->
                    val patientData = doc.data ?: emptyMap<String, Any?>()
                    doc.id to patientData
                } ?: emptyList()

                status(list)
            }
    }

    fun observeBuffers(status: (List<Pair<String, Map<String, Any?>>>) -> Unit): ListenerRegistration {
        val db = FirebaseFirestore.getInstance()

        return db.collection("buffers")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    e.printStackTrace()
                    return@addSnapshotListener
                }

                // get list of buffers
                val list = snapshots?.documents?.map { doc ->
                    val bufferData = doc.data ?: emptyMap<String, Any?>()
                    doc.id to bufferData
                } ?: emptyList()

                status(list)
            }
    }


        fun getBufferTime(bufferId: String, callback: (Int?) -> Unit) {
            val db = FirebaseFirestore.getInstance()
            val bufferRef = db.collection("buffers").document(bufferId)
            bufferRef.get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val time = document.getLong("time")?.toInt()
                        callback(time)
                    } else {
                        callback(null) // Document or field not found
                    }
                }
                .addOnFailureListener { exception ->
                    Log.w("getBufferTime", "Error getting buffer time", exception)
                    callback(null) // Or handle the error in a more appropriate way
                }
        }

        fun getBufferDocument(id: String): Map<String, Any?>? {
            return try {
                val documentSnapshot = FirebaseFirestore.getInstance()
                    .collection("buffers")
                    .document(id)
                    .get()
                    .result // Get the result synchronously

                if (documentSnapshot.exists()) {
                    documentSnapshot.data
                } else {
                    null // Document not found
                }
            } catch (e: Exception) {
                // Handle exceptions (e.g., network errors)
                println("Error getting buffer document: ${e.message}")
                null
            }
        }

    fun updateBufferTime(bufferId: String, time: Int) {
        val db = FirebaseFirestore.getInstance()
        val bufferRef = db.collection("buffers").document(bufferId)
        bufferRef.update("time", time)
            .addOnSuccessListener {
                Log.d("updateBufferTime", "Buffer time updated successfully")
            }
            .addOnFailureListener { e ->
                Log.w("updateBufferTime", "Error updating buffer time", e)
            }
    }


    private fun showToast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}