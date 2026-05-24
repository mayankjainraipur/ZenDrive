package com.example.zendrive

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import kotlinx.coroutines.launch

class RemindersFragment : Fragment() {

    private lateinit var adapter: ReminderAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var actvVehicleFilter: AutoCompleteTextView

    private var vehicles: List<Vehicle> = emptyList()
    private var selectedVehicleId: Int = -1

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_reminders, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val db = AppDatabase.getInstance(requireContext())

        recycler = view.findViewById(R.id.recyclerReminders)
        emptyState = view.findViewById(R.id.emptyState)
        actvVehicleFilter = view.findViewById(R.id.actvVehicleFilter)

        val fab = view.findViewById<ExtendedFloatingActionButton>(R.id.fabAddReminder)

        adapter = ReminderAdapter(
            onItemClick = { reminder ->
                val intent = Intent(requireContext(), AddReminderActivity::class.java)
                intent.putExtra("reminderId", reminder.id)
                startActivity(intent)
            },
            onItemLongClick = { reminder ->
                showDeleteDialog(db, reminder)
                true
            },
            onCompletionToggle = { reminder, isChecked ->
                toggleCompletion(db, reminder, isChecked)
            }
        )

        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        fab.setOnClickListener {
            val intent = Intent(requireContext(), AddReminderActivity::class.java)
            if (selectedVehicleId > 0) {
                intent.putExtra("vehicleId", selectedVehicleId)
            }
            startActivity(intent)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vehicles = db.vehicleDao().getAllVehicles()
            if (vehicles.isEmpty()) return@launch

            val vehicleNames = vehicles.map { it.name }
            actvVehicleFilter.setAdapter(
                ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, vehicleNames)
            )

            if (selectedVehicleId <= 0) {
                selectedVehicleId = vehicles.first().id
                actvVehicleFilter.setText(vehicles.first().name, false)
            }

            loadReminders(db)
        }

        actvVehicleFilter.setOnItemClickListener { _, _, position, _ ->
            selectedVehicleId = vehicles[position].id
            viewLifecycleOwner.lifecycleScope.launch { loadReminders(db) }
        }
    }

    override fun onResume() {
        super.onResume()
        if (selectedVehicleId > 0) {
            val db = AppDatabase.getInstance(requireContext())
            viewLifecycleOwner.lifecycleScope.launch { loadReminders(db) }
        }
    }

    private suspend fun loadReminders(db: AppDatabase) {
        if (selectedVehicleId <= 0) return
        val reminders = db.reminderDao().getRemindersForVehicle(selectedVehicleId)
        val sorted = reminders.sortedWith(
            compareBy<Reminder> { it.isCompleted }.thenBy { it.dueAt }
        )
        adapter.submitList(sorted)
        emptyState.visibility = if (sorted.isEmpty()) View.VISIBLE else View.GONE
        recycler.visibility = if (sorted.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun toggleCompletion(db: AppDatabase, reminder: Reminder, isChecked: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            val updated = reminder.copy(
                isCompleted = isChecked,
                completedAt = if (isChecked) System.currentTimeMillis() else null,
                updatedAt = System.currentTimeMillis()
            )
            db.reminderDao().update(updated)
            loadReminders(db)
            if (isChecked) {
                Toast.makeText(requireContext(), R.string.reminder_completed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showDeleteDialog(db: AppDatabase, reminder: Reminder) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.action_delete)
            .setMessage("Delete \"${reminder.title}\"?")
            .setPositiveButton(R.string.action_delete) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    db.reminderDao().delete(reminder)
                    loadReminders(db)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
