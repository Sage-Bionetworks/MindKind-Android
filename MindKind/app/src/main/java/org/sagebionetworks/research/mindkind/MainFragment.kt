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
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat.OnRequestPermissionsResultCallback
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.common.base.Supplier
import com.google.common.collect.ImmutableMap
import dagger.android.support.DaggerFragment
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import org.sagebionetworks.research.mindkind.tasklist.TaskListFragment
import kotlinx.android.synthetic.main.fragment_main.navigation
import org.sagebionetworks.research.sageresearch.dao.room.AppConfigRepository
import org.sagebionetworks.research.sageresearch.dao.room.ReportRepository
import org.sagebionetworks.research.sageresearch.profile.ProfileDataLoader
import org.sagebionetworks.research.sageresearch.profile.ProfileManager
import javax.inject.Inject

/**
 * A simple [Fragment] subclass.
 *
 */
class MainFragment : DaggerFragment(), OnRequestPermissionsResultCallback {

    companion object {
        val LOG_TAG = MainFragment::class.java.canonicalName
    }

    protected val compositeDispose = CompositeDisposable()

    // tag for identifying an instance of a fragment
    private val TAG_FRAGMENT_TASK_LIST = "tracking"
    private val TAG_FRAGMENT_PROFILE = "profile"

    // Mapping of a tag to a creation method for a fragment
    private val FRAGMENT_TAG_TO_CREATOR = ImmutableMap.Builder<String, Supplier<androidx.fragment.app.Fragment>>()
            .put(TAG_FRAGMENT_TASK_LIST, Supplier { TaskListFragment() })
            // TODO: mdephillips fix crash, bring in profile after bridge is setup
            // .put(TAG_FRAGMENT_PROFILE, Supplier { MPowerProfileSettingsFragment() })
            .build()

    // mapping of navigation IDs to a fragment tag
    private val FRAGMENT_NAV_ID_TO_TAG = ImmutableMap.Builder<Int, String>()
            .put(R.id.navigation_task_list, TAG_FRAGMENT_TASK_LIST)
            // TODO: mdephillips fix crash, bring in profile after bridge is setup
            // .put(R.id.navigation_profile, TAG_FRAGMENT_PROFILE)
            .build()

    @Inject
    lateinit var taskLauncher: TaskLauncher

    @Inject
    lateinit var reportRepo: ReportRepository

    @Inject
    lateinit var appConfigRepo: AppConfigRepository

    lateinit var profileManager: ProfileManager

    private lateinit var profileDataLoader: ProfileDataLoader

    private val mOnNavigationItemSelectedListener = BottomNavigationView.OnNavigationItemSelectedListener { item ->
        showFragment(FRAGMENT_NAV_ID_TO_TAG[item.itemId])
        return@OnNavigationItemSelectedListener true
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_main, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        showFragment(TAG_FRAGMENT_TASK_LIST)
        navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener)

        profileManager = ProfileManager(reportRepo, appConfigRepo)

        // Example on how to get app config, should eventually be
        // TODO: mdephillips 2/11/21 moved to its own view model
        compositeDispose.add(appConfigRepo.appConfig
                .subscribeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    Log.d(LOG_TAG, "Successfully retrieved app config with client data ${it.clientData}")
                }, {
                    Log.e(LOG_TAG, it.localizedMessage ?: "")
                }))
    }

    /**
     * Show the fragment specified by a certain tag. The fragment currently displayed in fragment_container is
     * detached from the UI. If this is the first time we are showing a fragment, an instance is created and added to
     * the fragment_container. If we have previously displayed a fragment, we retrieve it from the fragment manager
     * and re-attached to the UI.
     */
    fun showFragment(fragmentTag: String?) {
        if (fragmentTag == null) {
            Log.w(LOG_TAG, "could not show fragment with null tag")
        }

        val fragmentTransaction = childFragmentManager.beginTransaction()

        val previousFragment = childFragmentManager
                .findFragmentById(R.id.fragment_container)
        if (previousFragment != null) {
            Log.d(LOG_TAG, "detaching fragment with tag: ${previousFragment.tag}")
            fragmentTransaction.detach(previousFragment)
        }

        var nextFragment = childFragmentManager.findFragmentByTag(fragmentTag)
        if (nextFragment == null) {
            Log.d(LOG_TAG, "no fragment found for tag: $fragmentTag, creating a new one ")
            val fragmentSupplier: Supplier<androidx.fragment.app.Fragment>? = FRAGMENT_TAG_TO_CREATOR[fragmentTag]
                    ?: FRAGMENT_TAG_TO_CREATOR[TAG_FRAGMENT_TASK_LIST]

            if (fragmentSupplier == null) {
                Log.w(LOG_TAG, "no supplier found for fragment with tag: $fragmentTag")
                return
            }
            nextFragment = fragmentSupplier.get()

            fragmentTransaction
                    .add(R.id.fragment_container, nextFragment, fragmentTag)
        } else {
            Log.d(LOG_TAG, "reattaching fragment with tag: ${nextFragment.tag}")
            fragmentTransaction.attach(nextFragment)
        }
        fragmentTransaction.commit()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // Due to a behavior issue in nested child fragments
        // We must call the onActivityResult on all the children
        for (fragment in childFragmentManager.fragments) {
            Log.i(LOG_TAG, "Calling onActivityResult for fragment " + fragment.id)
            fragment.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onResume() {
        Log.d(LOG_TAG, "MainFragment onResume")
        super.onResume()
    }

    override fun onPause() {
        Log.d(LOG_TAG, "MainFragment onPause")
        // profileManager.profileDataLoader().removeObserver(passiveDataAllowedObserver)
        // NOTE: Do not disable passive gait tracking here.  This commented code is left here to allow
        // developers to disable for local testing.
        // passiveGaitViewModel.disableTracking()
        super.onPause()
    }
}
