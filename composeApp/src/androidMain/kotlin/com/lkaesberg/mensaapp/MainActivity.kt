package com.lkaesberg.mensaapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lkaesberg.mensaapp.notifications.AndroidNotificationContext
import com.lkaesberg.mensaapp.notifications.FavoriteAlarms

class MainActivity : ComponentActivity() {

    private var pendingPermissionCallback: ((Boolean) -> Unit)? = null

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            pendingPermissionCallback?.invoke(granted)
            pendingPermissionCallback = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        AndroidNotificationContext.attach(applicationContext)
        // Re-arm the daily reminder (and retire the old periodic job after an update).
        FavoriteAlarms.rearmFromSettings(applicationContext)

        setContent {
            App(
                requestNotificationPermission = { onResult ->
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        val alreadyGranted = ContextCompat.checkSelfPermission(
                            this,
                            android.Manifest.permission.POST_NOTIFICATIONS,
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        if (alreadyGranted) {
                            onResult(true)
                        } else {
                            pendingPermissionCallback = onResult
                            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    } else {
                        // Pre-Android-13: no runtime permission. Notifications work
                        // unless the user disabled them at the channel/app level.
                        onResult(NotificationManagerCompat.from(this).areNotificationsEnabled())
                    }
                }
            )
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
