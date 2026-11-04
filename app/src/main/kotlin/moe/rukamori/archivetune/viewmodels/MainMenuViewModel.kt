/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class MainMenuDestination(val route: String) {
    History("history"),
    Settings("settings"),
}

@HiltViewModel
class MainMenuViewModel
    @Inject
    constructor() : ViewModel() {
        private val _expanded = MutableStateFlow(false)
        val expanded = _expanded.asStateFlow()

        private val navigation = Channel<MainMenuDestination>(Channel.BUFFERED)
        val destinations = navigation.receiveAsFlow()

        fun toggle() {
            _expanded.value = !_expanded.value
        }

        fun dismiss() {
            _expanded.value = false
        }

        fun select(destination: MainMenuDestination) {
            if (!_expanded.value) return
            dismiss()
            viewModelScope.launch {
                navigation.send(destination)
            }
        }
    }
