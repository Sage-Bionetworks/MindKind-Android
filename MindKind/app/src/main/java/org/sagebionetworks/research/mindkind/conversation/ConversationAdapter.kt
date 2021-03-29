package org.sagebionetworks.research.mindkind.conversation

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import com.bumptech.glide.Glide
import org.sagebionetworks.research.mindkind.R
import java.util.*

data class ConversationItem(val text: String, val isQuestion: Boolean, val gifUrl: String? = null)

class ConversationAdapter(
        context: Context,
        private val dataSet: ArrayList<ConversationItem>) :
        RecyclerView.Adapter<ConversationAdapter.ViewHolder>() {

    private val LOG_TAG = this::class.java.canonicalName

    private val glide = Glide.with(context)

    open class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val container: View = view.findViewById(R.id.container)
    }

    open class ChatViewHolder(view: View) : ViewHolder(view) {
        val textView: TextView = view.findViewById(R.id.textView)
    }

    open class GifViewHolder(view: View) : ViewHolder(view) {
        val loadingDrawable: CircularProgressDrawable = CircularProgressDrawable(view.context)
        val imageView: ImageView = view.findViewById(R.id.conversation_image_view)
        init {
            loadingDrawable.strokeWidth = 5f
            loadingDrawable.centerRadius = 30f
            loadingDrawable.start()
        }
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        return when (viewType) {
            2 -> GifViewHolder(LayoutInflater.from(viewGroup.context).inflate(
                    R.layout.list_item_image_view, viewGroup, false))
            1 -> ChatViewHolder(LayoutInflater.from(viewGroup.context).inflate(
                    R.layout.list_item_question, viewGroup, false))
            else -> ChatViewHolder(LayoutInflater.from(viewGroup.context).inflate(
                    R.layout.list_item_reply, viewGroup, false))
        }
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        Log.i(LOG_TAG, "onBindViewHolder(): $position")
        val item = dataSet[position]

        (viewHolder as? ChatViewHolder)?.let {
            it.textView.text = dataSet[position].text

            val type = getItemViewType(position)
            // handle fade
            var resources = viewHolder.container.resources
            if(position != (dataSet.size-1)) {
                val textColor = ResourcesCompat.getColor(resources, R.color.black_overlay, null)
                viewHolder.textView.setTextColor(textColor)
                if(type == 0) {
                    viewHolder.container.setBackgroundResource(R.drawable.reply_background_fade)
                } else {
                    viewHolder.container.setBackgroundResource(R.drawable.question_background_fade)
                }
            } else {
                val textColor = ResourcesCompat.getColor(resources, R.color.black, null)
                viewHolder.textView.setTextColor(textColor)
                if(type == 0) {
                    viewHolder.container.setBackgroundResource(R.drawable.reply_background)
                } else {
                    viewHolder.container.setBackgroundResource(R.drawable.question_background)
                }
            }
        }

        (viewHolder as? GifViewHolder)?.let {
            val gifUrl = item.gifUrl ?: run { return@let }
            glide.load(gifUrl)
                 .placeholder(it.loadingDrawable)
                 .into(it.imageView)
        }
    }

    override fun getItemViewType(position: Int): Int {
        val item = dataSet[position]
        return when {
            item.gifUrl != null -> 2
            item.isQuestion -> 1
            else -> 0
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

    open fun addGif(gifUrl: String) {
        dataSet.add(ConversationItem("", false, gifUrl))
        notifyItemInserted(dataSet.size)
    }
}