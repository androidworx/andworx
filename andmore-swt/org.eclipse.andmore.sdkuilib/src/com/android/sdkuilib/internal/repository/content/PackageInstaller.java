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
package com.android.sdkuilib.internal.repository.content;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.andmore.sdktool.SdkContext;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.UpdatablePackage;
import com.android.repository.util.InstallerUtil;
import com.android.sdkuilib.internal.repository.ITask;
import com.android.sdkuilib.internal.repository.ITaskFactory;
import com.android.sdkuilib.internal.repository.ITaskMonitor;
import com.android.sdkuilib.internal.repository.PackageInstallListener;
import com.android.sdkuilib.internal.repository.PackageInstallTask;
import com.android.sdkuilib.internal.repository.ui.SdkProgressFactory;
import com.android.sdkuilib.internal.repository.ui.SdkUpdaterChooserDialog;

/**
 * Installs specified packages and their dependencies. 
 * The package details must first be loaded by PackageAnalyser. 
 * At the start of the installation, a dialog informs the user of the packages to
 * be installed and prompts for acceptance of license terms.
 * @author Andrew Bowley
 *
 * 29-11-2017
 */
public class PackageInstaller {

	private static final String ALREADY_INSTALLED = "Package %s is already installed on the local SDK";
	private static final String CANNOT_COMPUTE_DEPENDENCIES = "Unable to compute a complete list of dependencies.";
	
	/** User-specified packages listed as PkgItem objects */
    private final List<PkgItem> requiredPackageItems = new ArrayList<PkgItem>();
    /** List of remote packages to be downloaded */
    private final List<RemotePackage> remotes = new ArrayList<>();
    /** Map to record remote packages associated with updates */
    private final Map<RemotePackage,UpdatablePackage> updateMap = new HashMap<>();
    /** Contains various task factories linked to a ProgressView control */
    private final SdkProgressFactory factory;

    /**
     * Construct a PackageInstaller object using a package visitor to select what to install
     * @param packageAnalyser Package analyser
     * @param packageVisitor Package visitor
     * @param factory Contains various task factories linked to a ProgressView control
     */
	public PackageInstaller(PackageAnalyser packageAnalyser, PackageVisitor packageVisitor, SdkProgressFactory factory) {
		this.factory = factory;
   	    for (PkgItem packageItem: packageAnalyser.getAllPkgItems()) {
   	    	if (!packageVisitor.visit(packageItem))
   	    		break;
   	    }
   	    for (PkgItem packageItem: packageAnalyser.getAllPkgItems()) {
            // Is this the package we want to install?
            if (packageVisitor.accept(packageItem))
            	requiredPackageItems.add(packageItem);
   	    }
   	    assemblePackages();
	}

    /**
     * Construct a PackageInstaller object to install a given required packages listed as PkgItem objects
     * @param requiredPackageItems List of PkgItem objects
     * @param factory Contains various task factories linked to a ProgressView control
     */
	public PackageInstaller(List<PkgItem> requiredPackageItems, SdkProgressFactory factory) {
		this.factory = factory;
		this.requiredPackageItems.addAll(requiredPackageItems);
   	    assemblePackages();
	}

	/**
	 * Returns User-specified packages listed as PkgItem objects
	 * @return PkgItem list
	 */
	public Collection<? extends PkgItem> getRequiredPackageItems() {
		return requiredPackageItems;
	}

	/**
	 * The install task
	 * @param shell Shell of control invoking this call
	 * @param sdkContext SDK context
	 * @param installListener Callback for events package installed and task completed
	 */
	public void installPackages(Shell shell, SdkContext sdkContext, PackageInstallListener installListener) {
		ITaskFactory taskFactory = factory;
        List<RemotePackage> acceptedRemotes = new ArrayList<>();
        // Execute task asynchronously
		ITask prepareTask = new ITask(){

			@Override
			public void run(ITaskMonitor monitor) {
				// Compute dependencies
		        if (computeDependencies(sdkContext) > 0) {
		        	// Get user to review what is to be installed and accept licences
			        SdkUpdaterChooserDialog dialog =
			                new SdkUpdaterChooserDialog(shell, sdkContext, updateMap.values(), remotes);
			        Display.getDefault().syncExec(new Runnable(){
	
						@Override
						public void run() {
				            dialog.open();
						}});
			        // Launch task to perform package download and install
		            acceptedRemotes.addAll(dialog.getResult());
		    	    if (acceptedRemotes.size() > 0) {
		    	        PackageInstallTask packageInstallTask = new PackageInstallTask(sdkContext);
		    			packageInstallTask.run(shell, remotes, acceptedRemotes, installListener);
			        } else if (installListener != null) {
			        	installListener.onInstallComplete(0);
			        }
		        } else if (installListener != null) {
		        	installListener.onInstallComplete(0);
		        }
			}};
        taskFactory.start("Preparing Packages", prepareTask, null);
	}  

	/**
	 * Scan required packages to remove any already install and build update map
	 * @return flag set true if any packages remain to be installed
	 */
    private boolean assemblePackages() {
    	// Track already-installed packages
    	List<PkgItem> installedList = null;
        for (PkgItem item: requiredPackageItems) {
        	RemotePackage remotePackage = null;
        	if (item.hasUpdatePkg()) { // Update installed package
        		remotePackage = item.getUpdatePkg().getRemote();
        		updateMap.put(remotePackage, item.getUpdatePkg());
        	} else if (item.getMainPackage() instanceof LocalPackage) {
        		// An item with just a local package is not an update
        		factory.getProgressControl().setDescription(String.format(ALREADY_INSTALLED, item.getMainPackage().getDisplayName()));
        		if (installedList == null)
        			installedList = new ArrayList<>();
        		installedList.add(item);
        		continue;
        	} else { // Build remotes list
        		remotePackage = (RemotePackage)item.getMainPackage();
        	}
        	remotes.add(remotePackage);
        }
		if (installedList != null)
			requiredPackageItems.removeAll(installedList);
        return !requiredPackageItems.isEmpty();
	}

    /**
     * Compute dependencies. The work is done by an Android library function but
     * the results may contain duplicates which need to be filtered out.
     * @param sdkContext SDK context
     * @return final number of packages to be downloaded
     */
    private int computeDependencies(SdkContext sdkContext) {
        // computeRequiredPackages() may produce a list containing duplicates!
    	ProgressIndicator progress = sdkContext.getProgressIndicator();
        List<RemotePackage> requiredPackages = InstallerUtil.computeRequiredPackages(
                remotes, sdkContext.getPackages(), progress);
        if (requiredPackages == null) { 
        	// This is not expected as principal packages are normally returned
            progress.logWarning(CANNOT_COMPUTE_DEPENDENCIES);
            return 0;
        }
        // Filter out duplicates
        Iterator<RemotePackage> iterator = requiredPackages.iterator();
        Set<RemotePackage> existenceSet = new HashSet<>();
        existenceSet.addAll(remotes);
        while (iterator.hasNext()) {
        	RemotePackage requiredPackage = iterator.next();
        	if (!existenceSet.contains(requiredPackage)) {
        		{
        			existenceSet.add(requiredPackage);
        			remotes.add(requiredPackage);
        		}
        	}
        }
        // Remove references now existenceSet no longer needed
        existenceSet.clear();
        return remotes.size();
    }

}
