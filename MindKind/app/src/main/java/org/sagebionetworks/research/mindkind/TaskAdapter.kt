package org.sagebionetworks.research.mindkind

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class TaskItem(
        val title: String,
        val label: String,
        val jsonResourceName: String?)

public interface TaskAdapterListener {
    fun onTaskClicked(jsonResourceName: String?)
}

class TaskAdapter(
        private val dataSet: MutableList<TaskItem>,
        private val listener: TaskAdapterListener) :
        RecyclerView.Adapter<TaskAdapter.ViewHolder>() {

    companion object {
        private val LOG_TAG = this::class.java.canonicalName
    }

    open class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.survey_detail_title)
        val label: TextView = view.findViewById(R.id.survey_detail_label)
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        val view: View = LayoutInflater.from(viewGroup.context).inflate(R.layout.list_item_task,
                viewGroup, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        Log.i(LOG_TAG, "onBindViewHolder(): $position")
        val item = dataSet[position]

        viewHolder.title.text = item.title
        viewHolder.label.text = item.label
        viewHolder.itemView.isEnabled = true
        viewHolder.itemView.setOnClickListener {
            it.isEnabled = false
            listener.onTaskClicked(item.jsonResourceName)
        }
    }

    override fun getItemCount() = dataSet.size

}