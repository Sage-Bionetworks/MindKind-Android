package org.sagebionetworks.research.arcvalidation.researchstack.framework;

import static org.sagebionetworks.research.arcvalidation.researchstack.framework.SageSurveyItemAdapter.*;

import android.content.Context;
import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.sagebionetworks.researchstack.backbone.ResourceManager;
import org.sagebionetworks.researchstack.backbone.ResourcePathManager;
import org.sagebionetworks.researchstack.backbone.answerformat.AnswerFormat;
import org.sagebionetworks.researchstack.backbone.model.TaskModel;
import org.sagebionetworks.researchstack.backbone.model.survey.BooleanQuestionSurveyItem;
import org.sagebionetworks.researchstack.backbone.model.survey.ChoiceQuestionSurveyItem;
import org.sagebionetworks.researchstack.backbone.model.survey.IntegerRangeSurveyItem;
import org.sagebionetworks.researchstack.backbone.model.survey.QuestionSurveyItem;
import org.sagebionetworks.researchstack.backbone.model.survey.SurveyItem;
import org.sagebionetworks.researchstack.backbone.model.survey.SurveyItemType;
import org.sagebionetworks.researchstack.backbone.model.survey.TextfieldSurveyItem;
import org.sagebionetworks.researchstack.backbone.model.survey.factory.SurveyFactory;
import org.sagebionetworks.researchstack.backbone.model.taskitem.TaskItem;
import org.sagebionetworks.researchstack.backbone.model.taskitem.TaskItemAdapter;
import org.sagebionetworks.researchstack.backbone.step.QuestionStep;
import org.sagebionetworks.researchstack.backbone.task.Task;
import org.sagebionetworks.bridge.researchstack.onboarding.BridgeSurveyFactory;
import org.sagebionetworks.research.arcvalidation.researchstack.framework.step.SageFormStep;
import org.sagebionetworks.research.arcvalidation.researchstack.framework.step.SageFormSurveyItem;
import org.sagebionetworks.research.arcvalidation.researchstack.framework.step.SageInstructionStep;
import org.sagebionetworks.research.arcvalidation.researchstack.framework.step.SageInstructionSurveyItem;
import org.sagebionetworks.research.arcvalidation.researchstack.framework.step.SagePhoneInstructionStep;
import org.sagebionetworks.research.arcvalidation.researchstack.framework.step.SageSmartSurveyTask;
import org.sagebionetworks.research.arcvalidation.researchstack.framework.step.body.SageBooleanAnswerFormat;
import org.sagebionetworks.research.arcvalidation.researchstack.framework.step.body.SageCheckboxAnswerFormat;
import org.sagebionetworks.research.arcvalidation.researchstack.framework.step.body.SageChoiceAnswerFormat;
import org.sagebionetworks.research.arcvalidation.researchstack.framework.step.body.SageIntegerAnswerFormat;
import org.sagebionetworks.research.arcvalidation.researchstack.framework.step.body.SageMultiCheckboxAnswerFormat;
import org.sagebionetworks.research.arcvalidation.researchstack.framework.step.body.SageRadioButtonAnswerFormat;
import org.sagebionetworks.research.arcvalidation.researchstack.framework.step.body.SageSpinnerAnswerFormat;
import org.sagebionetworks.research.arcvalidation.researchstack.framework.step.body.SageTextQuestionBody;

import java.util.Collections;
import java.util.List;

public class SageTaskFactory extends BridgeSurveyFactory {

    private Gson gson;

    public SageTaskFactory() {
        super();
        gson = createGson();
    }

    public Task createTask(Context context, String resourceName) {
        ResourcePathManager.Resource resource = ResourceManager.getInstance().getResource(resourceName);
        String json = ResourceManager.getResourceAsString(context,
                ResourceManager.getInstance().generatePath(resource.getType(), resource.getName()));
        Gson gson = createGson(); // Do not store this gson as a member variable, it has a link to Context
        TaskItem taskItem = gson.fromJson(json, TaskItem.class);
        return super.createTask(context, taskItem);
    }

    public Gson getGson() {
        return gson;
    }

    @NonNull
    public SageSmartSurveyTask createMpSmartSurveyTask(
            @NonNull Context context, @NonNull TaskModel taskModel) {
        return new SageSmartSurveyTask(context, taskModel);
    }

    @Override
    protected void setupCustomStepCreator() {
        setCustomStepCreator(new MpCustomStepCreator());
    }

    private Gson createGson() {
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(SurveyItem.class, new SageSurveyItemAdapter());
        builder.registerTypeAdapter(TaskItem.class, new TaskItemAdapter());
        return builder.create();
    }

