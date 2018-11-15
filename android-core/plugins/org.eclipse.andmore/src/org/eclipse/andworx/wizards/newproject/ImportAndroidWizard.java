/*
 * Copyright (C) 2018 The Android Open Source Project
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
package org.eclipse.andworx.wizards.newproject;

import static org.eclipse.andmore.AndmoreAndroidConstants.PROJECT_LOGO_LARGE;

import org.eclipse.andmore.AdtUtils;
import org.eclipse.andmore.AndmoreAndroidPlugin;
import org.eclipse.andmore.base.resources.PluginResourceProvider;
import org.eclipse.andmore.internal.actions.OpenAndroidPerspectiveAction;
import org.eclipse.andmore.sdktool.SdkUserInterfacePlugin;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.IImportWizard;
import org.eclipse.ui.IWorkbench;

/**
 * Import existing Android project wizard. 
 * A project to be imported must have at a minimum, a build.andworx file pointing to a valid main Android manifest file.
 */
public class ImportAndroidWizard extends Wizard implements IImportWizard {

    /** Single page used by wizard */
    private OpenAndroidPage openAndroidPage;
    /** Working set selection that may be passed to wizard on initialization */
    private IStructuredSelection workingSetSelection;

    /**
     * Initializes this creation wizard using the passed workbench and object selection.
     * <p>
     * This method is called after the no argument constructor and
     * before other methods are called.
     * </p>
     *
     * @param workbench the current workbench
     * @param selection the current object selection
     */
	@Override
	public void init(IWorkbench workbench, IStructuredSelection workingSetSelection) {
		this.workingSetSelection = workingSetSelection;
        setHelpAvailable(false); // TODO have help
        ImageDescriptor desc = AndmoreAndroidPlugin.getImageDescriptor(PROJECT_LOGO_LARGE);
        setDefaultPageImageDescriptor(desc);
	}

	/**
     * Add pages before the wizard opens
     */
   @Override
    public void addPages() {
    	// Use SdkUserInterfacePlugin images on page
		PluginResourceProvider resourceProvider = new PluginResourceProvider(){
			@Override
			public ImageDescriptor descriptorFromPath(String imagePath) {
				return SdkUserInterfacePlugin.instance().getImageDescriptor("icons/" + imagePath);
			}};
        openAndroidPage = new OpenAndroidPage(resourceProvider);
        if (workingSetSelection != null) {
        	openAndroidPage.init(workingSetSelection, AdtUtils.getActivePart());
        }
        addPage(openAndroidPage);
    }

    /**
     * Respond to Finish button click.
     */
	@Override
	public boolean performFinish() {
		// Complete the import by creating a new a workspace project. 
		openAndroidPage.createProject();
		// If an error occurred, the page will no longer be complete
		if (!openAndroidPage.isPageComplete())
			return false;
        // Open the Android perspective
        new OpenAndroidPerspectiveAction().run();
        return true;
	}

}
