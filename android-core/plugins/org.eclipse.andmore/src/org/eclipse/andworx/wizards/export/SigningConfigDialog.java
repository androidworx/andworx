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

import org.eclipse.andworx.config.ConfigContext;
import org.eclipse.andworx.config.SecurityController;
import org.eclipse.andworx.config.SigningConfigField;
import org.eclipse.andworx.control.FileFilter;
import org.eclipse.andworx.entity.SigningConfigBean;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.window.IShellProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.FontMetrics;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import com.android.sdkuilib.ui.GridDataBuilder;
import com.android.sdkuilib.ui.GridLayoutBuilder;

public class SigningConfigDialog extends Dialog {
	
    public class FileSelectionControl
	{
	    /** File filter */
	    private final FileFilter fileFilter;
	    /** Parent shell */
	    private final Shell shell;
	 
	    /**
	     * Construct FileSelectionControl object
	     */
	    public FileSelectionControl(Shell shell)
	    {
	    	this.shell = shell;
	        fileFilter = new FileFilter();
	    }

	    /**
	     * Returns file filter
	     * @return FileFilter object
	     */
	    public FileFilter getFileFilter()
	    {
	        return fileFilter;
	    }

	    /**
	     * Open dialog with given prompt and return selected file path
	     * @param prompt Prompt to user
	     * @return a string describing the absolute path of the first selected file,
	     *          or null if the dialog was cancelled or an error occurred
	     */
	    public String getFilePath(String prompt)
	    {
	        // File standard dialog
	    	FileDialog fileDialog = new FileDialog(shell);
	        // Set the text
	        fileDialog.setText(prompt);
	        // Set filter on .txt files
	        fileDialog.setFilterExtensions(fileFilter.getExtensions());
	        // Put in a readable name for the filter
	        fileDialog.setFilterNames(fileFilter.getNames());
	        // Open Dialog and save result of selection
	        return fileDialog.open();
	    }
	}

    /** Dialog title */
	private final String title;
    /** Signing Config Bean persistence context */
    private final ConfigContext<SigningConfigBean> signingConfigContext;
    private SigningConfigBean signingConfig;
    private final SecurityController securityController;
	private FileSelectionControl fileSelectionControl;

    Text keystoreText;
    Combo keystoreTypeList;
    Text storePasswordText;
    Text keyPasswordText;
    Text keyAliasText;
    Button v1Check;
    Button v2Check;
    Group statusGroup;
    Text errorText;
    Button apply;
    Button browseButton;

    SecurityController.ErrorHandler errorHandler = new SecurityController.ErrorHandler() {

		@Override
		public void onVailidationFail(SigningConfigField field, String message) {
	        statusGroup.setVisible(true);
			errorText.setText(message);
			switch (field) {
			case storeFile: keystoreText.setFocus(); break;
			case storePassword: storePasswordText.setFocus(); break;
			case keyAlias: keyAliasText.setFocus(); break;
			case keyPassword: keyPasswordText.setFocus(); break;
			case storeType: keystoreTypeList.setFocus(); break;
			case v1SigningEnabled: v1Check.setFocus(); break;
			case v2SigningEnabled: v2Check.setFocus(); break;
			}
			
		}};
		
    /** Keystore file browse button cliked */
    SelectionAdapter browseListener = new SelectionAdapter() {
        public void widgetSelected(SelectionEvent event)
        {
            String file = fileSelectionControl.getFilePath("Select Keystore");
            if (file != null)
                keystoreText.setText(file);
        }
        public void widgetDefaultSelected(SelectionEvent event){
            widgetSelected(event);
        }
    };

    /** Keystore type selection clicked */
    SelectionListener keystoreTypeListener = new SelectionListener() {
        public void widgetSelected(SelectionEvent event)
        {
        	configChanged();
        }
        
        public void widgetDefaultSelected(SelectionEvent event){
            widgetSelected(event);
        }
    };

