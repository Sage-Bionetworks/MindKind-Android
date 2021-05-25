
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

package org.sagebionetworks.research.mindkind.backgrounddata

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.*
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.*
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.SensorManager.SENSOR_DELAY_NORMAL
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.Builder
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.room.Room
import dagger.android.DaggerService
import hu.akarnokd.rxjava.interop.RxJavaInterop
import io.reactivex.Completable
import io.reactivex.Scheduler
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import org.joda.time.DateTime
import org.joda.time.Days
import org.joda.time.Weeks
import org.joda.time.format.ISODateTimeFormat
import org.sagebionetworks.bridge.android.manager.UploadManager
import org.sagebionetworks.research.domain.Schema
import org.sagebionetworks.research.domain.result.implementations.FileResultBase
import org.sagebionetworks.research.domain.result.implementations.TaskResultBase
import org.sagebionetworks.research.mindkind.R.drawable
import org.sagebionetworks.research.mindkind.R.string
import org.sagebionetworks.research.mindkind.TaskListActivity
import org.sagebionetworks.research.mindkind.conversation.ConversationSurveyActivity
import org.sagebionetworks.research.mindkind.research.SageTaskIdentifier
import org.sagebionetworks.research.mindkind.room.BackgroundDataEntity
import org.sagebionetworks.research.mindkind.room.BackgroundDataTypeConverters
import org.sagebionetworks.research.mindkind.room.MindKindDatabase
import org.sagebionetworks.research.mindkind.util.NoLimitRateLimiter
import org.sagebionetworks.research.mindkind.util.RateLimiter
import org.sagebionetworks.research.sageresearch_app_sdk.TaskResultUploader
import org.threeten.bp.Instant
import org.threeten.bp.LocalDateTime
import org.threeten.bp.ZonedDateTime
import org.threeten.bp.temporal.ChronoUnit
import java.io.File
import java.io.FileOutputStream
import java.util.*
import javax.inject.Inject

class BackgroundDataService : DaggerService(), SensorEventListener {

