package org.sagebionetworks.research.mindkind.conversation

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.common.base.Preconditions
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import org.sagebionetworks.research.domain.Schema
import org.sagebionetworks.research.domain.result.AnswerResultType
import org.sagebionetworks.research.domain.result.implementations.AnswerResultBase
import org.sagebionetworks.research.domain.result.implementations.FileResultBase
import org.sagebionetworks.research.domain.result.implementations.TaskResultBase
import org.sagebionetworks.research.domain.result.interfaces.Result
import org.sagebionetworks.research.mindkind.backgrounddata.BackgroundDataService
import org.sagebionetworks.research.mindkind.room.BackgroundDataTypeConverters
import org.sagebionetworks.research.sageresearch.dao.room.AppConfigRepository
import org.sagebionetworks.research.sageresearch_app_sdk.TaskResultUploader
import org.threeten.bp.Instant
import java.io.File
import java.io.FileOutputStream
import java.util.*
import javax.inject.Inject

open class ConversationSurveyViewModel(
        private val taskResultUploader: TaskResultUploader,
        private val appConfigRepo: AppConfigRepository,
        private val cacheDirAbsPath: String) : ViewModel() {

    companion object {
        private val TAG = ConversationSurveyViewModel::class.java.simpleName
    }

    private val startTimeMap = mutableMapOf<String, Instant>()
    private val answersLiveData = MutableLiveData<ArrayList<ConversationAnswer>>()

    // Get updates about the user's progress through the conversation
    public val progressLiveData = MutableLiveData<Float>()

    private val conversationSurvey: MutableLiveData<ConversationSurvey> by lazy {
        return@lazy MutableLiveData<ConversationSurvey>()
    }

    class Factory @Inject constructor(
            private val taskResultUploader: TaskResultUploader,
            private val appConfigRepo: AppConfigRepository,
            private val cacheDirAbsPath: String):
            ViewModelProvider.Factory {

        // Suppress unchecked cast, pre-condition would catch it first anyways
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            Preconditions.checkArgument(modelClass.isAssignableFrom(ConversationSurveyViewModel::class.java))
            return ConversationSurveyViewModel(taskResultUploader, appConfigRepo, cacheDirAbsPath) as T
        }
    }

    private val compositeDisposable = CompositeDisposable()

    public var itemCount: Int = -1

    fun initConversation(conversation: ConversationSurvey) {
        conversationSurvey.value = conversation
    }

    fun getConversationSurvey(): LiveData<ConversationSurvey> {
        return conversationSurvey
    }

    fun getCurrentStep(): ConversationStep? {
        val steps = conversationSurvey.value?.steps ?: run { return null }
        if (itemCount >= 0 && itemCount < steps.size) {
            return steps[itemCount]
        }
        return null
    }

    /**
     * @param step the step within the conversation to look for an input field
     * @return the input field for input field step
     */
    fun conversationTitles(): List<String>? {
        val conversation = conversationSurvey.value ?: run { return null }
        return conversation.steps.map { it.title }
    }

    fun conversationDataType(): String {
        val conversation = conversationSurvey.value ?: run { return "" }
        return conversation.schemaIdentifier ?: ""
    }

    /**
     * Go to the next step in the conversation
     */
    fun goToNextStep() {
        val conversation = conversationSurvey.value ?: run {
            // End of conversation
            return
        }

        if (itemCount < 0) {
            itemCount = 0
            return
        }

        val lastStep = conversation.steps[itemCount]

        itemCount++ // go to next step in the list

        // Check for next step conditionals
        val ifAnsweredConditonalSplit = lastStep.ifUserAnswers?.split(", skip to ") ?: run {
            return
        }
        // Must have format [Answer] skip to [Step Identifier]
        if (ifAnsweredConditonalSplit.size < 2) {
            return
        }

        // Check conditional to see if we need to skip to anywhere
        val lastAnswer = stringAnswerForStep(lastStep.identifier) ?: run {
            return // No answer to compare to
        }

        if (lastAnswer == ifAnsweredConditonalSplit.firstOrNull()) {
            val newNextStep = stepWith(ifAnsweredConditonalSplit.lastOrNull()) ?: run {
                return
            }
            val nextStepIdx = conversationSurvey.value?.steps?.indexOf(newNextStep) ?: run {
                return // Can't find the step
            }
            if (nextStepIdx >= 0) {
                itemCount = nextStepIdx
            }
        }
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
            answersLiveData += ConversationAnswer(
                    step.identifier, startTime, endTime, AnswerResultType.INTEGER, it)
        }

        (answer as? String)?.let {
            answersLiveData += ConversationAnswer(
                    step.identifier, startTime, endTime, AnswerResultType.STRING, it)
        }

        if (answersLiveData.value?.lastOrNull()?.identifier != step.identifier) {
            Log.e(TAG, "Answer class type not supported by survey")
        }
    }

    fun hasAnswers(): Boolean {
        return (answersLiveData.value?.size ?: 0) > 0
    }

    fun stepWith(identifier: String?): ConversationStep? {
        return conversationSurvey.value?.steps?.firstOrNull {
            it.identifier == identifier
        }
    }

    fun stringAnswerForStep(identifier: String): String? {
        return answersLiveData.value?.firstOrNull {
            it.identifier == identifier
        }?.answer?.toString()
    }

    fun isOnLastStep(): Boolean {
        return ((conversationSurvey.value?.steps?.size ?: 1) - 1) == itemCount
    }

    /**
     * Complete the conversation and upload it to bridge
     * @return live data to monitor for changes
     */
    fun completeConversation() {
        val conversationId = conversationSurvey.value?.identifier ?: run { return }
        val answers = answersLiveData.value ?: arrayListOf()
        val stepHistory = ArrayList(answers.sortedWith(compareBy { it.startTime }))
        val startTime = stepHistory.firstOrNull()?.startTime ?: Instant.now()
        val endTime = Instant.now()
        var schema = Schema(conversationId, 1)

        // We want all the conversation answers to be in their own JSON file data.json
        val json = BackgroundDataTypeConverters().gson.toJson(stepHistory)
        val folderPath = "$cacheDirAbsPath${File.separator}conversation"
        val folder = File(folderPath)
        // Create folder to hold data file
        if (!folder.exists()) {
            folder.mkdir()
        }

        val file = File(folder, "data.json")
        val stream = FileOutputStream(file)
        try {
            stream.write(json.toByteArray())
        } finally {
            stream.close()
        }

        val finalStepHistory = arrayListOf<Result>()
        val jsonFileResult = FileResultBase("data",
                startTime, endTime, BackgroundDataService.JSON_MIME_CONTENT_TYPE,
                folderPath + File.separator + "data.json")
        finalStepHistory.add(jsonFileResult)

        // And the  answers map should have the dataType as it's own column in Synapse
        finalStepHistory.add(AnswerResultBase("dataType", startTime, endTime,
                conversationDataType(), AnswerResultType.STRING))

        // Upload the conversation result after looking for the current revision
        compositeDisposable.add(
                appConfigRepo.appConfig.firstOrError().flatMap { appConfig ->
                    appConfig.schemaReferences.firstOrNull { it.id == conversationId }?.let {
                        schema = Schema(it.id, it.revision.toInt())
                    }
                    val taskResult = TaskResultBase(
                            conversationId, startTime, endTime,
                            UUID.randomUUID(), schema, finalStepHistory, listOf())
                    return@flatMap taskResultUploader.processTaskResult(taskResult)
                            .andThen(Single.just(1L))
                }
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

public data class ConversationAnswer(
    public val identifier: String,
    public val startTime: Instant,
    public val endTime: Instant,
    public val type: String,
    public val answer: Any)