    protected class MpCustomStepCreator extends BridgeCustomStepCreator {
        @Override
        public org.sagebionetworks.researchstack.backbone.step.Step createCustomStep(
                Context context, SurveyItem item, boolean isSubtaskStep, SurveyFactory factory) {
            if (item.getCustomTypeValue() != null) {
                switch (item.getCustomTypeValue()) {
                    case SageSurveyItemAdapter.MP_INSTRUCTION_SURVEY_ITEM_TYPE:
                        if (!(item instanceof SageInstructionSurveyItem)) {
                            throw new IllegalStateException(
                                    "Error in json parsing, Mp_instruction types must be MpInstructionSurveyItem");
                        }
                        return createMpInstructionStep(context, (SageInstructionSurveyItem) item);
                    case SageSurveyItemAdapter.MP_INSTRUCTION_PHONE_SURVEY_ITEM_TYPE:
                        if (!(item instanceof SageInstructionSurveyItem)) {
                            throw new IllegalStateException(
                                    "Error in json parsing, Mp_phone_instruction types must " +
                                            "be MpInstructionSurveyItem");
                        }
                        return createMpPhoneInstructionStep((SageInstructionSurveyItem) item);
                    case SageSurveyItemAdapter.MP_FORM_SURVEY_ITEM_TYPE:
                        if (!(item instanceof SageFormSurveyItem)) {
                            throw new IllegalStateException("Error in json parsing, Mp_form types must be MpFormSurveyItem");
                        }
                        return createMpFormStep(context, (SageFormSurveyItem)item);
                    case MP_BOOLEAN_SURVEY_ITEM_TYPE:
                    case MP_INTEGER_SURVEY_ITEM_TYPE:
                    case MP_MULTIPLE_CHOICE_SURVEY_ITEM_TYPE:
                    case MP_SINGLE_CHOICE_SURVEY_ITEM_TYPE:
                    case MP_CHECKBOX_SURVEY_ITEM_TYPE:
                    case MP_MULTI_CHECKBOX_SURVEY_ITEM_TYPE:
                        if (!(item instanceof QuestionSurveyItem)) {
                            throw new IllegalStateException("Error in json parsing " + item.getCustomTypeValue() + ", types must be QuestionSurveyItem");
                        }
                        // Even though these weren't wrapped in a form step, we are going to wrap
                        // them in a MpFormStep so that the UI looks appropriate
                        QuestionSurveyItem questionItem = (QuestionSurveyItem)item;
                        SageFormSurveyItem compoundQuestionSurveyItem = new MpFormSurveyItemWrapper();
                        compoundQuestionSurveyItem.identifier = item.identifier + "Form";
                        compoundQuestionSurveyItem.items = Collections.singletonList(item);
                        compoundQuestionSurveyItem.skipIdentifier = questionItem.skipIdentifier;
                        compoundQuestionSurveyItem.skipIfPassed = questionItem.skipIfPassed;
                        compoundQuestionSurveyItem.expectedAnswer = questionItem.expectedAnswer;
                        return createMpFormStep(context, compoundQuestionSurveyItem);
                }
            }
            return super.createCustomStep(context, item, isSubtaskStep, factory);
        }
    }

    public static class MpFormSurveyItemWrapper extends SageFormSurveyItem {
        /* Default constructor needed for serilization/deserialization of object */
        public MpFormSurveyItemWrapper() {
            super();
        }

        @Override
        public String getCustomTypeValue() {
            return SageSurveyItemAdapter.MP_FORM_SURVEY_ITEM_TYPE;
        }
    }

    @Override
    public AnswerFormat createCustomAnswerFormat(Context context, QuestionSurveyItem item) {
        if (item.getCustomTypeValue() != null) {
            switch (item.getCustomTypeValue()) {
                case MP_BOOLEAN_SURVEY_ITEM_TYPE:
                    return createMpBooleanAnswerFormat(context, item);
                case MP_TEXT_SURVEY_ITEM_TYPE:
                    return createMpTextAnswerFormat(item);
                case MP_INTEGER_SURVEY_ITEM_TYPE:
                    return createMpIntegerAnswerFormat(context, item);
                case MP_MULTIPLE_CHOICE_SURVEY_ITEM_TYPE:
                case MP_SINGLE_CHOICE_SURVEY_ITEM_TYPE:
                    return createMpChoiceAnswerFormat(context, item);
                case MP_CHECKBOX_SURVEY_ITEM_TYPE:
                    return createMpCheckboxAnswerFormat(context, item);
                case MP_MULTI_CHECKBOX_SURVEY_ITEM_TYPE:
                    return createMpMultiCheckboxAnswerFormat(context, item);
                case MP_SPINNER_SURVEY_ITEM_TYPE:
                    return createBpSpinnerAnswerFormat(context, item);
            }
        }
        return super.createCustomAnswerFormat(context, item);
    }

