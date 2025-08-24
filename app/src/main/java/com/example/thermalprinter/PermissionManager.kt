package com.example.thermalprinter

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat

class PermissionManager(private val context: Context) {
    
    companion object {
        private const val TAG = "PermissionManager"
    }
    
    // Callbacks
    var onBluetoothPermissionsResult: ((Boolean) -> Unit)? = null
    var onLocationPermissionsResult: ((Boolean) -> Unit)? = null
    
    private val bluetoothPermissions = arrayOf(
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_SCAN
    )
    
    private val locationPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    
    fun checkBluetoothPermissions(): Boolean {
        return bluetoothPermissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    fun checkLocationPermissions(): Boolean {
        return locationPermissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    fun hasAllRequiredPermissions(): Boolean {
        return checkBluetoothPermissions() && checkLocationPermissions()
    }
    
    fun requestBluetoothPermissions() {
        if (context is Activity) {
            val launcher = context.registerForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                val allGranted = permissions.values.all { it }
                onBluetoothPermissionsResult?.invoke(allGranted)
                
                if (!allGranted) {
                    showBluetoothPermissionRationale()
                }
            }
            
            launcher.launch(bluetoothPermissions)
        }
    }
    
    fun requestLocationPermissions() {
        if (context is Activity) {
            val launcher = context.registerForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                val allGranted = permissions.values.all { it }
                onLocationPermissionsResult?.invoke(allGranted)
                
                if (!allGranted) {
                    showLocationPermissionRationale()
                }
            }
            
            launcher.launch(locationPermissions)
        }
    }
    
    private fun showBluetoothPermissionRationale() {
        if (context is Activity) {
            AlertDialog.Builder(context)
                .setTitle("Permissão Bluetooth Necessária")
                .setMessage("Este app precisa de permissão Bluetooth para conectar com impressoras térmicas.")
                .setPositiveButton("Conceder") { _, _ ->
                    requestBluetoothPermissions()
                }
                .setNegativeButton("Cancelar") { _, _ ->
                    onBluetoothPermissionsResult?.invoke(false)
                }
                .show()
        }
    }
    
    private fun showLocationPermissionRationale() {
        if (context is Activity) {
            AlertDialog.Builder(context)
                .setTitle("Permissão de Localização Necessária")
                .setMessage("O Bluetooth precisa de permissão de localização para funcionar corretamente.")
                .setPositiveButton("Conceder") { _, _ ->
                    requestLocationPermissions()
                }
                .setNegativeButton("Cancelar") { _, _ ->
                    onLocationPermissionsResult?.invoke(false)
                }
                .show()
        }
    }
    
    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        context.startActivity(intent)
    }
}
