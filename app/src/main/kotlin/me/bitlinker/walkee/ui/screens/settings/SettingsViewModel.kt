package me.bitlinker.walkee.ui.screens.settings

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import me.bitlinker.walkee.domain.usecase.ObserveFogStyleUseCase
import me.bitlinker.walkee.domain.usecase.SetFogCellShapeUseCase
import me.bitlinker.walkee.domain.usecase.SetFogDisplayZoomUseCase
import me.bitlinker.walkee.domain.usecase.SetFogOpacityUseCase
import me.bitlinker.walkee.ui.navigation.Router
import me.bitlinker.walkee.ui.redux.ReduxViewModel
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeFogStyle: ObserveFogStyleUseCase,
    private val setFogOpacity: SetFogOpacityUseCase,
    private val setFogDisplayZoom: SetFogDisplayZoomUseCase,
    private val setFogCellShape: SetFogCellShapeUseCase,
    private val router: Router,
) : ReduxViewModel<SettingsState, SettingsAction>(SettingsState(), ::reduceSettings) {

    init {
        viewModelScope.launch {
            observeFogStyle().collect { dispatch(SettingsAction.StyleLoaded(it)) }
        }
    }

    override fun onAction(action: SettingsAction, state: SettingsState) {
        when (action) {
            is SettingsAction.FogOpacityChanged -> viewModelScope.launch { setFogOpacity(state.fogOpacity) }
            is SettingsAction.DisplayZoomChanged -> viewModelScope.launch { setFogDisplayZoom(state.displayZoom) }
            is SettingsAction.CellShapeChanged -> viewModelScope.launch { setFogCellShape(state.cellShape) }
            SettingsAction.BackClicked -> router.pop()
            is SettingsAction.StyleLoaded -> Unit
        }
    }
}
