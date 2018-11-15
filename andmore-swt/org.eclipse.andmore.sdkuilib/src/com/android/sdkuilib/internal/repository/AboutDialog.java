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


import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.eclipse.andmore.base.resources.ImageFactory;
import org.eclipse.andmore.sdktool.SdkContext;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

import com.android.SdkConstants;
import com.android.repository.api.LocalPackage;
import com.android.repository.io.FileOp;
import com.android.repository.io.FileOpUtils;
import com.android.sdklib.repository.PkgProps;
import com.android.sdkuilib.ui.GridDataBuilder;
import com.android.sdkuilib.ui.GridLayoutBuilder;

/**
 * About Dialog. "Android SDK Manager" + verions + copyright 
 */
public class AboutDialog extends UpdaterBaseDialog {

    private static final String COPYRIGHT = "Copyright (C) 2009-2017";

	public AboutDialog(Shell parentShell, SdkContext sdkContext) {
        super(parentShell, sdkContext, "About" /*title*/);
    }

    @Override
    protected void createContents() {
        super.createContents();
        Shell shell = getShell();
        shell.setMinimumSize(new Point(450, 150));
        shell.setSize(450, 150);

        GridLayoutBuilder.create(shell).columns(3);

        Label logo = new Label(shell, SWT.NONE);
        ImageFactory imgf = mSdkContext.getSdkHelper().getImageFactory();
        Image image = imgf == null ? null : imgf.getImageByName("sdkman_logo_128.png");
        if (image != null) logo.setImage(image);

        Label label = new Label(shell, SWT.NONE);
        GridDataBuilder.create(label).hFill().hGrab().hSpan(2);
        String version = getVersion();
        if (!version.isEmpty())
        	version = String.format("Revision %1$s", version);
        label.setText(String.format(
                "Android SDK Manager.\n" +
                "%1$s\n" +
                "%2$s The Android Open Source Project.",
                version,
                COPYRIGHT));

        Label filler = new Label(shell, SWT.NONE);
        GridDataBuilder.create(filler).fill().grab().hSpan(2);

        createCloseButton();
    }

    @Override
    protected void checkSubclass() {
        // Disable the check that prevents subclassing of SWT components
    }

    // -- Start of internal part ----------
    // Hide everything down-below from SWT designer
    //$hide>>$

    // End of hiding from SWT Designer
    //$hide<<$

    /** 
     * Show current "tools" version
     * @return String
     */
    private String getVersion() {
    	LocalPackage platformPackage = mSdkContext.getHandler().getLatestLocalPackageForPrefix(SdkConstants.FD_TOOLS, null, false, mSdkContext.getProgressIndicator());
    	if (platformPackage != null)
    		return platformPackage.getVersion().toShortString();
    	return getToolsVersion();
    }
  
    /**
     * Get tools version from source properties file
     * @return
     */
    private String getToolsVersion() {
        Properties p = new Properties();
        File suffix = new File(SdkConstants.FD_TOOLS, SdkConstants.FN_SOURCE_PROP);
        FileOp fileOp = FileOpUtils.create();
        InputStream fis = null;
        try {
            fis = fileOp.newFileInputStream(new File(mSdkContext.getLocation().toString(), suffix.toString()));
            p.load(fis);
        } catch (IOException ignore) {
        } finally {
        	if (fis != null)
                try {
					fis.close();
                } catch (IOException ignore) {
            }
        }
	    return p.getProperty(PkgProps.PKG_REVISION, "");
    }

}
