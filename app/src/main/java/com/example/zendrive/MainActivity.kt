package com.example.zendrive

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: LogViewModel
    private lateinit var adapter: VehicleAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val db = AppDatabase.getInstance(this)
        val factory = ViewModelFactory(db.vehicleDao(), db.vehicleEventDao(), db.eventMetaDao())
        viewModel = ViewModelProvider(this, factory)[LogViewModel::class.java]

        val recycler = findViewById<RecyclerView>(R.id.recyclerVehicles)
        val emptyState = findViewById<LinearLayout>(R.id.emptyState)
        val fab = findViewById<FloatingActionButton>(R.id.fabAddVehicle)

        adapter = VehicleAdapter { vehicle ->
            val intent = Intent(this, VehicleDetailActivity::class.java)
            intent.putExtra("vehicleId", vehicle.id)
            startActivity(intent)
        }

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        lifecycleScope.launch {
            viewModel.vehicles.collectLatest { vehicles ->
                adapter.submitList(vehicles)
                emptyState.visibility = if (vehicles.isEmpty()) View.VISIBLE else View.GONE
                recycler.visibility = if (vehicles.isEmpty()) View.GONE else View.VISIBLE
            }
        }

        fab.setOnClickListener {
            startActivity(Intent(this, AddVehicleActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.fetchVehicles()
    }
}
