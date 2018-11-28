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
import java.util.List;

import org.eclipse.andmore.android.SdkUtils;
import org.eclipse.andmore.android.common.log.AndmoreLogger;
import org.eclipse.andmore.android.common.preferences.DialogWithToggleUtils;
import org.eclipse.andmore.android.launch.i18n.LaunchNLS;
import org.eclipse.andmore.internal.launch.AndroidLaunch;
import org.eclipse.andmore.internal.launch.LaunchConfigDelegate;
import org.eclipse.andmore.internal.project.AndroidManifestHelper;
import org.eclipse.andworx.AndworxConstants;
import org.eclipse.andworx.build.AndworxContext;
import org.eclipse.andworx.build.AndworxFactory;
import org.eclipse.andworx.ddms.devices.DeviceProfile;
import org.eclipse.andworx.ddms.devices.Devices;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.IDebugUIConstants;
import org.eclipse.debug.ui.ILaunchGroup;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;

import com.android.ddmlib.Client;
import com.android.ide.common.xml.ManifestData;
import com.android.ide.common.xml.ManifestData.Activity;

/**
 * DESCRIPTION: This class is responsible to execute the launch process <br>
 * RESPONSIBILITY: Perform application launch on a device. <br>
 * COLABORATORS: none <br>
 */
@SuppressWarnings("restriction")
public class StudioAndroidConfigurationDelegate extends LaunchConfigDelegate {

	private static final String ERRONEOUS_LAUNCH_CONFIGURATION = "erroneous.launch.config.dialog";

	private static final String NO_COMPATIBLE_DEVICE = "no.compatible.device.dialog";

	private Devices deviceManager;
	
	DeviceProfile compatibleInstance = null;

	DeviceProfile initialEmulatorInstance = null;

	public List<Client> waitingDebugger = new ArrayList<Client>();

	public StudioAndroidConfigurationDelegate() {
		AndworxContext objectFactory = AndworxFactory.instance();
        deviceManager = objectFactory.getDevices();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.debug.core.model.LaunchConfigurationDelegate#preLaunchCheck
	 * (org.eclipse.debug.core.ILaunchConfiguration, java.lang.String,
	 * org.eclipse.core.runtime.IProgressMonitor)
	 */
	@Override
	public boolean preLaunchCheck(ILaunchConfiguration configuration, String mode, IProgressMonitor monitor)
			throws CoreException {
		initialEmulatorInstance = null;
		boolean isOk = super.preLaunchCheck(configuration, mode, monitor);

		if (isOk) {
			try {
				final String instanceName = configuration.getAttribute(
						ILaunchConfigurationConstants.ATTR_DEVICE_INSTANCE_NAME, (String) null);
	
				// we found an instance
				if ((instanceName != null) && (instanceName.length() > 0)) {
					DeviceProfile instance = 
						deviceManager
							.getInstanceByName(instanceName);
					isOk = (instance != null);
					if (isOk && !instance.isStarted()) {
						initialEmulatorInstance = instance;
						// updates the compatible instance with user response
					    //	isOk = checkForCompatibleRunningInstances(configuration);
						}
					}
			} catch (Exception e) {
				AndmoreLogger.error(StudioAndroidConfigurationDelegate.class.getName(), "Error while checking if device available", e);
				isOk = false;
				handleErrorDuringLaunch(configuration, mode, null);
			}
		}
		// validate if the project isn't a library project
		if (isOk) {
			String projectName = configuration.getAttribute(ILaunchConfigurationConstants.ATTR_PROJECT_NAME,
					(String) null);
			if (projectName != null) {
				SdkUtils sdkUtils = AndworxFactory.instance().get(SdkUtils.class);
				IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
				if ((project != null) && sdkUtils.isLibraryProject(project)) {
					handleProjectError(configuration, project, mode);
					isOk = false;
				}
			}
		}
		return isOk;
	}

	private void handleProjectError(final ILaunchConfiguration config, final IProject project, final String mode) {
		PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {

			@Override
			public void run() {
				Shell shell = LaunchUtils.getActiveWorkbenchShell();

				String message = LaunchNLS.UI_LaunchConfigurationTab_ERR_PROJECT_IS_LIBRARY;

				String prefKey = ERRONEOUS_LAUNCH_CONFIGURATION;

				DialogWithToggleUtils.showInformation(prefKey, LaunchNLS.ERR_LaunchConfigurationShortcut_MsgTitle,
						message);

				StructuredSelection struturedSelection;

				String groupId = IDebugUIConstants.ID_RUN_LAUNCH_GROUP;

				ILaunchGroup group = DebugUITools.getLaunchGroup(config, mode);
				groupId = group.getIdentifier();
				struturedSelection = new StructuredSelection(config);

				DebugUITools.openLaunchConfigurationDialogOnGroup(shell, struturedSelection, groupId);
			}
		});
	}

