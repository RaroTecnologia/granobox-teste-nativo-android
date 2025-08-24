package com.example.thermalprinter

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager as AndroidBluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.thermalprinter.databinding.ActivityBluetoothBinding
import java.util.UUID

class BluetoothActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "BluetoothActivity"
        const val EXTRA_DEVICE_ADDRESS = "device_address"
        const val EXTRA_DEVICE_NAME = "device_name"
    }
    
    private lateinit var binding: ActivityBluetoothBinding
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var deviceManager: BluetoothDeviceManager
    private lateinit var deviceAdapter: BluetoothDeviceAdapter
    
    // Receiver para mudanças no estado de emparelhamento
    private val pairingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                android.bluetooth.BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(android.bluetooth.BluetoothDevice.EXTRA_DEVICE, android.bluetooth.BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(android.bluetooth.BluetoothDevice.EXTRA_DEVICE)
                    }
                    val bondState = intent.getIntExtra(android.bluetooth.BluetoothDevice.EXTRA_BOND_STATE, android.bluetooth.BluetoothDevice.ERROR)
                    
                    device?.let {
                        Log.d(TAG, "Estado de emparelhamento alterado para ${it.name}: $bondState")
                        updateDeviceList()
                    }
                }
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBluetoothBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        initializeBluetooth()
        setupRecyclerView()
        setupUI()
        loadPairedDevices()
    }
    
    private fun initializeBluetooth() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as AndroidBluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        
        deviceManager = BluetoothDeviceManager(this)
        deviceManager.initialize(bluetoothAdapter)
        deviceManager.registerReceivers()
        
        // Configurar callbacks
        deviceManager.onDeviceDiscovered = { device ->
            runOnUiThread {
                deviceAdapter.addDevice(device)
                Log.d(TAG, "Dispositivo adicionado à lista: ${device.name}")
            }
        }
        
        deviceManager.onScanStarted = {
            runOnUiThread {
                updateScanStatus("Procurando dispositivos...")
                binding.progressBar.visibility = View.VISIBLE
                binding.btnScan.isEnabled = false
                binding.btnStopScan.isEnabled = true
            }
        }
        
        deviceManager.onScanStopped = {
            runOnUiThread {
                updateScanStatus("Busca concluída")
                binding.progressBar.visibility = View.GONE
                binding.btnScan.isEnabled = true
                binding.btnStopScan.isEnabled = false
            }
        }
        
        deviceManager.onScanError = { error ->
            runOnUiThread {
                updateScanStatus("Erro: $error")
                binding.progressBar.visibility = View.GONE
                binding.btnScan.isEnabled = true
                binding.btnStopScan.isEnabled = false
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
            }
        }
        
        // Registrar receiver para mudanças de emparelhamento
        val filter = IntentFilter(android.bluetooth.BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        registerReceiver(pairingReceiver, filter)
    }
    
    private fun setupRecyclerView() {
        deviceAdapter = BluetoothDeviceAdapter()
        binding.rvDevices.layoutManager = LinearLayoutManager(this)
        binding.rvDevices.adapter = deviceAdapter
        
        deviceAdapter.onDeviceSelected = { device ->
            onDeviceSelected(device)
        }
    }
    
    private fun setupUI() {
        binding.btnBack.setOnClickListener {
            finish()
        }
        
        binding.btnScan.setOnClickListener {
            startDeviceDiscovery()
        }
        
        binding.btnStopScan.setOnClickListener {
            stopDeviceDiscovery()
        }
        
        updateScanStatus("Clique em 'Procurar' para encontrar dispositivos")
    }
    
    private fun startDeviceDiscovery() {
        if (deviceManager.isScanning()) {
            Toast.makeText(this, "Scan já está em andamento", Toast.LENGTH_SHORT).show()
            return
        }
        
        Log.d(TAG, "Iniciando descoberta de dispositivos")
        deviceManager.startScan()
    }
    
    private fun stopDeviceDiscovery() {
        Log.d(TAG, "Parando descoberta de dispositivos")
        deviceManager.stopScan()
    }
    
    private fun loadPairedDevices() {
        val pairedDevices = deviceManager.getPairedDevices()
        Log.d(TAG, "Dispositivos emparelhados carregados: ${pairedDevices.size}")
        
        pairedDevices.forEach { device ->
            Log.d(TAG, "Dispositivo emparelhado: ${device.name} (${device.address}) - Estado: ${device.bondState}")
        }
        
        deviceAdapter.updateDevices(pairedDevices)
        updateScanStatus("${pairedDevices.size} dispositivo(s) emparelhado(s) encontrado(s)")
    }
    
    private fun updateScanStatus(status: String) {
        binding.tvScanStatus.text = status
        Log.d(TAG, "Status atualizado: $status")
    }
    
    private fun onDeviceSelected(device: android.bluetooth.BluetoothDevice) {
        Log.d(TAG, "Dispositivo selecionado: ${device.name}")
        Log.d(TAG, "Estado de emparelhamento: ${device.bondState}")
        
        when (device.bondState) {
            android.bluetooth.BluetoothDevice.BOND_BONDED -> {
                // Dispositivo já emparelhado - conectar
                Log.d(TAG, "Dispositivo já emparelhado - conectando...")
                connectToDevice(device)
            }
            android.bluetooth.BluetoothDevice.BOND_BONDING -> {
                // Dispositivo está sendo emparelhado
                Toast.makeText(this, "Dispositivo está sendo emparelhado...", Toast.LENGTH_SHORT).show()
            }
            else -> {
                // Dispositivo não emparelhado - emparelhar primeiro
                Toast.makeText(this, "Emparelhando com ${device.name}...", Toast.LENGTH_SHORT).show()
                pairDevice(device)
            }
        }
    }
    
    private fun pairDevice(device: android.bluetooth.BluetoothDevice) {
        try {
            if (device.createBond()) {
                Log.d(TAG, "Solicitação de emparelhamento enviada para ${device.name}")
                Toast.makeText(this, "Solicitando emparelhamento...", Toast.LENGTH_SHORT).show()
            } else {
                Log.e(TAG, "Falha ao solicitar emparelhamento com ${device.name}")
                Toast.makeText(this, "Falha ao emparelhar", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao emparelhar: ${e.message}")
            Toast.makeText(this, "Erro ao emparelhar: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun connectToDevice(device: android.bluetooth.BluetoothDevice) {
        Log.d(TAG, "=== INICIANDO CONEXÃO ===")
        Log.d(TAG, "Dispositivo: ${device.name} (${device.address})")
        Toast.makeText(this, "Conectando a ${device.name}...", Toast.LENGTH_SHORT).show()
        updateScanStatus("Conectando a ${device.name}...")
        
        // Testar se a conexão é possível (sem conectar efetivamente)
        Log.d(TAG, "Testando se a conexão é possível...")
        
        try {
            // Tentar criar um socket temporário para verificar se é possível
            val testSocket = device.createRfcommSocketToServiceRecord(
                UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
            )
            
            // Tentar conectar brevemente para verificar se funciona
            testSocket.connect()
            
            if (testSocket.isConnected) {
                Log.d(TAG, "✅ Teste de conexão bem-sucedido, retornando resultado")
                
                // Fechar socket de teste
                testSocket.close()
                
                // Retornar resultado para MainActivity
                val resultIntent = Intent().apply {
                    putExtra(EXTRA_DEVICE_ADDRESS, device.address)
                    putExtra(EXTRA_DEVICE_NAME, device.name)
                }
                setResult(RESULT_OK, resultIntent)
                finish()
                
            } else {
                Log.e(TAG, "❌ Teste de conexão falhou")
                Toast.makeText(this, "Falha no teste de conexão", Toast.LENGTH_LONG).show()
                updateScanStatus("Falha no teste de conexão")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro no teste de conexão: ${e.message}")
            Toast.makeText(this, "Erro no teste de conexão: ${e.message}", Toast.LENGTH_LONG).show()
            updateScanStatus("Erro no teste de conexão")
        }
    }
    
    private fun updateDeviceList() {
        val allDevices = deviceManager.getDiscoveredDevices() + deviceManager.getPairedDevices()
        deviceAdapter.updateDevices(allDevices.distinctBy { it.address })
    }
    
    override fun onResume() {
        super.onResume()
        updateDeviceList()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(pairingReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao desregistrar pairingReceiver: ${e.message}")
        }
        
        deviceManager.cleanup()
    }
}
