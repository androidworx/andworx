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

import java.util.Locale;

import org.eclipse.andmore.base.resources.ImageFactory;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;

import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.ddmlib.DdmPreferences;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IDevice.DeviceState;

/**
 * A Label Provider for the {@link TreeViewer} in {@link DevicesPanel}. It provides
 * labels and images for {@link IDevice} and {@link Client} objects.
 */
public class ViewLabelProvider implements ITableLabelProvider {

    public final static int ICON_WIDTH = 16;
    public final static String ICON_THREAD = "thread.png";
    public final static String ICON_HEAP = "heap.png";
    public final static String ICON_HALT = "halt.png";
    public final static String ICON_GC = "gc.png";
    public final static String ICON_HPROF = "hprof.png";
    public final static String ICON_TRACING_START = "tracing_start.png";
    public final static String ICON_TRACING_STOP = "tracing_stop.png";

    private final ImageFactory imageFactory;
    private boolean advancedPortSupport;

    private Image deviceImage;
    private Image emulatorImage;
    private Image threadImage;
    private Image heapImage;
    private Image waitingImage;
    private Image debuggerImage;
    private Image debugErrorImage;

    public ViewLabelProvider(Display display, ImageFactory imageFactory) {
    	this.imageFactory = imageFactory;
        loadImages(display);
        advancedPortSupport = true;
    }
    
    public void setAdvancedPortSupport(boolean advancedPortSupport) {
		this.advancedPortSupport = advancedPortSupport;
	}

	/**
     * Returns the label image for the given column of the given element.
     *
     * @param element the object representing the entire row, or
     *    <code>null</code> indicating that no input object is set
     *    in the viewer
     * @param columnIndex the zero-based index of the column in which
     *   the label appears
     * @return Image or <code>null</code> if there is no image for the
     *  given object at columnIndex
     */
    @Override
    public Image getColumnImage(Object element, int columnIndex) {
        if (columnIndex == DevicesPanel.DEVICE_COL_SERIAL && element instanceof IDevice) {
            IDevice device = (IDevice)element;
            if (device.isEmulator()) {
                return emulatorImage;
            }

            return deviceImage;
        } else if (element instanceof Client) {
            Client client = (Client)element;
            ClientData cd = client.getClientData();

            switch (columnIndex) {
                case DevicesPanel.CLIENT_COL_NAME:
                    switch (cd.getDebuggerConnectionStatus()) {
                        case DEFAULT:
                            return null;
                        case WAITING:
                            return waitingImage;
                        case ATTACHED:
                            return debuggerImage;
                        case ERROR:
                            return debugErrorImage;
                    }
                    return null;
                case DevicesPanel.CLIENT_COL_THREAD:
                    if (client.isThreadUpdateEnabled()) {
                        return threadImage;
                    }
                    return null;
                case DevicesPanel.CLIENT_COL_HEAP:
                    if (client.isHeapUpdateEnabled()) {
                        return heapImage;
                    }
                    return null;
            }
        }
        return null;
    }

    @Override
    public String getColumnText(Object element, int columnIndex) {
        if (element instanceof IDevice) {
            IDevice device = (IDevice)element;
            switch (columnIndex) {
                case DevicesPanel.DEVICE_COL_SERIAL:
                    return device.getName();
                case DevicesPanel.DEVICE_COL_STATE:
                    return getStateString(device);
                case DevicesPanel.DEVICE_COL_BUILD: {
                    String version = device.getProperty(IDevice.PROP_BUILD_VERSION);
                    if (version != null) {
                        String debuggable = device.getProperty(IDevice.PROP_DEBUGGABLE);
                        if (device.isEmulator()) {
                            String avdName = device.getAvdName();
                            if (avdName == null) {
                                avdName = "?"; // the device is probably not online yet, so
                                               // we don't know its AVD name just yet.
                            }
                            if (debuggable != null && debuggable.equals("1")) { //$NON-NLS-1$
                                return String.format("%1$s [%2$s, debug]", avdName,
                                        version);
                            } else {
                                return String.format("%1$s [%2$s]", avdName, version); //$NON-NLS-1$
                            }
                        } else {
                            if (debuggable != null && debuggable.equals("1")) { //$NON-NLS-1$
                                return String.format("%1$s, debug", version);
                            } else {
                                return String.format("%1$s", version); //$NON-NLS-1$
                            }
                        }
                    } else {
                        return "unknown";
                    }
                }
            }
        } else if (element instanceof Client) {
            Client client = (Client)element;
            ClientData cd = client.getClientData();

            switch (columnIndex) {
                case DevicesPanel.CLIENT_COL_NAME:
                    String name = cd.getClientDescription();
                    if (name != null) {
                        if (cd.isValidUserId() && cd.getUserId() != 0) {
                            return String.format(Locale.US, "%s (%d)", name, cd.getUserId());
                        } else {
                            return name;
                        }
                    }
                    return "?";
                case DevicesPanel.CLIENT_COL_PID:
                    return Integer.toString(cd.getPid());
                case DevicesPanel.CLIENT_COL_PORT:
                    if (advancedPortSupport) {
                        int port = client.getDebuggerListenPort();
                        String portString = "?";
                        if (port != 0) {
                            portString = Integer.toString(port);
                        }
                        if (client.isSelectedClient()) {
                            return String.format("%1$s / %2$d", portString,
                                    DdmPreferences.getSelectedDebugPort());
                        }
                        return portString;
                    }
            }
        }
        return null;
    }
    
