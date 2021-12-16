package org.sagebionetworks.research.mindkind

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.telephony.TelephonyManager
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.Window
import android.view.animation.AccelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.MainThread
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.button.MaterialButton
import com.google.common.base.Strings
import dagger.android.AndroidInjection
import kotlinx.android.synthetic.main.activity_external_id_sign_in.*
import kotlinx.android.synthetic.main.activity_registration.*
import kotlinx.android.synthetic.main.activity_registration.header_layout
import kotlinx.android.synthetic.main.activity_registration.password_text_input
import kotlinx.android.synthetic.main.activity_registration.primary_button
import kotlinx.android.synthetic.main.activity_registration.progressBar
import kotlinx.android.synthetic.main.activity_registration.registration_message
import kotlinx.android.synthetic.main.activity_registration.registration_title
import kotlinx.android.synthetic.main.activity_registration.secondary_button
import kotlinx.android.synthetic.main.activity_registration.web_consent_container
import kotlinx.android.synthetic.main.activity_registration.welcome_butterflies
import org.sagebionetworks.bridge.android.manager.AuthenticationManager
import org.sagebionetworks.bridge.android.viewmodel.PhoneAuthViewModel
import org.sagebionetworks.bridge.researchstack.ApiUtils
import org.sagebionetworks.bridge.rest.exceptions.EntityNotFoundException
import org.sagebionetworks.bridge.rest.exceptions.InvalidEntityException
import org.sagebionetworks.bridge.rest.model.PhoneSignIn
import org.sagebionetworks.bridge.rest.model.PhoneSignInRequest
import org.sagebionetworks.bridge.rest.model.UserSessionInfo
import org.sagebionetworks.research.mindkind.authentication.ExternalIdSignInActivity
import org.sagebionetworks.research.mindkind.authentication.ExternalIdSignInViewModel
import org.sagebionetworks.researchstack.backbone.DataResponse
import org.slf4j.LoggerFactory
import rx.subscriptions.CompositeSubscription
import java.util.*
import javax.inject.Inject

open class RegistrationActivity: AppCompatActivity() {

