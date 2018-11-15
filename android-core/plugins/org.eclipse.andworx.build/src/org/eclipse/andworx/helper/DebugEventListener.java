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
package org.eclipse.andworx.helper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IDebugEventSetListener;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.IStreamListener;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.core.model.IStreamMonitor;
import org.eclipse.debug.core.model.IStreamsProxy;

import com.android.ide.common.process.ProcessOutput;

/**
 * DebugEventListener listens for a termination event on launched process.
 * It also captures the process console output.
 * A debug event set listener registers with the debug plug-in
 * to receive event notification from programs being run or debugged.
 * @see DebugEvent
 */
public class DebugEventListener implements IDebugEventSetListener {
    /** The process  */	
    private IProcess process;
    /** The launch configuration - used to idenify correct process on termination */
    private ILaunch launch;
    /** Debug plugin which provides launch notification */
    private DebugPlugin debugPlugin;
    /** Console output stream */
    private OutputStream consoleOutput;
    /** Console error stream */
    private OutputStream consoleError;
    /** Sharable process output grabber */
    private IStreamsProxy streamsProxy;
    /** Termination flag */
    private volatile boolean isProcessTerminated;

    /**
     * Construct DebugEventListener object
     * @param launch JDT Launch object
     * #param output Object to handle console standard and error outputs
     */
    public DebugEventListener(ILaunch launch, ProcessOutput output)
    {
        this.launch = launch;
        consoleOutput = output.getErrorOutput();
        consoleError = output.getErrorOutput();
        debugPlugin = DebugPlugin.getDefault();
        debugPlugin.addDebugEventListener(this);
    }
  
    /**
     * Returns process being monitored
     * @return IProcess object or null if no event has yet been received
     */
    public IProcess getProcess()
    {
        return process;
    }

    /**
     * Returns flag set true if process has reported itself as terminated
     * @return boolean
     */
    public boolean isProcessTerminated() {
		return isProcessTerminated;
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
        if (events == null)
        	return;
        for (DebugEvent debugEvent: events) {
        	if (debugEvent.getKind() == DebugEvent.CREATE) {
            	IProcess source = (IProcess) debugEvent.getSource();
            	if (source.getLaunch() == launch) {
            		process = source;
	        		streamsProxy = process.getStreamsProxy();
	        		if (streamsProxy != null) {
	        			// Grab existing console output as this will not be received by the stream listener
	        			IStreamMonitor outMonitorStream = streamsProxy.getOutputStreamMonitor();
	        			String standard = outMonitorStream.getContents();
	        			if (!standard.isEmpty()) {
		        			System.out.println(standard);
		        			writeOut(standard);
	        			}
	         			IStreamMonitor errMonitorStream = streamsProxy.getErrorStreamMonitor();
	         			String error = errMonitorStream.getContents();
	         			if (!error.isEmpty()) {
		           			writeErr(error);
		        			monitorStreams(outMonitorStream, errMonitorStream);
	         			}
	        		} else {
	        			System.err.println("Process does not have streams proxy");
	        		}
            	}
        	} else if (debugEvent.getKind() == DebugEvent.TERMINATE) {
            	IProcess source = (IProcess) debugEvent.getSource();
            	if (source == process) {
                    synchronized(this)
                    {   // Notify listeners of this object to signal process termination
                       	isProcessTerminated = true;
                       	this.notify();
                    }
            	}
            }
        }
    }

    /**
     * Write to error console
     * @param contents Text to write
     */
	private void writeOut(String contents) {
		if (!contents.isEmpty())
			copyText2OutStream(contents, consoleOutput);
	}

    /**
     * Write to console output
     * @param contents Text to write
     */
	private void writeErr(String contents) {
		if (!contents.isEmpty())
			copyText2OutStream(contents, consoleError);
	}

	/**
	 * Copy text to given output stream
	 * @param contents Text to write
	 * @param outputStream Stream to write to
	 */
	private void copyText2OutStream(String contents, OutputStream outputStream) {
		ByteArrayInputStream in;
		try {
			in = new ByteArrayInputStream(contents.getBytes("UTF-8"));
	        int byteRead;
	        while ((byteRead = in.read()) != -1) {
	        	outputStream.write(byteRead);
	        }
        	outputStream.flush();
		} catch (IOException e) {
			// Ignore
		}
	}

	/**
	 * Monitor for console output
	 * @param outputMonitor
	 * @param errorMonitor
	 */
	private void monitorStreams(IStreamMonitor outputMonitor, IStreamMonitor errorMonitor) {
		IStreamListener listener = new IStreamListener(){

			@Override
			public void streamAppended(String text, IStreamMonitor monitor) {
				writeOut(text);
			}};
		outputMonitor.addListener(listener);
		listener = new IStreamListener(){

			@Override
			public void streamAppended(String text, IStreamMonitor monitor) {
				writeErr(text);
			}};
		errorMonitor.addListener(listener);
	}

}
