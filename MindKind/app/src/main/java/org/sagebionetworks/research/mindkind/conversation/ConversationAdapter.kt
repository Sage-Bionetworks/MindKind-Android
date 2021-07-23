package org.sagebionetworks.research.mindkind.conversation

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import org.sagebionetworks.research.mindkind.R
import java.util.*


data class ConversationItem(
        val stepIdentifier: String,
        var text: String?,
        val isQuestion: Boolean,
        val linkWithNext: Boolean = false,
        val gifUrl: String? = null,
        val cannotEdit: Boolean = false)

public interface ConversationAdapterListener {
    fun onConversationClicked(stepIdentifier: String, answer: String?, position: Int)
}

class ConversationAdapter(
        context: Context,
        private val dataSet: ArrayList<ConversationItem>,
        private val listener: ConversationAdapterListener) :
        RecyclerView.Adapter<ConversationAdapter.ViewHolder>() {

    companion object {
        private val LOG_TAG = this::class.java.canonicalName
    }

    private val glide = Glide.with(context)
    private var currentIdentifier: String? = null

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

        if (item.cannotEdit) {
            viewHolder.itemView.setOnLongClickListener(null)
        } else {
            // Allow long press on both question and answer
            viewHolder.itemView.setOnLongClickListener {
                listener.onConversationClicked(item.stepIdentifier, findAnswer(item.stepIdentifier), position)
                currentIdentifier = item.stepIdentifier
                notifyDataSetChanged()
                return@setOnLongClickListener true
            }
        }

        // Only show as full alpha if the question is currently showing
        // or it is linked with the currently showing step
        val isFullAlpha = (item.stepIdentifier == currentIdentifier) ||
                (item.linkWithNext && (position >= (dataSet.size - 2)))

        (viewHolder as? ChatViewHolder)?.let {
            val text = dataSet[position].text
            val hasLink = text?.contains("https:") ?: false
            it.textView.setTextIsSelectable(hasLink)
            it.textView.text = text
            it.container.alpha = if (isFullAlpha) { 1.0f } else { 0.45f }
        }

        (viewHolder as? GifViewHolder)?.let {
            val gifUrl = item.gifUrl ?: run { return@let }
            glide.load(gifUrl)
                    .placeholder(it.loadingDrawable)
                    .listener(GifLoaderListener(item.stepIdentifier))
                    .into(it.imageView)

            // Accessibility support
            it.imageView.contentDescription = item.text
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

    open fun addItem(stepId: String, message: String?, question: Boolean,
                     linkWithNext: Boolean, cannotEdit: Boolean = false) {
        val item = ConversationItem(stepId, message, question, linkWithNext, cannotEdit = cannotEdit)
        Log.d(LOG_TAG, "addItem(): $stepId - $question: $message")
        if(findExistingQuestion(item)) {
            if(!question) {
                updateOrInsertItem(item)
            } else {
                currentIdentifier = dataSet.last().stepIdentifier
            }
        } else {
            dataSet.add(item)
            currentIdentifier = stepId
            notifyItemInserted(dataSet.size)
        }

        notifyDataSetChanged()
    }

    private fun findExistingQuestion(item: ConversationItem): Boolean {
        val found =  dataSet.find {
            it.stepIdentifier == item.stepIdentifier && it.isQuestion
        }

        return found != null
    }

    private fun findAnswer(stepIdentifier: String): String? {
        return dataSet.find {
            it.stepIdentifier == stepIdentifier && !it.isQuestion
        }?.text
    }

    private fun updateOrInsertItem(item: ConversationItem) {
        val found = dataSet.find {
            it.stepIdentifier == item.stepIdentifier && it.isQuestion == item.isQuestion
        }

        if (found != null) {
            if(item.text != null) {
                found.text = item.text
            } else {
                dataSet.remove(found)
            }
        } else {
            if(item.text != null) {
                val question = dataSet.find { it.stepIdentifier == item.stepIdentifier && it.isQuestion }
                dataSet.add(dataSet.indexOf(question) + 1, item)
            }
            currentIdentifier = dataSet.last().stepIdentifier
        }

    }

    open fun preloadGifs(gifSteps: List<GifStep>) {
        gifSteps.forEach {
            glide.load(it.gifUrl).preload()
        }
    }

    open fun addGif(stepId: String, backupText: String, gifUrl: String) {
        Log.d(LOG_TAG, "addGif(): $stepId")
        val item = ConversationItem(stepId, backupText, false, gifUrl = gifUrl)

        val found =  dataSet.find {
            it.stepIdentifier == item.stepIdentifier
        }

        if(found == null) {
            dataSet.add(item)
            notifyItemInserted(dataSet.size)
        }
    }

    inner class GifLoaderListener(val stepIdentifier: String): RequestListener<Drawable> {
        override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean {
            Log.d(LOG_TAG, "GIF failed to load $stepIdentifier")
            dataSet.firstOrNull { it.stepIdentifier == stepIdentifier }?.let { item ->
                val idx = dataSet.indexOfFirst { it.stepIdentifier == stepIdentifier }
                dataSet.removeAt(idx)
                // Remove the gif and reload backup text
                dataSet.add(item.copy(gifUrl = null, isQuestion = true))
                notifyItemChanged(idx)
            }
            return false
        }

        override fun onResourceReady(resource: Drawable?, model: Any?, target: Target<Drawable>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
            return false
        }
    }
}