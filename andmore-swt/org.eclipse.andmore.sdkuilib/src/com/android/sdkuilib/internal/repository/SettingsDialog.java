/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.sdkuilib.internal.repository;

import com.android.sdkuilib.ui.GridDataBuilder;
import com.android.sdkuilib.ui.GridLayoutBuilder;

import org.eclipse.andmore.sdktool.SdkContext;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import java.util.Properties;


public class SettingsDialog extends UpdaterBaseDialog {


    //    private Button mCheckUseCache;
    private Button mCheckForceHttp;

    private SelectionAdapter mApplyOnSelected = new SelectionAdapter() {
        @Override
        public void widgetSelected(SelectionEvent e) {
            applyNewSettings(); //$hide$
        }
    };

    public SettingsDialog(Shell parentShell, SdkContext sdkContext) {
        super(parentShell, sdkContext, "Settings" /*title*/);
    }

    @Override
    protected void createShell() {
        super.createShell();
        Shell shell = getShell();
        shell.setMinimumSize(new Point(450, 370));
        shell.setSize(450, 400);
    }

    @Override
    protected void createContents() {
        super.createContents();
        Shell shell = getShell();
         Group group = new Group(shell, SWT.NONE);
        group.setText("Options");
        GridDataBuilder.create(group).fill().grab().hSpan(2);
        GridLayoutBuilder.create(group).columns(2);

        mCheckForceHttp = new Button(group, SWT.CHECK);
        GridDataBuilder.create(mCheckForceHttp).hFill().hGrab().vCenter().hSpan(2);
        mCheckForceHttp.setText("Force https://... sources to be fetched using http://...");
        mCheckForceHttp.setToolTipText(
            "If you are not able to connect to the official Android repository using HTTPS,\n" +
            "enable this setting to force accessing it via HTTP.");
        mCheckForceHttp.addSelectionListener(mApplyOnSelected);

        Label filler = new Label(shell, SWT.NONE);
        GridDataBuilder.create(filler).hFill().hGrab();

        createCloseButton();
    }

    @Override
    protected void postCreate() {
        super.postCreate();
        loadSettings();
    }

    @Override
    protected void close() {
        // Dissociate this page from the controller
        //mSettingsController.setSettingsPage(null);
        super.close();
    }


    // -- Start of internal part ----------
    // Hide everything down-below from SWT designer
    //$hide>>$

    /** Loads settings from the given {@link Properties} container and update the page UI. */
    public void loadSettings() {
    	Settings settings = mSdkContext.getSettings();
        mCheckForceHttp.setSelection(settings.getForceHttp());
    }

    /** Called by the application to retrieve settings from the UI and store them in
     * the given {@link Properties} container. */
    public void retrieveSettings() {
    }

    /**
     * Callback invoked when user touches one of the settings.
     * There is no "Apply" button, settings are applied immediately as they are changed.
     * Notify the application that settings have changed.
     */
    private void applyNewSettings() {
    	retrieveSettings();
    }

    // End of hiding from SWT Designer
    //$hide<<$
}
