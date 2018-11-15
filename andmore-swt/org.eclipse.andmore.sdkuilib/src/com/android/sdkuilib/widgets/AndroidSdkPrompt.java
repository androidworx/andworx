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

package com.android.sdkuilib.widgets;

import java.io.File;

import org.eclipse.andmore.sdktool.preferences.AndroidSdk;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import com.android.sdkuilib.ui.GridDataBuilder;
import com.android.sdkuilib.ui.GridLayoutBuilder;
import com.android.utils.ILogger;

/**
 * Prompts for name and location of Android SDK installation.
 * A checkbox is provided so the user can indicated this a new installation.
 * Validation for a new installation allows the directory to be created if it does not exist.
 * Ths same validation also checks the location is empty to prevent installing the new SDK
 * over something else in that location.
 * @author Andrew Bowley
 *
 * 14-12-2017
 */
public class AndroidSdkPrompt extends FieldPrompt {

	class ValiationData {
		public boolean isCreateSdk;
		public String location;
	}
	
	/** VaildationJob runs in the background to allow for possible file operations */
	class VaildationJob extends Job {
    	boolean isValid = false;
    	String message = "";

		public VaildationJob() {
			super("Validating Android SDK Location");
		}

		@Override
		protected IStatus run(IProgressMonitor arg0) {
		    try {
		    	final ValiationData validationData = new ValiationData();
		    	Display.getDefault().syncExec(new Runnable(){

					@Override
					public void run() {
						validationData.location = newDirText.getText().trim();
						validationData.isCreateSdk = createSdkCheck.getSelection();
					}});
				if (validationData.location.isEmpty()) {
					// Location is a required field
					message = LOCATION_PROMPT;
				} else {
					File sdkPath = new File(validationData.location);
					if (!sdkPath.exists()) {
						// New installation. Create the location if it does not exest
						if (validationData.isCreateSdk) {
							try {
								if (!sdkPath.mkdirs())
									message = String.format(CREATE_DIRECTORY_FAIL, sdkPath.toString());
								else
									isValid = true;
							} catch (SecurityException e) {
								message = String.format(SECURITY_EXCEPTION, sdkPath.toString());
								logger.error(e, message);
							}
						} else
							// Location does not exist
							message = String.format(DIR_NOT_FOUND, validationData.location);
					}
					else if (!sdkPath.isDirectory()) {
						message = String.format(NOT_DIRECTORY, validationData.location);
					}
					else if (validationData.isCreateSdk && sdkPath.listFiles().length > 0) {
						// New installation location should be empty to ensure no conflicts during set up
						message = String.format(DIRECTORY_NOT_EMPTY, sdkPath.toString());
					} else		
						isValid = true;
				}
				return Status.OK_STATUS;
		    } catch (Exception e) {
				logger.error(e, "Error validating SDK location");
				isValid = false;
				return Status.CANCEL_STATUS;
		    }
	    }

	    boolean confirmValidation() {
	    	if (!isValid) {
				statusText.setText(message);
				newDirText.setFocus();
	    	}
			return isValid;
	    }
	}
	
	static final String SDK_NAME = "SDK name";
	private static final String TITLE = "Configure SDK";
	private static final String LOCATION = "Location *";
	private static final String BLANK = "";
	private static final String LOCATION_PROMPT = "Please enter SDK location";
	private static final String NOT_DIRECTORY = "\"%s\" is not a directory";
	private static final String DIR_NOT_FOUND = "Location \"%s\" not found";
	private static final String SECURITY_EXCEPTION = "Permission to create directory denied. %s";
	private static final String CREATE_DIRECTORY_FAIL = "Failed to create directory: \"%s\"";
	private static final String DIRECTORY_NOT_EMPTY = "Directory is not empty: \"%s\"";
	private static final String NEW_SDK_PROMPT = "Enter new SDK location";
	private static final String EXISTING_SDK_PROMPT = "Enter existing SDK location";
	
	private final ILogger logger;
	private boolean createNewSdk;
	private File sdkLocation;
	private Group directoryGroup;
    private Text newDirText;
    private Button newDirButton;
    private Button createSdkCheck;
    private Text statusText;
    private DirectoryDialog directoryDialog;

	/**
	 * @param parentShell
	 * @param title
	 * @param prompt
	 * @param initialValue
	 * @param dialogImageDescriptor
	 */
	public AndroidSdkPrompt(Shell parentShell, ImageDescriptor dialogImageDescriptor, ILogger logger, boolean createNewSdk) {
		super(parentShell, TITLE, SDK_NAME, BLANK, dialogImageDescriptor);
		this.logger = logger;
		this.createNewSdk = createNewSdk;
	}


	/**
	 * If called after dialog open() returns Window.OK, returns Android SDK specification entered by user.
	 * At other times, returns a default specification provided only for graceful error handling. 
	 * @return AndroidSdk object
	 */
	AndroidSdk getAndroidSdk() {
		if (sdkLocation == null) // This is not expected
			return new AndroidSdk(getDefaultLocation(), "");
		return new AndroidSdk(sdkLocation, getValue());
	}

