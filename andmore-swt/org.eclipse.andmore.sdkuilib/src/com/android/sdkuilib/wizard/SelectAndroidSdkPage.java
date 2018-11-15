/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.sdkuilib.wizard;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.andmore.base.resources.PluginResourceProvider;
import org.eclipse.andmore.sdktool.preferences.AndroidSdk;
import org.eclipse.andmore.sdktool.install.SdkInstallListener;
import org.eclipse.andmore.sdktool.install.SdkInstaller;
import org.eclipse.andmore.sdktool.install.SdkProfile;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;

import com.android.sdkuilib.widgets.AndroidSdkComposite;
import com.android.sdkuilib.widgets.AndroidSdkListener;
import com.android.sdkuilib.widgets.AndroidSdkSelector;
import com.android.utils.ILogger;

/**
 * Wizard window to select an existing SDK location or install a new one. Every SDK installation
 * can have an optional name. One SDK is selected for use by Andmore. SDK installation details are
 * stored in the Android home location so they can be shared between workspaces.
 * @author Andrew Bowley
 *
 * 13-12-2017
 */
public class SelectAndroidSdkPage extends WizardPage implements AndroidSdkComposite {
	public static final String PAGE_NAME = "selectSDK";
	private static final String CHOOSE_SDK = "Choose one SDK ";
	private static final String CONFIGURE_SDK = "Configure SDK ";
	private static final String INSTALL_COMPLETED = "Android SDK installation completed";
//	private static final String INSTALL_IN_PROGRESS = "Install pending...";
	private static final String INSTALL_FAILED = "Android SDK installation failed";
	private static final String PARTIAL_INSTALL = "Android SDK installation incomplete. %d package%s required";

	/** SDK Installer takes a directory and interatively installs an SDK */
	private final SdkInstaller sdkInstaller;
	/** Collection of Android SDK details which are new or have changed */
	private final Set<AndroidSdk> dirtyAndroidSdks;
	/** Image resources */
	private final PluginResourceProvider resourceProvider;
	/** Logger */
	private final ILogger logger;
	/** Control composite */
	private Composite controlParent;
	/** Page control */
	private AndroidSdkSelector androidSdkSelector;

	/**
	 * Construct SelectAndroidSdkPage object
	 * @param resourceProvider Image resources
	 * @param logger Logger
	 */
	public SelectAndroidSdkPage(SdkInstaller sdkInstaller, PluginResourceProvider resourceProvider, ILogger logger) {
		super(PAGE_NAME);
		this.sdkInstaller = sdkInstaller;
		this.resourceProvider = resourceProvider;
		this.logger = logger;
		dirtyAndroidSdks = new HashSet<>();
		// Assume new installation until SDK list is available.  
        setDescription(CONFIGURE_SDK);
        // Disable Finish button initially
        setPageComplete(false);
	}

	/**
	 * Set SDK installations to be displayed. This is called from a background thread, so care is required.
	 * @param sdkList List of Android SDK specifications
	 * @param param selection Which SDK location to highlight
	 */
	public void setSdkList(List<AndroidSdk> sdkList, String selection) {
		Display.getDefault().syncExec(new Runnable(){

			@Override
			public void run() {
				if (sdkList.size() > 0)
					// An SDK is available for selection  
					setDescription(CHOOSE_SDK);
				// Only proceed if androidSdkSelector has been constructed in call to createControl()
				if (androidSdkSelector != null) {
					androidSdkSelector.setSdkList(sdkList, selection);
					// Only enable Finish button if there is at least one SDK available
				} 	
			}});
	}

	/**
	 * Handle request to install new SDK
	 * @param newAndroidSdk  Android SDK specification
	 * @param sdkInstallListener Callback to handle SDK installed
	 * @param usePacketFilter Flag set true if initial selections are for platforms only
	 */
	public void doInstall(AndroidSdk newAndroidSdk, SdkInstallListener sdkInstallListener, boolean usePacketFilter) {
		sdkInstaller.doInstall(newAndroidSdk, getShell(), getSdkInstallListener(sdkInstallListener), usePacketFilter);
	}

	/**
	 * Returns Android SDK specifications which are new or have changed
	 * @return AndroidSdk set
	 */
	public Set<AndroidSdk> getDirtyAndroidSdks() {
		return dirtyAndroidSdks;
	}

