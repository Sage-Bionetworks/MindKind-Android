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
                    .registerSubtype(ConversationSingleChoiceInputField::class.java,
                            ConversationFormType.singleChoiceInt.type)
                    .registerSubtype(ConversationIntegerInputField::class.java,
                            ConversationFormType.integer.type)
                    .registerSubtype(ConversationTextInputField::class.java,
                            ConversationFormType.text.type)
                    .registerSubtype(ConversationTimeOfDayInputField::class.java,
                            ConversationFormType.timeOfDay.type)
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
    //abstract val choices: List<IntegerConversationInputFieldChoice>
}

data class ConversationSingleChoiceInputField(
    override val identifier: String,
    override val type: String,
    val choices: List<IntegerConversationInputFieldChoice>): ConversationInputField()

data class ConversationTextInputField(
        override val identifier: String,
        override val type: String,
        val maxCharacters: Int,
        val placeholderText: String): ConversationInputField()

data class ConversationIntegerInputField(
        override val identifier: String,
        override val type: String,
        val min: Int,
        val max: Int): ConversationInputField()

data class ConversationTimeOfDayInputField(
        override val identifier: String,
        override val type: String,
        val defaultTime: String): ConversationInputField()

abstract class ConversationInputFieldChoice {
    abstract val text: String
}
class IntegerConversationInputFieldChoice(
        override val text: String,
        val value: Int): ConversationInputFieldChoice()

class StringConversationInputFieldChoice(
        override val text: String,
        val value: String): ConversationInputFieldChoice()

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
    val inputFieldId: String,
    var buttonText: String,
    val optional: Boolean? = true
): ConversationStep() {

    fun inputField(from: List<ConversationInputField>): ConversationInputField {
        return from.first { it.identifier == this.identifier }
    }
}

public enum class ConversationFormType(val type: String) {
    singleChoiceInt("singleChoice.integer"),
    timeOfDay("timeOfDay"),
    text("text"),
    integer("integer")
}