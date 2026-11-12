package dev.suks.midas.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.suks.midas.AppContainer
import dev.suks.midas.MidasApplication
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope

/** Builds a ViewModel with the app's dependency container, no DI framework needed. */
@Composable
inline fun <reified VM : ViewModel> midasViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val container = (LocalContext.current.applicationContext as MidasApplication).container
    // Keyed by workspace so a screen never holds on to a ViewModel built for another workspace.
    return viewModel(key = "${container.workspaceId}:${key ?: VM::class.java.name}") { create(container) }
}

fun <T> ViewModel.stateOf(flow: Flow<T>, initial: T): StateFlow<T> =
    flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)
