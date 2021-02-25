/*
 * BSD 3-Clause License
 *
 * Copyright 2021  Sage Bionetworks. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1.  Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2.  Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 *
 * 3.  Neither the name of the copyright holder(s) nor the names of any contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission. No license is granted to the trademarks of
 * the copyright holders even if such marks are included in this software.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.sagebionetworks.research.mindkind.research;

import androidx.annotation.StringDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.SOURCE)
@StringDef({SageTaskIdentifier.TRIGGERS, SageTaskIdentifier.SYMPTOMS, SageTaskIdentifier.MEDICATION,
        SageTaskIdentifier.STUDY_BURST_COMPLETED, SageTaskIdentifier.STUDY_BURST_COMPLETED_UPLOAD,
        SageTaskIdentifier.TAPPING, SageTaskIdentifier.WALK_AND_BALANCE, SageTaskIdentifier.TREMOR, SageTaskIdentifier.DEMOGRAPHICS,
        SageTaskIdentifier.BACKGROUND, SageTaskIdentifier.ENGAGEMENT, SageTaskIdentifier.MOTIVATION, SageTaskIdentifier.STUDY_BURST_REMINDER,
        SageTaskIdentifier.MEASURING, SageTaskIdentifier.TRACKING, SageTaskIdentifier.HEALTH_SURVEYS, SageTaskIdentifier.AUTHENTICATE,
        SageTaskIdentifier.PASSIVE_DATA_PERMISSION, SageTaskIdentifier.PASSIVE_GAIT, SageTaskIdentifier.SLEEP_SURVEY, SageTaskIdentifier.foo_test_survey})
public @interface SageTaskIdentifier {
    String TRIGGERS = "Triggers";
    String SYMPTOMS = "Symptoms";
    String MEDICATION = "Medication";
    String STUDY_BURST_COMPLETED = "study-burst-task";
    String STUDY_BURST_COMPLETED_UPLOAD = "StudyBurst"; // upload identifier differs from schedule task identifier
    String TAPPING = "Tapping";
    String WALK_AND_BALANCE = "WalkAndBalance";
    String TREMOR = "Tremor";
    String DEMOGRAPHICS = "Demographics";
    String BACKGROUND = "Background";
    String ENGAGEMENT = "Engagement";
    String MOTIVATION = "Motivation";
    String STUDY_BURST_REMINDER = "StudyBurstReminder";
    String MEASURING = "Measuring";
    String TRACKING = "Tracking";
    String HEALTH_SURVEYS = "Health Surveys";
    String AUTHENTICATE = "Signup";
    String PASSIVE_DATA_PERMISSION = "PassiveDataPermission";
    String PASSIVE_GAIT = "PassiveGait";
    String SLEEP_SURVEY = "SleepSurvey";
    String foo_test_survey = "foo";
}
