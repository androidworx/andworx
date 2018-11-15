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
package org.eclipse.andmore.internal.build.builders;

import org.eclipse.andmore.AndroidPrintStream;
import org.eclipse.andmore.internal.build.BuildHelper.ResourceMarker;
import org.eclipse.andmore.internal.build.builders.BaseBuilder.AbortBuildException;
import org.eclipse.andworx.context.VariantContext;
import org.eclipse.andworx.task.TaskFactory;
import org.eclipse.andworx.transform.Pipeline;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.jdt.core.IJavaProject;

import com.android.sdklib.BuildToolInfo;

public interface PostCompilerContext extends Pipeline {

	IProject getProject();
	IJavaProject getJavaProject();
	TaskFactory getTaskFactory();
	IResourceDelta getDelta(IProject project);
	IMarker markProject(String markerId, String message, int severity);

	void setStartBuildTime(long value);
	
    /**
     * Dex conversion flag. This is set to true if one of the changed/added/removed
     * file is a .class file. Upon visiting all the delta resource, if this
     * flag is true, then we know we'll have to make the "classes.dex" file.
     */
    boolean isConvertToDex();
    void setConvertToDex(boolean value);
    void saveConvertToDex(boolean value);

    /**
     * Final package build flag.
     */
    boolean isBuildFinalPackage();
    void setBuildFinalPackage(boolean value);
    void saveBuildFinalPackage(boolean value);

	boolean isDebugLog();

	BuildToolInfo getBuildToolInfo() throws AbortBuildException;
	VariantContext getVariantContext();
    AndroidPrintStream getOutStream();
    AndroidPrintStream getErrStream();
    ResourceMarker getResourceMarker();

}
