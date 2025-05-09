package com.firebasevideocall.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.firebasevideocall.databinding.ItemManageRecyclerViewBinding

class PermissionRecyclerViewAdapter(private val listener:Listener) : RecyclerView.Adapter<PermissionRecyclerViewAdapter.PermissionRecyclerViewHolder>() {

    private var filteredList:List<Pair<String,Pair<String, String>>>?=null
    private var usersList:List<Pair<String,Pair<String, String>>>?=null

    @SuppressLint("NotifyDataSetChanged")
    fun updateList(list:List<Pair<String,Pair<String, String>>>){
        this.usersList = list
        this.filteredList = list
        notifyDataSetChanged()
    }

    fun updateFiltered(filtered : List<Pair<String,Pair<String, String>>>){
        this.filteredList = filtered
        notifyDataSetChanged()
    }

    fun getList(): List<Pair<String, Pair<String, String>>>? {
        return this.usersList
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PermissionRecyclerViewHolder {
        val binding = ItemManageRecyclerViewBinding.inflate(
            LayoutInflater.from(parent.context),parent,false
        )
        return PermissionRecyclerViewHolder(binding)
    }

    override fun getItemCount(): Int {
        return filteredList?.size?:0
    }

    override fun onBindViewHolder(holder: PermissionRecyclerViewHolder, position: Int) {
        filteredList?.let { list->
            val user = list[position]
            holder.bind(user,{
                listener.givePermissionsClicked(it)
            },{
                listener.overridePermissionClicked(it)
            })
        }
    }

    interface  Listener {
        fun givePermissionsClicked(username:String)
        fun overridePermissionClicked(username:String)
    }

    class PermissionRecyclerViewHolder(private val binding: ItemManageRecyclerViewBinding):
        RecyclerView.ViewHolder(binding.root){
        private val context = binding.root.context

        fun bind(
            user: Pair<String, Pair<String, String>>,
            givePermissionsClicked:(String) -> Unit,
            overridePermissionClicked:(String)-> Unit
        ){
            binding.apply {
                when (user.second.second) {
                    "0" -> {
                        switchButton.text = "User"
                        switchButton.isChecked = false
                        switchButton.setOnClickListener() {
                            givePermissionsClicked.invoke(user.first)
                        }
                    }
                    "1" -> {
                        switchButton.text = "Administrator"
                        switchButton.isChecked = true
                        switchButton.setOnClickListener() {
                            overridePermissionClicked.invoke(user.first)
                        }
                    }
                }

                usernameTv.text = user.second.first
            }
        }
    }
}