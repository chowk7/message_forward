package com.smsforwarder.ui.log

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.smsforwarder.R
import com.smsforwarder.databinding.ActivityForwardLogBinding
import com.smsforwarder.viewmodel.MainViewModel

class ForwardLogActivity : AppCompatActivity() {

    private lateinit var binding: ActivityForwardLogBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: ForwardLogAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityForwardLogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        title = getString(R.string.forward_logs)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        setupRecyclerView()
        observeData()
    }

    private fun setupRecyclerView() {
        adapter = ForwardLogAdapter()
        binding.rvLogs.layoutManager = LinearLayoutManager(this)
        binding.rvLogs.adapter = adapter
    }

    private fun observeData() {
        viewModel.forwardLogs.observe(this) { logs ->
            adapter.submitList(logs)
            binding.tvEmpty.visibility = if (logs.isEmpty()) View.VISIBLE else View.GONE
            binding.rvLogs.visibility = if (logs.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, getString(R.string.clear_logs))
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            1 -> {
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.clear_logs))
                    .setMessage("모든 포워딩 로그를 삭제하시겠습니까?")
                    .setPositiveButton(getString(R.string.delete)) { _, _ ->
                        viewModel.clearLogs()
                    }
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
