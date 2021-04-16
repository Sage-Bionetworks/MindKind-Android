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
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import androidx.lifecycle.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ItemDecoration
import com.google.android.material.button.MaterialButton
import dagger.android.AndroidInjection
import kotlinx.android.synthetic.main.activity_conversation_survey.*
import kotlinx.android.synthetic.main.integer_input.view.*
import kotlinx.android.synthetic.main.text_input.view.*
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

        val llm = LinearLayoutManager(this)
        llm.orientation = LinearLayoutManager.VERTICAL
        recycler_view_conversation.layoutManager = llm

        recycler_view_conversation.addItemDecoration(SpacesItemDecoration(resources.getDimensionPixelSize(R.dimen.converation_recycler_spacing)))

        recycler_view_conversation.addOnScrollListener(object: RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                logInfo("onScrollStateChanged(): $newState")
                if(newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    button_container.visibility = View.GONE
                    gradient.visibility = View.GONE
                    scroll_arrow.visibility = View.VISIBLE
                }
            }
        })

        scroll_arrow.setOnClickListener {
            val adapter = recycler_view_conversation.adapter as ConversationAdapter
            button_container.visibility = View.VISIBLE
            gradient.visibility = View.VISIBLE
            scroll_arrow.visibility = View.GONE
            recycler_view_conversation.smoothScrollToPosition(adapter.itemCount)
            //adapter.notifyDataSetChanged()
        }

        intent.extras?.getString(extraConversationId)?.let {
            val conversation = ConversationGsonHelper.createGson()
                    .fromJson(it, ConversationSurvey::class.java)

            // Setup the view model and start the conversation
            viewModel.initConversation(conversation)
            val adapter = ConversationAdapter(this, arrayListOf(), object: ConversationAdapterListener {
                override fun onConversationClicked(stepIdentifier: String, answer: String?) {
                    var step: ConversationStep? = findStep(conversation, stepIdentifier)
                    if(step != null) {
                        button_container.visibility = View.VISIBLE
                        gradient.visibility = View.VISIBLE
                        scroll_arrow.visibility = View.GONE
                        val index = findIndex(conversation, step)
                        val isLastItem = index >= conversation.steps.size
                        viewModel.itemCount--
                        showQuestion(step, answer, isLastItem, false)
                    }
                 }

            })
            recycler_view_conversation.adapter = adapter
            addQuestion(true)

            // Pre-load GIFs for quicker access when they come upMD
            val gifSteps = conversation.steps.filter {
                it.type == ConversationStepType.gif.type && (it as? GifStep) != null
            }.map { it as GifStep }
            adapter.preloadGifs(gifSteps)

        }
    }

    private fun findStep(conversation: ConversationSurvey, stepIdentifier: String) : ConversationStep? {
        return conversation.steps.find { it.identifier == stepIdentifier }
    }

    private fun findIndex(conversation: ConversationSurvey, step: ConversationStep) : Int {
        return conversation.steps.indexOf(step)
    }

    private fun addQuestion(scroll: Boolean) {
        val conversation = viewModel.getConversationSurvey().value ?: run { return }
        val steps = conversation.steps

        val count = steps.size
        logInfo("Count: ${viewModel.itemCount} - $count")
        if(viewModel.itemCount > (steps.size-1)) {
            viewModel.completeConversation()
            return
        }

        val step = steps[viewModel.itemCount]
        val shouldAddItem = (step.type != ConversationStepType.gif.type)

        val adapter = recycler_view_conversation.adapter as ConversationAdapter
        if (shouldAddItem) {
            adapter.addItem(step.identifier, step.title, true)
        }

        val isLastItem = viewModel.itemCount >= steps.size
        showQuestion(step, null, isLastItem, true)

        viewModel.itemCount++
        if(scroll) {
            recycler_view_conversation.smoothScrollToPosition(adapter.itemCount)
        }

    }

    private fun showQuestion(step: ConversationStep, answer: String?, isLastItem: Boolean, scroll: Boolean) {
        var hasQuestions = true

        when(step.type) {
            ConversationStepType.instruction.type ->
                hasQuestions = !handleInstructionItem(step as? ConversationInstructionStep, scroll)
            ConversationStepType.singleChoiceInt.type ->
                handleSingleChoice(step as? ConversationSingleChoiceIntFormStep, scroll)
            ConversationStepType.integer.type ->
                handleIntegerInput(step as? ConversationIntegerFormStep, answer, scroll)
            ConversationStepType.text.type ->
                handleTextInput(step as? ConversationTextFormStep, answer, scroll)
            ConversationStepType.timeOfDay.type ->
                handleTimeOfDayInput(step as? ConversationTimeOfDayStep, answer, scroll)
            ConversationStepType.gif.type -> {
                handleGifInput(step as? GifStep)
            }
            else -> {
                hasQuestions = false
            }
        }

        viewModel.userShown(step.identifier)

        if(!hasQuestions && !isLastItem) {
            handler?.postDelayed({
                addQuestion(scroll)
            }, DELAY)
        }

        if (isLastItem) {
            viewModel.completeConversation()
        }
    }

    private fun addAnswer(step: ConversationStep, textAnswer: String?, value: Any?, scroll: Boolean) {
        val adapter = recycler_view_conversation.adapter as ConversationAdapter
        val id = step.identifier
        adapter.addItem(id, textAnswer, false)
        logInfo("addAnswer(): $id - $textAnswer: scroll[$scroll]")

        if(scroll) {
            recycler_view_conversation.smoothScrollToPosition(adapter.itemCount)
        }

        viewModel.addAnswer(step, value)

        handler?.postDelayed({
            addQuestion(scroll)
        }, DELAY)
    }

    private fun handleInstructionItem(instructionStep: ConversationInstructionStep?, scroll: Boolean): Boolean {
        val step = instructionStep ?: run { return false }
        button_container.removeAllViews()

        (this.layoutInflater.inflate(R.layout.conversation_material_button,
                button_container, false) as? MaterialButton)?.let {

            it.text = step.buttonTitle

            it.setOnClickListener { _ ->
                disableAllButtons()
                handler?.postDelayed({
                    addQuestion(scroll)
                }, DELAY)
            }

            val llp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT)
            llp.bottomMargin = resources.getDimensionPixelSize(R.dimen.conversation_button_margin)
            button_container.addView(it, llp)
        }

        if(step.optional != false) {
            addSkipButton(step, scroll)
        }

        return instructionStep.continueAfterDelay ?: false
    }

    private fun handleSingleChoice(choiceIntFormStep: ConversationSingleChoiceIntFormStep?, scroll: Boolean) {
        val step = choiceIntFormStep ?: run { return }
        val choices = step.choices
        button_container.removeAllViews()

        choices.forEach { c ->
            (this.layoutInflater.inflate(R.layout.conversation_material_button,
                    button_container, false) as? MaterialButton)?.let {

                it.text = c.text

                it.setOnClickListener {
                    addAnswer(step, c.text, c.value, scroll)
                    disableAllButtons()
                }

                val llp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT)
                llp.bottomMargin = resources.getDimensionPixelSize(R.dimen.conversation_button_margin)
                button_container.addView(it, llp)
            }
        }

        if(step.optional != false) {
            addSkipButton(step, scroll)
        }
    }

    private fun addSkipButton(step: ConversationStep, scroll: Boolean) {
        (this.layoutInflater.inflate(R.layout.conversation_button_unfilled,
                button_container, false) as? MaterialButton)?.let {

            it.setOnClickListener {
                disableAllButtons()
                addAnswer(step, null, null, scroll)
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

    private fun handleIntegerInput(intStep: ConversationIntegerFormStep?, answer: String?, scroll: Boolean) {
        val step = intStep ?: run { return }

        button_container.removeAllViews()
        var counter = 0
        if(answer != null) {
            counter = Integer.parseInt(answer)
        }

        (this.layoutInflater.inflate(R.layout.integer_input,
                button_container, false) as? ViewGroup)?.let { vg ->

            val negView = vg.integer_negative
            val posView = vg.integer_positive
            val curView = vg.integer_current

            curView.text = counter.toString()
            applyBounds(step.min, step.max, counter, negView, posView)

            negView.setOnClickListener { _ ->
                counter--
                curView.text = counter.toString()
                applyBounds(step.min, step.max, counter, negView, posView)
            }

            posView.setOnClickListener { _ ->
                counter++
                curView.text = counter.toString()
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
                    addAnswer(step, text, intAnswer, scroll)
                }
                val llp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT)
                llp.bottomMargin = resources.getDimensionPixelSize(R.dimen.conversation_button_margin)
                button_container.addView(it, llp)
            }
        }

        if(step.optional != false) {
            addSkipButton(step, scroll)
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

    private fun handleTextInput(textStep: ConversationTextFormStep?, answer: String?, scroll: Boolean) {
        val step = textStep ?: run { return }
        button_container.removeAllViews()

        (this.layoutInflater.inflate(R.layout.text_input,
                button_container, false) as? ViewGroup)?.let { vg ->

            val inputView = vg.text_input
            inputView?.maxLines = 4
            inputView?.hint = step.placeholderText
            if(answer != null) {
                inputView?.setText(answer)
            }
            button_container.addView(vg)

            (this.layoutInflater.inflate(R.layout.conversation_material_button,
                    button_container, false) as? MaterialButton)?.let {

                it.text = getString(R.string.text_input_submit_button)

                it.setOnClickListener {
                    disableAllButtons()
                    val text = inputView?.text?.toString() ?: run { return@setOnClickListener }
                    addAnswer(step, text, text, scroll)
                }
                val llp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT)
                llp.bottomMargin = resources.getDimensionPixelSize(R.dimen.conversation_button_margin)
                button_container.addView(it, llp)
            }
        }

        if(step.optional != false) {
            addSkipButton(step, scroll)
        }
    }

    private fun handleTimeOfDayInput(timeStep: ConversationTimeOfDayStep?, answer: String?, scroll: Boolean) {
        val step = timeStep ?: run { return }
        button_container.removeAllViews()

        (this.layoutInflater.inflate(R.layout.conversation_material_button,
                button_container, false) as? MaterialButton)?.let {

            it.text = step.buttonTitle

            // For localization support, switch to whatever clock the user is using
            val textFormatter = if (is24HourFormat(baseContext)) {
                SimpleDateFormat("H:mm", Locale.US)
            } else {
                SimpleDateFormat("h:mm aa", Locale.US)
            }

            val activity = this
            it.setOnClickListener {
                var input: Date? = null
                if(answer != null) {
                    input = textFormatter.parse(answer)
                }
                val dialog = ConversationTimeOfDayDialog(input)
                val callback = object: ConversationTimeOfDayDialog.Callback {
                    override fun onDateSelected(d: Date) {
                        disableAllButtons()
                        logInfo("Received date callback: $d")

                        // Answer format should always be the same length and format for researchers
                        val answerFormatter = SimpleDateFormat("hh:mm aa", Locale.US)

                        addAnswer(step, textFormatter.format(d), answerFormatter.format(d), scroll)
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
            addSkipButton(step, scroll)
        }
    }

    private fun handleGifInput(gifStep: GifStep?) {
        val step = gifStep ?: run { return }
        button_container.removeAllViews()
        val adapter = recycler_view_conversation.adapter as ConversationAdapter
        val gifTitle: String? = gifStep.title
        adapter.addGif(gifStep.identifier, gifTitle ?: "", step.gifUrl)
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