    protected SageFormStep createMpFormStep(Context context, SageFormSurveyItem item) {
        if (item.items == null || item.items.isEmpty()) {
            throw new IllegalStateException("compound surveys must have step items to proceed");
        }
        List<QuestionStep> questionSteps = super.formStepCreateQuestionSteps(context, item);
        SageFormStep step = new SageFormStep(item.identifier, item.title, item.text, questionSteps);
        fillMpFormStep(step, item);
        return step;
    }

    protected void fillMpFormStep(SageFormStep step, SageFormSurveyItem item) {
        fillNavigationFormStep(step, item);
        if (item.statusBarColorRes != null) {
            step.statusBarColorRes = item.statusBarColorRes;
        }
        if (item.backgroundColorRes != null) {
            step.backgroundColorRes = item.backgroundColorRes;
        }
        if (item.imageColorRes != null) {
            step.imageBackgroundColorRes = item.imageColorRes;
        }
        if (item.buttonTitle != null) {
            step.buttonTitle = item.buttonTitle;
        }
        if (item.hideBackButton != null && item.hideBackButton) {
            step.hideBackButton = item.hideBackButton;
        }
        if (item.textContainerBottomPaddingRes != null) {
            step.textContainerBottomPaddingRes = item.textContainerBottomPaddingRes;
        }
        if (item.bottomLinkTaskId != null) {
            step.bottomLinkTaskId = item.bottomLinkTaskId;
        }
    }

    public SageInstructionStep createMpInstructionStep(Context context, SageInstructionSurveyItem item) {
        SageInstructionStep step = new SageInstructionStep(item.identifier, item.title, item.text);
        fillMpInstructionStep(step, item);
        return step;
    }

    public void fillMpInstructionStep(SageInstructionStep step, SageInstructionSurveyItem item) {
        fillInstructionStep(step, item);
        if (item.buttonText != null) {
            step.buttonText = item.buttonText;
        }
        if (item.backgroundColorRes != null) {
            step.backgroundColorRes = item.backgroundColorRes;
        }
        if (item.backgroundDrawableRes != null) {
            step.backgroundDrawableRes = item.backgroundDrawableRes;
        }
        if (item.imageColorRes != null) {
            step.imageBackgroundColorRes = item.imageColorRes;
        }
        if (item.tintColorRes != null) {
            step.tintColorRes = item.tintColorRes;
        }
        if (item.statusBarColorRes != null) {
            step.statusBarColorRes = item.statusBarColorRes;
        }
        if (item.hideProgress) {
            step.hideProgress = true;
        }
        if (item.behindToolbar) {
            step.behindToolbar = true;
        }
        if (item.hideToolbar) {
            step.hideToolbar = true;
        }
        if (item.mediaVolume) {
            step.mediaVolume = true;
        }
        if (item.textColorRes != null) {
            step.textColorRes = item.textColorRes;
        }
        if (item.textContainerHeightRes != null) {
            step.textContainerHeightRes = item.textContainerHeightRes;
        }
        if (item.bottomLinkText != null) {
            step.bottomLinkText = item.bottomLinkText;
        }
        if (item.bottomLinkStepId != null) {
            step.bottomLinkStepId = item.bottomLinkStepId;
        }
        if (item.bottomLinkTaskId != null) {
            step.bottomLinkTaskId = item.bottomLinkTaskId;
        }
        if (item.bottomLinkColorRes != null) {
            step.bottomLinkColorRes = item.bottomLinkColorRes;
        }
        if (item.topCrop) {
            step.topCrop = true;
        }
        if (item.centerText != null && item.centerText) {
            step.centerText = true;
        }
        if (item.soundRes != null) {
            step.soundRes = item.soundRes;
        }
        if (item.submitBarColorRes != null) {
            step.submitBarColorRes = item.submitBarColorRes;
        }
        if (item.advanceOnImageClick != null && item.advanceOnImageClick) {
            step.advanceOnImageClick = true;
        }
        if (item.actionEndOnNext != null && item.actionEndOnNext) {
            step.actionEndOnNext = true;
        }
        if (item.bottomContainerColorRes != null) {
            step.bottomContainerColorRes = item.bottomContainerColorRes;
        }
    }

