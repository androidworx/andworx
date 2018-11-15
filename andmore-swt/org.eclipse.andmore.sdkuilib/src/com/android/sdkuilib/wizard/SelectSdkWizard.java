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

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.eclipse.andmore.base.SdkSelectionListener;
import org.eclipse.andmore.base.resources.PluginResourceProvider;
import org.eclipse.andmore.sdktool.preferences.AndroidPreferenceStore;
import org.eclipse.andmore.sdktool.preferences.AndroidSdk;
import org.eclipse.andmore.sdktool.install.SdkInstaller;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;

/**
 * Wizard window to select SDK location to use and install or repair SDK if required
 * @author Andrew Bowley
 *
 * 13-12-2017
 */
public class SelectSdkWizard extends Wizard {
	public static final String WINDOW_TITLE = "Select Android SDK installation";
	private static final String SAVING_CONFIGURATION = "Saving configuration...";

	/** Wizard page to present SDK installations and select one to use */
	private SelectAndroidSdkPage selectAndroidSdkPage;
	/** Image resources */
	private final PluginResourceProvider resourceProvider;
	/** SDK Installer interatively installs an SDK in a specified location */
	private final SdkInstaller sdkInstaller;
	/** Which SDK location to highlight */
	private final String selection;
	/** Job to run collection of SDKs to display in backgroud */
	private Job collectAndroidSdkJob;
	/** Preference store for Android SDK specifications */
	private AndroidPreferenceStore androidPreferenceStore;
	/** Callback for user selection of SDK changed */
	private SdkSelectionListener sdkSelectionListener;
	/** Image for window icon */
	private Image shellImage;
	
	/**
	 * Create SelectSdkWizard object
	 * @param resourceProvider Image resources
	 * @param sdkInstaller Interatively installs an SDK in a specified location
	 * @param param selection Which SDK location to highlight
	 */
	public SelectSdkWizard(PluginResourceProvider resourceProvider, SdkInstaller sdkInstaller, String selection) {
		super();
		this.resourceProvider = resourceProvider;
		this.sdkInstaller = sdkInstaller;
		this.selection = selection;
        setWindowTitle(WINDOW_TITLE);
        ImageDescriptor image = resourceProvider.descriptorFromPath("android-64.png"); //$NON-NLS-1$
        setDefaultPageImageDescriptor(image);
        // No next and previous buttons. The second page is displayed on press of a command button on the Select Sdk Page
        setForcePreviousAndNextButtons(false);
        // Load Android SDKs asychronously as preferences are stored on disk. 
        // Wait for page controls to be constructed before scheduling job.
        collectAndroidSdkJob = getCollectAndroidSdkJob();
        // Default selection to workspace location
        if ((selection == null) || selection.isEmpty() && sdkInstaller.hasWorkspaceSdk())
        	selection = sdkInstaller.getSdkLocation().toString();
	}

    /**
     * Set SDK selectio listener
	 * @param sdkSelectionListener The sdkSelectionListener to set
	 */
	public void setSdkSelectionListener(SdkSelectionListener sdkSelectionListener) {
		this.sdkSelectionListener = sdkSelectionListener;
	}

	/**
	 * createPageControls
	 */
	@Override
	public void createPageControls(Composite pageContainer) {
    	super.createPageControls(pageContainer);
    	ImageDescriptor adtIconDesc = resourceProvider.descriptorFromPath("adt.ico");
    	if (adtIconDesc != null) {
    		shellImage = adtIconDesc.createImage();
    		getShell().setImage(shellImage);
    	}
    	// Now schedule job created in constructor to populate SDK table
    	collectAndroidSdkJob.schedule();
    }

	/**
	 * dispose
	 */
    @Override 
    public void dispose() {
    	super.dispose();
    	if (shellImage != null)
    		shellImage.dispose();
    }
    
	/**
	 * Add pages. The seconed page is shown when the command button of the first is clicked.
	 */
    @Override
    public void addPages() {
    	selectAndroidSdkPage = new SelectAndroidSdkPage(sdkInstaller, resourceProvider, sdkInstaller.getConsoleLogger());
        addPage(selectAndroidSdkPage);
    }

	/**
	 * performFinish
	 * @see org.eclipse.jface.wizard.Wizard#performFinish()
	 */
	@Override
	public boolean performFinish() {
		// Update status displayed and run job to save configuration
		selectAndroidSdkPage.displayStatus(true, SAVING_CONFIGURATION, false);
		Job job = getSelectAndroidSdkJob();
		// Close when job complete
		job.addJobChangeListener(new JobChangeAdapter(){
            @Override
            public void done(IJobChangeEvent event) {
            	Display.getDefault().syncExec(new Runnable(){

					@Override
					public void run() {
		            	getShell().close();
					}});
            }
		});
		job.schedule();
		return false;
	}

    /**
     * Override super logic to prevent next and previous buttons in button bar
     */
    @Override
	public boolean needsPreviousAndNextButtons() {
        return false;
    }

