package io.github.dark1ltg.meridian

import android.Manifest
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import io.github.dark1ltg.meridian.data.FolderPaths
import io.github.dark1ltg.meridian.playback.DeviceUnlock
import io.github.dark1ltg.meridian.ui.MainScreen
import io.github.dark1ltg.meridian.ui.OnboardingScreen
import io.github.dark1ltg.meridian.ui.PermissionScreen
import io.github.dark1ltg.meridian.ui.SessionViewModel
import io.github.dark1ltg.meridian.ui.SplashScreen
import io.github.dark1ltg.meridian.ui.theme.MeridianTheme
import io.github.dark1ltg.meridian.ui.theme.Night
import kotlinx.coroutines.delay
import java.io.File

private enum class Gate { Splash, Onboarding, Permission, App }

private data class FolderChoice(
    val music: Boolean,
    val downloads: Boolean,
    val other: Boolean,
    val allAudio: Boolean,
)

class MainActivity : ComponentActivity() {
    private val vm: SessionViewModel by viewModels()
    private var gate by mutableStateOf(Gate.Splash)
    private var pendingFolders: FolderChoice? = null
    private var revealUi by mutableStateOf(false)
    private var promptingUnlock = false

    private val pickTree = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
        }
        val path = FolderPaths.fromTreeUri(uri.toString())
        val asFile = path?.let { java.io.File(it) }
        if (asFile != null && asFile.isDirectory) {
            vm.addFolder(asFile.absolutePath)
        } else {
            vm.ingestTree(uri.toString())
        }
    }

    private val ask = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val ok = result.values.any { it } || hasAudioPermission()
        if (ok) {
            vm.boot()
            applyFolders(pendingFolders)
        }
        pendingFolders = null
        gate = Gate.App
    }

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_USER_PRESENT || intent?.action == Intent.ACTION_USER_UNLOCKED) {
                onDeviceUnlocked()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refuseLockscreenOcclusion()
        enableEdgeToEdge()
        val prefs = getSharedPreferences("meridian", Context.MODE_PRIVATE)
        vm.onPickFolder = { pickTree.launch(null) }
        if (hasAudioPermission()) vm.boot()
        requestNotificationPermission()
        ContextCompat.registerReceiver(
            this,
            unlockReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_USER_UNLOCKED)
            },
            ContextCompat.RECEIVER_EXPORTED,
        )
        syncUnlockState()
        setContent {
            MeridianTheme {
                if (!revealUi) {
                    Box(Modifier.fillMaxSize().background(Night))
                } else {
                    when (gate) {
                        Gate.Splash -> {
                            SplashScreen(onContinue = { advanceFromSplash(prefs) })
                            LaunchedEffect(Unit) {
                                delay(1600)
                                if (gate == Gate.Splash) advanceFromSplash(prefs)
                            }
                        }
                        Gate.Onboarding -> OnboardingScreen(
                            onGetStarted = {
                                prefs.edit().putBoolean("onboarded", true).apply()
                                gate = if (hasAudioPermission()) Gate.App else Gate.Permission
                            },
                            onSkip = {
                                prefs.edit().putBoolean("onboarded", true).apply()
                                gate = if (hasAudioPermission()) Gate.App else Gate.Permission
                            },
                        )
                        Gate.Permission ->                         PermissionScreen(
                            onContinue = { music, downloads, other, allAudio ->
                                pendingFolders = FolderChoice(music, downloads, other, allAudio)
                                if (hasAudioPermission()) {
                                    vm.boot()
                                    applyFolders(pendingFolders)
                                    gate = Gate.App
                                } else {
                                    ask.launch(neededPermissions())
                                }
                            },
                            onNotNow = {
                                vm.boot()
                                gate = Gate.App
                            },
                        )
                        Gate.App -> {
                            val ui by vm.state.collectAsState()
                            MainScreen(vm, ui)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        syncUnlockState()
    }

    override fun onResume() {
        super.onResume()
        syncUnlockState()
    }

    override fun onDestroy() {
        unregisterReceiver(unlockReceiver)
        super.onDestroy()
    }

    private fun keyguard(): KeyguardManager =
        getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

    private fun refuseLockscreenOcclusion() {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(false)
            setTurnScreenOn(false)
        }
        @Suppress("DEPRECATION")
        window.clearFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
        )
        if (Build.VERSION.SDK_INT >= 33) {
            setRecentsScreenshotEnabled(false)
        }
    }

    private fun syncUnlockState() {
        val locked = DeviceUnlock.isLocked(keyguard())
        if (DeviceUnlock.canRevealApp(locked)) {
            onDeviceUnlocked()
        } else {
            revealUi = false
            if (Build.VERSION.SDK_INT >= 33) {
                setRecentsScreenshotEnabled(false)
            }
            promptDeviceCredential()
        }
    }

    private fun promptDeviceCredential() {
        if (promptingUnlock) return
        promptingUnlock = true
        val km = keyguard()
        // Temporarily occupy the lockscreen with an empty window so the system
        // credential sheet (PIN / password / biometric / face) can be shown.
        // Library UI stays hidden until onDismissSucceeded / USER_PRESENT.
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
        if (Build.VERSION.SDK_INT >= 26) {
            km.requestDismissKeyguard(
                this,
                object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() {
                        promptingUnlock = false
                        onDeviceUnlocked()
                    }

                    override fun onDismissCancelled() {
                        promptingUnlock = false
                        refuseLockscreenOcclusion()
                        moveTaskToBack(true)
                    }

                    override fun onDismissError() {
                        promptingUnlock = false
                        refuseLockscreenOcclusion()
                    }
                },
            )
        } else {
            promptingUnlock = false
        }
    }

    private fun onDeviceUnlocked() {
        refuseLockscreenOcclusion()
        if (Build.VERSION.SDK_INT >= 33) {
            setRecentsScreenshotEnabled(true)
        }
        revealUi = true
        promptingUnlock = false
    }

    private fun advanceFromSplash(prefs: android.content.SharedPreferences) {
        gate = when {
            !prefs.getBoolean("onboarded", false) -> Gate.Onboarding
            !hasAudioPermission() -> Gate.Permission
            else -> Gate.App
        }
    }

    private fun applyFolders(choice: FolderChoice?) {
        if (choice == null) return
        val paths = mutableListOf<String>()
        if (choice.music) {
            paths += Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).absolutePath
        }
        if (choice.downloads) {
            paths += Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
        }
        if (choice.other) {
            paths += File(Environment.getExternalStorageDirectory(), "Documents").absolutePath
        }
        vm.importSources(paths, choice.allAudio)
    }

    private fun hasAudioPermission(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(this, perm) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private val notifyPerm = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notifyPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun neededPermissions(): Array<String> {
        val list = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            list += Manifest.permission.READ_MEDIA_AUDIO
            list += Manifest.permission.POST_NOTIFICATIONS
        } else {
            list += Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return list.toTypedArray()
    }
}
