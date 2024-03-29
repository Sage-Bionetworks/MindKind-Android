/*
 * BSD 3-Clause License
 *
 * Copyright 2018  Sage Bionetworks. All rights reserved.
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

package org.sagebionetworks.research.mindkind.researchstack.framework;

import android.app.Activity;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import androidx.annotation.IdRes;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.TextView;

import org.sagebionetworks.researchstack.backbone.result.TaskResult;
import org.sagebionetworks.researchstack.backbone.step.Step;
import org.sagebionetworks.researchstack.backbone.ui.ViewTaskActivity;
import org.sagebionetworks.researchstack.backbone.ui.step.layout.ActiveStepLayout;
import org.sagebionetworks.researchstack.backbone.ui.step.layout.StepLayout;

import org.sagebionetworks.research.mindkind.researchstack.R;
import org.sagebionetworks.research.mindkind.researchstack.inject.SageResearchStackModule;

/**
 * Created by mdephillips on 12/11/17, edited to pull into the mPower RS framework on 10/14/18.
 *
 * The MpViewTaskActivity is a ViewTaskActivity that is themed with a Sage style tool bar and footer view.
 * It supports customization of the status bar, tool bar, and several view layouts by any StepLayout.
 */

public class SageViewTaskActivity extends ViewTaskActivity {

    protected ViewGroup toolbarContainer;
    protected TextView stepProgressTextView;

    @Override
    public void onDataAuth() {
        storageAccessUnregister();
        SageResearchStackModule.mockAuthenticate(this);
        super.onDataReady();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // TODO: mdephillips 10/12/18 we probably don't need this
        // Adjust font scale per specific device
        //TextUtils.adjustFontScale(getResources().getConfiguration(), this, BpMainActivity.MAX_FONT_SCALE);

        stepProgressTextView = findViewById(R.id.bp_step_progress_textview);

        toolbarContainer = findViewById(R.id.bp_toolbar_container);
    }

    @Override
    public @IdRes
    int getViewSwitcherRootId() {
        return R.id.bp_step_switcher;
    }

    @Override
    public int getContentViewId() {
        return R.layout.sage_activity_view_task;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        toolbarItemClicked(item, currentStepLayout, this);
        return true;
    }

    @Override
    public void showStep(Step step, boolean alwaysReplaceView) {
        super.showStep(step, alwaysReplaceView);

        SageViewTaskActivity.refreshVolumeControl(this, currentStepLayout);
        SageViewTaskActivity.callTaskResultListener(taskResult, currentStepLayout);
    }

    /**
     * Helper method for when the toolbar item is clicked to be re-used in other ViewTaskActivitys
     */
    public static boolean toolbarItemClicked(
            MenuItem item, StepLayout current, ViewTaskActivity activity) {

        boolean clickWasConsumed = false;
        // Allow for customization of the toolbar
        if(!clickWasConsumed && item.getItemId() == android.R.id.home) {
            activity.showConfirmExitDialog();
            return true;
        }

        return clickWasConsumed;
    }

    /**
     * Helper method to call the task result listener for a step layout
     * @param taskResult current task result for task activity
     * @param current step layout
     */
    public static void callTaskResultListener(TaskResult taskResult, StepLayout current) {
        // Let steps know about the task result if it needs to
        if (current instanceof SageResultListener) {
            ((SageResultListener)current).taskResult(taskResult);
        }
    }

    /**
     * Helper method for refreshing the volume control for a task activity
     * @param taskActivity that is displaying the step layout
     * @param current step layout
     */
    public static void refreshVolumeControl(Activity taskActivity, StepLayout current) {
        // Media Volume controls
        int streamType = AudioManager.USE_DEFAULT_STREAM_TYPE;
        if (current instanceof MediaVolumeController) {
            if (((MediaVolumeController)current).controlMediaVolume()) {
                streamType = AudioManager.STREAM_MUSIC;
            }
        } else if (current instanceof ActiveStepLayout) {
            // ActiveStepLayouts have verbal spoken instructions
            streamType = AudioManager.STREAM_MUSIC;
        }
        taskActivity.setVolumeControlStream(streamType);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(resultCode == RESULT_OK) {
            StepLayout layout = getCurrentStepLayout();
            if(layout instanceof SageActivityResultListener) {
                ((SageActivityResultListener)layout).onActivityFinished(requestCode, resultCode, data);
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    public interface MediaVolumeController {
        /**
         * @return if true, volume buttons will control media, not ringer
         */
        boolean controlMediaVolume();
    }
}
