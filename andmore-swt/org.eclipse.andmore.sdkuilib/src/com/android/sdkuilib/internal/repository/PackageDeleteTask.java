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

package com.android.sdkuilib.internal.repository;

import java.util.List;

import org.eclipse.andmore.sdktool.SdkContext;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import com.android.annotations.NonNull;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.PackageOperation;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.Uninstaller;
import com.android.sdklib.repository.installer.SdkInstallerUtil;
import com.android.sdkuilib.internal.repository.content.PackageAnalyser;
import com.android.sdkuilib.internal.repository.content.PackageFilter;
import com.android.sdkuilib.internal.repository.content.PkgItem;

/**
 * Task to delete local packages. It is executed from an ITaskFactory implementation
 * which provides a monitor for logging and progress indication.
 * @author Andrew Bowley
 *
 * 16-12-2017
 */
public class PackageDeleteTask implements ITask {

    private static final String TITLE = "Delete SDK Package";
	private static final String NO_PACKAGES = "No packages found to delete";
	private static final String DELETE_PROMPT = "Are you sure you want to delete:";
	private static final String DELETE_FAIL = "Package delete failed due to an error";

	/** SDK context */
    private final SdkContext sdkContext;
    /** Package analyser builds and maintains the tree that underlies the SDK packages view */
    private final PackageAnalyser packageAnalyser;
    /** PackageFilter provides functions to select categories and and package items */
	private final PackageFilter packageFilter;
	/** Shell of parent control invoking this task */
    private final Shell shell;

    /**
     * Construct PackageDeleteTask object
     * @param shell Shell of parent control invoking this task
     * @param sdkContext SDK context
     * @param packageAnalyser Package analyser builds and maintains the tree that underlies the SDK packages view
     */
	public PackageDeleteTask(Shell shell, SdkContext sdkContext, PackageAnalyser packageAnalyser, PackageFilter packageFilter) {
		this.shell = shell;
		this.sdkContext = sdkContext;
		this.packageAnalyser = packageAnalyser;
		this.packageFilter = packageFilter; 
	}

	/**
	 * A task that executes and updates a monitor to display it's status.
	 * The task will be run in a separate job.
	 * @param monitor Progress monitor
	 */
    @Override
    public void run(ITaskMonitor monitor) {
        // A list of package items to delete
        List<PkgItem> packageItems = packageAnalyser.getPackagesToDelete(packageFilter);

        if (packageItems.isEmpty()) { // This is not expected
        	monitor.error(null, NO_PACKAGES);
        	return;
        }
        StringBuilder msg = new StringBuilder(DELETE_PROMPT);
        for (PkgItem packageItem: packageItems) {
        	msg.append("\n - ")    //$NON-NLS-1$
            .append(packageItem.getMainPackage().getDisplayName());
            }
        msg.append("\n").append("This cannot be undone.");  //$NON-NLS-1$
        boolean[] proceed = new boolean[]{false};
        Display.getDefault().syncExec(new Runnable(){

			@Override
			public void run() {
				proceed[0] = MessageDialog.openQuestion(shell, TITLE, msg.toString());
			}});
        if (!proceed[0]) 
        	return;
        monitor.setProgressMax(packageItems.size() + 1);
        for (PkgItem packageItem : packageItems) {
        	LocalPackage localPackage = (LocalPackage)packageItem.getMainPackage();
            monitor.setDescription("Deleting '%1$s' (%2$s)",
            		localPackage.getDisplayName(),
            		localPackage.getPath());

            // Delete the actual package
            Uninstaller uninstaller = SdkInstallerUtil.findBestInstallerFactory(localPackage, sdkContext.getHandler())
                    .createUninstaller(localPackage, sdkContext.getRepoManager(), sdkContext.getFileOp());
            if (applyPackageOperation(uninstaller)) {
            	packageItem.markDeleted();
            } else {
                // there was an error, abort.
                monitor.error(null, DELETE_FAIL);
                monitor.setProgressMax(0);
                break;
            }
            monitor.incProgress(1);
            if (monitor.isCancelRequested()) {
                break;
            }
        }
        monitor.incProgress(1);
        monitor.setDescription("Package delete done");
        packageAnalyser.removeDeletedNodes();
    }

    /**
     * Two-stage task execution - prepare and complete
     * @param operation Package operation
     * @return flag set true if task completed sucessfully
     */
	protected boolean applyPackageOperation(
            @NonNull PackageOperation operation) {
    	ProgressIndicator progressIndicator = sdkContext.getProgressIndicator();
        return operation.prepare(progressIndicator) && operation.complete(progressIndicator);
    }

}
