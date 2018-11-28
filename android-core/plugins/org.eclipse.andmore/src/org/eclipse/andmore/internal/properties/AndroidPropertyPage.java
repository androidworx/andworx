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

package org.eclipse.andmore.internal.properties;

import java.io.File;
import java.util.List;
import java.util.Set;

import org.eclipse.andmore.internal.sdk.SdkLocationListener;
import org.eclipse.andmore.internal.sdk.SdkTargetControl;
import org.eclipse.andworx.build.AndworxContext;
import org.eclipse.andworx.build.AndworxFactory;
import org.eclipse.andworx.context.AndroidEnvironment;
import org.eclipse.andworx.maven.Dependency;
import org.eclipse.andworx.project.ProjectProfile;
import org.eclipse.andworx.registry.ProjectRegistry;
import org.eclipse.andworx.registry.ProjectState;
import org.eclipse.core.resources.IProject;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.ui.dialogs.PropertyPage;

import com.android.sdklib.IAndroidTarget;

/**
 * Property page for "Android" project.
 * This is accessible from the Package Explorer when right clicking a project and choosing
 * "Properties".
 *
 */
public class AndroidPropertyPage extends PropertyPage {

    private IProject mProject;
    private ProjectRegistry projectRegistry;
    private SdkTargetControl mTargetControl;
    private Button libraryButton;
    // APK-SPLIT: This is not yet supported, so we hide the UI
//    private Button mSplitByDensity;
    private LibraryProperties mLibraryDependencies;

    public AndroidPropertyPage() {
    	projectRegistry = AndworxFactory.instance().getProjectRegistry();
    }

    @Override
    protected Control createContents(Composite parent) {
        // get the element (this is not yet valid in the constructor).
        mProject = (IProject)getElement();
        // build the UI.
        Composite top = new Composite(parent, SWT.NONE);
        top.setLayoutData(new GridData(GridData.FILL_BOTH));
        top.setLayout(new GridLayout(1, false));

        Group targetGroup = new Group(top, SWT.NONE);
        targetGroup.setLayoutData(new GridData(GridData.FILL_BOTH));
        targetGroup.setLayout(new GridLayout(1, false));
        targetGroup.setText("Project Build Target");

        mTargetControl = new SdkTargetControl(true /* allow selection */);
        mTargetControl.setSdkLocationListener(new SdkLocationListener(){

			@Override
			public void onSdkLocationChanged(File sdkLocation, boolean isValid, List<IAndroidTarget> targetList) {
				performDefaults();
			}});
        mTargetControl.createControl(targetGroup, 1);

        Group libraryGroup = new Group(top, SWT.NONE);
        libraryGroup.setLayoutData(new GridData(GridData.FILL_BOTH));
        libraryGroup.setLayout(new GridLayout(1, false));
        libraryGroup.setText("Library");

        libraryButton = new Button(libraryGroup, SWT.CHECK);
        libraryButton.setText("Is Library");

        mLibraryDependencies = new LibraryProperties(libraryGroup);

        // fill the ui
        fillUi();

        // add callbacks
        mTargetControl.setSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                updateValidity();
            }
        });

        if (mProject.isOpen() == false) {
            // disable the ui.
        }

        return top;
    }

    @Override
	public void createControl(Composite parent) {
    	super.createControl(parent);
    	mTargetControl.postCreate();
    }
    
    @Override
    public boolean performOk() {
        AndroidEnvironment env = AndworxFactory.instance().getAndroidEnvironment();
        if (env.isValid() && mProject.isOpen()) {

            boolean mustSaveProp = false;
            ProjectProfile profile = projectRegistry.getProjectProfile(mProject);
            IAndroidTarget newTarget = mTargetControl.getSelected();
           	profile.setTarget(newTarget);

        	boolean isLibrary = libraryButton.getSelection();
            if (isLibrary != profile.isLibrary()) {
            	profile.setLibrary(isLibrary);
            }

            if (mLibraryDependencies.save()) {
            	Set<Dependency> oldDependencies = profile.getDependencies();
            	Set<Dependency> newDependencies = mLibraryDependencies.getDependencies();
            	for (Dependency dependency: oldDependencies)
            		if (!newDependencies.contains(dependency))
            			profile.removeDependency(dependency);
            	for (Dependency dependency: newDependencies)
            		if (!oldDependencies.contains(dependency))
            			profile.addDependency(dependency);
                mustSaveProp = true;
            }

            if (mustSaveProp) {
            	/* TODO - use JPA to persist profile
                try {
                    IResource projectProp = mProject.findMember(SdkConstants.FN_PROJECT_PROPERTIES);
                    if (projectProp != null) {
                        projectProp.refreshLocal(IResource.DEPTH_ZERO, new NullProgressMonitor());
                    }
                } catch (Exception e) {
                    String msg = String.format(
                            "Failed to save %1$s for project %2$s",
                            SdkConstants.FN_PROJECT_PROPERTIES, mProject.getName());
                    AndmoreAndroidPlugin.log(e, msg);
                }
                */
            }
        }
        return true;
    }

    @Override
    protected void performDefaults() {
        fillUi();
        updateValidity();
    }

    private void fillUi() {
    	AndworxContext objectFactory = AndworxFactory.instance();
        AndroidEnvironment env = objectFactory.getAndroidEnvironment();
        if (env.isValid() && mProject.isOpen()) {
            ProjectState state = objectFactory.getProjectState(mProject);
            if (state == null)
            	return;
            // Get the target
            IAndroidTarget target = state.getProfile().getTarget();
            if (target != null) {
                mTargetControl.setSelection(target);
            }
            libraryButton.setSelection(state.isLibrary());
            mLibraryDependencies.setContent(state);
        }
    }

    private void updateValidity() {
        // look for the selection and validate the page if there is a selection
        IAndroidTarget target = mTargetControl.getSelected();
        setValid(target != null);
    }
}
