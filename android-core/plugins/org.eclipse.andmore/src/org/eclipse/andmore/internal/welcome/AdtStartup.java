/*
 * Copyright (C) 2011 - 2015 The Android Open Source Project
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
 * 
 * Contributors:
 * David Carver - bug 462184 - remove usage tracker
 * 
 */

package org.eclipse.andmore.internal.welcome;

import org.eclipse.andmore.AndmoreAndroidPlugin;
import org.eclipse.andmore.internal.editors.layout.gle2.LayoutWindowCoordinator;
import org.eclipse.ui.IStartup;
import org.eclipse.ui.IWindowListener;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

/**
 * Andmore startup tasks (other than those performed in {@link AndmoreAndroidPlugin#start(org.osgi.framework.BundleContext)}
 * when the plugin is initializing.
 * <p>
 * The main tasks currently performed are:
 * <ul>
 *   <li> See if the user has ever run the welcome wizard, and if not, run it
 * </ul>
 */
public class AdtStartup implements IStartup, IWindowListener {

    private static Boolean workbenchStarted;
    
    static {
    	workbenchStarted = new Boolean(false);
    }

    public static void waitForWorkbenchStart() throws InterruptedException {
    	if (!workbenchStarted) {
	    	synchronized(workbenchStarted) {
	    		if (!workbenchStarted)
	    			workbenchStarted.wait();
	    	}
    	}
	}

	@Override
    public void earlyStartup() {
        initializeWindowCoordinator();
    	synchronized(workbenchStarted) {
    		workbenchStarted.notifyAll();
    		workbenchStarted = true;
    	}
    }


    private void initializeWindowCoordinator() {
        final IWorkbench workbench = PlatformUI.getWorkbench();
        workbench.addWindowListener(this);
        workbench.getDisplay().asyncExec(new Runnable() {
            @Override
            public void run() {
                for (IWorkbenchWindow window : workbench.getWorkbenchWindows()) {
                    LayoutWindowCoordinator.get(window, true /*create*/);
                }
            }
        });
    }

    // ---- Implements IWindowListener ----

    @Override
    public void windowActivated(IWorkbenchWindow window) {
    }

    @Override
    public void windowDeactivated(IWorkbenchWindow window) {
    }

    @Override
    public void windowClosed(IWorkbenchWindow window) {
        LayoutWindowCoordinator listener = LayoutWindowCoordinator.get(window, false /*create*/);
        if (listener != null) {
            listener.dispose();
        }
    }

    @Override
    public void windowOpened(IWorkbenchWindow window) {
        LayoutWindowCoordinator.get(window, true /*create*/);
    }
}
