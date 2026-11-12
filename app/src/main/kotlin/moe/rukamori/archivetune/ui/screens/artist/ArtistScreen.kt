/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.artist

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.valentinilk.shimmer.shimmer
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.artist.OnlineArtistInfo
import moe.rukamori.archivetune.artist.OnlineArtistRelease
import moe.rukamori.archivetune.constants.AppBarHeight
import moe.rukamori.archivetune.constants.CONTENT_TYPE_ALBUM
import moe.rukamori.archivetune.constants.CONTENT_TYPE_HEADER
import moe.rukamori.archivetune.constants.CONTENT_TYPE_SONG
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.db.entities.Album
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.ui.component.AlbumGridItem
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.MediaDetailIconAction
import moe.rukamori.archivetune.ui.component.MediaDetailPrimaryActions
import moe.rukamori.archivetune.ui.component.NavigationTitle
import moe.rukamori.archivetune.ui.component.SongListItem
import moe.rukamori.archivetune.ui.component.shimmer.ButtonPlaceholder
import moe.rukamori.archivetune.ui.component.shimmer.ListItemPlaceHolder
import moe.rukamori.archivetune.ui.component.shimmer.ShimmerHost
import moe.rukamori.archivetune.ui.component.shimmer.TextPlaceholder
import moe.rukamori.archivetune.ui.menu.AlbumMenu
import moe.rukamori.archivetune.ui.menu.SongMenu
import moe.rukamori.archivetune.ui.utils.YtimgResizePolicy
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.ui.utils.formatCompactCount
import moe.rukamori.archivetune.ui.utils.navigateToAlbum
import moe.rukamori.archivetune.ui.utils.resize
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.ArtistAction
import moe.rukamori.archivetune.viewmodels.ArtistBlockState
import moe.rukamori.archivetune.viewmodels.ArtistEvent
import moe.rukamori.archivetune.viewmodels.ArtistViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ArtistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: ArtistViewModel = hiltViewModel(),
) {
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val libraryArtist by viewModel.libraryArtist.collectAsStateWithLifecycle()
    val loadedLibrarySongs by viewModel.librarySongs.collectAsStateWithLifecycle()
    val loadedLibraryAlbums by viewModel.libraryAlbums.collectAsStateWithLifecycle()
    val blockState by viewModel.blockState.collectAsStateWithLifecycle()
    val onlineArtistInfo by viewModel.onlineArtistInfo.collectAsStateWithLifecycle()
    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)
    val isArtistBlocked = (blockState as? ArtistBlockState.Success)?.isBlocked == true
    val librarySongs = remember(loadedLibrarySongs, isArtistBlocked) { loadedLibrarySongs.takeUnless { isArtistBlocked }.orEmpty() }
    val libraryAlbums = remember(loadedLibraryAlbums, isArtistBlocked) { loadedLibraryAlbums.takeUnless { isArtistBlocked }.orEmpty() }

    val lazyListState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ArtistEvent.ShowMessage -> {
                    snackbarHostState.showSnackbar(context.getString(event.messageRes))
                }
            }
        }
    }

    val systemBarsTopPadding = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
    val surfaceColor = MaterialTheme.colorScheme.surface
    val heroContentColor =
        if (surfaceColor.luminance() > 0.5f) {
            MaterialTheme.colorScheme.onSurface
        } else {
            Color.White
        }
    val artistName = libraryArtist?.artist?.name
    val localThumbnail = libraryArtist?.artist?.thumbnailUrl
    val onlineThumbnail = onlineArtistInfo?.pictureUrl
    val thumbnail = localThumbnail ?: onlineThumbnail

    val transparentAppBar by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset < 100
        }
    }

    val libraryLatestRelease =
        remember(libraryAlbums) {
            libraryAlbums.maxByOrNull { it.album.year ?: Int.MIN_VALUE }
        }

    val onlineLatestRelease = onlineArtistInfo?.latestRelease

    val filteredLibrarySongs =
        remember(librarySongs, hideExplicit) {
            if (hideExplicit) librarySongs.filter { !it.song.explicit } else librarySongs
        }

    val showArtistOverflowMenu: () -> Unit = {
        menuState.show {
            ArtistOverflowMenu(
                isBlocked = isArtistBlocked,
                blockActionEnabled = blockState !is ArtistBlockState.Loading,
                onAction = { action ->
                    viewModel.onAction(action)
                    menuState.dismiss()
                },
            )
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(surfaceColor),
    ) {
        LazyColumn(
            state = lazyListState,
            contentPadding =
                PaddingValues(
                    bottom = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding(),
                ),
        ) {
            if (libraryArtist == null) {
                item(key = "shimmer") {
                    ShimmerHost {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = ArtistHeroMinHeight)
                                    .shimmer()
                                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
                        ) {
                            Column(
                                modifier =
                                    Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .padding(horizontal = ArtistHorizontalPadding, vertical = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                TextPlaceholder(height = 36.dp, modifier = Modifier.fillMaxWidth(0.55f))
                                Spacer(modifier = Modifier.height(12.dp))
                                TextPlaceholder(height = 16.dp, modifier = Modifier.fillMaxWidth(0.72f))
                                Spacer(modifier = Modifier.height(20.dp))
                                TextPlaceholder(height = 14.dp, modifier = Modifier.fillMaxWidth(0.82f))
                                Spacer(modifier = Modifier.height(12.dp))
                                ButtonPlaceholder(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .height(ButtonDefaults.MediumContainerHeight),
                                )
                            }
                        }

                        repeat(5) {
                            ListItemPlaceHolder()
                        }
                    }
                }
            } else {
                item(key = "header") {
                    val unknownArtist = stringResource(R.string.unknown_artist)
                    val songsLabel = stringResource(R.string.songs)
                    val albumsLabel = stringResource(R.string.albums)
                    val catalog =
                        buildList {
                            if (librarySongs.isNotEmpty()) {
                                add("${formatCompactCount(librarySongs.size.toLong())} $songsLabel")
                            }
                            if (libraryAlbums.isNotEmpty()) {
                                add("${formatCompactCount(libraryAlbums.size.toLong())} $albumsLabel")
                            }
                        }.joinToString(ArtistStatSeparator)
                    val isSubscribed = libraryArtist?.artist?.bookmarkedAt != null

                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = ArtistHeroMinHeight)
                                .background(surfaceColor),
                    ) {
                        if (thumbnail != null) {
                            AsyncImage(
                                model =
                                    thumbnail.resize(
                                        width = ArtistHeroArtworkSizePx,
                                        height = ArtistHeroArtworkSizePx,
                                        sizeBuckets = ArtistHeroArtworkSizeBuckets,
                                        ytimgResizePolicy = YtimgResizePolicy.PreserveOriginal,
                                    ),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.matchParentSize(),
                            )
                        } else {
                            Box(
                                modifier =
                                    Modifier
                                        .matchParentSize()
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.person),
                                    contentDescription = null,
                                    modifier = Modifier.size(96.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Box(
                            modifier =
                                Modifier
                                    .matchParentSize()
                                    .background(
                                        Brush.verticalGradient(
                                            0f to Color.Black.copy(alpha = 0.42f),
                                            0.18f to Color.Transparent,
                                            0.42f to Color.Transparent,
                                            0.72f to surfaceColor.copy(alpha = 0.78f),
                                            1f to surfaceColor,
                                        ),
                                    ),
                        )

                        Column(
                            modifier =
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .padding(
                                        start = ArtistHorizontalPadding,
                                        top = systemBarsTopPadding + AppBarHeight + 96.dp,
                                        end = ArtistHorizontalPadding,
                                        bottom = 24.dp,
                                    ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = artistName ?: unknownArtist,
                                style = MaterialTheme.typography.headlineLarge,
                                color = heroContentColor,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )

                            if (catalog.isNotEmpty()) {
                                Text(
                                    text = catalog,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = heroContentColor.copy(alpha = 0.62f),
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp),
                                )
                            }

                            ArtistPrimaryActions(
                                isSubscribed = isSubscribed,
                                contentColor = heroContentColor,
                                contrastingColor = surfaceColor,
                                canShuffle = librarySongs.isNotEmpty(),
                                canPlay = librarySongs.isNotEmpty(),
                                onShuffle = {
                                    if (librarySongs.isNotEmpty()) {
                                        playerConnection.playQueue(
                                            ListQueue(
                                                title = artistName ?: unknownArtist,
                                                items = librarySongs.shuffled().map { it.toMediaItem() },
                                            ),
                                        )
                                    }
                                },
                                onPlay = {
                                    if (librarySongs.isNotEmpty()) {
                                        playerConnection.playQueue(
                                            ListQueue(
                                                title = artistName ?: unknownArtist,
                                                items = librarySongs.map { it.toMediaItem() },
                                            ),
                                        )
                                    }
                                },
                                onToggleSubscription = {
                                    database.transaction {
                                        libraryArtist?.artist?.let { artist ->
                                            update(artist.toggleLike())
                                        }
                                    }
                                },
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                    }
                }

                libraryLatestRelease?.let { album ->
                    item(
                        key = "new_release_${album.id}",
                        contentType = CONTENT_TYPE_ALBUM,
                    ) {
                        ArtistNewReleaseSection(
                            title = album.album.title,
                            thumbnailUrl = album.album.thumbnailUrl,
                            year = album.album.year,
                            onClick = { navController.navigateToAlbum(album.id) },
                        )
                    }
                }

                onlineLatestRelease?.let { release ->
                    item(
                        key = "online_new_release_${release.title.hashCode()}",
                        contentType = CONTENT_TYPE_ALBUM,
                    ) {
                        ArtistOnlineReleaseItem(release)
                    }
                }

                if (librarySongs.isNotEmpty()) {
                    item(key = "songs_header", contentType = CONTENT_TYPE_HEADER) {
                        NavigationTitle(
                            title = stringResource(R.string.songs),
                            onClick = {
                                navController.navigate("artist/${viewModel.artistId}/songs")
                            },
                        )
                    }

                    itemsIndexed(
                        items = filteredLibrarySongs.take(5),
                        key = { _, item -> "local_song_${item.id}" },
                        contentType = { _, _ -> CONTENT_TYPE_SONG },
                    ) { index, song ->
                        SongListItem(
                            song = song,
                            showInLibraryIcon = true,
                            isActive = song.id == mediaMetadata?.id,
                            isPlaying = isPlaying,
                            trailingContent = {
                                IconButton(
                                    onClick = {
                                        menuState.show {
                                            SongMenu(
                                                originalSong = song,
                                                navController = navController,
                                                onDismiss = menuState::dismiss,
                                            )
                                        }
                                    },
                                    onLongClick = {},
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.more_vert),
                                        contentDescription = null,
                                    )
                                }
                            },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            if (song.id == mediaMetadata?.id) {
                                                playerConnection.player.togglePlayPause()
                                            } else {
                                                playerConnection.playQueue(
                                                    ListQueue(
                                                        title = artistName ?: "Unknown Artist",
                                                        items = filteredLibrarySongs.map { it.toMediaItem() },
                                                        startIndex = index,
                                                    ),
                                                )
                                            }
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            menuState.show {
                                                SongMenu(
                                                    originalSong = song,
                                                    navController = navController,
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }
                                        },
                                    ).animateItem(),
                        )
                    }

                    if (filteredLibrarySongs.size > 5) {
                        item(key = "songs_view_all") {
                            Surface(
                                onClick = {
                                    navController.navigate("artist/${viewModel.artistId}/songs")
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.view_all),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }

                if (libraryAlbums.isNotEmpty()) {
                    item(key = "albums_header", contentType = CONTENT_TYPE_HEADER) {
                        NavigationTitle(
                            title = stringResource(R.string.albums),
                            onClick = {
                                navController.navigate("artist/${viewModel.artistId}/albums")
                            },
                        )
                    }

                    item(key = "albums_list", contentType = CONTENT_TYPE_ALBUM) {
                        val filteredLibraryAlbums =
                            if (hideExplicit) {
                                libraryAlbums.filter { !it.album.explicit }
                            } else {
                                libraryAlbums
                            }

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(
                                items = filteredLibraryAlbums,
                                key = { album -> "local_album_${album.id}" },
                                contentType = { CONTENT_TYPE_ALBUM },
                            ) { album ->
                                AlbumGridItem(
                                    album = album,
                                    isActive = mediaMetadata?.album?.id == album.id,
                                    isPlaying = isPlaying,
                                    coroutineScope = coroutineScope,
                                    modifier =
                                        Modifier
                                            .combinedClickable(
                                                onClick = {
                                                    navController.navigateToAlbum(album.id)
                                                },
                                                onLongClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    menuState.show {
                                                        AlbumMenu(
                                                            originalAlbum = Album(album.album, album.artists),
                                                            navController = navController,
                                                            onDismiss = menuState::dismiss,
                                                        )
                                                    }
                                                },
                                            ).animateItem(),
                                )
                            }
                        }
                    }
                }

                item(key = "bottom_spacer") {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier
                    .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
                    .align(Alignment.BottomCenter),
        )
    }

    TopAppBar(
        title = {
            val animatedAlpha by animateFloatAsState(
                targetValue = if (!transparentAppBar) 1f else 0f,
                animationSpec = tween(200),
                label = "titleAlpha",
            )
            Text(
                text = artistName.orEmpty(),
                modifier = Modifier.alpha(animatedAlpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        },
        actions = {
            IconButton(
                onClick = showArtistOverflowMenu,
                onLongClick = {},
            ) {
                Icon(
                    painter = painterResource(R.drawable.more_horiz),
                    contentDescription = stringResource(R.string.more_options),
                )
            }
        },
        colors =
            if (transparentAppBar) {
                TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    navigationIconContentColor = Color.White,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White,
                )
            } else {
                TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                )
            },
        scrollBehavior = scrollBehavior,
    )
}

