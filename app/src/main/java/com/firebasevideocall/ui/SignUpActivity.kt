package com.firebasevideocall.ui

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import com.firebasevideocall.R
import com.firebasevideocall.databinding.ActivitySignupBinding
import com.firebasevideocall.repository.MainRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SignUpActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignupBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firebaseStore: FirebaseFirestore
    @Inject
    lateinit var mainRepository: MainRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupBinding.inflate(layoutInflater)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        setContentView(binding.root)
        firebaseAuth = FirebaseAuth.getInstance()
        firebaseStore = FirebaseFirestore.getInstance()
        setupListeners()
    }

    private fun setupListeners() {
        binding.textView2.setOnClickListener {
            navigateToSignIn()
        }

        binding.btn.setOnClickListener {
            val email = binding.emailEt.text.toString()
            val id = binding.idEt.text.toString()
            val pass = binding.passwordEt.text.toString()
            val confirmPass = binding.passwordEt2.text.toString()

            if (validateInputs(email, id, pass, confirmPass)) {
                createUser(email, pass, id)
            }
        }
    }

    private fun validateInputs(email: String, id: String, pass: String, confirmPass: String): Boolean {
        return when {
            email.isEmpty() || id.isEmpty() || pass.isEmpty() || confirmPass.isEmpty() -> {
                showToast(getString(R.string.campos_vazios))
                false
            }
            pass != confirmPass -> {
                showToast(getString(R.string.wrong_password))
                false
            }
            else -> true
        }
    }

    private fun createUser2(email: String, pass: String, id: String) {
        firebaseAuth.createUserWithEmailAndPassword(email, pass)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user: FirebaseUser? = firebaseAuth.currentUser
                    mainRepository.register(email, id) { isDone, reason ->
                        if (!isDone) {
                            Toast.makeText(this@SignUpActivity, reason, Toast.LENGTH_SHORT).show()
                        } else {
                            val userInfo: MutableMap<String, Any> = mutableMapOf()
                            userInfo["email"] = email
                            userInfo["id"] = id
                            userInfo["isAdmin"] = 0
                            user?.let { firebaseStore.collection("Users").document(it.uid) }
                                ?.set(userInfo)
                            sendVerificationEmail()
                        }
                    }
                } else {
                    showToast(getString(R.string.failure_creating_user, task.exception?.message))
                }
            }.addOnFailureListener {
                showToast(getString(R.string.error_creating_user, it.message))
            }
    }

    private fun createUser(email: String, pass: String, id: String) {
        firebaseAuth.createUserWithEmailAndPassword(email, pass)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user: FirebaseUser? = firebaseAuth.currentUser
                    if (user != null) {
                        mainRepository.register(email, id) { isDone, reason ->
                            if (!isDone) {
                                if (reason != null) {
                                    showToast(reason)
                                }
                                rollbackUserCreation(user)
                            } else {
                                val userInfo: MutableMap<String, Any> = mutableMapOf()
                                userInfo["email"] = email
                                userInfo["id"] = id
                                userInfo["isAdmin"] = "0"
                                firebaseStore.collection("Users").document(user.uid)
                                    .set(userInfo)
                                    .addOnSuccessListener {
                                        sendVerificationEmail()
                                    }
                                    .addOnFailureListener { e ->
                                        showToast(
                                            getString(
                                                R.string.error_saving_user_info,
                                                e.message
                                            ))
                                        rollbackUserCreation(user)
                                    }
                            }
                        }
                    } else {
                        showToast(getString(R.string.error_getting_current_user))
                    }
                } else {
                    showToast(getString(R.string.failure_creating_user, task.exception?.message))
                }
            }
            .addOnFailureListener {
                showToast(getString(R.string.failure_creating_user, it.message))
            }
    }

    private fun rollbackUserCreation(user: FirebaseUser) {
        user.delete()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    showToast(
                        getString(R.string.user_removed) + " " +
                                getString(R.string.due_to,
                                    getString(R.string.registration_error))
                    )
                } else {
                    showToast(getString(R.string.error_removing_user, task.exception?.message))
                }
            }
    }

    private fun sendVerificationEmail() {
        val user = firebaseAuth.currentUser
        user?.sendEmailVerification()
            ?.addOnCompleteListener { verificationTask ->
                if (verificationTask.isSuccessful) {
                    showToast(getString(R.string.verification_email_sent))
                    navigateToSignIn()
                } else {
                    showToast(getString(R.string.failed_to_send_verification_email, ""))
                }
            }?.addOnFailureListener {
                showToast(getString(R.string.failed_to_send_verification_email, it.message))
            }
    }

    private fun navigateToSignIn() {
        val intent = Intent(this, SignInActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}