	boolean getCreateNewSdk() {
		return createNewSdk;
	}
	
    /**
     * Returns flag set true if field value is valid
     * @return boolean
     */
    @Override
    protected boolean isValid()
    {
    	// Run validation in job to prevent possible directory creation in Display thread
    	VaildationJob job = new VaildationJob();
		job.setPriority(Job.INTERACTIVE);
		final boolean[] jobFinished = {false};
		job.addJobChangeListener(new JobChangeAdapter(){
	        @Override
	        public void done(IJobChangeEvent event) {
					jobFinished[0] = true;;
				}});
		job.schedule();
		Shell shell = getShell();
		Display display = shell.getDisplay();
		// Run in idle loop to permit Display thread to remain responsive
		while (!shell.isDisposed() && !jobFinished[0]) {
		    // read the next OS event queue and transfer it to a SWT event
		    if (!display.readAndDispatch())
		    {
		    // if there are currently no other OS event to process
		    // sleep until the next OS event is available
		        display.sleep();
		    }
		}
		if (!job.confirmValidation())
			return false;
		return super.isValid();
    }
 
    /**
     * Save content of all fields because they get disposed as soon as the Dialog closes
     */
    @Override
    protected void saveInput() {
        sdkLocation = new File(newDirText.getText());
        super.saveInput();
    }

	/**
	 * Creates and returns the contents of the upper part of this dialog (above
	 * the button bar).
	 * <p>
	 * The <code>Dialog</code> implementation of this framework method creates
	 * and returns a new <code>Composite</code> with no margins and spacing.
	 * Subclasses should override.
	 * </p>
	 *
	 * @param parent
	 *            The parent composite to contain the dialog area
	 * @return the dialog area control
	 */
    @Override
    protected Control createDialogArea(Composite parent) {
        directoryDialog = new DirectoryDialog(parent.getShell(), SWT.OPEN);
    	Control area = createTitleDialogArea(parent);
        container = createContainer((Composite) area);
        Label directoryLabel = new Label(container, SWT.NONE);
        GridDataBuilder.create(directoryLabel).vCenter();
        directoryLabel.setText(LOCATION);
        directoryGroup = new Group(container, SWT.NONE);
        GridDataBuilder.create(directoryGroup).hSpan(1).hFill().hGrab();
        GridLayoutBuilder.create(directoryGroup).columns(2);
        newDirText = new Text(directoryGroup, SWT.BORDER);
        newDirText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        newDirButton = new Button(directoryGroup, SWT.FLAT);
        newDirButton.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        newDirButton.setText("Browse...");
        newDirButton.addSelectionListener(getBrowseButtonListener());
        new Label(container, SWT.NONE); // Spacer
        createSdkCheck = new Button(container, SWT.CHECK);
        GridDataBuilder.create(createSdkCheck).vTop();
        createSdkCheck.setText("Create new SDK");
        createSdkCheck.setToolTipText("Create directory if it does not exist");
        createSdkCheck.addSelectionListener(new SelectionListener(){

			@Override
			public void widgetSelected(SelectionEvent event) {
				createNewSdk = createSdkCheck.getSelection();
				setLocationPrompt(createNewSdk);
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
				widgetSelected(e);
			}});
        hintWidth = 200;
        createField(container);
        new Label(container, SWT.NONE); // Spacer
        statusText = new Text(container, SWT.NONE);
        GridDataBuilder.create(statusText).hSpan(2).hGrab().hFill();
        Display display = parent.getDisplay();
        statusText.setBackground(display.getSystemColor(SWT.COLOR_INFO_BACKGROUND));
        statusText.setForeground(display.getSystemColor(SWT.COLOR_INFO_FOREGROUND));
        newDirText.addModifyListener(new ModifyListener(){

			@Override
			public void modifyText(ModifyEvent e) {
				statusText.setText("");
			}});
        if (createNewSdk) {
        	createSdkCheck.setSelection(true);
        	setLocationPrompt(true);
        } else {
        	setLocationPrompt(false);
        }
        newDirText.setFocus();
    	return area;
    }

    private void setLocationPrompt(boolean isCreate) {
    	if (isCreate)
    		directoryGroup.setText(NEW_SDK_PROMPT);
    	else
    		directoryGroup.setText(EXISTING_SDK_PROMPT);
    	statusText.setText(BLANK);
    }
    
    /**
     * Returns the default location to install the SDK 
     * @return
     */
    private File getDefaultLocation() {
		return new File(System.getProperty("user.dir") + File.separator + "android_sdk");
	}

    /**
     * Returns Browse button selection listener
     * @return SelectionListener object
     */
    private SelectionListener getBrowseButtonListener() {
		return new SelectionListener(){

			@Override
			public void widgetSelected(SelectionEvent e) {
				statusText.setText("");
	        	String path = newDirText.getText();
	        	if (!path.isEmpty()) {
	                directoryDialog.setFilterPath(path);
	        	}
	            String file = directoryDialog.open();
	            if (file != null) {
	            	 newDirText.setText(file);
	            }
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
				widgetSelected(e);
			}};
	}
}
