/*
 * Copyright (C) 2011 The Android Open Source Project
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

/*******************************************************************************
* Copyright (c) 2015 David Carver and others
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
* David Carver - removing usage tracker implementation.
*******************************************************************************/

package org.eclipse.andmore.internal.welcome;

import static org.eclipse.andmore.AndmoreAndroidConstants.PROJECT_LOGO_LARGE;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.andmore.AndmoreAndroidPlugin;
import org.eclipse.andmore.internal.actions.AddSupportJarAction;
import org.eclipse.andmore.internal.sdk.AdtConsoleSdkLog;
import org.eclipse.andmore.sdktool.SdkCallAgent;
import org.eclipse.andworx.build.AndworxFactory;
import org.eclipse.andworx.context.AndroidEnvironment;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

import com.android.sdkuilib.ui.AdtUpdateDialog;

/**
 * Wizard shown on first start for new users: configure SDK location, accept or
 * reject usage data collection, etc
 */
public class WelcomeWizard extends Wizard {

    private WelcomeWizardPage mWelcomePage;

    private final boolean mShowWelcomePage;

    /**
     * Creates a new {@link WelcomeWizard}
     * @param showInstallSdkPage show page to install SDK's
     */
    public WelcomeWizard(boolean showInstallSdkPage) {
        mShowWelcomePage = showInstallSdkPage;

        setWindowTitle("Welcome to Android Development");
        ImageDescriptor image = AndmoreAndroidPlugin.getImageDescriptor(PROJECT_LOGO_LARGE); //$NON-NLS-1$
        setDefaultPageImageDescriptor(image);
    }

    @Override
    public void addPages() {
        if (mShowWelcomePage) {
            mWelcomePage = new WelcomeWizardPage();
            addPage(mWelcomePage);
        }
    }

    @Override
    public boolean performFinish() {

        if (mWelcomePage != null) {
            // Read out wizard settings immediately; we will perform the actual work
            // after the wizard window has been taken down and it's too late to read the
            // settings then
            final File path = mWelcomePage.getPath();
            final boolean installCommon = mWelcomePage.isInstallCommon();
            final boolean installLatest = mWelcomePage.isInstallLatest();
            final boolean createNew = mWelcomePage.isCreateNew();

            // Perform installation asynchronously since it takes a while.
            getShell().getDisplay().asyncExec(new Runnable() {
                @Override
                public void run() {
                    if (createNew) {
                        try {
                            Set<Integer> apiLevels = new HashSet<Integer>();
                            if (installCommon) {
                                apiLevels.add(8);
                            }
                            if (installLatest) {
                                apiLevels.add(AdtUpdateDialog.USE_MAX_REMOTE_API_LEVEL);
                            }
                            installSdk(path, apiLevels);
                        } catch (Exception e) {
                            AndmoreAndroidPlugin.logAndPrintError(e, "Andmore Welcome Wizard",
                                    "Installation failed");
                        }
                    }
                    // Set SDK path after installation
                    AndworxFactory.instance().loadSdk(path);
                }
            });
        }

        // The wizard always succeeds, even if installation fails or is aborted
        return true;
    }

    /**
     * Trigger the install window. It will connect to the repository, display
     * a confirmation window showing which packages are selected for install
     * and display a progress dialog during installation.
     */
    private boolean installSdk(File path, Set<Integer> apiLevels) {
        if (!path.isDirectory()) {
            if (!path.mkdirs()) {
                AndmoreAndroidPlugin.logAndPrintError(null, "Andmore Welcome Wizard",
                        "Failed to create directory %1$s",
                        path.getAbsolutePath());
                return false;
            }
        }

        // Get a shell to use for the SDK installation. There are cases where getActiveShell
        // returns null so attempt to obtain it through other means.
        Display display = AndmoreAndroidPlugin.getDisplay();
        Shell shell = display.getActiveShell();
        if (shell == null) {
            IWorkbench workbench = PlatformUI.getWorkbench();
            IWorkbenchWindow window = workbench.getActiveWorkbenchWindow();
            if (window != null) {
                shell = window.getShell();
            }
        }
        boolean disposeShell = false;
        if (shell == null) {
            shell = new Shell(display);
            AndmoreAndroidPlugin.log(IStatus.WARNING, "No parent shell for SDK installation dialog");
            disposeShell = true;
        }

        AndroidEnvironment env = AndworxFactory.instance().getAndroidEnvironment();
        if (!env.isValid()) {
            AndmoreAndroidPlugin.printErrorToConsole(
                    AddSupportJarAction.class.getSimpleName(),   // tag
                    "Error: Android SDK is not loaded yet."); //$NON-NLS-1$
            return false;
        }
        SdkCallAgent callAgent = new SdkCallAgent(
        		env.getAndroidSdkHandler(),
        		new AdtConsoleSdkLog());
        AdtUpdateDialog updater = new AdtUpdateDialog(
                shell,
                callAgent.getSdkContext());
        // Note: we don't have to specify tools & platform-tools since they
        // are required dependencies of any platform.
        boolean result = updater.installNewSdk(apiLevels);

        // TODO: Install extra package here as well since it is now core to most of
        // the templates
        // if (result) {
        //     updater.installExtraPackage(vendor, path);
        // }

        if (disposeShell) {
            shell.dispose();
        }

        if (!result) {
            AndmoreAndroidPlugin.printErrorToConsole("Failed to install Android SDK.");
            return false;
        }

        return true;
    }
}