	public void addDirtyAndroidSdk(AndroidSdk androidSdk) {
		if (androidSdk != null)
			dirtyAndroidSdks.add(androidSdk);
	}
	
	/**
	 * Display status
	 * @param isOk Flag set true if image is green tick otherwise show red cross
	 * @param message Status message
	 * @param pageComplete Flag set true if Finish button can be enabled
	 */
	public void displayStatus(boolean isOk, String message, boolean pageComplete) {
		setPageComplete(pageComplete);
		androidSdkSelector.displayStatus(isOk, message);
	}

	/**
	 * Returns checked Android SDK specification
	 * @return AndroidSdk object
	 */
	public AndroidSdk getSelectedAndroidSdk() {
		return 	androidSdkSelector.getSelectedAndroidSdk(); 
	}
	
	/**
	 * createControl
	 * @see org.eclipse.jface.dialogs.IDialogPage#createControl(org.eclipse.swt.widgets.Composite)
	 */
	@Override
	public void createControl(Composite parent) {
    	Composite container = new Composite(parent, SWT.NULL);
		this.controlParent = container;
        // Delegate to AndroidSdkSelector control
        setControl(container);
        container.setLayout(new GridLayout(1, false));
		androidSdkSelector = new AndroidSdkSelector(this, new AndroidSdkListener(){

			@Override
			public void onAndroidSdkCommand(AndroidSdk androidSdk, SdkInstallListener sdkInstallListener, boolean usePacketFilter) {
				doInstall(androidSdk, sdkInstallListener, usePacketFilter);
			}

			@Override
			public void onAndroidSdkDirty(AndroidSdk androidSdk) {
				dirtyAndroidSdks.add(androidSdk);
			}});
        GridData gd = (GridData) androidSdkSelector.getLayoutData();
        if (gd.heightHint == -1)
            // The number of SDKs varies according to configuration so fix on an accommodating height
            gd.heightHint = 250;
	}

	@Override
	public Composite getParent() {
		return controlParent;
	}

	@Override
	public PluginResourceProvider getResourceProvider() {
		return resourceProvider;
	}

	@Override
	public void onSdkChange(AndroidSdk androidSdk) {
		dirtyAndroidSdks.add(androidSdk);
		Display.getDefault().syncExec(new Runnable(){

			@Override
			public void run() {
				setPageComplete(true);
			}});
	}

	@Override
	public ILogger getLogger() {
		return logger;
	}

	/**
	 * Returns callback for event SDK install complete
	 * @param sdkInstallListener Piggy-back callback
	 * @return SdkInstallListener object
	 */
	private SdkInstallListener getSdkInstallListener(SdkInstallListener sdkInstallListener) {
		return  new SdkInstallListener(){

			@Override
			public void onSdkInstall(AndroidSdk newAndroidSdk) {
				confirmInstallation(newAndroidSdk);
				if (sdkInstallListener != null)
					sdkInstallListener.onSdkInstall(newAndroidSdk);
			}

			@Override
			public void onSdkAssign(AndroidSdk androidSdk) {
				// Not used
			}

			@Override
			public void onCancel() {
				if (sdkInstallListener != null)
					sdkInstallListener.onCancel();
			}}; 
	}

	/**
	 * Check SDK location profile to determine success or otherwise
	 * @param sdkLocation SDK install location
	 */
	private void confirmInstallation(AndroidSdk androidSdk) {
		// Two conditions are necessary for success
		boolean success = (androidSdk != null) && (sdkInstaller.getRepoManager() != null);
		String message;
		if (success) {
			// Ensure all platform packages are loaded
			SdkProfile sdkProfile = new SdkProfile(logger);
			sdkProfile.evaluate(sdkInstaller.getRepoManager());
			if (sdkProfile.getRequiredPackageCount() > 0) {
				success = false;
				String plural = sdkProfile.getRequiredPackageCount() > 1 ? "s" : "";
				message = String.format(PARTIAL_INSTALL, sdkProfile.getRequiredPackageCount(), plural);
			} else {
				message = INSTALL_COMPLETED;
			}
		} else {
			message = INSTALL_FAILED;
		}
		boolean ok = success;
		Display.getDefault().syncExec(new Runnable(){

			@Override
			public void run() {
                androidSdkSelector.displayStatus(ok, message);
        		if (ok)
        			// Enable Finish button
                    setPageComplete(true);
			}});
	}

}
