package com.example.zendrive

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReminderAdapter(
    private val onItemClick: (Reminder) -> Unit,
    private val onItemLongClick: (Reminder) -> Boolean,
    private val onCompletionToggle: (Reminder, Boolean) -> Unit
) : ListAdapter<Reminder, ReminderAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Reminder>() {
            override fun areItemsTheSame(a: Reminder, b: Reminder) = a.id == b.id
            override fun areContentsTheSame(a: Reminder, b: Reminder) = a == b
        }

        private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val cbCompleted: CheckBox = view.findViewById(R.id.cbCompleted)
        val tvType: TextView = view.findViewById(R.id.tvReminderType)
        val tvOverdue: TextView = view.findViewById(R.id.tvOverdue)
        val tvDueDate: TextView = view.findViewById(R.id.tvDueDate)
        val tvTitle: TextView = view.findViewById(R.id.tvReminderTitle)
        val tvDescription: TextView = view.findViewById(R.id.tvReminderDescription)
        val tvRepeatRule: TextView = view.findViewById(R.id.tvRepeatRule)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_reminder, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val reminder = getItem(position)

        holder.cbCompleted.setOnCheckedChangeListener(null)
        holder.cbCompleted.isChecked = reminder.isCompleted

        holder.tvType.text = reminder.reminderType.replace('_', ' ').uppercase()
        holder.tvDueDate.text = dateFormat.format(Date(reminder.dueAt))
        holder.tvTitle.text = reminder.title

        if (reminder.isCompleted) {
            holder.tvTitle.paintFlags = holder.tvTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            holder.tvTitle.alpha = 0.5f
            holder.tvOverdue.visibility = View.GONE
        } else {
            holder.tvTitle.paintFlags = holder.tvTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            holder.tvTitle.alpha = 1f
            val isOverdue = reminder.dueAt < System.currentTimeMillis()
            holder.tvOverdue.visibility = if (isOverdue) View.VISIBLE else View.GONE
        }

        if (!reminder.description.isNullOrBlank()) {
            holder.tvDescription.text = reminder.description
            holder.tvDescription.visibility = View.VISIBLE
        } else {
            holder.tvDescription.visibility = View.GONE
        }

        if (reminder.repeatRule != "none") {
            holder.tvRepeatRule.text = "Repeats ${reminder.repeatRule}"
            holder.tvRepeatRule.visibility = View.VISIBLE
        } else {
            holder.tvRepeatRule.visibility = View.GONE
        }

        holder.cbCompleted.setOnCheckedChangeListener { _, isChecked ->
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                onCompletionToggle(getItem(pos), isChecked)
            }
        }

        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                onItemClick(getItem(pos))
            }
        }

        holder.itemView.setOnLongClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                onItemLongClick(getItem(pos))
            } else false
        }
    }
}
