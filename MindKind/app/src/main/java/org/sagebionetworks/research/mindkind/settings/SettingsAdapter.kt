package org.sagebionetworks.research.mindkind.settings

import android.content.SharedPreferences
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.RecyclerView
import org.sagebionetworks.research.mindkind.R
import org.sagebionetworks.research.mindkind.backgrounddata.BackgroundDataService


data class SettingsItem(
        val label: String,
        var subtext: String?,
        val header: Boolean,
        val sectionHeader: Boolean,
        val identifier: String = label,
        val toggle: Boolean = false,
        var active: Boolean = false)

public interface SettingsAdapterListener {
    fun onItemClicked(item: SettingsItem?)
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
        val toggleView: SwitchCompat? = view.findViewById(R.id.settings_toggle)
        val actionView: ImageView? = view.findViewById(R.id.settings_action)
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
        Log.d(LOG_TAG, "Subtext: " + item.subtext)
        if(item.subtext != null) {
            viewHolder.subtextView?.text = item.subtext
            viewHolder.subtextView?.visibility = View. VISIBLE
        } else {
            viewHolder.subtextView?.visibility = View.GONE
        }
        if(!item.header && !item.sectionHeader && !item.toggle) {
            viewHolder.itemView.setOnClickListener {
                listener.onItemClicked(item)
            }
        }

        if(item.toggle) {
            viewHolder.actionView?.visibility = View.GONE
            viewHolder.toggleView?.visibility = View.VISIBLE
            viewHolder.toggleView?.setChecked(item.active)
            
            viewHolder.toggleView?.setOnCheckedChangeListener {
                buttonView, isChecked ->
                    item.active = isChecked
                    listener.onItemClicked(item)
            }
        } else {
            viewHolder.actionView?.visibility = View.VISIBLE
            viewHolder.toggleView?.visibility = View.GONE
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

    public fun updateDataTrackingItems(prefs: SharedPreferences) {
        val tracking = BackgroundDataService.loadDataAllowedToBeTracked(prefs)
        dataSet.forEach {
            it.active = tracking.contains(it.identifier)
        }
        notifyDataSetChanged()
    }
}