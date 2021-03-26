package org.sagebionetworks.research.mindkind.conversation

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.text.format.DateFormat
import android.text.format.DateFormat.is24HourFormat
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ItemDecoration
import com.google.android.material.button.MaterialButton
import com.google.common.base.Preconditions
import dagger.android.AndroidInjection
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import org.sagebionetworks.research.domain.Schema
import org.sagebionetworks.research.domain.result.AnswerResultType
import org.sagebionetworks.research.domain.result.implementations.AnswerResultBase
import org.sagebionetworks.research.domain.result.implementations.TaskResultBase
import org.sagebionetworks.research.mindkind.R
import org.sagebionetworks.research.sageresearch_app_sdk.TaskResultUploader
import org.threeten.bp.Instant
import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter
import java.util.*
import javax.inject.Inject
import kotlin.collections.set

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

    @Inject
    lateinit var taskResultUploader: TaskResultUploader

    // Create a ViewModel the first time the system calls an activity's onCreate() method.
    // Re-created activities receive the same ConversationSurveyViewModel
    // instance created by the first activity.
    // Use the 'by viewModels()' Kotlin property delegate
    // from the activity-ktx artifact
    lateinit var viewModel: ConversationSurveyViewModel

    var itemCount: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_conversation_survey)

        handler = Handler()

        viewModel = ViewModelProvider(this,
                ConversationSurveyViewModel.Factory(taskResultUploader)).get()

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
            return
        }

        val step = steps[itemCount]

        var hasQuestions = false
        (step as? ConversationFormStep)?.let {
            viewModel.userShown(it.identifier)
            hasQuestions = true
            var inputs = findInputs(it.inputFieldId, conversation.inputFields)
            when(inputs?.type) {
                ConversationFormType.singleChoiceInt.type ->
                    handleSingleChoice(it, inputs)
                ConversationFormType.integer.type ->
                    handleIntegerInput(it, inputs)
                ConversationFormType.text.type ->
                    handleTextInput(it, inputs)
                ConversationFormType.timeOfDay.type ->
                    handleTimeOfDayInput(it, inputs)
            }
        } ?: buttonContainer?.removeAllViews()

        val adapter = recyclerView?.adapter as ConversationAdapter
        adapter.addItem(step.title, true)
        itemCount++
        recyclerView?.smoothScrollToPosition(adapter.itemCount)

        val isLastItem = itemCount >= steps.size

        if(!hasQuestions && !isLastItem) {
            handler?.postDelayed({
                addQuestion()
            }, DELAY)
        }

        if (isLastItem) {
            viewModel.completeConversation()
        }
    }

    private fun addAnswer(stepId: String, inputField: ConversationInputFieldChoice, value: Any?) {
        val adapter = recyclerView?.adapter as ConversationAdapter
        adapter.addItem(inputField.text, false)
        recyclerView?.smoothScrollToPosition(adapter.itemCount)

        viewModel.addAnswer(stepId, inputField, value)

        handler?.postDelayed({
            addQuestion()
        }, DELAY)
    }

    private fun handleSingleChoice(step: ConversationFormStep, input: ConversationInputField?) {
        val stepId = step.identifier
        val choices = (input as ConversationSingleChoiceInputField).choices
        buttonContainer?.removeAllViews()

        choices?.forEach { c ->
            (this.layoutInflater.inflate(R.layout.conversation_material_button,
                    buttonContainer, false) as? MaterialButton)?.let {

                it.text = c.text

                it.setOnClickListener {
                    var value: Any = c.text
                    (c as? IntegerConversationInputFieldChoice)?.let {
                        value = it.value
                    }
                    addAnswer(stepId, c, value)
                    disableAllButtons()
                }

                val llp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT)
                llp.bottomMargin = resources.getDimensionPixelSize(R.dimen.conversation_button_margin)
                buttonContainer?.addView(it, llp)
            }
        }

        if(step.optional != false) {
            addSkipButton()
        }
    }

    private fun addSkipButton() {
        (this.layoutInflater.inflate(R.layout.conversation_button_unfilled,
                buttonContainer, false) as? MaterialButton)?.let {

            it.setOnClickListener {
                disableAllButtons()
                handler?.postDelayed({
                    addQuestion()
                }, DELAY)
            }
            val llp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT)
            llp.bottomMargin = resources.getDimensionPixelSize(R.dimen.conversation_button_margin)
            buttonContainer?.addView(it, llp)
        }
    }

    private fun disableAllButtons() {
        val count = buttonContainer?.childCount ?: 0
        repeat(count) { idx ->
            buttonContainer?.getChildAt(idx)?.let {
                it.isEnabled = false
                it.alpha = 0.33f
            }
        }
    }

    private fun findInputs(id: String, fields: List<ConversationInputField>): ConversationInputField? {
        return fields.firstOrNull { it.identifier == id }
    }

    private fun handleIntegerInput(step: ConversationFormStep, input: ConversationInputField?) {
        var stepId = step.identifier
        var integerInput = input as ConversationIntegerInputField
        buttonContainer?.removeAllViews()
        var counter = 0
        var current: TextView? = null

        (this.layoutInflater.inflate(R.layout.integer_input,
                buttonContainer, false) as? ViewGroup)?.let {

            current = it.findViewById(R.id.integer_current)

            val neg: View = it.findViewById(R.id.integer_negative)
            val pos: View = it.findViewById(R.id.integer_positive)
            applyBounds(integerInput.min, integerInput.max, counter, neg, pos)

            neg.setOnClickListener {
                counter--
                current?.text = counter.toString()
                applyBounds(integerInput.min, integerInput.max, counter, neg, pos)
            }

            pos.setOnClickListener {
                counter++
                current?.text = counter.toString()
                applyBounds(integerInput.min, integerInput.max, counter, neg, pos)
            }
            buttonContainer?.addView(it)
        }

        (this.layoutInflater.inflate(R.layout.conversation_material_button,
                buttonContainer, false) as? MaterialButton)?.let {

            it.text = step.buttonText

            it.setOnClickListener {
                disableAllButtons()
                val d = current?.text
                // TODO: fix storing/sending data
                val i = StringConversationInputFieldChoice(d.toString(), d.toString())
                addAnswer(stepId, i, d.toString())
            }
            val llp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT)
            llp.bottomMargin = resources.getDimensionPixelSize(R.dimen.conversation_button_margin)
            buttonContainer?.addView(it, llp)
        }

        if(step.optional != false) {
            addSkipButton()
        }
    }

    private fun applyBounds(min: Int, max: Int, current: Int, neg: View, pos: View) {
        if(current <= min) {
            neg.isEnabled = false
            neg.setBackgroundResource(R.drawable.integer_background_disabled)
        } else {
            neg.isEnabled = true
            neg.setBackgroundResource(R.drawable.integer_background)
        }

        if(current >= max) {
            pos.isEnabled = false
            pos.setBackgroundResource(R.drawable.integer_background_disabled)
        } else {
            pos.isEnabled = true
            pos.setBackgroundResource(R.drawable.integer_background)
        }
    }

    private fun handleTextInput(step: ConversationFormStep, input: ConversationInputField?) {
        val stepId = step.identifier
        val textInput = input as ConversationTextInputField
        var inputView: EditText? = null
        buttonContainer?.removeAllViews()

        (this.layoutInflater.inflate(R.layout.text_input,
                buttonContainer, false) as? ViewGroup)?.let {

            inputView = it.findViewById(R.id.text_input)
            inputView?.hint = textInput.placeholderText
            buttonContainer?.addView(it)
        }

        (this.layoutInflater.inflate(R.layout.conversation_material_button,
                buttonContainer, false) as? MaterialButton)?.let {

            it.text = getString(R.string.text_input_submit_button)

            it.setOnClickListener {
                disableAllButtons()
                val d = inputView?.text
                // TODO: fix storing/sending data
                val i = StringConversationInputFieldChoice(d.toString(), d.toString())
                addAnswer(stepId, i, d.toString())
            }
            val llp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT)
            llp.bottomMargin = resources.getDimensionPixelSize(R.dimen.conversation_button_margin)
            buttonContainer?.addView(it, llp)
        }

        if(step.optional != false) {
            addSkipButton()
        }
    }

    private fun handleTimeOfDayInput(step: ConversationFormStep, input: ConversationInputField?) {
        val stepId = step.identifier
        buttonContainer?.removeAllViews()

        (this.layoutInflater.inflate(R.layout.conversation_material_button,
                buttonContainer, false) as? MaterialButton)?.let {

            it.text = step.buttonText

            val activity = this
            it.setOnClickListener {
                val dialog = ConversationTimeOfDayDialog()
                val callback = object: ConversationTimeOfDayDialog.Callback {
                    override fun onDateSelected(d: Date) {
                        // For localization support, switch to whatever clock the user is using
                        val formatter = if (is24HourFormat(baseContext)) {
                            SimpleDateFormat("H:mm", Locale.US)
                        } else {
                            SimpleDateFormat("h:mm aa", Locale.US)
                        }
                        logInfo("Received date callback: " + formatter.format(d))
                        // TODO: fix store/sending data
                        val i = StringConversationInputFieldChoice(formatter.format(d), d.toString())
                        addAnswer(stepId, i, d.toString())
                    }
                }
                dialog.setCallback(callback)
                dialog.show(activity.supportFragmentManager, ConversationTimeOfDayDialog.TAG)
            }
            val llp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT)
            llp.bottomMargin = resources.getDimensionPixelSize(R.dimen.conversation_button_margin)
            buttonContainer?.addView(it, llp)
        }

        if(step.optional != false) {
            addSkipButton()
        }
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

