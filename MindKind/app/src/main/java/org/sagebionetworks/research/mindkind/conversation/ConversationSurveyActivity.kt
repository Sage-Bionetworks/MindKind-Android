package org.sagebionetworks.research.mindkind.conversation

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ItemDecoration
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import org.sagebionetworks.research.domain.Schema
import org.sagebionetworks.research.domain.result.AnswerResultType
import org.sagebionetworks.research.domain.result.implementations.AnswerResultBase
import org.sagebionetworks.research.domain.result.implementations.TaskResultBase
import org.sagebionetworks.research.mindkind.R
import org.sagebionetworks.research.sageresearch_app_sdk.TaskResultUploader
import org.threeten.bp.Instant
import java.util.*
import javax.inject.Inject
import kotlin.collections.ArrayList

open class ConversationSurveyActivity: AppCompatActivity() {

    companion object {
        const val extraConversationId = "EXTRA_CONVERSATION_SURVEY"
        const val DELAY = 1000L

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
    var questionButton: Button? = null
    var answerButton: Button? = null
    var buttonContainer: ViewGroup? = null
    var handler: Handler? = null

    // Create a ViewModel the first time the system calls an activity's onCreate() method.
    // Re-created activities receive the same ConversationSurveyViewModel
    // instance created by the first activity.
    // Use the 'by viewModels()' Kotlin property delegate
    // from the activity-ktx artifact
    val viewModel: ConversationSurveyViewModel by viewModels()

    var itemCount: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_conversation_survey)

        handler = Handler()

        findViewById<View>(R.id.back_button).setOnClickListener {
            finish()
        }

        questionButton = findViewById(R.id.add_question)

        recyclerView = findViewById(R.id.recycler_view_conversation)
        var llm = LinearLayoutManager(this)
        llm.orientation = LinearLayoutManager.VERTICAL
        recyclerView?.layoutManager = llm

        recyclerView?.addItemDecoration(SpacesItemDecoration(resources.getDimensionPixelSize(R.dimen.converation_recycler_spacing)))

        intent.extras?.getString(extraConversationId)?.let {
            val conversation = ConversationGsonHelper.createGson()
                    .fromJson(it, ConversationSurvey::class.java)

            // Setup the view model and start the conversation
            viewModel.initConversation(conversation)
            startConversation()
        }

        buttonContainer = findViewById(R.id.button_container)

        questionButton?.setOnClickListener {
            addQuestion()
        }

    }

    open fun startConversation() {
        logInfo("startConversation()")
        // TODO: mdephillips 3/3/2021 show first row of recycler view conversation
        val conversation = viewModel.getConversationSurvey().value ?: run { return }

        val list = arrayListOf<ConversationItem>()

        // TODO: this text should come from json
        list.add( ConversationItem("Hello, just a few questions to get your day started.", true))
        recyclerView?.adapter = ConversationAdapter(list)
    }

    private fun addQuestion() {
        val conversation = viewModel.getConversationSurvey().value ?: run { return }
        val steps = conversation.steps

        var count = steps.size
        logInfo("Count: $itemCount - $count")
        if(itemCount > (steps.size-1)) {
            finish()
            return
        }

        val step = steps[itemCount]

        var hasQuestions = false
        (step as? ConversationFormStep)?.let {
            viewModel.userShown(it.identifier)
            hasQuestions = true
            addButtons(findChoices(it.inputFieldId, conversation.inputFields))
        } ?: buttonContainer?.removeAllViews()

        val adapter = recyclerView?.adapter as ConversationAdapter
        adapter.addItem(step.title, true)
        itemCount++
        recyclerView?.smoothScrollToPosition(adapter.itemCount)

        if(!hasQuestions && itemCount < steps.size) {
            handler?.postDelayed({
                addQuestion()
            }, DELAY)
        }
    }

    private fun addAnswer(stepId: String, text: String, value: Any) {
        val adapter = recyclerView?.adapter as ConversationAdapter
        adapter.addItem(text, false)
        recyclerView?.smoothScrollToPosition(adapter.itemCount)

        viewModel.addAnswer()

        handler?.postDelayed({
            addQuestion()
        }, DELAY)
    }

    private fun addButtons(stepId: String, choices: List<ConversationInputFieldChoice>?) {
        buttonContainer?.removeAllViews()

        choices?.forEach { c ->
            val button = Button(this)
            button.text = c.text

            button.setOnClickListener {
                var value: Any = c.text
                (c as? IntegerConversationInputFieldChoice)?.let {
                    value = it.value
                }
                addAnswer(stepId, c.text, value)
                disableAllButtons()
            }

            val llp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT)
            llp.bottomMargin = resources.getDimensionPixelSize(R.dimen.conversation_button_margin)
            buttonContainer?.addView(button, llp)
        }

    }

    private fun disableAllButtons() {
        val count = buttonContainer!!.childCount
        repeat(count) {
            buttonContainer?.getChildAt(it)?.isEnabled = false
        }
    }

    private fun findChoices(id: String, fields: List<ConversationInputField>): List<ConversationInputFieldChoice>? {
        return fields.firstOrNull { it.identifier == id }?.choices
    }
}

