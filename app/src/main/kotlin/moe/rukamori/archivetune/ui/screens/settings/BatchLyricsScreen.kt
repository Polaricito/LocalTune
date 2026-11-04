/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.viewmodels.BatchLyricsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchLyricsScreen(
    navController: NavController,
    viewModel: BatchLyricsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showPlaylistMenu by remember { mutableStateOf(false) }
    val progress =
        if (state.total <= 0) {
            0f
        } else {
            state.completed.toFloat() / state.total.toFloat()
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.batch_lyrics_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = stringResource(R.string.back_button_desc),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        val topPadding = innerPadding.calculateTopPadding()

        Column(
            modifier =
                Modifier
                    .padding(top = topPadding)
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                        ),
                    )
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = SettingsDimensions.ScreenBottomPadding),
        ) {
            Text(
                text = stringResource(R.string.batch_lyrics_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )

            PreferenceGroup(title = stringResource(R.string.batch_lyrics_scope)) {
                item {
                    Box {
                        PreferenceEntry(
                            title = {
                                Text(
                                    text =
                                        state.selectedPlaylistName
                                            ?: stringResource(R.string.batch_lyrics_all_songs),
                                )
                            },
                            description = stringResource(R.string.batch_lyrics_select_playlist),
                            icon = { Icon(painterResource(R.drawable.queue_music), null) },
                            onClick = {
                                if (!state.isRunning) showPlaylistMenu = true
                            },
                            isEnabled = !state.isRunning,
                        )

                        DropdownMenu(
                            expanded = showPlaylistMenu,
                            onDismissRequest = { showPlaylistMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.batch_lyrics_all_songs)) },
                                onClick = {
                                    viewModel.selectPlaylist(null)
                                    showPlaylistMenu = false
                                },
                            )
                            state.playlists.forEach { playlist ->
                                DropdownMenuItem(
                                    text = { Text(playlist.name) },
                                    onClick = {
                                        viewModel.selectPlaylist(playlist.id)
                                        showPlaylistMenu = false
                                    },
                                )
                            }
                        }
                    }
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.batch_lyrics_overwrite)) },
                        description = stringResource(R.string.batch_lyrics_overwrite_description),
                        icon = { Icon(painterResource(R.drawable.edit), null) },
                        checked = state.overwriteExisting,
                        onCheckedChange = viewModel::setOverwriteExisting,
                        isEnabled = !state.isRunning,
                    )
                }

                item {
                    PreferenceEntry(
                        title = {
                            Text(
                                text =
                                    stringResource(
                                        R.string.batch_lyrics_song_count,
                                        state.songCount,
                                    ),
                            )
                        },
                        description = stringResource(R.string.batch_lyrics_model_notice),
                        icon = { Icon(painterResource(R.drawable.music_note), null) },
                    )
                }
            }

            if (state.isRunning || state.completed > 0) {
                PreferenceGroup(title = stringResource(R.string.batch_lyrics_progress_title)) {
                    item {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            LinearProgressIndicator(
                                progress = progress.coerceIn(0f, 1f),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                text =
                                    stringResource(
                                        R.string.batch_lyrics_progress,
                                        state.completed,
                                        state.total,
                                    ),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            state.currentTitle?.let { currentTitle ->
                                Text(
                                    text = stringResource(R.string.batch_lyrics_current, currentTitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (state.completed > 0) {
                                Text(
                                    text =
                                        stringResource(
                                            R.string.batch_lyrics_summary,
                                            state.succeeded,
                                            state.skipped,
                                            state.failed,
                                        ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.isRunning) {
                    Button(
                        onClick = viewModel::cancel,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.batch_lyrics_cancel))
                    }
                } else {
                    Button(
                        onClick = viewModel::start,
                        enabled = state.songCount > 0,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.batch_lyrics_start))
                    }
                }
            }

            if (state.finished && !state.isRunning) {
                Text(
                    text = stringResource(R.string.batch_lyrics_finished),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        }
    }
}
