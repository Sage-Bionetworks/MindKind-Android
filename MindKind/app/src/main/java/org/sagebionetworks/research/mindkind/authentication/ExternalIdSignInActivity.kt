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

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import dagger.android.AndroidInjection
import kotlinx.android.synthetic.main.activity_external_id_sign_in.*
import kotlinx.android.synthetic.main.activity_external_id_sign_in.primary_button
import kotlinx.android.synthetic.main.activity_external_id_sign_in.progressBar
import kotlinx.android.synthetic.main.activity_external_id_sign_in.secondary_button
import kotlinx.android.synthetic.main.activity_external_id_sign_in.password_text_input
import kotlinx.android.synthetic.main.activity_registration.*
import org.sagebionetworks.bridge.rest.model.UserSessionInfo
import org.sagebionetworks.research.mindkind.R
import org.sagebionetworks.research.mindkind.authentication.ExternalIdSignInViewModel.Factory
import org.sagebionetworks.research.mindkind.backgrounddata.BackgroundDataService
import org.sagebionetworks.research.mindkind.returnToEntryActivity
import java.io.ByteArrayOutputStream
import javax.inject.Inject

class ExternalIdSignInActivity : AppCompatActivity() {

    companion object {
        private val TAG = ExternalIdSignInActivity::class.qualifiedName
    }

    @JvmField
    @Inject
    var externalIdSignInViewModelFactory: Factory? = null
    var externalIdSignInViewModel: ExternalIdSignInViewModel? = null

    lateinit var sharedPrefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_external_id_sign_in)

        sharedPrefs = BackgroundDataService.createSharedPrefs(this)

        initViewModel()
        initView()
    }

    // Need to sync user sign up date immediately
    @SuppressLint("ApplySharedPref")
    private fun initViewModel() {
        val externalIdFactory = externalIdSignInViewModelFactory?.create() ?: run {
            Log.e(TAG, "Failed to create External ID View Model Factory")
            return
        }

        val externalIdViewModel =
                ViewModelProvider(this, externalIdFactory)
                        .get(ExternalIdSignInViewModel::class.java)

        externalIdViewModel.isSignedInLiveData.observe(this, Observer { user: UserSessionInfo? ->
            user?.let {
                // Proceed to entry to re-evaluate bridge access
                returnToEntryActivity()
            }
        })

        externalIdViewModel.isLoadingMutableLiveData.observe(this, Observer { isLoading: Boolean? ->
            val isLoadingUnwrapped = isLoading ?: false
            progressBar?.visibility = if (isLoading == true) {
                View.VISIBLE
            } else {
                View.INVISIBLE
            }
            refreshPrimaryButtonVisibilty(!isLoadingUnwrapped)
        })

        externalIdViewModel.errorMessageMutableLiveData.observe(this, Observer { errorMessage: String? ->
            onErrorMessage(errorMessage)
        })

        externalIdViewModel.isExternalIdValidLiveData.observe(this, Observer { isValid: Boolean? ->
            refreshPrimaryButtonVisibilty(isValid)
        })

        externalIdSignInViewModel = externalIdViewModel
    }

    private fun initView() {

        external_id_text_input.doOnTextChanged { externalId, _, _, _ ->
            externalIdSignInViewModel?.externalId = externalId?.toString()
        }

        password_text_input.doOnTextChanged { password, _, _, _ ->
            externalIdSignInViewModel?.customPassword = password?.toString()
        }

        primary_button.setOnClickListener {
            refreshPrimaryButtonVisibilty(false)
            externalIdSignInViewModel?.doSignIn()
        }

        secondary_button.setOnClickListener {
            joinStudy()
        }
    }

    fun refreshPrimaryButtonVisibilty(isEnabled: Boolean?) {
        primary_button.isEnabled = isEnabled ?: false
        primary_button.alpha = if (isEnabled == true) {
            1.0f
        } else {
            0.33f
        }
    }

    fun joinStudy() {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(
                "https://mindkindstudy.org/hub/eligibility"))
        startActivity(browserIntent)
    }

    private fun onErrorMessage(errorMessage: String?) {
        var error = errorMessage ?: run { return }
        // This is the no internet error message string from web sdk
        // It's a bit technical, so replace it with more informative copy
        if (error.contains("Unable to resolve host")) {
            error = getString(R.string.rsb_error_no_internet)
        }
        AlertDialog.Builder(this)
                .setMessage(error)
                .setNeutralButton(R.string.rsb_ok, null)
                .show()
    }
}