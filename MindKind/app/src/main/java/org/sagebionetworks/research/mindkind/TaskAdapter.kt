package org.sagebionetworks.research.mindkind

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import org.sagebionetworks.research.mindkind.conversation.ConversationSurveyActivity
import java.util.*


data class TaskItem(
        val title: String,
        val label: String,
        val json: String?)

class TaskAdapter(
        context: Context,
        private val dataSet: ArrayList<TaskItem>) :
        RecyclerView.Adapter<TaskAdapter.ViewHolder>() {

    companion object {
        private val LOG_TAG = this::class.java.canonicalName
    }

    open class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var title: TextView = view.findViewById(R.id.survey_detail_title)
        val label: TextView = view.findViewById(R.id.survey_detail_label)
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        val view: View = LayoutInflater.from(viewGroup.context).inflate(R.layout.list_item_task,
                viewGroup, false)

        val viewHolder = ViewHolder(view)
        return viewHolder
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        Log.i(LOG_TAG, "onBindViewHolder(): $position")
        val item = dataSet[position]

        viewHolder.title.text = item.title
        viewHolder.label.text = item.label
        viewHolder.itemView.setOnClickListener {
            item.json?.let { it1 -> ConversationSurveyActivity.start(viewHolder.title.context, it1) }
        }

    }

    override fun getItemCount() = dataSet.size

}