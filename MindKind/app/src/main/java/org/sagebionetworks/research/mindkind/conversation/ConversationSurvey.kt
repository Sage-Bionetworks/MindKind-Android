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
                    .registerSubtype(ConversationInfoStep::class.java, "instruction")
                    .registerSubtype(ConversationSingleChoiceIntFormStep::class.java,
                            ConversationFormType.singleChoiceInt.type)
                    .registerSubtype(ConversationIntegerFormStep::class.java,
                            ConversationFormType.integer.type)
                    .registerSubtype(ConversationTextFormStep::class.java,
                            ConversationFormType.text.type)
                    .registerSubtype(ConversationTimeOfDayStep::class.java,
                            ConversationFormType.timeOfDay.type)
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

data class ConversationInfoStep(
        override val identifier: String,
        override val type: String,
        override val title: String,
        override val buttonTitle: String,
        override val optional: Boolean? = true
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

public enum class ConversationFormType(val type: String) {
    singleChoiceInt("singleChoice.integer"),
    timeOfDay("timeOfDay"),
    text("text"),
    integer("integer")
}