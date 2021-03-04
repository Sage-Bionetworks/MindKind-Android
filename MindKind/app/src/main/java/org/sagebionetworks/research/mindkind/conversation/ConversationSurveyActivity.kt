package org.sagebionetworks.research.mindkind.conversation

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.recyclerview.widget.RecyclerView
import org.sagebionetworks.research.mindkind.R

open class ConversationSurveyActivity: AppCompatActivity() {

    companion object {
        const val extraConversationId = "EXTRA_CONVERSATION_SURVEY"

        fun logInfo(msg: String) {
            Log.i(ConversationSurveyActivity::class.simpleName, msg)
        }

        fun start(baseCtx: Context, conversationSurvey: String) {
            val intent = Intent(baseCtx, ConversationSurveyActivity::class.java)
            intent.putExtra(extraConversationId, conversationSurvey)
            baseCtx.startActivity(intent)
        }
    }

    var recyclerView: RecyclerView? = null

    // Create a ViewModel the first time the system calls an activity's onCreate() method.
    // Re-created activities receive the same ConversationSurveyViewModel
    // instance created by the first activity.
    // Use the 'by viewModels()' Kotlin property delegate
    // from the activity-ktx artifact
    val viewModel: ConversationSurveyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_conversation_survey)

        findViewById<View>(R.id.back_button).setOnClickListener {
            finish()
        }

        recyclerView = findViewById(R.id.recycler_view_conversation)

        intent.extras?.getString(extraConversationId)?.let {
            val conversation = ConversationGsonHelper.createGson()
                    .fromJson(it, ConversationSurvey::class.java)

            // Setup the view model and start the conversation
            viewModel.initConversation(conversation)
            startConversation()
        }
    }

    open fun startConversation() {
        // TODO: mdephillips 3/3/2021 show first row of recycler view conversation
        val conversation = viewModel.getConversationSurvey().value ?: run { return }
        val titles = conversation.steps.map { it.title }
        logInfo(titles.joinToString("\n", "\n", "\n"))
    }
}

open class ConversationSurveyViewModel : ViewModel() {
    private val conversationSurvey: MutableLiveData<ConversationSurvey> by lazy {
        return@lazy MutableLiveData<ConversationSurvey>()
    }

    fun initConversation(conversation: ConversationSurvey) {
        conversationSurvey.value = conversation
    }

    fun getConversationSurvey(): LiveData<ConversationSurvey> {
        return conversationSurvey
    }
}