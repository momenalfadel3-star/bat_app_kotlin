package com.alrufaaey.batapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class HostsAdapter(
    private val hosts: MutableList<String>,
    private val onHostClick: (String) -> Unit
) : RecyclerView.Adapter<HostsAdapter.HostViewHolder>() {

    inner class HostViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvHost: TextView = itemView.findViewById(R.id.tv_host)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HostViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_host, parent, false)
        return HostViewHolder(view)
    }

    override fun onBindViewHolder(holder: HostViewHolder, position: Int) {
        val host = hosts[position]
        holder.tvHost.text = host
        holder.itemView.setOnClickListener { onHostClick(host) }
        holder.itemView.setOnLongClickListener {
            hosts.removeAt(position)
            notifyItemRemoved(position)
            true
        }
    }

    override fun getItemCount(): Int = hosts.size
}
