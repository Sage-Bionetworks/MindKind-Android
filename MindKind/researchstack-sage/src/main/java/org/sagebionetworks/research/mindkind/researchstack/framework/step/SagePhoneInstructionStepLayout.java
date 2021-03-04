package org.sagebionetworks.research.mindkind.researchstack.framework.step;

import android.content.Context;
import android.content.Intent;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.View;
import android.widget.EditText;

import org.sagebionetworks.research.mindkind.researchstack.framework.SageResearchStack.SageDataProvider;
import org.sagebionetworks.researchstack.backbone.DataResponse;
import org.sagebionetworks.researchstack.backbone.result.StepResult;
import org.sagebionetworks.researchstack.backbone.step.Step;
import org.sagebionetworks.researchstack.backbone.utils.ObservableUtils;
import org.sagebionetworks.bridge.rest.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.rest.model.Phone;
import org.sagebionetworks.research.mindkind.researchstack.R;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rx.Observable;
import rx.subscriptions.CompositeSubscription;

public class SagePhoneInstructionStepLayout extends SageInstructionStepLayout {

    private static final Logger LOGGER = LoggerFactory.getLogger(SagePhoneInstructionStepLayout.class);

    public static final String US_REGION_CODE = "US";

    protected EditText phoneEntryField;

    // no destroy hook available to clean this up
    private CompositeSubscription compositeSubscription = new CompositeSubscription();

    private SagePhoneInstructionStep mpPhoneInstructionStep;

    public SagePhoneInstructionStepLayout(Context context) {
        super(context);
    }

    public SagePhoneInstructionStepLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SagePhoneInstructionStepLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public SagePhoneInstructionStepLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public void goForwardClicked(View v) {
        SageDataProvider provider = SageDataProvider.getInstance();
        String phoneNumber = phoneEntryField.getText().toString();

        findViewById(R.id.button_go_forward)
                .setEnabled(false);

        Observable<DataResponse> phoneAuthObservable;

        // check whether we should create new accounts
        if (getResources().getBoolean(R.bool.should_sign_up_phone)) {
            LOGGER.debug("Doing phone sign up and sign in");
            Phone phone = new Phone().regionCode(US_REGION_CODE).number(phoneNumber);
            phoneAuthObservable = provider.signUp(phone);
        } else {
            // only existing accounts can sign in
            LOGGER.debug("Doing phone sign in only");
            phoneAuthObservable = provider.requestPhoneSignIn(US_REGION_CODE, phoneNumber);
        }

        compositeSubscription.add(phoneAuthObservable
                .compose(ObservableUtils.applyDefault())
                .subscribe(dataResponse -> {
                    LOGGER.debug("Successfully signed up or in!");
                    super.goForwardClicked(v);
                }, throwable -> {
                    LOGGER.warn("Sign up or in error ", throwable);

                    // 400 is the response for an invalid phone number
                    if (throwable instanceof InvalidEntityException) {
                        showOkAlertDialog(
                                "The phone number you entered is not valid. Please enter a valid U.S. phone number.");
                    } else {
                        findViewById(R.id.button_go_forward)
                                .setEnabled(true);
                        showOkAlertDialog("The server returned an error: \n" + throwable.getMessage());
                    }
                }));
    }

    @Override
    public void initialize(Step step, StepResult result) {
        validateAndSetMpStartTaskStep(step);
        super.initialize(step, result);

        // for internal builds, enable external id sign in
        View v = findViewById(R.id.internal_sign_in_link);
        if (v != null) {
            v.setOnClickListener(this::onInternalSignInClick);
        }

        phoneEntryField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after) {
                // no-op
            }

            @Override
            public void onTextChanged(final CharSequence s, final int start, final int before, final int count) {
                //no-op
            }

            @Override
            public void afterTextChanged(final Editable s) {
                if (phoneEntryField.getText().length() >= 10) {
                    findViewById(R.id.button_go_forward)
                            .setEnabled(true);
                } else {
                    findViewById(R.id.button_go_forward)
                            .setEnabled(false);
                }
            }
        });
    }

    void onInternalSignInClick(View v) {
        Intent i = new Intent();
        // Fragile String-based reference, but this is used for internal builds (testing) only
        i.setClassName(getContext(), "org.sagebionetworks.research.mpower.authentication.ExternalIdSignInActivity");
        getContext().startActivity(i);
    }

    @Override
    public int getContentResourceId() {
        return R.layout.mp_step_layout_phone_entry;
    }

    @Override
    public void connectStepUi(int titleRId, int textRId, int imageRId, int detailRId) {
        super.connectStepUi(titleRId, textRId, imageRId, detailRId);
        findViewById(R.id.button_go_forward)
                .setEnabled(false);
        phoneEntryField = findViewById(R.id.mp_entry_field);
    }


    @Override
    public void refreshStep() {
        super.refreshStep();

        phoneEntryField.setHint("Enter your mobile number");
        // TODO figure out back button layout issue
//        backButton.setVisibility(VISIBLE);

        nextButton.setOnClickListener(this::goForwardClicked);
    }

    protected void validateAndSetMpStartTaskStep(Step step) {
        if (!(step instanceof SagePhoneInstructionStep)) {
            throw new IllegalStateException("MpPhoneInstructionStepLayout only works with MpPhoneInstructionStep");
        }
        this.mpPhoneInstructionStep = (SagePhoneInstructionStep) step;
    }
}