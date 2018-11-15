/*
 * Copyright (C) 2017 The Android Open Source Project
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
package org.eclipse.andworx.process;

/**
 * Job execution interface.
 */
public interface JobProcessor {

	/**
	 * Start new session
	 * @return session id
	 */
	public int start();
	/**
	 * End session
	 * @param key Session id
	 * @throws InterruptedException
	 */
	public void end(int key) throws InterruptedException;
	/**
	 * Returns maximum number of processes
	 * @return int
	 */
	int getParallelism();
}
