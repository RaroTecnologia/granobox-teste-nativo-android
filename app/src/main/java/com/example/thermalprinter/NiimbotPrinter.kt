package com.example.thermalprinter

import android.bluetooth.BluetoothSocket
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream

/**
 * Classe para comunicação com impressoras NIIMBOT
 * Baseada no protocolo específico da NIIMBOT
 */
class NiimbotPrinter {
    
    companion object {
        private const val TAG = "NiimbotPrinter"
        
        // Comandos NIIMBOT baseados na biblioteca niimbluelib
        private const val NIIMBOT_HEADER = 0x02.toByte()  // STX
        private const val NIIMBOT_FOOTER = 0x03.toByte()  // ETX
        
        // Tipos de comando
        private const val CMD_PRINT_TEXT = 0x01.toByte()
        private const val CMD_PRINT_LABEL = 0x02.toByte()
        private const val CMD_PRINT_QR = 0x03.toByte()
        private const val CMD_PRINT_BARCODE = 0x04.toByte()
        private const val CMD_FEED = 0x05.toByte()
        private const val CMD_CUT = 0x06.toByte()
    }
    
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    
    /**
     * Conecta à impressora NIIMBOT
     */
    fun connect(socket: BluetoothSocket, callback: (Boolean, String?) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                bluetoothSocket = socket
                outputStream = socket.outputStream
                
                Log.d(TAG, "✅ Conectado à impressora NIIMBOT")
                
                // Enviar comando de inicialização
                sendInitCommand()
                
                withContext(Dispatchers.Main) {
                    callback(true, "Conectado à NIIMBOT")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao conectar: ${e.message}")
                withContext(Dispatchers.Main) {
                    callback(false, "Erro: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Desconecta da impressora
     */
    fun disconnect() {
        try {
            outputStream?.close()
            bluetoothSocket?.close()
            outputStream = null
            bluetoothSocket = null
            Log.d(TAG, "Desconectado da NIIMBOT")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao desconectar: ${e.message}")
        }
    }
    
    /**
     * Verifica se está conectado
     */
    fun isConnected(): Boolean {
        return bluetoothSocket?.isConnected == true && outputStream != null
    }
    
    /**
     * Envia comando de inicialização
     */
    private fun sendInitCommand() {
        try {
            val initCommand = byteArrayOf(
                NIIMBOT_HEADER,
                0x00.toByte(),  // Comando de inicialização
                0x00.toByte(),  // Dados
                NIIMBOT_FOOTER
            )
            outputStream?.write(initCommand)
            outputStream?.flush()
            Log.d(TAG, "Comando de inicialização enviado")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao enviar inicialização: ${e.message}")
        }
    }
    
    /**
     * Imprime texto simples
     */
    fun printText(text: String, callback: (Boolean, String?) -> Unit) {
        if (!isConnected()) {
            callback(false, "Não conectado")
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "=== IMPRIMINDO TEXTO NIIMBOT ===")
                Log.d(TAG, "Texto: $text")
                
                val command = buildTextCommand(text)
                Log.d(TAG, "Comando: ${command.contentToString()}")
                
                outputStream?.write(command)
                outputStream?.flush()
                
                delay(200)
                
                Log.d(TAG, "✅ Texto enviado com sucesso")
                withContext(Dispatchers.Main) {
                    callback(true, "Texto impresso")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao imprimir texto: ${e.message}")
                withContext(Dispatchers.Main) {
                    callback(false, "Erro: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Imprime etiqueta 60x60mm
     */
    fun printLabel60x60(title: String, subtitle: String = "", callback: (Boolean, String?) -> Unit) {
        if (!isConnected()) {
            callback(false, "Não conectado")
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "=== IMPRIMINDO ETIQUETA 60x60 NIIMBOT ===")
                Log.d(TAG, "Título: $title")
                Log.d(TAG, "Subtítulo: $subtitle")
                
                // Comando para etiqueta 60x60mm
                val command = buildLabelCommand(title, subtitle)
                Log.d(TAG, "Comando: ${command.contentToString()}")
                
                outputStream?.write(command)
                outputStream?.flush()
                
                delay(300)
                
                Log.d(TAG, "✅ Etiqueta enviada com sucesso")
                withContext(Dispatchers.Main) {
                    callback(true, "Etiqueta impressa")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao imprimir etiqueta: ${e.message}")
                withContext(Dispatchers.Main) {
                    callback(false, "Erro: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Imprime código QR
     */
    fun printQR(data: String, callback: (Boolean, String?) -> Unit) {
        if (!isConnected()) {
            callback(false, "Não conectado")
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "=== IMPRIMINDO QR NIIMBOT ===")
                Log.d(TAG, "Dados: $data")
                
                val command = buildQRCommand(data)
                Log.d(TAG, "Comando: ${command.contentToString()}")
                
                outputStream?.write(command)
                outputStream?.flush()
                
                delay(300)
                
                Log.d(TAG, "✅ QR enviado com sucesso")
                withContext(Dispatchers.Main) {
                    callback(true, "QR impresso")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao imprimir QR: ${e.message}")
                withContext(Dispatchers.Main) {
                    callback(false, "Erro: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Alimenta papel
     */
    fun feedPaper(callback: (Boolean, String?) -> Unit) {
        if (!isConnected()) {
            callback(false, "Não conectado")
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "=== ALIMENTANDO PAPEL NIIMBOT ===")
                
                val command = byteArrayOf(
                    NIIMBOT_HEADER,
                    CMD_FEED,
                    0x01.toByte(),  // 1 linha
                    NIIMBOT_FOOTER
                )
                
                outputStream?.write(command)
                outputStream?.flush()
                
                delay(200)
                
                Log.d(TAG, "✅ Papel alimentado")
                withContext(Dispatchers.Main) {
                    callback(true, "Papel alimentado")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao alimentar papel: ${e.message}")
                withContext(Dispatchers.Main) {
                    callback(false, "Erro: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Corta papel
     */
    fun cutPaper(callback: (Boolean, String?) -> Unit) {
        if (!isConnected()) {
            callback(false, "Não conectado")
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "=== CORTANDO PAPEL NIIMBOT ===")
                
                val command = byteArrayOf(
                    NIIMBOT_HEADER,
                    CMD_CUT,
                    0x00.toByte(),  // Corte automático
                    NIIMBOT_FOOTER
                )
                
                outputStream?.write(command)
                outputStream?.flush()
                
                delay(200)
                
                Log.d(TAG, "✅ Papel cortado")
                withContext(Dispatchers.Main) {
                    callback(true, "Papel cortado")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao cortar papel: ${e.message}")
                withContext(Dispatchers.Main) {
                    callback(false, "Erro: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Constrói comando de texto
     */
    private fun buildTextCommand(text: String): ByteArray {
        val textBytes = text.toByteArray()
        return byteArrayOf(
            NIIMBOT_HEADER,
            CMD_PRINT_TEXT,
            textBytes.size.toByte()
        ) + textBytes + byteArrayOf(NIIMBOT_FOOTER)
    }
    
    /**
     * Constrói comando de etiqueta
     */
    private fun buildLabelCommand(title: String, subtitle: String): ByteArray {
        val titleBytes = title.toByteArray()
        val subtitleBytes = subtitle.toByteArray()
        
        return byteArrayOf(
            NIIMBOT_HEADER,
            CMD_PRINT_LABEL,
            (titleBytes.size + subtitleBytes.size + 2).toByte(),  // Tamanho total
            titleBytes.size.toByte()
        ) + titleBytes + byteArrayOf(0x00.toByte()) + subtitleBytes + byteArrayOf(NIIMBOT_FOOTER)
    }
    
    /**
     * Constrói comando de QR
     */
    private fun buildQRCommand(data: String): ByteArray {
        val dataBytes = data.toByteArray()
        return byteArrayOf(
            NIIMBOT_HEADER,
            CMD_PRINT_QR,
            dataBytes.size.toByte()
        ) + dataBytes + byteArrayOf(NIIMBOT_FOOTER)
    }
}
