package org.sagebionetworks.research.arcvalidation.researchstack.framework.step;

import android.content.Context;
import android.graphics.Matrix;
import androidx.core.content.res.ResourcesCompat;
import android.text.method.ScrollingMovementMethod;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import org.sagebionetworks.researchstack.backbone.result.StepResult;
import org.sagebionetworks.researchstack.backbone.step.Step;
import org.sagebionetworks.researchstack.backbone.ui.callbacks.StepCallbacks;
import org.sagebionetworks.researchstack.backbone.ui.step.layout.InstructionStepLayout;
import org.sagebionetworks.researchstack.backbone.utils.ResUtils;
import org.sagebionetworks.research.arcvalidation.researchstack.R;
import org.sagebionetworks.research.arcvalidation.researchstack.framework.step.toolbar.SageTaskBehindToolbarManipulator;
import org.sagebionetworks.research.arcvalidation.researchstack.framework.step.toolbar.SageTaskStatusBarManipulator;
import org.sagebionetworks.research.arcvalidation.researchstack.framework.step.toolbar.SageTaskToolbarProgressManipulator;

public class SageInstructionStepLayout extends InstructionStepLayout implements
        SageTaskBehindToolbarManipulator, SageTaskStatusBarManipulator, SageTaskToolbarProgressManipulator {

    protected Button backButton;

    protected SageInstructionStep mpInstructionStep;

    protected Button nextButton;

    protected View nextButtonContainer;

    protected View rootInstructionLayout;

    protected LinearLayout textContainer;

    public SageInstructionStepLayout(Context context) {
        super(context);
    }

    public SageInstructionStepLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SageInstructionStepLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public SageInstructionStepLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public int mpStatusBarColor() {
        if (mpInstructionStep.statusBarColorRes != null) {
            return ResUtils.getColorResourceId(getContext(), mpInstructionStep.statusBarColorRes);
        } else if (mpInstructionStep.backgroundColorRes != null) {
            return ResUtils.getColorResourceId(getContext(), mpInstructionStep.backgroundColorRes);
        } else if (mpInstructionStep.imageBackgroundColorRes != null) {
            return ResUtils.getColorResourceId(getContext(), mpInstructionStep.imageBackgroundColorRes);
        }
        return SageTaskStatusBarManipulator.DEFAULT_COLOR;
    }

    @Override
    public boolean mpToolbarShowProgress() {
        return !mpInstructionStep.hideProgress;
    }

    @Override
    public boolean mpToolbarStepLayoutBehind() {
        return mpInstructionStep.behindToolbar;
    }

    @Override
    public int getContentContainerLayoutId() {
        return R.id.mp_step_layout_container;
    }

    @Override
    public int getFixedSubmitBarLayoutId() {
        return R.layout.mp_step_layout_container;
    }

    public void goForwardClicked(View v) {
        nextButton.setEnabled(false);
        if (mpInstructionStep.bottomLinkStepId != null) {
            // Provide an empty step result to get rid of the step skip functionality
            callbacks.onSaveStep(StepCallbacks.ACTION_NEXT, step, new StepResult<>(mpInstructionStep));
        } else if (mpInstructionStep.actionEndOnNext != null && mpInstructionStep.actionEndOnNext) {
            callbacks.onSaveStep(StepCallbacks.ACTION_END, step, null);
        } else {
            onComplete();
        }
    }

    @Override
    public void initialize(Step step, StepResult result) {
        validateAndSetMpStep(step);
        super.initialize(step, result);
    }

    @Override
    public int getContentResourceId() {
        return R.layout.mp_step_layout_instruction;
    }

    @Override
    public void connectStepUi(int titleRId, int textRId, int imageRId, int detailRId) {
        super.connectStepUi(
                R.id.mp_instruction_title,
                R.id.mp_instruction_text,
                R.id.mp_image_view,
                R.id.mp_instruction_more_detail_text);

        nextButton = findViewById(R.id.button_go_forward);
        nextButton.setEnabled(true);
        rootInstructionLayout = findViewById(R.id.mp_root_instruction_layout);
        textContainer = findViewById(R.id.mp_text_container);
        nextButtonContainer = findViewById(R.id.mp_next_button_container);
        backButton = findViewById(R.id.button_go_back);
    }

    @Override
    public void refreshStep() {
        super.refreshStep();

        if (mpInstructionStep.buttonText != null) {
            nextButton.setText(mpInstructionStep.buttonText);
        }
        nextButton.setOnClickListener(this::goForwardClicked);

        if (mpInstructionStep.backgroundColorRes != null) {
            int colorId = ResUtils.getColorResourceId(getContext(), mpInstructionStep.backgroundColorRes);
            rootInstructionLayout.setBackgroundResource(colorId);
        }
        if (mpInstructionStep.backgroundDrawableRes != null) {
            int drawableId = ResUtils.getDrawableResourceId(getContext(), mpInstructionStep.backgroundDrawableRes);
            rootInstructionLayout.setBackgroundResource(drawableId);
        }
        if (mpInstructionStep.imageBackgroundColorRes != null) {
            int colorId = ResUtils.getColorResourceId(getContext(), mpInstructionStep.imageBackgroundColorRes);
            imageView.setBackgroundResource(colorId);
        }
        if (mpInstructionStep.behindToolbar) {
            imageView.setPadding(imageView.getPaddingLeft(), 0,
                    imageView.getPaddingRight(), imageView.getPaddingBottom());
        }

        if (mpInstructionStep.textColorRes != null) {
            int colorId = ResUtils.getColorResourceId(getContext(), mpInstructionStep.textColorRes);
            int color = ResourcesCompat.getColor(getResources(), colorId, null);
            titleTextView.setTextColor(color);
            textTextView.setTextColor(color);

            if (moreDetailTextView != null) {
                moreDetailTextView.setTextColor(color);
            }
        }

        if (moreDetailTextView != null) {
            moreDetailTextView.setMovementMethod(new ScrollingMovementMethod());
        }

        // Some sub-classes may not use a text container
        if (textContainer != null && mpInstructionStep.textContainerHeightRes != null) {
            int heightRes = ResUtils.getDimenResourceId(getContext(),
                    mpInstructionStep.textContainerHeightRes);
            int height = getResources().getDimensionPixelOffset(heightRes);
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) textContainer.getLayoutParams();
            params.height = height;
            textContainer.setLayoutParams(params);
        }

        if ((mpInstructionStep.scaleType == ImageView.ScaleType.MATRIX) && mpInstructionStep.topCrop) {
            // CenterCrop doesn't always focus the right way, so for now using Matrix to
            // top crop if topCrop is set to true.
            final Matrix matrix = imageView.getImageMatrix();
            final float imageWidth = imageView.getDrawable().getIntrinsicWidth();
            final int screenWidth = getResources().getDisplayMetrics().widthPixels;
            final float scaleRatio = screenWidth / imageWidth;
            matrix.postScale(scaleRatio, scaleRatio);
            if (scaleRatio > 1) {
                imageView.setImageMatrix(matrix);
            }
        }

        if (mpInstructionStep.centerText != null && mpInstructionStep.centerText) {
            textTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        } else {
            textTextView.setGravity(Gravity.LEFT);
        }

        if (mpInstructionStep.advanceOnImageClick) {
            imageView.setOnClickListener(this::goForwardClicked);
        }

        if (mpInstructionStep.bottomContainerColorRes != null) {
            int colorId = ResUtils.getColorResourceId(getContext(), mpInstructionStep.bottomContainerColorRes);
            if (colorId != 0 && nextButtonContainer != null && textContainer != null) {
                textContainer.setBackgroundResource(colorId);
                nextButtonContainer.setBackgroundResource(colorId);
            }
        }

    }

    protected void validateAndSetMpStep(Step step) {
        if (!(step instanceof SageInstructionStep)) {
            throw new IllegalStateException("MpInstructionStepLayout only works with MpInstructionStep");
        }
        this.mpInstructionStep = (SageInstructionStep) step;
    }
}
