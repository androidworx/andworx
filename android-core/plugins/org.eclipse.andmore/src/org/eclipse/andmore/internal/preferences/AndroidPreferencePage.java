/*
 * Copyright (C) 2007 The Android Open Source Project
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

package org.eclipse.andmore.internal.preferences;

import java.io.File;

import org.eclipse.andmore.base.SdkSelectionListener;
import org.eclipse.andmore.base.resources.PluginResourceProvider;
import org.eclipse.andmore.internal.sdk.AdtConsoleSdkLog;
import org.eclipse.andmore.sdktool.SdkUserInterfacePlugin;
import org.eclipse.andmore.sdktool.install.SdkInstaller;
import org.eclipse.andmore.sdktool.preferences.AndroidSdk;
import org.eclipse.andworx.build.AndworxFactory;
import org.eclipse.andworx.sdk.AndroidSdkPreferences;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IDialogPage;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.FieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import com.android.sdkuilib.wizard.SelectSdkWizard;
import com.android.utils.ILogger;

/**
 * This class represents a preference page that is contributed to the
 * Preferences dialog. By subclassing <samp>FieldEditorPreferencePage</samp>,
 * we can use the field support built into JFace that allows us to create a page
 * that is small and knows how to save, restore and apply itself.
 * <p>
 * This page is used to modify preferences only. They are stored in the
 * preference store that belongs to the main plug-in class. That way,
 * preferences can be accessed directly via the preference store.
 */

