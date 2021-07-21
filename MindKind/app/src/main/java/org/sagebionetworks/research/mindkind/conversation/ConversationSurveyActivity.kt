package org.sagebionetworks.research.mindkind.conversation

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.text.InputType
import android.text.format.DateFormat.is24HourFormat
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.animation.TranslateAnimation
import android.view.inputmethod.InputMethodManager
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import com.shawnlin.numberpicker.NumberPicker
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.get
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ItemDecoration
import com.google.android.material.button.MaterialButton
import dagger.android.AndroidInjection
import kotlinx.android.synthetic.main.activity_conversation_survey.*
import kotlinx.android.synthetic.main.integer_input.view.*
import kotlinx.android.synthetic.main.number_picker.view.*
import kotlinx.android.synthetic.main.text_input.view.*
import org.sagebionetworks.research.mindkind.MindKindApplication
import org.sagebionetworks.research.mindkind.R
import org.sagebionetworks.research.mindkind.backgrounddata.BackgroundDataService
import org.sagebionetworks.research.mindkind.viewmodel.TaskListViewModel
import org.sagebionetworks.research.sageresearch.dao.room.AppConfigRepository
import org.sagebionetworks.research.sageresearch.dao.room.ReportRepository
import org.sagebionetworks.research.sageresearch_app_sdk.TaskResultUploader
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import kotlin.math.roundToInt

open class ConversationSurveyActivity: AppCompatActivity() {

    companion object {
        const val extraConversationId = "EXTRA_CONVERSATION_SURVEY"
        const val DELAY = 1000L

        const val completedDateKey = "ConverstaionCompletedDate"

        fun logInfo(msg: String) {
            Log.i(ConversationSurveyActivity::class.simpleName, msg)
        }

        fun start(baseCtx: Context, conversationSurvey: String) {
            val intent = Intent(baseCtx, ConversationSurveyActivity::class.java)
            intent.putExtra(extraConversationId, conversationSurvey)
            baseCtx.startActivity(intent)
        }

        fun startForResult(activity: Activity, conversationSurvey: String, code: Int) {
            val intent = Intent(activity, ConversationSurveyActivity::class.java)
            intent.putExtra(extraConversationId, conversationSurvey)
            activity.startActivityForResult(intent, code)
        }
    }

    lateinit var sharedPrefs: SharedPreferences

    var exitDialog: ConfirmationDialog? = null

    var handler: Handler? = null

    @Inject
    lateinit var taskResultUploader: TaskResultUploader

    @Inject
    lateinit var appConfigRepo: AppConfigRepository

    @Inject
    lateinit var reportRepo: ReportRepository

    // Create a ViewModel the first time the system calls an activity's onCreate() method.
    // Re-created activities receive the same ConversationSurveyViewModel
    // instance created by the first activity.
    // Use the 'by viewModels()' Kotlin property delegate
    // from the activity-ktx artifact
    lateinit var viewModel: ConversationSurveyViewModel

