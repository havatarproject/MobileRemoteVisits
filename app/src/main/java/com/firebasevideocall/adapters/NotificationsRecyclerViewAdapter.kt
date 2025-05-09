package com.firebasevideocall.adapters

import android.annotation.SuppressLint
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.firebasevideocall.databinding.ItemNotificationUserRecyclerViewBinding

class NotificationsRecyclerViewAdapter() : RecyclerView.Adapter<NotificationsRecyclerViewAdapter.NotificationsRecyclerViewHolder>() {

    private var notifications: List<Pair<String, String>> = emptyList()
    @SuppressLint("NotifyDataSetChanged")
    fun updateList(newList: List<Pair<String, String>>) {
        Log.d("NotifsRecycler", newList.toString())
        this.notifications = newList
        notifyDataSetChanged()
    }

    // Called to create a new ViewHolder instance
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationsRecyclerViewHolder {
        val binding = ItemNotificationUserRecyclerViewBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return NotificationsRecyclerViewHolder(binding)
    }
    override fun onBindViewHolder(holder: NotificationsRecyclerViewHolder, position: Int) {
        val notification = notifications[position]
        holder.bind(notification)
    }

    // Returns the total number of items in the list
    override fun getItemCount(): Int {
        return notifications.size
    }

    class NotificationsRecyclerViewHolder(private val binding: ItemNotificationUserRecyclerViewBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(notification: Pair<String, String>) {
            Log.d("NotificationsRecyclerViewHolder", "here")
            binding.apply {
                notificationDate.text = notification.first
                notificationText.text = notification.second
            }
        }
    }
}