/*
 * BSD 3-Clause License
 *
 * Copyright 2020  Sage Bionetworks. All rights reserved.
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

package org.sagebionetworks.research.mindkind.sageresearch

import android.app.Activity
import android.content.Context
import android.content.Intent
import org.sagebionetworks.research.domain.result.interfaces.TaskResult
import org.sagebionetworks.research.mobile_ui.perform_task.PerformTaskActivity
import org.sagebionetworks.research.mobile_ui.perform_task.PerformTaskFragment.OnPerformTaskExitListener.Status
import org.sagebionetworks.research.presentation.model.TaskView
import java.util.UUID

class PerformTaskWithResultActivity: PerformTaskActivity() {
    override fun onTaskExit(status: Status, taskResult: TaskResult) {
        val resultCode = if (status == Status.CANCELLED) Activity.RESULT_CANCELED else Activity.RESULT_OK
        this.setResult(resultCode)
        super.onTaskExit(status, taskResult)
    }

    companion object {
        public fun createIntent(context: Context, taskView: TaskView, taskRunUUID: UUID?): Intent {
            val originalIntent = PerformTaskActivity.createIntent(context, taskView, taskRunUUID)

            val launchIntent = Intent(context, PerformTaskWithResultActivity::class.java)
            launchIntent.putExtras(originalIntent)
            return launchIntent
        }
    }
}
