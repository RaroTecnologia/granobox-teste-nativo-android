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
        addToLog("=== RESULTADO DO LAUNCHER ===")
        addToLog("resultCode: ${result.resultCode}")
        addToLog("data: ${result.data}")
        
        if (result.resultCode == RESULT_OK) {
            val deviceAddress = result.data?.getStringExtra(BluetoothActivity.EXTRA_DEVICE_ADDRESS)
            val deviceName = result.data?.getStringExtra(BluetoothActivity.EXTRA_DEVICE_NAME)
            
            addToLog("deviceAddress: $deviceAddress")
            addToLog("deviceName: $deviceName")
            
            if (deviceAddress != null && deviceName != null) {
                addToLog("✅ Dados válidos recebidos, definindo currentDevice...")
                currentDevice = deviceAddress
                addToLog("currentDevice definido como: $currentDevice")
                addToLog("✓ Dispositivo selecionado: $deviceName ($deviceAddress)")
                
                // A BluetoothActivity já testou a conexão, agora vamos conectar na MainActivity
                addToLog("BluetoothActivity testou conexão com sucesso, conectando na MainActivity...")
                
                val bluetoothManager = thermalBluetoothManager
                bluetoothManager.connectToDevice(deviceAddress) { success, error ->
                    runOnUiThread {
                        if (success) {
                            addToLog("✅ Conectado com sucesso na MainActivity a $deviceName")
                            updateConnectionStatus(true)
                        } else {
                            addToLog("❌ Falha na conexão na MainActivity: $error")
                            Toast.makeText(this, "Falha na conexão: $error", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } else {
                addToLog("❌ Dados inválidos recebidos")
                addToLog("deviceAddress é null: ${deviceAddress == null}")
                addToLog("deviceName é null: ${deviceName == null}")
            }
        } else {
            addToLog("❌ Seleção de dispositivo cancelada ou falhou")
            addToLog("resultCode: ${result.resultCode}")
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
        
        // Botão testar conexão
        binding.btnTestConnection.setOnClickListener {
            testConnection()
        }
        
        // Botão descobrir UUIDs
        binding.btnDiscoverUUIDs.setOnClickListener { discoverUUIDs() }
        binding.btnStatus.setOnClickListener { showDeviceStatus() }
        binding.btnTestSimple.setOnClickListener { testSimpleCPCL() }
        binding.btnTestText.setOnClickListener { testTextCPCL() }
        binding.btnTestSimpleLabel.setOnClickListener { testSimpleLabel60x60() }
        binding.btnUltraSimple.setOnClickListener { testUltraSimpleCPCL() }
        binding.btnPrintOnly.setOnClickListener { testPrintOnlyCPCL() }
        binding.btnFormOnly.setOnClickListener { testFormOnlyCPCL() }
        binding.btnCaptureCPCL.setOnClickListener { captureCPCLCommands() }
        binding.btnOpenLabelStyle.setOnClickListener { testOpenLabelStyleCPCL() }
        binding.btnUniversal.setOnClickListener { testUniversalCPCL() }
        binding.btnInitialized.setOnClickListener { testInitializedCPCL() }
        binding.btnReset.setOnClickListener { testResetCPCL() }
        binding.btn60x60Universal.setOnClickListener { test60x60UniversalCPCL() }
        
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
        addToLog("=== UPDATE CONNECTION STATUS ===")
        addToLog("isConnected: $isConnected")
        addToLog("currentDevice antes: $currentDevice")
        
        if (isConnected) {
            binding.tvBluetoothStatus.text = "Conectado"
            binding.tvBluetoothStatus.setTextColor(ContextCompat.getColor(this, R.color.success))
            binding.btnConnect.isEnabled = false
            binding.btnDisconnect.isEnabled = true
            addToLog("✅ Dispositivo conectado")
            addToLog("currentDevice após conexão: $currentDevice")
        } else {
            binding.tvBluetoothStatus.text = "Desconectado"
            binding.tvBluetoothStatus.setTextColor(ContextCompat.getColor(this, R.color.error))
            binding.btnConnect.isEnabled = true
            binding.btnDisconnect.isEnabled = false
            addToLog("⚠️ Dispositivo desconectado")
            addToLog("currentDevice mantido como: $currentDevice")
            addToLog("✗ Dispositivo desconectado (mas ainda selecionado)")
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
    
    private fun testConnection() {
        val bluetoothManager = thermalBluetoothManager
        
        addToLog("=== TESTANDO CONEXÃO ===")
        addToLog(bluetoothManager.getConnectionStatus())
        
        bluetoothManager.testConnection { success ->
            runOnUiThread {
                if (success) {
                    addToLog("✓ Teste de conexão: SUCESSO")
                    Toast.makeText(this, "Conexão funcionando!", Toast.LENGTH_SHORT).show()
                } else {
                    addToLog("✗ Teste de conexão: FALHOU")
                    Toast.makeText(this, "Conexão falhou!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun discoverUUIDs() {
        addToLog("=== DESCOBRINDO UUIDs ===")
        addToLog("currentDevice: $currentDevice")
        addToLog("thermalBluetoothManager.isConnected(): ${thermalBluetoothManager.isConnected()}")
        
        if (currentDevice == null) {
            addToLog("❌ Nenhum dispositivo selecionado")
            addToLog("💡 Dica: Conecte primeiro em um dispositivo Bluetooth")
            Toast.makeText(this, "Nenhum dispositivo selecionado", Toast.LENGTH_SHORT).show()
            return
        }
        
        val bluetoothManager = thermalBluetoothManager
        val bluetoothAdapter = bluetoothAdapter
        
        if (bluetoothAdapter != null) {
            val device = bluetoothAdapter.getRemoteDevice(currentDevice!!)
            
            addToLog("Dispositivo: ${device.name} (${device.address})")
            
            bluetoothManager.discoverDeviceUUIDs(device) { uuids ->
                runOnUiThread {
                    if (uuids.isNotEmpty()) {
                        addToLog("✅ UUIDs descobertos:")
                        uuids.forEach { uuid ->
                            addToLog("  - $uuid")
                        }
                        
                        // Se encontrou UUIDs diferentes do padrão, usar o primeiro
                        val firstUUID = uuids.first()
                        if (firstUUID.toString() != "00001101-0000-1000-8000-00805F9B34FB") {
                            addToLog("🎯 UUID alternativo encontrado! Tente conectar novamente.")
                        }
                        
                    } else {
                        addToLog("❌ Nenhum UUID suportado encontrado")
                        addToLog("💡 Tente emparelhar o dispositivo primeiro")
                    }
                }
            }
        } else {
            addToLog("❌ Bluetooth adapter não disponível")
        }
    }
    
    private fun showDeviceStatus() {
        addToLog("=== STATUS DO DISPOSITIVO ===")
        addToLog("Método chamado em: ${System.currentTimeMillis()}")
        addToLog("currentDevice: $currentDevice")
        addToLog("thermalBluetoothManager.isConnected(): ${thermalBluetoothManager.isConnected()}")
        addToLog("thermalBluetoothManager.getConnectionStatus():")
        addToLog(thermalBluetoothManager.getConnectionStatus())
        
        if (currentDevice != null) {
            addToLog("✅ Dispositivo selecionado: $currentDevice")
        } else {
            addToLog("❌ Nenhum dispositivo selecionado")
            addToLog("💡 Dica: Vá em 'Configurações Bluetooth' e selecione um dispositivo")
        }
        
        addToLog("=== FIM DO STATUS ===")
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

    private fun testSimpleCPCL() {
        val bluetoothManager = thermalBluetoothManager
        if (!bluetoothManager.isConnected()) {
            Toast.makeText(this, "Nenhum dispositivo conectado", Toast.LENGTH_SHORT).show()
            return
        }

        addToLog("=== TESTE CPCL SIMPLES ===")
        bluetoothManager.printSimpleTest { success, error ->
            runOnUiThread {
                if (success) {
                    addToLog("✅ Teste simples enviado com sucesso")
                    Toast.makeText(this, "Teste simples enviado!", Toast.LENGTH_SHORT).show()
                } else {
                    addToLog("❌ Falha no teste simples: $error")
                    Toast.makeText(this, "Falha no teste: $error", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun testTextCPCL() {
        val bluetoothManager = thermalBluetoothManager
        if (!bluetoothManager.isConnected()) {
            Toast.makeText(this, "Nenhum dispositivo conectado", Toast.LENGTH_SHORT).show()
            return
        }

        val testText = "TESTE123"
        addToLog("=== TESTE CPCL TEXTO: $testText ===")
        bluetoothManager.printTextOnlyTest(testText) { success, error ->
            runOnUiThread {
                if (success) {
                    addToLog("✅ Teste de texto enviado com sucesso")
                    Toast.makeText(this, "Texto enviado!", Toast.LENGTH_SHORT).show()
                } else {
                    addToLog("❌ Falha no teste de texto: $error")
                    Toast.makeText(this, "Falha no teste: $error", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun testSimpleLabel60x60() {
        val bluetoothManager = thermalBluetoothManager
        if (!bluetoothManager.isConnected()) {
            Toast.makeText(this, "Nenhum dispositivo conectado", Toast.LENGTH_SHORT).show()
            return
        }

        addToLog("=== TESTE ETIQUETA SIMPLES 60x60 ===")
        bluetoothManager.printSimpleLabel60x60 { success, error ->
            runOnUiThread {
                if (success) {
                    addToLog("✅ Etiqueta simples enviada com sucesso")
                    Toast.makeText(this, "Etiqueta enviada!", Toast.LENGTH_SHORT).show()
                } else {
                    addToLog("❌ Falha na etiqueta simples: $error")
                    Toast.makeText(this, "Falha na etiqueta: $error", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun testUltraSimpleCPCL() {
        val bluetoothManager = thermalBluetoothManager
        if (!bluetoothManager.isConnected()) {
            Toast.makeText(this, "Nenhum dispositivo conectado", Toast.LENGTH_SHORT).show()
            return
        }

        addToLog("=== TESTE ULTRA SIMPLES CPCL ===")
        bluetoothManager.printUltraSimpleTest { success, error ->
            runOnUiThread {
                if (success) {
                    addToLog("✅ Teste ultra simples enviado com sucesso")
                    Toast.makeText(this, "Ultra simples enviado!", Toast.LENGTH_SHORT).show()
                } else {
                    addToLog("❌ Falha no teste ultra simples: $error")
                    Toast.makeText(this, "Falha no teste: $error", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun testPrintOnlyCPCL() {
        val bluetoothManager = thermalBluetoothManager
        if (!bluetoothManager.isConnected()) {
            Toast.makeText(this, "Nenhum dispositivo conectado", Toast.LENGTH_SHORT).show()
            return
        }

        addToLog("=== TESTE APENAS PRINT ===")
        bluetoothManager.printPrintOnlyTest { success, error ->
            runOnUiThread {
                if (success) {
                    addToLog("✅ Comando PRINT enviado com sucesso")
                    Toast.makeText(this, "PRINT enviado!", Toast.LENGTH_SHORT).show()
                } else {
                    addToLog("❌ Falha no comando PRINT: $error")
                    Toast.makeText(this, "Falha no PRINT: $error", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun testFormOnlyCPCL() {
        val bluetoothManager = thermalBluetoothManager
        if (!bluetoothManager.isConnected()) {
            Toast.makeText(this, "Nenhum dispositivo conectado", Toast.LENGTH_SHORT).show()
            return
        }

        addToLog("=== TESTE APENAS FORM ===")
        bluetoothManager.printFormOnlyTest { success, error ->
            runOnUiThread {
                if (success) {
                    addToLog("✅ Comando FORM enviado com sucesso")
                    Toast.makeText(this, "FORM enviado!", Toast.LENGTH_SHORT).show()
                } else {
                    addToLog("❌ Falha no comando FORM: $error")
                    Toast.makeText(this, "Falha no FORM: $error", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun captureCPCLCommands() {
        val bluetoothManager = thermalBluetoothManager
        if (!bluetoothManager.isConnected()) {
            Toast.makeText(this, "Nenhum dispositivo conectado", Toast.LENGTH_SHORT).show()
            return
        }

        addToLog("=== CAPTURANDO COMANDOS CPCL ===")
        bluetoothManager.captureCPCLCommands { success, error ->
            runOnUiThread {
                if (success) {
                    addToLog("✅ Comandos CPCL capturados com sucesso")
                    Toast.makeText(this, "Comandos CPCL capturados!", Toast.LENGTH_SHORT).show()
                } else {
                    addToLog("❌ Falha ao capturar comandos CPCL: $error")
                    Toast.makeText(this, "Falha ao capturar comandos: $error", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun testOpenLabelStyleCPCL() {
        val bluetoothManager = thermalBluetoothManager
        if (!bluetoothManager.isConnected()) {
            Toast.makeText(this, "Nenhum dispositivo conectado", Toast.LENGTH_SHORT).show()
            return
        }

        addToLog("=== TESTE ESTILO OPENLABEL ===")
        bluetoothManager.testOpenLabelStyleCPCL { success, error ->
            runOnUiThread {
                if (success) {
                    addToLog("✅ Estilo da etiqueta aplicado com sucesso")
                    Toast.makeText(this, "Estilo da etiqueta aplicado!", Toast.LENGTH_SHORT).show()
                } else {
                    addToLog("❌ Falha ao aplicar estilo da etiqueta: $error")
                    Toast.makeText(this, "Falha ao aplicar estilo: $error", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun testUniversalCPCL() {
        val bluetoothManager = thermalBluetoothManager
        if (!bluetoothManager.isConnected()) {
            Toast.makeText(this, "Nenhum dispositivo conectado", Toast.LENGTH_SHORT).show()
            return
        }

        addToLog("=== TESTE CPCL UNIVERSAL ===")
        bluetoothManager.printUniversalTest { success, error ->
            runOnUiThread {
                if (success) {
                    addToLog("✅ Teste universal enviado com sucesso")
                    Toast.makeText(this, "Teste universal enviado!", Toast.LENGTH_SHORT).show()
                } else {
                    addToLog("❌ Falha no teste universal: $error")
                    Toast.makeText(this, "Falha no teste: $error", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun testInitializedCPCL() {
        val bluetoothManager = thermalBluetoothManager
        if (!bluetoothManager.isConnected()) {
            Toast.makeText(this, "Nenhum dispositivo conectado", Toast.LENGTH_SHORT).show()
            return
        }

        addToLog("=== TESTE CPCL INITIALIZED ===")
        bluetoothManager.printInitializedTest { success, error ->
            runOnUiThread {
                if (success) {
                    addToLog("✅ Teste initialized enviado com sucesso")
                    Toast.makeText(this, "Teste initialized enviado!", Toast.LENGTH_SHORT).show()
                } else {
                    addToLog("❌ Falha no teste initialized: $error")
                    Toast.makeText(this, "Falha no teste: $error", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun testResetCPCL() {
        val bluetoothManager = thermalBluetoothManager
        if (!bluetoothManager.isConnected()) {
            Toast.makeText(this, "Nenhum dispositivo conectado", Toast.LENGTH_SHORT).show()
            return
        }

        addToLog("=== TESTE CPCL RESET ===")
        bluetoothManager.printResetTest { success, error ->
            runOnUiThread {
                if (success) {
                    addToLog("✅ Teste reset enviado com sucesso")
                    Toast.makeText(this, "Teste reset enviado!", Toast.LENGTH_SHORT).show()
                } else {
                    addToLog("❌ Falha no teste reset: $error")
                    Toast.makeText(this, "Falha no teste: $error", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun test60x60UniversalCPCL() {
        val bluetoothManager = thermalBluetoothManager
        if (!bluetoothManager.isConnected()) {
            Toast.makeText(this, "Nenhum dispositivo conectado", Toast.LENGTH_SHORT).show()
            return
        }

        addToLog("=== TESTE ETIQUETA 60x60 UNIVERSAL ===")
        bluetoothManager.print60x60UniversalLabel { success, error ->
            runOnUiThread {
                if (success) {
                    addToLog("✅ Etiqueta 60x60 universal enviada com sucesso")
                    Toast.makeText(this, "Etiqueta 60x60 universal enviada!", Toast.LENGTH_SHORT).show()
                } else {
                    addToLog("❌ Falha na etiqueta 60x60 universal: $error")
                    Toast.makeText(this, "Falha na etiqueta: $error", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
