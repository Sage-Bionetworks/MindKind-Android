package org.sagebionetworks.research.mindkind.conversation

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import org.joda.time.DateTime
import org.joda.time.Days
import org.sagebionetworks.research.domain.RuntimeTypeAdapterFactory
import org.sagebionetworks.research.mindkind.backgrounddata.ProgressInStudy
import org.sagebionetworks.research.mindkind.researchstack.framework.SageResearchStack
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.Exception
import java.nio.charset.StandardCharsets

class ConversationGsonHelper {
    companion object {

        private val TAG = ConversationGsonHelper::class.java.simpleName

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
        fun createSurvey(context: Context, jsonFilename: String, progress: ProgressInStudy): ConversationSurvey? {
            val gson = createGson()
            val json = stringFromJsonAsset(context, jsonFilename)
            val dataGroups = SageResearchStack.SageDataProvider.getInstance().userDataGroups

            val conversation: ConversationSurvey?
            try {
                conversation = gson.fromJson(json, ConversationSurvey::class.java)
            } catch (e: Exception) {
                return null
            }
            var conversationSchemaIdentifier = conversation.schemaIdentifier

            val filteredSteps = mutableListOf<ConversationStep>()
            filteredSteps.addAll(conversation.steps)
            val newSteps = mutableListOf<ConversationStep>()

            // If this conversation is a schedule, there are multiple nestedGroup steps
            // that first need filtered based on the rules and priority
            if (conversation.isSchedule == true) {
                conversation.steps.filter {
                    val ngStep = (it as? NestedGroupStep) ?: run { return@filter false }
                    ngStep.userHasDataGroup?.let { dataGroup ->
                        if (!dataGroups.contains(dataGroup)) {
                            return@filter false
                        }
                    }
                    return@filter shouldInclude(ngStep, progress)
                }.sortedBy {
                    return@sortedBy (it as? NestedGroupStep)?.frequency?.ordinal
                }.firstOrNull()?.let {
                    filteredSteps.clear()
                    filteredSteps.add(it)
                    // Let's also use the schema identifier to track which specific
                    // nested group was the one we are doing today
                    (it as? NestedGroupStep)?.let { nestedGroupStep ->
                        conversationSchemaIdentifier = nestedGroupStep.schemaIdentifier
                        Log.d(TAG, "Run ${conversation.identifier} w/ dataType " +
                                (nestedGroupStep.identifier + nestedGroupStep.schemaIdentifier))
                    }
                }
            }

            filteredSteps.forEach { step ->
                val filenamesToLoad = mutableListOf<String>()
                (step as? NestedStep)?.let {
                    filenamesToLoad.add(it.filename)
                }

                (step as? NestedGroupStep)?.let {
                    filenamesToLoad.addAll(it.filenames)
                }
                if (filenamesToLoad.isEmpty()) {
                    newSteps.add(step)
                    return@forEach
                }

                filenamesToLoad.forEach { filename ->
                    val nestedJson = stringFromJsonAsset(context, filename)
                    val nestedConversation = gson.fromJson(nestedJson, ConversationSurvey::class.java)
                    newSteps.addAll(nestedConversation.steps)
                }
            }

            return conversation.copy(
                    schemaIdentifier = conversationSchemaIdentifier,
                    steps = newSteps)
        }

        fun shouldInclude(step: NestedGroupStep, progress: ProgressInStudy): Boolean {
            return when(step.frequency) {
                NestedGroupFrequency.weekly -> progress.dayOfWeek == step.startDay
                NestedGroupFrequency.weeklyRandom -> progress.dayOfWeek == (1..7).shuffled().first()
                NestedGroupFrequency.once -> true
                else /* .daily */ -> progress.daysFromStart >= step.startDay
            }
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
                    .registerSubtype(NestedGroupStep::class.java,
                            ConversationStepType.nestedGroup.type)
                    .registerSubtype(RandomTitleStep::class.java,
                            ConversationStepType.randomTitle.type)
                    .registerSubtype(
                            ConversationSingleChoiceWheelStringStep::class.java,
                            ConversationStepType.singleChoiceWheelString.type)
                    .registerSubtype(
                            ConversationMultiChoiceCheckboxStringStep::class.java,
                            ConversationStepType.multiChoiceCheckboxString.type)
        }
    }
}

data class ConversationSurvey(
    val identifier: String,
    val type: String?,
    val taskIdentifier: String?,
    val schemaIdentifier: String?,
    val isSchedule: Boolean?,
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

data class ConversationMultiChoiceCheckboxStringStep(
        override val identifier: String,
        override val type: String,
        override val title: String,
        override val buttonTitle: String,
        val choices: List<StringConversationInputFieldChoice>,
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

data class NestedGroupStep(
        override val identifier: String,
        override val type: String,
        override val title: String,
        override val buttonTitle: String,
        override val optional: Boolean? = true,
        override val ifUserAnswers: String? = null,
        val schemaIdentifier: String,
        val filenames: List<String>,
        val frequency: NestedGroupFrequency?,
        val startDay: Int,
        val userHasDataGroup: String?): ConversationStep()

data class RandomTitleStep(
        override val identifier: String,
        override val type: String,
        override val title: String,
        override val buttonTitle: String,
        override val optional: Boolean? = true,
        override val ifUserAnswers: String? = null,
        val titleList: List<String>): ConversationStep()

public enum class ConversationStepType(val type: String) {
    instruction("instruction"),
    singleChoiceInt("singleChoice.integer"),
    singleChoiceString("singleChoice.string"),
    singleChoiceWheelString("singleChoice.wheel.string"),
    timeOfDay("timeOfDay"),
    text("text"),
    integer("integer"),
    gif("gif"),
    nested("nested"),
    nestedGroup("nestedGroup"),
    randomTitle("instruction.random"),
    multiChoiceCheckboxString("multiChoice.checkbox.string")
}

public enum class NestedGroupFrequency(val type: String) {
    // The order matters here, as it will be the priority in which each stomps the other
    // Weekly is highest priority, then weekly random, then daily is the "default
    @SerializedName("weekly")
    weekly("weekly"),
    @SerializedName("weeklyRandom")
    weeklyRandom("weeklyRandom"),
    @SerializedName("daily")
    daily("daily"),
    @SerializedName("once")
    once("once")
}