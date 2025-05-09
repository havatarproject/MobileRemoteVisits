package com.firebasevideocall.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.LinearLayoutManager
import com.firebasevideocall.adapters.PermissionRecyclerViewAdapter
import com.firebasevideocall.databinding.ActivityManagePermissionsBinding
import com.firebasevideocall.repository.MainRepository
import com.firebasevideocall.service.MainService
import com.firebasevideocall.service.MainServiceRepository
import com.firebasevideocall.utils.DataModel
import com.firebasevideocall.utils.Utils
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ManagePermissionsActivity : AppCompatActivity(), PermissionRecyclerViewAdapter.Listener, MainService.Listener {
    private val TAG = "ManagePermissionsActivity"

    private lateinit var views: ActivityManagePermissionsBinding
    private var username: String? = null
    @Inject
    lateinit var mainRepository: MainRepository
    @Inject
    lateinit var mainServiceRepository: MainServiceRepository
    private var permissionAdapter: PermissionRecyclerViewAdapter? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        views = ActivityManagePermissionsBinding.inflate(layoutInflater)
        setContentView(views.root)
        init()
    }

    private fun init() {
        username = intent.getStringExtra("username")
        if (username == null) finish()
        startMyService()
        subscribeObservers()
        setupSearch()
        Utils.addOnBackPressedCallback(this)
    }

    private fun startMyService() {
        mainServiceRepository.startService(username!!)
    }

    private fun setupRecyclerView() {
        permissionAdapter = PermissionRecyclerViewAdapter(this)
        val layoutManager = LinearLayoutManager(this)
        views.mainRecyclerView.apply {
            setLayoutManager(layoutManager)
            adapter = permissionAdapter
        }
    }

    private fun subscribeObservers() {
        setupRecyclerView()
        MainService.listener = this
        mainRepository.observePermissions {
            Log.d(TAG, "subscribeObservers: $it")
            permissionAdapter?.updateList(it)
        }
    }

    private fun setupSearch() {
        views.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString()
                filterList(query)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString()
                filterList(query)}
        })
    }

    private fun filterList(query: String) {
        Log.d("List:", permissionAdapter!!.getList()!!.toString())
        val filteredList = permissionAdapter!!.getList()!!.filter {
            it.second.first.contains(query, ignoreCase = true)
        }
        Log.d("List:", filteredList.toString())
        permissionAdapter?.updateFiltered(filteredList)
    }


    override fun onCallReceived(model: DataModel) {
        TODO("Does nothing")
    }

    override fun givePermissionsClicked(username: String) {
        Log.d("UserID: ", username)
        mainRepository.updateIsAdmin(
            username,"1"
        ){ isDone, reason ->
            if (!isDone){
                Toast.makeText(this@ManagePermissionsActivity, reason, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun overridePermissionClicked(username: String) {
        Log.d("UserName: ", username)
        mainRepository.updateIsAdmin(
            username,"0"
        ){ isDone, reason ->
            if (!isDone){
                Toast.makeText(this@ManagePermissionsActivity, reason, Toast.LENGTH_SHORT).show()
            }
        }
    }


}