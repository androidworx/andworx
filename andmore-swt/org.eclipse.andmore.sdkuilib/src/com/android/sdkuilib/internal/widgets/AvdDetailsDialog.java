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

package com.android.sdkuilib.internal.widgets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.andmore.base.resources.ImageFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

import com.android.sdklib.internal.avd.AvdInfo.AvdStatus;
import com.android.SdkConstants;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.sdkuilib.internal.repository.avd.AvdAgent;
import com.android.sdkuilib.ui.GridDataBuilder;
import com.android.sdkuilib.ui.GridLayoutBuilder;
import com.android.sdkuilib.ui.SwtBaseDialog;

/**
 * Dialog displaying the details of an AVD.
 */
final class AvdDetailsDialog extends SwtBaseDialog {

    private static final String REQUIRES_PACKAGES = "Requires packages to be installed";
    
	private final AvdAgent avdAgent;
    private final ImageFactory imageFactory;
    private volatile int row = 0;

    public AvdDetailsDialog(Shell shell, ImageFactory imageFactory, AvdAgent avdAgent) {
        super(shell, SWT.APPLICATION_MODAL, "AVD details");
        this.avdAgent = avdAgent;
        this.imageFactory = imageFactory;
    }

    /**
     * Create contents of the dialog.
     */
    @Override
    protected void createContents() {
        Shell shell = getShell();
        GridLayoutBuilder.create(shell).columns(2);
        GridDataBuilder.create(shell).fill();
        Composite group = new Composite(shell, SWT.NONE);
        GridLayoutBuilder.create(group).columns(2).noMargins();
        GridDataBuilder.create(group).hFill().hGrab();
        if (avdAgent != null) {
            displayValue(group, "Name:", avdAgent.getAvd().getName(), false);
            displayValue(group, "CPU/ABI:", avdAgent.getPrettyAbiType(), false);
            displayValue(group, "Path:", avdAgent.getPath(), false);
            if (avdAgent.getAvd().getStatus() != AvdStatus.OK) {
                displayValue(group, "Error:", avdAgent.getAvd().getErrorMessage(), false);
            } else if (avdAgent.getTarget() == null) {
                displayValue(group, "Error:", REQUIRES_PACKAGES , false);
            } else {
                displayValue(group, "Target:", avdAgent.getTargetDisplayName(), false);
                displayValue(group, "Skin:", avdAgent.getSkin(), false);
                String sdcard = avdAgent.getSdcard();
                if (!sdcard.isEmpty()) {
                    displayValue(group, "SD Card:", sdcard, false);
                }
                String snapshot = avdAgent.getSnapshot();
                if (!snapshot.isEmpty()) {
                    displayValue(group, "Snapshot:", snapshot, false);
                }
                // display other hardware
                Map<String, String> copy = new HashMap<String, String>(avdAgent.getAvd().getProperties());
                // remove stuff we already displayed (or that we don't want to display)
                copy.remove(AvdManager.AVD_INI_ABI_TYPE);
                copy.remove(AvdManager.AVD_INI_CPU_ARCH);
                copy.remove(AvdManager.AVD_INI_SKIN_NAME);
                copy.remove(AvdManager.AVD_INI_SKIN_PATH);
                copy.remove(AvdManager.AVD_INI_SDCARD_SIZE);
                copy.remove(AvdManager.AVD_INI_SDCARD_PATH);
                copy.remove(AvdManager.AVD_INI_IMAGES_1);
                copy.remove(AvdManager.AVD_INI_IMAGES_2);

                if (copy.size() > 0) {
                	List<Map.Entry<String, String>> propertiesList = new ArrayList<>(copy.size());
                	propertiesList.addAll(copy.entrySet());
                    Label label = new Label(shell, SWT.SEPARATOR | SWT.HORIZONTAL);
                    GridDataBuilder.create(label).fill().hGrab().hSpan(2);
                    Composite group2 = new Composite(shell, SWT.NONE);
                    GridLayoutBuilder.create(group2).columns(2).noMargins().spacing(0);
                    GridDataBuilder.create(group2).fill();
					Comparator<Map.Entry<String, String>> propertiesCompare = new Comparator<Map.Entry<String, String>>(){

						@Override
						public int compare(Entry<String, String> prop1, Entry<String, String> prop2) {
							return prop1.getKey().toLowerCase(Locale.US).compareTo(prop2.getKey().toLowerCase(Locale.US));
						}};
					Collections.sort(propertiesList, propertiesCompare );
                    for (Map.Entry<String, String> entry : propertiesList) {
                        displayValue(group2, entry.getKey() + ": ", entry.getValue(), true);
                    }
                }
            }
        }
        setWindowImage(shell);
    }

    // -- Start of internal part ----------
    // Hide everything down-below from SWT designer
    //$hide>>$


    @Override
    protected void postCreate() {
        // pass
    }

    /**
     * Displays a value with a label.
     *
     * @param parent the parent Composite in which to display the value. This Composite must use a
     * {@link GridLayout} with 2 columns.
     * @param label the label of the value to display.
     * @param value the string value to display.
     * @param shade flag set true if shading required
     */
    private void displayValue(Composite parent, String key, String value, boolean shade) {
        Label label = new Label(parent, SWT.LEFT);
        GridDataBuilder.create(label).fill().vCenter();
        Display display = label.getDisplay();
        if (shade && ((row & 1) == 0)) {
	        label.setBackground(display.getSystemColor(SWT.COLOR_INFO_BACKGROUND));
	        label.setForeground(display.getSystemColor(SWT.COLOR_INFO_FOREGROUND));
        }
        label.setText(key);

        label = new Label(parent, SWT.LEFT);
        GridDataBuilder.create(label).fill().vCenter().hGrab();
        display = label.getDisplay();
        if (shade && ((row & 1) == 0)) {
	        label.setBackground(display.getSystemColor(SWT.COLOR_INFO_BACKGROUND));
	        label.setForeground(display.getSystemColor(SWT.COLOR_INFO_FOREGROUND));
        }
        if (value == null)
        	value = "";
        label.setText(value);
        if (shade)
        	++row;
    }

    /**
     * Creates the icon of the window shell.
     *
     * @param shell The shell on which to put the icon
     */
    private void setWindowImage(Shell shell) {
        String imageName = "android_icon_16.png"; //$NON-NLS-1$
        if (SdkConstants.currentPlatform() == SdkConstants.PLATFORM_DARWIN) {
            imageName = "android_icon_128.png";
        }

        if (imageFactory != null) {
            shell.setImage(imageFactory.getImageByName(imageName));
        }
    }

    // End of hiding from SWT Designer
    //$hide<<$
}
