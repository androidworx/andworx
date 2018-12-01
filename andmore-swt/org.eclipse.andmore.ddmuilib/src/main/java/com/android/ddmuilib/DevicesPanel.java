/*
 * Copyright (C) 2007 The Android Open Source Project
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
package com.android.ddmuilib;

import java.util.ArrayList;

import org.eclipse.andmore.base.resources.ImageFactory;
import org.eclipse.andworx.ddms.devices.DeviceProfile;
import org.eclipse.andworx.ddms.devices.Devices;
import org.eclipse.andworx.event.AndworxEvents;
import org.eclipse.e4.core.services.events.IEventBroker;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.viewers.TreePath;
import org.eclipse.jface.viewers.TreeSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.ControlListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.swt.widgets.TreeItem;
import org.osgi.service.event.EventHandler;

import com.android.annotations.Nullable;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData.DebuggerStatus;
import com.android.ddmlib.IDevice;

/**
 * A display of both the devices and their clients.
 */
public class DevicesPanel extends Panel {

	final static int DEVICE_COL_SERIAL = 0;
    final static int DEVICE_COL_STATE = 1;
    // col 2, 3 not used.
    final static int DEVICE_COL_BUILD = 4;

    final static int CLIENT_COL_NAME = 0;
    final static int CLIENT_COL_PID = 1;
    final static int CLIENT_COL_THREAD = 2;
    final static int CLIENT_COL_HEAP = 3;
    final static int CLIENT_COL_PORT = 4;

    private final static String PREFS_COL_NAME_SERIAL = "devicePanel.Col0";
    private final static String PREFS_COL_PID_STATE = "devicePanel.Col1"; 
    private final static String PREFS_COL_THREAD = "devicePanel.Col2"; 
    private final static String PREFS_COL_HEAP = "devicePanel.Col3"; 
    private final static String PREFS_COL_PORT_BUILD = "devicePanel.Col4"; 

    /**
     * Classes which implement this interface provide methods that deals
     * with {@link IDevice} and {@link Client} selection changes coming from the ui.
     */
    public interface IUiSelectionListener {
        /**
         * Sent when a new {@link IDevice} and {@link Client} are selected.
         * @param selectedDevice the selected device. If null, no devices are selected.
         * @param selectedClient The selected client. If null, no clients are selected.
         */
        public void selectionChanged(IDevice selectedDevice, Client selectedClient);
    }

    private final boolean advancedPortSupport;
    private final Devices devices;
    private final IPreferenceStore prefs;
    private final ImageFactory imageFactory;
    private final IEventBroker eventBroker;
    private final ArrayList<IUiSelectionListener> selectionListeners;
    private final EventHandler eventHandler1;
    private final EventHandler eventHandler2;
    private final EventHandler eventHandler3;
    private final EventHandler eventHandler4;
    
    /** The tree viewer */
    TreeViewer viewer;
    /** The Tree created by createControl() */
    private Control control;
    private IDevice currentDevice;
    private Client currentClient;
    private final ArrayList<String> devicesToExpand;

