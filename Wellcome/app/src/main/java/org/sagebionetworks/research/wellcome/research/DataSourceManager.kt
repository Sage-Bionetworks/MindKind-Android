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

package org.sagebionetworks.research.wellcome.research

import org.sagebionetworks.research.wellcome.research.MpIdentifier.BACKGROUND
import org.sagebionetworks.research.wellcome.research.MpIdentifier.DEMOGRAPHICS
import org.sagebionetworks.research.wellcome.research.MpIdentifier.ENGAGEMENT
import org.sagebionetworks.research.wellcome.research.MpIdentifier.MEDICATION
import org.sagebionetworks.research.wellcome.research.MpIdentifier.MOTIVATION
import org.sagebionetworks.research.wellcome.research.MpIdentifier.STUDY_BURST_COMPLETED
import org.sagebionetworks.research.wellcome.research.MpIdentifier.STUDY_BURST_REMINDER
import org.sagebionetworks.research.wellcome.research.MpIdentifier.SYMPTOMS
import org.sagebionetworks.research.wellcome.research.MpIdentifier.TAPPING
import org.sagebionetworks.research.wellcome.research.MpIdentifier.TREMOR
import org.sagebionetworks.research.wellcome.research.MpIdentifier.TRIGGERS
import org.sagebionetworks.research.wellcome.research.MpIdentifier.WALK_AND_BALANCE
import org.sagebionetworks.research.wellcome.research.MpTaskInfo.Tapping
import org.sagebionetworks.research.wellcome.research.MpTaskInfo.Tremor
import org.sagebionetworks.research.wellcome.research.MpTaskInfo.WalkAndBalance
import org.sagebionetworks.research.sageresearch.manager.ActivityGroup
import org.sagebionetworks.research.sageresearch.manager.ActivityGroupObject
import org.sagebionetworks.research.sageresearch.manager.TaskInfoObject
import org.threeten.bp.LocalDateTime
import java.util.Random

class DataSourceManager {

    // TODO: mdephillips 9/4/18 the contents of this companion object should come from bridge config,
    // TODO: mdephillips 9/4/18 but that architecture hasn't been finished yet
    companion object {

        val studyBurstGroup = ActivityGroupObject("Study Burst", "Study Burst",
                activityIdentifiers = setOf(
                        TAPPING, TREMOR, WALK_AND_BALANCE, STUDY_BURST_COMPLETED))

        val measuringGroup = ActivityGroupObject(
                "Measuring", "Measuring",
                tasks = setOf(Tapping, WalkAndBalance, Tremor),
                activityIdentifiers = setOf(TAPPING, TREMOR, WALK_AND_BALANCE),
                schedulePlanGuid = "3d898a6f-1ef2-4ece-9e9f-025d94bcd130")

        val trackingGroup = ActivityGroupObject(
                "Tracking", "Tracking",
                activityIdentifiers = setOf(SYMPTOMS, MEDICATION, TRIGGERS),
                activityGuidMap = mapOf(
                        SYMPTOMS to "60868b71-30a4-4e04-a00b-3aca6651deb2",
                        MEDICATION to "273c4518-7cb6-4496-b1dd-c0b5bf291b09",
                        TRIGGERS to "b0f07b7e-408e-4d50-9368-8220971e570c"
                ))

        const val surveyActivityGroupIdentifier = "Surveys"
        val surveyGroup = ActivityGroupObject(
                surveyActivityGroupIdentifier, surveyActivityGroupIdentifier,
                activityIdentifiers = setOf(DEMOGRAPHICS, ENGAGEMENT, MOTIVATION, BACKGROUND))

        val installedGroups: Array<ActivityGroup>
            get() {
                return arrayOf(studyBurstGroup, measuringGroup, trackingGroup)
            }

        /**
         * @property parkinsonsDataGroup the data group that is given to users who have been diagnosed with parksions
         */
        val parkinsonsDataGroup = "parkinsons"

        fun installedGroup(forIdentifier: String): ActivityGroup? {
            return installedGroups.firstOrNull { it.identifier == forIdentifier }
        }

        @JvmStatic
        fun defaultEngagementGroups(): Set<Set<String>> {
            return setOf(
                    setOf("gr_SC_DB","gr_SC_CS"),
                    setOf("gr_BR_AD","gr_BR_II"),
                    setOf("gr_ST_T","gr_ST_F"),
                    setOf("gr_DT_F","gr_DT_T"))
        }

        @JvmStatic
        fun randomDefaultEngagementGroups(): Set<String> {
            return defaultEngagementGroups().randomElements() ?: setOf()
        }
    }
}

