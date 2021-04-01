package org.sagebionetworks.research.mindkind.conversation

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.sagebionetworks.research.domain.RuntimeTypeAdapterFactory

class ConversationGsonHelper {
    companion object {
        fun createGson(): Gson {
            return GsonBuilder()
                    .registerTypeAdapterFactory(getStepTypeAdapterFactory())
                    .create()
        }

        private fun getStepTypeAdapterFactory(): RuntimeTypeAdapterFactory<ConversationStep> {
            return RuntimeTypeAdapterFactory
                    .of<ConversationStep>(ConversationStep::class.java, "type")
                    .registerSubtype(ConversationInstructionStep::class.java,
                            ConversationStepType.instruction.type)
                    .registerSubtype(ConversationSingleChoiceIntFormStep::class.java,
                            ConversationStepType.singleChoiceInt.type)
                    .registerSubtype(ConversationIntegerFormStep::class.java,
                            ConversationStepType.integer.type)
                    .registerSubtype(ConversationTextFormStep::class.java,
                            ConversationStepType.text.type)
                    .registerSubtype(ConversationTimeOfDayStep::class.java,
                            ConversationStepType.timeOfDay.type)
                    .registerSubtype(GifStep::class.java,
                            ConversationStepType.gif.type)
        }
    }
}

data class ConversationSurvey(
    val identifier: String,
    val type: String?,
    val taskIdentifier: String?,
    val schemaIdentifier: String?,
    val steps: List<ConversationStep>)

abstract class ConversationStep {
    abstract val identifier: String
    abstract val type: String
    abstract val title: String
    abstract val buttonTitle: String
    abstract val optional: Boolean?
}

data class ConversationInstructionStep(
        override val identifier: String,
        override val type: String,
        override val title: String,
        override val buttonTitle: String,
        override val optional: Boolean? = true,
        val continueAfterDelay: Boolean? = false
): ConversationStep()

data class ConversationTextFormStep(
        override val identifier: String,
        override val type: String,
        override val title: String,
        override val buttonTitle: String,
        val maxCharacters: Int,
        val placeholderText: String,
        override val optional: Boolean? = true): ConversationStep()

data class ConversationIntegerFormStep(
        override val identifier: String,
        override val type: String,
        override val title: String,
        override val buttonTitle: String,
        val min: Int,
        val max: Int,
        var maxLines: Int = 4,
        override val optional: Boolean? = true): ConversationStep()

data class ConversationTimeOfDayStep(
        override val identifier: String,
        override val type: String,
        override val title: String,
        override val buttonTitle: String,
        val defaultTime: String,
        override val optional: Boolean? = true): ConversationStep()

data class ConversationSingleChoiceIntFormStep(
        override val identifier: String,
        override val type: String,
        override val title: String,
        override val buttonTitle: String,
        val inputFieldId: String,
        var buttonText: String,
        val choices: List<IntegerConversationInputFieldChoice>,
        override val optional: Boolean? = true
): ConversationStep()

data class IntegerConversationInputFieldChoice(
        val text: String,
        val value: Int)

data class GifStep(
        override val identifier: String,
        override val type: String,
        override val title: String,
        override val buttonTitle: String,
        override val optional: Boolean? = true,
        val gifUrl: String): ConversationStep()

public enum class ConversationStepType(val type: String) {
    instruction("instruction"),
    singleChoiceInt("singleChoice.integer"),
    timeOfDay("timeOfDay"),
    text("text"),
    integer("integer"),
    gif("gif")
}