    var weekInStudy = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_conversation_survey)

        handler = Handler()
        sharedPrefs = BackgroundDataService.createSharedPrefs(this)

        viewModel = ViewModelProvider(this, ConversationSurveyViewModel.Factory(
                taskResultUploader, appConfigRepo, cacheDir.absolutePath, reportRepo)).get()

        close_button.setOnClickListener {
            closeOrBackPressed()
        }

        val llm = LinearLayoutManager(this)
        llm.orientation = LinearLayoutManager.VERTICAL
        recycler_view_conversation.layoutManager = llm

        recycler_view_conversation.addItemDecoration(SpacesItemDecoration(resources.getDimensionPixelSize(R.dimen.converation_recycler_spacing)))

        recycler_view_conversation.addOnScrollListener(object: RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                logInfo("onScrollStateChanged(): $newState")
                if(newState == RecyclerView.SCROLL_STATE_DRAGGING &&
                        (recyclerView.canScrollVertically(-1) )) {
                    if(scroll_arrow.visibility == View.GONE) {
                        hideButtonContainer()
                    }
                }
            }
        })

        scroll_arrow.setOnClickListener {
            val adapter = recycler_view_conversation.adapter as ConversationAdapter
            showButtonContainer()
            recycler_view_conversation.smoothScrollToPosition(adapter.itemCount)
            viewModel.itemCount--
            addQuestion(true)
        }

        viewModel.progressLiveData.observe(this, Observer { progress ->
            val newProgress = ((progress ?: 0f) * 100).roundToInt()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                conversation_progress_bar.setProgress(newProgress, true)
            } else {
                conversation_progress_bar.progress = newProgress
            }
        })

        viewModel.getAllSurveyAnswers().observe(this, Observer {
            // TODO: mdephillips 6/17/21 pre-computed the return of results for all questions
        })

        intent.extras?.getString(extraConversationId)?.let {
            val progress = TaskListViewModel.cachedProgressInStudy(sharedPrefs)
            weekInStudy = progress?.week ?: 1
            val conversation = ConversationGsonHelper.createSurvey(this, it, progress) ?: run {
                AlertDialog.Builder(this)
                        .setMessage(R.string.conversation_error_msg)
                        .setNeutralButton(R.string.rsb_ok) { dialog, which ->
                            finish()
                        }.show()
                return
            }

            // Setup the view model and start the conversation
            viewModel.initConversation(conversation)
            val adapter = ConversationAdapter(this, arrayListOf(), object: ConversationAdapterListener {
                override fun onConversationClicked(stepIdentifier: String, answer: String?, position: Int) {
                    var step: ConversationStep? = findStep(conversation, stepIdentifier)
                    if(step != null) {
                        showButtonContainer()
                        val index = findIndex(conversation, step)
                        val isLastItem = index >= conversation.steps.size
                        showBottomInputView(step, answer, isLastItem, false)
                        recycler_view_conversation.scrollToPosition(position+1)
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

    override fun onBackPressed() {
        if(viewModel.hasAnswers() && (exitDialog?.dialog?.isShowing != true)) {
            closeOrBackPressed()
            return
        }
        super.onBackPressed()
    }

    private fun closeOrBackPressed() {
        if(viewModel.hasAnswers()) {
            exitDialog = ConfirmationDialog.newInstance(getString(R.string.conversation_confirmation_title),
                    getString(R.string.conversation_confirmation_message),
                    getString(R.string.conversation_confirmation_continue),
                    getString(R.string.conversation_confirmation_quit))
            exitDialog?.show(supportFragmentManager, ConfirmationDialog.TAG)
            exitDialog?.setActionListener {
                logInfo("Action listener")
                finish()
            }
        } else {
            finish()
        }
    }

    private fun completeConversation() {
        viewModel.completeConversation(sharedPrefs)
        setResult(RESULT_OK)
        finish()
    }

    fun View.slideDown(duration: Int = 500) {
        visibility = View.VISIBLE
        val animate = TranslateAnimation(0f, 0f, 0f, this.height.toFloat())
        animate.duration = duration.toLong()
        animate.fillAfter = false
        this.visibility = View.GONE
        this.startAnimation(animate)
    }

    fun View.slideUp(duration: Int = 500) {
        visibility = View.VISIBLE
        val animate = TranslateAnimation(0f, 0f, this.height.toFloat(), 0f)
        animate.duration = duration.toLong()
        animate.fillAfter = false
        this.startAnimation(animate)
    }

    private fun hideButtonContainer() {
        button_container.slideDown(250)
        gradient.visibility = View.GONE
        scroll_arrow.visibility = View.VISIBLE
        scroll_arrow.bringToFront()
    }

    private fun showButtonContainer() {
        button_container.slideUp(5)
        gradient.visibility = View.VISIBLE
        scroll_arrow.visibility = View.GONE
    }

    private fun findStep(conversation: ConversationSurvey, stepIdentifier: String) : ConversationStep? {
        return conversation.steps.find { it.identifier == stepIdentifier }
    }

    private fun findIndex(conversation: ConversationSurvey, step: ConversationStep) : Int {
        return conversation.steps.indexOf(step)
    }

    private fun addQuestion(scroll: Boolean) {

        viewModel.goToNextStep()

        val currentStep = viewModel.getCurrentStep() ?: run {
            completeConversation()
            return
        }

        // Edge case treatment of random AI assignment, assign the AI, and go to next step
        if (currentStep.type == ConversationStepType.assignRandomAi.type) {
            handleRandomAi(currentStep as? AssignRandomAiStep)
            addQuestion(scroll)
            return
        }

        val adapter = recycler_view_conversation.adapter as ConversationAdapter

        val shouldLinkWithNext =
                (true == (currentStep as? ConversationInstructionStep)?.continueAfterDelay) ||
                (true == (currentStep as? RandomTitleStep)?.continueAfterDelay)

        val cannotEdit = currentStep is ConversationInstructionStep || currentStep is GifStep
        when (currentStep.type) {
            ConversationStepType.randomTitle.type -> {
                val randomTitleStep = (currentStep as? RandomTitleStep)
                if (randomTitleStep?.useWeekNumberAsIndex == true) {
                    val weekInStudyIdx = kotlin.math.max(weekInStudy - 1, 0)
                    if (weekInStudyIdx < randomTitleStep.titleList.size) {
                        randomTitleStep.titleList[weekInStudyIdx]
                    } else {
                        randomTitleStep.titleList.firstOrNull()
                    }
                } else {
                    randomTitleStep?.titleList?.shuffled()?.firstOrNull()
                }
            }
            ConversationStepType.gif.type -> null
            else -> currentStep.title
        }?.let {
            // Add to the answer map which random title we showed the user
            if (currentStep.type == ConversationStepType.randomTitle.type) {
                viewModel.addAnswer(currentStep, it)
            }
            adapter.addItem(currentStep.identifier, it, true, shouldLinkWithNext, cannotEdit)
        }

        showBottomInputView(currentStep, null, viewModel.isOnLastStep(), true)

        if(scroll) {
            recycler_view_conversation.smoothScrollToPosition(adapter.itemCount)
        }
    }

    private fun showBottomInputView(step: ConversationStep, answer: String?, isLastItem: Boolean, scroll: Boolean) {
        var hasQuestions = true

        when(step.type) {
            ConversationStepType.instruction.type ->
                hasQuestions = !handleInstructionItem(step, scroll)
            ConversationStepType.randomTitle.type ->
                hasQuestions = !handleInstructionItem(step, scroll)
            ConversationStepType.singleChoiceInt.type ->
                handleIntSingleChoice(step as? ConversationSingleChoiceIntFormStep, scroll)
            ConversationStepType.singleChoiceString.type ->
                handleStringSingleChoice(step as? ConversationSingleChoiceStringFormStep, scroll)
            ConversationStepType.integer.type ->
                handleIntegerInput(step as? ConversationIntegerFormStep, answer, scroll)
            ConversationStepType.text.type ->
                handleTextInput(step as? ConversationTextFormStep, answer, scroll)
            ConversationStepType.singleChoiceWheelString.type ->
                handleWheelStringInput(step as? ConversationSingleChoiceWheelStringStep, answer, scroll)
            ConversationStepType.timeOfDay.type ->
                handleTimeOfDayInput(step as? ConversationTimeOfDayStep, answer, scroll)
            ConversationStepType.multiChoiceCheckboxString.type ->
                handleMultiChoiceCheckboxStringInput(step as? ConversationMultiChoiceCheckboxStringStep, answer, scroll)
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
    }

    private fun addAnswer(step: ConversationStep, textAnswer: String?, value: Any?, scroll: Boolean) {
        val adapter = recycler_view_conversation.adapter as ConversationAdapter
        val id = step.identifier
        adapter.addItem(id, textAnswer, false, linkWithNext = false)
        logInfo("addAnswer(): $id - $textAnswer: scroll[$scroll]")

        if(scroll) {
            recycler_view_conversation.smoothScrollToPosition(adapter.itemCount)

            handler?.postDelayed({
                addQuestion(scroll)
            }, DELAY)
        } else {
            // this is edit
            hideButtonContainer()
        }

        viewModel.addAnswer(step, value)

    }

    private fun handleInstructionItem(
            instructionStep: ConversationStep?, scroll: Boolean): Boolean {

        val step = instructionStep ?: run { return false }
        button_container.removeAllViews()

        val continueAfterDelay =
                ((instructionStep as? ConversationInstructionStep)?.continueAfterDelay ?: false) ||
                ((instructionStep as? RandomTitleStep)?.continueAfterDelay ?: false)

        // If continuing after delay, we don't add the bottom button layout
        if (continueAfterDelay) {
            return continueAfterDelay
        }

        (this.layoutInflater.inflate(R.layout.conversation_material_button,
                button_container, false) as? MaterialButton)?.let {

            it.text = step.buttonTitle

            it.setOnClickListener { _ ->
                disableAllButtonsAndHideKeyboard()
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

        return continueAfterDelay
    }

    private fun handleStringSingleChoice(choiceStringFormStep: ConversationSingleChoiceStringFormStep?, scroll: Boolean) {
        val step = choiceStringFormStep ?: run { return }
        val choices = step.choices
        button_container.removeAllViews()

        choices.forEach { c ->
            (this.layoutInflater.inflate(R.layout.conversation_material_button,
                    button_container, false) as? MaterialButton)?.let {

                it.text = c.text

                it.setOnClickListener {
                    addAnswer(step, c.text, c.value, scroll)
                    disableAllButtonsAndHideKeyboard()
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

    private fun handleIntSingleChoice(choiceIntFormStep: ConversationSingleChoiceIntFormStep?, scroll: Boolean) {
        val step = choiceIntFormStep ?: run { return }
        val choices = step.choices
        button_container.removeAllViews()

        choices.forEach { c ->
            (this.layoutInflater.inflate(R.layout.conversation_material_button,
                    button_container, false) as? MaterialButton)?.let {

                it.text = c.text

                it.setOnClickListener {
                    addAnswer(step, c.text, c.value, scroll)
                    disableAllButtonsAndHideKeyboard()
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

    private fun handleWheelStringInput(choiceIntFormStep: ConversationSingleChoiceWheelStringStep?, answer: String?, scroll: Boolean) {
        val step = choiceIntFormStep ?: run { return }
        val choices = step.choices
        button_container.removeAllViews()

        (this.layoutInflater.inflate(R.layout.number_picker,
                button_container, false) as? NumberPicker)?.let {

            it.displayedValues = choices.toTypedArray()
            it.wrapSelectorWheel = false
            it.minValue = 0
            it.maxValue = choices.size - 1
            if (answer != null) {
                it.value = choices.indexOf(answer)
            }

            button_container.addView(it)
        }

        (this.layoutInflater.inflate(R.layout.conversation_material_button,
                button_container, false) as? MaterialButton)?.let {

            it.text = step.buttonTitle
            it.setOnClickListener {
                var numberPicker = button_container.number_picker
                var select = numberPicker.value
                addAnswer(step, choices[select], choices[select], scroll)
                disableAllButtonsAndHideKeyboard()
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

    private fun handleMultiChoiceCheckboxStringInput(choiceIntFormStep: ConversationMultiChoiceCheckboxStringStep?,
                                                     answer: String?, scroll: Boolean) {
        val step = choiceIntFormStep ?: run { return }
        val choices = step.choices
        button_container.removeAllViews()
        var selected: Array<String>? = null
        if(answer != null) {
            selected = answer.split(", ").toTypedArray()
        }

        val sv = ScrollView(this)
        val llp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelSize(R.dimen.conversation_checkbox_input_height))
        button_container.addView(sv, llp)

        val ll = LinearLayout(this)
        ll.orientation = LinearLayout.VERTICAL
        val llp2 = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
        sv.addView(ll, llp2)

        choices.forEach { c ->
            (this.layoutInflater.inflate(R.layout.conversation_checkbox,
                    ll, false) as? CheckBox)?.let {

                it.text = c.text
                if(selected != null && selected.contains(c.text)) {
                    it.isChecked = true
                }

                ll.addView(it, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT))
            }
        }

        (this.layoutInflater.inflate(R.layout.conversation_material_button,
                button_container, false) as? MaterialButton)?.let {

            it.text = step.buttonTitle
            it.setOnClickListener {
                val newSelected = ArrayList<String>()
                for (i in 0..(choices.size-1)) {
                    val cb = ll.getChildAt(i) as CheckBox
                    if(cb.isChecked) {
                        newSelected.add(choices[i].text)
                    }
                }

                val values = newSelected.joinToString()
                addAnswer(step, values, values, scroll)
                disableAllButtonsAndHideKeyboard()
            }
            button_container.addView(it,
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT))
        }

        if(step.optional != false) {
            addSkipButton(step, scroll)
        }
    }

    private fun addSkipButton(step: ConversationStep, scroll: Boolean) {
        (this.layoutInflater.inflate(R.layout.conversation_button_unfilled,
                button_container, false) as? MaterialButton)?.let {

            it.setOnClickListener {
                disableAllButtonsAndHideKeyboard()
                addAnswer(step, null, null, scroll)
            }
            val llp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT)
            llp.bottomMargin = resources.getDimensionPixelSize(R.dimen.conversation_button_margin)
            button_container.addView(it, llp)
        }
    }

    private fun disableAllButtonsAndHideKeyboard() {
        button_container.children.forEach {
            it.isEnabled = false
            it.alpha = 0.33f
        }
        hideKeyboard()
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
                    disableAllButtonsAndHideKeyboard()
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
            if(step.maxLines != null) {
                inputView?.maxLines = step.maxLines
            }

            if(step.inputType == "integer") {
                inputView?.inputType = InputType.TYPE_CLASS_NUMBER
            }

            inputView?.hint = step.placeholderText
            if(answer != null) {
                inputView?.setText(answer)
            }

            button_container.addView(vg)

            (this.layoutInflater.inflate(R.layout.conversation_material_button,
                    button_container, false) as? MaterialButton)?.let {

                it.text = if (step.buttonTitle.isEmpty()) {
                    getString(R.string.text_input_submit_button)
                } else {
                    step.buttonTitle
                }

                it.setOnClickListener {
                    disableAllButtonsAndHideKeyboard()
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

    open fun hideKeyboard() {
        (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)?.let { imm ->
            // Find the currently focused view, so we can grab the correct window token from it.
            // If no view currently has focus, create a new one, just so we can grab a window token from it
            val view = currentFocus ?: View(this)
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    private fun handleTimeOfDayInput(timeStep: ConversationTimeOfDayStep?, answer: String?, scroll: Boolean) {
        val step = timeStep ?: run { return }
        button_container.removeAllViews()

        (this.layoutInflater.inflate(R.layout.conversation_material_button,
                button_container, false) as? MaterialButton)?.let {

            it.text = step.buttonTitle

            // For localization support, switch to whatever clock the user is using
            val timeFormattor12 = SimpleDateFormat("h:mm aa", Locale.US)
            val textFormatter = if (is24HourFormat(baseContext)) {
                SimpleDateFormat("H:mm", Locale.US)
            } else {
                timeFormattor12
            }

            val activity = this
            it.setOnClickListener {
                var input: Date? = null
                if(answer != null) {
                    input = textFormatter.parse(answer)
                } else if (step.defaultTime != null) {
                    input = timeFormattor12.parse(step.defaultTime)
                }
                val dialog = ConversationTimeOfDayDialog(input)
                val callback = object: ConversationTimeOfDayDialog.Callback {
                    override fun onDateSelected(d: Date) {
                        disableAllButtonsAndHideKeyboard()
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

    private fun handleRandomAi(randomAiStep: AssignRandomAiStep?) {
        val step = randomAiStep ?: run { return }
        val randomAi = MindKindApplication.generateRandomAi()
        viewModel.addAnswer(step, randomAi)
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
