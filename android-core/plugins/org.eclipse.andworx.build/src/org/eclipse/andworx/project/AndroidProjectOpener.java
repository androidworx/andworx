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

import static org.eclipse.andworx.project.AndroidConfiguration.VOID_PROJECT_ID;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

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
import org.eclipse.andworx.model.CodeSource;
import org.eclipse.andworx.polyglot.AndroidConfigurationBuilder;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.IJobChangeListener;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.jface.operation.IRunnableWithProgress;

import com.android.SdkConstants;
import com.android.ide.common.xml.AndroidManifestParser;
import com.android.ide.common.xml.ManifestData;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.google.common.base.Throwables;
import com.google.common.io.Files;

/**
 * Parses manifest and build.gradle files of an Android project to produce a project profile 
 * containing dependencies that have been resolved.
 * Works in 3 stages to progressively update the caller in case the process takes a while to complete.
 * Stage 1 = parse build.gradle
 * Stage 2 = extract package from AndroidManifest.xml
 * Stage 3 = resolve dependencies
 * Also provides a task to persist the imported project configuration.
 */
public class AndroidProjectOpener {
    /** Job to compile build.gradle and contain the results in a AndroidConfigurationBuilder object */
	private abstract static class CompileJob extends Job {
		AndroidConfigurationBuilder androidConfigurationBuilder;
		public CompileJob(String name) {
			super(name);
		}
		public AndroidConfigurationBuilder getAndroidConfigurationBuilder() {
			return androidConfigurationBuilder;
		}
	}

	/**
	 * Runnable to allow a UI component to execute a long-running operation.
	 * Creates an Andworx project and provides the database ID of the new project.
	 */
	private class RunnableProjectCreator implements IRunnableWithProgress {
		
		String projectName;
		ProjectProfile projectProfile;
		AndroidConfigurationBuilder androidConfigurationBuilder;
		// Project database ID
		int projectId;

		public int getProjectId() {
			return projectId;
		}

		public RunnableProjectCreator(
				String projectName, 
				ProjectProfile projectProfile, 
				AndroidConfigurationBuilder androidConfigurationBuilder) {
			this.projectName = projectName;
			this.projectProfile = projectProfile;
			this.androidConfigurationBuilder = androidConfigurationBuilder;
			projectId = VOID_PROJECT_ID;
		}
		
