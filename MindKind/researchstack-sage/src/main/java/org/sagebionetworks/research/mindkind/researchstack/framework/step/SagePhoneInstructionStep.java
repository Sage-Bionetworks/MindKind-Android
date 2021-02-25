package org.sagebionetworks.research.mindkind.researchstack.framework.step;

public class SagePhoneInstructionStep extends SageInstructionStep {
    /**
     * Text to go in text view as hint text
     */
    public String phoneHint;

    /* Default constructor needed for serialization/deserialization of object */
    public SagePhoneInstructionStep() {
        super();
    }

    public SagePhoneInstructionStep(String identifier, String title, String detailText) {
        super(identifier, title, detailText);
    }

    @Override
    public Class getStepLayoutClass() {
        return SagePhoneInstructionStepLayout.class;
    }
}
