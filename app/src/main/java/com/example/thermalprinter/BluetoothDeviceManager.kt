package com.example.thermalprinter

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import kotlinx.coroutines.cancel

class BluetoothDeviceManager(private val context: Context) {
    
    companion object {
        private const val TAG = "BluetoothDeviceManager"
    }
    
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var isScanning = false
    
    // Callbacks
    var onDeviceDiscovered: ((BluetoothDevice) -> Unit)? = null
    var onScanStarted: (() -> Unit)? = null
    var onScanStopped: (() -> Unit)? = null
    var onScanError: ((String) -> Unit)? = null
    
    private val discoveredDevices = mutableListOf<BluetoothDevice>()
    private val pairedDevices = mutableListOf<BluetoothDevice>()
    
    // BroadcastReceiver para dispositivos clássicos
    private val discoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    device?.let { handleDiscoveredDevice(it) }
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    Log.d(TAG, "Descoberta de dispositivos iniciada")
                    onScanStarted?.invoke()
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Log.d(TAG, "Descoberta de dispositivos finalizada")
                    isScanning = false
                    onScanStopped?.invoke()
                }
            }
        }
    }
    
    // ScanCallback para dispositivos BLE
    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            handleDiscoveredDevice(result.device)
        }
        
        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e(TAG, "Scan BLE falhou com código: $errorCode")
            isScanning = false
            onScanError?.invoke("Scan BLE falhou: $errorCode")
        }
    }
    
    fun initialize(adapter: BluetoothAdapter) {
        bluetoothAdapter = adapter
        bluetoothLeScanner = adapter.bluetoothLeScanner
        Log.d(TAG, "BluetoothDeviceManager inicializado")
    }
    
    fun registerReceivers() {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        context.registerReceiver(discoveryReceiver, filter)
        Log.d(TAG, "Receivers registrados")
    }
    
    fun unregisterReceivers() {
        try {
            context.unregisterReceiver(discoveryReceiver)
            Log.d(TAG, "Receivers desregistrados")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao desregistrar receivers: ${e.message}")
        }
    }
    
    fun startScan() {
        if (isScanning) {
            Log.w(TAG, "Scan já está em andamento")
            onScanError?.invoke("Scan já está em andamento")
            return
        }
        
        bluetoothAdapter?.let { adapter ->
            if (!adapter.isEnabled) {
                onScanError?.invoke("Bluetooth não está habilitado")
                return
            }
            
            isScanning = true
            Log.d(TAG, "Iniciando scan de dispositivos")
            
            // Limpar lista de dispositivos descobertos
            discoveredDevices.clear()
            
            // Iniciar scan clássico
            if (adapter.startDiscovery()) {
                Log.d(TAG, "Scan clássico iniciado")
            } else {
                Log.e(TAG, "Falha ao iniciar scan clássico")
                isScanning = false
                onScanError?.invoke("Falha ao iniciar scan clássico")
            }
            
            // Iniciar scan BLE se disponível
            bluetoothLeScanner?.let { scanner ->
                try {
                    scanner.startScan(bleScanCallback)
                    Log.d(TAG, "Scan BLE iniciado")
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao iniciar scan BLE: ${e.message}")
                }
            }
            
        } ?: run {
            Log.e(TAG, "BluetoothAdapter é null")
            isScanning = false
            onScanError?.invoke("BluetoothAdapter não disponível")
        }
    }
    
    fun stopScan() {
        Log.d(TAG, "Parando scan de dispositivos")
        
        bluetoothAdapter?.let { adapter ->
            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
                Log.d(TAG, "Scan clássico parado")
            }
        }
        
        bluetoothLeScanner?.let { scanner ->
            try {
                scanner.stopScan(bleScanCallback)
                Log.d(TAG, "Scan BLE parado")
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao parar scan BLE: ${e.message}")
            }
        }
        
        isScanning = false
        onScanStopped?.invoke()
    }
    
    private fun handleDiscoveredDevice(device: BluetoothDevice) {
        if (!discoveredDevices.contains(device)) {
            discoveredDevices.add(device)
            Log.d(TAG, "Dispositivo descoberto: ${device.name ?: "Sem nome"} (${device.address})")
            onDeviceDiscovered?.invoke(device)
        }
    }
    
    fun getDiscoveredDevices(): List<BluetoothDevice> {
        return discoveredDevices.toList()
    }
    
    fun getPairedDevices(): List<BluetoothDevice> {
        bluetoothAdapter?.let { adapter ->
            if (pairedDevices.isEmpty()) {
                pairedDevices.addAll(adapter.bondedDevices)
                Log.d(TAG, "Dispositivos emparelhados carregados: ${pairedDevices.size}")
            }
        }
        return pairedDevices.toList()
    }
    
    fun isScanning(): Boolean {
        return isScanning
    }
    
    fun cleanup() {
        stopScan()
        unregisterReceivers()
        discoveredDevices.clear()
        pairedDevices.clear()
        Log.d(TAG, "BluetoothDeviceManager limpo")
    }
}
