package com.example.zendrive

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class VehiclesFragment : Fragment() {

    private lateinit var viewModel: LogViewModel
    private lateinit var adapter: VehicleAdapter

    private var allVehicles: List<Vehicle> = emptyList()
    private var searchQuery: String = ""
    private var searchExpanded: Boolean = false

    private lateinit var emptyState: LinearLayout
    private lateinit var tvNoSearchMatches: TextView
    private lateinit var recycler: RecyclerView
    private lateinit var searchLayout: TextInputLayout
    private lateinit var editSearch: TextInputEditText

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_vehicles, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val activity = requireActivity()
        val db = AppDatabase.getInstance(activity)
        val factory = ViewModelFactory(
            db.vehicleDao(),
            db.vehicleEventDao(),
            db.eventMetaDao(),
            db.userProfileDao()
        )
        viewModel = ViewModelProvider(activity, factory)[LogViewModel::class.java]

        emptyState = view.findViewById(R.id.emptyState)
        tvNoSearchMatches = view.findViewById(R.id.tvNoSearchMatches)
        recycler = view.findViewById(R.id.recyclerVehicles)
        searchLayout = view.findViewById(R.id.searchInputLayout)
        editSearch = view.findViewById(R.id.editSearch)

        val btnSearch = view.findViewById<ImageButton>(R.id.btnSearch)
        val btnProfile = view.findViewById<ImageButton>(R.id.btnProfile)
        val fab = view.findViewById<ExtendedFloatingActionButton>(R.id.fabAddVehicle)

        adapter = VehicleAdapter { vehicle ->
            val intent = Intent(activity, VehicleDetailActivity::class.java)
            intent.putExtra("vehicleId", vehicle.id)
            startActivity(intent)
        }

        recycler.layoutManager = LinearLayoutManager(activity)
        recycler.adapter = adapter

        btnProfile.setOnClickListener {
            startActivity(Intent(activity, ProfileActivity::class.java))
        }

        btnSearch.setOnClickListener {
            searchExpanded = !searchExpanded
            searchLayout.visibility = if (searchExpanded) View.VISIBLE else View.GONE
            if (searchExpanded) {
                editSearch.requestFocus()
                val imm = activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                        as android.view.inputmethod.InputMethodManager
                imm.showSoftInput(editSearch, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            } else {
                editSearch.setText("")
                searchQuery = ""
                applyFilterAndUpdateUi()
                val imm = activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                        as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(editSearch.windowToken, 0)
            }
        }

        editSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString().orEmpty()
                applyFilterAndUpdateUi()
            }
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.userProfileFlow.distinctUntilChanged().collectLatest { profile ->
                val showOnboarding = profile == null
                btnProfile.visibility = if (!showOnboarding) View.VISIBLE else View.GONE
                fab.visibility = if (!showOnboarding) View.VISIBLE else View.GONE
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.vehicles.collectLatest { vehicles ->
                allVehicles = vehicles
                applyFilterAndUpdateUi()
            }
        }

        fab.setOnClickListener {
            startActivity(Intent(activity, AddVehicleActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.fetchVehicles()
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

    private fun applyFilterAndUpdateUi() {
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
}
