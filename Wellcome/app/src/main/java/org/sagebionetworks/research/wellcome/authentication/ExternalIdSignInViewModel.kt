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

package org.sagebionetworks.research.wellcome.authentication

import android.util.Log
import androidx.annotation.MainThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.common.base.Strings
import org.sagebionetworks.bridge.android.manager.AuthenticationManager
import org.sagebionetworks.bridge.rest.model.UserSessionInfo

import rx.subscriptions.CompositeSubscription
import javax.inject.Inject

class ExternalIdSignInViewModel @MainThread constructor(
        private val authenticationManager: AuthenticationManager) : ViewModel() {

    companion object {
        private val LOG_TAG = ExternalIdSignInViewModel::class.java.canonicalName
    }

    class Factory @Inject constructor(private val authenticationManager: AuthenticationManager) {
        fun create(): androidx.lifecycle.ViewModelProvider.Factory {
            return object : androidx.lifecycle.ViewModelProvider.Factory {
                override fun <T : ViewModel?> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(ExternalIdSignInViewModel::class.java)) {
                        return ExternalIdSignInViewModel(authenticationManager) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class")
                }
            }
        }
    }

    private val compositeSubscription = CompositeSubscription()
    val errorMessageMutableLiveData: MutableLiveData<String?>

    var externalId: String? = ""
    set(value) {
        field = value
        isExternalIdValidLiveData.postValue(!Strings.isNullOrEmpty(value))
    }
    var customPassword: String? = null

    val isExternalIdValidLiveData: MutableLiveData<Boolean>
    val isLoadingMutableLiveData: MutableLiveData<Boolean>
    val isSignedInLiveData: MutableLiveData<Boolean>

    init {
        errorMessageMutableLiveData = MutableLiveData()
        isLoadingMutableLiveData = MutableLiveData()
        isLoadingMutableLiveData.value = false
        isSignedInLiveData = MutableLiveData()
        isSignedInLiveData.value = false
        isExternalIdValidLiveData = MutableLiveData()
        isExternalIdValidLiveData.value = false
    }

    fun doSignIn() {
        Log.d(LOG_TAG, "doSignIn")

        val externalIdNotNull = externalId ?: run {
            Log.w(LOG_TAG, "Cannot sign in with null or empty external Id")
            isSignedInLiveData.postValue(false)
            errorMessageMutableLiveData.postValue("Cannot sign in with null or empty external Id")
            return
        }

        // Enter a custom password, or use the auto-format
        val password = customPassword ?: (externalIdNotNull + "foo#\$H0")

        compositeSubscription.add(
                authenticationManager.signInWithExternalId(externalIdNotNull, password)
                        .doOnSubscribe { isLoadingMutableLiveData.postValue(true) }
                        .doAfterTerminate { isLoadingMutableLiveData.postValue(false) }
                        .subscribe(
                                { s: UserSessionInfo? -> isSignedInLiveData.postValue(true) }
                        ) { t: Throwable ->
                            isSignedInLiveData.postValue(false)
                            errorMessageMutableLiveData.postValue(t.message)
                        })
    }

    override fun onCleared() {
        compositeSubscription.unsubscribe()
    }

    fun onErrorMessageConsumed() {
        errorMessageMutableLiveData.postValue(null)
    }
}