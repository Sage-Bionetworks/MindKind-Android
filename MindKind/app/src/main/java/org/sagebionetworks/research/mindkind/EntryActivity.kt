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

package org.sagebionetworks.research.mindkind

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import dagger.android.AndroidInjection
import org.sagebionetworks.bridge.android.manager.AuthenticationManager
import org.sagebionetworks.research.mindkind.authentication.ExternalIdSignInActivity
import org.sagebionetworks.research.mindkind.backgrounddata.BackgroundDataService
import javax.inject.Inject

class EntryActivity : AppCompatActivity() {

    companion object {
        private val TAG = TaskListActivity::class.simpleName
        public const val prefsOnboardingKey = "OnboardingCompleteKey"
    }

    @Inject
    lateinit var authManager: AuthenticationManager

    private lateinit var sharedPrefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.entry_activity)

        sharedPrefs = BackgroundDataService.createSharedPrefs(this)
    }

    override fun onResume() {
        super.onResume()

        if (authManager.userSessionInfo?.isAuthenticated != true) {
            startActivity(Intent(this, RegistrationActivity::class.java))
        } else if (sharedPrefs.getBoolean(prefsOnboardingKey, false) ||
                sharedPrefs.getBoolean(TaskListActivity.prefsIntroAlertKey, false)) {
            startActivity(Intent(this, TaskListActivity::class.java))
        } else {
            startActivity(Intent(this, WelcomeActivity::class.java))
        }
        finish()
    }
}
