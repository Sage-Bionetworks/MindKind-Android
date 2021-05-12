package org.sagebionetworks.research.mindkind

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.telephony.TelephonyManager
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import androidx.annotation.MainThread
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.common.base.Strings
import dagger.android.AndroidInjection
import kotlinx.android.synthetic.main.activity_external_id_sign_in.*
import kotlinx.android.synthetic.main.activity_registration.*
import org.sagebionetworks.bridge.android.manager.AuthenticationManager
import org.sagebionetworks.bridge.researchstack.ApiUtils
import org.sagebionetworks.bridge.rest.exceptions.InvalidEntityException
import org.sagebionetworks.bridge.rest.model.Phone
import org.sagebionetworks.bridge.rest.model.SignUp
import org.sagebionetworks.research.mindkind.R
import org.sagebionetworks.research.mpower.authentication.SmsCodeActivity
import org.sagebionetworks.researchstack.backbone.DataResponse
import org.slf4j.LoggerFactory
import rx.subscriptions.CompositeSubscription
import java.util.*
import javax.inject.Inject
import kotlin.collections.ArrayList

open class RegistrationActivity: AppCompatActivity() {

    companion object {
        private val TAG = RegistrationActivity::class.java.simpleName
    }

    var phoneSignUpViewModel: PhoneSignUpViewModel? = null

    @JvmField
    @Inject
    var factory: PhoneSignUpViewModel.Factory? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_registration)

        val phoneSignUpFactory = factory?.create() ?: run {
            Log.e(TAG, "Failed to create Phone sign up view model factory")
            return
        }

        val viewModel =
                ViewModelProvider(this, phoneSignUpFactory)
                        .get(PhoneSignUpViewModel::class.java)

        viewModel.getIsSignedUpLiveData().observe(this, Observer { isSignedUp: Boolean ->
            if (isSignedUp) {
                val phoneNumber = viewModel.phoneNumber
                startActivity(SmsCodeActivity.create(this, phoneNumber))
                overridePendingTransition(R.anim.enter, R.anim.exit)
            }
        })

        viewModel.getIsSignedUpLiveData().observe(this, Observer { isSignedUp: Boolean ->
            if (isSignedUp) {
                progressBar?.visibility = View.INVISIBLE
            }
        })
        viewModel.isLoadingLiveData.observe(this, Observer { isLoading: Boolean? ->
            progressBar?.visibility = if (isLoading == true) {
                View.VISIBLE
            } else {
                View.INVISIBLE
            }
        })
        viewModel.errorMessageLiveData.observe(this, Observer { errorMessage: String? ->
            onErrorMessage(errorMessage)
        })

        submit_button.isEnabled = false
        submit_button.alpha = 0.33f
        viewModel.isPhoneNumberValid.observe(this, Observer { isValid: Boolean? ->
            submit_button.isEnabled = isValid ?: false
            submit_button.alpha = if (isValid == true) {
                1.0f
            } else {
                0.33f
            }
        })

        submit_button.setOnClickListener {
            viewModel.signInPhone(this)
        }

        phone_number_text_input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(phoneNumber: Editable?) {
                phoneSignUpViewModel?.phoneNumber = phoneNumber?.toString() ?: ""
            }
        })

        confirmation_continue.setOnClickListener {

        }

        phoneSignUpViewModel = viewModel
    }
}

public class PhoneSignUpViewModel @MainThread constructor(
        val authenticationManager: AuthenticationManager) : ViewModel() {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(PhoneSignUpViewModel::class.java)
        const val US_REGION_CODE = "US"
        const val NETHERLANDS_REGION_CODE = "NL"

        fun phoneRegion(context: Context): String {
            // Here we attempt to get the user's phone region, if it is correct and shows up, we should use it
            (context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager)?.networkCountryIso?.let {
                return it.toUpperCase(Locale.ROOT)
            }
            return US_REGION_CODE // Default to the US, should only apply to wifi only devices
        }
    }

    public class Factory @Inject constructor(val authenticationManager: AuthenticationManager) {
        public fun create(): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel?> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(PhoneSignUpViewModel::class.java)) {
                        return PhoneSignUpViewModel(authenticationManager) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class")
                }
            }
        }
    }

    private val compositeSubscription = CompositeSubscription()
    private val errorMessageMutableLiveData: MutableLiveData<String?> = MutableLiveData()
    private val isPhoneNumberValidLiveData: MutableLiveData<Boolean> = MutableLiveData()
    private val isLoadingMutableLiveData: MutableLiveData<Boolean> = MutableLiveData()
    private val isSignedUpLiveData: MutableLiveData<Boolean> = MutableLiveData()

    val errorMessageLiveData: LiveData<String?>
        get() = errorMessageMutableLiveData
    val isPhoneNumberValid: LiveData<Boolean>
        get() = isPhoneNumberValidLiveData
    val isLoadingLiveData: LiveData<Boolean>
        get() = isLoadingMutableLiveData

    var phoneNumber = ""
        set(value) {
            field = value
            isPhoneNumberValidLiveData.postValue(value.isNotEmpty())
        }

    fun getIsSignedUpLiveData(): LiveData<Boolean> {
        return isSignedUpLiveData
    }

    /**
     * Sign up the user and request a sign in token
     * @param context is used to get TELEPHONY_SERVICE and a user's iso country code
     */
    fun signInPhone(context: Context) {

        val phoneErrorMsg = context.getString(R.string.registration_phone_error)

        LOGGER.debug("signUpPhone $phoneNumber")
        if (Strings.isNullOrEmpty(phoneNumber)) {
            LOGGER.warn("Cannot sign in with null or empty phone number")
            isSignedUpLiveData.postValue(false)
            errorMessageMutableLiveData.postValue("Cannot sign in with null or empty phone number")
            return
        }

        val regionCode = phoneRegion(context)

        compositeSubscription.add(
                authenticationManager.requestPhoneSignIn(regionCode, phoneNumber)
                        .andThen(ApiUtils.SUCCESS_DATA_RESPONSE)
                        .doOnSubscribe {
                            isLoadingMutableLiveData.postValue(true)
                        }
                        .doAfterTerminate {
                            isLoadingMutableLiveData.postValue(false)
                        }
                        .subscribe({ response: DataResponse? ->
                            isSignedUpLiveData.postValue(true)
                        }) { error: Throwable ->
                            // 400 is the response for an invalid phone number
                            if (error is InvalidEntityException) {
                                errorMessageMutableLiveData.postValue(phoneErrorMsg)
                                return@subscribe
                            }
                            isSignedUpLiveData.postValue(false)
                            errorMessageMutableLiveData.postValue(error.message)
                        })
    }

    override fun onCleared() {
        compositeSubscription.unsubscribe()
    }

    fun onErrorMessageConsumed() {
        errorMessageMutableLiveData.postValue(null)
    }
}
