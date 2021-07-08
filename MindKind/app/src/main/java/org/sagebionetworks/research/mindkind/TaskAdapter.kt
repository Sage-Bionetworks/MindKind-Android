package org.sagebionetworks.research.mindkind

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

open class TaskListItem(
        val identifier: String,
        val titleResId: Int,
        val timeResId: Int,
        val detailResId: Int,
        val jsonResourceName: String)

public interface TaskAdapterListener {
    fun onTaskClicked(jsonResourceName: String?)
}

class TaskAdapter(
        var dataSet: MutableList<TaskListItem>,
        private val listener: TaskAdapterListener) :
        RecyclerView.Adapter<TaskAdapter.ViewHolder>() {

    companion object {
        private val LOG_TAG = this::class.java.canonicalName
    }

    open class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.survey_detail_title)
        val text: TextView = view.findViewById(R.id.survey_detail_text)
        val timeLabel: TextView = view.findViewById(R.id.survey_time_label)
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        val view: View = LayoutInflater.from(viewGroup.context).inflate(R.layout.list_item_task,
                viewGroup, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        Log.i(LOG_TAG, "onBindViewHolder(): $position")
        val item = dataSet[position]

        val context = viewHolder.title.context

        viewHolder.title.text = context.getString(item.titleResId)
        viewHolder.timeLabel.text = context.getString(item.timeResId)
        viewHolder.text.text = context.getString(item.detailResId)
        viewHolder.itemView.setOnClickListener {
            listener.onTaskClicked(item.jsonResourceName)
        }
    }

    override fun getItemCount() = dataSet.size
}