    /**
     * Creates the {@link DevicesPanel} object.
     * @param advancedPortSupport if true the device panel will add support for selected client port and display the ports in the ui.
     * @param imageFactory Image loader 
     */
	public DevicesPanel(
			Devices devices, 
			boolean advancedPortSupport, 
			ImageFactory imageFactory, 
			IPreferenceStore prefs,
			IEventBroker eventBroker) {
		this.devices = devices;
        this.advancedPortSupport = advancedPortSupport;
        this.imageFactory = imageFactory;
        this.prefs = prefs;
        this.eventBroker = eventBroker;
        devicesToExpand = new ArrayList<>();
        selectionListeners = new ArrayList<>();
    	eventHandler1 = new EventHandler() {
			@Override
			public void handleEvent(org.osgi.service.event.Event event) {
				IDevice device = (IDevice)event.getProperty(IEventBroker.DATA);
				if (device != null) {
					deviceConnected(device);
				}
			}};
		eventBroker.subscribe(AndworxEvents.DEVICE_CONNECTED, eventHandler1);
		for (DeviceProfile profile: devices.getAllDevices()) {
			if (profile.isStarted()) {
				String serialNumber = devices.getSerialNumberByName(profile.getName());
				deviceConnected(devices.getDeviceBySerialNumber(serialNumber));
			}
				
		}
    	eventHandler2 = new EventHandler() {
			@Override
			public void handleEvent(org.osgi.service.event.Event event) {
				IDevice device = (IDevice)event.getProperty(IEventBroker.DATA);
				if (device != null) {
					deviceDisconnected(device);
				}
			}};
		eventBroker.subscribe(AndworxEvents.DEVICE_DISCONNECTED, eventHandler2);
    	eventHandler3 = new EventHandler() {
			@Override
			public void handleEvent(org.osgi.service.event.Event event) {
				IDevice device = (IDevice)event.getProperty(IEventBroker.DATA);
				if (device != null) {
					deviceChanged(device);
				}
			}};
		eventBroker.subscribe(AndworxEvents.DEVICE_STATE_CHANGE, eventHandler3);
    	eventHandler4 = new EventHandler() {
			@Override
			public void handleEvent(org.osgi.service.event.Event event) {
				Client client = (Client)event.getProperty(IEventBroker.DATA);
				if (client != null) {
					debugStatusChanged(client);
				}
			}};
		eventBroker.subscribe(AndworxEvents.CHANGE_DEBUGGER_STATUS, eventHandler4);
	}

    /**
     * Returns the selected {@link Client}. May be null.
     */
	@Nullable
    public Client getSelectedClient() {
        return currentClient;
    }

    /**
     * Returns the selected {@link IDevice}. If a {@link Client} is selected, it returns the
     * IDevice object containing the client.
     */
	@Nullable
    public IDevice getSelectedDevice() {
        return currentDevice;
    }

    public void addSelectionListener(IUiSelectionListener listener) {
    	selectionListeners.add(listener);
    }

    public void removeSelectionListener(IUiSelectionListener listener) {
    	selectionListeners.remove(listener);
    }

	/**
     * Creates a control capable of displaying some information.  This is
     * called once, when the application is initializing, from the UI thread.
     * @parent Composite
     */
	@Override
	protected Control createControl(Composite parent) {
        //loadImages(parent.getDisplay());
        // Lays out controls in a single row or column, forcing them to be the same size
        FillLayout fillLayout = new FillLayout();
 		fillLayout.type = SWT.VERTICAL;
        parent.setLayout(fillLayout);
        control = postConstruct(parent);
		return control;
	}

	/**
	 * Optional steps taken after panel is created
	 */
	@Override
	protected void postCreation() {
	}

    /**
     * Sets the focus to the proper control inside the panel.
     */
	@Override
	public void setFocus() {
	}

	public void dispose() {
		eventBroker.unsubscribe(eventHandler1);
		eventBroker.unsubscribe(eventHandler2);
		eventBroker.unsubscribe(eventHandler3);
		eventBroker.unsubscribe(eventHandler4);
	}
	