	private void handleErrorDuringLaunch(final ILaunchConfiguration config, final String mode, final String instanceName) {
		PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {

			@Override
			public void run() {
				Shell shell = LaunchUtils.getActiveWorkbenchShell();

				String message = instanceName != null ? NLS.bind(LaunchNLS.ERR_LaunchDelegate_InvalidDeviceInstance,
						instanceName) : NLS.bind(LaunchNLS.ERR_LaunchDelegate_No_Compatible_Device, config.getName());

				String prefKey = instanceName != null ? ERRONEOUS_LAUNCH_CONFIGURATION : NO_COMPATIBLE_DEVICE;

				DialogWithToggleUtils.showInformation(prefKey, LaunchNLS.ERR_LaunchConfigurationShortcut_MsgTitle,
						message);

				StructuredSelection struturedSelection;

				String groupId = IDebugUIConstants.ID_RUN_LAUNCH_GROUP;

				ILaunchGroup group = DebugUITools.getLaunchGroup(config, mode);
				groupId = group.getIdentifier();
				struturedSelection = new StructuredSelection(config);

				DebugUITools.openLaunchConfigurationDialogOnGroup(shell, struturedSelection, groupId);
			}
		});
	}

	/**
	 * Launches an Android application based on the given launch configuration.
	 */
	@Override
	public void launch(ILaunchConfiguration configuration, String mode, ILaunch launch, IProgressMonitor monitor)
			throws CoreException

	{
		// use a working copy because it can be changed and these changes should
		// not be propagated to the original copy
		ILaunchConfigurationWorkingCopy configurationWorkingCopy = configuration.getWorkingCopy();
		AndmoreLogger.info(StudioAndroidConfigurationDelegate.class,
				"Launch Android Application using Studio for Android wizard. Configuration: "
						+ configurationWorkingCopy + " mode:" + mode + " launch: " + launch);
		try {

			String projectName = configurationWorkingCopy.getAttribute(ILaunchConfigurationConstants.ATTR_PROJECT_NAME,
					(String) null);
			int launchAction = configurationWorkingCopy.getAttribute(ILaunchConfigurationConstants.ATTR_LAUNCH_ACTION,
					ILaunchConfigurationConstants.ATTR_LAUNCH_ACTION_DEFAULT);

			String instanceName = configurationWorkingCopy.getAttribute(
					ILaunchConfigurationConstants.ATTR_DEVICE_INSTANCE_NAME, (String) null);

			if ((projectName != null) && (instanceName != null)) {
				AndmoreLogger.info(StudioAndroidConfigurationDelegate.class,
						"Aapplication will be executed on: " + instanceName);
				IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
				if (project == null) {
					IStatus status = new Status(IStatus.ERROR, LaunchPlugin.PLUGIN_ID, "Could not retrieve project: "
							+ projectName);
					throw new CoreException(status);
				}

				String appToLaunch = null;
				if (launchAction == ILaunchConfigurationConstants.ATTR_LAUNCH_ACTION_DEFAULT) {
					ManifestData manifestData = AndroidManifestHelper.parseForData(project);
					Activity launcherActivity = manifestData.getLauncherActivity();
					String activityName = null;
					if (launcherActivity != null) {
						activityName = launcherActivity.getName();
					}

					// if there's no default activity. Then there's nothing to
					// be launched.
					if (activityName != null) {
						appToLaunch = activityName;
					}
				}
				// case for a specific activity
				else if (launchAction == ILaunchConfigurationConstants.ATTR_LAUNCH_ACTION_ACTIVITY) {
					appToLaunch = configurationWorkingCopy.getAttribute(ILaunchConfigurationConstants.ATTR_ACTIVITY,
							(String) null);

					if ((appToLaunch == null) || "".equals(appToLaunch)) {
						IStatus status = new Status(IStatus.ERROR, LaunchPlugin.PLUGIN_ID,
								"Activity field cannot be empty. Specify an activity or use the default activity on launch configuration.");
						throw new CoreException(status);
					}
				}
                if (appToLaunch != null)
                	configurationWorkingCopy.setAttribute(AndworxConstants.ATTR_LAUNCH_APPLICATION, appToLaunch);
				try {
					bringConsoleView();
					super.launch(configuration, mode, launch, monitor);
				} catch (CoreException e) {
					AndroidLaunch androidLaunch = (AndroidLaunch) launch;
					androidLaunch.stopLaunch();
					AndmoreLogger.error(StudioAndroidConfigurationDelegate.class, "Error while lauching "
							+ configurationWorkingCopy.getName(), e);
				} 
			} else {
				throw new CoreException(new Status(IStatus.ERROR, LaunchPlugin.PLUGIN_ID,
						"Missing parameters for launch"));
			}
		} catch (CoreException e) {
			AndroidLaunch androidLaunch = (AndroidLaunch) launch;
			androidLaunch.stopLaunch();
			AndmoreLogger.error(StudioAndroidConfigurationDelegate.class, "Error while lauching "
					+ configurationWorkingCopy.getName(), e);
			throw e;
		} catch (Exception e) {
			AndmoreLogger.error(LaunchUtils.class, "An error occurred trying to parse AndroidManifest", e);
		} 
	}

	/**
	 * @param configuration
	 * @throws CoreException
	 */