public class AndroidPreferencePage extends FieldEditorPreferencePage implements
        IWorkbenchPreferencePage {

    private static final String CHANGE_TOOLTIP = "Select existing SDK or install new SDK";
	private static final String CHANGE_BUTTON_TEXT = "Change...";
    private static final String UPDATE_TOOLTIP = "Update/install SDK packages";
	private static final String UPDATE_BUTTON_TEXT = "Update...";
	private static final String DESCRIPTION = "Android SDK";
	private static final String SAVE_CHANGES_TITLE = "Change SDK Location";
	private static final String UPDATE_CHANGES_TITLE = "Update SDK Packages";
	private static final String SDK_LOC_SAVE_ERROR = "Error saving SDK location";
//	private static final String SAVE_CHANGES_PROMPT = "Do you want to save this SDK configuration?";
 
	/** Persistent logger */
	private final ILogger consoleLogger;
    /** Android SDK preferences */
    private final AndroidSdkPreferences androidSdkPreferences;
	/** Hides a bundle which contains image files.  */
    private final PluginResourceProvider resourceProvider;
    /** Field editor for the SDK directory preference */
    private SdkLocationFieldEditor directoryField;
    /** Change button */
    private Button changeButton;
    /** Update button */
    private Button updateButton;
    private Shell shell;

    public AndroidPreferencePage() {
        super(GRID);
        consoleLogger = new AdtConsoleSdkLog();
		androidSdkPreferences = AndworxFactory.instance().getAndroidSdkPreferences();
        setDescription(DESCRIPTION);
		resourceProvider = new PluginResourceProvider(){

			@Override
			public ImageDescriptor descriptorFromPath(String imagePath) {
				return SdkUserInterfacePlugin.instance().getImageDescriptor("icons/" + imagePath);
			}};
		// No default value available, so do not show Defaults button
		// No editing functions on this page (only in other dialogs), so do not show Apply button
		noDefaultAndApplyButton();
	}

    public AndroidSdkPreferences getAndroidSdkPreferences() {
    	return androidSdkPreferences;
    }
    
	/**
	 * Save SDK preference store
	 * @return flag set true if save completed successfully
	 */
	public boolean savePreferences() {
		if (!androidSdkPreferences.save()) {
        	displayErrorMessage(SDK_LOC_SAVE_ERROR);
        	return false;
		}
		return true;
	}
	
    /**
     * propertyChange
     */
    @Override
	public void propertyChange(PropertyChangeEvent event) {
        // This class is set as the property listener for all fields.
    	// As there is only one field, we know any value change is for the directory field
        if (event.getProperty().equals(FieldEditor.VALUE)) {
			String newSdkLocation = (String)event.getNewValue();
			// The newSdkLocation will only be null or empty if an error has occurred
			if ((newSdkLocation != null) && !newSdkLocation.isEmpty())
	            directoryField.fillTargetList(new File(newSdkLocation));
        }
        else
        	super.propertyChange(event);
    }
    
    /**
     * @see IDialogPage#createControl(Composite)
     */
    @Override
	public void createControl(Composite parent){
    	super.createControl(parent);
    	shell = parent.getShell();
    	changeButton.setFocus();
    }

    /**
     * Creates the field editors. Field editors are abstractions of the common
     * GUI blocks needed to manipulate various types of preferences. Each field
     * editor knows how to save and restore itself.
     */
    @Override
    public void createFieldEditors() {
        directoryField = new SdkLocationFieldEditor(this);
        directoryField.postCreate();
        addField(directoryField);
    }

    /**
     * Contributes additional buttons to the given composite.
     * @param parent the button bar
     */
    @Override
    protected void contributeButtons(Composite buttonBar) {
    	changeButton = createButton(buttonBar, CHANGE_BUTTON_TEXT);
		changeButton.setToolTipText(CHANGE_TOOLTIP);
		changeButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				onStartChangeSdk();
			}

		});
    	updateButton = createButton(buttonBar, UPDATE_BUTTON_TEXT);
    	updateButton.setToolTipText(UPDATE_TOOLTIP);
    	// Disable button until SDK status analysed
    	updateButton.setEnabled(false);
    	updateButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				onStartUpdateSdk();
			}

		});
    	GridLayout gridLayout = (GridLayout) buttonBar.getLayout(); 
    	// Increment pareent layout count is required to keep all buttons in alignment
    	gridLayout.numColumns += 2;
    }

    /**
     * init
     * @see org.eclipse.ui.IWorkbenchPreferencePage#init(org.eclipse.ui.IWorkbench)
     */
    @Override
    public void init(IWorkbench workbench) {
    }

    /**
     * dispose
     */
    @Override
    public void dispose() {
        super.dispose();
        // Clean up image resources  
        if (directoryField != null) {
            directoryField.dispose();
            directoryField = null;
        }
    }

    /**
     * Recomputes the page's error state by calling <code>isValid</code> for
     * every field editor.
     */
    @Override
    protected void checkState() {
        super.checkState();
        if (updateButton != null)
	        Display.getDefault().syncExec(new Runnable(){
	
				@Override
				public void run() {
					// Enable Update button only if SDK location is valid
			    	updateButton.setEnabled(directoryField.isValid());
				}});
    }

    /**
     * Handle event Change button hit
     */
	private void onStartChangeSdk() {
		String sdkLocation = directoryField.getStringValue();
		// Save SDK selection in case user does not complete the change
		if (directoryField.isChangePending()) {
			/*
	        boolean doApply = MessageDialog.openQuestion(getShell(), SAVE_CHANGES_TITLE, SAVE_CHANGES_PROMPT);
	        if (doApply) {*/
	        	Job job = new Job(SAVE_CHANGES_TITLE){

					@Override
					protected IStatus run(IProgressMonitor monitor) {
						try {
							performOk();
							doSdkSelect(sdkLocation);
						} catch (Exception e) {
							consoleLogger.error(e, "Error while applying SDK location change");
						}
						return Status.OK_STATUS;
					}};
				job.setPriority(Job.BUILD);
				job.schedule();
	        //}
	        directoryField.setChangePending(false);
		}
		else
			doSdkSelect(sdkLocation);
	}

    /**
     * Handle event Update button hit
     */
	private void onStartUpdateSdk() {
		String sdkLocation = directoryField.getStringValue();
		// Save SDK selection in case user does not complete the change
		if (directoryField.isChangePending()) {
        	Job job = new Job(UPDATE_CHANGES_TITLE){

				@Override
				protected IStatus run(IProgressMonitor monitor) {
					try {
						performOk();
						doSdkUpdate(sdkLocation);
					} catch (Exception e) {
						consoleLogger.error(e, "Error while applying SDK location change");
					}
					return Status.OK_STATUS;
				}};
			job.setPriority(Job.BUILD);
			job.schedule();
	        directoryField.setChangePending(false);
		}
		else
			doSdkUpdate(sdkLocation);
	}

	/**
	 * Select SDK
	 */
	private void doSdkSelect(String sdkLocation) {
		SdkInstaller sdkInstaller = new SdkInstaller(consoleLogger);

		// Show the Select SDK Wizard. All configuration takes place in the wizard.
		SelectSdkWizard selectSdkWizard = new SelectSdkWizard(resourceProvider, sdkInstaller, sdkLocation);
		// Set callback to handle change of selected SDK.
		selectSdkWizard.setSdkSelectionListener(new SdkSelectionListener(){

			@Override
			public void onSdkSelectionChange(File newSdkLocation) {
		        Display.getDefault().syncExec(new Runnable() {
		            @Override
		            public void run() {
		            	// Setting the directory field value will trigger a SDK profile check and update of listed targets
		            	directoryField.setStringValue(newSdkLocation.getPath());
		            }
		        });
 			}

			@Override
			public void onSelectionError(String message) {
				displayErrorMessage(message);
			}

			});
        Display.getDefault().syncExec(new Runnable() {
            @Override
            public void run() {
 	    		WizardDialog dialog = new WizardDialog(getShell(), selectSdkWizard);
                dialog.open();
            }
        });
	}
	
	/**
	 * Update SDK
	 */
	private void doSdkUpdate(String sdkLocation) {
		SdkInstaller sdkInstaller = new SdkInstaller(consoleLogger);
		AndroidSdk androidSdk = new AndroidSdk(new File(sdkLocation), "");
		sdkInstaller.doInstall(androidSdk, getShell(), null, false);
	}
	
	/**
	 * Returns parent composite
	 * @return Composite object
	 */
	public Composite getParent() {
		return getFieldEditorParent();
	}

	/**
	 * Create button on button bar
	 * @param buttonBar The parent composite
	 * @param buttonText The button text
	 * @return Button object
	 */
	private Button createButton(Composite buttonBar, String buttonText) {
    	// Code modelled on how Apply and Defaults buttons are created
		int widthHint = convertHorizontalDLUsToPixels(IDialogConstants.BUTTON_WIDTH);
    	Button newButton = new Button(buttonBar, SWT.PUSH);
    	newButton.setText(buttonText);
		Dialog.applyDialogFont(newButton);
		Point minButtonSize = newButton.computeSize(SWT.DEFAULT, SWT.DEFAULT, true);
		GridData data = new GridData(GridData.HORIZONTAL_ALIGN_FILL);
		data.widthHint = Math.max(widthHint, minButtonSize.x);
		newButton.setLayoutData(data);
		return newButton;
	}

    private void displayErrorMessage(String message) {
		Display.getDefault().syncExec(new Runnable(){

			@Override
			public void run() {
				MessageDialog.openError(shell, SAVE_CHANGES_TITLE, message);
			}});
	}
}
