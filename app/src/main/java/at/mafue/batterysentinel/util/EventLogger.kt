package at.mafue.batterysentinel.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object EventLogger {
    private const val TAG = "EventLogger"
    private const val FILE_NAME = "events.log"
    private const val MAX_LINES = 1000
    private const val CLEANUP_THRESHOLD = 1100

    private val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.ROOT)
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.ROOT)

    /**
     * Logs an event asynchronously.
     * Format: Datum[dd-mm-yyyy] ; Zeit[hh:mm:ss] ; Aktion ; Wert
     */
    suspend fun logEvent(context: Context, action: String, value: String) {
        withContext(Dispatchers.IO) {
            val file = File(context.filesDir, FILE_NAME)
            val now = Date()
            val dateStr = dateFormat.format(now)
            val timeStr = timeFormat.format(now)
            
            // Sanitize action and value to remove semicolons and newlines which would break the CSV format
            val safeAction = action.replace(";", ",").replace("\n", " ")
            val safeValue = value.replace(";", ",").replace("\n", " ")
            
            val logLine = "$dateStr ; $timeStr ; $safeAction ; $safeValue\n"
            
            try {
                file.appendText(logLine)
                trimLogFileIfNeeded(file)
            } catch (e: IOException) {
                Log.e(TAG, "Failed to write log, recreating file", e)
                handleCorruptFile(file, now)
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error writing log", e)
                handleCorruptFile(file, now)
            }
        }
    }

    private fun trimLogFileIfNeeded(file: File) {
        if (!file.exists()) return
        
        try {
            val lines = file.readLines()
            if (lines.size > CLEANUP_THRESHOLD) {
                val retainedLines = lines.takeLast(MAX_LINES)
                file.writeText(retainedLines.joinToString("\n") + "\n")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to trim log file", e)
            handleCorruptFile(file, Date())
        }
    }

    private fun handleCorruptFile(file: File, now: Date) {
        try {
            // Delete corrupt file
            if (file.exists()) {
                file.delete()
            }
            
            val dateStr = dateFormat.format(now)
            val timeStr = timeFormat.format(now)
            val resetLine = "$dateStr ; $timeStr ; Neues Log erstellt ; Log-Datei war defekt und musste gelöscht werden\n"
            
            file.writeText(resetLine)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to recover log file", e)
        }
    }

    /**
     * Reads all log entries. Replaces unparseable lines with a sanitized error line.
     */
    suspend fun readLogs(context: Context): List<String> = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return@withContext emptyList()

        val results = mutableListOf<String>()
        val now = Date()
        val dateStr = dateFormat.format(now)
        val timeStr = timeFormat.format(now)
        
        try {
            file.forEachLine { line ->
                val parts = line.split(";")
                if (parts.size >= 4) {
                    results.add(line)
                } else if (line.isNotBlank()) {
                    // Line is malformed
                    results.add("$dateStr ; $timeStr ; Defekter Eintrag erkannt ; Log-Zeile bereinigt")
                }
            }
            results.asReversed()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read logs", e)
            listOf("$dateStr ; $timeStr ; Fehler beim Lesen ; Log-Datei konnte nicht gelesen werden")
        }
    }
}