		@Override
		public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
	        try { 
	        	ProjectProfile profile = objectFactory.createProject(projectName, projectProfile, androidConfigurationBuilder);
				projectId = profile.getProjectId();
	        } catch (Exception e) {
	        	String message = "Error creating project " + projectName + ": " + Throwables.getStackTraceAsString(e);
	        	androidWizardListener.onError(message);
	        	AndworxBuildPlugin.instance().logAndPrintError(e, projectName, message);
	        }
		}
	}

	/** Interface to project import wizard page */
	private final AndroidWizardListener androidWizardListener;
	/** Object factory */
	private final AndworxFactory objectFactory;

	/**
	 * Construct AndroidProjectOpener object
	 * @param androidWizardListener Interface to project import wizard page
	 */
	public AndroidProjectOpener(AndroidWizardListener androidWizardListener) {
		this.androidWizardListener = androidWizardListener;
		objectFactory = AndworxFactory.instance();
	}

	/**
	 * Run tasks to parse import project manifest and build files
	 * @param andworxBuildFile build.andworx
	 */
	public void runOpenTasks(File andworxBuildFile) {
		File[] manifestFile = new File[] {null};
		CompileJob buildJob = new CompileJob("parsing build.gradle") {
		@Override
		protected IStatus run(IProgressMonitor monitor) {
	        try { 
				androidConfigurationBuilder = objectFactory.getAndroidConfigBuilder(andworxBuildFile);
				manifestFile[0] = new File(andworxBuildFile.getParentFile(), androidConfigurationBuilder.getSourceFolder(CodeSource.manifest));
				if (!manifestFile[0].exists()) {
                	String message = "Project file \"" + manifestFile[0].toString() + "\" not found";
    	        	androidWizardListener.onError(message);
    	        	AndworxBuildPlugin.instance().logAndPrintError(null, getName(), message);
    	        	return Status.CANCEL_STATUS;
				}
	        } catch (Exception e) {
	        	String message = "Error " + getName() + ": " + Throwables.getStackTraceAsString(e);
	        	androidWizardListener.onError(message);
	        	AndworxBuildPlugin.instance().logAndPrintError(e, getName(), message);
	        	return Status.CANCEL_STATUS;
	        }
			return Status.OK_STATUS;
		}};
			
		Job manifestJob = new Job("parsing Android manifest") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
		        try { 
	            	Path path = Paths.get(manifestFile[0].toURI());
	                ManifestData manifestData = AndroidManifestParser.parse(path);
	                androidWizardListener.onManifestParsed(manifestData);
		        } catch (Exception e) {
		        	String message = "Error " + getName() + ": " + Throwables.getStackTraceAsString(e);
		        	androidWizardListener.onError(message);
		        	AndworxBuildPlugin.instance().logAndPrintError(e, getName(), message);
		        	return Status.CANCEL_STATUS;
		        }
				return Status.OK_STATUS;
			}};
			
		Job profileJob = new Job("resolving profile") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
	        	File tempDir = Files.createTempDir();
	        	// Service interface for m2e interactions 
	        	MavenServices mavenServices = objectFactory.getMavenServices();
		        try { 
					// Allow "android-" prefix for platform target 
		        	AndroidConfigurationBuilder androidConfigurationBuilder = buildJob.getAndroidConfigurationBuilder();
					AndroidConfig androidConfig = androidConfigurationBuilder.getAndroidConfig();
				    final AndroidVersion hash = AndroidTargetHash.getVersionFromHash(androidConfig.getCompileSdkVersion());
				    String targetHashString = null;
				    if (hash != null) {
				    	targetHashString = AndroidTargetHash.getPlatformHashString(hash);
				    } // TODO - Report bad value
					File pomXml = new File(tempDir, mavenServices.getPomFilename());
			        mavenServices.createMavenModel(pomXml, androidConfigurationBuilder.getMavenModel());
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
						AndroidEnvironment androidEnvironment = objectFactory.getAndroidEnvironment();
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
		        	String message = "Error " + getName() + ": " + e.getMessage();
		        	androidWizardListener.onError(message);
		        	AndworxBuildPlugin.instance().logAndPrintError(e, getName(), message);
		        	return Status.CANCEL_STATUS;
		        } finally {
		        	tempDir.delete();
		        }
				return Status.OK_STATUS;
			}};
			
		final AndroidProjectOpener self = this;
		// Add job liseners
        final IJobChangeListener buildListener = new JobChangeAdapter(){
			@Override
			public void done(IJobChangeEvent event) {
				synchronized (self) {
					if (event.getResult() == Status.OK_STATUS)
						manifestJob.schedule();
				}
			}};
			buildJob.addJobChangeListener(buildListener);
        final IJobChangeListener manifestListener = new JobChangeAdapter(){
			@Override
			public void done(IJobChangeEvent event) {
				synchronized (self) {
					if (event.getResult() == Status.OK_STATUS)
						androidWizardListener.onConfigParsed(buildJob.getAndroidConfigurationBuilder());
						profileJob.schedule();
				}
			}};
		manifestJob.addJobChangeListener(manifestListener);
		buildJob.schedule();
	}

	/**
	 * Returns copy of resolved project profile with identity created using page field values
	 * @param fieldMap Map of field values
	 * @param resolvedProjectProfile Profile generated by 3rd open project task
	 * @return ProjectProfile object
	 */
	@SuppressWarnings("unused")
	public ProjectProfile getProjectProfile(
			Map<ProjectField,String> fieldMap, 
			ProjectProfile resolvedProjectProfile) {
		Identity identity = new Identity(
				fieldMap.get(ProjectField.groupId),
				fieldMap.get(ProjectField.artifactId),
				resolvedProjectProfile.getIdentity().getVersion());
		if (resolvedProjectProfile == null) // not expected
			return new ProjectProfile(identity);
		ProjectProfile projectProfile = resolvedProjectProfile.copy(identity);
		return projectProfile;
	}

	/**
	 * Creates an Andworx project and returns the database ID of the new project
	 * @param projectName  Project name
	 * @param projectProfile Project profile
	 * @param androidConfigurationBuilder Assembles configuration content extracted by a Groovy AST parser into JPA entity beans and then persists them
	 * @param runnableContext UI component runner
	 * @return int
	 * @throws InterruptedException 
	 * @throws InvocationTargetException 
	 */
	public int persistProjectConfigTask(
			String projectName,
			ProjectProfile projectProfile,
			AndroidConfigurationBuilder androidConfigurationBuilder,
			IRunnableContext runnableContext) throws InvocationTargetException, InterruptedException {
		RunnableProjectCreator projectCreator = new RunnableProjectCreator(projectName, projectProfile, androidConfigurationBuilder);
		runnableContext.run(true, false, projectCreator);
		return projectCreator.getProjectId();
	}

	/**
	 * Writes project id to disk
	 * @param projectName Project name
	 * @param projectId Project ID
	 * @param location Project absolute location
	 * @param runnableContext UI component runner
	 * @throws InterruptedException 
	 * @throws InvocationTargetException 
	 */
	public void writeProjectIdFileTask(
			String projectName,
			int projectId,
			File location,
			IRunnableContext runnableContext) throws InvocationTargetException, InterruptedException {
		IRunnableWithProgress runnable = new IRunnableWithProgress() {
			@Override
			public void run(IProgressMonitor monitor) {
		        try { 
		        	AndroidConfiguration androidConfig = objectFactory.getAndroidConfiguration();
		        	androidConfig.createProjectIdFile(projectId, location);
		        } catch (Exception e) {
		        	String message = "Error creating project " + projectName + ": " + Throwables.getStackTraceAsString(e);
		        	androidWizardListener.onError(message);
		        	AndworxBuildPlugin.instance().logAndPrintError(e, projectName, message);
		        }
			}};
		runnableContext.run(true, false, runnable);
	}

}