    Control postConstruct(Composite parent) {
        // create the tree and its column
    	Tree tree = new Tree(parent, SWT.SINGLE | SWT.FULL_SELECTION);
        tree.setHeaderVisible(true);
        tree.setLinesVisible(true);
        createTreeColumn(tree, "Name", 
                32*8, 
                PREFS_COL_NAME_SERIAL);
        createTreeColumn(tree, "", 
                9*8, 
                PREFS_COL_PID_STATE);
        createTreeColumn(tree, "", 
                18, 
                PREFS_COL_THREAD);
        createTreeColumn(tree, "", 
                18, 
                PREFS_COL_HEAP);
        createTreeColumn(tree, "", 
                10*8, 
                PREFS_COL_PORT_BUILD);
        viewer = new TreeViewer(tree);
        viewer.setContentProvider(new TreeContentProvider());
        // make the device auto expanded.
        viewer.setAutoExpandLevel(TreeViewer.ALL_LEVELS);

        viewer.setContentProvider(new TreeContentProvider());
        ViewLabelProvider viewLabelProvider = new ViewLabelProvider(parent.getDisplay(), imageFactory);
        viewLabelProvider.setAdvancedPortSupport(advancedPortSupport);
        viewer.setLabelProvider(viewLabelProvider);
        viewer.setInput(devices);

        GridLayoutFactory.fillDefaults().generateLayout(parent);
        tree.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                notifyListeners();
            }
        });
        if (!prefs.contains(PREFS_COL_NAME_SERIAL)) {
        	// First time expand - ensure first column is fully visible
	        Listener listener = new Listener() {
	
	           @Override
	           public void handleEvent(Event event) {
	              TreeItem treeItem = (TreeItem) event.item;
	              final TreeColumn[] treeColumns = treeItem.getParent().getColumns();
	              Listener self = this;
	              parent.getDisplay().asyncExec(new Runnable() {
	
	                 @Override
	                 public void run() {
	                    for (TreeColumn treeColumn : treeColumns)
	                         treeColumn.pack();
	                    tree.removeListener(SWT.Expand, self);
	                 }
	              });
	           }
	        };
	        tree.addListener(SWT.Expand, listener);
        }
        return tree;
    }


	public void killSelectedClient() {
		// TODO Auto-generated method stub
		
	}

	public void forceGcOnSelectedClient() {
		// TODO Auto-generated method stub
		
	}

	public void setEnabledHeapOnSelectedClient(boolean enable) {
		// TODO Auto-generated method stub
		
	}

	public void setEnabledThreadOnSelectedClient(boolean enable) {
		// TODO Auto-generated method stub
		
	}

	public void toggleMethodProfiling() {
		// TODO Auto-generated method stub
		
	}

    /**
     * Handle event device is connected to the {@link AndroidDebugBridge}.
     * <p/>
     * This can safely be called from a non UI thread.
     * @param device the new device.
     */
    private void deviceConnected(IDevice device) {
        refreshView();
        // If it doesn't have clients yet, it'll need to be manually expanded when it gets them.
        if (!device.hasClients()) {
            synchronized (devicesToExpand) {
                devicesToExpand.add(device.getSerialNumber());
            }
        }
    }
    
	/**
     * Handle event a device is disconnected from the {@link AndroidDebugBridge}.
     * <p/>
     * This can safely be called from a non UI thread.
     * @param device the device.
     */
    public void deviceDisconnected(IDevice device) {
        refreshView();

        // just in case, we remove it from the list of devices to expand.
        synchronized (devicesToExpand) {
            devicesToExpand.remove(device.getSerialNumber());
        }
    }

    /**
     * Sent when a device data changed, or when clients are started/terminated on the device.
     * <p/>
     * This can safely be called from a non UI thread.
     * @param device the device that was updated.
      */
    private void deviceChanged(final IDevice device) {
        boolean expand = false;
        synchronized (devicesToExpand) {
        	String serialNumber = device.getSerialNumber();
            int index = devicesToExpand.indexOf(serialNumber);
            if (device.hasClients() && index != -1) {
                devicesToExpand.remove(index);
                expand = true;
            }
        }
        final boolean finalExpand = expand;

        exec(new Runnable() {
            @Override
            public void run() {
                if (!control.isDisposed()) {
                    // Check if the current device is selected. This is done in case the current
                    // client of this particular device was killed. In this case, we'll need to
                    // manually reselect the device.

                    IDevice selectedDevice = getSelectedDevice();

                    // Refresh the device
                    viewer.refresh(device);

                    // If the selected device was the changed device and the new selection is
                    // empty, we reselect the device.
                    if (selectedDevice == device && viewer.getSelection().isEmpty()) {
                        viewer.setSelection(new TreeSelection(new TreePath(
                                new Object[] { device })));
                    }

                    // Notify the listener of a possible selection change.
                    notifyListeners();

                    if (finalExpand) {
                        viewer.setExpandedState(device, true);
                    }
                }
            }
        });
    }

    private void debugStatusChanged(Client client) {
        exec(new Runnable() {
            @Override
            public void run() {
                if (!control.isDisposed()) {
                    // refresh the client
                   viewer.refresh(client);
                   if (client.getClientData().getDebuggerConnectionStatus() ==
                                DebuggerStatus.WAITING) {
                       // make sure the device is expanded. Normally the setSelection below
                       // will auto expand, but the children of device may not already exist
                       // at this time. Forcing an expand will make the TreeViewer create them.
                       IDevice device = client.getDevice();
                       if (viewer.getExpandedState(device) == false) {
                           viewer.setExpandedState(device, true);
                       }

                       // create and set the selection
                       TreePath treePath = new TreePath(new Object[] { device, client});
                       TreeSelection treeSelection = new TreeSelection(treePath);
                       viewer.setSelection(treeSelection);

                       if (advancedPortSupport) {
                           client.setAsSelectedClient();
                       }
                       // notify the listener of a possible selection change.
                       notifyListeners(device, client);
                	   
                   }
                }
            }});
	}

    private void notifyListeners() {
        // get the selection
        TreeItem[] items = viewer.getTree().getSelection();

        Client client = null;
        IDevice device = null;

        if (items.length == 1) {
            Object object = items[0].getData();
            if (object instanceof Client) {
                client = (Client)object;
                device = client.getDevice();
            } else if (object instanceof IDevice) {
                device = (IDevice)object;
            }
        }
        notifyListeners(device, client);
    }

    private void notifyListeners(IDevice selectedDevice, Client selectedClient) {
        if (selectedDevice != currentDevice || selectedClient != currentClient) {
            currentDevice = selectedDevice;
            currentClient = selectedClient;

            for (IUiSelectionListener listener : selectionListeners) {
                // Notify the listener with a try/catch-all to make sure this thread won't die
                // because of an uncaught exception before all the listeners were notified.
                try {
                    listener.selectionChanged(selectedDevice, selectedClient);
                } catch (Exception e) {
                }
            }
        }
    }

    private void refreshView() {
    	if (control != null) {
	        exec(new Runnable() {
	            @Override
	            public void run() {
	                if (!control.isDisposed()) {
	                    // notify listeners of a possible selection change.
	                    notifyListeners();
	                    viewer.refresh();
	                }
	            }
	        });
    	}
	}

    /**
     * Create a TreeColumn with the specified parameters. If a
     * <code>PreferenceStore</code> object and a preference entry name String
     * object are provided then the column will listen to change in its width
     * and update the preference store accordingly.
     *
     * @param parent The Table parent object
     * @param header The header string
     * @param style The column style
     * @param width the width of the column if the preference value is missing
     * @param pref_name The preference entry name for column width
     * @param prefs The preference store
     */
    private  void createTreeColumn(Tree parent, String header,
            int width, final String pref_name) {

        // create the column
        TreeColumn col = new TreeColumn(parent, SWT.LEFT);

        // if there is no pref store or the entry is missing, we use the sample
        // text and pack the column.
        // Otherwise we just read the width from the prefs and apply it.
        if (prefs == null || prefs.contains(pref_name) == false) {
            col.setWidth(width);

            // init the prefs store with the current value
            if (prefs != null) {
                prefs.setValue(pref_name, width);
            }
        } else {
            col.setWidth(prefs.getInt(pref_name));
        }

        // set the header
        col.setText(header);

        // if there is a pref store and a pref entry name, then we setup a
        // listener to catch column resize to put store the new width value.
        if (prefs != null && pref_name != null) {
            col.addControlListener(new ControlListener() {
                @Override
                public void controlMoved(ControlEvent e) {
                }

                @Override
                public void controlResized(ControlEvent e) {
                    // get the new width
                    int w = ((TreeColumn)e.widget).getWidth();

                    // store in pref store
                    prefs.setValue(pref_name, w);
                }
            });
        }
    }

    /**
     * Executes the {@link Runnable} in the UI thread.
     * @param runnable the runnable to execute.
     */
    private void exec(Runnable runnable) {
        try {
            Display display = control.getDisplay();
            display.asyncExec(runnable);
        } catch (SWTException e) {
            // Tree is disposed, so do nothing
        }
    }

}
