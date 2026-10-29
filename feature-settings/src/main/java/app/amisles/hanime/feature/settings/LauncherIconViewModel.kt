package app.amisles.hanime.feature.settings

import androidx.lifecycle.ViewModel
import app.amisles.hanime.domain.model.LauncherIcon
import app.amisles.hanime.domain.repository.AppIconRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class LauncherIconViewModel @Inject constructor(
    private val icons: AppIconRepository
) : ViewModel() {

    private val _selected = MutableStateFlow(icons.current())
    val selected: StateFlow<LauncherIcon> = _selected.asStateFlow()

    fun select(icon: LauncherIcon): Boolean {
        val applied = icons.select(icon)
        if (applied) _selected.value = icon
        return applied
    }
}
