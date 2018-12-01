/*
 * Copyright (C) 2008 The Android Open Source Project
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
import java.util.Map;

import org.eclipse.andworx.build.AndworxContext;
import org.eclipse.andworx.event.AndworxEvents;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.e4.core.services.events.IEventBroker;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;

import com.android.ide.common.sdk.LoadStatus;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.IAndroidTarget;

/**
 * Container for data collected by Target Parser which also tracks Target Data load status
 */
public class SdkTargetDataMap<Data extends Disposeable> extends TargetLoadStatus {

    /** Event broker service */
    final private IEventBroker eventBroker;
    /** Maps Target Data to target hash string */
	final private Map<String, Data> targetDataMap;
	/** Job to handle event SDK loaded */
	final Job newSdkJob = new Job("Updating target data map") {

		@Override
		protected IStatus run(IProgressMonitor monitor) {
			refresh();
			return Status.OK_STATUS;
		}
		
	};

	/**
	 * Construct SdkTargetDataMap object
	 * @param objectFactory Object factory, required by super class to access Android environment
	 */
	public SdkTargetDataMap(AndworxContext objectFactory) {
		super(objectFactory);
		targetDataMap = new HashMap<>();
		// Retrieve Event Broker from Eclipse Context
        IEclipseContext eclipseContext = objectFactory.getEclipseContext();
    	eventBroker = (IEventBroker) eclipseContext.get(IEventBroker.class.getName());
    	// Subscribe to event SDK loaded
    	EventHandler eventHandler = new EventHandler() {
			@Override
			public void handleEvent(Event event) {
				// Job calls super.refresh()
				newSdkJob.schedule();
	        }};
        eventBroker.subscribe(AndworxEvents.SDK_LOADED, eventHandler);
	}

	/**
	 * Sets target data load status to LOADING
	 * @param hashString Identifies target
	 */
	public void setLoadingStatus(String hashString) {
        super.setLoadingStatus(hashString);
	}
	
	/**
	 * Sets target data load status to FAILED
	 * @param hashString Identifies target
	 */
	public void setFailedStatus(String hashString) {
        super.setFailedStatus(hashString);
	}

	/**
	 * Returns load status for target specified by hashstring
	 * @param hashString Identifies target
	 */
	public LoadStatus getLoadStatus(String hashString) {
	    return super.getLoadStatus(hashString);
	}

	/**
	 * Returns Target Data for target specified by hashstring
	 * @param hashString Identifies target
	 * @return target data or null if not available
	 */
	public Data getTargetData(String hashString) {
        synchronized (targetDataMap) {
        	return targetDataMap.get(hashString);
        }
	}

	/**
	 * For specified target, sets Target Data and changes load status to LOADED
	 * @param target IAndroidTarget object
	 * @param data Target data to set
	 */
    public void setTargetData(IAndroidTarget target, Data data) {
    	String hashString = AndroidTargetHash.getTargetHashString(target);
        synchronized (targetDataMap) {
        	setLoadedStatus(hashString);
            targetDataMap.put(hashString, data);
        }
    }

    /**
     * Call dispose() on every Target Data object and clear containers
     */
    public void dispose() {
        synchronized (targetDataMap) {
	        for (Data data : targetDataMap.values()) {
	        	try {
	        		data.dispose();
	        	} catch (Exception e) {
	        		
	        	}
	        }
	        targetDataMap.clear();
	        super.clear();
        }
    }
    

}