@Composable
private fun ArtistOverflowMenu(
    isBlocked: Boolean,
    blockActionEnabled: Boolean,
    onAction: (ArtistAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        SegmentedListItem(
            onClick = { onAction(ArtistAction.ToggleBlock) },
            enabled = blockActionEnabled,
            shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
            colors =
                ListItemDefaults.segmentedColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            leadingContent = {
                Icon(
                    painter = painterResource(R.drawable.block),
                    contentDescription = null,
                )
            },
        ) {
            Text(text = stringResource(if (isBlocked) R.string.unblock_artist else R.string.block_artist))
        }
    }
}

@Composable
private fun ArtistOnlineReleaseItem(release: OnlineArtistRelease) {
    val context = LocalContext.current
    ArtistNewReleaseSection(
        title = release.title,
        thumbnailUrl = release.coverUrl,
        year = release.year,
        onClick = {
            release.url?.let { url ->
                val uri = android.net.Uri.parse(url)
                context.startActivity(
                    android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        uri,
                    ),
                )
            }
        },
    )
}

private const val ArtistHeroArtworkSizePx = 1200
private const val ArtistReleaseArtworkSizePx = 320
private val ArtistHeroArtworkSizeBuckets = listOf(ArtistHeroArtworkSizePx)
private val ArtistHeroMinHeight = 560.dp
private val ArtistHorizontalPadding = 24.dp
private val ArtistContentMaxWidth = 720.dp
private val ArtistReleaseArtworkSize = 112.dp
private const val ArtistStatSeparator = "  •  "