/*
	private boolean checkForCompatibleRunningInstances(ILaunchConfiguration configuration) throws CoreException {
		IProject project = null;
		compatibleInstance = null;

		final String projectName = configuration.getAttribute(ILaunchConfigurationConstants.ATTR_PROJECT_NAME,
				(String) null);

		project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
		if (project == null) {
			IStatus status = new Status(IStatus.ERROR, LaunchPlugin.PLUGIN_ID, "Could not retrieve project: "
					+ projectName);
			throw new CoreException(status);
		}

		// Check if there is a compatible instance running to launch the app
		Collection<DeviceProfile> startedInstances = DeviceManager.getInstance()
				.getAllStartedInstances();

		final Collection<DeviceProfile> compatibleStartedInstances = new HashSet<DeviceProfile>();

		boolean continueLaunch = true;

		for (DeviceProfile i : startedInstances) {
			IStatus resultStatus = LaunchUtils.isCompatible(project, i.getName());
			if ((resultStatus.getSeverity() == IStatus.OK) || (resultStatus.getSeverity() == IStatus.WARNING)) {
				compatibleStartedInstances.add(i);
			}
		}
		if (compatibleStartedInstances.size() > 0) {
			// show a dialog with compatible running instances so the user can
			// choose one to run the app, or he can choose to run the preferred
			// AVD

			StartedInstancesDialogProxy proxy = new StartedInstancesDialogProxy(compatibleStartedInstances,
					configuration, project);
			PlatformUI.getWorkbench().getDisplay().syncExec(proxy);

			compatibleInstance = proxy.getSelectedInstance();
			continueLaunch = proxy.continueLaunch();
		}
		return continueLaunch;
	}
*/
/*
	private class StartedInstancesDialogProxy implements Runnable {
		private DeviceProfile selectedInstance = null;

		private boolean continueLaunch = true;

		private final ILaunchConfiguration configuration;

		Collection<DeviceProfile> compatibleStartedInstances = null;

		IProject project = null;

		public StartedInstancesDialogProxy(Collection<DeviceProfile> compatibleStartedInstances,
				ILaunchConfiguration configuration, IProject project) {
			this.compatibleStartedInstances = compatibleStartedInstances;
			this.configuration = configuration;
			this.project = project;
		}

		@Override
		public void run() {
			Shell aShell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
			Shell shell = new Shell(aShell);
			StartedInstancesDialog dialog;
			try {
				dialog = new StartedInstancesDialog(shell, compatibleStartedInstances, configuration, project);
				dialog.setBlockOnOpen(true);
				dialog.open();

				selectedInstance = null;
				if (dialog.getReturnCode() == IDialogConstants.OK_ID) {
					selectedInstance = dialog.getSelectedInstance();
				} else if (dialog.getReturnCode() == IDialogConstants.ABORT_ID) {
					continueLaunch = false;
				}
			} catch (CoreException e) {
				AndmoreLogger.error(StudioAndroidConfigurationDelegate.class,
						"It was not possible to open Started Instance Dialog", e);
			}
		}

		public DeviceProfile getSelectedInstance() {
			return selectedInstance;
		}

		public boolean continueLaunch() {
			return continueLaunch;
		}
	}
*/
	/**
	 * Bring Console View to the front and activate the appropriate stream
	 * 
	 */
	private void bringConsoleView() {
		IConsole activeConsole = null;

		IConsole[] consoles = ConsolePlugin.getDefault().getConsoleManager().getConsoles();
		for (IConsole console : consoles) {
			if (console.getName().equals(ILaunchConfigurationConstants.ANDROID_CONSOLE_ID)) {
				activeConsole = console;
			}
		}

		// Bring Console View to the front
		if (activeConsole != null) {
			ConsolePlugin.getDefault().getConsoleManager().showConsoleView(activeConsole);
		}

	}
}
