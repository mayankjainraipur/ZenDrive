package com.example.zendrive

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class VehicleAdapter(
    private val onClick: (Vehicle) -> Unit
) : ListAdapter<Vehicle, VehicleAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Vehicle>() {
            override fun areItemsTheSame(a: Vehicle, b: Vehicle) = a.id == b.id
            override fun areContentsTheSame(a: Vehicle, b: Vehicle) = a == b
        }
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: TextView = view.findViewById(R.id.tvVehicleIcon)
        val name: TextView = view.findViewById(R.id.tvVehicleName)
        val number: TextView = view.findViewById(R.id.tvVehicleNumber)
        val model: TextView = view.findViewById(R.id.tvVehicleModel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_vehicle, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val vehicle = getItem(position)

        holder.icon.text = when (vehicle.type.lowercase()) {
            "car" -> "🚗"
            "bike" -> "🏍️"
            "truck" -> "🚛"
            "auto" -> "🛺"
            "bus" -> "🚌"
            else -> "🚙"
        }

        holder.name.text = vehicle.name
        holder.number.text = vehicle.vehicleNumber
        holder.model.text = "${vehicle.brand} ${vehicle.model} · ${vehicle.year}"

        holder.itemView.setOnClickListener { onClick(vehicle) }
    }
}
