package com.example.thermalprinter

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.io.IOException
import java.io.OutputStream
import java.util.*

class BluetoothManager(private val context: Context) {
    
    companion object {
        private const val TAG = "BluetoothManager"
        private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
    
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var isConnected = false
    
    // Callbacks
    var onConnectionStateChanged: ((Boolean) -> Unit)? = null
    var onPrintResult: ((Boolean, String?) -> Unit)? = null
    
    fun initialize(adapter: BluetoothAdapter) {
        bluetoothAdapter = adapter
        Log.d(TAG, "BluetoothManager inicializado")
    }
    
    fun isBluetoothSupported(): Boolean {
        return bluetoothAdapter != null
    }
    
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }
    
    fun connectToDevice(device: BluetoothDevice, callback: (Boolean, String?) -> Unit) {
        if (!isBluetoothEnabled()) {
            callback(false, "Bluetooth não está habilitado")
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Conectando ao dispositivo: ${device.name}")
                
                // Desconectar se já estiver conectado
                disconnect()
                
                // Criar socket Bluetooth
                bluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                
                // Definir timeout de conexão
                bluetoothSocket?.connect()
                
                // Aguardar um pouco para estabilizar a conexão
                delay(1000)
                
                // Verificar se o socket está realmente conectado
                if (bluetoothSocket?.isConnected == true) {
                    // Obter output stream
                    outputStream = bluetoothSocket?.outputStream
                    
                    if (outputStream != null) {
                        isConnected = true
                        Log.d(TAG, "Conectado com sucesso ao dispositivo: ${device.name}")
                        
                        withContext(Dispatchers.Main) {
                            onConnectionStateChanged?.invoke(true)
                            callback(true, null)
                        }
                    } else {
                        throw IOException("Não foi possível obter o output stream")
                    }
                } else {
                    throw IOException("Socket não está conectado")
                }
                
            } catch (e: IOException) {
                Log.e(TAG, "Erro na conexão: ${e.message}")
                disconnect()
                
                withContext(Dispatchers.Main) {
                    callback(false, "Erro na conexão: ${e.message}")
                }
            }
        }
    }
    
    fun connectToDevice(address: String, callback: (Boolean, String?) -> Unit) {
        if (!isBluetoothEnabled()) {
            callback(false, "Bluetooth não está habilitado")
            return
        }
        
        bluetoothAdapter?.let { adapter ->
            val device = adapter.getRemoteDevice(address)
            connectToDevice(device, callback)
        } ?: run {
            callback(false, "BluetoothAdapter não disponível")
        }
    }
    
    fun disconnect() {
        try {
            outputStream?.close()
            bluetoothSocket?.close()
            isConnected = false
            Log.d(TAG, "Desconectado do dispositivo")
            
            onConnectionStateChanged?.invoke(false)
            
        } catch (e: IOException) {
            Log.e(TAG, "Erro ao desconectar: ${e.message}")
        }
    }
    
    fun isConnected(): Boolean {
        return try {
            isConnected && 
            bluetoothSocket?.isConnected == true && 
            outputStream != null &&
            bluetoothSocket?.isConnected == true
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao verificar conexão: ${e.message}")
            false
        }
    }
    
    fun printText(text: String) {
        if (!isConnected()) {
            onPrintResult?.invoke(false, "Dispositivo não conectado")
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Enviando texto: $text")
                
                // Verificar novamente se está conectado
                if (bluetoothSocket?.isConnected != true || outputStream == null) {
                    throw IOException("Conexão perdida durante a impressão")
                }
                
                val data = text.toByteArray()
                Log.d(TAG, "Enviando ${data.size} bytes")
                
                outputStream?.write(data)
                outputStream?.flush()
                
                // Aguardar um pouco para a impressora processar
                delay(500)
                
                Log.d(TAG, "Texto enviado com sucesso: $text")
                
                withContext(Dispatchers.Main) {
                    onPrintResult?.invoke(true, null)
                }
                
            } catch (e: IOException) {
                Log.e(TAG, "Erro ao imprimir texto: ${e.message}")
                Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
                
                // Tentar reconectar se a conexão foi perdida
                if (e.message?.contains("Conexão perdida") == true) {
                    disconnect()
                }
                
                withContext(Dispatchers.Main) {
                    onPrintResult?.invoke(false, "Erro ao imprimir: ${e.message}")
                }
            }
        }
    }
    
    fun printCPCL(cpclCommands: String) {
        if (!isConnected()) {
            onPrintResult?.invoke(false, "Dispositivo não conectado")
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Enviando comandos CPCL...")
                Log.d(TAG, "Comandos: $cpclCommands")
                
                // Verificar novamente se está conectado
                if (bluetoothSocket?.isConnected != true || outputStream == null) {
                    throw IOException("Conexão perdida durante a impressão")
                }
                
                val data = cpclCommands.toByteArray()
                Log.d(TAG, "Enviando ${data.size} bytes")
                
                // Enviar dados em chunks para evitar buffer overflow
                val chunkSize = 1024
                var offset = 0
                
                while (offset < data.size) {
                    val end = minOf(offset + chunkSize, data.size)
                    val chunk = data.copyOfRange(offset, end)
                    
                    outputStream?.write(chunk)
                    outputStream?.flush()
                    
                    Log.d(TAG, "Chunk enviado: ${chunk.size} bytes (${offset + chunk.size}/${data.size})")
                    
                    // Aguardar um pouco entre chunks
                    delay(100)
                    offset = end
                }
                
                // Aguardar um pouco para a impressora processar
                delay(500)
                
                Log.d(TAG, "Comandos CPCL enviados com sucesso")
                
                withContext(Dispatchers.Main) {
                    onPrintResult?.invoke(true, null)
                }
                
            } catch (e: IOException) {
                Log.e(TAG, "Erro ao imprimir CPCL: ${e.message}")
                Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
                
                // Tentar reconectar se a conexão foi perdida
                if (e.message?.contains("Conexão perdida") == true) {
                    disconnect()
                }
                
                withContext(Dispatchers.Main) {
                    onPrintResult?.invoke(false, "Erro ao imprimir CPCL: ${e.message}")
                }
            }
        }
    }
    
    fun printTestPage() {
        val testPage = CPCLCommands.generateTestPage()
        printCPCL(testPage)
    }
    
    fun testConnection(callback: (Boolean) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (isConnected()) {
                    // Tentar enviar um comando de teste simples
                    val testCommand = "\r\n"
                    outputStream?.write(testCommand.toByteArray())
                    outputStream?.flush()
                    delay(100)
                    
                    withContext(Dispatchers.Main) {
                        callback(true)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        callback(false)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Teste de conexão falhou: ${e.message}")
                withContext(Dispatchers.Main) {
                    callback(false)
                }
            }
        }
    }
    
    fun cleanup() {
        disconnect()
    }
}
