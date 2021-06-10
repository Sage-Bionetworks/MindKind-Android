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

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.util.DisplayMetrics
import android.util.Log
import android.widget.ArrayAdapter
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import dagger.android.AndroidInjection
import kotlinx.android.synthetic.main.activity_external_id_sign_in.*
import org.sagebionetworks.research.mindkind.R
import org.sagebionetworks.research.mindkind.TaskListActivity
import org.sagebionetworks.research.mindkind.authentication.ExternalIdSignInViewModel.Factory
import javax.inject.Inject

class ExternalIdSignInActivity : AppCompatActivity() {

    companion object {
        private val TAG = ExternalIdSignInActivity::class.qualifiedName
        public val BETA_EXTERNAL_IDS = arrayOf(
                "miranda.f.marcus@gmail.com",
                "himanishah3396@gmail.com",
                "sushmitasumant@gmail.com",
                "laramangravite@gmail.com",
                "gfinch@netactive.co.za",
                "emma.carey142@gmail.com",
                "blossom.fernandes@psych.ox.ac.uk",
                "amb278@medschl.cam.ac.uk",
                "m.wolpert@wellcome.org",
                "mikerkellen@gmail.com",
                "lucy909on@gmail.com",
                "larsson.omberg@sagebase.org",
                "minalkarani14@gmail.com",
                "ewzb98@gmail.com",
                "cmaccoinnich@gmail.com",
                "scanlan.erinjoy@gmail.com",
                "katiemtaylormail@gmail.com",
                "catsebastian@gmail.com",
                "kmartin271@gmail.com",
                "yochannah@gmail.com",
                "tamsincromwell@gmail.com",
                "refiloesibisi97@gmail.com",
                "mike@sdpdigital.com",
                "eric@sdpdigital.com",
                "solly.sieberts@sagebase.org",
                "ruppert.ian@gmail.com",
                "rianhouston@yahoo.com",
                "sonia.carlson@sagebase.org")
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
        externalId.doOnTextChanged { extlIdText, _, _, _ ->
            externalIdSignInViewModel?.externalId = extlIdText?.toString()
        }

        Log.d("TODO_REMOVE",
                BETA_EXTERNAL_IDS.sortedArray()
                .joinToString {
                    val parts = it.splitToSequence("@")
                    val externalId = parts.firstOrNull()?.replace(".", "")
                    return@joinToString "\n$externalId $externalId#MindKind1"
                })

        externalId.setAdapter(ArrayAdapter(this,
                android.R.layout.simple_dropdown_item_1line, BETA_EXTERNAL_IDS.sortedArray()))
        externalId.threshold = 1
        Handler().postDelayed({
            val displayMetrics = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(displayMetrics)
            externalId.dropDownHeight = (displayMetrics.heightPixels * 0.33f).toInt()
            externalId.showDropDown()
        }, 1000)

        signIn.setOnClickListener {
            externalIdSignInViewModel?.doSignIn()
        }
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

    @VisibleForTesting
    fun proceedToTaskListActivity() {
        val intent = Intent(this, TaskListActivity::class.java)
        startActivity(intent)
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        finish()
    }
}