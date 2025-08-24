package com.example.thermalprinter

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.thermalprinter.databinding.ActivityLogBinding
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * Activity dedicada para exibir logs do sistema em tempo real
 */
class LogActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLogBinding
    private var autoScroll = true
    private val handler = Handler(Looper.getMainLooper())
    private var updateRunnable: Runnable? = null

    companion object {
        private const val TAG = "LogActivity"
        
        // Lista estática para armazenar logs
        private val logMessages = mutableListOf<String>()
        
        /**
         * Adiciona uma mensagem ao log
         */
        fun addLog(message: String) {
            val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            val logEntry = "[$timestamp] $message"
            
            synchronized(logMessages) {
                logMessages.add(logEntry)
                
                // Manter apenas os últimos 1000 logs para evitar uso excessivo de memória
                if (logMessages.size > 1000) {
                    logMessages.removeAt(0)
                }
            }
        }
        
        /**
         * Limpa todos os logs
         */
        fun clearLogs() {
            synchronized(logMessages) {
                logMessages.clear()
            }
        }
        
        /**
         * Obtém todos os logs como string
         */
        fun getAllLogs(): String {
            synchronized(logMessages) {
                return if (logMessages.isEmpty()) {
                    "📋 Nenhum log disponível ainda...\n\n"
                } else {
                    logMessages.joinToString("\n") + "\n"
                }
            }
        }
        
        /**
         * Inicia a LogActivity
         */
        fun start(context: Context) {
            val intent = Intent(context, LogActivity::class.java)
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        startLogUpdates()
    }

    private fun setupUI() {
        // Botão voltar
        binding.btnBack.setOnClickListener {
            finish()
        }

        // Botão limpar logs
        binding.btnClearLogs.setOnClickListener {
            clearLogs()
            updateLogDisplay()
            Toast.makeText(this, "🗑️ Logs limpos", Toast.LENGTH_SHORT).show()
        }

        // Botão auto scroll
        binding.btnAutoScroll.setOnClickListener {
            autoScroll = !autoScroll
            binding.btnAutoScroll.text = if (autoScroll) {
                "📜 Auto Scroll: ON"
            } else {
                "📜 Auto Scroll: OFF"
            }
            
            if (autoScroll) {
                scrollToBottom()
            }
        }

        // Botão salvar logs
        binding.btnSaveLogs.setOnClickListener {
            saveLogs()
        }

        // Atualizar display inicial
        updateLogDisplay()
    }

    private fun startLogUpdates() {
        updateRunnable = object : Runnable {
            override fun run() {
                updateLogDisplay()
                handler.postDelayed(this, 500) // Atualizar a cada 500ms
            }
        }
        handler.post(updateRunnable!!)
    }

    private fun updateLogDisplay() {
        val logs = getAllLogs()
        binding.tvLogs.text = logs
        
        if (autoScroll) {
            scrollToBottom()
        }
    }

    private fun scrollToBottom() {
        binding.scrollViewLogs.post {
            binding.scrollViewLogs.fullScroll(android.view.View.FOCUS_DOWN)
        }
    }

    private fun saveLogs() {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "thermal_printer_logs_$timestamp.txt"
            val file = File(getExternalFilesDir(null), fileName)
            
            FileWriter(file).use { writer ->
                writer.write("=== THERMAL PRINTER LOGS ===\n")
                writer.write("Gerado em: ${SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())}\n")
                writer.write("=============================\n\n")
                writer.write(getAllLogs())
            }
            
            Toast.makeText(this, "💾 Logs salvos em: ${file.absolutePath}", Toast.LENGTH_LONG).show()
            
        } catch (e: Exception) {
            Toast.makeText(this, "❌ Erro ao salvar logs: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        updateRunnable?.let { handler.removeCallbacks(it) }
    }
}
