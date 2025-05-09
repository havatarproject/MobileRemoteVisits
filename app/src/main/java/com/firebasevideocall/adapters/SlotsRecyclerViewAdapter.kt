package com.firebasevideocall.adapters

import android.annotation.SuppressLint
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.firebasevideocall.databinding.ItemMainRecyclerViewBinding
import com.firebasevideocall.utils.Status
import com.firebasevideocall.R.id
import com.firebasevideocall.R.layout
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// THE AVAILABLE SLOTS
class SlotsRecyclerViewAdapter(private val listener: Listener) : RecyclerView.Adapter<SlotsRecyclerViewAdapter.MainRecyclerViewHolder>() {
    private val TAG = "SlotsRecyclerViewAdapter"
    private var eventList: List<Pair<String, Pair<String, String>>>? = null // Event, Date

    @SuppressLint("NotifyDataSetChanged")
    fun updateList(list: List<Pair<String, Pair<String, String>>>) {
        this.eventList = list.filter { it.second.second == Status.AVAILABLE.toString() }
            .sortedWith(compareBy {
                val timeParts = it.second.first.split(":")
                val hours = timeParts[0].toInt()
                val minutes = timeParts[1].toInt()
                hours * 60 + minutes
            })
        Log.d(TAG, this.eventList.toString())
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MainRecyclerViewHolder {
        val binding = ItemMainRecyclerViewBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return MainRecyclerViewHolder(binding)
    }

    override fun getItemCount(): Int {
        return eventList?.size ?: 0
    }

    override fun onBindViewHolder(holder: MainRecyclerViewHolder, position: Int) {
        eventList?.let { list ->
            val event = list[position]
            holder.bind(event, listener)
        }
    }

    interface Listener {
        fun onReserveClicked(date: String, hour: String, note: String)
    }

    class MainRecyclerViewHolder(private val binding: ItemMainRecyclerViewBinding) :

        RecyclerView.ViewHolder(binding.root) {

        fun bind(
            event: Pair<String, Pair<String, String>>,
            listener: Listener
        ) {
            binding.apply {
                dateTV.text = event.first
                hourTV.text = event.second.first
                statusTV.text = event.second.second

                when (event.second.second) {
                    "AVAILABLE" -> {
                        actionButton.text = "Agendar"
                        actionButton.isVisible = true
                        actionButton.setOnClickListener {
                            // Use itemView.context to ensure the dialog context is correct
                            val builder = AlertDialog.Builder(itemView.context)
                            val inflater = LayoutInflater.from(itemView.context)
                            val dialogLayout = inflater.inflate(layout.appointment_dialog, null)
                            val editText = dialogLayout.findViewById<EditText>(id.et_editText)
                            val dialog: AlertDialog

                            with(builder) {
                                setTitle("Dados do Agendamento")
                                setPositiveButton("Agendar", null) // Initially null listener
                                setNegativeButton("Cancelar") { dialog, _ ->
                                    dialog.dismiss()
                                }
                                setView(dialogLayout)
                                dialog = show() // Store the dialog instance
                            }

                            // Add TextWatcher to enable/disable positive button
                            var textChanged = false // Flag to track text changes
                            editText.addTextChangedListener(object : TextWatcher {
                                override fun afterTextChanged(s: Editable?) {
                                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = !s.isNullOrEmpty() &&
                                            s.toString().isNotEmpty()
                                    textChanged = dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled // Set the flag when text changes
                                }

                                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                            })

                            // Set positive button click listener after showing the dialog
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                                if (textChanged) { // Check if the text has been modified
                                    val note = editText.text.toString()
                                    listener.onReserveClicked(event.first, event.second.first, note)
                                    dialog.dismiss()
                                } else {
                                    // Optionally show a message to the user indicating they need to modify the text
                                    Toast.makeText(itemView.context, "Introduza o nome do utente a visitar", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                    else -> {
                        actionButton.isVisible = false
                        actionButton.setOnClickListener(null) // Clear any previous click listener
                    }
                }
            }
        }
    }

}
