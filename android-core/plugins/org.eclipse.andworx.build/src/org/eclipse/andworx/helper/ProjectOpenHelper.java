package org.eclipse.andworx.helper;

import static org.eclipse.andworx.project.AndroidConfiguration.VOID_PROJECT_ID;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

import org.eclipse.andworx.build.AndworxBuildPlugin;
import org.eclipse.andworx.build.AndworxContext;
import org.eclipse.andworx.build.AndworxFactory;
import org.eclipse.andworx.project.AndroidConfiguration;
import org.eclipse.andworx.project.AndroidDigest;
import org.eclipse.andworx.project.Identity;
import org.eclipse.andworx.project.ProjectField;
import org.eclipse.andworx.project.ProjectProfile;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.jface.operation.IRunnableWithProgress;

import com.google.common.base.Throwables;

public class ProjectOpenHelper {

	/**
	 * Runnable to allow a UI component to execute a long-running operation.
	 * Creates an Andworx project and provides the database ID of the new project.
	 */
	private class RunnableProjectCreator implements IRunnableWithProgress {
		
		private final String projectName;
		private final ProjectProfile projectProfile;
		private final AndroidDigest androidDigest;
		private final AndworxContext objectFactory;
		// Project database ID
		int projectId;

		public RunnableProjectCreator(
				String projectName, 
				ProjectProfile projectProfile, 
				AndroidDigest androidDigest) {
			this.projectName = projectName;
			this.projectProfile = projectProfile;
			this.androidDigest = androidDigest;
			projectId = VOID_PROJECT_ID;
        	objectFactory = AndworxFactory.getAndworxContext();
		}
		
		public int getProjectId() {
			return projectId;
		}

		@Override
		public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
	        try { 
	        	ProjectProfile profile = objectFactory.createProject(projectName, projectProfile, androidDigest);
				projectId = profile.getProjectId();
	        } catch (Exception e) {
	        	String message = "Error creating project " + projectName + ": " + Throwables.getStackTraceAsString(Throwables.getRootCause(e));
	        	errorListener.onError(message);
	        	AndworxBuildPlugin.instance().logAndPrintError(e, projectName, message);
	        }
		}
	}

	private final ErrorListener errorListener;

	public ProjectOpenHelper(ErrorListener errorListener) {
		this.errorListener = errorListener;
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
			AndroidDigest androidCDigest,
			IRunnableContext runnableContext) throws InvocationTargetException, InterruptedException {
		RunnableProjectCreator projectCreator = new RunnableProjectCreator(projectName, projectProfile, androidCDigest);
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
    	AndworxContext objectFactory = AndworxFactory.getAndworxContext();
		IRunnableWithProgress runnable = new IRunnableWithProgress() {
			@Override
			public void run(IProgressMonitor monitor) {
		        try { 
		        	AndroidConfiguration androidConfig = objectFactory.getAndroidConfiguration();
		        	androidConfig.createProjectIdFile(projectId, location);
		        } catch (Exception e) {
		        	String message = "Error creating project " + projectName + ": " + Throwables.getStackTraceAsString(Throwables.getRootCause(e));
		        	errorListener.onError(message);
		        	AndworxBuildPlugin.instance().logAndPrintError(e, projectName, message);
		        }
			}};
		runnableContext.run(true, false, runnable);
	}
}
