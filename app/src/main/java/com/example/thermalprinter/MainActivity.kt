package com.example.thermalprinter

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager as AndroidBluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.thermalprinter.databinding.ActivityMainBinding
import com.example.thermalprinter.BluetoothManager as ThermalBluetoothManager

class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_ENABLE_BLUETOOTH = 1
    }
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var thermalBluetoothManager: ThermalBluetoothManager
    private lateinit var permissionManager: PermissionManager
    
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var currentDevice: String? = null
    
    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Log.d(TAG, "Bluetooth habilitado pelo usuário")
            updateBluetoothStatus()
        } else {
            Log.d(TAG, "Usuário cancelou habilitação do Bluetooth")
            Toast.makeText(this, "Bluetooth é necessário para este app", Toast.LENGTH_LONG).show()
        }
    }
    
    private val bluetoothDeviceLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val deviceAddress = result.data?.getStringExtra(BluetoothActivity.EXTRA_DEVICE_ADDRESS)
            val deviceName = result.data?.getStringExtra(BluetoothActivity.EXTRA_DEVICE_NAME)
            
            if (deviceAddress != null && deviceName != null) {
                currentDevice = deviceAddress
                addToLog("✓ Dispositivo selecionado: $deviceName ($deviceAddress)")
                
                // Conectar ao dispositivo usando o BluetoothManager
                val bluetoothManager = thermalBluetoothManager
                bluetoothManager.connectToDevice(deviceAddress) { success, error ->
                    runOnUiThread {
                        if (success) {
                            addToLog("✓ Conectado com sucesso a $deviceName")
                            updateConnectionStatus(true)
                        } else {
                            addToLog("✗ Falha na conexão: $error")
                            Toast.makeText(this, "Falha na conexão: $error", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        } else {
            addToLog("Seleção de dispositivo cancelada")
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        initializeBluetooth()
        initializeManagers()
        setupUI()
        checkPermissions()
    }
    
    private fun initializeBluetooth() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as AndroidBluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth não é suportado neste dispositivo", Toast.LENGTH_LONG).show()
            finish()
            return
        }
    }
    
    private fun initializeManagers() {
        thermalBluetoothManager = ThermalBluetoothManager(this)
        bluetoothAdapter?.let { adapter ->
            thermalBluetoothManager.initialize(adapter)
        }
        
        permissionManager = PermissionManager(this)
        
        // Configurar callbacks
        val bluetoothManager = thermalBluetoothManager
        bluetoothManager.onConnectionStateChanged = { isConnected ->
            runOnUiThread {
                updateConnectionStatus(isConnected)
            }
        }
        
        bluetoothManager.onPrintResult = { success, error ->
            runOnUiThread {
                if (success) {
                    addToLog("✓ Impressão realizada com sucesso")
                    Toast.makeText(this, "Impressão realizada com sucesso", Toast.LENGTH_SHORT).show()
                } else {
                    addToLog("✗ Erro na impressão: $error")
                    Toast.makeText(this, "Erro na impressão: $error", Toast.LENGTH_LONG).show()
                }
            }
        }
        

    }
    
    private fun setupUI() {
        // Botão conectar
        binding.btnConnect.setOnClickListener {
            if (currentDevice != null) {
                // Se já temos um dispositivo selecionado, conectar
                connectToCurrentDevice()
            } else {
                // Abrir tela de seleção de dispositivos
                openBluetoothDevices()
            }
        }
        
        // Botão desconectar
        binding.btnDisconnect.setOnClickListener {
            disconnectFromDevice()
        }
        
        // Botão configurações Bluetooth
        binding.btnBluetoothSettings.setOnClickListener {
            openBluetoothDevices()
        }
        
        // Botão impressão de teste
        binding.btnTestPrint.setOnClickListener {
            printTestPage()
        }
        
        // Botão imprimir
        binding.btnPrint.setOnClickListener {
            printCustomText()
        }
        
        // Botão testar etiqueta 60x60mm
        binding.btnTestLabel60x60.setOnClickListener {
            printTestLabel60x60()
        }
        
        updateBluetoothStatus()
    }
    
    private fun checkPermissions() {
        if (!permissionManager.checkBluetoothPermissions()) {
            addToLog("Solicitando permissões Bluetooth...")
            permissionManager.requestBluetoothPermissions()
        } else if (!permissionManager.checkLocationPermissions()) {
            addToLog("Solicitando permissões de localização...")
            permissionManager.requestLocationPermissions()
        } else {
            addToLog("✓ Todas as permissões concedidas")
        }
    }
    
    private fun checkLocationPermissions() {
        if (!permissionManager.checkLocationPermissions()) {
            addToLog("Solicitando permissões de localização...")
            permissionManager.requestLocationPermissions()
        }
    }
    
    private fun updateBluetoothStatus() {
        bluetoothAdapter?.let { adapter ->
            if (adapter.isEnabled) {
                binding.tvBluetoothStatus.text = "Habilitado"
                binding.tvBluetoothStatus.setTextColor(ContextCompat.getColor(this, R.color.success))
                binding.btnConnect.isEnabled = true
                binding.btnDisconnect.isEnabled = false
            } else {
                binding.tvBluetoothStatus.text = "Desabilitado"
                binding.tvBluetoothStatus.setTextColor(ContextCompat.getColor(this, R.color.error))
                binding.btnConnect.isEnabled = false
                binding.btnDisconnect.isEnabled = false
                
                // Solicitar habilitação do Bluetooth
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                enableBluetoothLauncher.launch(enableBtIntent)
            }
        }
    }
    
    private fun updateConnectionStatus(isConnected: Boolean) {
        if (isConnected) {
            binding.tvBluetoothStatus.text = "Conectado"
            binding.tvBluetoothStatus.setTextColor(ContextCompat.getColor(this, R.color.success))
            binding.btnConnect.isEnabled = false
            binding.btnDisconnect.isEnabled = true
            addToLog("✓ Dispositivo conectado")
        } else {
            binding.tvBluetoothStatus.text = "Desconectado"
            binding.tvBluetoothStatus.setTextColor(ContextCompat.getColor(this, R.color.error))
            binding.btnConnect.isEnabled = true
            binding.btnDisconnect.isEnabled = false
            currentDevice = null
            addToLog("✗ Dispositivo desconectado")
        }
    }
    
    private fun openBluetoothDevices() {
        val intent = Intent(this, BluetoothActivity::class.java)
        bluetoothDeviceLauncher.launch(intent)
    }
    
    private fun connectToCurrentDevice() {
        // Implementar conexão com dispositivo atual
        addToLog("Tentando conectar ao dispositivo atual...")
    }
    
    private fun disconnectFromDevice() {
        val bluetoothManager = thermalBluetoothManager
        bluetoothManager.disconnect()
        addToLog("Desconectando do dispositivo...")
    }
    
    private fun printTestPage() {
        val bluetoothManager = thermalBluetoothManager
        if (!bluetoothManager.isConnected()) {
            Toast.makeText(this, "Nenhum dispositivo conectado", Toast.LENGTH_SHORT).show()
            return
        }
        
        addToLog("Enviando página de teste...")
        bluetoothManager.printTestPage()
    }
    
    private fun printCustomText() {
        val bluetoothManager = thermalBluetoothManager
        if (!bluetoothManager.isConnected()) {
            Toast.makeText(this, "Nenhum dispositivo conectado", Toast.LENGTH_SHORT).show()
            return
        }
        
        val text = binding.etPrintText.text.toString()
        if (text.isBlank()) {
            Toast.makeText(this, "Digite algum texto para imprimir", Toast.LENGTH_SHORT).show()
            return
        }
        
        addToLog("Imprimindo texto personalizado...")
        bluetoothManager.printText(text)
    }
    
    private fun printTestLabel60x60() {
        val bluetoothManager = thermalBluetoothManager
        if (!bluetoothManager.isConnected()) {
            Toast.makeText(this, "Nenhum dispositivo conectado", Toast.LENGTH_SHORT).show()
            return
        }
        
        addToLog("Imprimindo etiqueta 60x60mm...")
        bluetoothManager.printLabel60x60(
            title = "TESTE",
            subtitle = "60x60mm",
            barcode = "12345",
            qrData = "TEST123"
        )
    }
    
    private fun addToLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date())
        val logEntry = "[$timestamp] $message\n"
        
        binding.tvLog.append(logEntry)
        
        // Scroll para o final (verificar se o layout existe)
        binding.tvLog.post {
            val layout = binding.tvLog.layout
            if (layout != null) {
                val scrollAmount = layout.getLineTop(binding.tvLog.lineCount) - binding.tvLog.height
                if (scrollAmount > 0) {
                    binding.tvLog.scrollTo(0, scrollAmount)
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        updateBluetoothStatus()
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            PermissionManager.REQUEST_BLUETOOTH_PERMISSIONS -> {
                val allGranted = grantResults.all { it == android.content.pm.PackageManager.PERMISSION_GRANTED }
                if (allGranted) {
                    addToLog("✓ Permissões Bluetooth concedidas")
                    checkLocationPermissions()
                } else {
                    addToLog("✗ Permissões Bluetooth negadas")
                    Toast.makeText(this, "Permissões Bluetooth são necessárias", Toast.LENGTH_LONG).show()
                }
            }
            PermissionManager.REQUEST_LOCATION_PERMISSIONS -> {
                val allGranted = grantResults.all { it == android.content.pm.PackageManager.PERMISSION_GRANTED }
                if (allGranted) {
                    addToLog("✓ Permissões de localização concedidas")
                    updateBluetoothStatus()
                } else {
                    addToLog("✗ Permissões de localização negadas")
                    Toast.makeText(this, "Permissões de localização são necessárias para Bluetooth", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        val bluetoothManager = thermalBluetoothManager
        bluetoothManager.cleanup()
    }
}
