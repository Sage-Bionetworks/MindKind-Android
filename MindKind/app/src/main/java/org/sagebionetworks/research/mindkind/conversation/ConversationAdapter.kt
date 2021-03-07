package org.sagebionetworks.research.mindkind.conversation

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.sagebionetworks.research.mindkind.R
import java.util.*

class ConversationAdapter(private val dataSet: ArrayList<ConversationItem>) :
        RecyclerView.Adapter<ConversationAdapter.ViewHolder>() {

    private val LOG_TAG = this::class.java.canonicalName

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView
        val container: View

        init {
            textView = view.findViewById(R.id.textView)
            container = view.findViewById(R.id.container)
        }
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        var view: View

        if (viewType == 1) {
            view = LayoutInflater.from(viewGroup.context)
                    .inflate(R.layout.list_item_question, viewGroup, false)
        } else {
            view = LayoutInflater.from(viewGroup.context)
                    .inflate(R.layout.list_item_reply, viewGroup, false)
        }

        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        Log.i(LOG_TAG, "onBindViewHolder(): $position")
        viewHolder.textView.text = dataSet[position].text
        var type = getItemViewType(position)

        // handle fade
        if(position != (dataSet.size-1)) {
            var resources = viewHolder.container.resources
            viewHolder.textView.setTextColor(resources.getColor(R.color.black_overlay))
            if(type == 0) {
                viewHolder.container.setBackgroundResource(R.drawable.reply_background_fade)
            } else {
                viewHolder.container.setBackgroundResource(R.drawable.question_background_fade)
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        var question = dataSet[position].isQuestion

        if(question) {
            return 1
        } else {
            return 0
        }
    }

    override fun getItemCount() = dataSet.size

    open fun addItem(message: String, question: Boolean) {
        dataSet.add(ConversationItem(message, question))
        notifyItemInserted(dataSet.size)

        // TODO: this logic needs improving
        if(question) {
            // force previous question/answer to redraw
            notifyItemChanged(dataSet.size - 2)
            notifyItemChanged(dataSet.size - 3)
        }
    }
}