class SpacesItemDecoration(private val space: Int) : ItemDecoration() {

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        outRect.left = space
        outRect.right = space
        outRect.bottom = space

        // Add top margin only for the first item to avoid double space between items
        if (parent.getChildAdapterPosition(view!!) == 0) {
            outRect.top = space
        }
    }

}

open class ConversationSurveyViewModel : ViewModel() {

    companion object {
        private val TAG = ConversationSurveyViewModel::class.java.simpleName
    }

    private val startTimeMap = mutableMapOf<String, Instant>()
    private val answersLiveData = MutableLiveData<ArrayList<AnswerResultBase<Any>>>()
    private val uploadCompleteLiveData = MutableLiveData<Boolean>()

    private val conversationSurvey: MutableLiveData<ConversationSurvey> by lazy {
        return@lazy MutableLiveData<ConversationSurvey>()
    }

    @Inject
    lateinit var taskResultUploader: TaskResultUploader

    private val compositeDisposable = CompositeDisposable()

    fun initConversation(conversation: ConversationSurvey) {
        conversationSurvey.value = conversation
    }

    fun getConversationSurvey(): LiveData<ConversationSurvey> {
        return conversationSurvey
    }

    /**
     * @param step the step within the conversation to look for an input field
     * @return the input field for input field step
     */
    fun conversationTitles(): List<String>? {
        val conversation = conversationSurvey.value ?: run { return null }
        return conversation.steps.map { it.title }
    }

    /**
     * @param step the step within the conversation to look for an input field
     * @return the input field for input field step
     */
    fun inputFieldFor(step: ConversationStep): ConversationInputField? {
        val conversation = conversationSurvey.value ?: run { return null }
        return (step as? ConversationFormStep)?.inputField(conversation.inputFields)
    }

    /**
     * Call when user is shown a step, used to later set start times in "addAnswer" func
     */
    fun userShown(stepId: String) {
        startTimeMap[stepId] = Instant.now()
    }

    /**
     * Adds an answer to the answer live data
     */
    fun addAnswer(stepId: String, inputField: ConversationInputFieldChoice, answer: Any?) {
        val startTime = startTimeMap[stepId] ?: Instant.now()
        val endTime = Instant.now()

        (inputField as? IntegerConversationInputFieldChoice)?.let {
            val intAnswer = (answer as? Int) ?: 0
            val answerResult: AnswerResultBase<Any> = AnswerResultBase(
                    stepId, startTime, endTime, intAnswer, AnswerResultType.INTEGER)
            answersLiveData += answerResult
        }
    }

    /**
     * Complete the conversation and upload it to bridge
     * @return live data to monitor for changes
     */
    fun completeConversation(conversationType: String): LiveData<Boolean> {
        val answers = answersLiveData.value ?: arrayListOf()
        val stepHistory = answers.sortedWith(compareBy { it.startTime })
        val startTime = stepHistory.firstOrNull()?.startTime ?: Instant.now()
        val endTime = Instant.now()
        // TODO: mdephillips 3/12/21 get this revision from app config
        val schema = Schema(conversationType, 1)
        val taskResult = TaskResultBase(
                conversationType, startTime, endTime,
                UUID.randomUUID(), schema, stepHistory, listOf())

        // Upload the conversation result
        compositeDisposable.add(
                taskResultUploader.processTaskResult(taskResult)
                .subscribeOn(Schedulers.io())
                .subscribe({
                    Log.i(TAG, "Conversation Upload Complete")
                    uploadCompleteLiveData.postValue(true)
                }, {
                    Log.w(TAG, "Conversation Upload Failed ${it.localizedMessage}")
                    // Archive will upload eventually as we retry later
                    uploadCompleteLiveData.postValue(true)
                }))

        return uploadCompleteLiveData
    }

    /**
     * Adds "+=" operator to mutable live data
     */
    operator fun <T> MutableLiveData<ArrayList<T>>.plusAssign(item: T) {
        val values = this.value ?: arrayListOf()
        values.add(item)
        this.value = values
    }
}
