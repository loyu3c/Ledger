package com.loyu.ledger.data.remote

import com.loyu.ledger.data.local.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class VoiceTransactionResult(
    val type: TransactionType?,
    val amount: Long?,
    val categoryName: String?,
    val merchant: String,
    val note: String,
)

/**
 * Calls Groq's OpenAI-compatible chat completions API to turn a spoken sentence into
 * structured transaction fields. Returns null on any failure (no key, no network, bad
 * response) so callers can silently fall back to dumping the raw text into a note field.
 */
class GroqClient(private val apiKey: String) {

    suspend fun parseTransaction(spokenText: String, categoryNames: List<String>): VoiceTransactionResult? {
        if (apiKey.isBlank() || spokenText.isBlank()) return null
        return withContext(Dispatchers.IO) {
            try {
                val requestBody = JSONObject().apply {
                    put("model", "llama-3.1-8b-instant")
                    put("temperature", 0.2)
                    put("response_format", JSONObject().put("type", "json_object"))
                    put(
                        "messages",
                        JSONArray().apply {
                            put(JSONObject().put("role", "system").put("content", buildSystemPrompt(categoryNames)))
                            put(JSONObject().put("role", "user").put("content", spokenText))
                        },
                    )
                }

                val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Authorization", "Bearer $apiKey")
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000

                OutputStreamWriter(connection.outputStream).use { it.write(requestBody.toString()) }

                if (connection.responseCode !in 200..299) return@withContext null

                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val content = JSONObject(responseText)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")

                val parsed = JSONObject(content)
                VoiceTransactionResult(
                    type = when (parsed.optString("type").uppercase()) {
                        "INCOME" -> TransactionType.INCOME
                        "EXPENSE" -> TransactionType.EXPENSE
                        else -> null
                    },
                    amount = parsed.optLong("amount", -1).takeIf { it > 0 },
                    categoryName = parsed.optString("category").takeIf { it.isNotBlank() },
                    merchant = parsed.optString("merchant"),
                    note = parsed.optString("note"),
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun buildSystemPrompt(categoryNames: List<String>): String = """
        你是一個記帳助手。使用者會用語音描述一筆消費或收入，你要把內容整理成 JSON。
        可用的分類只能從這個清單裡選一個，選不到就留空字串：${categoryNames.joinToString("、")}
        請只回傳一個 JSON 物件，不要有任何其他文字，格式如下：
        {"type": "EXPENSE 或 INCOME", "amount": 數字, "category": "分類名稱或空字串", "merchant": "商家或對象，沒有就空字串", "note": "備註，沒有就空字串"}
    """.trimIndent()

    companion object {
        private const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
    }
}
