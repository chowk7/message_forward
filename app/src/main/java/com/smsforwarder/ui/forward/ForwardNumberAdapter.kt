package com.smsforwarder.ui.forward

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.smsforwarder.data.model.ForwardNumber
import com.smsforwarder.databinding.ItemForwardNumberBinding

class ForwardNumberAdapter(
    private val onToggle: (ForwardNumber, Boolean) -> Unit,
    private val onDelete: (ForwardNumber) -> Unit
) : ListAdapter<ForwardNumber, ForwardNumberAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemForwardNumberBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemForwardNumberBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(number: ForwardNumber) {
            binding.tvPhoneNumber.text = number.phoneNumber
            binding.tvLabel.text = number.label.ifEmpty { "라벨 없음" }

            binding.switchEnabled.setOnCheckedChangeListener(null)
            binding.switchEnabled.isChecked = number.isEnabled
            binding.switchEnabled.setOnCheckedChangeListener { _, checked ->
                onToggle(number, checked)
            }

            binding.btnDelete.setOnClickListener {
                onDelete(number)
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<ForwardNumber>() {
        override fun areItemsTheSame(oldItem: ForwardNumber, newItem: ForwardNumber) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: ForwardNumber, newItem: ForwardNumber) =
            oldItem == newItem
    }
}