object MpTaskInfo {
    val Tapping = TaskInfoObject(TAPPING, TAPPING,
            imageName = "ic_finger_tapping", estimatedMinutes = 1)

    val WalkAndBalance = TaskInfoObject(WALK_AND_BALANCE, WALK_AND_BALANCE,
            imageName = "ic_walk_and_stand", estimatedMinutes = 6)

    val Tremor = TaskInfoObject(TREMOR, TREMOR,
            imageName = "ic_tremor", estimatedMinutes = 4)
}

data class CompletionTask(
        val activityIdentifiers: LinkedHashSet<String>,
        val day: Int) {
    fun preferredIdentifier(): String? {
        return activityIdentifiers.intersect(linkedSetOf(DEMOGRAPHICS, ENGAGEMENT))
                .firstOrNull() ?: activityIdentifiers.firstOrNull()
    }
}

/**
 * The study burst configuration is a data class that can be added to the `AppConfig.data`.
 */
data class StudyBurstConfiguration(
        /**
         * @property identifier of the task.
         */
        val identifier: String = MpIdentifier.STUDY_BURST_COMPLETED,
        /**
         * @property numberOfDays in the study burst.
         */
        val numberOfDays: Int = 14,
        /**
         * @property minimumRequiredDays in the study burst.
         */
        val minimumRequiredDays: Int = 10,
        /**
         * @property maxDayCount The maximum number of days in a study burst.
         */
        var maxDayCount: Int = 19,
        /**
         * @property expiresLimit the time limit (in seconds) until the progress expires, defaults to 60 minutes
         */
        val expiresLimit: Long = 60 * 60L,
        /**
         * @property taskGroupIdentifier used to mark the active tasks included in the study burst.
         */
        val taskGroupIdentifier: String = MpIdentifier.MEASURING,
        /**
         * @property motivationIdentifier The identifier for the initial engagement survey.
         */
        val motivationIdentifier: String = MpIdentifier.MOTIVATION,
        /**
         * @property completionTasks for each day of the study burst.
         */
        val completionTasks: Set<CompletionTask> = setOf(
                CompletionTask(linkedSetOf(STUDY_BURST_REMINDER, DEMOGRAPHICS), 1),
                CompletionTask(linkedSetOf(BACKGROUND), 9)
                /**,
                // TODO: mdephillips 10/16/18 Add engagement back in when development is done on those features
                CompletionTask(linkedSetOf(ENGAGEMENT), 14)*/),
        /**
         * @property engagementGroups set of the possible engagement data groups.
         */
        val engagementGroups: Set<Set<String>>? = DataSourceManager.defaultEngagementGroups(),
        /**
         * Defaults to each study burst repeating after 13 weeks
         * @property repeatIntervalInDays the number of days before a new study burst is scheduled
         */
        val repeatIntervalInDays: Long = 13 * 7L) {

    /**
     * @return a set of the completion task's activity identifiers
     */
    fun completionTaskIdentifiers(): Set<String> {
        return completionTasks.flatMap { it.activityIdentifiers }.union(setOf(motivationIdentifier))
    }

    /**
     * @return a randomized set of possible combinations of engagement groups.
     */
    fun randomEngagementGroups(): Set<String>? {
        return engagementGroups.randomElements()
    }

    /**
     * @return the start of the expiration time window
     */
    fun startTimeWindow(now: LocalDateTime): LocalDateTime {
        return now.minusSeconds(expiresLimit)
    }
}

/**
 * @return a new set from combining a random element of each of the subsets if any exist
 */
fun <T> Set<Set<T>>?.randomElements(): Set<T>? {
    if (this == null) {
        return null
    }
    if (isEmpty()) {
        return null
    }
    val random = Random()
    return this.flatMap { subSet->
        if (subSet.isEmpty()) {
            return@flatMap setOf<T>()
        }
        val randomIndex= random.nextInt(subSet.size)
        setOf(subSet.elementAt(randomIndex))
    }.toSet()
}