package com.example.zendrive

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SyncFragment : Fragment() {

    private lateinit var viewModel: LogViewModel

    // JSON backup views
    private lateinit var btnExportBackup: Button
    private lateinit var btnImportBackup: Button
    private lateinit var layoutBackupStatus: View
    private lateinit var progressBackup: ProgressBar
    private lateinit var tvBackupStatus: TextView
    private lateinit var tvHistoryEmpty: TextView
    private lateinit var layoutHistoryEntries: LinearLayout

    // Drive views
    private lateinit var cardDriveSignIn: View
    private lateinit var cardDriveConnected: View
    private lateinit var btnGoogleSignIn: Button
    private lateinit var btnSignOut: Button
    private lateinit var tvAccountEmail: TextView
    private lateinit var tvLastBackup: TextView
    private lateinit var tvLastRestore: TextView
    private lateinit var switchAutoBackup: SwitchMaterial
    private lateinit var layoutSyncStatus: View
    private lateinit var progressSync: ProgressBar
    private lateinit var tvSyncStatus: TextView
    private lateinit var btnDriveBackup: Button
    private lateinit var btnDriveRestore: Button

    private val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportBackup(requireContext().applicationContext, uri)
        }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.backup_confirm_restore)
                .setMessage(R.string.restore_warning)
                .setPositiveButton(R.string.restore_proceed) { _, _ ->
                    viewModel.importBackup(requireContext().applicationContext, uri)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.handleSignInResult(result.data)
        }
    }

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
            db.userProfileDao(),
            db
        )
        viewModel = ViewModelProvider(activity, factory)[LogViewModel::class.java]

        bindViews(view)
        setupListeners()
        observeState()

        viewModel.checkExistingSignIn(requireContext())
        viewModel.loadBackupHistory()
    }

    private fun bindViews(view: View) {
        // JSON backup
        btnExportBackup = view.findViewById(R.id.btnExportBackup)
        btnImportBackup = view.findViewById(R.id.btnImportBackup)
        layoutBackupStatus = view.findViewById(R.id.layoutBackupStatus)
        progressBackup = view.findViewById(R.id.progressBackup)
        tvBackupStatus = view.findViewById(R.id.tvBackupStatus)
        tvHistoryEmpty = view.findViewById(R.id.tvHistoryEmpty)
        layoutHistoryEntries = view.findViewById(R.id.layoutHistoryEntries)

        // Drive
        cardDriveSignIn = view.findViewById(R.id.cardDriveSignIn)
        cardDriveConnected = view.findViewById(R.id.cardDriveConnected)
        btnGoogleSignIn = view.findViewById(R.id.btnGoogleSignIn)
        btnSignOut = view.findViewById(R.id.btnSignOut)
        tvAccountEmail = view.findViewById(R.id.tvAccountEmail)
        tvLastBackup = view.findViewById(R.id.tvLastBackup)
        tvLastRestore = view.findViewById(R.id.tvLastRestore)
        switchAutoBackup = view.findViewById(R.id.switchAutoBackup)
        layoutSyncStatus = view.findViewById(R.id.layoutSyncStatus)
        progressSync = view.findViewById(R.id.progressSync)
        tvSyncStatus = view.findViewById(R.id.tvSyncStatus)
        btnDriveBackup = view.findViewById(R.id.btnDriveBackup)
        btnDriveRestore = view.findViewById(R.id.btnDriveRestore)
    }

    private fun setupListeners() {
        btnExportBackup.setOnClickListener {
            val timestamp = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
            exportLauncher.launch("zendrive_backup_$timestamp.json")
        }

        btnImportBackup.setOnClickListener {
            importLauncher.launch(arrayOf("application/json", "*/*"))
        }

        btnGoogleSignIn.setOnClickListener {
            val intent = viewModel.getSignInIntent(requireContext())
            signInLauncher.launch(intent)
        }

        btnSignOut.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.drive_sign_out_confirm)
                .setMessage(R.string.drive_sign_out_warning)
                .setPositiveButton(R.string.drive_sign_out) { _, _ ->
                    viewModel.signOutFromGoogle(requireContext())
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        btnDriveBackup.setOnClickListener {
            viewModel.performDriveBackup(requireContext().applicationContext)
        }

        btnDriveRestore.setOnClickListener {
            viewModel.listDriveBackups(requireContext())
            showRestorePickerWhenReady()
        }

        switchAutoBackup.setOnCheckedChangeListener { _, isChecked ->
            viewModel.toggleAutoBackup(requireContext().applicationContext, isChecked)
        }
    }

    private fun showRestorePickerWhenReady() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.driveBackups.collectLatest { backups ->
                if (backups.isEmpty()) return@collectLatest

                val items = backups.map { info ->
                    val date = dateFormat.format(Date(info.createdTime))
                    val sizeKb = info.size / 1024
                    "$date  (${sizeKb} KB)"
                }.toTypedArray()

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.drive_select_backup)
                    .setItems(items) { _, which ->
                        val selected = backups[which]
                        confirmDriveRestore(selected)
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
                return@collectLatest
            }
        }
    }

    private fun confirmDriveRestore(backup: DriveBackupInfo) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.drive_restore_confirm)
            .setMessage(R.string.drive_restore_warning)
            .setPositiveButton(R.string.restore_proceed) { _, _ ->
                viewModel.performDriveRestore(
                    requireContext().applicationContext,
                    backup.fileId
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.driveAccount.collectLatest { account ->
                if (account != null) {
                    cardDriveSignIn.visibility = View.GONE
                    cardDriveConnected.visibility = View.VISIBLE
                    tvAccountEmail.text = account.email ?: ""
                    viewModel.listDriveBackups(requireContext())
                } else {
                    cardDriveSignIn.visibility = View.VISIBLE
                    cardDriveConnected.visibility = View.GONE
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.userProfileFlow.collectLatest { profile ->
                updateProfileInfo(profile)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.syncStatus.collectLatest { status ->
                updateSyncStatus(status)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.backupStatus.collectLatest { status ->
                updateBackupStatus(status)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.backupHistory.collectLatest { history ->
                renderHistory(history)
            }
        }
    }

    private fun updateProfileInfo(profile: UserProfile?) {
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

        switchAutoBackup.setOnCheckedChangeListener(null)
        switchAutoBackup.isChecked = profile?.backupEnabled == true
        switchAutoBackup.setOnCheckedChangeListener { _, isChecked ->
            viewModel.toggleAutoBackup(requireContext().applicationContext, isChecked)
        }
    }

    private fun updateSyncStatus(status: LogViewModel.SyncStatus) {
        when (status) {
            is LogViewModel.SyncStatus.Idle -> {
                layoutSyncStatus.visibility = View.GONE
                progressSync.visibility = View.GONE
                btnDriveBackup.isEnabled = true
                btnDriveRestore.isEnabled = true
            }
            is LogViewModel.SyncStatus.InProgress -> {
                layoutSyncStatus.visibility = View.VISIBLE
                progressSync.visibility = View.VISIBLE
                tvSyncStatus.text = getString(R.string.sync_in_progress)
                tvSyncStatus.setTextColor(resources.getColor(R.color.text_secondary, null))
                btnDriveBackup.isEnabled = false
                btnDriveRestore.isEnabled = false
            }
            is LogViewModel.SyncStatus.Success -> {
                layoutSyncStatus.visibility = View.VISIBLE
                progressSync.visibility = View.GONE
                tvSyncStatus.text = status.message
                tvSyncStatus.setTextColor(resources.getColor(R.color.success, null))
                btnDriveBackup.isEnabled = true
                btnDriveRestore.isEnabled = true
            }
            is LogViewModel.SyncStatus.Error -> {
                layoutSyncStatus.visibility = View.VISIBLE
                progressSync.visibility = View.GONE
                tvSyncStatus.text = status.message
                tvSyncStatus.setTextColor(resources.getColor(R.color.error, null))
                btnDriveBackup.isEnabled = true
                btnDriveRestore.isEnabled = true
            }
        }
    }

    private fun updateBackupStatus(status: LogViewModel.BackupStatus) {
        when (status) {
            is LogViewModel.BackupStatus.Idle -> {
                layoutBackupStatus.visibility = View.GONE
                progressBackup.visibility = View.GONE
                btnExportBackup.isEnabled = true
                btnImportBackup.isEnabled = true
            }
            is LogViewModel.BackupStatus.InProgress -> {
                layoutBackupStatus.visibility = View.VISIBLE
                progressBackup.visibility = View.VISIBLE
                tvBackupStatus.text = getString(R.string.backup_in_progress)
                tvBackupStatus.setTextColor(resources.getColor(R.color.text_secondary, null))
                btnExportBackup.isEnabled = false
                btnImportBackup.isEnabled = false
            }
            is LogViewModel.BackupStatus.Success -> {
                layoutBackupStatus.visibility = View.VISIBLE
                progressBackup.visibility = View.GONE
                tvBackupStatus.text = status.message
                tvBackupStatus.setTextColor(resources.getColor(R.color.success, null))
                btnExportBackup.isEnabled = true
                btnImportBackup.isEnabled = true
            }
            is LogViewModel.BackupStatus.Error -> {
                layoutBackupStatus.visibility = View.VISIBLE
                progressBackup.visibility = View.GONE
                tvBackupStatus.text = status.message
                tvBackupStatus.setTextColor(resources.getColor(R.color.error, null))
                btnExportBackup.isEnabled = true
                btnImportBackup.isEnabled = true
            }
        }
    }

    private fun renderHistory(history: List<BackupRestoreLog>) {
        layoutHistoryEntries.removeAllViews()

        if (history.isEmpty()) {
            tvHistoryEmpty.visibility = View.VISIBLE
            return
        }

        tvHistoryEmpty.visibility = View.GONE

        for ((index, entry) in history.withIndex()) {
            if (index > 0) {
                val divider = View(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1
                    ).apply { topMargin = dpToPx(8); bottomMargin = dpToPx(8) }
                    setBackgroundColor(resources.getColor(R.color.divider, null))
                }
                layoutHistoryEntries.addView(divider)
            }

            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val typeLabel = when (entry.operationType) {
                "backup" -> "Export"
                "restore" -> "Restore"
                "drive_backup" -> "Drive Backup"
                "drive_restore" -> "Drive Restore"
                else -> entry.operationType
            }
            val statusColor = when (entry.status) {
                "success" -> resources.getColor(R.color.success, null)
                "failed" -> resources.getColor(R.color.error, null)
                else -> resources.getColor(R.color.text_hint, null)
            }

            val leftText = TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = "$typeLabel  ·  ${entry.status}"
                setTextColor(statusColor)
                textSize = 13f
            }

            val timeText = TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                val ts = entry.completedAt ?: entry.startedAt
                text = dateFormat.format(Date(ts))
                setTextColor(resources.getColor(R.color.text_hint, null))
                textSize = 12f
            }

            row.addView(leftText)
            row.addView(timeText)
            layoutHistoryEntries.addView(row)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkExistingSignIn(requireContext())
        viewModel.loadBackupHistory()
    }
}
