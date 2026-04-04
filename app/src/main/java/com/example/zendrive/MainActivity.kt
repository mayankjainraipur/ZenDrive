package com.example.zendrive

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: LogViewModel
    private lateinit var adapter: VehicleAdapter

    private var allVehicles: List<Vehicle> = emptyList()
    private var searchQuery: String = ""
    private var searchExpanded: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val db = AppDatabase.getInstance(this)
        val factory = ViewModelFactory(db.vehicleDao(), db.vehicleEventDao(), db.eventMetaDao())
        viewModel = ViewModelProvider(this, factory)[LogViewModel::class.java]

        val recycler = findViewById<RecyclerView>(R.id.recyclerVehicles)
        val emptyState = findViewById<LinearLayout>(R.id.emptyState)
        val tvNoSearchMatches = findViewById<TextView>(R.id.tvNoSearchMatches)
        val fab = findViewById<ExtendedFloatingActionButton>(R.id.fabAddVehicle)
        val btnSearch = findViewById<ImageButton>(R.id.btnSearch)
        val searchLayout = findViewById<TextInputLayout>(R.id.searchInputLayout)
        val editSearch = findViewById<TextInputEditText>(R.id.editSearch)

        adapter = VehicleAdapter { vehicle ->
            val intent = Intent(this, VehicleDetailActivity::class.java)
            intent.putExtra("vehicleId", vehicle.id)
            startActivity(intent)
        }

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        btnSearch.setOnClickListener {
            searchExpanded = !searchExpanded
            searchLayout.visibility = if (searchExpanded) View.VISIBLE else View.GONE
            if (searchExpanded) {
                editSearch.requestFocus()
                val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.showSoftInput(editSearch, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            } else {
                editSearch.setText("")
                searchQuery = ""
                applyFilterAndUpdateUi(emptyState, tvNoSearchMatches, recycler)
                val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(editSearch.windowToken, 0)
            }
        }

        editSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString().orEmpty()
                applyFilterAndUpdateUi(emptyState, tvNoSearchMatches, recycler)
            }
        })

        lifecycleScope.launch {
            viewModel.vehicles.collectLatest { vehicles ->
                allVehicles = vehicles
                applyFilterAndUpdateUi(emptyState, tvNoSearchMatches, recycler)
            }
        }

        fab.setOnClickListener {
            startActivity(Intent(this, AddVehicleActivity::class.java))
        }
    }

    private fun filteredVehicles(): List<Vehicle> {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) return allVehicles
        return allVehicles.filter { v ->
            v.name.lowercase().contains(q) ||
                v.vehicleNumber.lowercase().contains(q) ||
                v.brand.lowercase().contains(q) ||
                v.model.lowercase().contains(q)
        }
    }

    private fun applyFilterAndUpdateUi(
        emptyState: LinearLayout,
        tvNoSearchMatches: TextView,
        recycler: RecyclerView
    ) {
        val filtered = filteredVehicles()
        adapter.submitList(filtered)

        when {
            allVehicles.isEmpty() -> {
                emptyState.visibility = View.VISIBLE
                tvNoSearchMatches.visibility = View.GONE
                recycler.visibility = View.GONE
            }
            filtered.isEmpty() && searchQuery.isNotBlank() -> {
                emptyState.visibility = View.GONE
                tvNoSearchMatches.visibility = View.VISIBLE
                recycler.visibility = View.GONE
            }
            else -> {
                emptyState.visibility = View.GONE
                tvNoSearchMatches.visibility = View.GONE
                recycler.visibility = View.VISIBLE
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.fetchVehicles()
    }
}