    companion object {
        private val TAG = RegistrationActivity::class.java.simpleName
        public val stagingSignUpUrl = "https://staging.mindkindstudy.org/"
        public val signUpUrl = "https://mindkindstudy.org/hub/eligibility"
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

        handleIntent(intent)?.let {
            // Store token in view model
            viewModel.deepLinkeToken = it
        }

        viewModel.getIsSignedUpLiveData().observe(this, { signInSuccess: Boolean ->
            if (signInSuccess) {
                if (isUserInIndia()) {
                    returnToEntryActivity() // This will send user to onboarding
                } else {
                    val phoneNumber = viewModel.phoneNumber
                    startActivity(SmsCodeActivity.create(this, phoneNumber))
                    overridePendingTransition(R.anim.enter, R.anim.exit)
                }
            }
        })

        viewModel.getIsSignedUpLiveData().observe(this, { isSignedUp: Boolean ->
            if (isSignedUp) {
                progressBar?.visibility = View.INVISIBLE
            }
        })
        viewModel.isLoadingLiveData.observe(this, { isLoading: Boolean? ->
            progressBar?.visibility = if (isLoading == true) {
                View.VISIBLE
            } else {
                View.INVISIBLE
            }
        })
        viewModel.errorMessageLiveData.observe(this, Observer { errorMessage: String? ->
            onErrorMessage(errorMessage)
        })

        viewModel.isPhoneNumberValid.observe(this, { isValid: Boolean? ->
            refreshPrimaryButtonVisibilty(isValid)
        })

        primary_button.setOnClickListener {
            if (phoneSignUpViewModel?.showingWelcomeView == true) {
                joinStudy()
            } else if (isUserInIndia()) {
                viewModel.doSignInViaExternalId(this)
            } else {
                if (isSecretTestUserPhoneNumber()) {
                    startTestUserSignInProcess()
                    return@setOnClickListener
                }
                viewModel.signInPhone(this)
            }
        }

        password_text_input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(personalCode: Editable?) {
                phoneSignUpViewModel?.personalCode = personalCode?.toString() ?: ""
            }
        })

        phone_number_text_input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(phoneNumber: Editable?) {
                phoneSignUpViewModel?.phoneNumber = phoneNumber?.toString() ?: ""
            }
        })
        val countryCode = PhoneSignUpViewModel.countryCode(this)
        phone_number_text_input.setText(countryCode)
        phoneSignUpViewModel?.phoneNumber = countryCode

        secondary_button.setOnClickListener {
            if (phoneSignUpViewModel?.showingWelcomeView == true) {
                phoneSignUpViewModel?.showingWelcomeView = false

                if (isUserInIndia()) {
                    showExternalIdRegistrationView()
                } else {
                    showRegistrationView()
                }
            } else {
                joinStudy()
            }
        }
        secondary_button.paintFlags = secondary_button.paintFlags or Paint.UNDERLINE_TEXT_FLAG

        phoneSignUpViewModel = viewModel

        // Per GHMD-240 before we switched over to use External ID sign-in for users in India,
        // Some were able to successfully sign-in using SMS verfication.
        // To allow these users to sign-in again if they deleted their app and re-installed,
        // they can long-hold the "Let's get started!" text.
        registration_title.setOnLongClickListener {
            startSmsIndianUserSignInProcess()
            return@setOnLongClickListener true
        }
    }

    fun isUserInIndia(): Boolean {
        return (PhoneSignUpViewModel.phoneRegion(this) ==
                PhoneSignUpViewModel.INDIA_REGION_CODE ||
                phoneSignUpViewModel?.isATester == true) &&
                phoneSignUpViewModel?.smsCodeIndianUser != true
    }

    fun refreshPrimaryButtonVisibilty(isValid: Boolean?) {
        if (phoneSignUpViewModel?.showingWelcomeView == true) {
            primary_button.isEnabled = true
            primary_button.alpha = 1.0f
            return
        }
        primary_button.isEnabled = isValid ?: false
        primary_button.alpha = if (isValid == true) {
            1.0f
        } else {
            0.33f
        }
    }

    fun joinStudy() {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(signUpUrl))
        startActivity(browserIntent)
    }

    open fun handleIntent(intent: Intent): String? {
        val appLinkAction = intent.action
        val appLinkData = intent.data
        if (Intent.ACTION_VIEW == appLinkAction && appLinkData != null) {
            // We came in via a link! Let's do something with it
            val token = appLinkData.lastPathSegment
            Log.d(TAG, "Deep link intercepted by app with token $token")
            return token
        }
        return null
    }

    override fun onBackPressed() {
        if (phoneSignUpViewModel?.showingWelcomeView == true) {
            super.onBackPressed()
            return
        }
        phoneSignUpViewModel?.showingWelcomeView = true
        showWelcomeView()
    }

    override fun onResume() {
        super.onResume()
        web_consent_container.visibility = View.GONE

        if (phoneSignUpViewModel?.showingWelcomeView == true) {
            showWelcomeView()
        } else if (isUserInIndia()) {
            showExternalIdRegistrationView()
        } else {
            showRegistrationView()
        }
    }

    open fun showWelcomeView() {
        phoneSignUpViewModel?.showingWelcomeView = true
        external_id_password_field_layout.visibility = View.GONE
        external_id_detail_message.visibility = View.GONE
        registration_title.text = getString(R.string.registration_welcome_title)
        registration_message.text = getString(R.string.registration_welcome_message)
        primary_button.text = getString(R.string.registration_join_study)
        secondary_button.text = getString(R.string.registration_continue_to_login)
        header_layout.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelOffset(R.dimen.registration_welcome_header_height))
        phone_text_field_layout.visibility = View.GONE
        primary_button.isEnabled = true
        primary_button.alpha = 1.0f
        welcome_butterflies.alpha = 0.0f
    }

    open fun showExternalIdRegistrationView() {
        phoneSignUpViewModel?.showingWelcomeView = false
        phone_number_text_input.setText("")
        phoneSignUpViewModel?.phoneNumber = ""
        external_id_password_field_layout.visibility = View.VISIBLE
        external_id_detail_message.visibility = View.VISIBLE
        registration_title.text = getString(R.string.registration_title)
        registration_message.text = getString(R.string.india_registration_message)
        primary_button.text = getString(R.string.registration_continue)
        secondary_button.text = getString(R.string.registration_join_study_link)
        header_layout.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelOffset(R.dimen.registration_header_height))
        phone_text_field_layout.visibility = View.VISIBLE
        welcome_butterflies.animate().alpha(1f).setDuration(333)
                .setInterpolator(AccelerateInterpolator()).start()
    }

    open fun showRegistrationView() {
        phoneSignUpViewModel?.showingWelcomeView = false
        val countryCode = PhoneSignUpViewModel.countryCode(this)
        phone_number_text_input.setText(countryCode)
        phoneSignUpViewModel?.phoneNumber = countryCode
        external_id_password_field_layout.visibility = View.GONE
        external_id_detail_message.visibility = View.GONE
        registration_title.text = getString(R.string.registration_title)
        registration_message.text = getString(R.string.registration_message)
        primary_button.text = getString(R.string.registration_continue)
        secondary_button.text = getString(R.string.registration_join_study_link)
        header_layout.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelOffset(R.dimen.registration_header_height))
        phone_text_field_layout.visibility = View.VISIBLE
        welcome_butterflies.animate().alpha(1f).setDuration(333)
                .setInterpolator(AccelerateInterpolator()).start()
    }

    private fun isSecretTestUserPhoneNumber(): Boolean {
        var phone = phoneSignUpViewModel?.phoneNumber ?: ""
        if (phone.length > 3) {
            phone = phone.substring(0, phone.length - 2)
            if (phone.endsWith("555-01") ||
                    phone.endsWith("55501")) {
                return true
            }
        }
        return false
    }

    private fun startTestUserSignInProcess() {
        val dialog = Dialog(this)

        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(true)
        dialog.setContentView(R.layout.dialog_2_button_message)
        dialog.window?.setBackgroundDrawableResource(android.R.color.white)

        val title = dialog.findViewById<TextView>(R.id.dialog_title)
        title?.text = getString(R.string.tester_message)

        val msg = dialog.findViewById<TextView>(R.id.dialog_message)
        msg?.text = ""

        val posButton = dialog.findViewById<MaterialButton>(R.id.confirm_button)
        posButton?.text = getString(R.string.rsb_BOOL_YES)
        posButton?.setOnClickListener {
            dialog.dismiss()
            phoneSignUpViewModel?.isATester = true
            showExternalIdRegistrationView()
        }

        val negButton = dialog.findViewById<MaterialButton>(R.id.cancel_button)
        negButton?.text = getString(R.string.rsb_BOOL_NO)
        negButton?.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun startSmsIndianUserSignInProcess() {
        val dialog = Dialog(this)

        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(true)
        dialog.setContentView(R.layout.dialog_2_button_message)
        dialog.window?.setBackgroundDrawableResource(android.R.color.white)

        val msg = dialog.findViewById<TextView>(R.id.dialog_message)
        msg?.text = getString(R.string.sms_india_message)

        val posButton = dialog.findViewById<MaterialButton>(R.id.confirm_button)
        posButton?.text = getString(R.string.rsb_BOOL_YES)
        posButton?.setOnClickListener {
            dialog.dismiss()
            phoneSignUpViewModel?.smsCodeIndianUser = true
            showRegistrationView()
        }

        val negButton = dialog.findViewById<MaterialButton>(R.id.cancel_button)
        negButton?.text = getString(R.string.rsb_BOOL_NO)
        negButton?.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
}

public fun AppCompatActivity.onErrorMessage(errorMessage: String?) {
    if (Strings.isNullOrEmpty(errorMessage)) {
        return
    }
    val dialog = Dialog(this)

    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
    dialog.setCancelable(true)
    dialog.setContentView(R.layout.dialog_basic_message)
    dialog.window?.setBackgroundDrawableResource(android.R.color.white)

    val title = dialog.findViewById<TextView>(R.id.dialog_title)
    title?.text = getString(R.string.consent_error_title)

    val msg = dialog.findViewById<TextView>(R.id.dialog_message)
    msg?.text = getString(R.string.registration_error_msg)

    dialog.findViewById<View>(R.id.close_button)?.setOnClickListener {
        dialog.dismiss()
    }

    dialog.show()
}

public fun AppCompatActivity.returnToEntryActivity() {
    val intent = Intent(this, EntryActivity::class.java)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TOP)
    startActivity(intent)
    finish()
}

