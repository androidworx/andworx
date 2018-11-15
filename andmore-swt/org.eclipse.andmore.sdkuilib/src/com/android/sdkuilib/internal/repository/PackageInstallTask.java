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
/**
 * 
 */
package com.android.sdkuilib.internal.repository;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.andmore.sdktool.SdkContext;
import org.eclipse.andmore.sdktool.Utilities;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
import com.android.repository.io.FileOpUtils;
import com.android.repository.io.impl.FileSystemFileOp;
import com.android.sdkuilib.internal.repository.content.PackageAnalyser;

/**
 * Task to prompt user to accept package licenses and then download and install packages.
 * The task is to be executed in a worker thread such as a job.
 * @author Andrew Bowley
 *
 * 29-11-2017
 */
public class PackageInstallTask {
	protected static final String DOWNLOAD_PACKAGE = "%s | %s";

	private static final String PACKAGE_INSTALL_FAILED = "Install of package failed due to an error";
	private static final String PREPARING_INSTALL = "Preparing to install packages";
	private static final String DIRECTORY_DELETE_REQUIRED = "Directory %s needs to be deleted";
	private static final String PACKAGES_INSTALLED = "%d package%s installed";
	private static final String PACKAGE_INSTALL_CANCELLED = "Install cancelled";

	/** SDK context */
    private final SdkContext sdkContext;
    /** Package manager which performs load and install operations */
	private final PackageManager packageManager;
	/** Number of packages actually installed */
	private int installCount;
	
	/**
	 * Construct PackageInstallTask object
	 * @param sdkContext SDK context
	 */
	public PackageInstallTask(SdkContext sdkContext) {
		this.sdkContext = sdkContext;
		this.packageManager = sdkContext.getPackageManager();
	}

	/**
	 * Returns number of packages actually installed
	 * @return int
	 */
	public int getinstallCount() {
		return installCount;
	}

	/**
	 * The task
	 * @param shell Shell of parent control invoking this task
	 * @param packageList Packages initially selected to be installed
	 * @param acceptedRemotes Packages which were accepted by the user 
	 * @param packageInstallListener Callback for install completed events
	 */
	public void run(
			Shell shell,
			List<RemotePackage> packageList, 
			List<RemotePackage> acceptedRemotes, 
			PackageInstallListener packageInstallListener) {
		// Ensure if installing tools package that the tools directory is deleted if it already exists
		// This is needed in case a previous failed install attempt left files in the tools directory
		ProgressIndicator progressIndicator = sdkContext.getProgressIndicator();
		for (RemotePackage remotePackage: packageList) {
	    	String name = PackageAnalyser.getNameFromPath(remotePackage.getPath());
			if (name.equals("tools")) {
				File toolsDir = new File(sdkContext.getLocation(), "tools");
				boolean success =  !toolsDir.exists();
				if (!success) {
					try {
				        FileSystemFileOp fop = (FileSystemFileOp)FileOpUtils.create();
				        fop.deleteFileOrFolder(toolsDir);
						success = !toolsDir.exists();
					} catch (SecurityException e) {
						progressIndicator.logError("Error deleting directory " + toolsDir.toString(), e);
					}
				}
				if (!success) {
					progressIndicator.logWarning(String.format(DIRECTORY_DELETE_REQUIRED, toolsDir.toString()));
					return;
				}
				break;
			}
		}
    	// Assume installation will proceed  The pre-install operation will take an indeterminate time to complete.
    	sdkContext.getSdkHelper().broadcastPreInstallHook(sdkContext.getSdkLog());
 
    	// Determine which initially selected packages have been rejected
        List<RemotePackage> rejectedRemotes = new ArrayList<>();
        Iterator<RemotePackage> iterator = packageList.iterator();
        while (iterator.hasNext()) {
        	RemotePackage remote = iterator.next();
        	if (!acceptedRemotes.contains(remote))
        		rejectedRemotes.add(remote);
        }
        List<RemotePackage> remotes = new ArrayList<>();
        remotes.addAll(packageList);
        // Prompt user whether to continue when there are rejects
    	if (!rejectedRemotes.isEmpty()) {
    		String title = "Package licences not accepted";
         	StringBuilder builder = new StringBuilder();
        	builder.append("The following packages can not be installed since their " +
                          "licenses or those of the packages they depend on were not accepted:");
        	Iterator<RemotePackage> iterator2 = rejectedRemotes.iterator();
        	while(iterator2.hasNext())
        		builder.append('\n').append(iterator2.next().getPath());
            if (!acceptedRemotes.isEmpty()) {
            	builder.append("\n\nContinue installing the remaining packages?");
                final boolean[] doContinue = new boolean[] { false };
                Display.getDefault().syncExec(new Runnable() {
                    @Override
                    public void run() {
                    	doContinue[0] = MessageDialog.openQuestion(shell, title, builder.toString());
                    }});
            	if (!doContinue[0]) {
            		return;
            	} 
            } else { // No packages left to install
                Display.getDefault().syncExec(new Runnable() {
                    @Override
                    public void run() {
                         MessageDialog.openInformation(shell, title, builder.toString());
                    }
                });
               return;
            }
            remotes = acceptedRemotes;
    	}
    	// Delegate installation of packages, one by one, to PackageManager
    	progressIndicator.setText(PREPARING_INSTALL);
        for (RemotePackage remotePackage : remotes) {
      	    long fileSize = remotePackage.getArchive().getComplete().getSize();
        	progressIndicator.setSecondaryText(String.format(
        		DOWNLOAD_PACKAGE, remotePackage.getDisplayName(),
        		Utilities.formatFileSize(fileSize)));

            if (packageManager.applyPackageOperation(remotePackage)) {
            	++installCount;
            	if (packageInstallListener != null)
            		packageInstallListener.onPackageInstalled(remotePackage);
            } else {
                // there was an error, abort.
            	if (progressIndicator.isCanceled())
            		progressIndicator.logWarning(PACKAGE_INSTALL_CANCELLED);
            	else
            		progressIndicator.logError(PACKAGE_INSTALL_FAILED);
                break;
            }
            if (progressIndicator.isCanceled()) {
                break;
            }
        }
        if (installCount > 0) { // Perform post install operations
			sdkContext.reloadLocalPackages();
    	    sdkContext.getSdkHelper().broadcastPostInstallHook(sdkContext.getSdkLog());
        }
        /* Post package install operations no longer used: 
         * Give the user an opportuning to restart ADB and advise check for ADT Updates 
        if (installedAddon || installedPlatformTools) {
            // We need to restart ADB. Actually since we don't know if it's even
            // running, maybe we should just kill it and not start it.
            // Note: it turns out even under Windows we don't need to kill adb
            // before updating the tools folder, as adb.exe is (surprisingly) not
            // locked.

            askForAdbRestart(monitor);
        }

        if (installedTools) {
            notifyToolsNeedsToBeRestarted(flags);
        }
        */
    	if (packageInstallListener != null)
    		packageInstallListener.onInstallComplete(installCount);
    	if (installCount > 0) {
			String plural = installCount > 1 ? "s" : "";
    		progressIndicator.setSecondaryText(String.format(PACKAGES_INSTALLED,  installCount, plural));
    	} else if (progressIndicator.isCanceled())
    		progressIndicator.setSecondaryText(PACKAGE_INSTALL_CANCELLED);
    	else
    		progressIndicator.setSecondaryText(PACKAGE_INSTALL_FAILED);
    }
}
