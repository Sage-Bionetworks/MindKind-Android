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

import com.google.common.collect.ImmutableList;

import org.sagebionetworks.bridge.android.di.BridgeApplicationScope;
import org.sagebionetworks.research.mindkind.sageresearch.archive.TappingResultArchiveFactory;
import org.sagebionetworks.research.mobile_ui.inject.PerformTaskFragmentScope;
import org.sagebionetworks.research.mobile_ui.inject.ShowStepFragmentModule;
import org.sagebionetworks.research.mobile_ui.perform_task.PerformTaskFragment;
import org.sagebionetworks.research.sageresearch_app_sdk.archive.AbstractResultArchiveFactory.ResultArchiveFactory;
import org.sagebionetworks.research.sageresearch_app_sdk.archive.AnswerResultArchiveFactory;
import org.sagebionetworks.research.sageresearch_app_sdk.archive.BaseResultArchiveFactory;
import org.sagebionetworks.research.sageresearch_app_sdk.archive.FileResultArchiveFactory;
import org.sagebionetworks.research.sageresearch_app_sdk.archive.TaskResultAnswerMapResultArchiveFactory;
import org.sagebionetworks.research.sageresearch_app_sdk.archive.TaskResultArchiveFactory;
import org.sagebionetworks.research.mindkind.authentication.ExternalIdSignInActivity;
import org.sagebionetworks.research.mindkind.TaskListActivity;

import dagger.Module;
import dagger.Provides;
import dagger.android.ContributesAndroidInjector;
import org.sagebionetworks.research.mindkind.EntryActivity;

@Module(includes = {})
public abstract class SageUserModule {
    @ContributesAndroidInjector
    abstract ExternalIdSignInActivity contributeExternalIdSignInActivityInjector();

    // these modules contain an aggregate of ShowStepFragment subcomponents, so they are scoped under the PerformTaskFragment
    @ContributesAndroidInjector(modules = {ShowStepFragmentModule.class})
    @PerformTaskFragmentScope
    abstract PerformTaskFragment contributePerformTaskFragmentInjector();

    @ContributesAndroidInjector
    abstract EntryActivity contributeEntryActivityInjector();

    @ContributesAndroidInjector
    abstract TaskListActivity contributeTaskListActivityInjector();

    @Provides
    @BridgeApplicationScope
    static TaskResultArchiveFactory provideTaskResultArchiveFactory() {
        return new TaskResultAnswerMapResultArchiveFactory();
    }

    @Provides
    @BridgeApplicationScope
    static ImmutableList<ResultArchiveFactory> provideAbstractResultArchiveFactory(
            TappingResultArchiveFactory tappingResultArchiveFactory,
            FileResultArchiveFactory fileResultArchiveFactory,
            AnswerResultArchiveFactory answerResultArchiveFactory,
            BaseResultArchiveFactory baseResultArchiveFactory) {

        return ImmutableList.of(tappingResultArchiveFactory,
                fileResultArchiveFactory, answerResultArchiveFactory, baseResultArchiveFactory);
    }
}
