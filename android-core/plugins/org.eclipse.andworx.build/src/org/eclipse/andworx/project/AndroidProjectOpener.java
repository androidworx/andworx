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
package org.eclipse.andworx.project;

import java.io.File;

import org.eclipse.andmore.base.AndworxJob;
import org.eclipse.andworx.build.AndroidProjectReader;
import org.eclipse.andworx.build.AndworxContext;
import org.eclipse.andworx.build.AndworxFactory;
import org.eclipse.andworx.polyglot.AndworxBuildParser;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.IJobChangeListener;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;

/**
 * Parses manifest and build.gradle files of an Android project to produce a project profile 
 * containing dependencies that have been resolved.
 * Works in 3 stages to progressively update the caller in case the process takes a while to complete.
 * Stage 1 = parse build.gradle
 * Stage 2 = extract package from AndroidManifest.xml
 * Stage 3 = resolve dependencies
 * Also provides a task to persist the imported project configuration.
 */
public abstract class AndroidProjectOpener implements AndroidProjectReader {

	/** Interface to project import wizard page */
	private final AndroidWizardListener androidWizardListener;
	/** Object factory */
	private final AndworxContext objectFactory;

	/**
	 * Construct AndroidProjectOpener object
	 * @param androidWizardListener Interface to project import wizard page
	 */
	public AndroidProjectOpener(AndroidWizardListener androidWizardListener) {
		this.androidWizardListener = androidWizardListener;
		objectFactory = AndworxFactory.getAndworxContext();
	}

	protected void parse(File buildFile, AndworxBuildParser parser) {
		AndroidDigest androidDigest = parser.getAndroidDigest();
		ParseBuildFunction parseFuntion = 
				new ParseBuildFunction(buildFile, androidWizardListener, parser);
		AndworxJob manifestJob = new AndworxJob(ParseBuildFunction.FUNCTION_NAME, parseFuntion);
		CreateProfileFunction createProfileFunction = 
				new CreateProfileFunction(
						androidDigest, 
						androidWizardListener, 
						objectFactory.getMavenServices(), 
						objectFactory.getAndroidEnvironment());
		AndworxJob profileJob = new AndworxJob(CreateProfileFunction.FUNCTION_NAME, createProfileFunction);
		final AndroidProjectOpener self = this;
		// Add job liseners
        final IJobChangeListener manifestListener = new JobChangeAdapter(){
			@Override
			public void done(IJobChangeEvent event) {
				synchronized (self) {
					if (event.getResult() == Status.OK_STATUS)
						androidWizardListener.onConfigParsed(androidDigest);
						profileJob.schedule();
				}
			}};
		manifestJob.addJobChangeListener(manifestListener);
		manifestJob.schedule();
	}


}
