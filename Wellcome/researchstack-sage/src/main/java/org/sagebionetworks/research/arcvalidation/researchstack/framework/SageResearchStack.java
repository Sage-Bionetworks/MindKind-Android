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

package org.sagebionetworks.research.arcvalidation.researchstack.framework;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;

import com.google.gson.Gson;

import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.sagebionetworks.bridge.android.manager.BridgeManagerProvider;
import org.sagebionetworks.bridge.researchstack.BridgeDataProvider;
import org.sagebionetworks.bridge.rest.model.UserSessionInfo;
import org.sagebionetworks.researchstack.backbone.ActionItem;
import org.sagebionetworks.researchstack.backbone.AppPrefs;
import org.sagebionetworks.researchstack.backbone.DataProvider;
import org.sagebionetworks.researchstack.backbone.PermissionRequestManager;
import org.sagebionetworks.researchstack.backbone.ResearchStack;
import org.sagebionetworks.researchstack.backbone.ResourceManager;
import org.sagebionetworks.researchstack.backbone.TaskProvider;
import org.sagebionetworks.researchstack.backbone.UiManager;
import org.sagebionetworks.researchstack.backbone.model.SchedulesAndTasksModel;
import org.sagebionetworks.researchstack.backbone.model.SectionModel;
import org.sagebionetworks.researchstack.backbone.model.TaskModel;
import org.sagebionetworks.researchstack.backbone.notification.NotificationConfig;
import org.sagebionetworks.researchstack.backbone.notification.SimpleNotificationConfig;
import org.sagebionetworks.researchstack.backbone.onboarding.OnboardingManager;
import org.sagebionetworks.researchstack.backbone.result.StepResult;
import org.sagebionetworks.researchstack.backbone.result.TaskResult;
import org.sagebionetworks.researchstack.backbone.step.Step;
import org.sagebionetworks.researchstack.backbone.storage.database.AppDatabase;
import org.sagebionetworks.researchstack.backbone.storage.file.EncryptionProvider;
import org.sagebionetworks.researchstack.backbone.storage.file.FileAccess;
import org.sagebionetworks.researchstack.backbone.storage.file.PinCodeConfig;
import org.sagebionetworks.researchstack.backbone.storage.file.SimpleFileAccess;
import org.sagebionetworks.researchstack.backbone.storage.file.aes.AesProvider;
import org.sagebionetworks.researchstack.backbone.task.Task;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * This class is the Bridge SDK Research Stack setup
 * Most of this functionality is deprecated or not-needed,
 * But it is necessary to include to communicate with Sage Bridge Web API
 */
public class SageResearchStack extends ResearchStack {

    SageDataProvider mDataProvider;
    SageEmptyAppDatabase mEmptyDb;
    AesProvider mEncryptionProvider;
    SimpleFileAccess mFileAccess;
    SimpleNotificationConfig mNotificationConfig;
    SagePermissionRequestManager mPermissionManager;
    PinCodeConfig mPinCodeConfig;
    SageResourceManager mResourceManager;
    SageTaskProvider mTaskProvider;
    SageUiManager mUiManager;

    public SageResearchStack(Context context) {
        ArcPrefs.init(context);
        mFileAccess = new SimpleFileAccess();
        mEncryptionProvider = new AesProvider();
        mResourceManager = new SageResourceManager();
        mNotificationConfig = new SimpleNotificationConfig();
        mPermissionManager = new SagePermissionRequestManager();
    }

    @Override
    protected AppDatabase createAppDatabaseImplementation(Context context) {
        if (mEmptyDb == null) {
            mEmptyDb = new SageEmptyAppDatabase();
        }
        return mEmptyDb;
    }

    @Override
    protected PinCodeConfig getPinCodeConfig(Context context) {
        if (mPinCodeConfig == null) {
            long autoLockTime = AppPrefs.getInstance(context).getAutoLockTime();
            mPinCodeConfig = new PinCodeConfig(autoLockTime);
        }
        return mPinCodeConfig;
    }

    @Override
    protected EncryptionProvider getEncryptionProvider(Context context) {
        return mEncryptionProvider;
    }

    @Override
    public OnboardingManager getOnboardingManager() {
        // We don't need an on-boarding manager using ResearchStack
        return null;
    }

    @Override
    public void createOnboardingManager(Context context) {
        // We don't need an on-boarding manager using ResearchStack
    }

    @Override
    protected FileAccess createFileAccessImplementation(Context context) {
        return mFileAccess;
    }

    @Override
    protected ResourceManager createResourceManagerImplementation(Context context) {
        return mResourceManager;
    }

    @Override
    protected UiManager createUiManagerImplementation(Context context) {
        if (mUiManager == null) {
            mUiManager = new SageUiManager();
        }
        return mUiManager;
    }

    @Override
    protected DataProvider createDataProviderImplementation(Context context) {
        if (mDataProvider == null) {
            mDataProvider = new SageDataProvider();
        }
        return mDataProvider;
    }