	@Override
	public void addListener(ILabelProviderListener listener) {

	}

	@Override
	public void dispose() {

	}

	@Override
	public boolean isLabelProperty(Object element, String property) {
		return false;
	}

	@Override
	public void removeListener(ILabelProviderListener listener) {
	}

    private void loadImages(Display display) {
        if (deviceImage == null) {
        	String imageName = "device.png";
            deviceImage = imageFactory.getImageByName(imageName);
            if (deviceImage == null) {
            	deviceImage = loadImage(imageName, display, 
            			                 ICON_WIDTH, ICON_WIDTH,
            		                     display.getSystemColor(SWT.COLOR_RED)); 
            }
        }
        if (emulatorImage == null) {
        	String imageName = "emulator.png";
        	emulatorImage = imageFactory.getImageByName(imageName);
            if (emulatorImage == null) {
	            emulatorImage = loadImage(imageName, display,
	                                      ICON_WIDTH, ICON_WIDTH,
	                                      display.getSystemColor(SWT.COLOR_BLUE));
            }
        }
        if (threadImage == null) {
        	String imageName = ICON_THREAD;
        	threadImage = imageFactory.getImageByName(imageName);
            if (threadImage == null) {
	            threadImage = loadImage(imageName, display,
	                                     ICON_WIDTH, ICON_WIDTH,
	                                     display.getSystemColor(SWT.COLOR_YELLOW));
            }
        }
        if (heapImage == null) {
        	String heapImageName = ICON_HEAP;
        	heapImage = imageFactory.getImageByName(heapImageName);
            if (heapImage == null) {
                 heapImage = loadImage(heapImageName, display, 
                                       ICON_WIDTH, ICON_WIDTH,
                                       display.getSystemColor(SWT.COLOR_BLUE));
            }
        }
        if (waitingImage == null) {
        	String waitImageName = "debug-wait.png";
        	waitingImage = imageFactory.getImageByName(waitImageName);
            if (waitingImage == null) {
	            waitingImage = loadImage(waitImageName, display,
	                                      ICON_WIDTH, ICON_WIDTH,
	                                      display.getSystemColor(SWT.COLOR_RED));
            }
        }
        if (debuggerImage == null) {
        	String debugImageName = "debug-attach.png";
        	debuggerImage = imageFactory.getImageByName(debugImageName);
            if (debuggerImage == null) {
	            debuggerImage = loadImage(debugImageName, display,
	                                      ICON_WIDTH, ICON_WIDTH,
	                                       display.getSystemColor(SWT.COLOR_GREEN));
            }
        }
        if (debugErrorImage == null) {
        	String deviceImageName = "device.png";
        	debugErrorImage = imageFactory.getImageByName(deviceImageName);
            if (debugErrorImage == null) {
	            debugErrorImage = loadImage(deviceImageName, display,
	                                        ICON_WIDTH, ICON_WIDTH, 
	                                        display.getSystemColor(SWT.COLOR_RED));
            }
        }
    }

    private Image loadImage(String imageName, Display display, int width, int height, Color color) {
        return imageFactory.getImageByName(imageName, new ReplacementImageFactory(display, width, height, color));
    }
    
    /**
     * Returns a display string representing the state of the device.
     * @param d the device
     */
    private static String getStateString(IDevice d) {
        DeviceState deviceState = d.getState();
        if (deviceState == DeviceState.ONLINE) {
            return "Online";
        } else if (deviceState == DeviceState.OFFLINE) {
            return "Offline";
        } else if (deviceState == DeviceState.BOOTLOADER) {
            return "Bootloader";
        }
        return "??";
    }

}
