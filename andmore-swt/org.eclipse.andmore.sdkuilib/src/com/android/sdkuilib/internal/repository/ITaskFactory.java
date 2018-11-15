/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.sdkuilib.internal.repository;


/**
 * A factory starts {@link ITask}s as a job and returns immediately.
 * An optional Runnable parameter can be given to run when the job is done.
 */
public interface ITaskFactory {

    /**
     * Starts a new task with a new {@link ITaskMonitor}.
     * The task will execute asynchronously in a job.
     * @param title The title of the task, displayed in the monitor if any.
     * @param task The task to run.
     * @param onTerminateTask Callback when task done. Can be null
     */
    void start(String title, ITask task, Runnable onTerminateTask);

}
