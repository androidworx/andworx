/**
 * 
 */
package org.eclipse.andmore.internal.sdk;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.andmore.AndmoreAndroidPlugin;
import org.eclipse.andmore.sdktool.install.SdkProfile;
import org.eclipse.andmore.sdktool.install.SdkProfileListener;
import org.eclipse.andworx.build.AndworxFactory;
import org.eclipse.andworx.sdk.AndroidSdkPreferences;
import org.eclipse.andmore.internal.sdk.AdtConsoleSdkLog;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;

import com.android.annotations.NonNull;
import com.android.sdklib.IAndroidTarget;
import com.android.sdkuilib.widgets.AndroidSdkSelector;
import com.android.sdkuilib.widgets.SdkTargetSelector;
import com.android.utils.ILogger;

/**
 * @author andrew
 *
 */
public class SdkTargetControl {
    private static final int CONTROL_HEIGHT_PIXELS = 350;
    
    /** Android SDK preferences */
    private final AndroidSdkPreferences androidSdkPreferences;
	/** Flag set true if selection is enabled */
	private final boolean allowSelection;
	/** Persisted logger */
	private final ILogger consoleLogger;
	/** The SDK targets and location status control */
    private SdkTargetSelector targetSelector;
	/** Field valid flag, updated when the field value is changed and verified */
	private boolean isValid;
	/** Callback to handle event SDK location changed */
	private SdkLocationListener sdkLocationListener;

	/**
	 * 
     * @param allowSelection Flag set true if selection is enabled
	 */
	public SdkTargetControl(boolean allowSelection) {
		this.allowSelection = allowSelection;
		consoleLogger = new AdtConsoleSdkLog();
		androidSdkPreferences = AndworxFactory.instance().getAndroidSdkPreferences();
	}

	/**
	 * Returns flag set true if workspace ADT preferences contains a SDK folder value
	 * @return boolean
	 */
	public boolean hasWorkspaceSdk() {
		@NonNull
		String workSpaceSdkLocation = getWorkSpaceSdk();
		boolean hasWorkspaceSdk = (workSpaceSdkLocation != null) && !workSpaceSdkLocation.trim().isEmpty();
		if (hasWorkspaceSdk) {
			File sdkLocation = new File(workSpaceSdkLocation.trim());
			hasWorkspaceSdk = sdkLocation.exists() && sdkLocation.isDirectory();
		}
		return hasWorkspaceSdk;
	}

	/**
	 * Returns SDK location configured in workspace preferences
	 * @return
	 */
	public String getWorkSpaceSdk() {
		return androidSdkPreferences.getSdkLocationValue();
	}

	/**
	 * Returns flag set true if current SDK location contains an SDK with the minimum required packages
	 * @return boolean
	 */
	public boolean isValid() {
		return isValid;
	}
	
	/**
	 * @param sdkLocationListener The sdkLocationListener to set
	 */
	public void setSdkLocationListener(SdkLocationListener sdkLocationListener) {
		this.sdkLocationListener = sdkLocationListener;
	}

	public void createControl(Composite parent, int numColumns) {
        targetSelector = new SdkTargetSelector(
        		parent,
                allowSelection);
        GridData gd = (GridData) targetSelector.getLayoutData();
        if (numColumns > 0)
        	gd.horizontalSpan = numColumns;
        if (gd.heightHint == -1)
            // The number of targets varies according to SDK so fix on an accommodating height
            gd.heightHint = CONTROL_HEIGHT_PIXELS;
        if (gd.widthHint == -1)
            // The number of targets varies according to SDK so fix on an accommodating height
            gd.widthHint = 450;
	}

    /**
     * Complete construction after controls created
     */
	public void postCreate() {
		 // Only fill target table if the SDK location is set in the workspace 
		 // and the location is for a directory
         if (hasWorkspaceSdk()) {
    		String value = getWorkSpaceSdk();
    		if ((value != null) && !value.isEmpty()) {
    			File sdkLocation = new File(value);
    			fillTargetList(sdkLocation);
    		}
        }
	}

	/**
	 * Fill target table for given SDK location. Runs in background as SDK packages are loaded and profiled.
	 * @param sdkLocation The SDK location
	 */
	public void fillTargetList(File sdkLocation) {
        Job job = new Job("Get SDK targets") {

			@Override
			protected IStatus run(IProgressMonitor monitor) {
		        try {
			        List<IAndroidTarget> targetList = new ArrayList<>();
			        // Show empty table if the location is invalid
					if (sdkLocation.exists() && sdkLocation.isDirectory()) {
						targetSelector.displayPendingStatus();
						SdkProfile sdkProfile = new SdkProfile(consoleLogger);
						sdkProfile.evaluate(sdkLocation, getSdkProfileListener(targetList, sdkLocation));
					} else {
						targetSelector.displayLocationStatus(sdkLocation);
						displayTargets(targetList, sdkLocation);
						isValid = false;
					}
		        } catch (Exception e) {
		            // We need to catch *any* exception that arises here, otherwise it disables
		            // the whole pref panel. We can live without the Sdk target selector but
		            // not being able to actually set an sdk path.
		        	consoleLogger.error(e, "SdkTargetSelector failed");
		        }
				return Status.OK_STATUS;
			}};
			job.setPriority(Job.BUILD);
			job.schedule();
	}

	public void setSelection(IAndroidTarget target) {
		if (targetSelector != null)
			targetSelector.setSelection(target);
	}

	public IAndroidTarget getSelected() {
		if (targetSelector != null)
			return targetSelector.getSelected();
		return null;
	}

	public void setSelectionListener(SelectionAdapter selectionAdapter) {
		if (targetSelector != null)
			targetSelector.setSelectionListener(selectionAdapter);
	}

	private SdkProfileListener getSdkProfileListener(List<IAndroidTarget> targetList, File sdkLocation) {
		return new SdkProfileListener(){

			@Override
			public void onProfile(SdkProfile sdkProfile) {
				if (!sdkProfile.getSuccess()) {
					// Error while obtaining profile. This is not expected.
					targetSelector.displayStatus(false, sdkProfile.getStatusMessage());
					isValid = false;
				}
				else {
					// Check if required packages are missing
					int requiredPackageCount = sdkProfile.getRequiredPackageCount();
					if (requiredPackageCount != 0) {
					    String plural = requiredPackageCount > 1 ? "s" : "";
					    targetSelector.displayStatus(false, String.format(AndroidSdkSelector.PACKAGES, requiredPackageCount, plural));
					    if (sdkProfile.getPlatforms().size() > 0)
					        targetList.addAll(sdkProfile.getTargets(sdkLocation));
						isValid = false;
					} else {
				        targetList.addAll(sdkProfile.getTargets(sdkLocation));
					    targetSelector.displayLocationStatus(sdkLocation);
						isValid = true;
					}
				}
				displayTargets(targetList, sdkLocation);
			}};
	}
	
	/**
	 * Display targets
	 * @param targetList IAndroidTarget list
	 */
	private void displayTargets(List<IAndroidTarget> targetList, File sdkLocation) {
		IAndroidTarget[] targets = targetList.toArray(new IAndroidTarget[0]);
    	Display.getDefault().syncExec(new Runnable(){

			@Override
			public void run() {
	        	targetSelector.setTargets(targets);
	        	if (sdkLocationListener != null)
	        		sdkLocationListener.onSdkLocationChanged(sdkLocation, isValid, targetList);
			}});
	}

}
