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
package org.eclipse.andmore.ddms;

import java.io.File;

import org.eclipse.andworx.event.AndworxEvents;
import org.eclipse.andworx.log.SdkLogger;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.e4.core.services.events.IEventBroker;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;

/**
 * Listens for SDK_LOADED events
 */
public abstract class SdkLoadListener {
	
	private static SdkLogger logger = SdkLogger.getLogger(SdkLoadListener.class.getName());
	
	/** Name of listener to display when running load job */
	private final String name;
	private volatile File sdkLocation;
	
	public SdkLoadListener(String name, IEventBroker eventBroker) {
		this.name = name;
    	EventHandler eventHandler = new EventHandler() {
			@Override
			public void handleEvent(Event event) {
				File data = (File) event.getProperty(IEventBroker.DATA);
				if (data != null) {
					setSdkLocation(data);
				}
			}};
		eventBroker.subscribe(AndworxEvents.SDK_LOADED, eventHandler);
	}

	public void setSdkLocation(File sdkLocation) {
		assert(sdkLocation != null);
		boolean isLocationChanged = !sdkLocation.equals(this.sdkLocation);
		this.sdkLocation = sdkLocation;
		if (isLocationChanged) {
			Job job = new Job(name) {

				@Override
				protected IStatus run(IProgressMonitor monitor) {
					try {
						onLoadSdk(sdkLocation);
					} catch(Exception e) {
						logger.error(e, "Error in %s loading SDK location %s", name, sdkLocation);
						return Status.CANCEL_STATUS;
					}
					return Status.OK_STATUS;
				}};
			job.schedule();
		}
	}
	
	protected abstract void onLoadSdk(File sdkLocation);
	
	
}
