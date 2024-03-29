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

package org.sagebionetworks.research.mindkind;

import android.app.Activity;
import android.app.Service;

import org.sagebionetworks.bridge.android.di.BridgeStudyComponent;
import org.sagebionetworks.research.mindkind.inject.DaggerSageApplicationComponent;
import org.sagebionetworks.research.mindkind.inject.DaggerSageUserScopeComponent;
import org.sagebionetworks.research.mindkind.inject.SageUserScopeComponent;
import org.sagebionetworks.research.sageresearch.BridgeSageResearchApp;
import org.sagebionetworks.researchstack.backbone.ResearchStack;

import javax.inject.Inject;

import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.Fragment;
import androidx.work.Configuration;
import dagger.android.AndroidInjector;
import dagger.android.DispatchingAndroidInjector;
import dagger.android.HasActivityInjector;
import dagger.android.HasServiceInjector;
import dagger.android.support.DaggerApplication;
import dagger.android.support.HasSupportFragmentInjector;

public class MindKindApplication extends BridgeSageResearchApp implements HasSupportFragmentInjector,
        HasActivityInjector, HasServiceInjector, Configuration.Provider {

    public static final String DATAGROUP_ARM1 = "ARM1_choice";
    public static final String DATAGROUP_ARM2 = "ARM2_assigned";

    public static final String SOCIAL_AI = "Social";
    public static final String SLEEP_AI = "Sleep";
    public static final String BODY_MOVEMENT_AI = "Movement";
    public static final String POSITIVE_EXPERIENCES_AI = "Experiences";

    public static final String[] AI = new String[] {
        SOCIAL_AI, SLEEP_AI, BODY_MOVEMENT_AI, POSITIVE_EXPERIENCES_AI
    };

    public static final String CURRENT_AI_RESULT_ID = "Current_AI";
    public static final String REPORT_LOCAL_DATE_TIME = "LocalDateTime";
    public static final String RESULT_DATA_TYPE = "dataType";

    @Inject
    DispatchingAndroidInjector<Activity> dispatchingActivityInjector;

    @Inject
    DispatchingAndroidInjector<Fragment> dispatchingSupportFragmentInjector;

    @Inject
    DispatchingAndroidInjector<Service> dispatchingServiceInjector;

    // this causes ResearchStack provider method, which also initializes RS, to be called during onCreate
    @Inject
    ResearchStack researchStack;

    @VisibleForTesting
    @Override
    protected AndroidInjector<? extends DaggerApplication> applicationInjector() {
        return DaggerSageApplicationComponent
                .builder()
                .mindKindUserScopeComponent((SageUserScopeComponent) getOrInitBridgeManagerProvider())
                .application(this)
                .build();
    }

    @Override
    protected SageUserScopeComponent initBridgeManagerScopedComponent(BridgeStudyComponent bridgeStudyComponent) {
        SageUserScopeComponent bridgeManagerProvider = DaggerSageUserScopeComponent.builder()
                .applicationContext(this.getApplicationContext())
                .bridgeStudyComponent(bridgeStudyComponent)
                .build();
        return bridgeManagerProvider;
    }

    @Override
    public Configuration getWorkManagerConfiguration() {
        // Default custom initialization of work manager
        return new Configuration.Builder()
                .setMinimumLoggingLevel(android.util.Log.INFO)
                .build();
    }

    public static String generateRandomAi() {
        int randomAiIndex = (int)(Math.random() * 4);
        return aiForIndex(randomAiIndex);
    }

    public static String aiForIndex(int index) {
        switch (index) {
            case 0: return SOCIAL_AI;
            case 1: return SLEEP_AI;
            case 2: return BODY_MOVEMENT_AI;
            default: return POSITIVE_EXPERIENCES_AI;
        }
    }
}
