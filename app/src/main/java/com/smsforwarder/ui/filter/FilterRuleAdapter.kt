package com.smsforwarder.ui.filter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.smsforwarder.data.model.FilterRule
import com.smsforwarder.data.model.FilterType
import com.smsforwarder.databinding.ItemFilterRuleBinding

class FilterRuleAdapter(
    private val onToggle: (FilterRule, Boolean) -> Unit,
    private val onDelete: (FilterRule) -> Unit
) : ListAdapter<FilterRule, FilterRuleAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFilterRuleBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemFilterRuleBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(rule: FilterRule) {
            binding.tvFilterType.text = when (rule.type) {
                FilterType.TEXT_KEYWORD -> "텍스트 키워드"
                FilterType.SENDER_NUMBER -> "발신 번호"
            }
            binding.tvFilterValue.text = rule.value

            binding.switchEnabled.setOnCheckedChangeListener(null)
            binding.switchEnabled.isChecked = rule.isEnabled
            binding.switchEnabled.setOnCheckedChangeListener { _, checked ->
                onToggle(rule, checked)
            }

            binding.btnDelete.setOnClickListener {
                onDelete(rule)
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<FilterRule>() {
        override fun areItemsTheSame(oldItem: FilterRule, newItem: FilterRule) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: FilterRule, newItem: FilterRule) =
            oldItem == newItem
    }
}
