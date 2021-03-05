package org.sagebionetworks.research.mindkind.conversation

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.sagebionetworks.research.domain.RuntimeTypeAdapterFactory

class ConversationGsonHelper {
    companion object {
        fun createGson(): Gson {
            return GsonBuilder()
                    .registerTypeAdapterFactory(getInputFieldTypeAdapterFactory())
                    .registerTypeAdapterFactory(getStepTypeAdapterFactory())
                    .create()
        }

        private fun getInputFieldTypeAdapterFactory(): RuntimeTypeAdapterFactory<ConversationInputField> {
            return RuntimeTypeAdapterFactory
                    .of<ConversationInputField>(ConversationInputField::class.java, "type")
                    .registerSubtype(ConversationIntegerInputField::class.java, "singleChoice.integer")
        }

        private fun getStepTypeAdapterFactory(): RuntimeTypeAdapterFactory<ConversationStep> {
            return RuntimeTypeAdapterFactory
                    .of<ConversationStep>(ConversationStep::class.java, "type")
                    .registerSubtype(ConversationInfoStep::class.java, "instruction")
                    .registerSubtype(ConversationFormStep::class.java, "form")
        }
    }
}

data class ConversationSurvey(
    val identifier: String,
    val type: String?,
    val taskIdentifier: String?,
    val schemaIdentifier: String?,
    val inputFields: List<ConversationInputField>,
    val steps: List<ConversationStep>)

abstract class ConversationInputField {
    abstract val identifier: String
    abstract val type: String
    abstract val choices: List<IntegerConversationInputFieldChoice>
}

data class ConversationIntegerInputField(
    override val identifier: String,
    override val type: String,
    override val choices: List<IntegerConversationInputFieldChoice>): ConversationInputField()

abstract class ConversationInputFieldChoice {
    abstract val text: String
}
class IntegerConversationInputFieldChoice(
        override val text: String,
        val value: Int): ConversationInputFieldChoice()

abstract class ConversationStep {
    abstract val identifier: String
    abstract val type: String
    abstract val title: String
}

data class ConversationInfoStep(
    override val identifier: String,
    override val type: String,
    override val title: String
): ConversationStep()

data class ConversationFormStep(
    override val identifier: String,
    override val type: String,
    override val title: String,
    val inputFieldId: String
): ConversationStep() {

    fun inputField(from: List<ConversationInputField>): ConversationInputField {
        return from.first { it.identifier == this.identifier }
    }
}
