/*
 * Copyright (C) 2012 The Android Open Source Project
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
package org.eclipse.andmore.android.preferences.ui;

import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

/**
 * This class represents the root preference page (Eclipse Andmore), implemented
 * according User Interface Standards.
 */
public class AndmorePreference extends PreferencePage implements IWorkbenchPreferencePage {

	/**
	 * Default constructor
	 */
	public AndmorePreference() {
		// Empty
	}

	/**
	 * Constructor
	 * 
	 * @param title
	 *            The preference page title
	 */
	public AndmorePreference(String title) {
		super(title);
	}

	/**
	 * Constructor
	 * 
	 * @param title
	 *            The preference page title
	 * @param image
	 *            The prefence page image
	 */
	public AndmorePreference(String title, ImageDescriptor image) {
		super(title, image);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.jface.preference.PreferencePage#createContents(Composite)
	 */
	@Override
	protected Control createContents(Composite parent) {
		noDefaultAndApplyButton();
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.IWorkbenchPreferencePage#init(IWorkbench)
	 */
	@Override
	public void init(IWorkbench workbench) {
		// Empty
	}
}
