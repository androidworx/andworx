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

import java.util.LinkedList;
import java.util.List;

import org.eclipse.andworx.event.AndworxEvents;
import org.eclipse.e4.core.services.events.IEventBroker;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;

/**
 * Broker for IDebuggerConnector instances and consumers
 */
public class DebugConnectorListener {
	
	private final List<DebugConnectorHandler> handlerList;
	private final List<IDebuggerConnector> connectorList;
	
	public DebugConnectorListener(IEventBroker eventBroker) {
		
		handlerList = new LinkedList<>();
		connectorList = new LinkedList<>();
		
    	EventHandler eventHandler = new EventHandler() {
			@Override
			public void handleEvent(Event event) {
				IDebuggerConnector debuggerConnector = (IDebuggerConnector)event.getProperty(IEventBroker.DATA);
				if (debuggerConnector != null) {
					connectorList.add(debuggerConnector);
					for (DebugConnectorHandler handler: handlerList) {
						if (!handler.isDisposed())
							handler.onDebugConnectorRequest(debuggerConnector);
						else
							handlerList.remove(handler);
					}
				}
		}};
		eventBroker.subscribe(AndworxEvents.DEVICE_DEBUG_REQUEST, eventHandler);
    	eventHandler = new EventHandler() {
			@Override
			public void handleEvent(Event event) {
				DebugConnectorHandler handler = (DebugConnectorHandler)event.getProperty(IEventBroker.DATA);
					if (handler != null) {
						if (handler.isDisposed()) {
							handlerList.remove(handler);
						} else {
							for (IDebuggerConnector connector: connectorList)
								handler.onDebugConnectorRequest(connector);
							addDebugConnectorHandler(handler);
						}
					}
			}};
		eventBroker.subscribe(AndworxEvents.DEBUG_CONNECTOR_HANDLER, eventHandler);
	}
	
	public void addDebugConnectorHandler(DebugConnectorHandler handler) {
		handlerList.add(handler);
	}
}
