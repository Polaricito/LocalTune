/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.OpenableColumns
import android.view.View
import android.view.WindowManager
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.datastore.preferences.core.edit
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_MUSIC
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import androidx.window.core.layout.WindowSizeClass
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.valentinilk.shimmer.LocalShimmerTheme
import dagger.hilt.android.AndroidEntryPoint
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.aod.ACTION_AOD_MODE
import moe.rukamori.archivetune.constants.AodAutoStartScreenOffKey
import moe.rukamori.archivetune.constants.AodModeEnabledKey
import moe.rukamori.archivetune.constants.AppBarHeight
import moe.rukamori.archivetune.constants.AppFontPreference
import moe.rukamori.archivetune.constants.CustomFontUriKey
import moe.rukamori.archivetune.constants.CustomThemeColorKey
import moe.rukamori.archivetune.constants.DarkModeKey
import moe.rukamori.archivetune.constants.DefaultOpenTabKey
import moe.rukamori.archivetune.constants.DisableAnimationsKey
import moe.rukamori.archivetune.constants.DisableScreenshotKey
import moe.rukamori.archivetune.constants.DynamicThemeKey
import moe.rukamori.archivetune.constants.EnableHapticFeedbackKey
import moe.rukamori.archivetune.constants.FontPreferenceKey
import moe.rukamori.archivetune.constants.MiniPlayerBottomSpacing
import moe.rukamori.archivetune.constants.MiniPlayerHeight
import moe.rukamori.archivetune.constants.MiniPlayerLastAnchorKey
import moe.rukamori.archivetune.constants.NavigationBarAnimationSpec
import moe.rukamori.archivetune.constants.NavigationBarBackgroundStyle
import moe.rukamori.archivetune.constants.NavigationBarBackgroundStyleKey
import moe.rukamori.archivetune.constants.NavigationBarBottomPadding
import moe.rukamori.archivetune.constants.NavigationBarHeight
import moe.rukamori.archivetune.constants.NavigationBarHorizontalPadding
import moe.rukamori.archivetune.constants.NavigationBarMode
import moe.rukamori.archivetune.constants.NavigationBarModeKey
import moe.rukamori.archivetune.constants.PlayerBackgroundStyle
import moe.rukamori.archivetune.constants.PlayerBackgroundStyleKey
import moe.rukamori.archivetune.constants.PlayerDesignStyle
import moe.rukamori.archivetune.constants.PlayerDesignStyleKey
import moe.rukamori.archivetune.constants.PureBlackKey
import moe.rukamori.archivetune.constants.StopMusicOnTaskClearKey
import moe.rukamori.archivetune.constants.UseSystemFontKey
import moe.rukamori.archivetune.constants.UseSystemLanguageKey
import moe.rukamori.archivetune.constants.WallpaperExtractionFailedKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.playback.MusicService
import moe.rukamori.archivetune.playback.MusicService.MusicBinder
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.playback.queues.Queue
import moe.rukamori.archivetune.ui.component.BottomSheetMenu
import moe.rukamori.archivetune.ui.component.BottomSheetPage
import moe.rukamori.archivetune.ui.component.COLLAPSED_ANCHOR
import moe.rukamori.archivetune.ui.component.DISMISSED_ANCHOR
import moe.rukamori.archivetune.ui.component.EXPANDED_ANCHOR
import moe.rukamori.archivetune.ui.component.FloatingNavigationToolbar
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.LocalBottomSheetPageState
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.TopAppBarOverflowMenu
import moe.rukamori.archivetune.ui.component.TvNavigationRail
import moe.rukamori.archivetune.ui.component.rememberBottomSheetState
import moe.rukamori.archivetune.ui.component.shimmer.ShimmerTheme
import moe.rukamori.archivetune.ui.player.BottomSheetPlayer
import moe.rukamori.archivetune.ui.screens.Screens
import moe.rukamori.archivetune.ui.screens.navigationBuilder
import moe.rukamori.archivetune.ui.screens.settings.DarkMode
import moe.rukamori.archivetune.ui.screens.settings.NavigationTab
import moe.rukamori.archivetune.ui.theme.ArchiveTuneTheme
import moe.rukamori.archivetune.ui.theme.ColorSaver
import moe.rukamori.archivetune.ui.theme.DefaultThemeColor
import moe.rukamori.archivetune.ui.theme.extractThemeColor
import moe.rukamori.archivetune.ui.theme.extractWallpaperThemeColor
import moe.rukamori.archivetune.ui.utils.appBarScrollBehavior
import moe.rukamori.archivetune.ui.utils.resetHeightOffset
import moe.rukamori.archivetune.utils.PreferenceStore
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import moe.rukamori.archivetune.utils.isLowRamDevice
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.utils.reportException
import moe.rukamori.archivetune.utils.setAppLocale
import moe.rukamori.archivetune.viewmodels.BackupCategory
import moe.rukamori.archivetune.viewmodels.BackupRestoreViewModel
import moe.rukamori.archivetune.viewmodels.MainMenuViewModel
import java.util.Locale
import javax.inject.Inject

