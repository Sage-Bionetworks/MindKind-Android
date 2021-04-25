package org.sagebionetworks.research.mindkind.conversation

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.common.base.Preconditions
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import org.sagebionetworks.research.domain.Schema
import org.sagebionetworks.research.domain.result.AnswerResultType
import org.sagebionetworks.research.domain.result.implementations.AnswerResultBase
import org.sagebionetworks.research.domain.result.implementations.TaskResultBase
import org.sagebionetworks.research.sageresearch_app_sdk.TaskResultUploader
import org.threeten.bp.Instant
import java.util.*
import javax.inject.Inject

open class ConversationSurveyViewModel(private val taskResultUploader: TaskResultUploader) : ViewModel() {

    companion object {
        private val TAG = ConversationSurveyViewModel::class.java.simpleName
    }

    private val startTimeMap = mutableMapOf<String, Instant>()
    private val answersLiveData = MutableLiveData<ArrayList<AnswerResultBase<Any>>>()

    // Get updates about the user's progress through the conversation
    public val progressLiveData = MutableLiveData<Float>()

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

    public var itemCount: Int = 0

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
     * Call when user is shown a step, used to later set start times in "addAnswer" func
     */
    fun userShown(stepId: String) {
        startTimeMap[stepId] = Instant.now()

        // Update progress
        val steps = conversationSurvey.value?.steps ?: run {
            progressLiveData.postValue(0.0f)
            return
        }
        progressLiveData.postValue(startTimeMap.size.toFloat() / steps.size.toFloat())
    }

    /**
     * Adds an answer to the answer live data
     */
    fun addAnswer(step: ConversationStep, answer: Any?) {
        val startTime = startTimeMap[step.identifier] ?: Instant.now()
        val endTime = Instant.now()

        (answer as? Int)?.let {
            val answerResult: AnswerResultBase<Any> = AnswerResultBase(
                    step.identifier, startTime, endTime, it, AnswerResultType.INTEGER)
            answersLiveData += answerResult
        }

        (answer as? String)?.let {
            val answerResult: AnswerResultBase<Any> = AnswerResultBase(
                    step.identifier, startTime, endTime, it, AnswerResultType.STRING)
            answersLiveData += answerResult
        }

        if (answersLiveData.value?.lastOrNull()?.identifier != step.identifier) {
            Log.e(TAG, "Answer class type not supported by survey")
        }
    }

    fun hasAnswers(): Boolean {
        return (answersLiveData.value?.size ?: 0) > 0
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