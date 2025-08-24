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
        
        // UUIDs comuns para impressoras térmicas
        private val COMMON_PRINTER_UUIDS = listOf(
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"), // SPP Padrão
            UUID.fromString("00001108-0000-1000-8000-00805F9B34FB"), // HID
            UUID.fromString("0000110A-0000-1000-8000-00805F9B34FB"), // Audio
            UUID.fromString("0000110B-0000-1000-8000-00805F9B34FB"), // Audio
            UUID.fromString("0000110C-0000-1000-8000-00805F9B34FB"), // Audio
            UUID.fromString("0000110E-0000-1000-8000-00805F9B34FB"), // Audio
            UUID.fromString("0000110F-0000-1000-8000-00805F9B34FB"), // Audio
            UUID.fromString("0000111E-0000-1000-8000-00805F9B34FB"), // Handsfree
            UUID.fromString("00001200-0000-1000-8000-00805F9B34FB"), // PnP Information
            UUID.fromString("00001800-0000-1000-8000-00805F9B34FB"), // Generic Access
            UUID.fromString("00001801-0000-1000-8000-00805F9B34FB")  // Generic Attribute
        )
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
            Log.e(TAG, "Bluetooth não está habilitado")
            callback(false, "Bluetooth não está habilitado")
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "=== INICIANDO CONEXÃO ===")
                Log.d(TAG, "Dispositivo: ${device.name} (${device.address})")
                Log.d(TAG, "Tipo: ${device.bondState}")
                Log.d(TAG, "UUID: $SPP_UUID")
                
                // Desconectar se já estiver conectado
                Log.d(TAG, "Desconectando conexão anterior...")
                disconnect()
                
                // Criar socket Bluetooth
                Log.d(TAG, "Criando socket RFCOMM...")
                bluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                
                if (bluetoothSocket == null) {
                    throw IOException("Falha ao criar socket")
                }
                
                Log.d(TAG, "Socket criado, tentando conectar...")
                
                // Definir timeout de conexão
                bluetoothSocket?.connect()
                
                Log.d(TAG, "Connect() chamado, aguardando estabilização...")
                
                // Aguardar um pouco para estabilizar a conexão
                delay(1000)
                
                Log.d(TAG, "Verificando status da conexão...")
                
                // Verificar se o socket está realmente conectado
                val isSocketConnected = bluetoothSocket?.isConnected == true
                Log.d(TAG, "Socket conectado: $isSocketConnected")
                
                if (isSocketConnected) {
                    // Obter output stream
                    Log.d(TAG, "Obtendo output stream...")
                    outputStream = bluetoothSocket?.outputStream
                    
                    if (outputStream != null) {
                        isConnected = true
                        Log.d(TAG, "=== CONEXÃO ESTABELECIDA COM SUCESSO ===")
                        Log.d(TAG, "Dispositivo: ${device.name}")
                        Log.d(TAG, "Socket conectado: ${bluetoothSocket?.isConnected}")
                        Log.d(TAG, "Output stream: ${outputStream != null}")
                        
                        withContext(Dispatchers.Main) {
                            onConnectionStateChanged?.invoke(true)
                            callback(true, null)
                        }
                    } else {
                        Log.e(TAG, "Output stream é null")
                        throw IOException("Não foi possível obter o output stream")
                    }
                } else {
                    Log.e(TAG, "Socket não está conectado após connect()")
                    throw IOException("Socket não está conectado")
                }
                
            } catch (e: IOException) {
                Log.e(TAG, "=== ERRO NA CONEXÃO ===")
                Log.e(TAG, "Erro: ${e.message}")
                Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
                disconnect()
                
                withContext(Dispatchers.Main) {
                    callback(false, "Erro na conexão: ${e.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "=== ERRO INESPERADO ===")
                Log.e(TAG, "Erro: ${e.message}")
                Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
                disconnect()
                
                withContext(Dispatchers.Main) {
                    callback(false, "Erro inesperado: ${e.message}")
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
        Log.d(TAG, "=== INICIANDO IMPRESSÃO CPCL ===")
        
        if (!isConnected()) {
            Log.e(TAG, "Dispositivo não conectado para impressão")
            onPrintResult?.invoke(false, "Dispositivo não conectado")
            return
        }
        
        Log.d(TAG, "Status da conexão:")
        Log.d(TAG, "- isConnected: $isConnected")
        Log.d(TAG, "- Socket conectado: ${bluetoothSocket?.isConnected}")
        Log.d(TAG, "- Output stream: ${outputStream != null}")
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Enviando comandos CPCL...")
                Log.d(TAG, "Comandos: $cpclCommands")
                
                // Verificar novamente se está conectado
                if (bluetoothSocket?.isConnected != true || outputStream == null) {
                    Log.e(TAG, "Conexão perdida durante a impressão")
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
                    
                    Log.d(TAG, "Enviando chunk ${offset/chunkSize + 1}: ${chunk.size} bytes")
                    outputStream?.write(chunk)
                    outputStream?.flush()
                    
                    Log.d(TAG, "Chunk enviado: ${chunk.size} bytes (${offset + chunk.size}/${data.size})")
                    
                    // Aguardar um pouco entre chunks
                    delay(100)
                    offset = end
                }
                
                // Aguardar um pouco para a impressora processar
                Log.d(TAG, "Aguardando processamento da impressora...")
                delay(500)
                
                Log.d(TAG, "=== IMPRESSÃO CPCL CONCLUÍDA COM SUCESSO ===")
                Log.d(TAG, "Total de bytes enviados: ${data.size}")
                
                withContext(Dispatchers.Main) {
                    onPrintResult?.invoke(true, null)
                }
                
            } catch (e: IOException) {
                Log.e(TAG, "=== ERRO AO IMPRIMIR CPCL ===")
                Log.e(TAG, "Erro: ${e.message}")
                Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
                
                // Tentar reconectar se a conexão foi perdida
                if (e.message?.contains("Conexão perdida") == true) {
                    Log.d(TAG, "Tentando desconectar devido à perda de conexão")
                    disconnect()
                }
                
                withContext(Dispatchers.Main) {
                    onPrintResult?.invoke(false, "Erro ao imprimir CPCL: ${e.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "=== ERRO INESPERADO NA IMPRESSÃO ===")
                Log.e(TAG, "Erro: ${e.message}")
                Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
                
                withContext(Dispatchers.Main) {
                    onPrintResult?.invoke(false, "Erro inesperado: ${e.message}")
                }
            }
        }
    }
    
    fun printTestPage() {
        val testPage = CPCLCommands.generateTestPage()
        printCPCL(testPage)
    }
    
    fun printLabel60x60(title: String, subtitle: String = "", barcode: String = "", qrData: String = "") {
        val label = CPCLCommands.generateLabel60x60(title, subtitle, barcode, qrData)
        printCPCL(label)
    }
    
    fun testConnection(callback: (Boolean) -> Unit) {
        Log.d(TAG, "=== TESTE DE CONEXÃO ===")
        Log.d(TAG, "Status atual:")
        Log.d(TAG, "- isConnected: $isConnected")
        Log.d(TAG, "- SPP_UUID: $SPP_UUID")
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (isConnected()) {
                    Log.d(TAG, "Conexão ativa, testando envio...")
                    
                    // Tentar enviar um comando de teste simples
                    val testCommand = "\r\n"
                    Log.d(TAG, "Enviando comando de teste: '$testCommand'")
                    
                    outputStream?.write(testCommand.toByteArray())
                    outputStream?.flush()
                    
                    Log.d(TAG, "Comando de teste enviado, aguardando...")
                    delay(100)
                    
                    Log.d(TAG, "=== TESTE DE CONEXÃO SUCESSO ===")
                    
                    withContext(Dispatchers.Main) {
                        callback(true)
                    }
                } else {
                    Log.e(TAG, "Conexão não está ativa")
                    withContext(Dispatchers.Main) {
                        callback(false)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "=== TESTE DE CONEXÃO FALHOU ===")
                Log.e(TAG, "Erro: ${e.message}")
                Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
                
                withContext(Dispatchers.Main) {
                    callback(false)
                }
            }
        }
    }
    
    fun getConnectionStatus(): String {
        return buildString {
            append("=== STATUS DA CONEXÃO ===\n")
            append("isConnected: $isConnected\n")
            append("Socket: ${bluetoothSocket?.isConnected}\n")
            append("Output Stream: ${outputStream != null}\n")
            append("Bluetooth Adapter: ${bluetoothAdapter?.isEnabled}\n")
            append("SPP UUID: $SPP_UUID")
        }
    }
    
    fun discoverDeviceUUIDs(device: BluetoothDevice, callback: (List<UUID>) -> Unit) {
        Log.d(TAG, "=== DESCOBRINDO UUIDs DO DISPOSITIVO ===")
        Log.d(TAG, "Dispositivo: ${device.name} (${device.address})")
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val supportedUUIDs = mutableListOf<UUID>()
                
                // Tentar conectar com cada UUID comum
                COMMON_PRINTER_UUIDS.forEach { uuid ->
                    try {
                        Log.d(TAG, "Testando UUID: $uuid")
                        
                        val testSocket = device.createRfcommSocketToServiceRecord(uuid)
                        testSocket.connect()
                        
                        if (testSocket.isConnected) {
                            Log.d(TAG, "✅ UUID suportado: $uuid")
                            supportedUUIDs.add(uuid)
                            testSocket.close()
                        } else {
                            Log.d(TAG, "❌ UUID não suportado: $uuid")
                        }
                        
                    } catch (e: Exception) {
                        Log.d(TAG, "❌ UUID falhou: $uuid - ${e.message}")
                    }
                }
                
                Log.d(TAG, "=== UUIDs DESCOBERTOS ===")
                Log.d(TAG, "Total: ${supportedUUIDs.size}")
                supportedUUIDs.forEach { uuid ->
                    Log.d(TAG, "✅ $uuid")
                }
                
                withContext(Dispatchers.Main) {
                    callback(supportedUUIDs)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao descobrir UUIDs: ${e.message}")
                withContext(Dispatchers.Main) {
                    callback(emptyList())
                }
            }
        }
    }
    
    fun cleanup() {
        disconnect()
    }

    /**
     * Testa impressão com comando CPCL muito simples
     */
    fun printSimpleTest(callback: (Boolean, String?) -> Unit) {
        if (!isConnected()) {
            callback(false, "Dispositivo não conectado")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "=== TESTE SIMPLES CPCL ===")
                Log.d(TAG, "Status da conexão:")
                Log.d(TAG, "- isConnected: ${isConnected()}")
                Log.d(TAG, "- Socket conectado: ${bluetoothSocket?.isConnected}")
                Log.d(TAG, "- Output stream: ${outputStream != null}")

                val simpleCommands = CPCLCommands.generateSimpleTest()
                Log.d(TAG, "Comandos simples: $simpleCommands")

                val data = simpleCommands.toByteArray()
                Log.d(TAG, "Enviando ${data.size} bytes")

                // Enviar em chunks pequenos
                val chunkSize = 512
                var offset = 0
                while (offset < data.size) {
                    val end = minOf(offset + chunkSize, data.size)
                    val chunk = data.copyOfRange(offset, end)
                    outputStream?.write(chunk)
                    outputStream?.flush()
                    Log.d(TAG, "Chunk enviado: ${chunk.size} bytes (${offset + chunk.size}/${data.size})")
                    delay(50) // Delay menor entre chunks
                    offset = end
                }

                delay(200) // Delay final menor
                Log.d(TAG, "=== TESTE SIMPLES ENVIADO ===")
                withContext(Dispatchers.Main) {
                    callback(true, "Teste simples enviado")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro no teste simples: ${e.message}")
                Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
                withContext(Dispatchers.Main) {
                    callback(false, "Erro: ${e.message}")
                }
            }
        }
    }

    /**
     * Testa impressão com comando CPCL de texto simples
     */
    fun printTextOnlyTest(text: String, callback: (Boolean, String?) -> Unit) {
        if (!isConnected()) {
            callback(false, "Dispositivo não conectado")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "=== TESTE TEXTO SIMPLES CPCL ===")
                Log.d(TAG, "Texto: $text")

                val textCommands = CPCLCommands.generateTextOnlyTest(text)
                Log.d(TAG, "Comandos: $textCommands")

                val data = textCommands.toByteArray()
                Log.d(TAG, "Enviando ${data.size} bytes")

                outputStream?.write(data)
                outputStream?.flush()
                delay(200)

                Log.d(TAG, "=== TESTE TEXTO ENVIADO ===")
                withContext(Dispatchers.Main) {
                    callback(true, "Texto enviado")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro no teste de texto: ${e.message}")
                withContext(Dispatchers.Main) {
                    callback(false, "Erro: ${e.message}")
                }
            }
        }
    }

    /**
     * Testa impressão com etiqueta 60x60mm simplificada
     */
    fun printSimpleLabel60x60(callback: (Boolean, String?) -> Unit) {
        if (!isConnected()) {
            callback(false, "Dispositivo não conectado")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "=== TESTE ETIQUETA SIMPLES 60x60 ===")

                val labelCommands = CPCLCommands.generateSimpleLabel60x60()
                Log.d(TAG, "Comandos: $labelCommands")

                val data = labelCommands.toByteArray()
                Log.d(TAG, "Enviando ${data.size} bytes")

                outputStream?.write(data)
                outputStream?.flush()
                delay(300)

                Log.d(TAG, "=== ETIQUETA SIMPLES ENVIADA ===")
                withContext(Dispatchers.Main) {
                    callback(true, "Etiqueta simples enviada")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro na etiqueta simples: ${e.message}")
                withContext(Dispatchers.Main) {
                    callback(false, "Erro: ${e.message}")
                }
            }
        }
    }

    /**
     * Testa comando CPCL ULTRA SIMPLES
     */
    fun printUltraSimpleTest(callback: (Boolean, String?) -> Unit) {
        if (!isConnected()) {
            callback(false, "Dispositivo não conectado")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "=== TESTE ULTRA SIMPLES CPCL ===")
                
                val ultraCommands = CPCLCommands.generateUltraSimpleTest()
                Log.d(TAG, "Comandos ultra simples: $ultraCommands")

                val data = ultraCommands.toByteArray()
                Log.d(TAG, "Enviando ${data.size} bytes")

                outputStream?.write(data)
                outputStream?.flush()
                delay(100)

                Log.d(TAG, "=== TESTE ULTRA SIMPLES ENVIADO ===")
                withContext(Dispatchers.Main) {
                    callback(true, "Teste ultra simples enviado")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro no teste ultra simples: ${e.message}")
                withContext(Dispatchers.Main) {
                    callback(false, "Erro: ${e.message}")
                }
            }
        }
    }

    /**
     * Testa comando CPCL apenas PRINT
     */
    fun printPrintOnlyTest(callback: (Boolean, String?) -> Unit) {
        if (!isConnected()) {
            callback(false, "Dispositivo não conectado")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "=== TESTE APENAS PRINT ===")
                
                val printCommand = CPCLCommands.generatePrintOnlyTest()
                Log.d(TAG, "Comando PRINT: $printCommand")

                val data = printCommand.toByteArray()
                Log.d(TAG, "Enviando ${data.size} bytes")

                outputStream?.write(data)
                outputStream?.flush()
                delay(100)

                Log.d(TAG, "=== COMANDO PRINT ENVIADO ===")
                withContext(Dispatchers.Main) {
                    callback(true, "Comando PRINT enviado")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro no comando PRINT: ${e.message}")
                withContext(Dispatchers.Main) {
                    callback(false, "Erro: ${e.message}")
                }
            }
        }
    }

    /**
     * Testa comando CPCL apenas FORM
     */
    fun printFormOnlyTest(callback: (Boolean, String?) -> Unit) {
        if (!isConnected()) {
            callback(false, "Dispositivo não conectado")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "=== TESTE APENAS FORM ===")
                
                val formCommand = CPCLCommands.generateFormOnlyTest()
                Log.d(TAG, "Comando FORM: $formCommand")

                val data = formCommand.toByteArray()
                Log.d(TAG, "Enviando ${data.size} bytes")

                outputStream?.write(data)
                outputStream?.flush()
                delay(100)

                Log.d(TAG, "=== COMANDO FORM ENVIADO ===")
                withContext(Dispatchers.Main) {
                    callback(true, "Comando FORM enviado")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro no comando FORM: ${e.message}")
                withContext(Dispatchers.Main) {
                    callback(false, "Erro: ${e.message}")
                }
            }
        }
    }
}