    public SageBooleanAnswerFormat createMpBooleanAnswerFormat(Context context, QuestionSurveyItem item) {
        if (!(item instanceof BooleanQuestionSurveyItem)) {
            throw new IllegalStateException("Error in json parsing, QUESTION_BOOLEAN types must be BooleanQuestionSurveyItem");
        }
        SageBooleanAnswerFormat format = new SageBooleanAnswerFormat();
        fillBooleanAnswerFormat(context, format, (BooleanQuestionSurveyItem)item);
        return format;
    }

    public SageTextQuestionBody.AnswerFormat createMpTextAnswerFormat(QuestionSurveyItem item) {
        if (!(item instanceof TextfieldSurveyItem)) {
            throw new IllegalStateException("Error in json parsing, " +
                    "MpText types must be TextfieldSurveyItem");
        }

        SageTextQuestionBody.AnswerFormat format = new SageTextQuestionBody.AnswerFormat();
        fillTextAnswerFormat(format, (TextfieldSurveyItem)item);
        return format;
    }

    public SageIntegerAnswerFormat createMpIntegerAnswerFormat(Context context, QuestionSurveyItem item) {
        if (!(item instanceof IntegerRangeSurveyItem)) {
            throw new IllegalStateException("Error in json parsing, QUESTION_INTEGER types must be IntegerRangeSurveyItem");
        }
        SageIntegerAnswerFormat format = new SageIntegerAnswerFormat();
        fillIntegerAnswerFormat(format, (IntegerRangeSurveyItem)item);
        return format;
    }

    public SageChoiceAnswerFormat createMpChoiceAnswerFormat(Context context, QuestionSurveyItem item) {
        if (!(item instanceof ChoiceQuestionSurveyItem)) {
            throw new IllegalStateException("Error in json parsing, this type must be ChoiceQuestionSurveyItem");
        }
        SageChoiceAnswerFormat format = new SageChoiceAnswerFormat();
        fillChoiceAnswerFormat(format, (ChoiceQuestionSurveyItem)item);
        // Override setting multiple choice answer format, since it is a custom survey type
        if (MP_MULTIPLE_CHOICE_SURVEY_ITEM_TYPE.equals(item.getCustomTypeValue())) {
            format.setAnswerStyle(AnswerFormat.ChoiceAnswerStyle.MultipleChoice);
        }
        return format;
    }

    public SageCheckboxAnswerFormat createMpCheckboxAnswerFormat(Context context, QuestionSurveyItem item) {
        if (!(item instanceof BooleanQuestionSurveyItem)) {
            throw new IllegalStateException("Error in json parsing, QUESTION_BOOLEAN types must be BooleanQuestionSurveyItem");
        }
        SageCheckboxAnswerFormat format = new SageCheckboxAnswerFormat();
        fillBooleanAnswerFormat(context, format, (BooleanQuestionSurveyItem)item);
        return format;
    }

    public SageMultiCheckboxAnswerFormat createMpMultiCheckboxAnswerFormat(Context context, QuestionSurveyItem item) {
        if (!(item instanceof ChoiceQuestionSurveyItem)) {
            throw new IllegalStateException("Error in json parsing, this type must be ChoiceQuestionSurveyItem");
        }
        SageMultiCheckboxAnswerFormat format = new SageMultiCheckboxAnswerFormat();
        fillChoiceAnswerFormat(format, (ChoiceQuestionSurveyItem)item);
        return format;
    }

    public SageRadioButtonAnswerFormat createMpRadioAnswerFormat(Context context, QuestionSurveyItem item) {
        if (!(item instanceof ChoiceQuestionSurveyItem)) {
            throw new IllegalStateException("Error in json parsing, this type must be ChoiceQuestionSurveyItem");
        }
        if (item.type == SurveyItemType.QUESTION_MULTIPLE_CHOICE) {
            throw new IllegalStateException("Radio button types can only be single choice");
        }
        SageRadioButtonAnswerFormat format = new SageRadioButtonAnswerFormat();
        fillChoiceAnswerFormat(format, (ChoiceQuestionSurveyItem)item);
        return format;
    }

    public SageSpinnerAnswerFormat createBpSpinnerAnswerFormat(Context context, QuestionSurveyItem item) {
        if (!(item instanceof ChoiceQuestionSurveyItem)) {
            throw new IllegalStateException("Error in json parsing, this type must be ChoiceQuestionSurveyItem");
        }
        SageSpinnerAnswerFormat format = new SageSpinnerAnswerFormat();
        fillChoiceAnswerFormat(format, (ChoiceQuestionSurveyItem)item);

        return format;
    }

    protected SagePhoneInstructionStep createMpPhoneInstructionStep(SageInstructionSurveyItem item) {
        SagePhoneInstructionStep step = new SagePhoneInstructionStep(
                item.identifier, item.title, item.text);
        fillMpInstructionStep(step, item);
        return step;
    }
}
