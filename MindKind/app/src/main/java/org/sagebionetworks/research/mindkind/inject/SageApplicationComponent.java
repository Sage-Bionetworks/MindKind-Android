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

package org.sagebionetworks.research.mindkind.inject;

import android.app.Application;

import org.sagebionetworks.bridge.android.di.BridgeApplicationScope;
import org.sagebionetworks.research.mindkind.MindKindApplication;

import org.sagebionetworks.research.mindkind.researchstack.inject.SageResearchStackModule;
import org.sagebionetworks.research.domain.inject.TaskModule;
import org.sagebionetworks.research.mobile_ui.inject.PerformTaskModule;
import org.sagebionetworks.research.sageresearch.dao.room.AppConfigRepository;
import org.sagebionetworks.research.sageresearch.dao.room.ReportRepository;
import org.sagebionetworks.research.sageresearch.dao.room.SurveyRepository;
import org.sagebionetworks.research.sageresearch.repos.BridgeRepositoryManager;
import org.sagebionetworks.research.sageresearch_app_sdk.inject.SageResearchAppSDKModule;

import dagger.BindsInstance;
import dagger.Component;
import dagger.android.AndroidInjector;
import dagger.android.support.AndroidSupportInjectionModule;

@BridgeApplicationScope
@Component(modules = {PerformTaskModule.class, SageResearchAppSDKModule.class, TaskModule.class,
        SageResearchStackModule.class, AppDataModule.class, AndroidSupportInjectionModule.class,
        SageAppModule.class, SageUserModule.class, SageHistoryModule.class},
        dependencies = {SageUserScopeComponent.class})
public interface SageApplicationComponent extends AndroidInjector<MindKindApplication> {


    void inject(ReportRepository instance);

    void inject(AppConfigRepository instance);

    void inject(SurveyRepository instance);

    void inject(BridgeRepositoryManager instance);

    @Component.Builder
    interface Builder {
        @BindsInstance
        Builder application(Application application);

        Builder mindKindUserScopeComponent(SageUserScopeComponent sageUserScopeComponent);

        SageApplicationComponent build();
    }
}
