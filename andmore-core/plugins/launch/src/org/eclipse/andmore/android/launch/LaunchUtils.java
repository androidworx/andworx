/*
 * Copyright (C) 2012 The Android Open Source Project
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

package org.eclipse.andmore.android.launch;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.eclipse.andmore.android.AndroidPlugin;
import org.eclipse.andmore.android.ISerialNumbered;
import org.eclipse.andmore.android.SdkUtils;
import org.eclipse.andmore.android.common.log.AndmoreLogger;
import org.eclipse.andworx.ddms.devices.Devices;
import org.eclipse.andworx.ddms.devices.DeviceProfile;
import org.eclipse.andmore.internal.project.AndroidManifestHelper;
import org.eclipse.andworx.build.AndworxFactory;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

import com.android.ide.common.xml.ManifestData;
import com.android.ide.common.xml.ManifestData.Activity;

/**
 * DESCRIPTION: Utilities for Studio for Android Launch use
 * 
 * RESPONSIBILITY: Provide common utility methods that can be used by any Studio
 * for Android Launch plugin.
 * 
 * COLABORATORS: None
 * 
 * USAGE: This class should not be instantiated and its methods should be called
 * statically.
 */
public class LaunchUtils {
	/**
	 * Get a project in the current workspace based on its projectName
	 * 
	 * @param projectName
	 * @return the IProject representing the project, or null if none is found
	 */
	public static IProject getProject(String projectName) {
		IProject project = null;

		Path projectPath = new Path(projectName);
		if (projectPath.isValidSegment(projectName)) {
			project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
		}

		return project;
	}

	/**
	 * Verify if a given project is supported by the Studio for Android
	 * Launcher, checking if the project is a Android project
	 * 
	 * @param project
	 *            to be verified
	 * @return true if project is a an Android project, false otherwise.
	 */
	public static boolean isProjectSupported(IProject project) {
		boolean hasNature = false;
		boolean isLibrary = true;

		if ((project != null) && project.isOpen()) {
			SdkUtils sdkUtils = AndworxFactory.instance().get(SdkUtils.class);
			try {
				hasNature = project.hasNature(AndroidPlugin.Android_Nature);
				isLibrary = sdkUtils.isLibraryProject(project);
			} catch (CoreException e) {
				// Do nothing
			}
		}

		return hasNature && !isLibrary;
	}

	/**
	 * Get all Android Projects within the current workspace.
	 * 
	 * @return IProject array with all Android projects in the current
	 *         workspace, or an empty array if none is found
	 */
	public static IProject[] getSupportedProjects() {
		Collection<IProject> projectCollection = new ArrayList<IProject>();
		IProject[] projectsName = ResourcesPlugin.getWorkspace().getRoot().getProjects();

		/* select only Android projects */
		for (IProject project : projectsName) {
			if (project.isAccessible()) {
				if (LaunchUtils.isProjectSupported(project)) {
					projectCollection.add(project);
				}
			}
		}

		return projectCollection.toArray(new IProject[projectCollection.size()]);
	}

	/**
	 * Retrieve the project activities from the MANIFEST.xml file
	 * 
	 * @param project
	 * @return An array of activities.
	 */
	public static String[] getProjectActivities(IProject project) {

		String[] activities = null;
		Activity[] adtActivities = null;

		// parse the manifest for the list of activities.
		try {
			ManifestData manifestData = AndroidManifestHelper.parseForData(project);

			if (manifestData != null) {
				adtActivities = manifestData.getActivities();
			}

			if ((adtActivities != null) && (adtActivities.length > 0)) {
				activities = new String[adtActivities.length];
				for (int i = 0; i < adtActivities.length; i++) {
					activities[i] = adtActivities[i].getName();
				}
			}

		} catch (Exception e) {
			AndmoreLogger.error(LaunchUtils.class, "An error occurred trying to parse AndroidManifest", e);
		}

		return activities;

	}

