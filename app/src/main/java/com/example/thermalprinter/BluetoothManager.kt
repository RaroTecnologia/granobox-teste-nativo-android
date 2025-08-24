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
import com.example.thermalprinter.NiimbotPrinter

class BluetoothManager(private val context: Context) {
    
    companion object {
        private const val TAG = "BluetoothManager"
        private const val SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB"
        
        // UUIDs específicos da NIIMBOT
        private val NIIMBOT_UUIDS = listOf(
            "0000FFE0-0000-1000-8000-00805F9B34FB",  // NIIMBOT comum
            "0000FFE1-0000-1000-8000-00805F9B34FB"   // NIIMBOT serviço
        )
    }
    
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var currentDevice: BluetoothDevice? = null
    
    // Instância da impressora NIIMBOT
    private var niimbotPrinter: NiimbotPrinter? = null
    
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
    
    /**
     * Verifica se o dispositivo é uma NIIMBOT
     */
    private fun isNiimbotPrinter(device: BluetoothDevice): Boolean {
        val deviceName = device.name?.lowercase() ?: ""
        val deviceAddress = device.address.uppercase()
        
        // Verificar por nome
        if (deviceName.contains("niimbot") || 
            deviceName.contains("niim") || 
            deviceName.contains("nii")) {
            Log.d(TAG, "✅ Dispositivo identificado como NIIMBOT por nome: $deviceName")
            return true
        }
        
        // Verificar por endereço MAC (alguns padrões conhecidos)
        if (deviceAddress.startsWith("00:15:") || 
            deviceAddress.startsWith("00:16:") ||
            deviceAddress.startsWith("00:17:")) {
            Log.d(TAG, "✅ Dispositivo identificado como NIIMBOT por endereço: $deviceAddress")
            return true
        }
        
        Log.d(TAG, "❌ Dispositivo não identificado como NIIMBOT: $deviceName ($deviceAddress)")
        return false
    }
    
    /**
     * Conecta ao dispositivo usando endereço MAC
     */
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
    
