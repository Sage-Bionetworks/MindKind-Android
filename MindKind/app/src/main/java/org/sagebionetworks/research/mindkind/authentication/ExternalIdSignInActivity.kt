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

package org.sagebionetworks.research.mindkind.authentication

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.google.common.base.Strings
import dagger.android.AndroidInjection
import org.sagebionetworks.research.mindkind.authentication.ExternalIdSignInViewModel.Factory
import kotlinx.android.synthetic.main.activity_external_id_sign_in.externalId
import kotlinx.android.synthetic.main.activity_external_id_sign_in.progressBar
import kotlinx.android.synthetic.main.activity_external_id_sign_in.signIn
import org.sagebionetworks.research.mindkind.R
import javax.inject.Inject

class ExternalIdSignInActivity : AppCompatActivity() {

    companion object {
        private val TAG = ExternalIdSignInActivity::class.qualifiedName
    }

    @JvmField
    @Inject
    var externalIdSignInViewModelFactory: Factory? = null
    var externalIdSignInViewModel: ExternalIdSignInViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_external_id_sign_in)

        initViewModel()
        initView()
    }

    private fun initViewModel() {
        val externalIdFactory = externalIdSignInViewModelFactory?.create() ?: run {
            Log.e(TAG, "Failed to create External ID View Model Factory")
            return
        }

        val externalIdViewModel =
                ViewModelProvider(this, externalIdFactory)
                        .get(ExternalIdSignInViewModel::class.java)

        externalIdViewModel.isSignedInLiveData.observe(this, Observer { isSignedIn: Boolean ->
            if (isSignedIn) {
                proceedToTaskListActivity()
            }
        })

        externalIdViewModel.isLoadingMutableLiveData.observe(this, Observer { isLoading: Boolean? ->
            progressBar?.isIndeterminate = isLoading ?: false
        })

        externalIdViewModel.errorMessageMutableLiveData.observe(this, Observer { errorMessage: String? ->
            onErrorMessage(errorMessage)
        })

        externalIdViewModel.isExternalIdValidLiveData.observe(this, Observer { isValid: Boolean? ->
            signIn?.isEnabled = isValid ?: false
        })

        externalIdSignInViewModel = externalIdViewModel
    }

    private fun initView() {
        externalId.doOnTextChanged { externalId, _, _, _ ->
            externalIdSignInViewModel?.externalId = externalId?.toString()
        }

        signIn.setOnClickListener {
            externalIdSignInViewModel?.doSignIn()
        }
    }

    private fun onErrorMessage(errorMessage: String?) {
        if (Strings.isNullOrEmpty(errorMessage)) {
            return
        }
        AlertDialog.Builder(this)
                .setTitle(errorMessage)
                .setPositiveButton(android.R.string.ok, null)
                .show()
    }

    @VisibleForTesting
    fun proceedToTaskListActivity() {
        // this will go back to the entry activity and show the main fragement
        finish()
    }
}