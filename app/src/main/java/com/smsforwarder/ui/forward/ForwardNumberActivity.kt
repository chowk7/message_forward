package com.smsforwarder.ui.forward

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.textfield.TextInputEditText
import com.smsforwarder.R
import com.smsforwarder.data.model.ForwardNumber
import com.smsforwarder.databinding.ActivityForwardNumberBinding
import com.smsforwarder.viewmodel.MainViewModel

class ForwardNumberActivity : AppCompatActivity() {

    private lateinit var binding: ActivityForwardNumberBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: ForwardNumberAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityForwardNumberBinding.inflate(layoutInflater)
        setContentView(binding.root)

        title = getString(R.string.forward_numbers)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        setupRecyclerView()
        setupFab()
        observeData()
    }

    private fun setupRecyclerView() {
        adapter = ForwardNumberAdapter(
            onToggle = { number, enabled ->
                viewModel.updateNumber(number.copy(isEnabled = enabled))
            },
            onDelete = { number ->
                viewModel.deleteNumber(number)
            }
        )
        binding.rvNumbers.layoutManager = LinearLayoutManager(this)
        binding.rvNumbers.adapter = adapter
    }

    private fun setupFab() {
        binding.fabAdd.setOnClickListener {
            showAddDialog()
        }
    }

    private fun showAddDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_number, null)
        val etPhone = dialogView.findViewById<TextInputEditText>(R.id.etPhoneNumber)
        val etLabel = dialogView.findViewById<TextInputEditText>(R.id.etLabel)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.add_number))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val phone = etPhone.text?.toString()?.trim() ?: return@setPositiveButton
                if (phone.isEmpty()) return@setPositiveButton

                val label = etLabel.text?.toString()?.trim() ?: ""

                viewModel.insertNumber(ForwardNumber(phoneNumber = phone, label = label))
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun observeData() {
        viewModel.forwardNumbers.observe(this) { numbers ->
            adapter.submitList(numbers)
            binding.tvEmpty.visibility = if (numbers.isEmpty()) View.VISIBLE else View.GONE
            binding.rvNumbers.visibility = if (numbers.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
