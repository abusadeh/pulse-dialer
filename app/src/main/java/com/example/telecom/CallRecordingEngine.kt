package com.example.telecom

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.data.model.RecordingItem
import com.example.data.repository.RecordingsRepository
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RecordingState(
    val isRecording: Boolean = false,
    val durationSeconds: Long = 0,
    val callerNumber: String? = null,
    val callerName: String? = null,
    val filePath: String? = null,
    val errorMessage: String? = null,
    val isLimitationNoticeVisible: Boolean = false
)

sealed class RecordingStartResult {
    data object Success : RecordingStartResult()
    data object PermissionRequired : RecordingStartResult()
    data class Error(val message: String) : RecordingStartResult()
}

object CallRecordingEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var timerJob: Job? = null

    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private var recordingStartTime = 0L
    private var activeCallerNumber: String? = null
    private var activeCallerName: String? = null

    private val _recordingState = MutableStateFlow(RecordingState())
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    fun isRecordingActive(): Boolean = _recordingState.value.isRecording

    fun hasRecordPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun startRecording(
        context: Context,
        callerNumber: String?,
        callerName: String?,
        recordingsRepository: RecordingsRepository,
        audioFormat: String = "M4A"
    ): RecordingStartResult {
        if (!hasRecordPermission(context)) {
            return RecordingStartResult.PermissionRequired
        }

        if (isRecordingActive()) {
            return RecordingStartResult.Success
        }

        try {
            val safeNumber = (callerNumber ?: "unknown").filter { it.isLetterOrDigit() || it == '+' }
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val ext = if (audioFormat.equals("3GP", ignoreCase = true)) "3gp" else "m4a"
            val fileId = "Call_${timeStamp}_$safeNumber"
            val file = File(recordingsRepository.getRecordingsDirectory(), "$fileId.$ext")

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            // Attempt VOICE_COMMUNICATION first, fallback to MIC
            val audioSource = try {
                recorder.setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                MediaRecorder.AudioSource.VOICE_COMMUNICATION
            } catch (_: Exception) {
                recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                MediaRecorder.AudioSource.MIC
            }

            if (ext == "3gp") {
                recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            } else {
                recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                recorder.setAudioEncodingBitRate(128000)
                recorder.setAudioSamplingRate(44100)
            }

            recorder.setOutputFile(file.absolutePath)
            recorder.prepare()
            recorder.start()

            mediaRecorder = recorder
            currentOutputFile = file
            recordingStartTime = System.currentTimeMillis()
            activeCallerNumber = callerNumber
            activeCallerName = callerName

            _recordingState.value = RecordingState(
                isRecording = true,
                durationSeconds = 0,
                callerNumber = callerNumber,
                callerName = callerName,
                filePath = file.absolutePath,
                errorMessage = null
            )

            startTimer()
            return RecordingStartResult.Success
        } catch (e: Exception) {
            cleanupRecorder()
            currentOutputFile?.let { if (it.exists()) it.delete() }
            currentOutputFile = null
            _recordingState.value = RecordingState(
                isRecording = false,
                errorMessage = "Recording unavailable: Android/HyperOS security restricts direct cellular audio access without hardware authorization."
            )
            return RecordingStartResult.Error(e.message ?: "Failed to start recording")
        }
    }

    fun stopRecording(recordingsRepository: RecordingsRepository): RecordingItem? {
        if (!isRecordingActive()) return null

        stopTimer()
        val durationSec = _recordingState.value.durationSeconds
        val file = currentOutputFile
        val num = activeCallerNumber
        val name = activeCallerName

        try {
            mediaRecorder?.stop()
        } catch (_: Exception) {
            // Can happen if stopped immediately after start
        } finally {
            cleanupRecorder()
        }

        _recordingState.value = RecordingState(isRecording = false)

        if (file != null && file.exists() && file.length() > 500) {
            val fileId = file.nameWithoutExtension
            scope.launch {
                recordingsRepository.saveRecordingMetadata(
                    fileId = fileId,
                    callerNumber = num,
                    callerName = name,
                    dateMillis = recordingStartTime,
                    durationSeconds = durationSec.coerceAtLeast(1L)
                )
            }
            return RecordingItem(
                id = fileId,
                filePath = file.absolutePath,
                fileName = file.name,
                callerNumber = num,
                callerName = name,
                dateMillis = recordingStartTime,
                durationSeconds = durationSec,
                fileSizeBytes = file.length()
            )
        } else {
            // Delete empty/corrupt file
            file?.delete()
            return null
        }
    }

    fun dismissError() {
        _recordingState.value = _recordingState.value.copy(errorMessage = null)
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            var secs = 0L
            while (isActive) {
                delay(1000)
                secs++
                _recordingState.value = _recordingState.value.copy(durationSeconds = secs)
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    private fun cleanupRecorder() {
        try {
            mediaRecorder?.reset()
            mediaRecorder?.release()
        } catch (_: Exception) {
            // Ignore
        }
        mediaRecorder = null
    }
}
