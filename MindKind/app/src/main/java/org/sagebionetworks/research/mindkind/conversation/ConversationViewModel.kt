package org.sagebionetworks.research.mindkind.conversation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import org.sagebionetworks.research.domain.result.AnswerResultType
import org.sagebionetworks.research.domain.result.implementations.AnswerResultBase
import org.threeten.bp.Instant
import java.util.*

open class ConversationViewModel(application: Application): AndroidViewModel(application) {

    companion object {
        private val TAG = ConversationViewModel::class.java.simpleName
    }

    private val answerResultData = MutableLiveData<ArrayList<AnswerResultBase<Any>>>()
    private val userShownTimeMap = mutableMapOf<String, Instant>()

    public fun userShown(stepId: String) {
        userShownTimeMap[stepId] = Instant.now()
    }

    /**
     * @param stepId of the question asked
     * @param type of the answer
     * @param answer must match value related to type
     */
    public fun userAnswered(stepId: String, type: ConversationFormType, answer: Any) {
        val startTime = userShownTimeMap[stepId] ?: Instant.now()
        val endTime = Instant.now()

        val answerResult: AnswerResultBase<Any> = when(type) {
            ConversationFormType.singleChoiceInt -> {
                val intAnswer = (answer as? Int) ?: 0
                AnswerResultBase(stepId, startTime, endTime, intAnswer, AnswerResultType.INTEGER)
            }
        }

        answerResultData += answerResult
    }

    // Extension of "+=" operator to mutable live data
    operator fun <T> MutableLiveData<ArrayList<T>>.plusAssign(item: T) {
        val values = this.value ?: arrayListOf()
        values.add(item)
        this.value = values
    }
}