/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.eclipse.andworx.task;

import java.util.concurrent.Future;

import org.eclipse.andworx.transform.Pipeline;

/**
 * Atomic piece of work for a transform build. The task requires a Pipeline start parameter.
 * This parameter allows transform details to be passed along the tool chain.
 */
public abstract class PipelineBuildTask implements BuildTask {
	
	private final TaskFactory taskFactory;
	private final Pipeline pipeline;

	protected PipelineBuildTask(TaskFactory taskFactory, Pipeline pipeline) {
		this.taskFactory = taskFactory;
		this.pipeline = pipeline;
	}

	public AndroidBuildJob schedule() {
		return taskFactory.create(this);
	}
	
	@Override
	public Future<Void> doFullTaskAction() {
		return doFullTaskAction(pipeline);
	}

	protected abstract Future<Void> doFullTaskAction(Pipeline pipeline);

}