    /** Apply button clicked */
    SelectionAdapter applyListener = new SelectionAdapter() {
        
        public void widgetSelected(SelectionEvent e) 
        {
        	apply();
        }
    };

    /** Enable Apply button when key pressed */
    KeyListener changeListener = new KeyListener(){

        @Override
        public void keyPressed(KeyEvent e)
        {
        	configChanged();
        }

        @Override
        public void keyReleased(KeyEvent e)
        {
        }};


	public SigningConfigDialog(IShellProvider parentShell, String title, ConfigContext<SigningConfigBean> signingConfigContext, SecurityController securityController) {
		super(parentShell);
		this.title = title;
		this.signingConfigContext = signingConfigContext;
		this.securityController = securityController;
		signingConfig = signingConfigContext.getBean();
	}


	/**
     * Delegate createDialogArea()
     * @see org.eclipse.jface.dialogs.Dialog#createDialogArea(org.eclipse.swt.widgets.Composite)
     */
    @Override
    protected Control createDialogArea(Composite parent) 
    {   
        Composite top = new Composite(parent, SWT.NONE);
        GridLayout topLayout = new GridLayout();
        topLayout.marginHeight = 0;
        topLayout.marginWidth = 0;
        topLayout.verticalSpacing = 0;
        top.setLayout(topLayout);
        top.setLayoutData(new GridData(GridData.FILL_BOTH));
        Composite composite = new Composite(top, SWT.NONE);
        GridLayout layout = new GridLayout(1, false);
        composite.setLayout(layout);
		fileSelectionControl = new FileSelectionControl(parent.getShell());
        // Customize control to select keystore file on file system
        FileFilter fileFilter = fileSelectionControl.getFileFilter();
        String defaultStoreType = signingConfig.getStoreType().toUpperCase();
        if ("PKCS12".equals(defaultStoreType))
        	fileFilter.addName("PKCS12", "pfx", "p12");
        else
        	fileFilter.addName(defaultStoreType, defaultStoreType.toLowerCase());
        if (!"JKS".equals(defaultStoreType))
        	fileFilter.addName("JKS", "jks");
        if (!"JCEKS".equals(defaultStoreType))
        	fileFilter.addName("JCEKS", "jceks");
        if (!"PKCS12".equals(defaultStoreType))
        	fileFilter.addName("PKCS12", "pfx", "p12");
        fileFilter.addName("Any", "*");
        // Create text, checkbox and label controls
        createBody(composite, parent);
        return top;
    }

    /**
     * Set Dialog title
     * @see org.eclipse.jface.window.Window#configureShell(org.eclipse.swt.widgets.Shell)
     */
    @Override
    protected void configureShell(Shell newShell) 
    {
    	super.configureShell(newShell);
    	newShell.setText(title);
    }

	protected boolean apply() {
	    SigningConfigBean toValidate = new SigningConfigBean(signingConfig.getName());
    	if (validate(toValidate))  {
    		signingConfigContext.update(toValidate);
    		securityController.persist();
   		    return true;
    	}
    	return false;
	}

    protected boolean validate(SigningConfigBean toValidate) {
	    toValidate.setKeyAlias(keyAliasText.getText());
	    toValidate.setKeyPassword(keyPasswordText.getText());
	    toValidate.setStoreFile(keystoreText.getText());
	    toValidate.setStorePassword(storePasswordText.getText());
	    toValidate.setStoreType(keystoreTypeList.getText());
	    toValidate.setV1SigningEnabled(v1Check.getSelection());
	    toValidate.setV2SigningEnabled(v2Check.getSelection());
    	return securityController.validate(toValidate, errorHandler);
	}

