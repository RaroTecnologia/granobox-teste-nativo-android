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
    
    // Protocolo selecionado pelo usuário
    private var selectedProtocol: PrinterProtocol = PrinterProtocol.AUTO
    
    fun setProtocol(protocol: PrinterProtocol) {
        selectedProtocol = protocol
        Log.d(TAG, "🔧 Protocolo alterado para: $protocol")
    }
    
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
            deviceName.contains("nii") ||
            deviceName.contains("blue") ||
            deviceName.contains("bluetooth")) {
            Log.d(TAG, "✅ Dispositivo identificado como NIIMBOT por nome: $deviceName")
            return true
        }
        
        // Verificar por endereço MAC (alguns padrões conhecidos)
        if (deviceAddress.startsWith("00:15:") || 
            deviceAddress.startsWith("00:16:") ||
            deviceAddress.startsWith("00:17:") ||
            deviceAddress.startsWith("66:32:")) {
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
                
                // Respeitar o protocolo selecionado pelo usuário
                when (selectedProtocol) {
                    PrinterProtocol.AUTO -> {
                        // Detecção automática (comportamento anterior)
                        val isNiimbot = isNiimbotPrinter(device)
                        if (isNiimbot) {
                            Log.d(TAG, "🔍 [AUTO] Conectando como NIIMBOT...")
                            connectAsNiimbot(device, callback)
                        } else {
                            Log.d(TAG, "🔍 [AUTO] Conectando como impressora genérica...")
                            connectAsGenericPrinter(device, callback)
                        }
                    }
                    PrinterProtocol.NIIMBOT -> {
                        Log.d(TAG, "🔍 [FORÇADO] Conectando como NIIMBOT...")
                        connectAsNiimbot(device, callback)
                    }
                    PrinterProtocol.TSPL -> {
                        Log.d(TAG, "🔍 [FORÇADO] Conectando como TSPL...")
                        connectAsTSPL(device, callback)
                    }
                    PrinterProtocol.CPCL -> {
                        Log.d(TAG, "🔍 [FORÇADO] Conectando como CPCL...")
                        connectAsGenericPrinter(device, callback)
                    }
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
                            
                            CoroutineScope(Dispatchers.Main).launch {
                                onConnectionStateChanged?.invoke(true)
                                callback(true, "Conectado à NIIMBOT")
                            }
                        } else {
                            Log.e(TAG, "❌ Falha na inicialização NIIMBOT: $error")
                            CoroutineScope(Dispatchers.Main).launch {
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
            CoroutineScope(Dispatchers.Main).launch {
                callback(false, "Erro na conexão NIIMBOT: ${e.message}")
            }
        }
    }
    
    /**
     * Conecta como impressora TSPL
     */
    private suspend fun connectAsTSPL(device: BluetoothDevice, callback: (Boolean, String?) -> Unit) {
        try {
            Log.d(TAG, "UUID: TSPL usando SPP padrão")
            
            // Desconectar conexão anterior
            disconnect()
            delay(1000)
            
            // Criar socket usando UUID SPP padrão
            val uuid = UUID.fromString(SPP_UUID)
            bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid)
            
            Log.d(TAG, "Socket TSPL criado, tentando conectar...")
            bluetoothSocket?.connect()
            
            Log.d(TAG, "Connect() chamado, aguardando estabilização...")
            delay(1000)
            
            // Verificar status da conexão
            Log.d(TAG, "Verificando status da conexão TSPL...")
            if (bluetoothSocket?.isConnected == true) {
                Log.d(TAG, "Obtendo output stream TSPL...")
                outputStream = bluetoothSocket?.outputStream
                
                if (outputStream != null) {
                    currentDevice = device
                    
                    Log.d(TAG, "=== CONEXÃO TSPL ESTABELECIDA COM SUCESSO ===")
                    Log.d(TAG, "Dispositivo: ${device.name}")
                    Log.d(TAG, "Socket conectado: ${bluetoothSocket?.isConnected}")
                    Log.d(TAG, "Output stream: ${outputStream != null}")
                    
                    CoroutineScope(Dispatchers.Main).launch {
                        onConnectionStateChanged?.invoke(true)
                        callback(true, "Conectado via TSPL")
                    }
                } else {
                    throw IOException("Não foi possível obter output stream TSPL")
                }
            } else {
                throw IOException("Socket TSPL não conectado")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro na conexão TSPL: ${e.message}")
            Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
            withContext(Dispatchers.Main) {
                callback(false, "Erro na conexão TSPL: ${e.message}")
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
                    
                    CoroutineScope(Dispatchers.Main).launch {
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
            CoroutineScope(Dispatchers.Main).launch {
                callback(false, "Erro na conexão genérica: ${e.message}")
            }
        }
    }
    
    /**
     * Verifica se está conectado
     */
    fun isConnected(): Boolean {
        return bluetoothSocket?.isConnected == true && outputStream != null
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
     * Imprime etiqueta 60x60mm usando o protocolo selecionado
     */
    fun printLabel60x60(title: String, subtitle: String = "", barcode: String = "", qrData: String = "", callback: (Boolean, String?) -> Unit) {
        if (!isConnected()) {
            callback(false, "Dispositivo não conectado")
            return
        }

        // Respeitar o protocolo selecionado pelo usuário
        when (selectedProtocol) {
            PrinterProtocol.AUTO -> {
                // Detecção automática (comportamento anterior)
                if (niimbotPrinter != null && niimbotPrinter!!.isConnected()) {
                    Log.d(TAG, "🔍 [AUTO] Usando protocolo NIIMBOT para etiqueta")
                    niimbotPrinter!!.printLabel60x60(title, subtitle) { success, error ->
                        if (success && qrData.isNotEmpty()) {
                            niimbotPrinter!!.printQR(qrData) { qrSuccess, qrError ->
                                callback(qrSuccess, if (qrSuccess) "Etiqueta e QR impressos" else qrError)
                            }
                        } else {
                            callback(success, error)
                        }
                    }
                } else {
                    Log.d(TAG, "🔍 [AUTO] Usando protocolo CPCL para etiqueta")
                    printLabel60x60Generic(title, subtitle, barcode, qrData, callback)
                }
            }
            PrinterProtocol.NIIMBOT -> {
                Log.d(TAG, "🔍 [FORÇADO] Usando protocolo NIIMBOT para etiqueta")
                val cpclCommands = CPCLCommands.generateLabel60x60(title, subtitle, barcode, qrData)
                val niimbotCommands = convertCPCLToNiimbot(cpclCommands)
                val data = niimbotCommands.toByteArray()
                
                CoroutineScope(Dispatchers.IO).launch {
                    val success = sendDataWithRetry(data)
                    withContext(Dispatchers.Main) {
                        callback(success, if (success) "Etiqueta NIIMBOT impressa" else "Falha na impressão NIIMBOT")
                    }
                }
            }
            PrinterProtocol.TSPL -> {
                Log.d(TAG, "🔍 [FORÇADO] Usando protocolo TSPL para etiqueta")
                val tsplCommands = TSPLCommands.generateTextLabel(title, subtitle)
                val data = tsplCommands.toByteArray()
                
                CoroutineScope(Dispatchers.IO).launch {
                    val success = sendDataWithRetry(data)
                    withContext(Dispatchers.Main) {
                        callback(success, if (success) "Etiqueta TSPL impressa" else "Falha na impressão TSPL")
                    }
                }
            }
            PrinterProtocol.CPCL -> {
                Log.d(TAG, "🔍 [FORÇADO] Usando protocolo CPCL para etiqueta")
                printLabel60x60Generic(title, subtitle, barcode, qrData, callback)
            }
        }
    }
    
    /**
     * Imprime texto usando protocolo genérico (CPCL)
     */
    private fun printTextGeneric(text: String, callback: (Boolean, String?) -> Unit) {
        if (!isConnected()) {
            callback(false, "Dispositivo não conectado")
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Enviando texto: $text")
                
                val data = text.toByteArray()
                Log.d(TAG, "Enviando ${data.size} bytes")
                
                outputStream?.write(data)
                outputStream?.flush()
                
                delay(500)
                
                Log.d(TAG, "Texto enviado com sucesso: $text")
                
                withContext(Dispatchers.Main) {
                    callback(true, null)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao imprimir texto: ${e.message}")
                withContext(Dispatchers.Main) {
                    callback(false, "Erro ao imprimir: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Imprime etiqueta 60x60mm usando protocolo genérico (CPCL)
     */
    private fun printLabel60x60Generic(title: String, subtitle: String, barcode: String, qrData: String, callback: (Boolean, String?) -> Unit) {
        if (!isConnected()) {
            callback(false, "Dispositivo não conectado")
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "=== INICIANDO IMPRESSÃO CPCL ===")
                Log.d(TAG, "Status da conexão:")
                Log.d(TAG, "- isConnected: ${isConnected()}")
                Log.d(TAG, "- Socket conectado: ${bluetoothSocket?.isConnected}")
                Log.d(TAG, "- Output stream: ${outputStream != null}")
                
                val cpclCommands = CPCLCommands.generateLabel60x60(title, subtitle, barcode, qrData)
                Log.d(TAG, "Enviando comandos CPCL...")
                Log.d(TAG, "Comandos: $cpclCommands")
                
                val data = cpclCommands.toByteArray()
                Log.d(TAG, "Enviando ${data.size} bytes")
                
                // Enviar em chunks
                val chunkSize = 1024
                var offset = 0
                while (offset < data.size) {
                    val end = minOf(offset + chunkSize, data.size)
                    val chunk = data.copyOfRange(offset, end)
                    outputStream?.write(chunk)
                    outputStream?.flush()
                    Log.d(TAG, "Chunk enviado: ${chunk.size} bytes (${offset + chunk.size}/${data.size})")
                    delay(100)
                    offset = end
                }
                
                delay(500)
                
                Log.d(TAG, "=== IMPRESSÃO CPCL CONCLUÍDA COM SUCESSO ===")
                Log.d(TAG, "Total de bytes enviados: ${data.size}")
                
                withContext(Dispatchers.Main) {
                    callback(true, null)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Erro na impressão CPCL: ${e.message}")
                Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
                
                // Tentar reconectar se a conexão foi perdida
                if (e.message?.contains("Conexão perdida") == true) {
                    disconnect()
                }
                
                withContext(Dispatchers.Main) {
                    callback(false, "Erro na impressão: ${e.message}")
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
                Log.d(TAG, "=== INICIANDO IMPRESSÃO ===")
                Log.d(TAG, "Status da conexão:")
                Log.d(TAG, "- isConnected: ${isConnected()}")
                Log.d(TAG, "- Socket conectado: ${bluetoothSocket?.isConnected}")
                Log.d(TAG, "- Output stream: ${outputStream != null}")
                
                // Respeitar o protocolo selecionado pelo usuário
                when (selectedProtocol) {
                    PrinterProtocol.AUTO -> {
                        // Detecção automática (comportamento anterior)
                        val isNiimbot = currentDevice?.let { isNiimbotPrinter(it) } ?: false
                        if (isNiimbot) {
                            Log.d(TAG, "🔍 [AUTO] Usando protocolo NIIMBOT para impressão")
                            val niimbotCommands = convertCPCLToNiimbot(cpclCommands)
                            val data = niimbotCommands.toByteArray()
                            val success = sendDataWithRetry(data)
                            if (success) {
                                Log.d(TAG, "=== IMPRESSÃO NIIMBOT CONCLUÍDA COM SUCESSO ===")
                                withContext(Dispatchers.Main) {
                                    onPrintResult?.invoke(true, "Impressão NIIMBOT realizada")
                                }
                            } else {
                                Log.e(TAG, "❌ Falha no envio NIIMBOT após retry")
                                withContext(Dispatchers.Main) {
                                    onPrintResult?.invoke(false, "Falha na impressão NIIMBOT")
                                }
                            }
                        } else {
                            Log.d(TAG, "🔍 [AUTO] Usando protocolo CPCL genérico para impressão")
                            val data = cpclCommands.toByteArray()
                            val success = sendDataWithRetry(data)
                            if (success) {
                                Log.d(TAG, "=== IMPRESSÃO CPCL CONCLUÍDA COM SUCESSO ===")
                                withContext(Dispatchers.Main) {
                                    onPrintResult?.invoke(true, null)
                                }
                            } else {
                                Log.e(TAG, "❌ Falha no envio CPCL após retry")
                                withContext(Dispatchers.Main) {
                                    onPrintResult?.invoke(false, "Falha no envio após tentativas de reconexão")
                                }
                            }
                        }
                    }
                    PrinterProtocol.NIIMBOT -> {
                        Log.d(TAG, "🔍 [FORÇADO] Usando protocolo NIIMBOT para impressão")
                        val niimbotCommands = convertCPCLToNiimbot(cpclCommands)
                        val data = niimbotCommands.toByteArray()
                        val success = sendDataWithRetry(data)
                        if (success) {
                            Log.d(TAG, "=== IMPRESSÃO NIIMBOT CONCLUÍDA COM SUCESSO ===")
                            withContext(Dispatchers.Main) {
                                onPrintResult?.invoke(true, "Impressão NIIMBOT realizada")
                            }
                        } else {
                            Log.e(TAG, "❌ Falha no envio NIIMBOT após retry")
                            withContext(Dispatchers.Main) {
                                onPrintResult?.invoke(false, "Falha na impressão NIIMBOT")
                            }
                        }
                    }
                    PrinterProtocol.TSPL -> {
                        Log.d(TAG, "🔍 [FORÇADO] Usando protocolo TSPL para impressão")
                        // Converter comandos CPCL para TSPL
                        val tsplCommands = convertCPCLToTSPL(cpclCommands)
                        val data = tsplCommands.toByteArray()
                        val success = sendDataWithRetry(data)
                        if (success) {
                            Log.d(TAG, "=== IMPRESSÃO TSPL CONCLUÍDA COM SUCESSO ===")
                            withContext(Dispatchers.Main) {
                                onPrintResult?.invoke(true, "Impressão TSPL realizada")
                            }
                        } else {
                            Log.e(TAG, "❌ Falha no envio TSPL após retry")
                            withContext(Dispatchers.Main) {
                                onPrintResult?.invoke(false, "Falha na impressão TSPL")
                            }
                        }
                    }
                    PrinterProtocol.CPCL -> {
                        Log.d(TAG, "🔍 [FORÇADO] Usando protocolo CPCL genérico para impressão")
                        val data = cpclCommands.toByteArray()
                        val success = sendDataWithRetry(data)
                        if (success) {
                            Log.d(TAG, "=== IMPRESSÃO CPCL CONCLUÍDA COM SUCESSO ===")
                            withContext(Dispatchers.Main) {
                                onPrintResult?.invoke(true, null)
                            }
                        } else {
                            Log.e(TAG, "❌ Falha no envio CPCL após retry")
                            withContext(Dispatchers.Main) {
                                onPrintResult?.invoke(false, "Falha no envio após tentativas de reconexão")
                            }
                        }
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Erro na impressão: ${e.message}")
                Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
                
                withContext(Dispatchers.Main) {
                    onPrintResult?.invoke(false, "Erro na impressão: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Converte comandos CPCL para protocolo TSPL
     */
    private fun convertCPCLToTSPL(cpclCommands: String): String {
        // Converter comandos CPCL básicos para TSPL
        return buildString {
            // Inicialização TSPL
            append(TSPLCommands.initialize())
            
            // Se contém "TEXT", extrair e converter
            if (cpclCommands.contains("TEXT")) {
                // Exemplo: TEXT 4 0 10 20 TESTE
                val textMatch = Regex("TEXT (\\d+) (\\d+) (\\d+) (\\d+) (.+)").find(cpclCommands)
                if (textMatch != null) {
                    val (font, rotation, x, y, text) = textMatch.destructured
                    append(TSPLCommands.text(x.toInt(), y.toInt(), font, rotation.toInt(), 1, 1, text))
                } else {
                    // Texto simples
                    append(TSPLCommands.text(50, 100, "3", 0, 1, 1, "TEXTO TSPL"))
                }
            } else {
                // Texto padrão se não houver TEXT
                append(TSPLCommands.text(50, 100, "3", 0, 1, 1, "CPCL->TSPL"))
            }
            
            // Se contém "QR", extrair e converter
            if (cpclCommands.contains("QR")) {
                val qrMatch = Regex("QR (\\d+) (\\d+) (\\d+) (.+)").find(cpclCommands)
                if (qrMatch != null) {
                    val (x, y, size, data) = qrMatch.destructured
                    append(TSPLCommands.qrCode(x.toInt(), y.toInt(), "M", size.toInt(), "A", 0, data))
                }
            }
            
            // Comando de impressão
            append(TSPLCommands.print(1))
        }
    }

    /**
     * Converte comandos CPCL para protocolo NIIMBOT
     */
    private fun convertCPCLToNiimbot(cpclCommands: String): String {
        // Protocolo NIIMBOT baseado na biblioteca niimbluelib
        return buildString {
            // Comando de inicialização
            append(0x02.toChar())  // STX
            append(0x00.toChar())  // Comando de inicialização
            append(0x00.toChar())  // Dados
            append(0x03.toChar())  // ETX
            
            // Comando de impressão de texto
            append(0x02.toChar())  // STX
            append(0x01.toChar())  // CMD_PRINT_TEXT
            append(cpclCommands.length.toChar())  // Tamanho
            append(cpclCommands)  // Texto
            append(0x03.toChar())  // ETX
            
            // Comando de alimentar papel
            append(0x02.toChar())  // STX
            append(0x05.toChar())  // CMD_FEED
            append(0x01.toChar())  // 1 linha
            append(0x03.toChar())  // ETX
        }
    }
    
    fun printTestPage() {
        val cpclCommands = CPCLCommands.generateTestPage()
        printCPCL(cpclCommands)
    }
    
    fun printLabel60x60Simple(title: String, subtitle: String = "", barcode: String = "", qrData: String = "") {
        // Este método agora usa printCPCL que já respeita o protocolo selecionado
        val cpclCommands = CPCLCommands.generateLabel60x60(title, subtitle, barcode, qrData)
        printCPCL(cpclCommands)
    }
    
    fun testConnection(callback: (Boolean) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "=== TESTE DE CONEXÃO ===")
                Log.d(TAG, "Status atual:")
                Log.d(TAG, "- isConnected: ${isConnected()}")
                Log.d(TAG, "- SPP_UUID: $SPP_UUID")
                
                if (isConnected()) {
                    Log.d(TAG, "Conexão ativa, testando envio...")
                    
                    // Enviar comando de teste simples
                    val testCommand = "TESTE"
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
                Log.e(TAG, "Erro no teste de conexão: ${e.message}")
                withContext(Dispatchers.Main) {
                    callback(false)
                }
            }
        }
    }
    
    fun getConnectionStatus(): String {
        return buildString {
            append("=== STATUS DA CONEXÃO ===\n")
            append("isConnected: ${isConnected()}\n")
            append("Socket: ${bluetoothSocket?.isConnected}\n")
            append("Output Stream: ${outputStream != null}\n")
            append("Bluetooth Adapter: ${bluetoothAdapter?.isEnabled}\n")
            append("SPP UUID: $SPP_UUID")
        }
    }
    
    fun discoverDeviceUUIDs(device: BluetoothDevice, callback: (List<UUID>) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val supportedUUIDs = mutableListOf<UUID>()
            
            // UUIDs comuns para impressoras térmicas
            val commonUUIDs = listOf(
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
            
            commonUUIDs.forEach { uuid ->
                try {
                    Log.d(TAG, "Testando UUID: $uuid")
                    val testSocket = device.createRfcommSocketToServiceRecord(uuid)
                    testSocket.connect()
                    
                    if (testSocket.isConnected) {
                        Log.d(TAG, "✅ UUID suportado: $uuid")
                        supportedUUIDs.add(uuid)
                        testSocket.close()
                    } else {
                        Log.d(TAG, "❌ UUID falhou: $uuid - socket não conectado")
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
        }
    }
    
    fun cleanup() {
        disconnect()
    }

    /**
     * Testa comando CPCL muito simples
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
     * Testa comando CPCL de texto simples
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
     * Testa comando CPCL com etiqueta 60x60mm simplificada
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

    /**
     * Verifica se está conectado e reconecta se necessário
     */
    private fun ensureConnection(): Boolean {
        if (isConnected()) {
            return true
        }
        
        Log.w(TAG, "⚠️ Conexão perdida, tentando reconectar...")
        
        // Tentar reconectar automaticamente
        currentDevice?.let { device ->
            try {
                // Fechar conexões antigas
                disconnect()
                
                // Aguardar um pouco
                Thread.sleep(1000)
                
                // Tentar reconectar
                val uuid = UUID.fromString(SPP_UUID)
                bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid)
                bluetoothSocket?.connect()
                
                Thread.sleep(1000)
                
                if (bluetoothSocket?.isConnected == true) {
                    outputStream = bluetoothSocket?.outputStream
                    if (outputStream != null) {
                        Log.d(TAG, "✅ Reconexão bem-sucedida")
                        return true
                    } else {
                        Log.w(TAG, "⚠️ Output stream nulo após reconexão")
                    }
                } else {
                    Log.w(TAG, "⚠️ Socket não conectado após reconexão")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Falha na reconexão: ${e.message}")
            }
        }
        
        Log.e(TAG, "❌ Não foi possível reconectar")
        return false
    }
    
    /**
     * Envia dados com verificação de conexão e retry
     */
    private fun sendDataWithRetry(data: ByteArray, maxRetries: Int = 3): Boolean {
        var retries = 0
        
        while (retries < maxRetries) {
            try {
                if (!ensureConnection()) {
                    Log.e(TAG, "❌ Sem conexão para envio")
                    return false
                }
                
                // Verificar se o socket ainda está válido
                if (bluetoothSocket?.isConnected != true || outputStream == null) {
                    Log.w(TAG, "⚠️ Socket inválido, tentando reconectar...")
                    if (!ensureConnection()) {
                        retries++
                        continue
                    } else {
                        Log.d(TAG, "✅ Reconexão bem-sucedida durante envio")
                    }
                } else {
                    Log.d(TAG, "✅ Socket válido para envio")
                }
                
                // Enviar dados
                outputStream?.write(data)
                outputStream?.flush()
                
                Log.d(TAG, "✅ Dados enviados com sucesso (${data.size} bytes)")
                return true
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro no envio (tentativa ${retries + 1}): ${e.message}")
                
                if (e.message?.contains("Broken pipe", ignoreCase = true) == true ||
                    e.message?.contains("Connection reset", ignoreCase = true) == true) {
                    Log.w(TAG, "🔄 Conexão resetada, tentando reconectar...")
                    disconnect()
                    Thread.sleep(1000)
                }
                
                retries++
                if (retries < maxRetries) {
                    Log.d(TAG, "🔄 Aguardando antes da próxima tentativa...")
                    Thread.sleep(2000)
                } else {
                    Log.d(TAG, "🔄 Última tentativa realizada")
                }
            }
        }
        
        Log.e(TAG, "❌ Falha no envio após $maxRetries tentativas")
        return false
    }

    /**
     * Imprime etiqueta usando protocolo NIIMBOT correto
     */
    fun printNiimbotLabel(title: String, subtitle: String = "", callback: (Boolean, String?) -> Unit) {
        if (!isConnected()) {
            callback(false, "Dispositivo não conectado")
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "=== IMPRESSÃO ETIQUETA NIIMBOT ===")
                Log.d(TAG, "Título: $title")
                Log.d(TAG, "Subtítulo: $subtitle")
                
                // Protocolo NIIMBOT correto baseado na biblioteca niimbluelib
                val niimbotCommands = buildString {
                    // Comando de inicialização
                    append(0x02.toChar())  // STX
                    append(0x00.toChar())  // Comando de inicialização
                    append(0x00.toChar())  // Dados
                    append(0x03.toChar())  // ETX
                    
                    // Comando de impressão de etiqueta
                    append(0x02.toChar())  // STX
                    append(0x02.toChar())  // CMD_PRINT_LABEL
                    append(0x00.toChar())  // Tamanho (será calculado)
                    append(0x03.toChar())  // ETX
                    
                    // Comando de impressão de texto (título)
                    append(0x02.toChar())  // STX
                    append(0x01.toChar())  // CMD_PRINT_TEXT
                    append(title.length.toChar())  // Tamanho do título
                    append(title)  // Título
                    append(0x03.toChar())  // ETX
                    
                    // Comando de impressão de texto (subtítulo)
                    if (subtitle.isNotEmpty()) {
                        append(0x02.toChar())  // STX
                        append(0x01.toChar())  // CMD_PRINT_TEXT
                        append(subtitle.length.toChar())  // Tamanho do subtítulo
                        append(subtitle)  // Subtítulo
                        append(0x03.toChar())  // ETX
                    } else {
                        // Não fazer nada se não houver subtítulo
                    }
                    
                    // Comando de alimentar papel
                    append(0x02.toChar())  // STX
                    append(0x05.toChar())  // CMD_FEED
                    append(0x01.toChar())  // 1 linha
                    append(0x03.toChar())  // ETX
                }
                
                Log.d(TAG, "Comandos NIIMBOT: ${niimbotCommands.map { it.code.toByte() }}")
                
                val data = niimbotCommands.toByteArray()
                Log.d(TAG, "Enviando ${data.size} bytes via NIIMBOT")
                
                // Usar sistema de retry para evitar "Broken Pipe"
                val success = sendDataWithRetry(data)
                
                if (success) {
                    Log.d(TAG, "=== ETIQUETA NIIMBOT IMPRESSA COM SUCESSO ===")
                    withContext(Dispatchers.Main) {
                        callback(true, "Etiqueta NIIMBOT impressa")
                    }
                } else {
                    Log.e(TAG, "❌ Falha na impressão da etiqueta NIIMBOT")
                    withContext(Dispatchers.Main) {
                        callback(false, "Falha na impressão da etiqueta")
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Erro na impressão da etiqueta NIIMBOT: ${e.message}")
                withContext(Dispatchers.Main) {
                    callback(false, "Erro: ${e.message}")
                }
            }
        }
    }
}