    @Override
    protected TaskProvider createTaskProviderImplementation(Context context) {
        if (mTaskProvider == null) {
            mTaskProvider = new SageTaskProvider(context);
        }
        return mTaskProvider;
    }

    @Override
    protected NotificationConfig createNotificationConfigImplementation(Context context) {
        return mNotificationConfig;
    }

    @Override
    protected PermissionRequestManager createPermissionRequestManagerImplementation(Context context) {
        return mPermissionManager;
    }

    public static class SageDataProvider extends BridgeDataProvider {

        protected SageTaskFactory taskFactory;

        public static SageDataProvider getInstance() {
            DataProvider provider = DataProvider.getInstance();
            if (!(provider instanceof SageDataProvider)) {
                throw new IllegalStateException("This app only works with SageDataProvider");
            }
            return (SageDataProvider) DataProvider.getInstance();
        }

        public SageDataProvider() {
            super(BridgeManagerProvider.getInstance());
            taskFactory = new SageTaskFactory();
            taskHelper.setSurveyFactory(taskFactory);
        }

        @Override
        public void processInitialTaskResult(Context context, TaskResult taskResult) {
            // no op
        }

        /**
         * @return the current status of the user's data groups, empty list of user is not signed in
         */
        public @NonNull
        List<String> getUserDataGroups() {
            UserSessionInfo sessionInfo = bridgeManagerProvider.getAuthenticationManager().getUserSessionInfo();
            if (sessionInfo == null || sessionInfo.getDataGroups() == null) {
                return new ArrayList<>();
            }
            return sessionInfo.getDataGroups();
        }
    }

    public static class ArcPrefs {

        public static final DateTimeFormatter FORMATTER = ISODateTimeFormat.dateTime().withOffsetParsed();

        private static ArcPrefs instance;

        private Gson gson;
        private final SharedPreferences prefs;

        public static ArcPrefs getInstance() {
            if (instance == null) {
                throw new RuntimeException("ArcPrefs instance is null. Make sure it is initialized in ResearchStack before calling.");
            }
            return instance;
        }

        public static void init(Context context) {
            instance = new ArcPrefs(context);
        }

        @VisibleForTesting
        ArcPrefs(Context context) {
            gson = new Gson();
            prefs = createPrefs(context);
        }

        public SharedPreferences getPrefs() {
            return prefs;
        }

        @VisibleForTesting
        SharedPreferences createPrefs(Context context) {
            return PreferenceManager.getDefaultSharedPreferences(context);
        }
    }

    /**
     * The EmptyAppDatabase is a no-op implementation of the AppDatabase, since Wellcome does not use a sql database
     */
    public static class SageEmptyAppDatabase implements AppDatabase {
        public SageEmptyAppDatabase() { super(); }
        @Override public void saveTaskResult(TaskResult result) { /* no-op */ }
        @Override public TaskResult loadLatestTaskResult(String taskIdentifier) { return null; }
        @Override public List<TaskResult> loadTaskResults(String taskIdentifier) { return null; }
        @Override public List<StepResult> loadStepResults(String stepIdentifier) { return null; }
        @Override public void setEncryptionKey(String key) { /* no-op */ }
    }

    public static class SagePermissionRequestManager extends PermissionRequestManager {

        public static final String PERMISSION_NOTIFICATIONS = "Arc.permission.NOTIFICATIONS";

        private static final int RESULT_REQUEST_CODE_NOTIFICATION = 143;

        public SagePermissionRequestManager() {}

        /**
         * Used to tell if the permission-id should be handled by the system (using {@link
         * Activity#requestPermissions(String[], int)}) or through our own custom implementation in {@link
         * #onRequestNonSystemPermission}
         */
        @Override
        public boolean isNonSystemPermission(String permissionId) {
            // SampleApplication.PERMISSION_NOTIFICATIONS is our non-system permission so we return true
            // if permissionId's are the same
            return permissionId.equals(PERMISSION_NOTIFICATIONS);
        }

        @Override
        public boolean hasPermission(Context context, String permissionId) {
            switch (permissionId) {
                case PERMISSION_NOTIFICATIONS:
                    return AppPrefs.getInstance(context).isTaskReminderEnabled();
                default: // This is a system permission, simply ask the system
                    return ContextCompat.checkSelfPermission(context, permissionId) == PackageManager.PERMISSION_GRANTED;
            }
        }

        /**
         * This method is called when {@link #isNonSystemPermission} returns true. For example, if using Google+ Sign In,
         * you would create your signIn-Intent and start that activity. Any result will then be passed through to {#link
         * onNonSystemPermissionResult}
         */
        @Override
        public void onRequestNonSystemPermission(Activity activity, String permissionId) {
            // no notifications requested
        }

