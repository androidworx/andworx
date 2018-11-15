/*******************************************************************************
 * Copyright (c) 2000, 2015 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Kaloyan Raev - bug 471527 - Some wizards still open the Java perspective
 *******************************************************************************/
package org.eclipse.andmore.internal.actions;

import org.eclipse.andmore.AndmoreAndroidConstants;
import org.eclipse.andmore.AndmoreAndroidPlugin;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.action.Action;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.WorkbenchException;

/**
 * Action to programmatically open an Android perspective.
 *
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 *
 * @since 0.5
 *
 * @noextend This class is not intended to be subclassed by clients.
 */
public class OpenAndroidPerspectiveAction extends Action {

    @Override
    public void run() {
        IWorkbench workbench = PlatformUI.getWorkbench();
        IWorkbenchWindow window = workbench.getActiveWorkbenchWindow();
        IWorkbenchPage page = window.getActivePage();

        IAdaptable input;
        if (page != null) {
            input = page.getInput();
        } else {
            input = ResourcesPlugin.getWorkspace().getRoot();
        }

        try {
            workbench.showPerspective(AndmoreAndroidConstants.PERSPECTIVE_ANDROID, window, input);
        } catch (WorkbenchException e) {
            AndmoreAndroidPlugin.log(e, Messages.OpenAndroidPerspectiveAction_error_open_failed);
            //AndmoreAndroidPlugin.displayError(Messages.OpenAndroidPerspectiveAction_dialog_title,
            //        Messages.OpenAndroidPerspectiveAction_error_open_failed);
        }
    }
}
