package org.sagebionetworks.research.mindkind.settings

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import org.sagebionetworks.research.mindkind.R
import org.sagebionetworks.research.mindkind.conversation.ConversationAdapter
import java.util.*


data class SettingsItem(
        val label: String,
        val subtext: String?,
        val header: Boolean,
        val sectionHeader: Boolean)

public interface SettingsAdapterListener {
    fun onItemClicked(jsonResourceName: String?)
}

class SettingsAdapter(
        private val dataSet: MutableList<SettingsItem>,
        private val listener: SettingsAdapterListener) :
        RecyclerView.Adapter<SettingsAdapter.ViewHolder>() {

    companion object {
        private val LOG_TAG = this::class.java.canonicalName
    }

    open class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val labelView: TextView = view.findViewById(R.id.settings_item_label)
        val subtextView: TextView? = view.findViewById(R.id.settings_item_subtext)
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): SettingsAdapter.ViewHolder {
        return when (viewType) {
            0-> SettingsAdapter.ViewHolder(LayoutInflater.from(viewGroup.context).inflate(
                    R.layout.list_item_settings_header, viewGroup, false))
            1-> SettingsAdapter.ViewHolder(LayoutInflater.from(viewGroup.context).inflate(
                    R.layout.list_item_settings_section_header, viewGroup, false))
            else -> SettingsAdapter.ViewHolder(LayoutInflater.from(viewGroup.context).inflate(
                    R.layout.list_item_settings, viewGroup, false))
        }
    }

    override fun onBindViewHolder(viewHolder: SettingsAdapter.ViewHolder, position: Int) {
        Log.i(LOG_TAG, "onBindViewHolder(): $position")
        val item = dataSet[position]

        viewHolder.labelView.text = item.label
        if(item.subtext != null) {
            viewHolder.subtextView?.text = item.subtext
            viewHolder.subtextView?.visibility = View. VISIBLE
        } else {
            viewHolder.subtextView?.visibility = View.GONE
        }
        if(!item.header && !item.sectionHeader) {
            viewHolder.itemView.setOnClickListener {
                listener.onItemClicked(item.label)
            }
        }
    }

    override fun getItemCount() = dataSet.size

    override fun getItemViewType(position: Int): Int {
        val item = dataSet[position]
        return when {
            item.header -> 0
            item.sectionHeader -> 1
            else -> 2
        }
    }
}