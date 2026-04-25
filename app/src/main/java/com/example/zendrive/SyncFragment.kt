package com.example.zendrive

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SyncFragment : Fragment() {

    private lateinit var viewModel: LogViewModel

    private lateinit var tvAccountEmail: TextView
    private lateinit var tvLastBackup: TextView
    private lateinit var tvLastRestore: TextView
    private lateinit var tvSyncStatus: TextView
    private lateinit var progressSync: ProgressBar
    private lateinit var btnSync: Button

    private val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_sync, container, false)
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

        tvAccountEmail = view.findViewById(R.id.tvAccountEmail)
        tvLastBackup = view.findViewById(R.id.tvLastBackup)
        tvLastRestore = view.findViewById(R.id.tvLastRestore)
        tvSyncStatus = view.findViewById(R.id.tvSyncStatus)
        progressSync = view.findViewById(R.id.progressSync)
        btnSync = view.findViewById(R.id.btnSync)

        btnSync.setOnClickListener {
            performSync()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.userProfileFlow.collectLatest { profile ->
                updateAccountInfo(profile)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.syncStatus.collectLatest { status ->
                updateSyncStatus(status)
            }
        }
    }

    private fun updateAccountInfo(profile: UserProfile?) {
        val email = profile?.driveAccountEmail
        if (email.isNullOrBlank()) {
            tvAccountEmail.text = getString(R.string.sync_no_account)
        } else {
            tvAccountEmail.text = getString(R.string.sync_account_email, email)
        }

        val lastBackup = profile?.lastBackupAt
        tvLastBackup.text = if (lastBackup != null) {
            dateFormat.format(Date(lastBackup))
        } else {
            getString(R.string.sync_never)
        }

        val lastRestore = profile?.lastRestoreAt
        tvLastRestore.text = if (lastRestore != null) {
            dateFormat.format(Date(lastRestore))
        } else {
            getString(R.string.sync_never)
        }
    }

    private fun updateSyncStatus(status: LogViewModel.SyncStatus) {
        when (status) {
            is LogViewModel.SyncStatus.Idle -> {
                progressSync.visibility = View.GONE
                tvSyncStatus.visibility = View.GONE
                btnSync.isEnabled = true
            }
            is LogViewModel.SyncStatus.InProgress -> {
                progressSync.visibility = View.VISIBLE
                tvSyncStatus.visibility = View.VISIBLE
                tvSyncStatus.text = getString(R.string.sync_in_progress)
                tvSyncStatus.setTextColor(resources.getColor(R.color.text_secondary, null))
                btnSync.isEnabled = false
            }
            is LogViewModel.SyncStatus.Success -> {
                progressSync.visibility = View.GONE
                tvSyncStatus.visibility = View.VISIBLE
                tvSyncStatus.text = getString(R.string.sync_completed)
                tvSyncStatus.setTextColor(resources.getColor(R.color.success, null))
                btnSync.isEnabled = true
            }
            is LogViewModel.SyncStatus.Error -> {
                progressSync.visibility = View.GONE
                tvSyncStatus.visibility = View.VISIBLE
                tvSyncStatus.text = status.message
                tvSyncStatus.setTextColor(resources.getColor(R.color.error, null))
                btnSync.isEnabled = true
            }
        }
    }

    private fun performSync() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.performGoogleDriveSync(requireContext())
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh profile data when returning to this screen
    }
}
