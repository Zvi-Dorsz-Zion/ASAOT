package com.transport.beithashem

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * All calls to the call2all (Yemot Hamashiach) API used by this app.
 * This mirrors exactly the logic of the original desktop Python application.
 */
object ApiClient {

    private const val TOKEN =
        "WU1BUElL.apik_wGoXJpTzFv02cXH-Zu7-ig.pJ9nt0J1WfkKP_TZxvefI7JWIGQacd8xFGthpvxRg_w"

    private const val BASE = "https://www.call2all.co.il/ym/api"

    private val TEMPLATE_IDS = mapOf(2 to 366999, 3 to 367000, 4 to 367001)

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun get(url: String, params: Map<String, String>): Boolean {
        val builder = url.toHttpUrl().newBuilder()
        params.forEach { (k, v) -> builder.addQueryParameter(k, v) }
        val request = Request.Builder().url(builder.build()).get().build()
        client.newCall(request).execute().use { response ->
            return response.isSuccessful
        }
    }

    private fun getBytes(url: String, params: Map<String, String>): ByteArray? {
        val builder = url.toHttpUrl().newBuilder()
        params.forEach { (k, v) -> builder.addQueryParameter(k, v) }
        val request = Request.Builder().url(builder.build()).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.bytes()
        }
    }

    /**
     * Result of the full "open extension" flow.
     */
    sealed class OpenResult {
        object Success : OpenResult()
        data class Failure(val stage: String) : OpenResult()
    }

    /**
     * Runs the full sequence exactly like the Python app:
     * 1) Clear template entries (only for extensions 2,3,4)
     * 2) Update extension (places / closing date-time)
     * 3) Delete leftover files
     */
    suspend fun openExtension(
        extension: Int,
        placesVal: String,
        formattedDateTime: String?
    ): OpenResult = withContext(Dispatchers.IO) {
        try {
            // Step 1: clear template (only for extensions that have a template id)
            TEMPLATE_IDS[extension]?.let { templateId ->
                val ok = get(
                    "$BASE/ClearTemplateEntries",
                    mapOf("token" to TOKEN, "templateId" to templateId.toString())
                )
                if (!ok) return@withContext OpenResult.Failure("שגיאה בניקוי התבנית")
            }

            // Step 2: update extension
            val updateParams = mutableMapOf(
                "token" to TOKEN,
                "path" to "ivr2:$extension"
            )
            if (placesVal.isNotBlank()) {
                updateParams["booking_max"] = "10$placesVal"
            }
            if (formattedDateTime != null) {
                updateParams["enter_foldar_end_time"] = formattedDateTime
            }
            val updateOk = get("$BASE/UpdateExtension", updateParams)
            if (!updateOk) return@withContext OpenResult.Failure("שגיאה בפתיחת השלוחה")

            // Step 3: delete leftover files
            val fileTypes = listOf(
                "ApprovalOk",
                "Record",
                "ApprovalNumberNow.ini",
                "LogRecordingAndEnteringData.ymgr",
                "ApprovalAll.ymgr"
            )
            val deleteParams = mutableMapOf(
                "token" to TOKEN,
                "action" to "delete"
            )
            fileTypes.forEachIndexed { i, fileType ->
                deleteParams["what$i"] = "ivr2:$extension/$fileType"
            }
            val deleteOk = get("$BASE/FileAction", deleteParams)
            if (!deleteOk) return@withContext OpenResult.Failure("שגיאה במחיקת הקבצים")

            OpenResult.Success
        } catch (e: Exception) {
            OpenResult.Failure(e.message ?: "שגיאה לא ידועה")
        }
    }

    /**
     * Downloads the report (CSV bytes) for a given extension.
     */
    suspend fun downloadReport(extension: Int): ByteArray? = withContext(Dispatchers.IO) {
        try {
            getBytes(
                "$BASE/RenderYMGRFile",
                mapOf(
                    "token" to TOKEN,
                    "wath" to "ivr2:/$extension/ApprovalAll.ymgr",
                    "convertType" to "csv",
                    "notLoadLang" to "0"
                )
            )
        } catch (e: Exception) {
            null
        }
    }
}
