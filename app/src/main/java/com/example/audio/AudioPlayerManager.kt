package com.example.audio

import android.media.MediaPlayer
import com.example.data.model.RecordingItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class PlaybackState(
    val activeRecordingId: String? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Int = 0,
    val totalDurationMs: Int = 0
)

object AudioPlayerManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var progressJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    fun play(recording: RecordingItem) {
        val file = File(recording.filePath)
        if (!file.exists()) return

        if (_playbackState.value.activeRecordingId == recording.id && mediaPlayer != null) {
            mediaPlayer?.start()
            _playbackState.value = _playbackState.value.copy(isPlaying = true)
            startProgressLoop()
            return
        }

        stop()

        try {
            val player = MediaPlayer().apply {
                setDataSource(recording.filePath)
                prepare()
                start()
                setOnCompletionListener {
                    stop()
                }
            }
            mediaPlayer = player

            _playbackState.value = PlaybackState(
                activeRecordingId = recording.id,
                isPlaying = true,
                currentPositionMs = 0,
                totalDurationMs = player.duration
            )

            startProgressLoop()
        } catch (_: Exception) {
            stop()
        }
    }

    fun pause() {
        try {
            mediaPlayer?.pause()
            _playbackState.value = _playbackState.value.copy(isPlaying = false)
            progressJob?.cancel()
        } catch (_: Exception) {
            stop()
        }
    }

    fun seekTo(positionMs: Int) {
        try {
            mediaPlayer?.seekTo(positionMs)
            _playbackState.value = _playbackState.value.copy(currentPositionMs = positionMs)
        } catch (_: Exception) {
            // Ignore
        }
    }

    fun stop() {
        progressJob?.cancel()
        progressJob = null
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {
            // Ignore
        }
        mediaPlayer = null
        _playbackState.value = PlaybackState()
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                delay(200)
                val player = mediaPlayer
                if (player != null && player.isPlaying) {
                    _playbackState.value = _playbackState.value.copy(
                        currentPositionMs = player.currentPosition,
                        totalDurationMs = player.duration
                    )
                }
            }
        }
    }
}