        /**
         * Method is called when your Activity called in {@link #onRequestNonSystemPermission} has returned with a result
         */
        @Override
        public boolean onNonSystemPermissionResult(Activity activity, int requestCode, int resultCode, Intent data) {
            if (requestCode == RESULT_REQUEST_CODE_NOTIFICATION) {
                AppPrefs.getInstance(activity).setTaskReminderComplete(resultCode == Activity.RESULT_OK);
                return true;
            }
            return false;
        }
    }

    /**
     * Empty task provider, all tasks will be done through new SageResearch SDK
     */
    public static class SageTaskProvider extends TaskProvider {
        private HashMap<String, Task> map = new HashMap<>();
        public SageTaskProvider(Context context) {}
        @Override
        public Task get(String taskId) {
            return map.get(taskId);
        }
        @Override
        public void put(String id, Task task) {
            map.put(id, task);
        }
    }

    public static class SageResourceManager extends ResourceManager {
        public static final int PEM = 4;
        public static final int SURVEY = 5;

        public static final String BASE_PATH_HTML = "html";
        private static final String BASE_PATH_JSON = "json";
        private static final String BASE_PATH_JSON_SURVEY = "json/survey";
        private static final String BASE_PATH_PDF = "pdf";
        private static final String BASE_PATH_VIDEO = "mp4";
        public static final String SIGNUP_TASK_RESOURCE = "Signup";

        public SageResourceManager() {
            super();
        }

        @Override
        public String generatePath(int type, String name) {
            String dir;
            switch (type) {
                default:
                    dir = null;
                    break;
                case Resource.TYPE_HTML:
                    dir = BASE_PATH_HTML;
                    break;
                case Resource.TYPE_JSON:
                    dir = BASE_PATH_JSON;
                    break;
                case Resource.TYPE_PDF:
                    dir = BASE_PATH_PDF;
                    break;
                case Resource.TYPE_MP4:
                    dir = BASE_PATH_VIDEO;
                    break;
                case SURVEY:
                    dir = BASE_PATH_JSON_SURVEY;
                    break;
            }

            StringBuilder path = new StringBuilder();
            if (!TextUtils.isEmpty(dir)) {
                path.append(dir).append('/');
            }

            return path.append(name).append('.').append(getFileExtension(type)).toString();
        }

        @Override
        public String getFileExtension(int type) {
            switch (type) {
                case PEM:
                    return "pem";
                case SURVEY:
                    return "json";
                default:
                    return super.getFileExtension(type);
            }
        }

        @Override
        public Resource getStudyOverview() {
            return new Resource(Resource.TYPE_HTML, BASE_PATH_HTML, "StudyInformation");
        }

        @Override
        public Resource getConsentHtml() {
            return new Resource(Resource.TYPE_HTML, BASE_PATH_HTML, "consent");
        }

        @Override
        public Resource getConsentPDF() {
            return null;
        }

        @Override
        public Resource getConsentSections() {
            return new Resource(Resource.TYPE_JSON, BASE_PATH_JSON, "learn", SectionModel.class);
        }

        @Override
        public Resource getLearnSections() {
            return new Resource(Resource.TYPE_HTML, BASE_PATH_HTML, "app_privacy_policy");
        }

        @Override
        public Resource getPrivacyPolicy() {
            return new Resource(Resource.TYPE_HTML, BASE_PATH_HTML, "PrivacyPolicy");
        }

        @Override
        public Resource getLicense() {
            return new Resource(Resource.TYPE_HTML, BASE_PATH_HTML, "Licenses");
        }

        @Override
        public Resource getSoftwareNotices() {
            return new Resource(Resource.TYPE_HTML, BASE_PATH_HTML, "software_notices");
        }

        @Override
        public Resource getTasksAndSchedules() {
            return new Resource(Resource.TYPE_JSON,
                    BASE_PATH_JSON,
                    "tasks_and_schedules",
                    SchedulesAndTasksModel.class);
        }

        @Override
        public Resource getTask(String taskFileName) {
            return new Resource(Resource.TYPE_JSON,
                    BASE_PATH_JSON_SURVEY,
                    taskFileName,
                    TaskModel.class);
        }

        @Override
        public Resource getInclusionCriteria() {
            return null;
        }

        @Override
        public Resource getOnboardingManager() {
            return null;
        }
    }

    public static class SageUiManager extends UiManager {
        @Override
        public List<ActionItem> getMainActionBarItems() {
            List<ActionItem> navItems = new ArrayList<>();

            return navItems;
        }

        @Override
        public List<ActionItem> getMainTabBarItems() {
            List<ActionItem> navItems = new ArrayList<>();

            return navItems;
        }

        @Override
        public Step getInclusionCriteriaStep(Context context) {
            return null;
        }

        @Override
        public boolean isInclusionCriteriaValid(StepResult result) {
            return false;
        }

        @Override
        public boolean isConsentSkippable() {
            return true;
        }
    }
}
