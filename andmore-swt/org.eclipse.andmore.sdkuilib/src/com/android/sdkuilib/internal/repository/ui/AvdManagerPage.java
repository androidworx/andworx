/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.sdkuilib.internal.repository.ui;

import com.android.prefs.AndroidLocation.AndroidLocationException;
import com.android.sdklib.devices.DeviceManager;
import com.android.sdklib.devices.DeviceManager.DevicesChangedListener;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.sdkuilib.repository.ISdkChangeListener;
import com.android.sdkuilib.ui.AvdDisplayMode;
import com.android.sdkuilib.internal.widgets.AvdSelector;

import org.eclipse.andmore.sdktool.SdkContext;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

/**
 * An Update page displaying AVD Manager entries.
 * This is the sole page displayed by {@link AvdManagerWindowImpl1}.
 *
 * Note: historically the SDK Manager was a single window with several sub-pages and a tab
 * switcher. For simplicity each page was separated in its own window. The AVD Manager is
 * thus composed of the {@link AvdManagerWindowImpl1} (the window shell itself) and this
 * page displays the actually list of AVDs and various action buttons.
 */
public class AvdManagerPage extends Composite
    implements ISdkChangeListener, DevicesChangedListener, DisposeListener {

    private AvdSelector mAvdSelector;

    private final SdkContext mSdkContext;
    private final DeviceManager mDeviceManager;
    /**
     * Create the composite.
     * @param parent The parent of the composite.
     * @param sdkContext SDK handler and repo manager
     */
    public AvdManagerPage(Composite parent,
            int swtStyle,
            SdkContext sdkContext,
            ManagerControls managerControls) {
        super(parent, swtStyle);

        mSdkContext = sdkContext;
        mSdkContext.getSdkHelper().addListeners(this);

        mDeviceManager = mSdkContext.getDeviceManager();
        mDeviceManager.registerListener(this);

        createContents(this, managerControls);
        postCreate();  //$hide$
    }

    private void createContents(Composite parent, ManagerControls managerControls) {
        parent.setLayout(new GridLayout(1, false));

        Label label = new Label(parent, SWT.NONE);
        label.setLayoutData(new GridData());

        try {
             label.setText(String.format(
                        "List of existing Android Virtual Devices located at %s",
                        mSdkContext.getAvdManager().getBaseAvdFolder()));
        } catch (AndroidLocationException e) {
            label.setText(e.getMessage());
        }

        mAvdSelector = new AvdSelector(parent,
        		mSdkContext,
                AvdDisplayMode.MANAGER,
                managerControls);
    }

    @Override
    public void widgetDisposed(DisposeEvent e) {
        dispose();
    }

    @Override
    public void dispose() {
    	mSdkContext.getSdkHelper().removeListener(this);
        mDeviceManager.unregisterListener(this);
        super.dispose();
    }

    @Override
    protected void checkSubclass() {
        // Disable the check that prevents subclassing of SWT components
    }

    public void selectAvd(AvdInfo avd, boolean reloadAvdList) {
        if (reloadAvdList) {
            mAvdSelector.refresh(true /*reload*/);

            // Reloading the AVDs created new objects, so the reference to avdInfo
            // will never be selected. Instead reselect it based on its unique name.
            AvdManager avdManager = mSdkContext.getAvdManager();
            avd = avdManager.getAvd(avd.getName(), false /*validAvdOnly*/);
        }
        mAvdSelector.setSelection(avd);
    }

    // -- Start of internal part ----------
    // Hide everything down-below from SWT designer
    //$hide>>$

    /**
     * Called by the constructor right after {@link #createContents(Composite)}.
     */
    private void postCreate() {
        // nothing to be done for now.
    }

    // --- Implementation of ISdkChangeListener ---

    @Override
    public void onSdkReload() {
    }

    @Override
    public void preInstallHook() {
        // nothing to be done for now.
    }

    @Override
    public void postInstallHook() {
    	mAvdSelector.refresh(true /*reload*/);
    }

    // --- Implementation of DevicesChangeListener

    @Override
    public void onDevicesChanged() {
        mAvdSelector.refresh(false /*reload*/);
    }

    // End of hiding from SWT Designer
    //$hide<<$
}