    companion object {
        private const val TAG = "BackgroundDataService"

        public const val ACTIVITY_UPLOAD_DATA_ACTION =
                "org.sagebionetworks.research.MindKind.UploadDataToBridge"
        public const val WIFI_CHARGING_UPLOAD_DATA_ACTION =
                "org.sagebionetworks.research.MindKind.WifiChargingUploadDataToBridge"
        public const val BACKGROUND_DATA_DB_FILENAME =
                "org.sagebionetworks.research.MindKind.BackgroundData"
        public const val DATA_USAGE_RECEIVER_ACTION =
                "org.sagebionetworks.research.MindKind.DataUsageReceiver"
        public const val AMBIENT_LIGHT_WORKER_ACTION =
                "org.sagebionetworks.research.MindKind.AmbientLightWorker"

        public const val SHOW_ENGAGEMENT_NOTIFICATION_ACTION =
                "org.sagebionetworks.research.MindKind.ShowEngagementNotification"

        public const val SETTINGS_CHANGED_ACTION =
                "org.sagebionetworks.research.MindKind.SettingsChanged"

        private const val TASK_IDENTIFIER = SageTaskIdentifier.BACKGROUND_DATA
        private const val FOREGROUND_NOTIFICATION_ID = 100
        public const val JSON_MIME_CONTENT_TYPE = "application/json"

        private const val FOREGROUND_CHANNEL_ID = "MindKind Passive Data"
        private const val ENGAGEMENT_CHANNEL_ID = "MindKind Engagement"

        private const val ENGAGEMENT_REQUEST_CODE = 2314
        // The number of days without answers a conversation survey that triggers
        // and engagement notification
        private const val ENGAGEMENT_TRIGGER_DAYS = 14

        // True when this service is running, false otherwise
        public var isServiceRunning = false

        private fun createMinuteRateLimit(minutes: Int): RateLimiter {
            return RateLimiter(minutes * 1000L * 60L)
        }

        private fun createHourRateLimit(hours: Double): RateLimiter {
            return RateLimiter((hours * 1000.0 * 60.0 * 60.0).toLong())
        }

        fun chargingTimeData(intentAction: String?): String {
            return when (intentAction) {
                Intent.ACTION_POWER_CONNECTED -> "connected"
                Intent.ACTION_POWER_DISCONNECTED -> "disconnected"
                else -> "error"
            }
        }

        fun screenTimeData(intentAction: String?): String {
            return when (intentAction) {
                Intent.ACTION_SCREEN_ON -> "on"
                Intent.ACTION_SCREEN_OFF -> "off"
                Intent.ACTION_USER_PRESENT -> "present"
                else -> "error"
            }
        }

        public const val studyStartDateKey = "StudyStartDate"
        public const val completedTasksKey = "completeTasks"
        public val dateFormatter = ISODateTimeFormat.dateTime().withOffsetParsed()

        fun createSharedPrefs(context: Context): SharedPreferences {
            return context.getSharedPreferences("Mindkind", MODE_PRIVATE)
        }

        public const val studyDurationInWeeks = 12

        fun progressInStudy(sharedPreferences: SharedPreferences): ProgressInStudy {
            val now = DateTime.now()
            val studyStartDate = sharedPreferences.getString(studyStartDateKey, null)?.let {
                dateFormatter.parseDateTime(it)
            } ?: now
            val week = Weeks.weeksBetween(studyStartDate.withTimeAtStartOfDay(), now).weeks + 1
            val daysFromStart = Days.daysBetween(studyStartDate.withTimeAtStartOfDay(), now).days + 1
            val dayOfWeek = (daysFromStart % 7) + 1
            return ProgressInStudy(week, dayOfWeek, daysFromStart)
        }

        fun isConversationComplete(sharedPreferences: SharedPreferences, identifier: String): Boolean {
            val progress = progressInStudy(sharedPreferences)
            sharedPreferences.getStringSet(completedTasksKey, null)?.let {
                return it.contains("$identifier $progress")
            }
            return false
        }

        // We need the changes to be reflected immediately
        @SuppressLint("ApplySharedPref")
        fun markConversationComplete(sharedPreferences: SharedPreferences, identifier: String) {
            val progress = progressInStudy(sharedPreferences)
            val editPrefs = sharedPreferences.edit()
            editPrefs.putString(ConversationSurveyActivity.completedDateKey, LocalDateTime.now().toString())

            val prev = sharedPreferences.getStringSet(completedTasksKey, null)
                    ?.toMutableSet() ?: mutableSetOf()
            prev.add("$identifier $progress")
            editPrefs.putStringSet(completedTasksKey, prev)

            editPrefs.commit()
        }

        // List of data types to track
        // This should be hooked up to the permission manager's list of data the user wants us to track
        public val allDataTypes = listOf(
                SageTaskIdentifier.ScreenTime,
                SageTaskIdentifier.BatteryStatistics,
                SageTaskIdentifier.ChargingTime,
                SageTaskIdentifier.DataUsage,
                SageTaskIdentifier.AmbientLight)

        fun loadDataAllowedToBeTracked(sharedPrefs: SharedPreferences): List<String> {
            return allDataTypes.filter {
                // TODO: mdephillips 5/24/21 switch back to default to false before launch
                //sharedPrefs.getBoolean("DataTracking$it", false)
                sharedPrefs.getBoolean("DataTracking$it", true)
            }
        }

        @SuppressLint("ApplySharedPref")
        fun setDataAllowedToBeTracked(sharedPrefs: SharedPreferences,
                                      dataToTrack: String, isTracking: Boolean) {
            sharedPrefs.edit().putBoolean("DataTracking$dataToTrack", isTracking).commit()
        }

        fun notifySettingsChanged(context: Context) {
            // Notify the service that data tracking has changed
            val intent = Intent(context, BackgroundDataService::class.java)
            intent.action = BackgroundDataService.SETTINGS_CHANGED_ACTION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun permanentlyStopSelf(context: Context) {
            WorkUtils.cancelPeriodicWork(context, BridgeUploadWorker.periodicWorkName)
            WorkUtils.cancelPeriodicWork(context, DataUsageWorker.periodicWorkName)
            context.stopService(Intent(context, BackgroundDataService::class.java))
        }
    }

    private lateinit var sharedPrefs: SharedPreferences

    private val typeConverters = BackgroundDataTypeConverters()
    lateinit var database: MindKindDatabase

    // Injected from BridgeAndroidSdk, this controls uploading a TaskResult
    @Inject
    lateinit var taskResultUploader: TaskResultUploader

    // Injected from BridgeAndroidSdk, it controls uploading past failed uploads
    @Inject
    lateinit var uploadManager: UploadManager

    private var sensorManager: SensorManager? = null

    // Updated onCreate and when the service gets settings changed commands
    private var dataAllowedToBeTracked = listOf<String>()

    private val compositeDisposable = CompositeDisposable()
    /**
     * Subscribes to the completable using the CompositeDisposable
     * This is open for unit testing purposes to to run a blockingGet() instead of an asynchronous subscribe
     */
    private fun subscribeCompletableAsync(
            completable: Completable, successMsg: String, errorMsg: String) {

        compositeDisposable.add(completable
                .subscribeOn(asyncScheduler)
                .subscribe({
                    Log.i(TAG, successMsg)
                }, {
                    Log.w(TAG, "$errorMsg ${it.localizedMessage}")
                }))
    }

    /**
     * @property asyncScheduler for performing network and database operations
     */
    private val asyncScheduler: Scheduler get() = Schedulers.io()

    private val receiver = BackgroundDataReceiver()

    // If you always want to log an event, use no rate limit
    private val noRateLimit = NoLimitRateLimiter()

    // Battery changed broadcast receiver is very noisy, so rate limit it every 1 hour
    private val batteryChangedLimiter = createHourRateLimit(1.0)

    // Service main thread handler
    private val handler = Handler()

    // The ambient light runnable
    private var ambientLightValues = ArrayList<Float>()
    private var ambientLightKickoffTime: Long? = null
    private val ambientLightDuration = 1000L * 10L // 10 seconds
    private val ambientLightFreq = 1000L * 60L * 2 // 2 minutes
    private val ambientLightRunnable = Runnable() {
        kickOffAmbientLightUpdates()
    }

    override fun onCreate() {
        Log.d(TAG, "onCreate")
        super.onCreate()

        isServiceRunning = true

        // Used for local data storage
        sharedPrefs = createSharedPrefs(this)

        // Refresh the data that the service should be tracking
        // It is important that this always stays up to date
        dataAllowedToBeTracked = loadDataAllowedToBeTracked(sharedPrefs)

        // Setup daily wifi & charger upload worker
        WorkUtils.enqueueDailyWorkNetwork(this,
                BridgeUploadWorker::class.java, BridgeUploadWorker.periodicWorkName)

        createNotificationChannel()
        createEngagementNotificationChannel()
        startForeground()

        database = Room.databaseBuilder(this,
                MindKindDatabase::class.java,
                BACKGROUND_DATA_DB_FILENAME)
                /* .addMigrations(*migrations) */
                .build()

        // If this is our first time running, mark a starting point for convo complete date
        if (!sharedPrefs.contains(ConversationSurveyActivity.completedDateKey)) {
            sharedPrefs.edit().putString(ConversationSurveyActivity.completedDateKey,
                    LocalDateTime.now().toString()).apply()
        }

        LocalBroadcastManager.getInstance(this).registerReceiver(
                uploadDataReceiver, IntentFilter(ACTIVITY_UPLOAD_DATA_ACTION))

        startBackgroundData()
    }

    private fun startBackgroundData() {

        Log.d(TAG, "Starting background data $dataAllowedToBeTracked")

        // Register broadcast receivers
        val filter = IntentFilter().apply {
            if (dataAllowedToBeTracked.contains(SageTaskIdentifier.ChargingTime)) {
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            }
            if (dataAllowedToBeTracked.contains(SageTaskIdentifier.BatteryStatistics)) {
                addAction(Intent.ACTION_BATTERY_CHANGED)
            }
            if (dataAllowedToBeTracked.contains(SageTaskIdentifier.ScreenTime)) {
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
        }
        registerReceiver(receiver, filter)

        // Setup data usage worker
        if (dataAllowedToBeTracked.contains(SageTaskIdentifier.DataUsage)) {
            WorkUtils.enqueueHourlyWork(this,
                    DataUsageWorker::class.java,
                    DataUsageWorker.periodicWorkName)
        }

        if (dataAllowedToBeTracked.contains(SageTaskIdentifier.AmbientLight)) {
            handler.post(ambientLightRunnable)
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")

        stopBackgroundData()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(uploadDataReceiver)
        compositeDisposable.clear()
        stopForeground(true)

        isServiceRunning = false

        super.onDestroy()
    }

    private fun stopBackgroundData() {
        unregisterReceiver(receiver)
        sensorManager?.unregisterListener(this)
        handler.removeCallbacks(ambientLightRunnable)
    }

    override fun onBind(intent: Intent?): IBinder? {
        Log.d(TAG, "onBind")
        return null
    }

    private val uploadDataReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            uploadDataToBridge()
        }
    }

    /**
     * Packages all existing un-uploaded data and attempts to upload it to bridge
     */
    private fun uploadDataToBridge() {
        // Always try to upload all past failed uploads if any exist
        subscribeCompletableAsync(
                RxJavaInterop.toV2Completable(uploadManager.processUploadFiles()),
                "Successfully uploaded all past data in upload manager",
                "Failed to uploaded all past data in upload manager")

        // Upload all the data from the background data table
        subscribeCompletableAsync(Completable.fromAction {
            val data = database.backgroundDataDao().getData(false)

            if (data.isEmpty()) {
                return@fromAction
            }

            val taskResultList = mutableListOf<TaskResultBase>()
            // Upload all the data types we have data for
            allDataTypes.forEach { taskIdentifier ->
                val filtered = data.filter {
                    return@filter it.dataType == taskIdentifier
                }
                if (filtered.isNotEmpty()) {
                    taskResultList.add(createTaskResult(taskIdentifier, filtered))
                }
            }

            // Upload all task results
            taskResultList.forEach {
                subscribeCompletableAsync(
                        taskResultUploader.processTaskResult(it),
                        "Successfully uploaded ${it.identifier} task result to bridge",
                        "Failed to upload ${it.identifier} task result to bridge")
            }

            // Archive/uploader always eventually succeeds so mark all data as uploaded
            data.forEach {
                it.uploaded = true
            }
            database.backgroundDataDao().upsert(data)

        } .doOnError {
            Log.w(TAG, it.localizedMessage ?: "")
        },
        "Successfully saved BackgroundData to room",
        "Failed to save BackgroundData to room")
    }

    private fun createTaskResult(taskIdentifier: String,
                                 backgroundData: List<BackgroundDataEntity>): TaskResultBase {

        val startTime = backgroundData.firstOrNull()?.date
                ?.toInstant() ?: Instant.now()
        val endTime = backgroundData.lastOrNull()?.date
                ?.toInstant() ?: Instant.now()

        val json = BackgroundDataTypeConverters().gsonExposeOnly.toJson(backgroundData)
        val folderPath = cacheDir.absolutePath + File.separator + taskIdentifier
        val folder = File(folderPath)
        // Create folder to hold data file
        if (!folder.exists()) {
            folder.mkdir()
        }

        val file = File(folder, "data.json")
        val stream = FileOutputStream(file)
        try {
            stream.write(json.toByteArray())
        } finally {
            stream.close()
        }

        val jsonFileResult = FileResultBase("data",
                startTime, endTime, JSON_MIME_CONTENT_TYPE,
                folderPath + File.separator + "data.json")

        var taskResultBase = TaskResultBase(taskIdentifier, startTime,
                endTime, UUID.randomUUID(), Schema(taskIdentifier, 1),
                mutableListOf(), mutableListOf())

        taskResultBase = taskResultBase.addStepHistory(jsonFileResult)
        return taskResultBase
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: startId = $startId")

        when (intent?.action) {
            WIFI_CHARGING_UPLOAD_DATA_ACTION -> uploadDataToBridge()
            DATA_USAGE_RECEIVER_ACTION -> writeDataUsageToDatabase()
            AMBIENT_LIGHT_WORKER_ACTION -> kickOffAmbientLightUpdates()
            SHOW_ENGAGEMENT_NOTIFICATION_ACTION -> checkLastConversationCompleteDate(true)
            SETTINGS_CHANGED_ACTION -> checkAllowedDataTypes()
        }

        // Always check last conversation at this point
        checkLastConversationCompleteDate()

        return START_STICKY
    }

    private fun startForeground() {
        val notification = createForegroundNotification(
                "Monitoring for data changes.  We only collect data you have consented to sharing.")
        startForeground(FOREGROUND_NOTIFICATION_ID, notification)
    }

    private fun createForegroundNotification(text: String? = null): Notification {
        return Builder(this, FOREGROUND_CHANNEL_ID)
                .setContentText(text ?: "")
                .setSmallIcon(drawable.ic_status_bar)
                .setStyle(NotificationCompat.BigTextStyle()
                        .bigText(text ?: ""))
                .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create the NotificationChannel
            val title = getString(string.foreground_channel_title)
            val desc = getString(string.foreground_channel_desc)
            val importance = NotificationManager.IMPORTANCE_LOW
            val mChannel = NotificationChannel(FOREGROUND_CHANNEL_ID, title, importance)
            mChannel.description = desc
            mChannel.importance = IMPORTANCE_LOW
            // Register the channel with the system; can't change importance or other behaviors after this
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(mChannel)
        }
    }