    /**
     * Conecta ao dispositivo usando o protocolo apropriado
     */
    fun connectToDevice(device: BluetoothDevice, callback: (Boolean, String?) -> Unit) {
        if (!isBluetoothEnabled()) {
            callback(false, "Bluetooth não está habilitado")
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "=== INICIANDO CONEXÃO ===")
                Log.d(TAG, "Dispositivo: ${device.name} (${device.address})")
                Log.d(TAG, "Tipo: ${device.type}")
                
                // Verificar se é uma NIIMBOT
                val isNiimbot = isNiimbotPrinter(device)
                
                if (isNiimbot) {
                    Log.d(TAG, "🔍 Conectando como NIIMBOT...")
                    connectAsNiimbot(device, callback)
                } else {
                    Log.d(TAG, "🔍 Conectando como impressora genérica...")
                    connectAsGenericPrinter(device, callback)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro na conexão: ${e.message}")
                Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
                withContext(Dispatchers.Main) {
                    callback(false, "Erro na conexão: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Conecta como impressora NIIMBOT
     */
    private suspend fun connectAsNiimbot(device: BluetoothDevice, callback: (Boolean, String?) -> Unit) {
        try {
            Log.d(TAG, "UUID: NIIMBOT específico")
            
            // Desconectar conexão anterior
            disconnect()
            delay(1000)
            
            // Criar socket usando UUID NIIMBOT
            val niimbotUUID = UUID.fromString("0000FFE0-0000-1000-8000-00805F9B34FB")
            bluetoothSocket = device.createRfcommSocketToServiceRecord(niimbotUUID)
            
            Log.d(TAG, "Socket criado, tentando conectar...")
            bluetoothSocket?.connect()
            
            Log.d(TAG, "Connect() chamado, aguardando estabilização...")
            delay(1000)
            
            // Verificar status da conexão
            Log.d(TAG, "Verificando status da conexão...")
            if (bluetoothSocket?.isConnected == true) {
                Log.d(TAG, "Obtendo output stream...")
                outputStream = bluetoothSocket?.outputStream
                
                if (outputStream != null) {
                    currentDevice = device
                    
                    // Inicializar impressora NIIMBOT
                    niimbotPrinter = NiimbotPrinter()
                    niimbotPrinter?.connect(bluetoothSocket!!) { success, error ->
                        if (success) {
                            Log.d(TAG, "=== CONEXÃO NIIMBOT ESTABELECIDA COM SUCESSO ===")
                            Log.d(TAG, "Dispositivo: ${device.name}")
                            Log.d(TAG, "Socket conectado: ${bluetoothSocket?.isConnected}")
                            Log.d(TAG, "Output stream: ${outputStream != null}")
                            
                            withContext(Dispatchers.Main) {
                                onConnectionStateChanged?.invoke(true)
                                callback(true, "Conectado à NIIMBOT")
                            }
                        } else {
                            Log.e(TAG, "❌ Falha na inicialização NIIMBOT: $error")
                            withContext(Dispatchers.Main) {
                                callback(false, "Falha na inicialização NIIMBOT: $error")
                            }
                        }
                    }
                } else {
                    throw IOException("Não foi possível obter output stream")
                }
            } else {
                throw IOException("Socket não conectado")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro na conexão NIIMBOT: ${e.message}")
            disconnect()
            withContext(Dispatchers.Main) {
                callback(false, "Erro na conexão NIIMBOT: ${e.message}")
            }
        }
    }
    
    /**
     * Conecta como impressora genérica
     */
    private suspend fun connectAsGenericPrinter(device: BluetoothDevice, callback: (Boolean, String?) -> Unit) {
        try {
            Log.d(TAG, "UUID: $SPP_UUID")
            
            // Desconectar conexão anterior
            disconnect()
            delay(1000)
            
            // Criar socket RFCOMM
            bluetoothSocket = device.createRfcommSocketToServiceRecord(UUID.fromString(SPP_UUID))
            
            Log.d(TAG, "Socket criado, tentando conectar...")
            bluetoothSocket?.connect()
            
            Log.d(TAG, "Connect() chamado, aguardando estabilização...")
            delay(1000)
            
            // Verificar status da conexão
            Log.d(TAG, "Verificando status da conexão...")
            if (bluetoothSocket?.isConnected == true) {
                Log.d(TAG, "Obtendo output stream...")
                outputStream = bluetoothSocket?.outputStream
                
                if (outputStream != null) {
                    currentDevice = device
                    Log.d(TAG, "=== CONEXÃO GENÉRICA ESTABELECIDA COM SUCESSO ===")
                    Log.d(TAG, "Dispositivo: ${device.name}")
                    Log.d(TAG, "Socket conectado: ${bluetoothSocket?.isConnected}")
                    Log.d(TAG, "Output stream: ${outputStream != null}")
                    
                    withContext(Dispatchers.Main) {
                        onConnectionStateChanged?.invoke(true)
                        callback(true, "Conectado à impressora genérica")
                    }
                } else {
                    throw IOException("Não foi possível obter output stream")
                }
            } else {
                throw IOException("Socket não conectado")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro na conexão genérica: ${e.message}")
            disconnect()
            withContext(Dispatchers.Main) {
                callback(false, "Erro na conexão genérica: ${e.message}")
            }
        }
    }
    
    /**
     * Desconecta do dispositivo
     */
    fun disconnect() {
        try {
            niimbotPrinter?.disconnect()
            niimbotPrinter = null
            
            outputStream?.close()
            bluetoothSocket?.close()
            outputStream = null
            bluetoothSocket = null
            currentDevice = null
            
            Log.d(TAG, "Desconectado do dispositivo")
            
            onConnectionStateChanged?.invoke(false)
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
     * Imprime texto usando o protocolo apropriado
     */
    fun printText(text: String, callback: (Boolean, String?) -> Unit) {
        if (niimbotPrinter != null && niimbotPrinter!!.isConnected()) {
            Log.d(TAG, "🔍 Usando protocolo NIIMBOT para texto")
            niimbotPrinter!!.printText(text, callback)
        } else if (isConnected()) {
            Log.d(TAG, "🔍 Usando protocolo genérico para texto")
            printTextGeneric(text, callback)
        } else {
            callback(false, "Dispositivo não conectado")
        }
    }
    
    /**
     * Imprime etiqueta 60x60mm usando o protocolo apropriado
     */
    fun printLabel60x60(title: String, subtitle: String = "", barcode: String = "", qrData: String = "", callback: (Boolean, String?) -> Unit) {
        if (niimbotPrinter != null && niimbotPrinter!!.isConnected()) {
            Log.d(TAG, "🔍 Usando protocolo NIIMBOT para etiqueta")
            niimbotPrinter!!.printLabel60x60(title, subtitle) { success, error ->
                if (success && qrData.isNotEmpty()) {
                    // Se há dados QR, imprimir também
                    niimbotPrinter!!.printQR(qrData) { qrSuccess, qrError ->
                        callback(qrSuccess, if (qrSuccess) "Etiqueta e QR impressos" else qrError)
                    }
                } else {
                    callback(success, error)
                }
            }
        } else if (isConnected()) {
            Log.d(TAG, "🔍 Usando protocolo genérico para etiqueta")
            printLabel60x60Generic(title, subtitle, barcode, qrData, callback)
        } else {
            callback(false, "Dispositivo não conectado")
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

    /**
     * Captura comandos CPCL para análise
     */
    fun captureCPCLCommands(callback: (String) -> Unit) {
        if (!isConnected()) {
            callback("Dispositivo não conectado")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "=== CAPTURANDO COMANDOS CPCL ===")
                
                // Comandos de teste baseados no que pode estar funcionando
                val testCommands = listOf(
                    "! 0 200 200 50 1\r\nTEXT 4 0 10 20 TESTE\r\nFORM\r\nPRINT\r\n",
                    "! 0 200 200 100 1\r\nCENTER 4 0 50 TESTE CENTRO\r\nFORM\r\nPRINT\r\n",
                    "! 0 200 200 80 1\r\nLEFT 4 0 40 TESTE ESQUERDA\r\nFORM\r\nPRINT\r\n",
                    "! 0 200 200 60 1\r\nRIGHT 4 0 30 TESTE DIREITA\r\nFORM\r\nPRINT\r\n"
                )

                testCommands.forEachIndexed { index, command ->
                    Log.d(TAG, "Testando comando ${index + 1}: $command")
                    
                    val data = command.toByteArray()
                    outputStream?.write(data)
                    outputStream?.flush()
                    
                    delay(500) // Delay entre comandos
                    
                    Log.d(TAG, "Comando ${index + 1} enviado")
                }

                withContext(Dispatchers.Main) {
                    callback("Comandos de teste enviados - verifique a impressora")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro na captura: ${e.message}")
                withContext(Dispatchers.Main) {
                    callback("Erro: ${e.message}")
                }
            }
        }
    }

    /**
     * Testa comandos CPCL específicos baseados no OpenLabel
     */
    fun testOpenLabelStyleCPCL(callback: (Boolean, String?) -> Unit) {
        if (!isConnected()) {
            callback(false, "Dispositivo não conectado")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "=== TESTE ESTILO OPENLABEL ===")
                
                // Comando baseado no que o OpenLabel pode estar usando
                val openLabelCommand = buildString {
                    append("! 0 200 200 60 1\r\n")  // Form pequeno
                    append("CENTER 4 0 30 TESTE\r\n")  // Texto centralizado
                    append("FORM\r\n")
                    append("PRINT\r\n")
                }
                
                Log.d(TAG, "Comando OpenLabel: $openLabelCommand")
                
                val data = openLabelCommand.toByteArray()
                outputStream?.write(data)
                outputStream?.flush()
                
                delay(300)
                
                Log.d(TAG, "=== COMANDO OPENLABEL ENVIADO ===")
                withContext(Dispatchers.Main) {
                    callback(true, "Comando OpenLabel enviado")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro no teste OpenLabel: ${e.message}")
                withContext(Dispatchers.Main) {
                    callback(false, "Erro: ${e.message}")
                }
            }
        }
    }

    /**
     * Testa comando CPCL UNIVERSAL
     */
    fun printUniversalTest(callback: (Boolean, String?) -> Unit) {
        if (!isConnected()) {
            callback(false, "Dispositivo não conectado")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "=== TESTE CPCL UNIVERSAL ===")
                
                val universalCommand = CPCLCommands.generateUniversalTest()
                Log.d(TAG, "Comando universal: $universalCommand")
                
                val data = universalCommand.toByteArray()
                outputStream?.write(data)
                outputStream?.flush()
                
                delay(300)
                
                Log.d(TAG, "=== COMANDO UNIVERSAL ENVIADO ===")
                withContext(Dispatchers.Main) {
                    callback(true, "Comando universal enviado")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro no teste universal: ${e.message}")
                withContext(Dispatchers.Main) {
                    callback(false, "Erro: ${e.message}")
                }
            }
        }
    }

    /**
     * Testa comando CPCL com inicialização
     */
    fun printInitializedTest(callback: (Boolean, String?) -> Unit) {
        if (!isConnected()) {
            callback(false, "Dispositivo não conectado")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "=== TESTE CPCL INICIALIZADO ===")
                
                val initializedCommand = CPCLCommands.generateInitializedTest()
                Log.d(TAG, "Comando inicializado: $initializedCommand")
                
                val data = initializedCommand.toByteArray()
                outputStream?.write(data)
                outputStream?.flush()
                
                delay(300)
                
                Log.d(TAG, "=== COMANDO INICIALIZADO ENVIADO ===")
                withContext(Dispatchers.Main) {
                    callback(true, "Comando inicializado enviado")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro no teste inicializado: ${e.message}")
                withContext(Dispatchers.Main) {
                    callback(false, "Erro: ${e.message}")
                }
            }
        }
    }

    /**
     * Testa comando CPCL com reset
     */
    fun printResetTest(callback: (Boolean, String?) -> Unit) {
        if (!isConnected()) {
            callback(false, "Dispositivo não conectado")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "=== TESTE CPCL COM RESET ===")
                
                val resetCommand = CPCLCommands.generateResetTest()
                Log.d(TAG, "Comando com reset: $resetCommand")
                
                val data = resetCommand.toByteArray()
                outputStream?.write(data)
                outputStream?.flush()
                
                delay(500) // Delay maior para reset
                
                Log.d(TAG, "=== COMANDO COM RESET ENVIADO ===")
                withContext(Dispatchers.Main) {
                    callback(true, "Comando com reset enviado")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro no teste com reset: ${e.message}")
                withContext(Dispatchers.Main) {
                    callback(false, "Erro: ${e.message}")
                }
            }
        }
    }

    /**
     * Testa comando CPCL 60x60mm universal
     */
    fun print60x60UniversalLabel(callback: (Boolean, String?) -> Unit) {
        if (!isConnected()) {
            callback(false, "Dispositivo não conectado")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "=== TESTE CPCL 60x60mm UNIVERSAL ===")
                
                val universal60x60Command = CPCLCommands.generate60x60mmUniversal()
                Log.d(TAG, "Comando 60x60mm universal: $universal60x60Command")
                
                val data = universal60x60Command.toByteArray()
                outputStream?.write(data)
                outputStream?.flush()
                
                delay(400)
                
                Log.d(TAG, "=== COMANDO 60x60mm UNIVERSAL ENVIADO ===")
                withContext(Dispatchers.Main) {
                    callback(true, "Comando 60x60mm universal enviado")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro no teste 60x60mm universal: ${e.message}")
                withContext(Dispatchers.Main) {
                    callback(false, "Erro: ${e.message}")
                }
            }
        }
    }
}
