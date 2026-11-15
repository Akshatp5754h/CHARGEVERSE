package com.akshat.chargeverse.ui.ai

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akshat.chargeverse.core.di.ServiceLocator
import com.akshat.chargeverse.data.db.AiHistoryEntity
import com.akshat.chargeverse.data.db.MediaItemEntity
import com.akshat.chargeverse.data.db.MediaType
import com.akshat.chargeverse.data.prefs.CvSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

data class AiUiState(
    val settings: CvSettings = CvSettings(),
    val history: List<AiHistoryEntity> = emptyList(),
    val prompt: String = "",
    val checkpoint: String = "",
    val generating: Boolean = false,
    val statusMessage: String? = null,
)

/**
 * AI studio — optional integration with the user's own **local** ComfyUI
 * server. Everything here is no-op unless the user enables AI in Settings.
 */
class AiViewModel : ViewModel() {

    private val db = ServiceLocator.db
    private val settingsRepo = ServiceLocator.settings
    private val comfy = ServiceLocator.comfyClient
    private val resolver = com.akshat.chargeverse.core.media.MediaResolver(ServiceLocator.app)

    private val prompt = MutableStateFlow("")
    private val checkpoint = MutableStateFlow("")
    private val generating = MutableStateFlow(false)
    private val status = MutableStateFlow<String?>(null)

    val ui: StateFlow<AiUiState> = combine(
        settingsRepo.settings,
        db.aiHistoryDao().observeRecent(50),
        prompt,
        combine(checkpoint, generating, status) { c, g, s -> Triple(c, g, s) },
    ) { s, history, promptText, extras ->
        AiUiState(
            settings = s,
            history = history,
            prompt = promptText,
            checkpoint = checkpoint.value.ifEmpty { "sd_xl_base_1.0.safetensors" },
            generating = extras.second,
            statusMessage = extras.third,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AiUiState())

    fun setPrompt(p: String) {
        prompt.value = p
    }

    fun setCheckpoint(c: String) {
        checkpoint.value = c
    }

    fun dismissStatus() {
        status.value = null
    }

    /** Runs the text-to-image workflow against the configured local server. */
    fun generate() = viewModelScope.launch {
        val s = settingsRepo.current()
        if (!s.aiEnabled) {
            status.value = "AI studio is disabled — enable it in Settings first."
            return@launch
        }
        val text = prompt.value.trim()
        if (text.isEmpty()) {
            status.value = "Write a prompt first."
            return@launch
        }
        generating.value = true
        status.value = "Contacting ${s.aiServerUrl} …"
        val entryId = db.aiHistoryDao().insert(
            AiHistoryEntity(
                workflow = "TXT2IMG",
                prompt = text,
                serverUrl = s.aiServerUrl,
                outputUri = null,
                createdAtMs = System.currentTimeMillis(),
                status = "RUNNING",
            )
        )
        val result = comfy.txt2img(s.aiServerUrl, text, checkpoint.value)
        if (result != null) {
            status.value = "Downloading image …"
            val saved = withContext(Dispatchers.IO) {
                runCatching { downloadToPrivateStorage(result) }.getOrNull()
            }
            if (saved != null) {
                db.mediaItemDao().insert(
                    MediaItemEntity(
                        type = MediaType.IMAGE,
                        uri = Uri.fromFile(saved).toString(),
                        title = "ai_${text.take(16)}",
                        category = com.akshat.chargeverse.data.db.AnimationCategory.ABSTRACT,
                        addedAtMs = System.currentTimeMillis(),
                        sizeBytes = saved.length(),
                        isBuiltIn = false,
                        durationMs = 0,
                        widthPx = 0,
                        heightPx = 0,
                    )
                )
                db.aiHistoryDao().updateResult(entryId, "OK", Uri.fromFile(saved).toString(), null)
                status.value = "Generated and added to your animation library."
            } else {
                db.aiHistoryDao().updateResult(entryId, "ERROR", null, "download failed")
                status.value = "Generation finished but download failed."
            }
        } else {
            db.aiHistoryDao().updateResult(entryId, "ERROR", null, "no output")
            status.value = "No output. Is ComfyUI running at ${s.aiServerUrl}?"
        }
        generating.value = false
    }

    private fun downloadToPrivateStorage(viewUrl: String): File {
        val dir = resolver.mediaDir()
        val out = File(dir, "ai_${System.currentTimeMillis()}.png")
        val conn = URL(viewUrl).openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 30000
        conn.inputStream.use { input -> out.outputStream().use { input.copyTo(it) } }
        conn.disconnect()
        return out
    }

    fun deleteHistory(entry: AiHistoryEntity) = viewModelScope.launch {
        db.aiHistoryDao().delete(entry.id)
    }
}
