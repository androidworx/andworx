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
package org.eclipse.andworx;

import java.io.File;
import java.io.IOException;

import org.eclipse.andworx.context.AndroidEnvironment;
import org.eclipse.andworx.exception.AndworxException;
import org.eclipse.andworx.file.FileManager;
import org.eclipse.andworx.polyglot.AndroidConfigurationBuilder;
import org.eclipse.andworx.polyglot.AndworxBuildParser;

import dagger.Module;
import dagger.Provides;

/**
 * Assembles Android configuration read from a Gradle build file
 */
@Module
public class ConfigBuilderModule {
	
	private final File gradleBuildFile;
	private final AndroidEnvironment androidEnvironment;
	
	public ConfigBuilderModule(File gradleBuildFile, AndroidEnvironment androidEnvironment) {
		this.gradleBuildFile = gradleBuildFile;
		this.androidEnvironment = androidEnvironment;
	}
	
    @Provides
    AndroidConfigurationBuilder provideAndroidConfigurationBuilder(FileManager fileManager) {
		AndroidConfigurationBuilder androidConfigurationBuilder = 
				new AndroidConfigurationBuilder(fileManager, gradleBuildFile.getParentFile(), androidEnvironment);
		AndworxBuildParser andworxBuildParser = 
			new AndworxBuildParser(gradleBuildFile, androidConfigurationBuilder);
		try {
			andworxBuildParser.parse();
		} catch (IOException e) {
			throw new AndworxException("Error parsing file " + gradleBuildFile.toString(), e);
		}
		return androidConfigurationBuilder;
    }
}
