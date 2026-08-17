package com.minos2020.immichswipe.core

import android.content.Context
import android.util.Log
import retrofit2.HttpException
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Logger personnalisé qui enregistre les logs localement dans des fichiers.
 * Permet de conserver les logs même après un crash pour le débogage.
 */
object AppLogger {
    private const val TAG = "AppLogger"
    private const val CURRENT_LOG_FILE = "current_logs.txt"
    private const val PREVIOUS_LOG_FILE = "previous_logs.txt"
    private const val MAX_FILE_SIZE = 1024 * 1024 // 1 MB
    
    private var logsDir: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    /**
     * Initialise le logger avec le contexte de l'application.
     */
    fun init(context: Context) {
        this.logsDir = context.applicationContext.filesDir
        writeRaw("\n\n" + "=".repeat(50) + "\n" + "   NEW SESSION START   \n" + "=".repeat(50) + "\n\n")
    }

    fun d(tag: String, message: String) {
        Log.d(tag, message)
        write("D", tag, message)
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
        write("I", tag, message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Log.w(tag, message, throwable)
        val fullMsg = buildString {
            append(message)
            if (throwable != null) {
                append(" ")
                append(extractErrorInfo(throwable))
                append("\n")
                append(throwable.stackTraceToString())
            }
        }
        write("W", tag, fullMsg)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        val fullMsg = buildString {
            append(message)
            if (throwable != null) {
                append(" ")
                append(extractErrorInfo(throwable))
                append("\n")
                append(throwable.stackTraceToString())
            }
        }
        write("E", tag, fullMsg)
    }

    /**
     * Tente d'extraire des informations supplémentaires d'une exception (ex: body d'erreur Retrofit).
     */
    private fun extractErrorInfo(throwable: Throwable): String {
        return try {
            if (throwable is HttpException) {
                val rawResponse = throwable.response()?.raw()
                val errorBody = rawResponse?.peekBody(4096)?.string()
                
                if (!errorBody.isNullOrBlank()) {
                    "[HTTP ${throwable.code()}] Body: $errorBody"
                } else {
                    "[HTTP ${throwable.code()}]"
                }
            } else {
                throwable.toString()
            }
        } catch (_: Exception) {
            throwable.toString()
        }
    }

    @Synchronized
    private fun writeRaw(text: String) {
        val dir = logsDir ?: return
        try {
            val currentFile = File(dir, CURRENT_LOG_FILE)
            FileOutputStream(currentFile, true).use {
                it.write(text.toByteArray())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write raw log", e)
        }
    }

    @Synchronized
    private fun write(level: String, tag: String, message: String) {
        val dir = logsDir ?: return
        
        try {
            val currentFile = File(dir, CURRENT_LOG_FILE)
            
            // Rotation des fichiers si le fichier actuel dépasse 1 Mo
            if (currentFile.exists() && currentFile.length() > MAX_FILE_SIZE) {
                val previousFile = File(dir, PREVIOUS_LOG_FILE)
                if (previousFile.exists()) previousFile.delete()
                currentFile.renameTo(previousFile)
            }
            
            val timestamp = dateFormat.format(Date())
            val logLine = "$timestamp $level/$tag: $message\n"
            
            FileOutputStream(currentFile, true).use {
                it.write(logLine.toByteArray())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write log to file", e)
        }
    }

    /**
     * Récupère l'intégralité des logs stockés (actuels et précédents).
     */
    fun getLogs(): String {
        val dir = logsDir ?: return "Logger not initialized"
        val currentFile = File(dir, CURRENT_LOG_FILE)
        val previousFile = File(dir, PREVIOUS_LOG_FILE)
        
        val logs = StringBuilder()
        if (previousFile.exists()) {
            logs.append("--- PREVIOUS LOGS ---\n")
            try {
                logs.append(previousFile.readText())
            } catch (e: Exception) {
                logs.append("Error reading previous logs: ${e.message}\n")
            }
            logs.append("\n\n")
        }
        
        if (currentFile.exists()) {
            logs.append("--- CURRENT LOGS ---\n")
            try {
                logs.append(currentFile.readText())
            } catch (e: Exception) {
                logs.append("Error reading current logs: ${e.message}\n")
            }
        }
        
        return if (logs.isEmpty()) "No logs available" else logs.toString()
    }

    /**
     * Supprime tous les fichiers de logs locaux.
     */
    fun clearLogs() {
        val dir = logsDir ?: return
        try {
            File(dir, CURRENT_LOG_FILE).delete()
            File(dir, PREVIOUS_LOG_FILE).delete()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear logs", e)
        }
    }
}
