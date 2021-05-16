package org.sagebionetworks.research.mindkind.conversation

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.sagebionetworks.research.domain.RuntimeTypeAdapterFactory
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

class ConversationGsonHelper {
    companion object {
        fun createGson(): Gson {
            return GsonBuilder()
                    .registerTypeAdapterFactory(getStepTypeAdapterFactory())
                    .create()
        }

        fun stringFromJsonAsset(context: Context, fileName: String): String? {
            val assetPath = "task/$fileName.json"
            val inputStream = InputStreamReader(context.assets.open(assetPath), StandardCharsets.UTF_8)
            val r = BufferedReader(inputStream)
            val total = StringBuilder()
            var line: String? = null
            while (r.readLine().also({ line = it }) != null) {
                total.append(line).append('\n')
            }
            return total.toString()
        }

        /**
         * @param context can be app, activity, or fragment
         * @param jsonFilename json filename without the ".json" file extension
         * @return a parsed ConversationSurvey that has all it's nested steps expanded
         */
        fun createSurvey(context: Context, jsonFilename: String): ConversationSurvey? {
            val gson = createGson()
            val json = stringFromJsonAsset(context, jsonFilename)
            val conversation = gson.fromJson(json, ConversationSurvey::class.java)

            val newSteps = mutableListOf<ConversationStep>()
            conversation.steps.forEach {
                val nestedStep = (it as? NestedStep) ?: run {
                    newSteps.add(it)
                    return@forEach
                }
                val nestedJson = stringFromJsonAsset(context, nestedStep.filename)
                val nestedConversation = gson.fromJson(nestedJson, ConversationSurvey::class.java)
                newSteps.addAll(nestedConversation.steps)
            }

            return conversation.copy(steps = newSteps)
        }

        private fun getStepTypeAdapterFactory(): RuntimeTypeAdapterFactory<ConversationStep> {
            return RuntimeTypeAdapterFactory
                    .of<ConversationStep>(ConversationStep::class.java, "type")
                    .registerSubtype(
                            ConversationInstructionStep::class.java,
                            ConversationStepType.instruction.type)
                    .registerSubtype(
                            ConversationSingleChoiceIntFormStep::class.java,
                            ConversationStepType.singleChoiceInt.type)
                    .registerSubtype(
                            ConversationSingleChoiceStringFormStep::class.java,
                            ConversationStepType.singleChoiceString.type)
                    .registerSubtype(
                            ConversationIntegerFormStep::class.java,
                            ConversationStepType.integer.type)
                    .registerSubtype(
                            ConversationTextFormStep::class.java,
                            ConversationStepType.text.type)
                    .registerSubtype(
                            ConversationTimeOfDayStep::class.java,
                            ConversationStepType.timeOfDay.type)
                    .registerSubtype(
                            GifStep::class.java,
                            ConversationStepType.gif.type)
                    .registerSubtype(NestedStep::class.java,
                            ConversationStepType.nested.type)
                    .registerSubtype(
                            ConversationSingleChoiceWheelStringStep::class.java,
                            ConversationStepType.singleChoiceWheelString.type)
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
    abstract val ifUserAnswers: String?
}

data class ConversationInstructionStep(
        override val identifier: String,
        override val type: String,
        override val title: String,
        override val buttonTitle: String,
        override val optional: Boolean? = true,
        override val ifUserAnswers: String? = null,
        val continueAfterDelay: Boolean? = false
): ConversationStep()

data class ConversationTextFormStep(
        override val identifier: String,
        override val type: String,
        override val title: String,
        override val buttonTitle: String,
        val maxCharacters: Int,
        val placeholderText: String,
        override val ifUserAnswers: String? = null,
        override val optional: Boolean? = true,
        val maxLines: Int?,
        val inputType: String?): ConversationStep()

data class ConversationIntegerFormStep(
        override val identifier: String,
        override val type: String,
        override val title: String,
        override val buttonTitle: String,
        val min: Int,
        val max: Int,
        var maxLines: Int = 4,
        override val ifUserAnswers: String? = null,
        override val optional: Boolean? = true): ConversationStep()

data class ConversationTimeOfDayStep(
        override val identifier: String,
        override val type: String,
        override val title: String,
        override val buttonTitle: String,
        val defaultTime: String,
        override val ifUserAnswers: String? = null,
        override val optional: Boolean? = true): ConversationStep()

data class ConversationSingleChoiceIntFormStep(
        override val identifier: String,
        override val type: String,
        override val title: String,
        override val buttonTitle: String,
        val choices: List<IntegerConversationInputFieldChoice>,
        override val ifUserAnswers: String? = null,
        override val optional: Boolean? = true
): ConversationStep()

data class ConversationSingleChoiceStringFormStep(
        override val identifier: String,
        override val type: String,
        override val title: String,
        override val buttonTitle: String,
        val choices: List<StringConversationInputFieldChoice>,
        override val ifUserAnswers: String? = null,
        override val optional: Boolean? = true
): ConversationStep()

data class ConversationSingleChoiceWheelStringStep(
        override val identifier: String,
        override val type: String,
        override val title: String,
        override val buttonTitle: String,
        val choices: List<String>,
        override val ifUserAnswers: String? = null,
        override val optional: Boolean? = true
): ConversationStep()

data class IntegerConversationInputFieldChoice(
        val text: String,
        val value: Int)

data class StringConversationInputFieldChoice(
        val text: String,
        val value: String)

data class GifStep(
        override val identifier: String,
        override val type: String,
        override val title: String,
        override val buttonTitle: String,
        override val optional: Boolean? = true,
        override val ifUserAnswers: String? = null,
        val gifUrl: String): ConversationStep()

data class NestedStep(
        override val identifier: String,
        override val type: String,
        override val title: String,
        override val buttonTitle: String,
        override val optional: Boolean? = true,
        override val ifUserAnswers: String? = null,
        val filename: String): ConversationStep()

public enum class ConversationStepType(val type: String) {
    instruction("instruction"),
    singleChoiceInt("singleChoice.integer"),
    singleChoiceString("singleChoice.string"),
    singleChoiceWheelString("singleChoice.wheel.string"),
    timeOfDay("timeOfDay"),
    text("text"),
    integer("integer"),
    gif("gif"),
    nested("nested")
}