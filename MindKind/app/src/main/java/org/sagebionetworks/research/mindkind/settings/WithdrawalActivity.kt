package org.sagebionetworks.research.mindkind.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.View.OnClickListener
import androidx.annotation.MainThread
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.android.AndroidInjection
import kotlinx.android.synthetic.main.activity_withdrawal.*
import org.sagebionetworks.bridge.android.manager.AuthenticationManager
import org.sagebionetworks.bridge.researchstack.ApiUtils
import org.sagebionetworks.bridge.researchstack.BridgeDataProvider
import org.sagebionetworks.research.mindkind.EntryActivity
import org.sagebionetworks.research.mindkind.R
import org.sagebionetworks.research.mindkind.backgrounddata.BackgroundDataService
import org.sagebionetworks.research.mindkind.conversation.ConfirmationDialog
import org.sagebionetworks.research.mindkind.conversation.ConversationSurveyActivity
import org.sagebionetworks.research.mindkind.onErrorMessage
import org.sagebionetworks.researchstack.backbone.DataResponse
import rx.subscriptions.CompositeSubscription
import java.util.*
import javax.inject.Inject

open class WithdrawalActivity: AppCompatActivity() {

    companion object {
        private val TAG = WithdrawalActivity::class.java.simpleName
        private val CONVO_REQUEST_CODE = 1001
    }

    var viewModel: WithdrawViewModel? = null

    @JvmField
    @Inject
    var factory: WithdrawViewModel.Factory? = null

    private var handler: Handler? = null

    @SuppressLint("UseCompatLoadingForDrawables")
    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_withdrawal)

        handler = Handler()

        val viewModelFactory = factory?.create() ?: run {
            Log.e(TAG, "Failed to create withdrawal view model factory")
            return
        }

        viewModel = ViewModelProvider(this, viewModelFactory)
                        .get(WithdrawViewModel::class.java)

        viewModel?.errorMessageLiveData?.observe(this, {
            if (it?.contains("Not signed in") == true) {
                withdrawComplete()
            } else {
                onErrorMessage(it)
            }
        })

        viewModel?.isLoadingLiveData?.observe(this, {
            loading_progress.visibility = if (it) {
                View.VISIBLE
            } else {
                View.GONE
            }
        })

        viewModel?.isWithdrawnLiveData?.observe(this, {
            if (it == true) {
                withdrawComplete()
            }
        })

        launchDialogSurveyRequest()
    }

    private fun withdrawComplete() {
        BackgroundDataService.permanentlyStopSelf(this)
        startActivity(Intent(this, EntryActivity::class.java))
        finishAffinity()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == CONVO_REQUEST_CODE) {
            showDialogWithdraw()
        }
    }

    private fun launchDialogSurveyRequest() {
        val fm = supportFragmentManager

        val dialog = ConfirmationDialog.newInstance(getString(R.string.settings_withdrawal2_title),
                getString(R.string.settings_withdrawal2_message),
                getString(R.string.settings_withdrawal2_continue),
                getString(R.string.settings_withdrawal2_quit))

        dialog.show(fm, ConfirmationDialog.TAG)

        dialog.setActionListener {
            dialog.dismiss()
            ConversationSurveyActivity.startForResult(
                    this, "Withdrawal", CONVO_REQUEST_CODE)
        }

        dialog.skipButtonListener = OnClickListener {
            showDialogWithdraw()
        }

        dialog.cancelListener = OnClickListener {
            finish()
        }
    }

    private fun showDialogWithdraw() {
        val fm = supportFragmentManager

        val dialog = ConfirmationDialog.newInstance(getString(R.string.settings_withdrawal_title),
                getString(R.string.settings_withdrawal_message),
                getString(R.string.settings_withdrawal_continue),
                getString(R.string.settings_withdrawal_quit))

        dialog.show(fm, ConfirmationDialog.TAG)

        dialog.setActionListener {
            dialog.dismiss()
            viewModel?.withdraw(this)
        }

        dialog.skipButtonListener = OnClickListener {
            finish()
        }

        dialog.cancelListener = OnClickListener {
            finish()
        }
    }
}

public class WithdrawViewModel @MainThread constructor(
        val authenticationManager: AuthenticationManager) : ViewModel() {

    companion object {
        private val TAG = WithdrawViewModel::class.java.simpleName
    }

    @Suppress("UNCHECKED_CAST")
    public class Factory @Inject constructor(val authenticationManager: AuthenticationManager) {
        public fun create(): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel?> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(WithdrawViewModel::class.java)) {
                        return WithdrawViewModel(authenticationManager) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class")
                }
            }
        }
    }

    private val compositeSubscription = CompositeSubscription()

    private val errorMessageMutableLiveData: MutableLiveData<String?> = MutableLiveData()
    private val isLoadingMutableLiveData: MutableLiveData<Boolean> = MutableLiveData()
    private val isWithdrawnMutableLiveData: MutableLiveData<Boolean> = MutableLiveData()

    val errorMessageLiveData: LiveData<String?> get() = errorMessageMutableLiveData
    val isLoadingLiveData: LiveData<Boolean> get() = isLoadingMutableLiveData
    val isWithdrawnLiveData: LiveData<Boolean> get() = isWithdrawnMutableLiveData

    /**
     * Sign up the user and request a sign in token
     * @param context is used to get TELEPHONY_SERVICE and a user's iso country code
     */
    fun withdraw(context: Context) {
        compositeSubscription.add(
                BridgeDataProvider.getInstance().withdrawAndSignout(context, null)
                .andThen(ApiUtils.SUCCESS_DATA_RESPONSE)
                .doOnSubscribe {
                    isLoadingMutableLiveData.postValue(true)
                }
                .doAfterTerminate {
                    isLoadingMutableLiveData.postValue(false)
                }
                .subscribe({ response: DataResponse? ->
                    // Give the system a second to withdrawal and sign out the user
                    isWithdrawnMutableLiveData.postValue(true)
                }) { error: Throwable ->
                    isWithdrawnMutableLiveData.postValue(false)
                    errorMessageMutableLiveData.postValue(error.message)
                })
    }
}