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
package org.eclipse.andmore.internal.preferences;

import java.io.File;
import java.util.List;

import org.eclipse.andmore.internal.sdk.SdkLocationListener;
import org.eclipse.andmore.internal.sdk.SdkTargetControl;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import com.android.sdklib.IAndroidTarget;

/**
 * Field editor for the SDK directory preference that displays SDK details while
 * the actual configuration is delegated to the SDK Selection Wizard.
 * @author Andrew Bowley
 *
 * 01-01-2018
 */
public class SdkLocationFieldEditor extends StringFieldEditor implements SdkLocationListener {

    private static final String PROMPT = "Location:";
    private static final int FIELD_WIDTH_CHARs = 55;

    /** The SdkLocationFieldEditor parent */
    private final AndroidPreferencePage fieldEditor;
	/** Field valid flag, updated when the field value is changed and verified */
	private boolean isValid;
	/** Flag set true if location is both valid and different from workspace value */
	private boolean isChangePending;
	/** The SDK targets and location status control */
    private SdkTargetControl targetControl;
    /** The SDK location field */
    private Text textField;

	/**
     * Construct a SdkLocationFieldEditor object
     * @param fieldEditor The SdkLocationFieldEditor parent
     * @param sdkInstaller Interactive SDK package installer
     */
	public SdkLocationFieldEditor(AndroidPreferencePage fieldEditor) {
		super(AdtPrefs.PREFS_SDK_DIR, PROMPT, FIELD_WIDTH_CHARs, fieldEditor.getParent());
		this.fieldEditor = fieldEditor;
		isValid = false;
 	}

    /**
     * Complete construction after controls created
     */
	public void postCreate() {
		if (targetControl != null)
			targetControl.postCreate();
	}

   public boolean isChangePending() {
		return isChangePending;
	}

	public void setChangePending(boolean isChangePending) {
		this.isChangePending = isChangePending;
	}

/**
     * Informs this field editor's listener, if it has one, about a change to
     * one of this field editor's properties.
     *
     * @param property the field editor property name,
     *   such as <code>VALUE</code> or <code>IS_VALID</code>
     * @param oldLocation the old value object, or <code>null</code>
     * @param newLocation the new value, or <code>null</code>
     */
    @Override
    public void fireValueChanged(String property, Object oldLocation,  Object newLocation) {
    	super.fireValueChanged(property, oldLocation, newLocation);
    }

	@SuppressWarnings("hiding")
	@Override
	public void onSdkLocationChanged(File sdkLocation, boolean isValid, List<IAndroidTarget> targetList) {
		this.isValid = isValid;
		isChangePending = isValid && 
				          (!targetControl.hasWorkspaceSdk() || 
				           !targetControl.getWorkSpaceSdk().equals(sdkLocation.toString()));
    	// Update Apply button enable/disable
    	fieldEditor.checkState();
	}
	
    /**
     * getTextControl
     */
    @Override
    public Text getTextControl(Composite parent) {
        textField = new Text(parent, SWT.SINGLE | SWT.BORDER | SWT.READ_ONLY);
        textField.setFont(parent.getFont());
        textField.addDisposeListener(event -> textField = null);
        return textField;
    }

    @Override
	protected void doLoad() {
        if (textField != null) {
            String value = fieldEditor.getAndroidSdkPreferences().getSdkLocationValue();
            textField.setText(value);
            oldValue = value;
        }
    }

    /**
     * doLoadDefault
     */
    @Override
	protected void doLoadDefault() {
    	// No suitable default
    }

    /**
     * Returns the field editor's value.
     *
     * @return the current value
     */
    @Override
    public String getStringValue() {
        if (textField != null) {
			return textField.getText();
		}
        return fieldEditor.getAndroidSdkPreferences().getSdkLocationValue();
    }

   /**
     * isValid
     */
    @Override
	public boolean isValid() {
        return isValid;
    }
    
    /**
     * doFillIntoGrid
     * Method declared on StringFieldEditor (and FieldEditor).
     */
    @Override
    protected void doFillIntoGrid(Composite parent, int numColumns) {
        Label label = new Label(parent, SWT.NONE);
        GridData gd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        gd.horizontalSpan = numColumns;
        label.setLayoutData(gd);
        label.setText("Targets");
        targetControl = new SdkTargetControl(false /* allow selection */);
        targetControl.createControl(parent, numColumns);
        targetControl.setSdkLocationListener(this);
        super.doFillIntoGrid(parent, numColumns);
    }

    /**
     * doStore - Override to allow execution in background thread
     */
    @Override
	protected void doStore() {
    	final String[] value = new String[1];
        Display.getDefault().syncExec(new Runnable() {
            @Override
            public void run() {
            	value[0] = textField.getText();
            }
        });
        fieldEditor.getAndroidSdkPreferences().setSdkLocation(new File(value[0]));
        fieldEditor.savePreferences();
    }

	/**
	 * Fill target table for given SDK location. Runs in background as SDK packages are loaded and profiled.
	 * @param sdkLocation The SDK location
	 */
	void fillTargetList(File sdkLocation) {
		if (targetControl != null)
			targetControl.fillTargetList(sdkLocation);
	}

	
}