@Suppress("DEPRECATION", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var database: MusicDatabase

    private lateinit var navController: NavHostController
    private var pendingIntent: Intent? = null
    private var pendingDeepLinkQueue: Queue? = null
    private var pendingAodModeRequest = false
    private var aodPreferenceReadJob: Job? = null
    private var pendingAodModeJob: Job? = null
    private var aodModeLaunchRequestCount by mutableIntStateOf(0)
    private var isAodScreenOffReceiverRegistered = false
    private var pendingBackupRestoreUri by mutableStateOf<Uri?>(null)

    private var playerConnection by mutableStateOf<PlayerConnection?>(null)
    private var isMusicServiceBound = false
    private var immersiveStatusBarsHidden = false

    private val aodScreenOffReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?,
            ) {
                if (intent?.action != Intent.ACTION_SCREEN_OFF) return
                if (playerConnection?.player?.isPlaying != true) return
                requestAodMode(requireAutoStart = true)
            }
        }

    private val serviceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?,
            ) {
                isMusicServiceBound = true
                if (service is MusicBinder) {
                    playerConnection =
                        PlayerConnection(this@MainActivity, service, database, lifecycleScope)
                    playPendingDeepLinkQueueIfReady()
                    openPendingAodModeIfReady()
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                isMusicServiceBound = false
                pendingAodModeJob?.cancel()
                pendingAodModeJob = null
                playerConnection?.dispose()
                playerConnection = null
            }
        }

    private fun playPendingDeepLinkQueueIfReady() {
        val pending = pendingDeepLinkQueue ?: return
        val connection = playerConnection ?: return
        pendingDeepLinkQueue = null
        connection.playQueue(pending)
    }

    private fun requestAodMode(requireAutoStart: Boolean = false) {
        aodPreferenceReadJob?.cancel()
        aodPreferenceReadJob =
            lifecycleScope.launch {
                try {
                    val preferences = dataStore.data.first()
                    val isAodEnabled = preferences[AodModeEnabledKey] ?: false
                    val shouldAutoStart = preferences[AodAutoStartScreenOffKey] ?: true
                    if (!isAodEnabled || (requireAutoStart && !shouldAutoStart)) return@launch

                    pendingAodModeRequest = true
                    startMusicServiceSafely()
                    openPendingAodModeIfReady()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (throwable: Throwable) {
                    pendingAodModeRequest = false
                    reportException(throwable)
                }
            }
    }

    private fun registerAodScreenOffReceiver() {
        if (isAodScreenOffReceiverRegistered) return
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(aodScreenOffReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(aodScreenOffReceiver, filter)
        }
        isAodScreenOffReceiverRegistered = true
    }

    private fun unregisterAodScreenOffReceiver() {
        if (!isAodScreenOffReceiverRegistered) return
        unregisterReceiver(aodScreenOffReceiver)
        isAodScreenOffReceiverRegistered = false
    }

    private fun openPendingAodModeIfReady() {
        if (!pendingAodModeRequest) return
        val connection = playerConnection ?: return
        pendingAodModeRequest = false
        pendingAodModeJob?.cancel()
        pendingAodModeJob =
            lifecycleScope.launch {
                connection.queueRestoreCompleted.first { it }
                if (hasRestorablePlayback(connection)) {
                    aodModeLaunchRequestCount++
                }
            }
    }

    private fun hasRestorablePlayback(connection: PlayerConnection): Boolean =
        (
            connection.player.currentMediaItem != null ||
                connection.player.mediaItemCount > 0 ||
                connection.mediaMetadata.value != null
        )

    private suspend fun awaitRestorablePlayback(connection: PlayerConnection): Boolean {
        repeat(15) {
            if (hasRestorablePlayback(connection)) return true
            delay(100)
        }

        return hasRestorablePlayback(connection)
    }

    override fun onStart() {
        super.onStart()
        registerAodScreenOffReceiver()
        isMusicServiceBound =
            bindService(
                Intent(this, MusicService::class.java),
                serviceConnection,
                Context.BIND_AUTO_CREATE,
            )
        playPendingDeepLinkQueueIfReady()
        openPendingAodModeIfReady()
    }

    private fun safeUnbindMusicService() {
        if (!isMusicServiceBound) return
        try {
            unbindService(serviceConnection)
        } catch (e: IllegalArgumentException) {
        } catch (e: Exception) {
            reportException(e)
        } finally {
            isMusicServiceBound = false
        }
    }

    override fun onStop() {
        unregisterAodScreenOffReceiver()
        if (!isMusicServiceBound || playerConnection?.aodModeEnabled?.value == true) {
            super.onStop()
            return
        }
        safeUnbindMusicService()
        super.onStop()
    }

    override fun onDestroy() {
        aodPreferenceReadJob?.cancel()
        aodPreferenceReadJob = null
        super.onDestroy()

        val shouldStopOnTaskClear =
            if (!isFinishing) {
                false
            } else {
                dataStore.get(StopMusicOnTaskClearKey, false)
            }

        if (shouldStopOnTaskClear) {
            playerConnection?.service?.stopAndClearPlayback(clearPersistentState = true)
            safeUnbindMusicService()
            stopService(Intent(this, MusicService::class.java))
            playerConnection = null
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && immersiveStatusBarsHidden) {
            setStatusBarsHidden(true)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (::navController.isInitialized) {
            handleIntent(intent, navController)
        } else {
            pendingIntent = intent
        }
    }

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_LTR
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val initialLocale =
            if (PreferenceStore.get(UseSystemLanguageKey) ?: true) {
                Locale.getDefault()
            } else {
                Locale.ENGLISH
            }
        setAppLocale(this, initialLocale)

        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                dataStore.data.first().let { prefs ->
                    if (prefs[UseSystemLanguageKey] ?: true) {
                        Locale.getDefault()
                    } else {
                        Locale.ENGLISH
                    }
                }
            }.onSuccess { targetLocale ->
                if (targetLocale != initialLocale) {
                    withContext(Dispatchers.Main) {
                        setAppLocale(this@MainActivity, targetLocale)
                        recreate()
                    }
                }
            }
        }

        lifecycleScope.launch(Dispatchers.IO) {
            dataStore.data
                .map { it[DisableScreenshotKey] ?: false }
                .distinctUntilChanged()
                .collectLatest {
                    withContext(Dispatchers.Main) {
                        if (it) {
                            window.setFlags(
                                WindowManager.LayoutParams.FLAG_SECURE,
                                WindowManager.LayoutParams.FLAG_SECURE,
                            )
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        }
                    }
                }
        }

        setContent {
            val bottomSheetPageState = remember { moe.rukamori.archivetune.ui.component.BottomSheetPageState() }
            val menuState = remember { moe.rukamori.archivetune.ui.component.MenuState() }

            val enableDynamicTheme by rememberPreference(DynamicThemeKey, defaultValue = true)
            val customThemeColorValue by rememberPreference(CustomThemeColorKey, defaultValue = "default")
            val darkTheme by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
            val defaultDisableAnimations = remember(this@MainActivity) { applicationContext.isLowRamDevice() }
            val disableAnimations by rememberPreference(
                DisableAnimationsKey,
                defaultValue = defaultDisableAnimations,
            )
            val fontPreference by rememberEnumPreference(FontPreferenceKey, defaultValue = AppFontPreference.DEFAULT)
            val customFontUri by rememberPreference(CustomFontUriKey, defaultValue = "")
            val legacyUseSystemFont by rememberPreference(UseSystemFontKey, defaultValue = false)
            val isSystemInDarkTheme = isSystemInDarkTheme()
            val useDarkTheme =
                remember(darkTheme, isSystemInDarkTheme) {
                    if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
                }
            val pureBlackEnabled by rememberPreference(PureBlackKey, defaultValue = false)
            val pureBlack = pureBlackEnabled && useDarkTheme

            val customThemeSeedPalette =
                remember(customThemeColorValue) {
                    if (customThemeColorValue.startsWith("#")) {
                        null
                    } else if (customThemeColorValue.startsWith("seedPalette:")) {
                        moe.rukamori.archivetune.ui.theme.ThemeSeedPaletteCodec
                            .decodeFromPreference(customThemeColorValue)
                    } else {
                        moe.rukamori.archivetune.ui.screens.settings.ThemePalettes
                            .findById(customThemeColorValue)
                            ?.let {
                                moe.rukamori.archivetune.ui.theme.ThemeSeedPalette(
                                    primary = it.primary,
                                    secondary = it.secondary,
                                    tertiary = it.tertiary,
                                    neutral = it.neutral,
                                )
                            }
                    }
                }

            val customThemeColor =
                remember(customThemeColorValue, customThemeSeedPalette) {
                    if (customThemeColorValue.startsWith("#")) {
                        try {
                            val colorString = customThemeColorValue.removePrefix("#")
                            Color(android.graphics.Color.parseColor("#$colorString"))
                        } catch (e: Exception) {
                            DefaultThemeColor
                        }
                    } else {
                        customThemeSeedPalette?.primary ?: DefaultThemeColor
                    }
                }

            var themeColor by rememberSaveable(stateSaver = ColorSaver) {
                mutableStateOf(DefaultThemeColor)
            }

            LaunchedEffect(legacyUseSystemFont) {
                if (!legacyUseSystemFont) return@LaunchedEffect
                val preferences = dataStore.data.first()
                if (preferences[FontPreferenceKey] == null) {
                    dataStore.edit { it[FontPreferenceKey] = AppFontPreference.SYSTEM.name }
                }
            }

            LaunchedEffect(playerConnection, enableDynamicTheme, isSystemInDarkTheme, customThemeColor) {
                val playerConnection = playerConnection
                if (!enableDynamicTheme || playerConnection == null) {
                    themeColor = if (!enableDynamicTheme) customThemeColor else DefaultThemeColor
                    return@LaunchedEffect
                }
                playerConnection.service.currentMediaMetadata.collectLatest { song ->
                    if (song != null) {
                        withContext(Dispatchers.Default) {
                            try {
                                val result =
                                    imageLoader.execute(
                                        ImageRequest
                                            .Builder(this@MainActivity)
                                            .data(song.thumbnailUrl)
                                            .allowHardware(false)
                                            .build(),
                                    )
                                val extractedColor = result.image?.toBitmap()?.extractThemeColor()
                                withContext(Dispatchers.Main) {
                                    themeColor = extractedColor ?: DefaultThemeColor
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    themeColor = DefaultThemeColor
                                }
                            }
                        }
                    } else {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            themeColor = DefaultThemeColor
                        } else {
                            val wallpaperColor = extractWallpaperThemeColor(this@MainActivity)
                            themeColor = wallpaperColor ?: customThemeColor
                            dataStore.edit { prefs ->
                                prefs[WallpaperExtractionFailedKey] = wallpaperColor == null
                            }
                        }
                    }
                }
            }

            ArchiveTuneTheme(
                darkTheme = useDarkTheme,
                pureBlack = pureBlack,
                themeColor = themeColor,
                seedPalette = if (!enableDynamicTheme) customThemeSeedPalette else null,
                disableAnimations = disableAnimations,
                fontPreference = fontPreference,
                customFontUri = customFontUri,
            ) {
                val navController = rememberNavController()

                BoxWithConstraints(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(
                                if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface,
                            ),
                ) {
                    val density = LocalDensity.current
                    val windowsInsets = WindowInsets.systemBars
                    val bottomInset = with(density) { windowsInsets.getBottom(density).toDp() }

                    val isTvDevice = remember { applicationContext.isTvDevice() }
                    val useRail =
                        isTvDevice ||
                            currentWindowAdaptiveInfo()
                                .windowSizeClass
                                .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

                    DisposableEffect(navController) {
                        this@MainActivity.navController = navController
                        onDispose {}
                    }

                    val coroutineScope = rememberCoroutineScope()
                    val mainMenuViewModel: MainMenuViewModel = hiltViewModel()
                    val topAppBarOverflowExpanded by mainMenuViewModel.expanded.collectAsStateWithLifecycle()
                    val menuHazeState = rememberHazeState()
                    val floatingBarsHazeState = rememberHazeState()
                    val navigationBarBackgroundStyle by rememberEnumPreference(
                        key = NavigationBarBackgroundStyleKey,
                        defaultValue = NavigationBarBackgroundStyle.THEME,
                    )
                    val navigationBarMode by rememberEnumPreference(
                        key = NavigationBarModeKey,
                        defaultValue = NavigationBarMode.CONNECTED,
                    )
                    val menuLifecycleOwner = LocalLifecycleOwner.current
                    LaunchedEffect(mainMenuViewModel, navController, menuLifecycleOwner) {
                        menuLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                            mainMenuViewModel.destinations.collect { destination ->
                                navController.navigate(destination.route) { launchSingleTop = true }
                            }
                        }
                    }

                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route

                    val navigationItems =
                        remember(isTvDevice) {
                            if (isTvDevice) Screens.TvMainScreens else Screens.MainScreens
                        }
                    val (savedMiniPlayerAnchor, setSavedMiniPlayerAnchor) =
                        rememberPreference(
                            MiniPlayerLastAnchorKey,
                            defaultValue = COLLAPSED_ANCHOR,
                        )
                    val defaultOpenTab by rememberEnumPreference(DefaultOpenTabKey, NavigationTab.SEARCH)
                    val tabOpenedFromShortcut =
                        remember {
                            when (intent?.action) {
                                ACTION_LIBRARY -> NavigationTab.LIBRARY
                                ACTION_SEARCH -> NavigationTab.SEARCH
                                else -> null
                            }
                        }

                    val topLevelScreens =
                        remember(navigationItems) {
                            navigationItems.map(Screens::route) + "settings"
                        }

                    val shouldShowNavigationBar =
                        remember(navBackStackEntry) {
                            navBackStackEntry?.destination?.route == null ||
                                navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }
                        }

                    fun getBottomNavPadding(): Dp =
                        if (shouldShowNavigationBar && !useRail) {
                            NavigationBarHeight
                        } else {
                            0.dp
                        }

                    val floatingBarsBottomPadding = NavigationBarBottomPadding
                    val navVisibleHeight = NavigationBarHeight

                    val bottomNavigationBarHeightState = animateDpAsState(
                        targetValue = if (shouldShowNavigationBar && !useRail) navVisibleHeight else 0.dp,
                        animationSpec = if (disableAnimations) snap() else NavigationBarAnimationSpec,
                        label = "",
                    )
                    val bottomNavigationBarHeight by bottomNavigationBarHeightState

                    val playerBottomSheetState =
                        rememberBottomSheetState(
                            dismissedBound = 0.dp,
                            collapsedBound =
                                bottomInset +
                                    (if (shouldShowNavigationBar && !useRail) floatingBarsBottomPadding else 0.dp) +
                                    getBottomNavPadding() +
                                    MiniPlayerBottomSpacing +
                                    MiniPlayerHeight,
                            expandedBound = maxHeight,
                        )

                    val playerBackground by rememberEnumPreference(
                        key = PlayerBackgroundStyleKey,
                        defaultValue = PlayerBackgroundStyle.DEFAULT,
                    )
                    val playerDesignStyle by rememberEnumPreference(
                        key = PlayerDesignStyleKey,
                        defaultValue = PlayerDesignStyle.V4,
                    )

                    val aodModeEnabled by remember(playerConnection) {
                        playerConnection?.aodModeEnabled ?: MutableStateFlow(false)
                    }.collectAsStateWithLifecycle()

                    LaunchedEffect(currentRoute) {
                        mainMenuViewModel.dismiss()
                    }

                    LaunchedEffect(aodModeLaunchRequestCount, playerConnection) {
                        val launchRequestCount = aodModeLaunchRequestCount
                        if (launchRequestCount == 0) return@LaunchedEffect
                        val connection = playerConnection ?: return@LaunchedEffect
                        if (!hasRestorablePlayback(connection)) return@LaunchedEffect
                        if (!playerBottomSheetState.isExpandedOrExpanding) {
                            playerBottomSheetState.expandSoft()
                        }
                        connection.aodModeEnabled.value = true
                        if (aodModeLaunchRequestCount == launchRequestCount) {
                            aodModeLaunchRequestCount = 0
                        }
                    }

                    LaunchedEffect(aodModeEnabled) {
                        val controller = WindowCompat.getInsetsController(window, window.decorView)
                        if (aodModeEnabled) {
                            controller.systemBarsBehavior =
                                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                            controller.hide(WindowInsetsCompat.Type.systemBars())
                            @Suppress("DEPRECATION")
                            window.addFlags(
                                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                                    WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
                                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
                            )
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                                setShowWhenLocked(true)
                                setTurnScreenOn(true)
                            }
                        } else {
                            controller.show(WindowInsetsCompat.Type.systemBars())
                            controller.systemBarsBehavior =
                                WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                            @Suppress("DEPRECATION")
                            window.clearFlags(
                                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                                    WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
                                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
                            )
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                                setShowWhenLocked(false)
                                setTurnScreenOn(false)
                            }
                        }
                    }

                    LaunchedEffect(useDarkTheme, playerBottomSheetState.isExpanded, playerBackground, aodModeEnabled) {
                        if (aodModeEnabled) return@LaunchedEffect
                        val isDarkStatusBar =
                            if (playerBottomSheetState.isExpanded &&
                                playerBackground != PlayerBackgroundStyle.DEFAULT
                            ) {
                                true
                            } else {
                                useDarkTheme
                            }
                        setSystemBarAppearance(isDarkStatusBar)
                    }

                    val miniPlayerAnchor by remember(playerBottomSheetState) {
                        derivedStateOf {
                            when {
                                playerBottomSheetState.isExpanded -> EXPANDED_ANCHOR
                                playerBottomSheetState.isDismissed -> DISMISSED_ANCHOR
                                else -> COLLAPSED_ANCHOR
                            }
                        }
                    }

                    var miniPlayerAnchorPersistenceEnabled by remember(playerConnection) {
                        mutableStateOf(false)
                    }

                    LaunchedEffect(miniPlayerAnchor, miniPlayerAnchorPersistenceEnabled) {
                        if (miniPlayerAnchorPersistenceEnabled) {
                            setSavedMiniPlayerAnchor(miniPlayerAnchor)
                        }
                    }

                    val shouldHideStatusBars =
                        playerBottomSheetState.isExpandedOrExpanding &&
                            playerDesignStyle == PlayerDesignStyle.V7

                    LaunchedEffect(shouldHideStatusBars, aodModeEnabled) {
                        if (aodModeEnabled) return@LaunchedEffect
                        setStatusBarsHidden(shouldHideStatusBars)
                    }

                    val playerAwareWindowInsets =
                        remember(
                            useRail,
                            bottomInset,
                            shouldShowNavigationBar,
                            playerBottomSheetState.isDismissed,
                        ) {
                            var bottom = bottomInset
                            if (shouldShowNavigationBar && !useRail) {
                                bottom += getBottomNavPadding() + floatingBarsBottomPadding
                            }
                            if (!playerBottomSheetState.isDismissed) {
                                bottom += MiniPlayerHeight + MiniPlayerBottomSpacing
                            }
                            windowsInsets
                                .only(
                                    (
                                        if (useRail) {
                                            WindowInsetsSides.Right
                                        } else {
                                            WindowInsetsSides.Horizontal
                                        }
                                    ) + WindowInsetsSides.Top,
                                ).add(WindowInsets(top = AppBarHeight, bottom = bottom))
                        }

                    val topAppBarScrollBehavior =
                        appBarScrollBehavior(
                            canScroll = {
                                currentRoute != Screens.Library.route &&
                                    !playerBottomSheetState.isExpandedOrExpanding
                            },
                        )
                    val libraryScrollBehavior =
                        appBarScrollBehavior(
                            canScroll = {
                                currentRoute == Screens.Library.route &&
                                    !playerBottomSheetState.isExpandedOrExpanding
                            },
                        )

                    val handlePrimaryNavigationClick: (Screens, Boolean) -> Unit = { screen, isSelected ->
                        if (isSelected) {
                            if (screen == Screens.Library) {
                                coroutineScope.launch { libraryScrollBehavior.state.resetHeightOffset() }
                            }
                        } else {
                            if (navController.currentDestination?.route != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    }

                    LaunchedEffect(currentRoute, playerBottomSheetState.isExpanded) {
                        if (!playerBottomSheetState.isExpanded) {
                            when (currentRoute) {
                                Screens.Library.route -> {
                                    libraryScrollBehavior.state.resetHeightOffset()
                                }

                                else -> {}
                            }
                        }
                    }

                    LaunchedEffect(navBackStackEntry) {
                        val route = navBackStackEntry?.destination?.route
                        if ((
                                route?.startsWith("artist/") == true ||
                                    route?.startsWith("album/") == true ||
                                    route?.startsWith("local_playlist/") == true
                            ) &&
                            playerBottomSheetState.isExpandedOrExpanding
                        ) {
                            playerBottomSheetState.collapseSoft()
                        }
                    }

                    var restoredMiniPlayerAnchor by remember(playerConnection) { mutableStateOf(false) }

                    LaunchedEffect(playerConnection, savedMiniPlayerAnchor, playerBottomSheetState) {
                        if (restoredMiniPlayerAnchor) return@LaunchedEffect
                        val connection = playerConnection ?: return@LaunchedEffect
                        connection.queueRestoreCompleted.first { it }
                        if (!awaitRestorablePlayback(connection)) {
                            if (!playerBottomSheetState.isDismissed) {
                                playerBottomSheetState.dismiss()
                            }
                        } else {
                            when (savedMiniPlayerAnchor) {
                                EXPANDED_ANCHOR -> playerBottomSheetState.expandSoft()
                                COLLAPSED_ANCHOR -> playerBottomSheetState.collapseSoft()
                                DISMISSED_ANCHOR -> playerBottomSheetState.collapseSoft()
                                else -> playerBottomSheetState.collapseSoft()
                            }
                        }
                        restoredMiniPlayerAnchor = true
                        miniPlayerAnchorPersistenceEnabled = true
                    }

                    val currentPlayerBottomSheetState = rememberUpdatedState(playerBottomSheetState)

                    DisposableEffect(playerConnection) {
                        val player = playerConnection?.player ?: return@DisposableEffect onDispose { }
                        val listener =
                            object : Player.Listener {
                                private fun collapseDismissedMiniPlayerForActivePlayback() {
                                    if (
                                        player.mediaItemCount > 0 &&
                                        player.currentMediaItem != null &&
                                        player.playWhenReady &&
                                        player.playbackState != Player.STATE_IDLE &&
                                        player.playbackState != Player.STATE_ENDED &&
                                        currentPlayerBottomSheetState.value.isDismissed
                                    ) {
                                        currentPlayerBottomSheetState.value.collapseSoft()
                                    }
                                }

                                override fun onMediaItemTransition(
                                    mediaItem: MediaItem?,
                                    reason: Int,
                                ) {
                                    collapseDismissedMiniPlayerForActivePlayback()
                                }

                                override fun onTimelineChanged(
                                    timeline: Timeline,
                                    reason: Int,
                                ) {
                                    collapseDismissedMiniPlayerForActivePlayback()
                                }

                                override fun onPlaybackStateChanged(playbackState: Int) {
                                    collapseDismissedMiniPlayerForActivePlayback()
                                }

                                override fun onPlayWhenReadyChanged(
                                    playWhenReady: Boolean,
                                    reason: Int,
                                ) {
                                    collapseDismissedMiniPlayerForActivePlayback()
                                }
                            }
                        player.addListener(listener)
                        onDispose {
                            player.removeListener(listener)
                        }
                    }

                    LaunchedEffect(Unit) {
                        if (pendingIntent != null) {
                            handleIntent(pendingIntent, navController)
                            pendingIntent = null
                        } else {
                            handleIntent(intent, navController)
                        }
                    }

                    val haptic = LocalHapticFeedback.current
                    val (enableHapticFeedback) = rememberPreference(EnableHapticFeedbackKey, true)
                    val customHaptic =
                        remember(haptic, enableHapticFeedback) {
                            object : HapticFeedback {
                                override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                                    if (enableHapticFeedback) {
                                        haptic.performHapticFeedback(hapticFeedbackType)
                                    }
                                }
                            }
                        }

                    val playerBackDispatcherOwner = checkNotNull(LocalNavigationEventDispatcherOwner.current)
                    val screenBackEnabled by remember(playerBottomSheetState) {
                        derivedStateOf {
                            !playerBottomSheetState.isExpandedOrExpanding &&
                                playerBottomSheetState.progress <= 0f
                        }
                    }
                    val screenBackDispatcherOwner =
                        rememberNavigationEventDispatcherOwner(
                            enabled = screenBackEnabled,
                            parent = playerBackDispatcherOwner,
                        )

                    CompositionLocalProvider(
                        LocalHapticFeedback provides customHaptic,
                        LocalAnimationsDisabled provides disableAnimations,
                        LocalDatabase provides database,
                        LocalContentColor provides if (pureBlack) Color.White else contentColorFor(MaterialTheme.colorScheme.surface),
                        LocalPlayerConnection provides playerConnection,
                        LocalPlayerAwareWindowInsets provides playerAwareWindowInsets,
                        LocalShimmerTheme provides ShimmerTheme,
                        moe.rukamori.archivetune.ui.component.LocalBottomSheetPageState provides bottomSheetPageState,
                        moe.rukamori.archivetune.ui.component.LocalMenuState provides menuState,
                    ) {
                        CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides screenBackDispatcherOwner) {
                            Row {
                                androidx.compose.animation.AnimatedVisibility(
                                    visible =
                                        useRail &&
                                            shouldShowNavigationBar &&
                                            (isTvDevice || !playerBottomSheetState.isExpandedOrExpanding),
                                    enter = fadeIn(animationSpec = tween(durationMillis = if (disableAnimations) 0 else 150)),
                                    exit = fadeOut(animationSpec = tween(durationMillis = if (disableAnimations) 0 else 100)),
                                ) {
                                    if (isTvDevice) {
                                        TvNavigationRail(
                                            items = navigationItems,
                                            selectedItemRoute = currentRoute,
                                            modifier = Modifier,
                                            onItemClick = { screen ->
                                                val wasPlayerActive = playerBottomSheetState.isExpandedOrExpanding
                                                if (wasPlayerActive) {
                                                    playerBottomSheetState.collapse(if (disableAnimations) snap() else spring())
                                                }
                                                val isSelected =
                                                    navBackStackEntry?.destination?.hierarchy?.any { it.route == screen.route } == true
                                                if (wasPlayerActive && isSelected) {
                                                    return@TvNavigationRail
                                                }
                                                handlePrimaryNavigationClick(screen, isSelected)
                                            },
                                        )
                                    } else {
                                        NavigationRail(
                                            containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer,
                                            contentColor = if (pureBlack) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                            header = { androidx.compose.foundation.layout.Spacer(Modifier.height(24.dp)) },
                                        ) {
                                            navigationItems.fastForEach { screen ->
                                                val isSelected =
                                                    navBackStackEntry?.destination?.hierarchy?.any { it.route == screen.route } == true

                                                NavigationRailItem(
                                                    selected = isSelected,
                                                    icon = {
                                                        Icon(
                                                            painter =
                                                                painterResource(
                                                                    id = if (isSelected) screen.iconIdActive else screen.iconIdInactive,
                                                                ),
                                                            contentDescription = null,
                                                        )
                                                    },
                                                    label = {
                                                        Text(
                                                            text = stringResource(screen.titleId),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                        )
                                                    },
                                                    onClick = {
                                                        val wasPlayerActive = playerBottomSheetState.isExpanded

                                                        if (wasPlayerActive) {
                                                            playerBottomSheetState.collapse(if (disableAnimations) snap() else spring())
                                                        }

                                                        if (wasPlayerActive && isSelected) return@NavigationRailItem
                                                        handlePrimaryNavigationClick(screen, isSelected)
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }

                                Scaffold(
                                    containerColor = Color.Transparent,
                                    topBar = {
                                        val shouldShowTopBar =
                                            currentRoute == Screens.Search.route ||
                                                currentRoute == Screens.Library.route
                                        if (shouldShowTopBar) {
                                            TopAppBar(
                                                windowInsets =
                                                    WindowInsets.safeDrawing.only(
                                                        (
                                                            if (useRail) {
                                                                WindowInsetsSides.Right
                                                            } else {
                                                                WindowInsetsSides.Horizontal
                                                            }
                                                        ) + WindowInsetsSides.Top,
                                                    ),
                                                title = {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            painter = painterResource(R.drawable.about_appbar),
                                                            contentDescription = null,
                                                            modifier =
                                                                Modifier
                                                                    .size(35.dp)
                                                                    .padding(end = 3.dp),
                                                        )
                                                        Text(
                                                            text = stringResource(R.string.app_name),
                                                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                        )
                                                    }
                                                },
                                                actions = {
                                                    Box {
                                                        IconButton(
                                                            onClick = mainMenuViewModel::toggle,
                                                            onLongClick = {},
                                                        ) {
                                                            Icon(
                                                                painter = painterResource(R.drawable.more_horiz),
                                                                contentDescription = stringResource(R.string.more_options),
                                                            )
                                                        }
                                                        TopAppBarOverflowMenu(
                                                            expanded = topAppBarOverflowExpanded,
                                                            hazeState = menuHazeState,
                                                            pureBlack = pureBlack,
                                                            onDismissRequest = mainMenuViewModel::dismiss,
                                                            onSelect = { destination ->
                                                                mainMenuViewModel.select(destination)
                                                            },
                                                        )
                                                    }
                                                },
                                                colors =
                                                    TopAppBarDefaults.topAppBarColors(
                                                        containerColor = Color.Transparent,
                                                        scrolledContainerColor = Color.Transparent,
                                                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                                                        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    ),
                                            )
                                        }
                                    },
                                    bottomBar = {
                                        Box {
                                            val navigationProximityProvider: () -> Float =
                                                remember(playerBottomSheetState, bottomNavigationBarHeightState) {
                                                    {
                                                        val navRatio =
                                                            (bottomNavigationBarHeightState.value / navVisibleHeight).coerceIn(0f, 1f)
                                                        val isNavTransitioning =
                                                            bottomNavigationBarHeightState.value > 0.dp &&
                                                                bottomNavigationBarHeightState.value < navVisibleHeight
                                                        val morphThreshold = MiniPlayerHeight + MiniPlayerBottomSpacing
                                                        val swipeDeviation =
                                                            if (isNavTransitioning && playerBottomSheetState.targetAnchor == COLLAPSED_ANCHOR) {
                                                                0.dp
                                                            } else {
                                                                playerBottomSheetState.value.let { v ->
                                                                    if (v < playerBottomSheetState.collapsedBound) {
                                                                        playerBottomSheetState.collapsedBound - v
                                                                    } else {
                                                                        v - playerBottomSheetState.collapsedBound
                                                                    }
                                                                }
                                                            }
                                                        val sheetPresence = (1f - (swipeDeviation / morphThreshold)).coerceIn(0f, 1f)
                                                        navRatio * sheetPresence
                                                    }
                                                }

                                            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides playerBackDispatcherOwner) {
                                                BottomSheetPlayer(
                                                    state = playerBottomSheetState,
                                                    navController = navController,
                                                    hazeState = floatingBarsHazeState,
                                                    pureBlack = pureBlack,
                                                    navigationProximityProvider = navigationProximityProvider,
                                                    navigationBarMode = navigationBarMode,
                                                )
                                            }

                                            if (useRail) return@Box

                                            val navSlideDistance =
                                                bottomInset + floatingBarsBottomPadding + navVisibleHeight

                                            Box(
                                                modifier =
                                                    Modifier
                                                        .align(Alignment.BottomCenter)
                                                        .height(navSlideDistance)
                                                        .offset {
                                                            if (bottomNavigationBarHeight == 0.dp) {
                                                                IntOffset(
                                                                    x = 0,
                                                                    y = navSlideDistance.roundToPx(),
                                                                )
                                                            } else {
                                                                val slideOffset =
                                                                    navSlideDistance *
                                                                        playerBottomSheetState.progress.coerceIn(
                                                                            0f,
                                                                            1f,
                                                                        )
                                                                val hideOffset =
                                                                    navSlideDistance *
                                                                        (
                                                                            1 -
                                                                                bottomNavigationBarHeight.coerceAtMost(navVisibleHeight) /
                                                                                navVisibleHeight
                                                                        )
                                                                IntOffset(
                                                                    x = 0,
                                                                    y = (slideOffset + hideOffset).roundToPx(),
                                                                )
                                                            }
                                                        },
                                            ) {
                                                FloatingNavigationToolbar(
                                                    items = navigationItems,
                                                    pureBlack = pureBlack,
                                                    backgroundStyle = navigationBarBackgroundStyle,
                                                    hazeState = floatingBarsHazeState,
                                                    miniPlayerProximityProvider = navigationProximityProvider,
                                                    navigationBarMode = navigationBarMode,
                                                    modifier =
                                                        Modifier
                                                            .align(Alignment.BottomCenter)
                                                            .padding(
                                                                start = NavigationBarHorizontalPadding,
                                                                end = NavigationBarHorizontalPadding,
                                                                bottom = bottomInset + floatingBarsBottomPadding,
                                                            ).height(navVisibleHeight),
                                                    isSelected = { screen ->
                                                        navBackStackEntry?.destination?.hierarchy?.any { it.route == screen.route } ==
                                                            true
                                                    },
                                                    onItemClick = { screen, isSelected ->
                                                        val wasPlayerActive = playerBottomSheetState.isExpandedOrExpanding
                                                        if (wasPlayerActive) {
                                                            playerBottomSheetState.collapse(
                                                                if (disableAnimations) snap() else spring(),
                                                            )
                                                        }
                                                        if (!wasPlayerActive || !isSelected) {
                                                            handlePrimaryNavigationClick(screen, isSelected)
                                                        }
                                                    },
                                                )
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize().hazeSource(menuHazeState),
                                ) {
                                    NavHost(
                                        navController = navController,
                                        startDestination =
                                            when (tabOpenedFromShortcut ?: defaultOpenTab) {
                                                NavigationTab.LIBRARY -> Screens.Library.route
                                                NavigationTab.HISTORY -> Screens.History.route
                                                else -> Screens.Search.route
                                            },
                                        enterTransition = {
                                            if (disableAnimations) {
                                                fadeIn(tween(0))
                                            } else if (initialState.destination.route in topLevelScreens &&
                                                targetState.destination.route in topLevelScreens
                                            ) {
                                                fadeIn(tween(250))
                                            } else {
                                                fadeIn(tween(250)) + slideInHorizontally(animationSpec = tween(250)) { it }
                                            }
                                        },
                                        exitTransition = {
                                            if (disableAnimations) {
                                                fadeOut(tween(0))
                                            } else if (initialState.destination.route in topLevelScreens &&
                                                targetState.destination.route in topLevelScreens
                                            ) {
                                                fadeOut(tween(200))
                                            } else {
                                                fadeOut(tween(200)) + slideOutHorizontally(animationSpec = tween(200)) { -it }
                                            }
                                        },
                                        popEnterTransition = {
                                            if (disableAnimations) {
                                                fadeIn(tween(0))
                                            } else if (initialState.destination.route in topLevelScreens &&
                                                targetState.destination.route in topLevelScreens
                                            ) {
                                                fadeIn(tween(250))
                                            } else {
                                                slideInHorizontally(animationSpec = tween(250)) { -it }
                                            }
                                        },
                                        popExitTransition = {
                                            if (disableAnimations) {
                                                fadeOut(tween(0))
                                            } else if (initialState.destination.route in topLevelScreens &&
                                                targetState.destination.route in topLevelScreens
                                            ) {
                                                fadeOut(tween(200))
                                            } else {
                                                slideOutHorizontally(animationSpec = tween(200)) { it }
                                            }
                                        },
                                        modifier =
                                            Modifier
                                                .hazeSource(floatingBarsHazeState)
                                                .then(
                                                    if (isTvDevice) {
                                                        Modifier
                                                            .focusGroup()
                                                            .focusable()
                                                    } else {
                                                        Modifier
                                                    },
                                                ),
                                    ) {
                                        navigationBuilder(
                                            navController,
                                            topAppBarScrollBehavior,
                                            libraryScrollBehavior,
                                            disableAnimations,
                                        )
                                    }
                                }
                            }
                        }

                        BottomSheetMenu(
                            state = LocalMenuState.current,
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )

                        BottomSheetPage(
                            state = LocalBottomSheetPageState.current,
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }

                    pendingBackupRestoreUri?.let { uri ->
                        BackupRestoreFromIntentDialog(uri = uri)
                    }
                }
            }
        }
    }

    private fun isBackupUri(uri: Uri?): Boolean {
        if (uri == null) return false
        val path = uri.lastPathSegment?.lowercase(Locale.US)
        return path?.endsWith(".backup") == true || uri.toString().lowercase(Locale.US).endsWith(".backup")
    }

    private fun handleIntent(
        intent: Intent?,
        navController: NavHostController,
    ) {
        if (intent == null) return
        intent.getStringExtra("navigate_to")?.takeIf { it.isNotBlank() }?.let { route ->
            navController.navigate(route) {
                launchSingleTop = true
            }
            intent.removeExtra("navigate_to")
            return
        }
        if (intent.action == ACTION_SEARCH) {
            navController.navigate(Screens.Search.route) { launchSingleTop = true }
            return
        }
        if (intent.action == ACTION_LIBRARY) {
            navController.navigate(Screens.Library.route) { launchSingleTop = true }
            return
        }
        val dataUri = intent.data
        if (isBackupUri(dataUri)) {
            pendingBackupRestoreUri = dataUri
            return
        }
        if (intent.action == ACTION_AOD_MODE) {
            requestAodMode()
            return
        }
        if (handleExternalAudioIntent(intent)) {
            return
        }
    }

    private fun handleExternalAudioIntent(intent: Intent): Boolean {
        val incomingUris =
            buildList {
                intent.data?.let(::add)
                when (intent.action) {
                    Intent.ACTION_SEND -> {
                        IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let(::add)
                    }

                    Intent.ACTION_SEND_MULTIPLE -> {
                        addAll(
                            IntentCompat
                                .getParcelableArrayListExtra(
                                    intent,
                                    Intent.EXTRA_STREAM,
                                    Uri::class.java,
                                ).orEmpty(),
                        )
                    }
                }
            }.distinct()

        if (incomingUris.isEmpty()) return false

        val fallbackMimeType = intent.type
        val playableUris =
            incomingUris.filter { uri ->
                val mimeType = contentResolver.getType(uri)
                mimeType.isAudioMimeType() || fallbackMimeType.isAudioMimeType() || uri.hasAudioExtension()
            }
        if (playableUris.isEmpty()) return false

        pendingDeepLinkQueue = ListQueue(items = playableUris.map(::toExternalAudioMediaItem))
        startMusicServiceSafely()
        playPendingDeepLinkQueueIfReady()
        return true
    }

    private fun toExternalAudioMediaItem(uri: Uri): MediaItem {
        val mediaId = uri.toString()
        val title = resolveExternalAudioTitle(uri)
        val metadata =
            moe.rukamori.archivetune.models.MediaMetadata(
                id = mediaId,
                title = title,
                artists = emptyList(),
                duration = -1,
            )
        return MediaItem
            .Builder()
            .setMediaId(mediaId)
            .setUri(uri)
            .setTag(metadata)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata
                    .Builder()
                    .setTitle(title)
                    .setIsPlayable(true)
                    .setMediaType(MEDIA_TYPE_MUSIC)
                    .build(),
            ).build()
    }

    private fun resolveExternalAudioTitle(uri: Uri): String {
        val displayName =
            runCatching {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (columnIndex >= 0 && cursor.moveToFirst()) cursor.getString(columnIndex) else null
                }
            }.getOrNull()
        return displayName
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment
                ?.substringAfterLast('/')
                ?.substringBefore('?')
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            ?: getString(R.string.unknown)
    }

    private fun String?.isAudioMimeType(): Boolean = this?.startsWith("audio/", ignoreCase = true) == true

    private fun Uri.hasAudioExtension(): Boolean {
        val extension = MimeTypeMap.getFileExtensionFromUrl(toString()).orEmpty()
        val normalized = extension.lowercase(Locale.US)
        return normalized in setOf("aac", "flac", "m4a", "mp3", "ogg", "opus", "wav", "webm")
    }

    private fun startMusicServiceSafely() {
        runCatching { startService(Intent(this, moe.rukamori.archivetune.playback.MusicService::class.java)) }
            .onFailure { reportException(it) }
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun setSystemBarAppearance(isDark: Boolean) {
        WindowCompat.getInsetsController(window, window.decorView.rootView).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            window.statusBarColor =
                (if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.2f)).toArgb()
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            window.navigationBarColor =
                (if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.2f)).toArgb()
        }
    }

    private fun setStatusBarsHidden(hidden: Boolean) {
        immersiveStatusBarsHidden = hidden
        val controller = WindowCompat.getInsetsController(window, window.decorView)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes =
                window.attributes.apply {
                    layoutInDisplayCutoutMode =
                        if (hidden) {
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                        } else {
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                        }
                }
        }

        if (hidden) {
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.statusBars())
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            controller.show(WindowInsetsCompat.Type.statusBars())
        }
    }

    @Composable
    private fun BackupRestoreFromIntentDialog(
        uri: Uri,
        viewModel: BackupRestoreViewModel = hiltViewModel(),
    ) {
        var selected by remember { mutableStateOf(BackupCategory.entries.toSet()) }

        AlertDialog(
            onDismissRequest = { pendingBackupRestoreUri = null },
            icon = { Icon(painterResource(R.drawable.restore), null) },
            title = { Text(stringResource(R.string.restore_options_title)) },
            text = {
                Column {
                    BackupCategory.entries.forEach { category ->
                        val isChecked = category in selected
                        val labelRes =
                            when (category) {
                                BackupCategory.LIBRARY -> R.string.backup_category_library
                                BackupCategory.SETTINGS -> R.string.backup_category_settings
                            }
                        val descRes =
                            when (category) {
                                BackupCategory.LIBRARY -> R.string.backup_category_library_desc
                                BackupCategory.SETTINGS -> R.string.backup_category_settings_desc
                            }
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = Color.Transparent,
                            onClick = {
                                selected = if (isChecked) selected - category else selected + category
                            },
                        ) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 72.dp)
                                        .padding(horizontal = 4.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(labelRes),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = stringResource(descRes),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                androidx.compose.material3.Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        selected = if (checked) selected + category else selected - category
                                    },
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val uri = pendingBackupRestoreUri ?: return@TextButton
                        pendingBackupRestoreUri = null
                        viewModel.restore(this@MainActivity, uri, selected)
                    },
                    enabled = selected.isNotEmpty(),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.action_restore))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingBackupRestoreUri = null },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    companion object {
        const val ACTION_SEARCH = "moe.rukamori.archivetune.action.SEARCH"
        const val ACTION_LIBRARY = "moe.rukamori.archivetune.action.LIBRARY"
    }
}

val LocalDatabase = staticCompositionLocalOf<MusicDatabase> { error("No database provided") }
val LocalPlayerConnection =
    staticCompositionLocalOf<PlayerConnection?> { error("No PlayerConnection provided") }
val LocalPlayerAwareWindowInsets =
    compositionLocalOf<WindowInsets> { error("No WindowInsets provided") }

private fun Context.isTvDevice(): Boolean {
    val isTelevisionUiMode =
        resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_TYPE_MASK ==
            android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
    val hasLeanback = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    return isTelevisionUiMode || hasLeanback
}
