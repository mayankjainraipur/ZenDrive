package com.example.zendrive

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EventAdapter : ListAdapter<VehicleEvent, EventAdapter.VH>(DIFF) {

    var onEventClick: ((VehicleEvent) -> Unit)? = null

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<VehicleEvent>() {
            override fun areItemsTheSame(a: VehicleEvent, b: VehicleEvent) = a.id == b.id
            override fun areContentsTheSame(a: VehicleEvent, b: VehicleEvent) = a == b
        }

        private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val type: TextView = view.findViewById(R.id.tvEventType)
        val date: TextView = view.findViewById(R.id.tvEventDate)
        val title: TextView = view.findViewById(R.id.tvEventTitle)
        val cost: TextView = view.findViewById(R.id.tvEventCost)
        val odometer: TextView = view.findViewById(R.id.tvEventOdometer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_event, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val event = getItem(position)

        holder.type.text = event.eventType.uppercase()
        holder.date.text = dateFormat.format(Date(event.date))
        holder.title.text = event.title

        if (event.cost != null && event.cost > 0) {
            holder.cost.visibility = View.VISIBLE
            holder.cost.text = "₹${String.format("%.0f", event.cost)}"
        } else {
            holder.cost.visibility = View.GONE
        }

        if (event.odometer != null && event.odometer > 0) {
            holder.odometer.visibility = View.VISIBLE
            holder.odometer.text = "${String.format("%.0f", event.odometer)} km"
        } else {
            holder.odometer.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                onEventClick?.invoke(getItem(pos))
            }
        }
    }
}
