package org.sagebionetworks.research.mindkind.conversation

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.text.format.DateFormat.is24HourFormat
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import androidx.lifecycle.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ItemDecoration
import com.google.android.material.button.MaterialButton
import dagger.android.AndroidInjection
import kotlinx.android.synthetic.main.activity_conversation_survey.*
import kotlinx.android.synthetic.main.integer_input.*
import kotlinx.android.synthetic.main.integer_input.view.*
import org.sagebionetworks.research.mindkind.R
import org.sagebionetworks.research.sageresearch_app_sdk.TaskResultUploader
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

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

        back_button.setOnClickListener {
            finish()
        }

        var llm = LinearLayoutManager(this)
        llm.orientation = LinearLayoutManager.VERTICAL
        recycler_view_conversation.layoutManager = llm

        recycler_view_conversation.addItemDecoration(SpacesItemDecoration(resources.getDimensionPixelSize(R.dimen.converation_recycler_spacing)))

        intent.extras?.getString(extraConversationId)?.let {
            val conversation = ConversationGsonHelper.createGson()
                    .fromJson(it, ConversationSurvey::class.java)

            // Setup the view model and start the conversation
            viewModel.initConversation(conversation)
            startConversation()
        }

        add_question.setOnClickListener {
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
        recycler_view_conversation.adapter = ConversationAdapter(list)
    }

    private fun addQuestion() {
        val conversation = viewModel.getConversationSurvey().value ?: run { return }
        val steps = conversation.steps

        val count = steps.size
        logInfo("Count: $itemCount - $count")
        if(itemCount > (steps.size-1)) {
            viewModel.completeConversation()
            return
        }

        val step = steps[itemCount]

        var hasQuestions = true
        when(step.type) {
            ConversationFormType.singleChoiceInt.type ->
                handleSingleChoice(step as? ConversationSingleChoiceIntFormStep)
            ConversationFormType.integer.type ->
                handleIntegerInput(step as? ConversationIntegerFormStep)
            ConversationFormType.text.type ->
                handleTextInput(step as? ConversationTextFormStep)
            ConversationFormType.timeOfDay.type ->
                handleTimeOfDayInput(step as? ConversationTimeOfDayStep)
            else -> hasQuestions = false
        }

        viewModel.userShown(step.identifier)
        if (!hasQuestions) { // Remove all views for non-question type instruction step
            button_container.removeAllViews()
        }

        val adapter = recycler_view_conversation.adapter as ConversationAdapter
        adapter.addItem(step.title, true)
        itemCount++
        recycler_view_conversation.smoothScrollToPosition(adapter.itemCount)

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

    private fun addAnswer(step: ConversationStep, textAnswer: String, value: Any?) {
        val adapter = recycler_view_conversation.adapter as ConversationAdapter
        adapter.addItem(textAnswer, false)
        recycler_view_conversation.smoothScrollToPosition(adapter.itemCount)

        viewModel.addAnswer(step, value)

        handler?.postDelayed({
            addQuestion()
        }, DELAY)
    }

    private fun handleSingleChoice(choiceIntFormStep: ConversationSingleChoiceIntFormStep?) {
        val step = choiceIntFormStep ?: run { return }
        val choices = step.choices
        button_container.removeAllViews()

        choices.forEach { c ->
            (this.layoutInflater.inflate(R.layout.conversation_material_button,
                    button_container, false) as? MaterialButton)?.let {

                it.text = c.text

                it.setOnClickListener {
                    addAnswer(step, c.text, c.value)
                    disableAllButtons()
                }

                val llp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT)
                llp.bottomMargin = resources.getDimensionPixelSize(R.dimen.conversation_button_margin)
                button_container.addView(it, llp)
            }
        }

        if(step.optional != false) {
            addSkipButton()
        }
    }

    private fun addSkipButton() {
        (this.layoutInflater.inflate(R.layout.conversation_button_unfilled,
                button_container, false) as? MaterialButton)?.let {

            it.setOnClickListener {
                disableAllButtons()
                handler?.postDelayed({
                    addQuestion()
                }, DELAY)
            }
            val llp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT)
            llp.bottomMargin = resources.getDimensionPixelSize(R.dimen.conversation_button_margin)
            button_container.addView(it, llp)
        }
    }

    private fun disableAllButtons() {
        button_container.children.forEach {
            it.isEnabled = false
            it.alpha = 0.33f
        }
    }

    private fun handleIntegerInput(intStep: ConversationIntegerFormStep?) {
        val step = intStep ?: run { return }

        button_container.removeAllViews()
        var counter = 0

        (this.layoutInflater.inflate(R.layout.integer_input,
                button_container, false) as? ViewGroup)?.let { vg ->

            val negView = vg.integer_negative
            val posView = vg.integer_positive
            val curView = vg.integer_current

            applyBounds(step.min, step.max, counter, negView, posView)

            negView.setOnClickListener { _ ->
                counter--
                vg.integer_current.text = counter.toString()
                applyBounds(step.min, step.max, counter, negView, posView)
            }

            posView.setOnClickListener { _ ->
                counter++
                vg.integer_current.text = counter.toString()
                applyBounds(step.min, step.max, counter, negView, posView)
            }
            button_container.addView(vg)

            (this.layoutInflater.inflate(R.layout.conversation_material_button,
                    button_container, false) as? MaterialButton)?.let {

                it.text = step.buttonTitle

                it.setOnClickListener { _ ->
                    disableAllButtons()
                    val text = curView.text?.toString() ?: run { return@setOnClickListener }
                    val intAnswer = text.toInt()
                    addAnswer(step, text, intAnswer)
                }
                val llp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT)
                llp.bottomMargin = resources.getDimensionPixelSize(R.dimen.conversation_button_margin)
                button_container.addView(it, llp)
            }
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

    private fun handleTextInput(textStep: ConversationTextFormStep?) {
        val step = textStep ?: run { return }
        val stepId = step.identifier
        var inputView: EditText? = null
        button_container.removeAllViews()

        (this.layoutInflater.inflate(R.layout.text_input,
                button_container, false) as? ViewGroup)?.let {

            inputView = it.findViewById(R.id.text_input)
            inputView?.hint = step.placeholderText
            button_container.addView(it)
        }

        (this.layoutInflater.inflate(R.layout.conversation_material_button,
                button_container, false) as? MaterialButton)?.let {

            it.text = getString(R.string.text_input_submit_button)

            it.setOnClickListener {
                disableAllButtons()
                val text = inputView?.text?.toString() ?: run { return@setOnClickListener }
                addAnswer(step, text, text)
            }
            val llp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT)
            llp.bottomMargin = resources.getDimensionPixelSize(R.dimen.conversation_button_margin)
            button_container.addView(it, llp)
        }

        if(step.optional != false) {
            addSkipButton()
        }
    }

    private fun handleTimeOfDayInput(timeStep: ConversationTimeOfDayStep?) {
        val step = timeStep ?: run { return }
        button_container.removeAllViews()

        (this.layoutInflater.inflate(R.layout.conversation_material_button,
                button_container, false) as? MaterialButton)?.let {

            it.text = step.buttonTitle

            val activity = this
            it.setOnClickListener {
                val dialog = ConversationTimeOfDayDialog()
                val callback = object: ConversationTimeOfDayDialog.Callback {
                    override fun onDateSelected(d: Date) {
                        disableAllButtons()
                        logInfo("Received date callback: $d")

                        // For localization support, switch to whatever clock the user is using
                        val textFormatter = if (is24HourFormat(baseContext)) {
                            SimpleDateFormat("H:mm", Locale.US)
                        } else {
                            SimpleDateFormat("h:mm aa", Locale.US)
                        }
                        // Answer format should always be the same length and format for researchers
                        val answerFormatter = SimpleDateFormat("hh:mm aa", Locale.US)

                        addAnswer(step, textFormatter.format(d), answerFormatter.format(d))
                    }
                }
                dialog.setCallback(callback)
                dialog.show(activity.supportFragmentManager, ConversationTimeOfDayDialog.TAG)
            }
            val llp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT)
            llp.bottomMargin = resources.getDimensionPixelSize(R.dimen.conversation_button_margin)
            button_container.addView(it, llp)
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
        if (parent.getChildAdapterPosition(view) == 0) {
            outRect.top = space
        }
    }
}
