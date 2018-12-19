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
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;
import org.eclipse.andworx.build.AndworxBuildPlugin;
import org.eclipse.andworx.build.AndworxFactory;
import org.eclipse.andworx.config.AndroidConfig;
import org.eclipse.andworx.context.AndroidEnvironment;
import org.eclipse.andworx.maven.AndworxMavenProject;
import org.eclipse.andworx.maven.Dependency;
import org.eclipse.andworx.maven.MavenDependency;
import org.eclipse.andworx.maven.MavenServices;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobFunction;

import com.android.SdkConstants;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.google.common.io.Files;

public class CreateProfileFunction implements IJobFunction {

	public static String FUNCTION_NAME = "Create Profile";

	protected final AndroidDigest androidDigest;
	protected final AndroidWizardListener androidWizardListener;
	protected final AndroidEnvironment androidEnvironment;
	protected final MavenServices mavenServices;

	public CreateProfileFunction(
			AndroidDigest androidDigest,
			AndroidWizardListener androidWizardListener,
			MavenServices mavenServices,
			AndroidEnvironment androidEnvironment) {
		this.androidDigest = androidDigest;
		this.androidWizardListener = androidWizardListener;
		this.mavenServices = mavenServices;
		this.androidEnvironment = androidEnvironment;
	}

	@Override
	public IStatus run(IProgressMonitor monitor) {
    	File tempDir = Files.createTempDir();
    	// Service interface for m2e interactions 
        try { 
			// Allow "android-" prefix for platform target 
        	AndroidConfig androidConfig = androidDigest.getAndroidConfig();
		    final AndroidVersion hash = AndroidTargetHash.getVersionFromHash(androidConfig.getCompileSdkVersion());
		    String targetHashString = null;
		    if (hash != null) {
		    	targetHashString = AndroidTargetHash.getPlatformHashString(hash);
		    } // TODO - Report bad value
			File pomXml = new File(tempDir, mavenServices.getPomFilename());
	        mavenServices.createMavenModel(pomXml, androidDigest.getMavenModel());
	        MavenProject mavenProject = mavenServices.readMavenProject(pomXml);
			ProjectProfile projectProfile = 
					new ProjectProfile( 
						new Identity(
							mavenProject.getGroupId(), 
							mavenProject.getArtifactId(), 
							mavenProject.getVersion()));
			if (targetHashString != null)
				projectProfile.setTargetHash(targetHashString);
			projectProfile.setBuildToolsVersion(androidConfig.getBuildToolsVersion());
			AndworxMavenProject andworxProject = mavenServices.createAndworxProject(mavenProject);
	    	if (SdkConstants.EXT_AAR.equals(mavenProject.getPackaging())) {
	    	    projectProfile.setLibrary(true);
	    	}
			List<MavenDependency> dependencies = andworxProject.getLibraryDependencies();
			if (dependencies.isEmpty()) {
				androidWizardListener.onProfileResolved(projectProfile, true, "No dependencies configured");
			} else {
				// Utility used to obtain location of expanded aar repository
				mavenServices.configureLibraries(
						andworxProject, 
						androidEnvironment.getRepositoryLocation());
				for (Dependency dependency: dependencies) {
				    projectProfile.addDependency(dependency);
				}
				int unresolvedCount = 0;
				for (Artifact artifact: mavenProject.getArtifacts())
					if (!artifact.isResolved())
						++unresolvedCount;
				if (unresolvedCount == 0) 
					androidWizardListener.onProfileResolved(projectProfile, true, Integer.toString(dependencies.size()) + " dependencies resolved");
				else {
					String plural = unresolvedCount == 1 ? "y" : "ies";
					androidWizardListener.onProfileResolved(projectProfile, false, Integer.toString(unresolvedCount) + " dependenc" + plural + " unresolved");
				}
			}
        } catch (Exception e) {
        	String message = "Error " + FUNCTION_NAME + ": " + e.getMessage();
        	androidWizardListener.onError(message);
        	AndworxFactory.getAndworxContext().getBuildConsole().logAndPrintError(e, FUNCTION_NAME, message);
        	return Status.CANCEL_STATUS;
        } finally {
        	tempDir.delete();
        }
		return Status.OK_STATUS;
	}

}
