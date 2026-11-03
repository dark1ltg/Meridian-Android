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
import androidx.activity.OnBackPressedCallback
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

    companion object {
        private const val STATE_GATE = "meridian_gate"

        /** Splash is a cold-start intro only — skip it after the first advance in this process. */
        @Volatile
        private var splashDoneThisProcess = false
    }

    private val pickTree = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri == null) {
            vm.clearFolderPickMessage()
            return@registerForActivityResult
        }
        // Always keep the SAF grant and ingest through DocumentFile. A bare
        // filesystem path often looks like a directory but cannot be listed
        // under scoped storage — which made Add look like a no-op.
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
        }
        vm.ingestTree(uri.toString())
    }

    private val ask = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val prefs = getSharedPreferences("meridian", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("permission_prompt_done", true).apply()
        if (result.containsKey(Manifest.permission.POST_NOTIFICATIONS)) {
            prefs.edit().putBoolean("notify_prompt_done", true).apply()
        }
        val ok = hasAudioPermission() || result[audioPermission()] == true
        if (ok) {
            vm.boot()
            applyFolders(pendingFolders)
        } else {
            vm.boot()
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
        gate = initialGate(prefs, savedInstanceState)
        // Root back backgrounds the task instead of finishing — otherwise
        // overview relaunch recreates MainActivity and replayed the splash.
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    moveTaskToBack(true)
                }
            },
        )
        vm.onPickFolder = { pickTree.launch(null) }
        if (hasAudioPermission()) vm.boot()
        maybeRequestNotificationPermission()
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
                        Gate.Permission -> PermissionScreen(
                            onContinue = { music, downloads, other, allAudio ->
                                pendingFolders = FolderChoice(music, downloads, other, allAudio)
                                prefs.edit().putBoolean("permission_prompt_done", true).apply()
                                if (hasAudioPermission()) {
                                    vm.boot()
                                    applyFolders(pendingFolders)
                                    pendingFolders = null
                                    gate = Gate.App
                                    maybeRequestNotificationPermission()
                                } else {
                                    ask.launch(neededPermissions())
                                }
                            },
                            onNotNow = {
                                prefs.edit().putBoolean("permission_prompt_done", true).apply()
                                vm.boot()
                                gate = Gate.App
                            },
                        )
                        Gate.App -> {
                            val ui by vm.state.collectAsState()
                            MainScreen(
                                vm = vm,
                                ui = ui,
                                onLeaveApp = { moveTaskToBack(true) },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_GATE, gate.name)
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
        try {
            unregisterReceiver(unlockReceiver)
        } catch (_: IllegalArgumentException) {
        }
        super.onDestroy()
    }

    private fun initialGate(
        prefs: android.content.SharedPreferences,
        savedInstanceState: Bundle?,
    ): Gate {
        val restored = savedInstanceState?.getString(STATE_GATE)?.let { name ->
            runCatching { Gate.valueOf(name) }.getOrNull()
        }
        if (restored != null && restored != Gate.Splash) {
            splashDoneThisProcess = true
            return restored
        }
        // Already past splash in this process (activity recreated after finish /
        // overview reopen) — jump straight back into the app.
        if (splashDoneThisProcess) {
            return postSplashGate(prefs)
        }
        return Gate.Splash
    }

    private fun postSplashGate(prefs: android.content.SharedPreferences): Gate = when {
        !prefs.getBoolean("onboarded", false) -> Gate.Onboarding
        !hasAudioPermission() && !prefs.getBoolean("permission_prompt_done", false) -> Gate.Permission
        else -> Gate.App
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
        // Once the user has answered the music-access screen (Continue or Not
        // now), do not re-show it every cold start — even if they denied the
        // system dialog. They can still grant access from system Settings and
        // add folders via SAF.
        splashDoneThisProcess = true
        gate = postSplashGate(prefs)
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

    private fun audioPermission(): String =
        if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    private fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, audioPermission()) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    private val notifyPerm = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        getSharedPreferences("meridian", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("notify_prompt_done", true)
            .apply()
    }

    /** Ask for notifications at most once unless already granted. */
    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val prefs = getSharedPreferences("meridian", Context.MODE_PRIVATE)
        if (prefs.getBoolean("notify_prompt_done", false)) return
        notifyPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun neededPermissions(): Array<String> {
        val list = mutableListOf<String>()
        if (!hasAudioPermission()) {
            list += audioPermission()
        }
        // Notifications stay optional and are prompted separately once — bundling
        // them with music access made "Allow all the time" feel ignored.
        return list.toTypedArray()
    }
}
