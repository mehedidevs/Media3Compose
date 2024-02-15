package com.mehedi.media3withcompose.ui.audio

import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.saveable
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.mehedi.media3withcompose.data.model.Audio
import com.mehedi.media3withcompose.data.repository.AudioRepository
import com.mehedi.media3withcompose.player.service.JetAudioServiceHandler
import com.mehedi.media3withcompose.player.service.JetAudioState
import com.mehedi.media3withcompose.player.service.PlayerEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private val audioDummy = Audio("".toUri(), "", 0L, "", "", 0, "")


@HiltViewModel
class AudioViewModel @Inject constructor(
    private val audioServiceHandler: JetAudioServiceHandler,
    private val repository: AudioRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {


    var duration by savedStateHandle.saveable { mutableLongStateOf(0L) }
    var progcess by savedStateHandle.saveable { mutableStateOf(0f) }
    var progcessString by savedStateHandle.saveable { mutableStateOf("00:00") }
    var isplaying by savedStateHandle.saveable { mutableStateOf(false) }
    var currentSelectedAudio by savedStateHandle.saveable { mutableStateOf(audioDummy) }
    var audioList by savedStateHandle.saveable { mutableStateOf(listOf<Audio>()) }

    private val _uiState: MutableStateFlow<UIState> = MutableStateFlow(UIState.Initial)

    val uiState: StateFlow<UIState>
        get() = _uiState.asStateFlow()

    init {
        loadAudioData()
    }

    init {
        viewModelScope.launch {

            audioServiceHandler.audioState.collectLatest { mediaState ->

                when (mediaState) {
                    is JetAudioState.Buffering -> calculateProgress(mediaState.progress)
                    is JetAudioState.CurrentPlaying -> {
                        currentSelectedAudio = audioList[mediaState.mediaItemIndex]
                    }

                    JetAudioState.Initial -> _uiState.value = UIState.Initial
                    is JetAudioState.Playing -> isplaying = mediaState.isPlaying
                    is JetAudioState.Progress -> calculateProgress(mediaState.progress)
                    is JetAudioState.Ready -> {
                        duration = mediaState.duration
                        _uiState.value = UIState.Ready
                    }
                }

            }

        }

    }

    private fun loadAudioData() {


        viewModelScope.launch {
            val audio = repository.getAudioData()
            audioList = audio
            setMediaItems()
        }
    }


    fun onUiEvents(uiEvents: UIEvents) {

        viewModelScope.launch {

            when (uiEvents) {
                UIEvents.Backward -> audioServiceHandler.onPlayerEvents(PlayerEvent.Backward)
                UIEvents.Forward -> audioServiceHandler.onPlayerEvents(PlayerEvent.Forward)
                UIEvents.PlayPause -> audioServiceHandler.onPlayerEvents(PlayerEvent.PlayPause)
                is UIEvents.SeekTo -> audioServiceHandler.onPlayerEvents(
                    PlayerEvent.SeekTo,
                    seekPosition = ((duration * uiEvents.position) / 100f).toLong()
                )

                UIEvents.SeekToNext -> audioServiceHandler.onPlayerEvents(PlayerEvent.SeekToNext)
                is UIEvents.SelectedAudioChange -> {
                    audioServiceHandler.onPlayerEvents(
                        PlayerEvent.SelectedAudioChange,
                        selectedAudioIndex = uiEvents.index
                    )

                }

                is UIEvents.UpdateProgress -> {
                    audioServiceHandler.onPlayerEvents(
                        PlayerEvent.UpdateProgress(uiEvents.newProgress)
                    )

                }
            }


        }

    }

    private fun setMediaItems() {

        audioList.map { audio: Audio ->
            MediaItem.Builder()
                .setUri(audio.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setAlbumArtist(audio.artist)
                        .setDisplayTitle(audio.title)
                        .setSubtitle(audio.displayName)
                        .build()

                ).build()

        }.also {
            audioServiceHandler.setMediaItemList(it)
        }

    }


    private fun calculateProgress(currentProgress: Long) {
        progcess = if (currentProgress > 0) {
            ((currentProgress.toFloat()) / duration.toFloat()) * 100f
        } else {
            0f
        }

        progcessString = formatDuration(currentProgress)
    }


    private fun formatDuration(duration: Long): String {
        val minute = TimeUnit.MINUTES.convert(duration, TimeUnit.MILLISECONDS)
        val seconds = (minute) - minute * TimeUnit.SECONDS.convert(1, TimeUnit.MINUTES)
        return String().format("%02d:%02d", minute, seconds)

    }


}

sealed class UIEvents {
     object PlayPause : UIEvents()
    data class SelectedAudioChange(val index: Int) : UIEvents()
    data class SeekTo(val position: Int) : UIEvents()
     object SeekToNext : UIEvents()
     object Backward : UIEvents()
     object Forward : UIEvents()
    data class UpdateProgress(val newProgress: Float) : UIEvents()
}

sealed class UIState {

     object Initial : UIState()
     object Ready : UIState()

}