package com.lumera.app.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumera.app.data.local.AddonDao
import com.lumera.app.data.model.WatchHistoryEntity
import com.lumera.app.data.trakt.TraktScrobbleManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val WATCHED_THRESHOLD = 0.90 // 90% — above Trakt's 80% minimum

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val dao: com.lumera.app.data.local.AddonDao,
    private val traktScrobbleManager: com.lumera.app.data.trakt.TraktScrobbleManager,
    private val subtitleRepository: com.lumera.app.data.repository.SubtitleRepository
) : ViewModel() {

    suspend fun loadExternalSubtitles(type: String, playbackId: String): List<com.lumera.app.ui.player.base.PlayerSubtitleSource> {
        return try {
            subtitleRepository.getSubtitles(type, playbackId).map { 
                com.lumera.app.ui.player.base.PlayerSubtitleSource(
                    id = it.id,
                    url = it.url,
                    label = it.addonName,
                    language = it.lang
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    fun saveProgress(
        id: String,
        type: String,
        title: String,
        poster: String?,
        position: Long,
        duration: Long?
    ) {
        viewModelScope.launch(Dispatchers.IO + NonCancellable) {
            if (id.startsWith("trailer_")) return@launch
            val safePosition = position.coerceAtLeast(0L)
            if (safePosition < 5_000L) return@launch

            val existing = dao.getHistoryItem(id)
            val safeDuration = (duration ?: existing?.duration ?: safePosition)
                .coerceAtLeast(safePosition)

            val remaining = safeDuration - safePosition
            val completionRatio = if (safeDuration > 0L) safePosition.toDouble() / safeDuration.toDouble() else 0.0

            val isCompleted = completionRatio >= WATCHED_THRESHOLD || remaining <= 30_000L

            val entry = WatchHistoryEntity(
                id = id,
                title = title,
                poster = poster ?: existing?.poster,
                background = existing?.background,
                logo = existing?.logo,
                position = safePosition,
                duration = safeDuration,
                lastWatched = System.currentTimeMillis(),
                type = type.ifBlank { "movie" },
                watched = isCompleted,
                scrobbled = existing?.scrobbled ?: traktScrobbleManager.isScrobbled(id)
            )
            dao.upsertHistory(entry)
        }
    }

    fun markCompleted(id: String) {
        viewModelScope.launch(Dispatchers.IO + NonCancellable) {
            val existing = dao.getHistoryItem(id)
            if (existing != null) {
                dao.upsertHistory(existing.copy(watched = true, lastWatched = System.currentTimeMillis()))
            }
        }
    }

    // ── Trakt Scrobbling ──

    fun scrobbleStart(id: String, type: String, positionMs: Long, durationMs: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            traktScrobbleManager.scrobbleStart(id, type, positionMs, durationMs)
        }
    }

    fun scrobblePause(id: String, type: String, positionMs: Long, durationMs: Long, force: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            traktScrobbleManager.scrobblePause(id, type, positionMs, durationMs, force = force)
        }
    }

    fun scrobbleStop(id: String, type: String, positionMs: Long, durationMs: Long) {
        viewModelScope.launch(Dispatchers.IO + NonCancellable) {
            traktScrobbleManager.scrobbleStop(id, type, positionMs, durationMs)
        }
    }

    suspend fun getResumePosition(id: String): Long {
        return withContext(Dispatchers.IO) {
            val item = dao.getHistoryItem(id)
            item?.position?.takeIf { it > 0 } ?: 0L
        }
    }
}
