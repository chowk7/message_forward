package com.smsforwarder.ui.log

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.smsforwarder.R
import com.smsforwarder.data.model.ForwardLog
import com.smsforwarder.databinding.ItemForwardLogBinding
import java.text.SimpleDateFormat
import java.util.*

class ForwardLogAdapter : ListAdapter<ForwardLog, ForwardLogAdapter.ViewHolder>(DiffCallback) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemForwardLogBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemForwardLogBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(log: ForwardLog) {
            val context = binding.root.context

            binding.tvStatus.text = if (log.isSuccess) "성공" else "실패"
            binding.tvStatus.setTextColor(
                ContextCompat.getColor(context, if (log.isSuccess) R.color.success else R.color.error)
            )

            binding.tvTimestamp.text = dateFormat.format(Date(log.timestamp))
            binding.tvSenderToForward.text = "${log.originalSender} → ${log.forwardedTo}"
            binding.tvMessage.text = log.messageBody
            binding.tvMatchedRule.text = "매칭 규칙: ${log.matchedRule}"
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<ForwardLog>() {
        override fun areItemsTheSame(oldItem: ForwardLog, newItem: ForwardLog) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: ForwardLog, newItem: ForwardLog) =
            oldItem == newItem
    }
}
