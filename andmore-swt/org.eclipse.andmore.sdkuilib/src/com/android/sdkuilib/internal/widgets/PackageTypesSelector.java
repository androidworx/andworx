/*
 * Copyright (C) 2017 The Android Open Source Project
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
/**
 * 
 */
package com.android.sdkuilib.internal.widgets;

import java.util.Set;
import java.util.TreeSet;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Shell;

import com.android.sdkuilib.internal.repository.content.PackageFilter;
import com.android.sdkuilib.internal.repository.content.PackageType;
import com.android.sdkuilib.ui.GridDataBuilder;
import com.android.sdkuilib.ui.GridLayoutBuilder;
import com.android.sdkuilib.ui.SwtBaseDialog;

/**
 * Allows the user to select the types of packages to view for installation
 * @author Andrew Bowley
 *
 * 23-11-2017
 */
public class PackageTypesSelector extends SwtBaseDialog {

	private static final String WINDOW_TITLE = "Filter by Category";
	private static final String SELECTION_HEADING = "Categories";
	
	private static int WINDOW_HEIGHT = 400;
	private static int WINDOW_WIDTH = 200;

	public static final PackageType[] PACKAGE_TYPES = {
			PackageType.tools,
			PackageType.platform_tools,
			PackageType.build_tools,
			PackageType.platforms,
			PackageType.add_ons,
			PackageType.system_images,
			PackageType.sources,
			//PackageType.samples,
			//PackageType.docs,
			PackageType.extras,
			//PackageType.emulator,
			//PackageType.cmake,
			//PackageType.lldb,
			//PackageType.ndk_bundle,
			//PackageType.patcher,
			PackageType.generic
			
	};
	
	/** User selected set of package types */
	private Set<PackageType> packageTypeSet = new TreeSet<>();
	/** Initial set of package types retained to detect if anything has changed */
	private Set<PackageType> originalPackageTypeSet = new TreeSet<>();
	// Controls
	private SelectionAdapter selectionAdapter;
	private Button buttonOK;
	private Button buttonCancel;
	
	/**
	 * Create PackageTypesSelector object
	 * @param shell Parent shell
	 * @param packageTypeSet Set of package types for initial selection
	 */
	public PackageTypesSelector(Shell shell, Set<PackageType> packageTypeSet) {
        super(shell, SWT.APPLICATION_MODAL, WINDOW_TITLE);
        if (packageTypeSet != null) {
        	this.packageTypeSet.addAll(packageTypeSet);
        	originalPackageTypeSet.addAll(packageTypeSet);
        }
        selectionAdapter = 	getSelectionAdapter();
    }

	/**
	 * Returns user selected set of package types
	 * @return PackageType set
	 */
    public Set<PackageType> getPackageTypeSet() {
		return packageTypeSet != null ? packageTypeSet : PackageFilter.EMPTY_PACKAGE_TYPE_SET;
	}

    /**
     * Creates the shell for this dialog.
     * <p/>
     * Called before {@link #createContents()}.
     */
    protected void createShell() {
        super.createShell();
        Shell shell = getShell();
        shell.setMinimumSize(new Point(WINDOW_WIDTH, WINDOW_HEIGHT));
        shell.setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
    }
    
	/**
     * Create contents of the dialog.
     */
	@Override
	protected void createContents() {
        Shell shell = getShell();
        GridLayoutBuilder.create(shell).columns(1);
        GridDataBuilder.create(shell).fill();
        Group composite = new Group(shell, SWT.SHADOW_ETCHED_OUT);
        composite.setText(SELECTION_HEADING);
        GridLayoutBuilder.create(composite).margins(2);
        GridDataBuilder.create(composite).hFill().hGrab();
        Display display = composite.getDisplay();
        composite.setBackground(display.getSystemColor(SWT.COLOR_INFO_BACKGROUND));
        composite.setForeground(display.getSystemColor(SWT.COLOR_INFO_FOREGROUND));
    	boolean isPreset = !packageTypeSet.isEmpty(); 
        for (PackageType packageType: PACKAGE_TYPES) {
        	Button button = addCheck(composite, packageType, isPreset && packageTypeSet.contains(packageType));
        	button.setBackground(display.getSystemColor(SWT.COLOR_INFO_BACKGROUND));
        	button.setForeground(display.getSystemColor(SWT.COLOR_INFO_FOREGROUND));
        }
        Group controls = new Group(composite, SWT.SHADOW_ETCHED_OUT);
        GridDataBuilder.create(controls).vCenter().hGrab().hFill();
        GridLayoutBuilder.create(controls).columns(2);
        controls.setBackground(display.getSystemColor(SWT.COLOR_INFO_BACKGROUND));
        controls.setForeground(display.getSystemColor(SWT.COLOR_INFO_FOREGROUND));
        buttonCancel = new Button(controls, SWT.PUSH);
        buttonCancel.setText("Cancel");  
        GridDataBuilder.create(buttonCancel).wHint(100).hFill().hGrab().hRight();
        buttonCancel.addSelectionListener(new SelectionAdapter() 
        {
            @Override
            public void widgetSelected(SelectionEvent e) {
            	setReturnValue(false);
            	close();
            }
        });
        buttonOK = new Button(controls, SWT.PUSH);
        buttonOK.setText("OK");  
        GridDataBuilder.create(buttonOK).wHint(100);
        buttonOK.addSelectionListener(new SelectionAdapter() 
        {
            @Override
            public void widgetSelected(SelectionEvent e) {
            	setReturnValue(isDirty());
            	close();
            }
        });
        shell.setDefaultButton(buttonOK);
    }

	@Override
	protected void postCreate() {
	}

	/**
	 * Returns flag set true if selection has changed
	 * @return boolean
	 */
	private boolean isDirty() {
		if (packageTypeSet.size() != originalPackageTypeSet.size())
			return true;
		return !packageTypeSet.containsAll(originalPackageTypeSet);
	}

	/**
	 * Add check button
	 * @param composite Parent composite
	 * @param packageType Button package type
	 * @param isSet Flag set true if package type is selected
	 * @return
	 */
	private Button addCheck(Composite composite, PackageType packageType, boolean isSet) {
        Button check = new Button(composite, SWT.CHECK);
        check.setData(packageType);
        check.setText(packageType.label);
        //check.setToolTipText("");
        check.addSelectionListener(selectionAdapter);
        if (isSet)
            check.setSelection(true);
        return check;
	}

	/**
	 * Returns check button selection handler
	 * @return
	 */
	private SelectionAdapter getSelectionAdapter() {
		return new SelectionAdapter() {

	        @Override
	        public void widgetSelected(SelectionEvent event) {
	            Button button = (Button) event.getSource();
	            boolean selected = button.getSelection();
	            if (selected) {
	            	packageTypeSet.add((PackageType)button.getData());
	            } else {
	            	packageTypeSet.remove((PackageType)button.getData());
	            }
	        }
	    };
	}
	
}
