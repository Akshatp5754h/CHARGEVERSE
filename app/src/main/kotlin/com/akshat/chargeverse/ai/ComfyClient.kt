package com.akshat.chargeverse.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal zero-cost client for a **local** ComfyUI server (default
 * http://127.0.0.1:8188). Uses HttpURLConnection only — no third-party HTTP
 * library. The app is fully functional without any AI integration.
 */
class ComfyClient {

    data class Check(val ok: Boolean, val detail: String)

    /** GET /system_stats — verifies the server is reachable. */
    suspend fun ping(baseUrl: String): Check = withContext(Dispatchers.IO) {
        runCatching {
            val conn = open("$baseUrl/system_stats")
            val code = conn.responseCode
            conn.disconnect()
            if (code in 200..299) Check(true, "ComfyUI reachable (HTTP $code)")
            else Check(false, "ComfyUI responded HTTP $code")
        }.getOrElse { Check(false, "Cannot reach ComfyUI: ${it.message}") }
    }

    /**
     * Submits a text-to-image prompt in ComfyUI API format and polls until a
     * filename appears in history. Returns the /view query for the output, or
     * null on failure. Uses a generic SD/SDXL checkpoint graph the user can
     * adapt to their installed models.
     */
    suspend fun txt2img(
        baseUrl: String,
        prompt: String,
        checkpoint: String,
        width: Int = 832,
        height: Int = 1216,
        steps: Int = 24,
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val workflow = buildTxt2ImgWorkflow(prompt, checkpoint, width, height, steps)
            val submitConn = open("$baseUrl/prompt", "POST")
            submitConn.outputStream.use { it.write(workflow.toString().toByteArray()) }
            val body = submitConn.inputStream.bufferedReader().readText()
            val promptId = JSONObject(body).optString("prompt_id", "")
            if (promptId.isEmpty()) throw IllegalStateException("no prompt_id in response")
            submitConn.disconnect()

            val deadline = System.currentTimeMillis() + TIMEOUT_MS
            while (System.currentTimeMillis() < deadline) {
                val h = open("$baseUrl/history/$promptId")
                val historyBody = h.inputStream.bufferedReader().readText()
                h.disconnect()
                val history = JSONObject(historyBody)
                if (history.has(promptId)) {
                    val outputs = history.getJSONObject(promptId).getJSONObject("outputs")
                    val keys = outputs.keys()
                    while (keys.hasNext()) {
                        val node = outputs.getJSONObject(keys.next())
                        val images = node.optJSONArray("images") ?: continue
                        val img = images.getJSONObject(0)
                        val filename = img.getString("filename")
                        val subfolder = img.optString("subfolder", "")
                        val type = img.optString("type", "output")
                        return@withContext "$baseUrl/view?filename=$filename&subfolder=$subfolder&type=$type"
                    }
                }
                Thread.sleep(POLL_MS)
            }
            null
        }.getOrNull()
    }

    private fun buildTxt2ImgWorkflow(
        prompt: String,
        checkpoint: String,
        width: Int,
        height: Int,
        steps: Int,
    ): JSONObject {
        val node = { classType: String, inputs: JSONObject ->
            JSONObject().put("class_type", classType).put("inputs", inputs)
        }
        val graph = JSONObject()
            .put("4", node("CheckpointLoaderSimple", JSONObject().put("ckpt_name", checkpoint)))
            .put(
                "5", node(
                    "EmptyLatentImage",
                    JSONObject().put("width", width).put("height", height).put("batch_size", 1),
                )
            )
            .put(
                "6", node(
                    "CLIPTextEncode",
                    JSONObject().put("text", prompt)
                        .put("clip", JSONArray().put(listOf("4", 1))),
                )
            )
            .put(
                "7", node(
                    "CLIPTextEncode",
                    JSONObject().put("text", "low quality, blurry")
                        .put("clip", JSONArray().put(listOf("4", 1))),
                )
            )
            .put(
                "3", node(
                    "KSampler",
                    JSONObject()
                        .put("seed", System.currentTimeMillis())
                        .put("steps", steps)
                        .put("cfg", 6)
                        .put("sampler_name", "euler_ancestral")
                        .put("scheduler", "normal")
                        .put("denoise", 1)
                        .put("model", JSONArray().put(listOf("4", 0)))
                        .put("positive", JSONArray().put(listOf("6", 0)))
                        .put("negative", JSONArray().put(listOf("7", 0)))
                        .put("latent_image", JSONArray().put(listOf("5", 0))),
                )
            )
            .put(
                "8", node(
                    "VAEDecode",
                    JSONObject()
                        .put("samples", JSONArray().put(listOf("3", 0)))
                        .put("vae", JSONArray().put(listOf("4", 2))),
                )
            )
            .put(
                "9", node(
                    "SaveImage",
                    JSONObject().put("images", JSONArray().put(listOf("8", 0))).put("filename_prefix", "ChargeverseAI"),
                )
            )
        return JSONObject().put("prompt", graph)
    }

    private fun open(url: String, method: String = "GET"): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 3000
        conn.readTimeout = if (method == "POST") 10000 else 3000
        conn.requestMethod = method
        if (method == "POST") {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
        }
        return conn
    }

    private companion object {
        const val POLL_MS = 1000L
        const val TIMEOUT_MS = 300_000L
    }
}
