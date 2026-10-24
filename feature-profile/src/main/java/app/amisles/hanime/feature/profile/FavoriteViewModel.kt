package app.amisles.hanime.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.domain.model.FavoriteGroup
import app.amisles.hanime.domain.model.FavoriteVideo
import app.amisles.hanime.domain.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class FavoriteViewModel @Inject constructor(
    private val repository: LibraryRepository
) : ViewModel() {

    private val _favorites = MutableStateFlow<List<FavoriteVideo>>(emptyList())
    val favorites: StateFlow<List<FavoriteVideo>> = _favorites.asStateFlow()

    val groups: StateFlow<List<FavoriteGroup>> = repository.getAllFavoriteGroupsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadFavorites() {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val favorites = withContext(Dispatchers.IO) { repository.getAllFavorites() }
                _favorites.value = favorites
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("FavoriteViewModel", "Error loading favorites: " + e.message, e)
                _favorites.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** 新建分组；[name] 为空或重名时直接忽略，避免产生无法区分的条目。 */
    fun createGroup(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        if (groups.value.any { it.name.equals(trimmed, ignoreCase = true) }) return
        viewModelScope.launch(Dispatchers.IO) {
            repository.upsertFavoriteGroup(
                FavoriteGroup(
                    id = UUID.randomUUID().toString(),
                    name = trimmed,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun renameGroup(groupId: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val target = groups.value.firstOrNull { it.id == groupId } ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repository.upsertFavoriteGroup(target.copy(name = trimmed))
        }
    }

    fun deleteGroup(groupId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteFavoriteGroup(groupId)
            val favorites = repository.getAllFavorites()
            withContext(Dispatchers.Main) { _favorites.value = favorites }
        }
    }

    fun moveToGroup(videoId: String, groupId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.moveFavoriteToGroup(videoId, groupId)
            val favorites = repository.getAllFavorites()
            withContext(Dispatchers.Main) { _favorites.value = favorites }
        }
    }

    fun setTags(videoId: String, tags: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.setFavoriteTags(videoId, tags)
            val favorites = repository.getAllFavorites()
            withContext(Dispatchers.Main) { _favorites.value = favorites }
        }
    }

    fun removeFavorite(videoId: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repository.removeFavorite(videoId) }
                loadFavorites()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("FavoriteViewModel", "Error removing favorites: " + e.message, e)
            }
        }
    }

    fun removeFavorites(videoIds: List<String>) {
        if (videoIds.isEmpty()) return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repository.removeFavorites(videoIds) }
                loadFavorites()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("FavoriteViewModel", "Error removing favorites: " + e.message, e)
            }
        }
    }
}
