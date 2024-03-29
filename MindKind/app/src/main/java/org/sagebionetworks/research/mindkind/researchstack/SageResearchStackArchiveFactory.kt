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

package org.sagebionetworks.research.mindkind.researchstack

import org.joda.time.DateTime
import org.sagebionetworks.researchstack.backbone.answerformat.AnswerFormat
import org.sagebionetworks.researchstack.backbone.result.Result
import org.sagebionetworks.researchstack.backbone.result.StepResult
import org.sagebionetworks.bridge.data.Archive
import org.sagebionetworks.bridge.data.ArchiveFile
import org.sagebionetworks.bridge.data.JsonArchiveFile
import org.sagebionetworks.bridge.researchstack.survey.SurveyAnswer
import org.sagebionetworks.research.mindkind.researchstack.framework.SageTaskHelper
import org.sagebionetworks.research.sageresearch.viewmodel.ResearchStackUploadArchiveFactory
import org.slf4j.LoggerFactory

open class SageResearchStackArchiveFactory: ResearchStackUploadArchiveFactory() {

    companion object {
        const val studyBurstArchiveFileName = "tasks"
        const val answersFilename = "answers"
        // List of result identifiers that will be excluded from bridge result archives
        // This should include steps that contain sensitive information that should only be sent to user reports
        val resultExclusionList = listOf<String>()
    }

    private val logger = LoggerFactory.getLogger(SageResearchStackArchiveFactory::class.java)

    /**
     * Can be overridden by sub-class for custom data archiving
     * @param archiveBuilder fill this builder up with files from the flattenedResultList
     * @param flattenedResultList read these and add them to the archiveBuilder
     */
    override fun addFiles(
            archiveBuilder: Archive.Builder,
            flattenedResultList: List<org.sagebionetworks.researchstack.backbone.result.Result>?,
            taskIdentifier: String) {

//        if (SageTaskIdentifier.STUDY_BURST_COMPLETED_UPLOAD == taskIdentifier) {
//            // Study burst completed marker has custom upload archive names "tasks"
//            // and all results are consolidated into that file with their result identifiers
//            archiveBuilder.addDataFile(fromResultList(studyBurstArchiveFileName, flattenedResultList))
//        } else if (SageTaskIdentifier.STUDY_BURST_REMINDER == taskIdentifier) {
//            // Study burst completed marker has custom upload archive names "answers"
//            // and all results are consolidated into that file with their result identifiers
//            archiveBuilder.addDataFile(fromResultList(answersFilename, flattenedResultList))
//        } else {
            super.addFiles(archiveBuilder, flattenedResultList, taskIdentifier)
//        }
    }

    /**
     * Packages up all the results into a single json archive file
     * @param filename for the json archive
     * @param resultList to include all the results in a single json archive
     */
    protected fun fromResultList(
            filename: String,
            resultList: List<org.sagebionetworks.researchstack.backbone.result.Result>?): JsonArchiveFile {

        val answerMap = HashMap<String, Any>()
        resultList?.forEach {
            SageTaskHelper.addToAnswerMap(this, answerMap, it)
        }

        // The answer group will not have a valid end date, if one is needed,
        // consider adding key_endDate as a key/value in answer map above
        return JsonArchiveFile(filename, DateTime.now(), answerMap)
    }

    /**
     * Override to allow for the resultExclusionList to filter out results
     */
    override fun fromResult(result: Result): ArchiveFile? {
        if (resultExclusionList.contains(result.identifier)) {
            return null
        }
        return super.fromResult(result)
    }

    /**
     * Due to the nature of the AppCore survey UI being directly coupled to Result types,
     * We need to override the custom survey answers to tell the archive factory
     * which types of survey answers or custom result types should be archived as
     * @param stepResult to transform into a survey answer
     * @param format the answer format that should be analyzed to make a survey answer
     * @return a valid SurveyAnswer, or null if conversion is unknown
     */
    override fun customSurveyAnswer(stepResult: StepResult<*>, format: AnswerFormat): SurveyAnswer? {
        if (stepResult.results == null || stepResult.results.isEmpty()) {
            return null  // question was skipped
        }
        return super.customSurveyAnswer(stepResult, format)
    }
}