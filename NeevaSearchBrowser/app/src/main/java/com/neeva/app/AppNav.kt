package com.neeva.app

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neeva.app.browsing.SelectedTabModel
import com.neeva.app.browsing.WebLayerModel
import com.neeva.app.card.CardViewModel
import com.neeva.app.card.CardsContainer
import com.neeva.app.history.HistoryContainer
import com.neeva.app.history.HistoryViewModel
import com.neeva.app.neeva_menu.NeevaMenuSheet
import com.neeva.app.settings.SettingsContainer
import com.neeva.app.spaces.AddToSpaceSheet
import com.neeva.app.storage.DomainViewModel
import com.neeva.app.storage.SpaceStore
import com.neeva.app.urlbar.URLBarModel
import kotlinx.coroutines.launch

class AppNavModel: ViewModel() {
    private val _state = MutableLiveData(AppNavState.HIDDEN)
    val state: LiveData<AppNavState> = _state

    lateinit var onOpenUrl: (Uri) -> Unit

    fun setContentState(state: AppNavState) {
        _state.value = state

        if(state == AppNavState.ADD_TO_SPACE) {
            viewModelScope.launch {
                SpaceStore.shared.refresh()
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppNav(
    model: AppNavModel,
    selectedTabModel: SelectedTabModel,
    historyViewModel: HistoryViewModel,
    domainViewModel: DomainViewModel,
    webLayerModel: WebLayerModel,
    urlBarModel: URLBarModel,
    cardViewModel: CardViewModel
) {
    Box {
        AddToSpaceSheet(appNavModel = model, selectedTabModel = selectedTabModel)
        NeevaMenuSheet(appNavModel = model)
        SettingsContainer(appNavModel = model)
        HistoryContainer(
            appNavModel = model,
            historyViewModel = historyViewModel,
            domainViewModel = domainViewModel)
        CardsContainer(
            appNavModel = model,
            webLayerModel = webLayerModel,
            domainViewModel = domainViewModel,
            urlBarModel = urlBarModel,
            cardViewModel = cardViewModel
        )
    }
}

enum class AppNavState {
    HIDDEN,
    SETTINGS,
    ADD_TO_SPACE,
    NEEVA_MENU,
    HISTORY,
    CARD_GRID
}