package com.smsforwarder.ui.filter

import android.os.Bundle
import android.view.Menu
import android.view.View
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.textfield.TextInputEditText
import com.smsforwarder.R
import com.smsforwarder.data.model.FilterRule
import com.smsforwarder.data.model.FilterType
import com.smsforwarder.databinding.ActivityFilterEditBinding
import com.smsforwarder.viewmodel.MainViewModel

class FilterEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFilterEditBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: FilterRuleAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFilterEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        title = getString(R.string.filter_rules)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        setupRecyclerView()
        setupFab()
        observeData()
    }

    private fun setupRecyclerView() {
        adapter = FilterRuleAdapter(
            onToggle = { rule, enabled ->
                viewModel.updateRule(rule.copy(isEnabled = enabled))
            },
            onDelete = { rule ->
                viewModel.deleteRule(rule)
            }
        )
        binding.rvFilters.layoutManager = LinearLayoutManager(this)
        binding.rvFilters.adapter = adapter
    }

    private fun setupFab() {
        binding.fabAdd.setOnClickListener {
            showAddDialog()
        }
    }

    private fun showAddDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_filter, null)
        val rgType = dialogView.findViewById<RadioGroup>(R.id.rgFilterType)
        val etValue = dialogView.findViewById<TextInputEditText>(R.id.etFilterValue)

        rgType.setOnCheckedChangeListener { _, checkedId ->
            etValue.hint = when (checkedId) {
                R.id.rbKeyword -> getString(R.string.keyword_hint)
                R.id.rbSenderNumber -> getString(R.string.number_hint)
                else -> ""
            }
            etValue.inputType = when (checkedId) {
                R.id.rbSenderNumber -> android.text.InputType.TYPE_CLASS_PHONE
                else -> android.text.InputType.TYPE_CLASS_TEXT
            }
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.add_filter))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val value = etValue.text?.toString()?.trim() ?: return@setPositiveButton
                if (value.isEmpty()) return@setPositiveButton

                val type = when (rgType.checkedRadioButtonId) {
                    R.id.rbSenderNumber -> FilterType.SENDER_NUMBER
                    else -> FilterType.TEXT_KEYWORD
                }

                viewModel.insertRule(FilterRule(type = type, value = value))
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun observeData() {
        viewModel.filterRules.observe(this) { rules ->
            adapter.submitList(rules)
            binding.tvEmpty.visibility = if (rules.isEmpty()) View.VISIBLE else View.GONE
            binding.rvFilters.visibility = if (rules.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
