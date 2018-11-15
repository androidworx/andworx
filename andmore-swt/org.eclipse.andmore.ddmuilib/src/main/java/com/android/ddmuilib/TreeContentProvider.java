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

import org.eclipse.andworx.ddms.devices.Devices;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.andworx.ddms.devices.DeviceProfile;
import org.eclipse.jface.viewers.AbstractTreeViewer;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.Viewer;

import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;

public class TreeContentProvider implements ITreeContentProvider {

    /**
     * Returns the elements to display in the viewer
     * when its input is set to the given element.
     * These elements can be presented as rows in a table, items in a list, etc.
     * The result is not modified by the viewer.
	 * <p>
	 * <b>NOTE:</b> The returned array must not contain the given
	 * <code>inputElement</code>, since this leads to recursion issues in
	 * {@link AbstractTreeViewer} (see
	 * </p>
     *
     * @param inputElement the input element
     * @return the array of elements to display in the viewer
     */
    @Override
    public Object[] getElements(Object inputElement) {
    	if (inputElement instanceof Devices) {
    		Devices devices = (Devices)inputElement;
    		List<IDevice> deviceList = new ArrayList<>();
    		for (DeviceProfile deviceProfile: devices.getAllDevices()) {
    			if (deviceProfile.isStarted()) {
    				String serialNumber = devices.getSerialNumberByName(deviceProfile.getName());
    				deviceList.add(devices.getDeviceBySerialNumber(serialNumber));
    			}
    		}
            return deviceList.toArray(new IDevice[deviceList.size()]);
        }
        return new Object[0];
    }

    /**
     * Returns the child elements of the given parent element.
     * <p>
     * The difference between this method and <code>IStructuredContentProvider.getElements</code>
     * is that <code>getElements</code> is called to obtain the
     * tree viewer's root elements, whereas <code>getChildren</code> is used
     * to obtain the children of a given parent element in the tree (including a root).
     * </p>
     * The result is not modified by the viewer.
     *
     * @param parentElement the parent element
     * @return an array of child elements
     */
    @Override
    public Object[] getChildren(Object parentElement) {
        if (parentElement instanceof IDevice) {
            return ((IDevice)parentElement).getClients();
        }
        return new Object[0];
    }

    /**
     * Returns the parent for the given element, or <code>null</code>
     * indicating that the parent can't be computed.
     * In this case the tree-structured viewer can't expand
     * a given node correctly if requested.
     *
     * @param element the element
     * @return the parent element, or <code>null</code> if it
     *   has none or if the parent cannot be computed
     */
    @Override
    public Object getParent(Object element) {
        if (element instanceof Client) {
            return ((Client)element).getDevice();
        }
        return null;
    }

    /**
     * Returns whether the given element has children.
     * <p>
     * Intended as an optimization for when the viewer does not
     * need the actual children.  Clients may be able to implement
     * this more efficiently than <code>getChildren</code>.
     * </p>
     *
     * @param element the element
     * @return <code>true</code> if the given element has children,
     *  and <code>false</code> if it has no children
     */
    @Override
    public boolean hasChildren(Object element) {
        if (element instanceof IDevice) {
            return ((IDevice)element).hasClients();
        }
        // Clients never have children.
        return false;
    }


    /**
     * Notifies this content provider that the given viewer's input
     * has been switched to a different element.
     * <p>
     * A typical use for this method is registering the content provider as a listener
     * to changes on the new input (using model-specific means), and deregistering the viewer
     * from the old input. In response to these change notifications, the content provider
     * should update the viewer (see the add, remove, update and refresh methods on the viewers).
     * </p>
     * <p>
     * The viewer should not be updated during this call, as it might be in the process
     * of being disposed.
     * </p>
     * <p>
     * This implementation does nothing.
     * </p>
     *
     * @param viewer the viewer
     * @param oldInput the old input element, or <code>null</code> if the viewer
     *   did not previously have an input
     * @param newInput the new input element, or <code>null</code> if the viewer
     *   does not have an input
     */
    @Override
    public void inputChanged(Viewer v, Object oldInput, Object newInput) {
    }

    /**
     * Disposes of this content provider.
     * This is called by the viewer when it is disposed.
     * <p>
     * The viewer should not be updated during this call, as it is in the process
     * of being disposed.
     * </p>
     * <p>
     * This implementation does nothing.
     * </p>
     */
    @Override
    public void dispose() {
    }

}
