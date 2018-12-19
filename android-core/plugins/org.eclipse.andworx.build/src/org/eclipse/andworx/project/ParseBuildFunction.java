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
import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.andworx.build.AndworxFactory;
import org.eclipse.andworx.model.CodeSource;
import org.eclipse.andworx.polyglot.AndworxBuildParser;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobFunction;

import com.android.ide.common.xml.AndroidManifestParser;
import com.android.ide.common.xml.ManifestData;
import com.google.common.base.Throwables;

/**
 * Function to parse an Android manifest
 */
public class ParseBuildFunction implements IJobFunction {

	public static String FUNCTION_NAME = "Parse build ";

	protected final File buildFile;
	protected final AndroidWizardListener androidWizardListener;
	protected final AndworxBuildParser andworxBuildParser;
	protected File manifestFile;

	public ParseBuildFunction(
			File buildFile,
			AndroidWizardListener androidWizardListener,
			AndworxBuildParser andworxBuildParser) {
		this.buildFile = buildFile;
		this.androidWizardListener = androidWizardListener;
		this.andworxBuildParser = andworxBuildParser;
	}
	
	public File getManifestFile() {
		return manifestFile;
	}

	@Override
	public IStatus run(IProgressMonitor monitor) {
        try { 
    		andworxBuildParser.parse(buildFile);
			manifestFile = new File(buildFile.getParentFile(), andworxBuildParser.getAndroidDigest().getSourceFolder(CodeSource.manifest));
			if (manifestFile.exists()) {
	        	Path path = Paths.get(manifestFile.toURI());
	            ManifestData manifestData = AndroidManifestParser.parse(path);
	            androidWizardListener.onManifestParsed(manifestData);
			} else {
				androidWizardListener.onNoManifest(manifestFile.getAbsolutePath());
	        	return Status.CANCEL_STATUS;
			}
        } catch (Exception e) {
        	e.printStackTrace();
        	String message = "Error " + FUNCTION_NAME + ": " + Throwables.getStackTraceAsString(Throwables.getRootCause(e));
        	androidWizardListener.onError(message);
        	AndworxFactory.getAndworxContext().getBuildConsole().logAndPrintError(e, FUNCTION_NAME, message);
        	return Status.CANCEL_STATUS;
        }
		return Status.OK_STATUS;
	}		
}
