/*
 * BSD 3-Clause License
 *
 * Copyright 2018  Sage Bionetworks. All rights reserved.
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

package org.sagebionetworks.research.mindkind.researchstack.framework;

import android.content.Context;
import androidx.annotation.Nullable;

import org.joda.time.DateTime;
import org.sagebionetworks.researchstack.backbone.AppPrefs;
import org.sagebionetworks.researchstack.backbone.ResourceManager;
import org.sagebionetworks.researchstack.backbone.answerformat.AnswerFormat;
import org.sagebionetworks.researchstack.backbone.model.TaskModel;
import org.sagebionetworks.researchstack.backbone.result.FileResult;
import org.sagebionetworks.researchstack.backbone.result.Result;
import org.sagebionetworks.researchstack.backbone.result.StepResult;
import org.sagebionetworks.researchstack.backbone.storage.NotificationHelper;
import org.sagebionetworks.researchstack.backbone.task.Task;
import org.sagebionetworks.bridge.android.manager.BridgeManagerProvider;
import org.sagebionetworks.bridge.data.Archive;
import org.sagebionetworks.bridge.data.ArchiveFile;
import org.sagebionetworks.bridge.data.JsonArchiveFile;
import org.sagebionetworks.bridge.researchstack.TaskHelper;
import org.sagebionetworks.bridge.researchstack.factory.ArchiveFileFactory;
import org.sagebionetworks.bridge.researchstack.survey.SurveyAnswer;
import org.sagebionetworks.bridge.researchstack.wrapper.StorageAccessWrapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SageTaskHelper extends TaskHelper {

    public static final String ANSWERS_FILENAME = "answers.json";

    /**
     * The RESULT_CONVERSION_MAP allows you to map any step identifier result to whatever you want
     */
    static HashMap<String, String> RESULT_CONVERSION_MAP = new HashMap<>();

    public SageTaskHelper(final StorageAccessWrapper storageAccess,
            final ResourceManager resourceManager, final AppPrefs appPrefs,
            final NotificationHelper notificationHelper,
            final BridgeManagerProvider bridgeManagerProvider) {
        super(storageAccess, resourceManager, appPrefs, notificationHelper, bridgeManagerProvider);
        setArchiveFileFactory(new MpArchiveFileFactory());
    }

    /**
     * @param identifier identifier for the result
     * @return the filename to use for the bridge result
     */
    @Override
    public String bridgifyIdentifier(String identifier) {
        String trueIdentifier = identifier;
        if (RESULT_CONVERSION_MAP.containsKey(identifier)) {
            trueIdentifier = RESULT_CONVERSION_MAP.get(trueIdentifier);
        }
        return super.bridgifyIdentifier(trueIdentifier);
    }

    @Override
    protected Task createSmartSurveyTask(Context context, @Nullable TaskModel taskModel) {
        // We provide our own smart survey tasks
        return surveyFactory.createSmartSurveyTask(context, taskModel);
    }

    /**
     * Can be overridden by sub-class for custom data archiving
     * @param archiveBuilder fill this builder up with files from the flattenedResultList
     * @param flattenedResultList read these and add them to the archiveBuilder
     */
    @Override
    protected void addFiles(Archive.Builder archiveBuilder, List<Result> flattenedResultList, String taskResultId) {

        // We also upload each individual json answer result to support AppCore surveys
        super.addFiles(archiveBuilder, flattenedResultList, taskResultId);

        // The other tasks group the question step results in a single "answers" file
        // This is behavior that the bridge server team has wanted for a long time
        // Once this is proven capable, its functionality should be moved into TaskHelper base class
        Map<String, Object> answersMap = new HashMap<>();
        for (Result result : flattenedResultList) {
            boolean addedToAnswerMap = addToAnswerMap(archiveFileFactory, answersMap, result);

            // This is the default implementation
            if (!addedToAnswerMap) {
                ArchiveFile archiveFile = archiveFileFactory.fromResult(result);
                if (archiveFile != null) {
                    archiveBuilder.addDataFile(archiveFile);
                } else {
                    logger.error("Failed to convert Result to BridgeDataInput " + result.toString());
                }
            }
        }

        if (!answersMap.isEmpty()) {
            // The answer group will not have a valid end date, if one is needed,
            // consider adding key_endDate as a key/value in answer map above
            archiveBuilder.addDataFile(new JsonArchiveFile(ANSWERS_FILENAME, DateTime.now(), answersMap));
        }
    }

    /**
     * Helper method for building an Answer Map that groups all StepResults together
     * for easy storing in client data and easy upload to bridge
     * @param answersMap to append to
     * @param result to analyze and possibly add the result content to
     * @return true if content was added to the answer map, false otherwise
     */
    public static boolean addToAnswerMap(ArchiveFileFactory factory,
            Map<String, Object> answersMap, Result result) {
        boolean addedToAnswerMap = false;
        if (result instanceof StepResult) {
            StepResult stepResult = (StepResult)result;
            addedToAnswerMap = true;
            // This is a question step result, and will be added to the answers group
            Map mapResults = stepResult.getResults();
            for (Object key : mapResults.keySet()) {
                Object value = mapResults.get(key);
                if (key instanceof String && !(value instanceof FileResult)) {
                    // We can only work with String keys
                    String resultKey = stepResult.getIdentifier();
                    if (!StepResult.DEFAULT_KEY.equals(key)) {
                        resultKey = (String)key;
                    }

                    // Some base SurveyAnswer types require special formatting for bridge
                    if (stepResult.getAnswerFormat() != null) {
                        SurveyAnswer surveyAnswer = factory.surveyAnswer(stepResult);
                        if (surveyAnswer instanceof SurveyAnswer.DateSurveyAnswer) {
                            value = ((SurveyAnswer.DateSurveyAnswer) surveyAnswer).getDateAnswer();
                        }
                    }
                    answersMap.put(resultKey, value);
                }
            }
        }
        return addedToAnswerMap;
    }

    /**
     * There is currently an architecture issue in Bridge SDK,
     * Where the survey answer type is coupled to a non-extendable enum,
     * So we need to specifically specify how the these custom answer will become SurveyAnswers
     * We can get rid of this after we fix this https://sagebionetworks.jira.com/browse/AA-91
     */
    public static class MpArchiveFileFactory extends ArchiveFileFactory {
        protected MpArchiveFileFactory() {
            super();
        }

        @Override
        protected String getFilename(String identifier) {
            if (SageTaskHelper.RESULT_CONVERSION_MAP.containsKey(identifier)) {
                return SageTaskHelper.RESULT_CONVERSION_MAP.get(identifier);
            }
            return identifier;
        }

        @Override
        public SurveyAnswer customSurveyAnswer(StepResult stepResult, AnswerFormat format) {
            if (stepResult.getResults() == null || stepResult.getResults().isEmpty()) {
                return null;  // question was skipped
            }
            return super.customSurveyAnswer(stepResult, format);
        }
    }
}
