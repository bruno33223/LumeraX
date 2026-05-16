package com.lumera.app.data.model

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lumera.app.data.model.stremio.Stream
import com.lumera.app.data.model.stremio.MetaVideo
import com.lumera.app.domain.AddonSubtitle
import com.lumera.app.ui.player.base.PlayerSourceOption

data class PlayerSubtitlePayload(
    val id: String,
    val url: String,
    val name: String,
    val language: String?
)

data class PendingSourceSelection(
    val playbackId: String,
    val launchedStream: Stream,
    val candidateStreams: List<Stream>
)

data class PendingEpisodeSwitch(
    val playbackId: String,
    val playbackTitle: String,
    val streams: List<Stream>?,
    val addonSubs: List<AddonSubtitle>,
    val playerCurrentSourceUrl: String?
)

@Stable
class PlayerState {
    var selectedPlayerSubtitles by mutableStateOf<List<PlayerSubtitlePayload>>(emptyList())
    var selectedPlayerSources by mutableStateOf<List<PlayerSourceOption>>(emptyList())
    var pendingSourceSelection by mutableStateOf<PendingSourceSelection?>(null)
    var showPlayerChoiceDialog by mutableStateOf(false)
    var currentEpisodeList by mutableStateOf<List<MetaVideo>>(emptyList())
    var currentStream by mutableStateOf<Stream?>(null)
    var pendingEpisodeSwitch by mutableStateOf<PendingEpisodeSwitch?>(null)
    var isEpisodeSwitchLoading by mutableStateOf(false)
}
