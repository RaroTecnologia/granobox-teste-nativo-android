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
                
                // Criar socket Bluetooth
                bluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                bluetoothSocket?.connect()
                
                // Obter output stream
                outputStream = bluetoothSocket?.outputStream
                
                isConnected = true
                Log.d(TAG, "Conectado com sucesso ao dispositivo: ${device.name}")
                
                withContext(Dispatchers.Main) {
                    onConnectionStateChanged?.invoke(true)
                    callback(true, null)
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
        return isConnected && bluetoothSocket?.isConnected == true
    }
    
    fun printText(text: String) {
        if (!isConnected()) {
            onPrintResult?.invoke(false, "Dispositivo não conectado")
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val data = text.toByteArray()
                outputStream?.write(data)
                outputStream?.flush()
                
                Log.d(TAG, "Texto enviado com sucesso: $text")
                
                withContext(Dispatchers.Main) {
                    onPrintResult?.invoke(true, null)
                }
                
            } catch (e: IOException) {
                Log.e(TAG, "Erro ao imprimir texto: ${e.message}")
                
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
                val data = cpclCommands.toByteArray()
                outputStream?.write(data)
                outputStream?.flush()
                
                Log.d(TAG, "Comandos CPCL enviados com sucesso")
                
                withContext(Dispatchers.Main) {
                    onPrintResult?.invoke(true, null)
                }
                
            } catch (e: IOException) {
                Log.e(TAG, "Erro ao imprimir CPCL: ${e.message}")
                
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
    
    fun cleanup() {
        disconnect()
    }
}