    /**
     * Fetch Android SDK specifications and eliminate location duplication
     * @return Job object ready to schedule
     */
    private Job getCollectAndroidSdkJob() {
		Job job = new Job("Initiate Select SDK") {

			@Override
			protected IStatus run(IProgressMonitor arg0) {
				try {
					// Fetch SDK specifications from Android home
			    	androidPreferenceStore = new AndroidPreferenceStore(sdkInstaller.getConsoleLogger());
			    	List<AndroidSdk> androidSdks = androidPreferenceStore.getAndroidSdkList();
			    	List<AndroidSdk> displayList = new ArrayList<>();
					if (sdkInstaller.hasWorkspaceSdk()) {
						// Merge workspace installation which may not be in Android PreferenceStore and
						// Ensure it is the first item in the list
						AndroidSdk localSdk = null;
			    		File workspaceSdk = new File(sdkInstaller.getWorkSpaceSdk());
			    		for (AndroidSdk sdk: androidSdks)
			    			if (sdk.getSdkLocation().equals(workspaceSdk)) {
			    				localSdk = sdk;
			    				// Do not hide workspace SDK
			    				localSdk.setHidden(false);
			    				sdk.setHidden(false);
			    				selectAndroidSdkPage.addDirtyAndroidSdk(sdk);
			    			} else if (sdk.getSdkLocation().exists() && sdk.getSdkLocation().isDirectory()) {
			    				// Skip invalid directory locations
			    				displayList.add(sdk);
			    			}
			    		if (localSdk == null) {
			    			// Insert at head of list and add to Android configuration
			    			localSdk = new AndroidSdk(workspaceSdk, "");
							androidPreferenceStore.saveAndroidSdk(localSdk);
			    		}
	    				displayList.add(0, localSdk);
					} else {
			    		for (AndroidSdk sdk: androidSdks)
			    			if (sdk.getSdkLocation().exists() && sdk.getSdkLocation().isDirectory()) {
			    				// Skip invalid directory locations
			    				displayList.add(sdk);
			    			}
					}
					// Merge legacy last SDK path
			    	String lastSdkPath = androidPreferenceStore.getLastSdkPath();
			    	if (!lastSdkPath.isEmpty()) {
						AndroidSdk lastAndroidSdk = null;
			    		File lastSdk = new File(lastSdkPath);
			    		if (lastSdk.exists() && lastSdk.isDirectory()) {
				    		for (AndroidSdk sdk: displayList)
				    			if (sdk.getSdkLocation().equals(lastSdk)) {
				    				lastAndroidSdk = sdk;
				    				break;
				    			}
				    		if (lastAndroidSdk == null) {
				    			lastAndroidSdk = new AndroidSdk(lastSdk, "");
				    			displayList.add(lastAndroidSdk);
				    		}
			    		}
			    	}
			    	selectAndroidSdkPage.setSdkList(displayList, selection);
				} catch (Exception e) {
					sdkInstaller.getConsoleLogger().error(e, "Error collecting Android SDKs");
				}
				return Status.OK_STATUS;
			}};
		job.setPriority(Job.BUILD);
		return job;
    }

    /**
     * Complete SDK location selection
     * @return Job object ready to schedule
     */
	private Job getSelectAndroidSdkJob() {
		Set<AndroidSdk> changedSet = selectAndroidSdkPage.getDirtyAndroidSdks(); 
		Job job = new Job("Finish Select SDK") {

			@Override
			protected IStatus run(IProgressMonitor arg0) {
				File workspaceSdk = null;
				try {
					if (sdkInstaller.hasWorkspaceSdk())
				    	workspaceSdk = new File(sdkInstaller.getWorkSpaceSdk());
					// Do SDK selection if workspace SDK not configured
					boolean doSdkSelection = !sdkInstaller.hasWorkspaceSdk();
					AndroidSdk selectedAndroidSdk = getSelectedAndroidSdk();
					if (selectedAndroidSdk != null) {
						changedSet.add(selectedAndroidSdk);
						selectedAndroidSdk.setHidden(false);
						// Handle case workspaceSdk has changed
					    if (!doSdkSelection && !selectedAndroidSdk.getSdkLocation().equals(workspaceSdk))
					    	doSdkSelection = true;
						if (doSdkSelection) {
							// Notify through callback, if set
							File newSdkLocation = selectedAndroidSdk.getSdkLocation();
							if (sdkSelectionListener != null)
								sdkSelectionListener.onSdkSelectionChange(newSdkLocation);
						}
					}
					for (AndroidSdk androidSdk: changedSet) {
						androidPreferenceStore.saveAndroidSdk(androidSdk);
					}
				} catch (Exception e) { // This is not expected
					sdkInstaller.getLogger().error(e, WINDOW_TITLE + " error");
					if (sdkSelectionListener != null)
						sdkSelectionListener.onSelectionError(e.getMessage());
				}
				return Status.OK_STATUS;
			}};
		job.setPriority(Job.BUILD);
		return job;
	}

	/**
	 * Return currently selected SDK
	 * @return AndroidSdk object
	 */
	private AndroidSdk getSelectedAndroidSdk() {
		final AndroidSdk[] androidSdk = new AndroidSdk[1];
		Display.getDefault().syncExec(new Runnable(){

			@Override
			public void run() {
				androidSdk[0] = selectAndroidSdkPage.getSelectedAndroidSdk();
			}});
		return androidSdk[0];
	}
}