public class PhoneSignUpViewModel @MainThread constructor(
        val authenticationManager: AuthenticationManager) : ViewModel() {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(PhoneSignUpViewModel::class.java)
        const val US_REGION_CODE = "US"
        const val US_COUNTRY_CODE = "+1"

        const val UK_REGION_CODE = "GB" // (UK excluding Isle of Man)
        const val UK_COUNTRY_CODE = "+44"

        const val INDIA_REGION_CODE = "IN"
        const val INDIA_COUNTRY_CODE = "+91"

        const val SOUTH_AFRICA_REGION_CODE = "ZA"
        const val SOUTH_AFRICA_COUNTRY_CODE = "+27"

        fun countryCode(context: Context): String {
            return when(phoneRegion(context)) {
                UK_REGION_CODE -> UK_COUNTRY_CODE
                INDIA_REGION_CODE -> INDIA_COUNTRY_CODE
                SOUTH_AFRICA_REGION_CODE -> SOUTH_AFRICA_COUNTRY_CODE
                else -> US_COUNTRY_CODE
            }
        }

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

    public var showingWelcomeView = true
    public var isATester = false
    public var smsCodeIndianUser = false

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

    // Used for external ID sign in
    var personalCode: String? = null

    var deepLinkeToken: String? = null

    fun getIsSignedUpLiveData(): LiveData<Boolean> {
        return isSignedUpLiveData
    }

    /**
     * Sign up the user and request a sign in token
     * @param context is used to get TELEPHONY_SERVICE and a user's iso country code
     */
    fun signInPhone(context: Context) {

        LOGGER.debug("signUpPhone $phoneNumber")
        if (Strings.isNullOrEmpty(phoneNumber)) {
            LOGGER.warn("Cannot sign in with null or empty phone number")
            isSignedUpLiveData.postValue(false)
            errorMessageMutableLiveData.postValue("Cannot sign in with null or empty phone number")
            return
        }

        val regionCode = phoneRegion(context)

        deepLinkeToken?.let {
            signInWithAuthToken(context, it, regionCode, phoneNumber)
            return
        }

        val phoneErrorMsg = context.getString(R.string.registration_phone_error)

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

    fun signInWithAuthToken(context: Context, authToken: String, regionCode: String, number: String) {
        val phoneErrorMsg = context.getString(R.string.registration_phone_error)
        compositeSubscription.add(
                authenticationManager.signInViaPhoneLink(regionCode, number, authToken)
                        .doOnSubscribe {
                            isLoadingMutableLiveData.postValue(true)
                        }
                        .doAfterTerminate {
                            isLoadingMutableLiveData.postValue(false)
                        }
                        .subscribe({ response: UserSessionInfo? ->
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

    fun doSignInViaExternalId(context: Context) {
        LOGGER.debug("doSignInViaExternalId")

        if (Strings.isNullOrEmpty(phoneNumber)) {
            LOGGER.warn("Cannot sign in with null or empty phone number")
            isSignedUpLiveData.postValue(false)
            errorMessageMutableLiveData.postValue("Cannot sign in with null or empty phone number")
            return
        }
        val externalIdNotNull = phoneNumber

        val personalCodeNotNull = personalCode ?: run {
            LOGGER.warn("Cannot sign in with null or empty personal code")
            isSignedUpLiveData.postValue(false)
            errorMessageMutableLiveData.postValue("Cannot sign in with null or empty personal code")
            return
        }

        val phoneErrorMsg = context.getString(R.string.registration_phone_code_error)
        val fullPersonalCode = "M!ndKind${personalCodeNotNull}"

        compositeSubscription.add(
                authenticationManager.signInWithExternalId(externalIdNotNull, fullPersonalCode)
                        .doOnSubscribe {
                            isLoadingMutableLiveData.postValue(true)
                        }
                        .doAfterTerminate {
                            isLoadingMutableLiveData.postValue(false)
                        }
                        .subscribe({ response: UserSessionInfo? ->
                            isSignedUpLiveData.postValue(true)
                        }) { error: Throwable ->
                            // 400 is the response for an invalid phone number
                            if (error is EntityNotFoundException) {
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
