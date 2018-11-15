/*
 * Copyright (C) 2008 The Android Open Source Project
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

package org.eclipse.andmore.internal.wizards.actions;

import org.eclipse.andmore.internal.lint.EclipseLintRunner;
import org.eclipse.andworx.build.AndworxFactory;
import org.eclipse.andworx.registry.ProjectState;
import org.eclipse.andworx.wizards.export.ExportAndroidWizard;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPart;

public class ExportWizardAction implements IObjectActionDelegate {

    private ISelection mSelection;
    private IWorkbench mWorkbench;

    /**
     * @see IObjectActionDelegate#setActivePart(IAction, IWorkbenchPart)
     */
    @Override
    public void setActivePart(IAction action, IWorkbenchPart targetPart) {
        mWorkbench = targetPart.getSite().getWorkbenchWindow().getWorkbench();
    }

    @Override
    public void run(IAction action) {
        if (mSelection instanceof IStructuredSelection) {
            IStructuredSelection selection = (IStructuredSelection)mSelection;

            // get the unique selected item.
            if (selection.size() == 1) {
                Object element = selection.getFirstElement();

                // get the project object from it.
                IProject project = null;
                if (element instanceof IProject) {
                    project = (IProject) element;
                } else if (element instanceof IAdaptable) {
                    project = (IProject) ((IAdaptable) element).getAdapter(IProject.class);
                }

                // and finally do the action
                if (project != null) {
                    if (!EclipseLintRunner.runLintOnExport(
                            mWorkbench.getActiveWorkbenchWindow().getShell(), project)) {
                        return;
                    }

                    ProjectState state = AndworxFactory.instance().getProjectState(project);
                    if (state.isLibrary()) {
                        MessageDialog.openError(mWorkbench.getDisplay().getActiveShell(),
                                "Android Export",
                                "Android library projects cannot be exported.");
                    } else {
                        // call the export wizard on the current selection.
                    	ExportAndroidWizard wizard = new ExportAndroidWizard();
                        wizard.init(mWorkbench, selection);
                        WizardDialog dialog = new WizardDialog(
                                mWorkbench.getDisplay().getActiveShell(), wizard);
                        dialog.open();
                    }
                }
            }
        }
    }

    @Override
    public void selectionChanged(IAction action, ISelection selection) {
        mSelection = selection;
    }
}
