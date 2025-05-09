package com.firebasevideocall.ui

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import com.firebasevideocall.R
import com.firebasevideocall.databinding.ActivitySigninBinding
import com.firebasevideocall.repository.MainRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SignInActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySigninBinding
    @Inject
    lateinit var mainRepository: MainRepository
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firebaseStore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        binding = ActivitySigninBinding.inflate(layoutInflater)
        setContentView(binding.root)
        firebaseAuth = FirebaseAuth.getInstance()
        firebaseStore = FirebaseFirestore.getInstance()
        setupUI()
    }

    private fun setupUI() {
        binding.textView.setOnClickListener {
            val intent = Intent(this, SignUpActivity::class.java)
            startActivity(intent)
        }

        binding.btn.setOnClickListener {
            val email = binding.usernameEt.text.toString().trim()
            val pass = binding.passwordEt.text.toString().trim()

            if (email.isNotEmpty() && pass.isNotEmpty()) {
                signInUser(email, pass)
            } else {
                showToast(getString(R.string.campos_vazios))
            }
        }
    }

    private fun signInUser(email: String, password: String) {
        firebaseAuth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = firebaseAuth.currentUser
                    if (user != null && user.isEmailVerified) {
                        checkUserAccessLevel(user.uid, email)
                        //loginUser(email)
                    } else {
                        showToast(getString(R.string.unverified_email))
                    }
                } else {
                    showToast(task.exception?.message.toString())
                }
            }
    }

    private fun checkUserAccessLevel(uid: String, email: String) {
        val df: DocumentReference = firebaseStore.collection("Users").document(uid)
        df.get().addOnSuccessListener {documentSnapshot ->
            if (checkAdmin(documentSnapshot)) {
                //isAdmin
                loginAdmin(email)
            } else {
                //isUser
                loginUser(email)
            }
        }.addOnFailureListener { exception ->
            showToast("Failed to get document: ${exception.message}")
        }
    }

    private fun checkAdmin(documentSnapshot: DocumentSnapshot): Boolean {
        return documentSnapshot.getString("isAdmin") == "1"
    }

    private fun loginUser(email: String) {
        mainRepository.login(email, null) { isDone, reason ->
            if (isDone) {
                navigateToMainActivity(email)
            } else {
                showToast(reason!!)
            }
        }
    }

    private fun loginAdmin(email: String) {
        mainRepository.login(email, null) { isDone, reason ->
            if (isDone) {
                navigateToAdminActivity(email)
            } else {
                showToast(reason!!)
            }
        }
    }

    private fun navigateToMainActivity(email: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("username", email)
        }
        startActivity(intent)
        finish()
    }

    private fun navigateToAdminActivity(email: String) {
        val intent = Intent(this, AdminActivity::class.java).apply {
            putExtra("username", email)
        }
        startActivity(intent)
        finish()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onStart() {
        super.onStart()
        val user = firebaseAuth.currentUser
        user?.let {
            if (it.isEmailVerified) {
                it.email?.let { it1 -> checkUserAccessLevel(it.uid, it1) }
            } else {
                showToast(getString(R.string.unverified_email))
            }
        }
    }
}