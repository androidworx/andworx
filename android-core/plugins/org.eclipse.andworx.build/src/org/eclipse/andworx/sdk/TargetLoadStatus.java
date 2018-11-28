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
package org.eclipse.andworx.sdk;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.andworx.build.AndworxContext;
import org.eclipse.andworx.context.AndroidEnvironment;

import com.android.ide.common.sdk.LoadStatus;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.IAndroidTarget;

/**
 * Tracks status of data that must be loaded by parsing Android targets.
 * Notifies all waiting objects when any status change occurs.
 * Intended to be sub classed by Target Data container which provides higher level API.
 */
public class TargetLoadStatus implements TargetLoadStatusMonitor {
	/** Maps Target Data Load Status to target hashstring */
	final private Map<String, LoadStatus> loadStatusMap;
	/** Object factory to access the Android Environment */
	final private AndworxContext objectFactory;

	/**
	 * Construct TargetLoadStatus object
	 * @param objectFactory Object factory to access the Android Environment
	 */
	public TargetLoadStatus(AndworxContext objectFactory) {
		this.objectFactory = objectFactory;
		loadStatusMap = new HashMap<>();
	}

	/**
	 * Returns Target Data Load Status for target specified by hashstring
	 * @param hashString
	 * @return {@link LoadStatus} enum
	 */
	@Override
	public LoadStatus getLoadStatus(String hashString) {
	    synchronized (loadStatusMap) {
    	    LoadStatus loadStatus = loadStatusMap.get(hashString);
        	if (loadStatus == null)
        		loadStatus = getInitialStatus(hashString);
        	return loadStatus;
        }
	}

	/**
	 * Sets target data load status to LOADING
	 * @param hashString Identifies target
	 */
	protected void setLoadingStatus(String hashString) {
       synchronized (loadStatusMap) {
    	   loadStatusMap.put(hashString, LoadStatus.LOADING);
        }
        signal();
	}
		
	/**
	 * Sets target data load status to LOADED
	 * @param hashString Identifies target
	 */
	protected void setLoadedStatus(String hashString) {
	    synchronized (loadStatusMap) {
	        loadStatusMap.put(hashString, LoadStatus.LOADED);
	    }
	    signal();
	}
			
	/**
	 * Sets target data load status to FAILED
	 * @param hashString Identifies target
	 */
	protected void setFailedStatus(String hashString) {
       synchronized (loadStatusMap) {
    	   loadStatusMap.put(hashString, LoadStatus.FAILED);
        }
        signal();
	}

	/**
	 * Empty load status container
	 */
	protected void clear() {
       synchronized (loadStatusMap) {
    	   loadStatusMap.clear();
        }
        signal();
	}

	/**
	 * Update load statuses to align withthe  current Android SDK configuration
	 */
	protected void refresh() {
		AndroidEnvironment androidEnv = objectFactory.getAndroidEnvironment();
		synchronized(loadStatusMap) {
	   		if (androidEnv.isValid()) {
				// Collect set of targets that exist in the current SDK
	   			Set<String> existingTargets = new HashSet<>();
				for (IAndroidTarget target: androidEnv.getAndroidTargets()) {
					if (target.isPlatform())
						existingTargets.add(AndroidTargetHash.getTargetHashString(target));
			    // Update all statuses. If the target does not exist, the status is automatically FAILED.		
				for (Map.Entry<String, LoadStatus> entry: loadStatusMap.entrySet()) {
					if (!existingTargets.contains(entry.getKey())) {
						entry.setValue(LoadStatus.FAILED);
					} else if (entry.getValue() == LoadStatus.FAILED)
						entry.setValue(LoadStatus.LOADING);
				    }
			    }
		   	} else {
		   		// If the SDK is not available, all statuses are FAILED
				for (Map.Entry<String, LoadStatus> entry: loadStatusMap.entrySet())
					entry.setValue(LoadStatus.FAILED);
		   	}
	   		signal();
		}
	}

	/**
	 * Notify all objects waiting on this monitor.
	 */
    private void signal() {
    	synchronized(this) {
    		notifyAll();
    	}
    }

    /**
     * Returns initial status for target spectifed by hashstring
     * @param hashString
     * @return LoadStatus enum
     */
	private LoadStatus getInitialStatus(String hashString) {
		AndroidEnvironment androidEnv = objectFactory.getAndroidEnvironment();
		LoadStatus loadStatus = LoadStatus.FAILED;
   		if (androidEnv.isValid()) {
			for (IAndroidTarget target: androidEnv.getAndroidTargets()) {
				if (hashString.equals(AndroidTargetHash.getTargetHashString(target))) {
					loadStatus = LoadStatus.LOADING;
					break;
				}
			}
		}
   		loadStatusMap.put(hashString, loadStatus);
 	    return loadStatus; 
    }
}
