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
package org.eclipse.andworx.wizards.export;

import static org.eclipse.andmore.AndmoreAndroidConstants.PROJECT_LOGO_LARGE;

import org.eclipse.andmore.AndmoreAndroidConstants;
import org.eclipse.andmore.base.resources.PluginResourceProvider;
import org.eclipse.andmore.base.resources.PluginResourceRegistry;
import org.eclipse.andworx.build.AndworxContext;
import org.eclipse.andworx.build.AndworxFactory;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.IExportWizard;
import org.eclipse.ui.IWorkbench;

/**
 * Selects an Android project, generates an APK and then exports it
 */
public class ExportAndroidWizard extends Wizard implements IExportWizard {

	/** Container for plugin ImageDescriptor providers */
    private final PluginResourceRegistry resourceRegistry ;
	/** Andworx object factory */
    private final AndworxContext objectFactory;
    /** Images provider */
	private PluginResourceProvider resourceProvider;
    /** Selected project or null if none selected */
    private IProject project;
    /** Single export page */
    private ExportAndroidPage exportAndroidPage;

    /**
     * Create ExportAndroidWizard object
     */
    public ExportAndroidWizard() {
    	super();
    	objectFactory = AndworxFactory.getAndworxContext();
        resourceRegistry = objectFactory.getPluginResourceRegistry();
    }
    
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
	public void init(IWorkbench workbench, IStructuredSelection selection) {
        setHelpAvailable(false); // TODO have help
        // Set header image
        resourceProvider = resourceRegistry.getResourceProvider(AndmoreAndroidConstants.PLUGIN_ID);
        ImageDescriptor desc = resourceProvider.descriptorFromPath(PROJECT_LOGO_LARGE);
        setDefaultPageImageDescriptor(desc);
       // Get the project from the selection, if available
        Object selected = selection.getFirstElement();

        if (selected instanceof IProject) {
            project = (IProject)selected;
        } else if (selected instanceof IAdaptable) {
            IResource r = ((IAdaptable)selected).getAdapter(IResource.class);
            if (r != null) {
                project = r.getProject();
            }
        }
	}

	/**
     * Add export page before the wizard opens
     */
    @Override
    public void addPages() {
    	exportAndroidPage = new ExportAndroidPage(objectFactory);
    	if (project != null)
    		exportAndroidPage.posfConstruct(project);
    	addPage(exportAndroidPage);
    }

    /**
     * Respond to Finish button click.
     */
	@Override
	public boolean performFinish() {
		// Delegate perform export to page
     	exportAndroidPage.exportProject();
     	// Return flag set true if export completes successfully. Note the Wizard handler discards return value
    	return exportAndroidPage.isPageComplete();
	}

}