open class ConversationSurveyViewModel(private val taskResultUploader: TaskResultUploader) : ViewModel() {

    companion object {
        private val TAG = ConversationSurveyViewModel::class.java.simpleName
    }

    private val startTimeMap = mutableMapOf<String, Instant>()
    private val answersLiveData = MutableLiveData<ArrayList<AnswerResultBase<Any>>>()

    private val conversationSurvey: MutableLiveData<ConversationSurvey> by lazy {
        return@lazy MutableLiveData<ConversationSurvey>()
    }

    class Factory @Inject constructor(private val taskResultUploader: TaskResultUploader):
            ViewModelProvider.Factory {

        // Suppress unchecked cast, pre-condition would catch it first anyways
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            Preconditions.checkArgument(modelClass.isAssignableFrom(ConversationSurveyViewModel::class.java))
            return ConversationSurveyViewModel(taskResultUploader) as T
        }
    }

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
    fun completeConversation() {
        val conversationId = conversationSurvey.value?.identifier ?: run { return }
        val answers = answersLiveData.value ?: arrayListOf()
        val stepHistory = answers.sortedWith(compareBy { it.startTime })
        val startTime = stepHistory.firstOrNull()?.startTime ?: Instant.now()
        val endTime = Instant.now()
        // TODO: mdephillips 3/12/21 get this revision from app config
        val schema = Schema(conversationId, 1)
        val taskResult = TaskResultBase(
                conversationId, startTime, endTime,
                UUID.randomUUID(), schema, stepHistory, listOf())

        // Upload the conversation result
        compositeDisposable.add(
                taskResultUploader.processTaskResult(taskResult)
                .subscribeOn(Schedulers.io())
                .subscribe({
                    Log.i(TAG, "Conversation Upload Complete")
                }, {
                    Log.w(TAG, "Conversation Upload Failed ${it.localizedMessage}")
                    // Archive will upload eventually as we retry later
                }))
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
