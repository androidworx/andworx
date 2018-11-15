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
package org.eclipse.andmore.internal.build;

import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IDebugEventSetListener;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.IStreamListener;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.core.model.IStreamMonitor;
import org.eclipse.debug.core.model.IStreamsProxy;

/**
 * DebugEventListener listens for a termination event on launched process.
 * It also captures the process console output.
 * A debug event set listener registers with the debug plug-in
 * to receive event notification from programs being run or debugged.
 * @see DebugEvent
 */
public class DebugEventListener implements IDebugEventSetListener {
	
    private IProcess debugProcess;
    private ILaunchConfiguration config;
    private StringBuffer consoleOutput;
    private StringBuffer consoleError;
    private IStreamsProxy streamsProxy;
    private volatile boolean isProcessTerminated;

    /**
     * Construct DebugEventListener object
     * @param config JDT Launch Configuration
     */
    public DebugEventListener(ILaunchConfiguration config)
    {
        this.config = config;
        consoleOutput = new StringBuffer();
        consoleError = new StringBuffer();
    }
    
    public IProcess getProcess()
    {
        return debugProcess;
    }

    public boolean isProcessTerminated() {
		return (debugProcess != null) && isProcessTerminated;
	}

	public String getConsoleOutput() {
    	return consoleOutput.toString();
    }
    
    public String getConsoleError() {
    	return consoleError.toString();
    }
   
	/**
	 * Notifies this listener of the given debug events.
	 * All of the events in the given event collection occurred
	 * at the same location the program be run or debugged.
	 *
	 * @param events the debug events
	 */
    @Override
    public void handleDebugEvents(DebugEvent[] events)
    {
        if (events!=null) {
            for (DebugEvent debugEvent: events) {
                if (debugEvent.getSource() instanceof IProcess) {
                	debugProcess = (IProcess) debugEvent.getSource();
                	if (streamsProxy == null) {
                		streamsProxy = debugProcess.getStreamsProxy();
                		if (streamsProxy != null)
                			monitorStreams(streamsProxy.getOutputStreamMonitor(), streamsProxy.getErrorStreamMonitor());
                	}
                    if (debugEvent.getKind() == DebugEvent.TERMINATE) {
                    	isProcessTerminated = true;
                        if (handleDebugEvent(debugProcess)) 
                        	break;
                    }
                }
            }
        }
    }

	private void monitorStreams(IStreamMonitor outputMonitor, IStreamMonitor errorMonitor) {
		IStreamListener listener = new IStreamListener(){

			@Override
			public void streamAppended(String text, IStreamMonitor monitor) {
				if (consoleOutput.length() > 0)
					consoleOutput.append('\n');
				consoleOutput.append(text);
			}};
		outputMonitor.addListener(listener);
		listener = new IStreamListener(){

			@Override
			public void streamAppended(String text, IStreamMonitor monitor) {
				if (consoleError.length() > 0)
					consoleError.append('\n');
				consoleError.append(text);
			}};
		errorMonitor.addListener(listener);
	}

	private boolean handleDebugEvent(IProcess process)
    {
        if (config != process.getLaunch().getLaunchConfiguration()) 
            return false;
        DebugPlugin.getDefault().removeDebugEventListener(this);
        synchronized(this)
        {
            this.notifyAll();
        }
        return true;
     }

}
