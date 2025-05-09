package com.firebasevideocall.utils

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startActivity
import androidx.fragment.app.FragmentActivity
import com.firebasevideocall.service.MainServiceRepository
import com.firebasevideocall.ui.SignInActivity
import com.google.firebase.auth.FirebaseAuth

class Utils {
    companion object {
        fun addLogoutOnBackPressedCallback(
            activity: FragmentActivity,
            mainServiceRepository: MainServiceRepository
        ) {
            activity.onBackPressedDispatcher.addCallback(activity, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    AlertDialog.Builder(activity)
                        .setTitle("Sair da Conta")
                        .setMessage("Tem a certeza que deseja sair?")
                        .setPositiveButton("Sim") { _, _ ->
                            mainServiceRepository.stopService()
                            FirebaseAuth.getInstance().signOut()

                            // Create Intent
                            val intent = Intent(activity, SignInActivity::class.java).apply {
                            }
                            activity.startActivity(intent) // Start activity using the Intent
                        }
                        .setNegativeButton("Não") { dialog, _ ->
                            dialog.dismiss()
                        }
                        .create()
                        .show()
                }
            })
        }

        fun addOnBackPressedCallback(activity: FragmentActivity) {
            val callback = object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    isEnabled = false
                    // Trigger the normal back press behavior
                    activity.onBackPressedDispatcher.onBackPressed()
                }
            }
            activity.onBackPressedDispatcher.addCallback(activity, callback)
        }


        fun registerForNotificationPermission(activity: FragmentActivity): ActivityResultLauncher<String> {
            return activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (isGranted) {
                    // FCM SDK (and your app) can post notifications.
                } else {
                    AlertDialog.Builder(activity)
                        .setTitle("Notification Permission Denied")
                        .setMessage("Without notification permission, this app will not be able to show notifications. Please enable notifications in your device settings.")
                        .setPositiveButton("OK") { dialog, _ ->
                            dialog.dismiss()
                        }
                        .create()
                        .show()
                }
            }
        }

        fun askNotificationPermission(context: Context, requestPermissionLauncher: ActivityResultLauncher<String>) {
            // This is only necessary for API level >= 33 (TIRAMISU)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    // FCM SDK (and your app) can post notifications.
                }
                else if ((context as FragmentActivity).shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                    // TODO: display an educational UI explaining to the user the features that will be enabled
                    //       by them granting the POST_NOTIFICATION permission. This UI should provide the user
                    //       "OK" and "No thanks" buttons. If the user selects "OK," directly request the permission.
                    //       If the user selects "No thanks," allow the user to continue without notifications.
                }
                else {
                    // Directly ask for the permission
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }

        fun encodeEmail(username: String?): String {
            return username!!.replace(".", ",")
        }

        fun decodeEmail(encodedEmail: String): String {
            return encodedEmail.replace(",", ".")
        }
    }
}