@Composable
private fun ArtistPrimaryActions(
    isSubscribed: Boolean,
    contentColor: Color,
    contrastingColor: Color,
    canShuffle: Boolean,
    canPlay: Boolean,
    onShuffle: () -> Unit,
    onPlay: () -> Unit,
    onToggleSubscription: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MediaDetailPrimaryActions(
        isAdded = isSubscribed,
        contentColor = contentColor,
        contrastingColor = contrastingColor,
        addContentDescription = R.string.subscribe,
        removeContentDescription = R.string.subscribed,
        onShuffle = if (canShuffle) onShuffle else null,
        onPlay = if (canPlay) onPlay else null,
        onToggleAdd = onToggleSubscription,
        additionalActions = { _ -> },
        modifier = modifier,
    )
}

@Composable
private fun ArtistNewReleaseSection(
    title: String,
    thumbnailUrl: String?,
    year: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val releaseTypeLabel = stringResource(R.string.release_type_album)
    val metadata =
        year?.let { value ->
            stringResource(R.string.release_metadata, releaseTypeLabel, value)
        } ?: releaseTypeLabel

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Card(
            onClick = onClick,
            shape = MaterialTheme.shapes.large,
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = ArtistContentMaxWidth),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (thumbnailUrl != null) {
                    AsyncImage(
                        model =
                            thumbnailUrl.resize(
                                width = ArtistReleaseArtworkSizePx,
                                height = ArtistReleaseArtworkSizePx,
                                ytimgResizePolicy = YtimgResizePolicy.PreserveOriginal,
                            ),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .size(ArtistReleaseArtworkSize)
                                .clip(RoundedCornerShape(10.dp)),
                    )
                } else {
                    Box(
                        modifier =
                            Modifier
                                .size(ArtistReleaseArtworkSize)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.album),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.latest_release).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = metadata,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