    private fun createEngagementNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create the NotificationChannel
            val title = getString(string.foreground_channel_engagement_title)
            val desc = getString(string.foreground_channel_engagement_desc)
            val importance = NotificationManager.IMPORTANCE_LOW
            val mChannel = NotificationChannel(ENGAGEMENT_CHANNEL_ID, title, importance)
            mChannel.description = desc
            mChannel.importance = IMPORTANCE_DEFAULT
            // Register the channel with the system; can't change importance or other behaviors after this
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(mChannel)
        }
    }

    private fun createEngagementNotification(text: String? = null): Notification {

        val intent = Intent(this, TaskListActivity::class.java)
        intent.action = SHOW_ENGAGEMENT_NOTIFICATION_ACTION
        val pendingIntent = PendingIntent.getActivity(
                this, ENGAGEMENT_REQUEST_CODE,
                intent, FLAG_UPDATE_CURRENT)

        return Builder(this, ENGAGEMENT_CHANNEL_ID)
                .setContentText(text ?: "")
                .setSmallIcon(drawable.ic_status_bar)
                .setStyle(NotificationCompat.BigTextStyle()
                        .bigText(text ?: ""))
                .setContentIntent(pendingIntent)
                .build()
    }

    /**
     * Some broadcast receivers are called too frequently and need rate limited
     * to avoid constantly writing to the database and draining the user's battery
     * @param dataType to rate limit
     */
    fun rateLimiterFor(dataType: String): RateLimiter {
        return when(dataType) {
            SageTaskIdentifier.BatteryStatistics -> batteryChangedLimiter
            //SageTaskIdentifier.AmbientLight -> ambientLightChangedLimiter
            else -> noRateLimit
        }
    }

    /**
     * Writes background data to room asynchronously
     * @param backgroundData to add to room
     */
    private fun writeBackgroundDataToRoom(backgroundData: List<BackgroundDataEntity>) {
        backgroundData.forEach {
            Log.d(TAG, "Writing ${it.dataType} ${it.subType ?: ""} ${it.data}")
        }

        val completable = Completable.fromAction {
            database.backgroundDataDao().upsert(backgroundData)
        } .doOnError {
            Log.w(TAG, it.localizedMessage ?: "")
        }
        subscribeCompletableAsync(completable,
                "Successfully saved BackgroundData to room",
                "Failed to save BackgroundData to room")

        checkLastConversationCompleteDate()
    }

    /**
     * Writes background data to room asynchronously
     * @param backgroundData to add to room
     */
    fun writeBackgroundDataToRoom(backgroundData: BackgroundDataEntity) {
        writeBackgroundDataToRoom(listOf(backgroundData))
    }

    private fun writeDataUsageToDatabase() {
        if (!dataAllowedToBeTracked.contains(SageTaskIdentifier.DataUsage)) {
            // User did not consent to have this tracked
            return
        }

        val now = ZonedDateTime.now()
        val backgroundData = mutableListOf<BackgroundDataEntity>()
        mapOf(
            "totalRx" to TrafficStats.getTotalRxBytes(),
            "totalTx" to TrafficStats.getTotalTxBytes(),
            "mobileRx" to TrafficStats.getMobileRxBytes(),
            "mobileTx" to TrafficStats.getMobileTxBytes()).forEach {

            backgroundData.add(BackgroundDataEntity(
                    date = now,
                    dataType = SageTaskIdentifier.DataUsage,
                    subType = it.key,
                    uploaded = false,
                    data = it.value.toString()))
        }

        writeBackgroundDataToRoom(backgroundData)
    }

    private fun kickOffAmbientLightUpdates() {
        if (!dataAllowedToBeTracked.contains(SageTaskIdentifier.DataUsage)) {
            // User did not consent to have this tracked
            return
        }

        // Unfortunately, android doesn't provide a way to directly read the
        // ambient light sensor, so let's register for callbacks,
        // grab all we can in ten seconds, write to db, and then stop listening.
        // See the onSensorChanged function below.
        sensorManager = getSystemService(SENSOR_SERVICE) as? SensorManager
        sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)?.let {
            ambientLightValues.clear()
            ambientLightKickoffTime = null
            sensorManager?.unregisterListener(this) // make sure we don't register twice
            sensorManager?.registerListener(this, it, SENSOR_DELAY_NORMAL)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op needed for ambient light sensor
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val sensorEvent = event ?: run { return }
        if(sensorEvent.sensor?.type == Sensor.TYPE_LIGHT) {
            val intensity = sensorEvent.values?.firstOrNull() ?: run { return }
            val now = System.currentTimeMillis()

            // Set initial value of kick off time if necessary
            val ambientLightTimeNotNull = ambientLightKickoffTime ?: System.currentTimeMillis()
            ambientLightKickoffTime = ambientLightTimeNotNull
            ambientLightValues.add(intensity)

            if ((now - ambientLightTimeNotNull) > ambientLightDuration) {
                // Write data to room
                writeBackgroundDataToRoom(BackgroundDataEntity(
                        date = ZonedDateTime.now(),
                        dataType = SageTaskIdentifier.AmbientLight,
                        uploaded = false,
                        data = ambientLightValues.joinToString(", ")))

                // We got our measurements, unregister listener
                sensorManager?.unregisterListener(this)
                // Register for next round of ambient light sensor sampling
                handler.postDelayed(ambientLightRunnable, ambientLightFreq)
            }
        }
    }

    private fun checkLastConversationCompleteDate(debugForceShow: Boolean = false) {
        val lastConversationCompleteStr = sharedPrefs.getString(
                ConversationSurveyActivity.completedDateKey, null) ?: run {
            return
        }
        val lastConvoDate = LocalDateTime.parse(lastConversationCompleteStr)
        val daysFromLastConvo = ChronoUnit.DAYS.between(lastConvoDate, LocalDateTime.now())
        if ((daysFromLastConvo >= ENGAGEMENT_TRIGGER_DAYS) || debugForceShow) {
            val notification = createEngagementNotification(
                    getString(string.engagement_notification_title))

            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(ENGAGEMENT_REQUEST_CODE, notification)
        }
    }

    private fun checkAllowedDataTypes() {
        val newAllowedDataTypes = loadDataAllowedToBeTracked(sharedPrefs)
        // No need to update if data types are the same
        if (dataAllowedToBeTracked.containsAll(newAllowedDataTypes) &&
                newAllowedDataTypes.containsAll(dataAllowedToBeTracked)) {
            return
        }
        // Update the data types and restart the background data trackers
        dataAllowedToBeTracked = newAllowedDataTypes
        stopBackgroundData()
        startBackgroundData()
    }

    inner class BackgroundDataReceiver : BroadcastReceiver() {

        override fun onReceive(ctx: Context, intent: Intent) {
            val dataType = dataType(intent) ?: run {
                Log.e(TAG, "Error creating data type from intent action")
                return
            }

            val now = ZonedDateTime.now()
            val localNow = LocalDateTime.now()

            // Check if we should rate-limit this data type
            if (rateLimiterFor(dataType).shouldLimit(localNow)) {
                return
            }

            val data = // Do dataType specific operations
                    when(dataType) {
                        SageTaskIdentifier.BatteryStatistics -> onReceiveBatteryChanged(intent)
                        SageTaskIdentifier.ScreenTime -> screenTimeData(intent.action)
                        SageTaskIdentifier.ChargingTime -> chargingTimeData(intent.action)
                        else -> onReceiveDefaultActionChanged(intent.action)
                    }

            val backgroundData = BackgroundDataEntity(
                    date = now,
                    dataType = dataType,
                    uploaded = false,
                    data = data)

            writeBackgroundDataToRoom(backgroundData)
        }

        /**
         * @return the background data type for the action
         */
        private fun dataType(intent: Intent): String? {
            val action = intent.action ?: run { return null }
            return when(action) {
                Intent.ACTION_BATTERY_CHANGED -> SageTaskIdentifier.BatteryStatistics
                Intent.ACTION_SCREEN_ON -> SageTaskIdentifier.ScreenTime
                Intent.ACTION_SCREEN_OFF -> SageTaskIdentifier.ScreenTime
                Intent.ACTION_USER_PRESENT -> SageTaskIdentifier.ScreenTime
                Intent.ACTION_POWER_CONNECTED -> SageTaskIdentifier.ChargingTime
                Intent.ACTION_POWER_DISCONNECTED -> SageTaskIdentifier.ChargingTime
                else -> null
            }
        }

        private fun onReceiveBatteryChanged(intent: Intent): String {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val batteryPct = level * 100 / scale.toFloat()
            Log.d(TAG, "Received $batteryPct% ${intent.action}")
            return "$batteryPct%"
        }


        fun onReceiveDefaultActionChanged(intentAction: String?): String? {
            Log.d(TAG, "Received ${intentAction}")
            return null
        }
    }
}

public data class ProgressInStudy(
        /**
         * @property week in study. Initial week is 1, not 0.
         */
        val week: Int,
        /**
         * @property dayOfWeek in the study. Initial day is 1, max is 7, and repeats every 7.
         */
        val dayOfWeek: Int,
        /**
         * @property daysFromStart of the study. Initial day is 1, not 0.
         */
        val daysFromStart: Int) {

    override fun toString(): String {
        return "Week $week | Day $dayOfWeek"
    }
}
