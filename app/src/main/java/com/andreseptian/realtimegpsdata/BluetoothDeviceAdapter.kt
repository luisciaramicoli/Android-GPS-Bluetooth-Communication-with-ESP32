package com.andreseptian.realtimegpsdata

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.RecyclerView
import android.bluetooth.BluetoothDevice

class BluetoothDeviceAdapter(
    private val devices: List<BluetoothDevice>,
    private val onDeviceSelected: (BluetoothDevice) -> Unit
) : RecyclerView.Adapter<BluetoothDeviceAdapter.DeviceViewHolder>() {

    inner class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val deviceName: TextView = itemView.findViewById(R.id.tv_device_name)
        val deviceAddress: TextView = itemView.findViewById(R.id.tv_device_address)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_bluetooth_device, parent, false)
        return DeviceViewHolder(view)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = devices[position]

        // Periksa izin sebelum mengakses nama dan alamat perangkat
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    holder.itemView.context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                holder.deviceName.text = device.name ?: "Unknown Device"
                holder.deviceAddress.text = device.address
            } else {
                holder.deviceName.text = "Permission required"
                holder.deviceAddress.text = "N/A"
            }
        } else {
            holder.deviceName.text = device.name ?: "Unknown Device"
            holder.deviceAddress.text = device.address
        }

        holder.itemView.setOnClickListener {
            onDeviceSelected(device)
        }
    }

    override fun getItemCount(): Int {
        return devices.size
    }
}