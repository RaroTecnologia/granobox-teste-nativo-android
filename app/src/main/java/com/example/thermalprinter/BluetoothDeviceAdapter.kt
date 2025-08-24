package com.example.thermalprinter

import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.thermalprinter.databinding.ItemBluetoothDeviceBinding

class BluetoothDeviceAdapter : RecyclerView.Adapter<BluetoothDeviceAdapter.DeviceViewHolder>() {
    
    private val devices = mutableListOf<BluetoothDevice>()
    var onDeviceSelected: ((BluetoothDevice) -> Unit)? = null
    
    inner class DeviceViewHolder(private val binding: ItemBluetoothDeviceBinding) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(device: BluetoothDevice) {
            binding.tvDeviceName.text = device.name ?: "Dispositivo Desconhecido"
            binding.tvDeviceAddress.text = device.address
            
            // Determinar tipo do dispositivo
            val deviceType = when (device.type) {
                BluetoothDevice.DEVICE_TYPE_CLASSIC -> "Clássico"
                BluetoothDevice.DEVICE_TYPE_LE -> "BLE"
                BluetoothDevice.DEVICE_TYPE_DUAL -> "Dual"
                else -> "Desconhecido"
            }
            binding.tvDeviceType.text = "Tipo: $deviceType"
            
            // Determinar estado de emparelhamento
            val bondState = device.bondState
            val actionText = when (bondState) {
                BluetoothDevice.BOND_BONDED -> "Conectar"
                BluetoothDevice.BOND_BONDING -> "Emparelhando..."
                else -> "Emparelhar"
            }
            binding.btnAction.text = actionText
            
            // Configurar botão baseado no estado
            binding.btnAction.isEnabled = bondState != BluetoothDevice.BOND_BONDING
            
            // Configurar click listener
            binding.btnAction.setOnClickListener {
                onDeviceSelected?.invoke(device)
            }
            
            // Configurar click no item inteiro
            binding.root.setOnClickListener {
                onDeviceSelected?.invoke(device)
            }
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemBluetoothDeviceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DeviceViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(devices[position])
    }
    
    override fun getItemCount(): Int = devices.size
    
    fun updateDevices(newDevices: List<BluetoothDevice>) {
        devices.clear()
        devices.addAll(newDevices)
        notifyDataSetChanged()
    }
    
    fun addDevice(device: BluetoothDevice) {
        if (!devices.contains(device)) {
            devices.add(device)
            notifyItemInserted(devices.size - 1)
        }
    }
    
    fun clearDevices() {
        devices.clear()
        notifyDataSetChanged()
    }
}
