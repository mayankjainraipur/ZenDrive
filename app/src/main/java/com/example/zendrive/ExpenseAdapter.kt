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

class ExpenseAdapter(
    private val currencyCode: String = "INR"
) : ListAdapter<VehicleEvent, ExpenseAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<VehicleEvent>() {
            override fun areItemsTheSame(a: VehicleEvent, b: VehicleEvent) = a.id == b.id
            override fun areContentsTheSame(a: VehicleEvent, b: VehicleEvent) = a == b
        }
        private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: TextView = view.findViewById(R.id.tvExpenseIcon)
        val title: TextView = view.findViewById(R.id.tvExpenseTitle)
        val type: TextView = view.findViewById(R.id.tvExpenseType)
        val date: TextView = view.findViewById(R.id.tvExpenseDate)
        val cost: TextView = view.findViewById(R.id.tvExpenseCost)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_expense, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val event = getItem(position)

        holder.icon.text = when (event.eventType.lowercase()) {
            "fuel" -> "\u26FD" // fuel pump
            "service" -> "\u2699\uFE0F" // gear
            "repair" -> "\uD83D\uDD27" // wrench
            "insurance" -> "\uD83D\uDCDC" // document
            "tax" -> "\uD83D\uDCB8" // money
            else -> "\uD83D\uDED2" // shopping cart
        }

        holder.title.text = event.title
        holder.type.text = event.eventType.replaceFirstChar { it.uppercase() }
        holder.date.text = dateFormat.format(Date(event.date))

        val costValue = event.cost ?: 0.0
        holder.cost.text = "$currencyCode ${String.format("%,.2f", costValue)}"
    }
}
