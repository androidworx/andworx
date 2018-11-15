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

import org.eclipse.andmore.internal.build.RsSourceChangeHandler;
import org.eclipse.andmore.internal.build.builders.BaseBuilder.AbortBuildException;
import org.eclipse.andworx.context.VariantContext;
import org.eclipse.andworx.task.TaskFactory;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;

import com.android.io.StreamException;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.build.legacy.RenderScriptChecker;

public interface PreCompilerContext {
	IProject getProject();
	IJavaProject getJavaProject();
	TaskFactory getTaskFactory();
	IFolder getGenFolder();
	IFolder getGenManifestPackageFolder() throws CoreException;
	IResourceDelta getDelta(IProject project);
	IMarker markProject(String markerId, String message, int severity);
	
	boolean isDebugLog();
    boolean isMustMergeManifest();
	boolean isMustCompileResources();
	boolean isMustCreateBuildConfig();
	boolean getLastBuildConfigMode();
	
	String getManifestPackage();
	int getSourceProcessorStatus();

	void setMustMergeManifest(boolean mustMergeManifest);
	void saveMustMergeManifest(boolean mustMergeManifest);

	void setMustCompileResources(boolean mustCompileResources);
	void saveMustCompileResources(boolean mustCompileResources);

	void setMustCreateBuildConfig(boolean mustCreateBuildConfig);
	void saveMustCreateBuildConfig(boolean mustCreateBuildConfig);

	void saveManifestPackage(String manifestPackage);
	void saveLastBuildConfigMode(boolean lastBuildConfigMode);

	void removeMarkersFromResource(IResource resource, String markerId);
	void removeMarkersFromContainer(IFolder resFolder, String markerId);
	void handleStreamException(StreamException e);
	void handleException(Throwable t, String message);
	void cleanProject(IProgressMonitor monitor) throws CoreException;
	void addSourceProcessorStatus(int processorStatus);
	
	VariantContext getVariantContext();
	BuildToolInfo getBuildToolInfo() throws AbortBuildException;
	//AidlProcessor initializeAidl(boolean isFullBuild) throws AbortBuildException;
	RsSourceChangeHandler initializeRenderScript(RenderScriptChecker checker, boolean isFullBuild);
	IProgressMonitor getDerivedProgressMonitor();
}