	/**
     * Create Text, Checkbox and Label controls
     * @param composite Layout for controls
     * @param parent Parent composite of View
     */
    protected void createBody(Composite composite, Composite parent)
    {
    	Group keystoreGroup = new Group(composite, SWT.SHADOW_NONE);
    	keystoreGroup.setText("Key Store");
        GridDataBuilder.create(keystoreGroup).hFill().vCenter().hGrab();
        GridLayoutBuilder.create(keystoreGroup).columns(3).margins(5);
        Label keystoreLabel = new Label(keystoreGroup, SWT.NONE);
        keystoreLabel.setText("File:");
        keystoreLabel.setLayoutData(new GridData(SWT.END, SWT.CENTER, false, false));
        keystoreText = new Text(keystoreGroup, SWT.BORDER);
        keystoreText.setText(signingConfig.getStoreFileValue());
        keystoreText.addKeyListener(changeListener);
        GridData gridData1 = new GridData(SWT.FILL, SWT.FILL, true, false);
        FontMetrics fontMetrics = getFontMetrics(parent);
        gridData1.widthHint = fontMetrics.getHeight() * 20;
        keystoreText.setLayoutData(gridData1);
        // Add button to browse file system for keystore file
        browseButton = new Button(keystoreGroup, SWT.PUSH);
        browseButton.setText("Browse ...");
        browseButton.addSelectionListener(browseListener);
        GridData gridData2 = new GridData(SWT.BEGINNING, SWT.FILL, true, false);
        gridData2.widthHint = fontMetrics.getHeight() * 8;
        browseButton.setLayoutData(gridData2);
        Label keystoreTypeLabel = new Label(keystoreGroup, SWT.NONE);
        keystoreTypeLabel.setText("Type:");
        keystoreTypeLabel.setLayoutData(new GridData(SWT.END, SWT.CENTER, false, false));
        keystoreTypeList = new Combo(keystoreGroup, SWT.DROP_DOWN | SWT.READ_ONLY);
        GridData gridData3 = new GridData(SWT.BEGINNING, SWT.FILL, true, false, 2, 1);
        gridData3.widthHint = fontMetrics.getHeight() * 6;
        keystoreTypeList.setLayoutData(gridData3);
        int index = -1, select = -1;
        String defaultStoreType = signingConfig.getStoreType().toUpperCase();
        for (String type: SecurityController.KEYSTORE_TYPES) {
            keystoreTypeList.add(type);
            ++index;
           if (type.equals(defaultStoreType))
            	select = index;
        }
        if (select == -1) {
            keystoreTypeList.add(defaultStoreType);
            select = index;
        }
        keystoreTypeList.select(select);
        keystoreTypeList.addSelectionListener(keystoreTypeListener);
        Label storePasswordLabel = new Label(keystoreGroup, SWT.NONE);
        storePasswordLabel.setText("Password:");
        storePasswordLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        storePasswordText = new Text(keystoreGroup, SWT.BORDER | SWT.PASSWORD);
        storePasswordText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 2, 1));
        String password = signingConfig.getStorePassword();
        if (!password.isEmpty())
        	storePasswordText.setText(password);
        storePasswordText.addKeyListener(changeListener);
        new Label(composite, SWT.NONE);
      	Group keyGroup = new Group(composite, SWT.SHADOW_NONE);
    	keyGroup.setText("Key");
        GridDataBuilder.create(keyGroup).hFill().vCenter().hGrab();
        GridLayoutBuilder.create(keyGroup).columns(2).margins(5);
        Label keyAliasLabel = new Label(keyGroup, SWT.NONE);
        keyAliasLabel.setText("Alias:");
        keyAliasLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        keyAliasText = new Text(keyGroup, SWT.BORDER | SWT.PASSWORD);
        keyAliasText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1));
        keyAliasText.setText(signingConfig.getKeyAlias());
        keyAliasText.addKeyListener(changeListener);
        Label keyPasswordLabel = new Label(keyGroup, SWT.NONE);
        keyPasswordLabel.setText("Password:");
        keyPasswordLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        keyPasswordText = new Text(keyGroup, SWT.BORDER | SWT.PASSWORD);
        keyPasswordText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1));
        password = signingConfig.getKeyPassword();
        if (!password.isEmpty())
        	keyPasswordText.setText(password);
        keyPasswordText.addKeyListener(changeListener);
        new Label(composite, SWT.NONE);
      	Group signingGroup = new Group(composite, SWT.SHADOW_NONE);
      	signingGroup.setText("Signing");
        GridDataBuilder.create(signingGroup).hFill().vCenter().hGrab();
        GridLayoutBuilder.create(signingGroup).columns(2).margins(5);
        v1Check = new Button(signingGroup, SWT.CHECK);
        GridDataBuilder.create(v1Check).vTop();
        v1Check.setText("V1 Enabled");
        v1Check.setToolTipText("Version 1 Signing enabled");
        v1Check.addSelectionListener(new SelectionAdapter() 
        {
            @Override
            public void widgetSelected(SelectionEvent event) {
            	configChanged();
            }
        });
        v1Check.setSelection(signingConfig.isV1SigningEnabled());
        v2Check = new Button(signingGroup, SWT.CHECK);
        GridDataBuilder.create(v2Check).vTop();
        v2Check.setText("V2 Enabled");
        v2Check.setToolTipText("Version 2 Signing enabled");
        v2Check.addSelectionListener(new SelectionAdapter() 
        {
            @Override
            public void widgetSelected(SelectionEvent event) {
            	configChanged();
            }
        });
        v2Check.setSelection(signingConfig.isV2SigningEnabled());
      	statusGroup = new Group(composite, SWT.SHADOW_NONE);
        GridDataBuilder.create(statusGroup).hFill().vCenter().hGrab();
        GridLayoutBuilder.create(statusGroup).margins(5);
        errorText = new Text(statusGroup, SWT.READ_ONLY);
        errorText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1));
        statusGroup.setVisible(false);
    }

	/**
     * Create push buttons
     * @param controlFactory SWT widget factory
     * @param parent Layout for button bar
    */
    @Override
    protected void createButtonsForButtonBar(Composite buttonBar)
    {
        apply = createButton(buttonBar,
                IDialogConstants.CLIENT_ID + 1, 
                "Apply",                 
                false);
        apply.addSelectionListener(applyListener);
        apply.setEnabled(false);
        createButton(buttonBar, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL, true);
        createButton(buttonBar, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
    }
    
	/**
	 * Notifies that this dialog's button with the given id has been pressed.
	 * <p>
	 * The <code>Dialog</code> implementation of this framework method calls
	 * <code>okPressed</code> if the ok button is the pressed, and
	 * <code>cancelPressed</code> if the cancel button is the pressed. All
	 * other button presses are ignored. Subclasses may override to handle other
	 * buttons, but should call <code>super.buttonPressed</code> if the
	 * default handling of the ok and cancel buttons is desired.
	 * </p>
	 *
	 * @param buttonId
	 *            the id of the button that was pressed (see
	 *            <code>IDialogConstants.*_ID</code> constants)
	 */
    @Override
	protected void buttonPressed(int buttonId) {
		if (IDialogConstants.OK_ID == buttonId) {
			if (apply())
				okPressed();
		} else if (IDialogConstants.CANCEL_ID == buttonId) {
			cancelPressed();
		}
	}
    
    protected void configChanged() {
        statusGroup.setVisible(false);
        apply.setEnabled(true);
	}

   /**
     * Returns a FontMetrics which contains information about the font currently 
     * being used by the receiver to draw and measure text.
     * @param parent Parent composite
     * @return font metrics for the receiver's font
     */
    private FontMetrics getFontMetrics(Composite parent)
    {
        GC gc = new GC(parent);
        gc.setFont(parent.getFont());
        FontMetrics fontMetrics = gc.getFontMetrics();
        gc.dispose();
        return fontMetrics;
    }

}
