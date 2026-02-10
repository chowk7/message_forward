package com.smsforwarder.ui.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.smsforwarder.R
import com.smsforwarder.databinding.ActivityMainBinding
import com.smsforwarder.ui.filter.FilterEditActivity
import com.smsforwarder.ui.forward.ForwardNumberActivity
import com.smsforwarder.ui.log.ForwardLogActivity
import com.smsforwarder.service.MessageObserverService
import com.smsforwarder.viewmodel.MainViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var prefs: SharedPreferences

    private val requiredPermissions = buildList {
        add(Manifest.permission.RECEIVE_SMS)
        add(Manifest.permission.SEND_SMS)
        add(Manifest.permission.READ_SMS)
        add(Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (!allGranted) {
            Toast.makeText(this, getString(R.string.permissions_required), Toast.LENGTH_LONG).show()
            binding.switchForwarding.isChecked = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        title = getString(R.string.app_name)

        prefs = getSharedPreferences("sms_forwarder_prefs", MODE_PRIVATE)
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        setupToggle()
        setupCards()
        setupBatteryWarning()
        observeData()
    }

    override fun onResume() {
        super.onResume()
        updateBatteryWarning()
    }

    private fun setupToggle() {
        val isEnabled = prefs.getBoolean("forwarding_enabled", false)
        binding.switchForwarding.isChecked = isEnabled
        updateStatusText(isEnabled)

        if (isEnabled) {
            startMessageObserverService()
        }

        binding.switchForwarding.setOnCheckedChangeListener { _, checked ->
            if (checked && !hasAllPermissions()) {
                permissionLauncher.launch(requiredPermissions)
            }
            prefs.edit().putBoolean("forwarding_enabled", checked).apply()
            updateStatusText(checked)

            if (checked) {
                checkBatteryOptimization()
                startMessageObserverService()
            } else {
                stopMessageObserverService()
            }
        }
    }

    private fun startMessageObserverService() {
        val intent = Intent(this, MessageObserverService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopMessageObserverService() {
        val intent = Intent(this, MessageObserverService::class.java)
        stopService(intent)
    }

    private fun updateStatusText(enabled: Boolean) {
        binding.tvStatus.text = if (enabled) "활성화됨 - SMS/MMS/채팅+ 포워딩 중" else "비활성화"
        binding.tvStatus.setTextColor(
            ContextCompat.getColor(this, if (enabled) R.color.success else R.color.text_secondary)
        )
    }

    private fun setupCards() {
        binding.cardFilterRules.setOnClickListener {
            startActivity(Intent(this, FilterEditActivity::class.java))
        }
        binding.cardForwardNumbers.setOnClickListener {
            startActivity(Intent(this, ForwardNumberActivity::class.java))
        }
        binding.cardForwardLogs.setOnClickListener {
            startActivity(Intent(this, ForwardLogActivity::class.java))
        }
    }

    private fun setupBatteryWarning() {
        binding.cardBatteryWarning.setOnClickListener {
            showBatteryDialog()
        }
        binding.btnFixBattery.setOnClickListener {
            showBatteryDialog()
        }
        updateBatteryWarning()
    }

    private fun updateBatteryWarning() {
        val isOptimized = isBatteryOptimized()
        binding.cardBatteryWarning.visibility = if (isOptimized) View.VISIBLE else View.GONE
    }

    private fun isBatteryOptimized(): Boolean {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        return !powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun checkBatteryOptimization() {
        if (isBatteryOptimized()) {
            showBatteryDialog()
        }
    }

    private fun showBatteryDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.battery_dialog_title))
            .setMessage(getString(R.string.battery_dialog_message))
            .setPositiveButton(getString(R.string.battery_dialog_auto)) { _, _ ->
                requestIgnoreBatteryOptimization()
            }
            .setNeutralButton(getString(R.string.battery_dialog_manual)) { _, _ ->
                openBatterySettings()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    @Suppress("BatteryLife")
    private fun requestIgnoreBatteryOptimization() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
        startActivity(intent)
    }

    private fun openBatterySettings() {
        try {
            // Try manufacturer-specific battery settings first
            val intent = Intent().apply {
                action = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
            }
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback to general app settings
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }

    private fun observeData() {
        viewModel.filterRules.observe(this) { rules ->
            binding.tvRulesCount.text = getString(R.string.rules_count, rules.size)
        }
        viewModel.forwardNumbers.observe(this) { numbers ->
            binding.tvNumbersCount.text = getString(R.string.numbers_count, numbers.size)
        }
    }

    private fun hasAllPermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }
}
