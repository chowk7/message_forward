package com.smsforwarder.ui.home

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.smsforwarder.R
import com.smsforwarder.databinding.ActivityMainBinding
import com.smsforwarder.ui.filter.FilterEditActivity
import com.smsforwarder.ui.forward.ForwardNumberActivity
import com.smsforwarder.ui.log.ForwardLogActivity
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
        observeData()
    }

    private fun setupToggle() {
        val isEnabled = prefs.getBoolean("forwarding_enabled", false)
        binding.switchForwarding.isChecked = isEnabled
        updateStatusText(isEnabled)

        binding.switchForwarding.setOnCheckedChangeListener { _, checked ->
            if (checked && !hasAllPermissions()) {
                permissionLauncher.launch(requiredPermissions)
            }
            prefs.edit().putBoolean("forwarding_enabled", checked).apply()
            updateStatusText(checked)
        }
    }

    private fun updateStatusText(enabled: Boolean) {
        binding.tvStatus.text = if (enabled) "활성화됨 - SMS 포워딩 중" else "비활성화"
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
