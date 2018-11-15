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

import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import com.android.sdkuilib.ui.GridDataBuilder;

/**
 * Prompts user to set value of a single field
 * @author Andrew Bowley
 *
 * 13-12-2017
 */
public class FieldPrompt extends TitleAreaDialog {

	/** Field value as text */
	protected String value;
	/** Dialog title */
	private final String title;
	/** Dialog prompt */
	private final String prompt;
	
	protected Text valueText;
	protected int hintWidth;
	private Image dialogImage;
	protected Composite container;

	/**
	 * Construct a FieldPrompt object
	 * @param parentShell Parent shell
	 * @param title Dialog title
	 * @param prompt Dialog prompt
	 * @param initialValue Default value
	 */
	public FieldPrompt(Shell parentShell, String title, String prompt, String initialValue) {
		this(parentShell, title, prompt,initialValue, null);
	}
	
	/**
	 * Construct a FieldPrompt object
	 * @param parentShell Parent shell
	 * @param title Dialog title
	 * @param prompt Dialog prompt
	 * @param initialValue Default value
	 * @param dialogImageDescriptor Image descriptor to show on top right of dialog
	 */
	public FieldPrompt(Shell parentShell, String title, String prompt, String initialValue, ImageDescriptor dialogImageDescriptor) {
		super(parentShell);
		this.title = title;
		this.prompt = prompt;
		value = initialValue != null ? initialValue : "";
		if (dialogImageDescriptor != null) {
			dialogImage = dialogImageDescriptor.createImage();
			parentShell.addDisposeListener(new DisposeListener(){
                // Dispose of image
				@Override
				public void widgetDisposed(DisposeEvent e) {
					dispose();
				}});
			setTitleImage(dialogImage);
		}
	}

	/**
	 * Returns field value
	 * @return String
	 */
	public String getValue() {
		return value;
	}

	/**
	 * Dispose image
	 */
	protected void dispose() {
		if (dialogImage != null)
			dialogImage.dispose();
	}

	/**
	 * Creates this window's widgetry in a new top-level shell.
	 */
    @Override
    public void create() {
        super.create();
        setTitle(title);
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
        Composite area = (Composite) super.createDialogArea(parent);
        container = createContainer(area);
        createField(container);
        return area;
    }

	/**
     * Returns true to make the dialog resizable
     */
    @Override
    protected boolean isResizable() {
        return true;
    }

    /** 
     * Handle OK pressed
     */
    @Override
    protected void okPressed() {
    	if (isValid()) {
	        saveInput();
	        super.okPressed();
    	}
    }

    /**
     * Returns flag set true if field value is valid
     * @return boolean
     */
    protected boolean isValid()
    {
    	return true;
    }
    
    /**
     * Save content of all fields because they get disposed as soon as the Dialog closes
     */
    protected void saveInput() {
        value = valueText.getText();
    }

    protected Composite createContainer(Composite area) {
        container = new Composite(area, SWT.NONE);
        container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        GridLayout layout = new GridLayout(2, false);
        container.setLayout(layout);
        return container;
    }
    
    /**
     * Create input field
     * @param container Group composite
     */
    protected void createField(Composite container) {
        Label promptLabel = new Label(container, SWT.NONE);
        promptLabel.setText(prompt);
        valueText = new Text(container, SWT.BORDER);
        if (hintWidth > 0)
        	GridDataBuilder.create(valueText).wHint(hintWidth);
        else
        	GridDataBuilder.create(valueText).hFill().hGrab();
        valueText.setText(value);
    }

    protected Control createTitleDialogArea(Composite parent) {
        return super.createDialogArea(parent);
    }

}
