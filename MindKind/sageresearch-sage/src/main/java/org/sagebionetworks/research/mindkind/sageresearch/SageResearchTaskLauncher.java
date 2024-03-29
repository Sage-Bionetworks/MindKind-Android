package org.sagebionetworks.research.mindkind.sageresearch;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.sagebionetworks.research.domain.repository.TaskRepository;
import org.sagebionetworks.research.domain.task.TaskInfoView;
import org.sagebionetworks.research.mobile_ui.perform_task.PerformTaskActivity;
import org.sagebionetworks.research.presentation.model.TaskView;

import java.util.UUID;

import javax.inject.Inject;

public class SageResearchTaskLauncher {
    private final TaskRepository taskRepository;

    @Inject
    public SageResearchTaskLauncher(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public void launchTask(@NonNull Context context, @NonNull String taskIdentifier,
            @Nullable UUID taskRunUUID) {
        TaskInfoView taskInfoView = taskRepository.getTaskInfo(taskIdentifier).blockingGet();

        //TODO: mapper
        TaskView taskView = TaskView.builder().setIdentifier(taskInfoView.getIdentifier()).build();

        Intent intent = PerformTaskActivity.createIntent(context, taskView, taskRunUUID);
        context.startActivity(intent);
    }
    public void launchTask(@NonNull Context context, @NonNull Fragment fragment, @NonNull String taskIdentifier,
            @Nullable UUID taskRunUUID, @NonNull int requestCode, @Nullable Bundle options) {
        TaskInfoView taskInfoView = taskRepository.getTaskInfo(taskIdentifier).blockingGet();

        TaskView taskView = TaskView.builder().setIdentifier(taskInfoView.getIdentifier()).build();

        Intent intent = PerformTaskWithResultActivity.Companion.createIntent(context, taskView, taskRunUUID);
        fragment.startActivityForResult(intent, requestCode, options);
    }
}