	/**
	 * Set the default launch configuration values
	 */
	public static void setADTLaunchConfigurationDefaults(ILaunchConfigurationWorkingCopy configuration) {
		configuration.setAttribute(ILaunchConfigurationConstants.ATTR_ALLOW_TERMINATE,
				ILaunchConfigurationConstants.ATTR_ALLOW_TERMINATE_DEFAULT);
		configuration.setAttribute(ILaunchConfigurationConstants.ATTR_LAUNCH_ACTION,
				ILaunchConfigurationConstants.ATTR_LAUNCH_ACTION_DEFAULT);
		configuration.setAttribute(ILaunchConfigurationConstants.ATTR_TARGET_MODE,
				ILaunchConfigurationConstants.ATTR_TARGET_MODE_DEFAULT.toString());
		configuration.setAttribute(ILaunchConfigurationConstants.ATTR_SPEED,
				ILaunchConfigurationConstants.ATTR_SPEED_DEFAULT);
		configuration.setAttribute(ILaunchConfigurationConstants.ATTR_DELAY,
				ILaunchConfigurationConstants.ATTR_DELAY_DEFAULT);
		configuration.setAttribute(ILaunchConfigurationConstants.ATTR_WIPE_DATA,
				ILaunchConfigurationConstants.ATTR_WIPE_DATA_DEFAULT);
		configuration.setAttribute(ILaunchConfigurationConstants.ATTR_NO_BOOT_ANIM,
				ILaunchConfigurationConstants.ATTR_NO_BOOT_ANIM_DEFAULT);
		configuration.setAttribute(ILaunchConfigurationConstants.ATTR_COMMANDLINE,
				ILaunchConfigurationConstants.DEFAULT_VALUE);

	}

	/**
	 * Update the launch configuration values
	 */
	public static void updateLaunchConfigurationDefaults(ILaunchConfigurationWorkingCopy configuration) {
		/*
		try {
			String deviceName = configuration.getAttribute(ILaunchConfigurationConstants.ATTR_DEVICE_INSTANCE_NAME, "");

			if ((deviceName != null) && !deviceName.equals("")) {
				DeviceProfile deviceInstance = DeviceManager.getInstance().getInstanceByName(
						deviceName);

				if (deviceInstance instanceof IAndroidLogicInstance) {
					String commandLine = ((IAndroidLogicInstance) deviceInstance).getCommandLineArguments();
					configuration.setAttribute(ILaunchConfigurationConstants.ATTR_COMMANDLINE, commandLine);
				}
			}
		} catch (CoreException e) {
			AndmoreLogger.error(LaunchUtils.class,
					"Error updating launch configuration values for : " + configuration.getName(), e);
		}
		*/
	}

	/**
	 * Get the shell of the active workbench or null if there is no active
	 * workbench.
	 * 
	 * @return the active workbench shell
	 */
	public static Shell getActiveWorkbenchShell() {
		class ActiveShellRunnable implements Runnable {
			private Shell shell = null;

			public Shell getShell() {
				return shell;
			}

			@Override
			public void run() {
				IWorkbenchWindow activeWorkbench = PlatformUI.getWorkbench().getActiveWorkbenchWindow();

				if (activeWorkbench != null) {
					shell = activeWorkbench.getShell();
				}
			}
		}
		;

		ActiveShellRunnable runnable = new ActiveShellRunnable();
		PlatformUI.getWorkbench().getDisplay().syncExec(runnable);

		return runnable.getShell();
	}

	/**
	 * Show the error message using the given title and message
	 * 
	 * @param title
	 *            of the error dialog
	 * @param message
	 *            to be displayed in the error dialog.
	 */
	public static void showErrorDialog(final String title, final String message) {
		PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {
			@Override
			public void run() {
				IWorkbenchWindow ww = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
				MessageDialog.openError(ww.getShell(), title, message);
			}
		});
	}

	/**
	 * Check if an instanceName is compatible with some project
	 * 
	 * @param project
	 * @param instanceName
	 * @return {@link IStatus#OK} if fully compatible, {@link IStatus#WARNING}
	 *         if can be compatible and {@link IStatus#ERROR} if not compatible.
	 *         Return <code>null</code> if the instance does not exists
	 */

	public static IStatus isCompatible(IProject project, String instanceName) {
		IStatus status = null;
		Devices deviceManager = 
				AndworxFactory.instance().getDevices();

		Collection<DeviceProfile> instances = deviceManager.getAllDevices();
		for (DeviceProfile instance : instances) {
			if (instanceName.equals(instance.getName())) {
				status = deviceManager.isCompatible(project, instance);
				break;
			}
		}
		// TODO -fix broken code
		if (status == null)
			return Status.OK_STATUS;
		return status;
	}


}