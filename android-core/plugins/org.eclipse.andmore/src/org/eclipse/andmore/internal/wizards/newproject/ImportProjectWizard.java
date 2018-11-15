/**
 * Copyright (C) 2012, 2015 The Android Open Source Project
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
 * Kaloyan Raev - bug 471527 - Some wizards still open the Java perspective
 */
package org.eclipse.andmore.internal.wizards.newproject;

import org.eclipse.andmore.AdtUtils;
import org.eclipse.andmore.AndmoreAndroidPlugin;
import org.eclipse.andmore.internal.actions.OpenAndroidPerspectiveAction;
import org.eclipse.andmore.internal.wizards.newproject.NewProjectWizardState.Mode;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;


/**
 * An "Import Android Project" wizard.
 */
public class ImportProjectWizard extends Wizard implements INewWizard {
    private static final String PROJECT_LOGO_LARGE = "icons/android-64.png"; //$NON-NLS-1$

    private NewProjectWizardState mValues;
    private ImportPage mImportPage;
    private IStructuredSelection mSelection;

    /** Constructs a new wizard default project wizard */
    public ImportProjectWizard() {
    }

    @Override
    public void addPages() {
        mValues = new NewProjectWizardState(Mode.ANY);
        mImportPage = new ImportPage(mValues);
        if (mSelection != null) {
            mImportPage.init(mSelection, AdtUtils.getActivePart());
        }
        addPage(mImportPage);
    }

    @Override
    public void init(IWorkbench workbench, IStructuredSelection selection) {
        mSelection = selection;

        setHelpAvailable(false); // TODO have help
        ImageDescriptor desc = AndmoreAndroidPlugin.getImageDescriptor(PROJECT_LOGO_LARGE);
        setDefaultPageImageDescriptor(desc);

        // Trigger a check to see if the SDK needs to be reloaded (which will
        // invoke onSdkLoaded asynchronously as needed).
        AndmoreAndroidPlugin.getDefault().refreshSdk();
    }

    @Override
    public boolean performFinish() {
    	/* TODO - Move this check to a better confext
        File file = new File(AndmoreAndroidPlugin.getDefault().getOsSdkFolder(), OS_SDK_TOOLS_LIB_FOLDER + File.separator
                + FN_PROJECT_PROGUARD_FILE);
        if (!file.exists()) {
            AndmoreAndroidPlugin.displayError("Tools Out of Date?",
            String.format("It looks like you do not have the latest version of the "
                    + "SDK Tools installed. Make sure you update via the SDK Manager "
                    + "first. (Could not find %1$s)", file.getPath()));
            return false;
        }
*/
        NewProjectCreator creator = new NewProjectCreator(mValues, getContainer());
        if (!(creator.createAndroidProjects())) {
            return false;
        }

        // Open the Android perspective
        new OpenAndroidPerspectiveAction().run();
        return true;
    }
}
