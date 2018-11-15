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
package org.eclipse.andworx.options;

import org.eclipse.andworx.context.CodeShrinker;

/**
 * PostProcessing options: removing dead code, obfuscating etc.
 *
 * <p>To configure code and resource shrinkers,
 * Refer to properties already available in the <a
 * href="com.android.build.gradle.internal.dsl.BuildType.html"><code>buildType</code></a> block.
 *
 * <p>To learn more, read <a
 * href="https://developer.android.com/studio/build/shrink-code.html">Shrink Your Code and
 * Resources</a>.
 */
public class PostprocessingOptions {
    private boolean removeUnusedCode;
    private boolean removeUnusedResources;
    private boolean obfuscate;
    private boolean optimizeCode;

	public CodeShrinker getCodeShrinkerType() {
		// TODO - Replace Hard code with configuration setting
		return CodeShrinker.ANDROID_GRADLE;
	}

	public boolean isRemoveUnusedCode() {
		return removeUnusedCode;
	}

	public void setRemoveUnusedCode(boolean removeUnusedCode) {
		this.removeUnusedCode = removeUnusedCode;
	}

	public boolean isRemoveUnusedResources() {
		return removeUnusedResources;
	}

	public void setRemoveUnusedResources(boolean removeUnusedResources) {
		this.removeUnusedResources = removeUnusedResources;
	}

	public boolean isObfuscate() {
		return obfuscate;
	}

	public void setObfuscate(boolean obfuscate) {
		this.obfuscate = obfuscate;
	}

	public boolean isOptimizeCode() {
		return optimizeCode;
	}

	public void setOptimizeCode(boolean optimizeCode) {
		this.optimizeCode = optimizeCode;
	}

}
