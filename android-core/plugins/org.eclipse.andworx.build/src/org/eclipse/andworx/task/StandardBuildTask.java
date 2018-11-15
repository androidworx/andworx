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

public abstract class StandardBuildTask implements BuildTask {

	private final TaskFactory taskFactory;

	protected StandardBuildTask(TaskFactory taskFactory) {
		this.taskFactory = taskFactory;
	}
	
	public abstract Future<Void> doFullTaskAction();

	public AndroidBuildJob schedule() {
		return taskFactory.create(this);
	}
	
}
