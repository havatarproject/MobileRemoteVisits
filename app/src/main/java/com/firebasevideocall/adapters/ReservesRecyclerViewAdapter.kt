package com.firebasevideocall.adapters

import android.annotation.SuppressLint
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.firebasevideocall.databinding.ItemReservesRecyclerViewBinding

//reserves made by certain user
class ReservesRecyclerViewAdapter(private val listener:Listener) : RecyclerView.Adapter<ReservesRecyclerViewAdapter.ReservesRecyclerViewHolder>() {
    private val TAG = "ReservesRecyclerViewAdapter"
    private var slotsList:List<Pair<String,Pair<String, String>>>?=null

    @SuppressLint("NotifyDataSetChanged")
    fun updateList(list:List<Pair<String,Pair<String, String>>>){
        Log.d("ReservesRecycler", list.toString())
        this.slotsList = list
            .sortedWith(compareBy {
                val timeParts = it.second.first.split(":")
                val hours = timeParts[0].toInt()
                val minutes = timeParts[1].toInt()
                hours * 60 + minutes
            })
        Log.d(TAG, this.slotsList.toString())
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReservesRecyclerViewHolder {
        val binding = ItemReservesRecyclerViewBinding.inflate(
            LayoutInflater.from(parent.context),parent,false
        )
        return ReservesRecyclerViewHolder(binding)
    }

    override fun getItemCount(): Int {
        return slotsList?.size?:0
    }

    override fun onBindViewHolder(holder: ReservesRecyclerViewHolder, position: Int) {
        slotsList?.let { list ->
            val user = list[position]
            holder.bind(user, listener)
        }
    }


    interface  Listener {
        fun cancelClicked(date: String, hour: String)
    }

    class ReservesRecyclerViewHolder(private val binding: ItemReservesRecyclerViewBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(
            list: Pair<String, Pair<String, String>>,
            listener: Listener
        ) {
            binding.apply {
                when (list.second.second) {
                    "REQUESTED", "ACCEPTED" -> {
                        dateTV.text = list.first
                        hourTV.text = list.second.first
                        statusTV.text = list.second.second
                        cancelButton.text = "Cancelar"
                        cancelButton.setOnClickListener {
                            // Create an AlertDialog
                            val builder = AlertDialog.Builder(itemView.context)
                            builder.setMessage("Are you sure you want to cancel?")
                                .setCancelable(false)
                                .setPositiveButton("Yes") { _, _ ->
                                    // Invoke the cancelClicked function
                                    listener.cancelClicked(list.first, list.second.first)
                                }
                                .setNegativeButton("No") { dialog, _ ->
                                    // Dismiss the dialog
                                    dialog.dismiss()
                                }
                            val alert = builder.create()
                            alert.show()
                        }
                    }
                }
            }
        }
    }
}