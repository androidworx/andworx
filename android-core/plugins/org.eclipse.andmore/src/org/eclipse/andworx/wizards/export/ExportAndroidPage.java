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

import static com.android.builder.core.BuilderConstants.RELEASE;
import static org.eclipse.swt.events.SelectionListener.widgetSelectedAdapter;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import org.eclipse.andmore.AndmoreAndroidConstants;
import org.eclipse.andmore.AndmoreAndroidPlugin;
import org.eclipse.andmore.base.BaseContext;
import org.eclipse.andmore.base.JavaProjectHelper;
import org.eclipse.andmore.base.resources.PluginResourceProvider;
import org.eclipse.andmore.internal.build.builders.BaseBuilder;
import org.eclipse.andmore.internal.build.builders.PostCompilerBuilder;
import org.eclipse.andmore.internal.build.builders.PreCompilerBuilder;
import org.eclipse.andmore.internal.editors.IconFactory;
import org.eclipse.andmore.internal.project.ProjectChooserHelper;
import org.eclipse.andmore.internal.project.ProjectChooserHelper.NonLibraryProjectOnlyFilter;
import org.eclipse.andmore.internal.project.ProjectHelper;
import org.eclipse.andworx.build.AndworxBuildPlugin;
import org.eclipse.andworx.build.AndworxContext;
import org.eclipse.andworx.config.ConfigContext;
import org.eclipse.andworx.config.SecurityController;
import org.eclipse.andworx.config.SecurityController.ErrorHandler;
import org.eclipse.andworx.config.SigningConfigField;
import org.eclipse.andworx.control.StatusItemLayoutData;
import org.eclipse.andworx.entity.SigningConfigBean;
import org.eclipse.andworx.project.AndroidManifestData;
import org.eclipse.andworx.project.AndworxProject;
import org.eclipse.andworx.project.ProjectProfile;
import org.eclipse.andworx.registry.ProjectRegistry;
import org.eclipse.andworx.registry.ProjectState;
import org.eclipse.core.resources.ICommand;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.window.IShellProvider;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.events.TraverseEvent;
import org.eclipse.swt.events.TraverseListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.actions.WorkspaceModifyOperation;

import com.android.builder.model.SigningConfig;
import com.android.sdkuilib.ui.GridDataBuilder;
import com.android.sdkuilib.ui.GridLayoutBuilder;
import com.google.common.base.Throwables;

public class ExportAndroidPage extends WizardPage {
	
    private final static String IMG_MATCH = "match"; 
    private final static String IMG_ERROR = "error"; 
    private final static String IMG_WARNING = "warning"; 
	private final static String EXPORT_TITLE = "Export Release APK";
	private final static int SIGNING_ID = 256;

	private class ErrorControl {
		private final Composite composite;
		private final Color backColor;
		
		public ErrorControl(Composite parent) {
			composite = new Composite(parent, SWT.NONE);
	        composite.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
	        backColor = parent.getBackground();
	        GridLayout gl = new GridLayout(1, false);
	        gl.marginHeight = gl.marginWidth = 0;
	        gl.verticalSpacing *= 3; // more spacing than normal.
	        composite.setLayout(gl);
		}
		
		public void displyNoErrors() {
            CLabel label = new CLabel(composite, SWT.NONE);
            GridData gd = new GridData(GridData.FILL_HORIZONTAL);
            label.setLayoutData(gd);
            label.setText("No errors found - Click Finish.");
	        label.setImage(okImage);
            label.setBackground(backColor);
		}
		
		public void setError(String message) {
	        CLabel label = new CLabel(composite, SWT.NONE);
	        label.setText(message);
	        label.setImage(errorImage);
            label.setBackground(backColor);
            GridData gd = new GridData(SWT.FILL, GridData.VERTICAL_ALIGN_BEGINNING, true, true);
            StatusItemLayoutData layoutData = new StatusItemLayoutData(label, SWT.DEFAULT);
            gd.widthHint = layoutData.widthHint;
            gd.heightHint = layoutData.heightHint;
             label.setLayoutData(gd);
		}
		
		public void setWarning(String message) {
	        CLabel label = new CLabel(composite, SWT.NONE);
	        label.setText(message);
	        label.setImage(warnImage);
            label.setBackground(backColor);
            GridData gd = new GridData(SWT.FILL, GridData.VERTICAL_ALIGN_BEGINNING, true, true);
            StatusItemLayoutData layoutData = new StatusItemLayoutData(label, SWT.DEFAULT);
            gd.widthHint = layoutData.widthHint;
            gd.heightHint = layoutData.heightHint;
            gd.grabExcessHorizontalSpace = true;
            label.setLayoutData(gd);
		}
		
		public void dispose() {
			composite.dispose();
		}
	}

	private final BaseContext baseContext;
    private final ProjectRegistry projectRegistry;
    private final SecurityController securityController;
    private final PluginResourceProvider resourceProvider;
    private SigningConfigBean signingConfig;
    /** Completion task created as soon as the project is validated, but only runs when the user clicks "Finish" */
    private Callable<Boolean> commitTask;
    /** Project */
    private IProject project;
    /** Flag set true if message is being displayed indicating an error */
    private boolean hasMessage;
    /** Project selector */
    private ProjectChooserHelper projectChooserHelper;
    /** Make icon usage subject to all images available */
    private boolean useImages;

    // Controls and images
    private Composite container;
    private Text projectText;
    private ProjectChooserHelper.ProjectCombo projectCombo;
    private Text groupIdText;
    private Text artifactIdText;
    private Text versionText;
    private Button signingButton;
    private Group statusGroup;
    private ErrorControl errorControl;
    private Color hiBackColor;
    private Image okImage;
    private Image errorImage;
    private Image warnImage;
    private Button defaultButton;

    /**
     * Construct ExportAndroidPage object
     */
	protected ExportAndroidPage(BaseContext baseContext, AndworxContext andworxContext, PluginResourceProvider resourceProvider) {
		super("exportAndroidApk");
		this.baseContext = baseContext;
		this.resourceProvider = resourceProvider;
        projectRegistry = andworxContext.getProjectRegistry();
        securityController = andworxContext.getSecurityController();
		hasMessage = false;
        setTitle(EXPORT_TITLE);
        setDescription("Select an Android project from which to export the APK");
        // Set page complete flag false so wizard "Finish" button is not enabled at start
        setPageComplete(false);
	}

	/**
	 * Callback from ExportAndroidPage to notify of an error which caused a task to fail
	 * @param message Error message
	 */
	public void onError(String message) {
		Display.getDefault().asyncExec(new Runnable() {

			@Override
			public void run() {
				setErrorMessage(message);
			}});
	}

	@Override
	public void createControl(Composite parent) {
		defaultButton = parent.getShell().getDefaultButton();
		initializeDialogUnits(parent);
    	createImages(parent.getShell());
		// Note ProjectChooserHelper is only used for logic, not as a control
		projectChooserHelper = new ProjectChooserHelper(null,  new NonLibraryProjectOnlyFilter());
        container = new Composite(parent, SWT.NULL);
        setControl(container);
        container.setLayout(new GridLayout(2, false));
        Group groupDetails = new Group(container, SWT.NONE);
        GridDataBuilder.create(groupDetails).hFill().vCenter().hGrab().hSpan(2).wHint(600);
        GridLayoutBuilder.create(groupDetails).columns(2);
        groupDetails.setFont(container.getFont());
        groupDetails.setText("Project details");
        
        Label nameLabel = new Label(groupDetails, SWT.NONE); 
        nameLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
        GridDataBuilder.create(nameLabel).hLeft().fill().vCenter();
        nameLabel.setText("Project name");
        projectCombo = new ProjectChooserHelper.ProjectCombo(projectChooserHelper, groupDetails, project);
        GridDataBuilder.create(projectCombo).fill().hGrab().vCenter();
        Label groupIdLabel = new Label(groupDetails, SWT.NONE); 
        groupIdLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
        groupIdLabel.setText("Group ID");
        groupIdText = new Text(groupDetails, SWT.BORDER | SWT.READ_ONLY);
        groupIdText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        // Grey background to indicate field is non-editable
    	Display display = container.getDisplay();
        groupIdText.setBackground(display.getSystemColor(SWT.COLOR_WIDGET_BACKGROUND));
        Label artifactIdLabel = new Label(groupDetails, SWT.NONE); 
        artifactIdLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
        artifactIdLabel.setText("Artifact ID");
        artifactIdText = new Text(groupDetails, SWT.BORDER | SWT.READ_ONLY);
        artifactIdText.setData(artifactIdLabel);
        artifactIdText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        artifactIdText.setBackground(display.getSystemColor(SWT.COLOR_WIDGET_BACKGROUND));
        Label versionLabel = new Label(groupDetails, SWT.NONE); 
        versionLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
        versionLabel.setText("Version");
        versionText = new Text(groupDetails, SWT.BORDER);
        versionText.setData(versionLabel);
        versionText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));		
        statusGroup = new Group(container, SWT.NONE);
        GridDataBuilder.create(statusGroup).hFill().vBottom().grab().hSpan(2);
        GridLayoutBuilder.create(statusGroup).columns(1);
    	hiBackColor = display.getSystemColor(SWT.COLOR_WHITE);
    	statusGroup.setBackground(hiBackColor);
    	createButtonBar(container);
    	if (project != null) {
            if (validateProject()) {
        		// Get ready to complete import
        		commitTask = getCommitTask();       
        		refresh();
        	}
        }
        SelectionListener listener = new SelectionListener() {

			@Override
			public void widgetSelected(SelectionEvent e) {
				 handleProjectNameChange();
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
				widgetSelected(e);
			}};
		projectCombo.addSelectionListener(listener);
	}

	void setProject(IProject project) {
		if (project != null)
			this.project = project;
	}

	/**
	 * Run commit task and set page complete flag to true if successful.
	 * This is a synchronous call but the wizard runs the task in a forked thread to keep the UI thread available for event processing.
	 */
	void exportProject() {
        setPageComplete(false);
        if (project != null)
			try {
				setPageComplete(commitTask.call());
			} catch (Exception e) {
				AndmoreAndroidPlugin.logAndPrintError(e, project.getName(), Throwables.getStackTraceAsString(e));
	            MessageDialog.openError(AndmoreAndroidPlugin.getShell(), "Error", e.getMessage());
			}
	}

	/**
     * Checks the parameters for correctness, and update the error message and buttons.
     */
    private void handleProjectNameChange() {
        setPageComplete(false);
        disposeErrorControl();
        signingConfig = null;
        // Check the project name
        String text = projectText.getText().trim();
        if (text.length() == 0) {
            setErrorMessage("Select project to export.");
        } else if (text.matches("[a-zA-Z0-9_ \\.-]+") == false) {
            setErrorMessage("Project name contains unsupported characters!");
        } else {
            IJavaProject[] projects = projectChooserHelper.getAndroidProjects(null);
            IProject exportProject = null;
            for (IJavaProject javaProject : projects) {
                if (javaProject.getProject().getName().equals(text)) {
                	exportProject = javaProject.getProject();
                    break;
                }
            }
            if (exportProject != null) {
        		project = exportProject;
                if (validateProject()) {
	        		// Get ready to complete import
	        		commitTask = getCommitTask();     
	        		refresh();	
	        		return;
                }
            } else {
                setErrorMessage(String.format("There is no android project named '%1$s'", text));
            }
        }
        project = null;
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
	private void buttonPressed(int buttonId) {
		if (SIGNING_ID == buttonId) {
			String title = String.format("Configure %s Signing Information", signingConfig.getName());
    		IShellProvider shellProvider = new IShellProvider() {

				@Override
				public Shell getShell() {
					return container.getShell();
				}};
			SigningConfigBean dialogSigningConfig = new SigningConfigBean(signingConfig);
	    	ProjectState projectState = projectRegistry.getProjectState(project);
	    	ProjectProfile profile = projectState.getProfile();
			ConfigContext<SigningConfigBean> signingConfigContext = securityController.configContext(profile, dialogSigningConfig);
			SigningConfigDialog signingConfigDialog = new SigningConfigDialog(shellProvider, title, signingConfigContext, securityController);
			if (signingConfigDialog.open() == IDialogConstants.OK_ID) {
				if (!signingConfigDialog.equals(dialogSigningConfig)) {
					signingConfig = dialogSigningConfig;
					disposeErrorControl();
					if (validateProject())
		        		commitTask = getCommitTask();       
				}
			}
		}
	}

	private Callable<Boolean> getCommitTask() {
		return new Callable<Boolean>() {
			
			@Override
			public Boolean call() throws Exception {
				boolean[] success = new boolean[] {false};
		        // Create a monitored operation to modify project resources
		        WorkspaceModifyOperation op = new WorkspaceModifyOperation() {
		            @Override
		            protected void execute(IProgressMonitor monitor) throws InvocationTargetException {
				        try { 
				        	project.open(monitor);
	        		        ICommand[] commands = project.getDescription().getBuildSpec();
	        		        for (ICommand command : commands) {
	        		            String name = command.getBuilderName();
	        		            if (PreCompilerBuilder.ID.equals(name)) {
	        		                Map<String, String> newArgs = new HashMap<>();
	        		                newArgs.put(BaseBuilder.RELEASE_REQUESTED, "");
	        		                if (command.getArguments() != null) {
	        		                    newArgs.putAll(command.getArguments());
	        		                }
	        		                project.build(IncrementalProjectBuilder.FULL_BUILD,
	        		                        PreCompilerBuilder.ID, newArgs, monitor);
	        		            } else if (PostCompilerBuilder.ID.equals(name)) {
	        		                Map<String, String> newArgs = new HashMap<>();
	        		                newArgs.put(BaseBuilder.RELEASE_REQUESTED, "");
	        		                if (command.getArguments() != null) {
	        		                    newArgs.putAll(command.getArguments());
	        		                }
	        		                project.build(IncrementalProjectBuilder.FULL_BUILD,
	        		                		PostCompilerBuilder.ID, newArgs, monitor);
	        		            }  else {
		        		            project.build(IncrementalProjectBuilder.FULL_BUILD, name,
		        	                        command.getArguments(), monitor);
	        		            }
	        		            success[0] = true;
	        		        }
				        } catch (Exception e) {
				        	reportError(e);
				        }
					}

				};
		        // run the export in an UI runnable.
				try {
					getContainer().run(true /* fork */, true /* cancelable */, op);
				} catch (InvocationTargetException e) {
		        	reportError(e.getCause());
				} catch (InterruptedException e) {
					Thread.interrupted();
				}
		        if (success[0])
		        	setPageComplete(true);
		        return Boolean.valueOf(success[0]);
			}
		};
	}

    private boolean validateProject() {
    	boolean isProjectValid = false;
        // Show description the first timeProjectRegistry projectRegistry
        setErrorMessage(null);
        setMessage(null);
        setPageComplete(true);
        hasMessage = false;
        JavaProjectHelper javaProjectHelper = baseContext.getJavaProjectHelper();
        errorControl = new ErrorControl(statusGroup);

        if (project == null) {
            setErrorMessage("Select project to export.");
            hasMessage = true;
        } else {
            try {
                if (project.hasNature(AndmoreAndroidConstants.NATURE_DEFAULT) == false) {
                    addError("Project is not an Android project.");
                } else {
                    // check for errors
                    if (ProjectHelper.hasError(project, true))  {
                        addError("Project has compilation error(s)");
                    }

                    // check the project output
                    IFolder outputIFolder = javaProjectHelper.getJavaOutputFolder(project);
                    if (outputIFolder == null) {
                        addError("Unable to get the output folder of the project!");
                    }

                    // project is an android project, we check the debuggable attribute.
                    AndworxProject andworxProject = projectRegistry.getProjectState(project).getAndworxProject();
                    AndroidManifestData manifestData = andworxProject.parseManifest();
                    Boolean debuggable = null;
                    if (manifestData != null) {
                        debuggable = manifestData.debuggable;
                    }

                    if (debuggable != null && debuggable == Boolean.TRUE) {
                        addWarning(
                                "The manifest 'debuggable' attribute is set to true.\n" +
                                "You should set it to false for applications that you release to the public.\n\n" +
                                "Applications with debuggable=true are compiled in debug mode always.");
                    }

                    // check for mapview stuff
                }
            } catch (CoreException e) {
                // unable to access nature
                addError("Unable to get project nature");
            }
        }

        if (hasMessage) {
			signingButton.setEnabled(false);
        } else {
        	isProjectValid = true;
        	if (!validateSigning())
                setPageComplete(false);
        	else {	
        		errorControl.displyNoErrors();;
				signingButton.setEnabled(true);
				defaultButton.setFocus();
        	}
        } 
        container.layout();
        return isProjectValid;
    }

    private boolean validateSigning() {
    	if (signingConfig == null) {
    		ProjectState projectState = projectRegistry.getProjectState(project);
    		signingConfig = new SigningConfigBean(getSigningConfig(projectState));
    	}
		ErrorHandler errorHandler = new ErrorHandler() {

			@Override
			public void onVailidationFail(SigningConfigField field, String message) {
				addWarning(message + " - Click Siging.");
				signingButton.setEnabled(true);
				signingButton.setFocus();
			}};
		return (securityController.validate(signingConfig, errorHandler));
	}


    /**
     * Adds an error label to a {@link Composite} object.
     * @param parent the Composite parent.
     * @param message the error message.
     */
    private void addError(String message) {
        errorControl.setError(message);
        setErrorMessage("Application cannot be exported due to the error(s) below.");
        setPageComplete(false);
        hasMessage = true;
    }

    /**
     * Adds a warning label to a {@link Composite} object.
     * @param parent the Composite parent.
     * @param message the warning message.
     */
    private void addWarning(String message) {
    	errorControl.setWarning(message);
        hasMessage = true;
    }

	private void disposeErrorControl() {
        if (errorControl != null) {
            errorControl.dispose();
            errorControl = null;
        }
	}

	private void refresh() {
    	ProjectState projectState = projectRegistry.getProjectState(project);
    	ProjectProfile profile = projectState.getProfile();
    	groupIdText.setText(profile.getIdentity().getGroupId());
    	artifactIdText.setText(profile.getIdentity().getArtifactId());
    	versionText.setText(profile.getIdentity().getVersion());
    }
 
    private SigningConfig getSigningConfig(ProjectState projectState) {
        AndworxProject andworxProject = projectState.getAndworxProject();
		return andworxProject.getContext(RELEASE).getVariantConfiguration().getSigningConfig();
    }
    
	private void reportError(Throwable e) {
		String projectName = project != null ? project.getName() : "?";
    	String message = "Error creating project " + projectName + ": " + Throwables.getRootCause(e).getMessage();
    	onError(message);
    	AndworxBuildPlugin.instance().logAndPrintError(e, projectName, message);
	}
	
	/**
	 * Creates and returns the contents of this dialog's button bar.
	 * <p>
	 * The <code>Dialog</code> implementation of this framework method lays
	 * out a button bar and calls the <code>createButtonsForButtonBar</code>
	 * framework method to populate it. Subclasses may override.
	 * </p>
	 * <p>
	 * The returned control's layout data must be an instance of
	 * <code>GridData</code>.
	 * </p>
	 *
	 * @param parent
	 *            the parent composite to contain the button bar
	 * @return the button bar control
	 */
    private Control createButtonBar(Composite parent) {
 		Group composite = new Group(parent, SWT.NONE);
		// create a layout with spacing and margins appropriate for the font
		// size.
		GridLayout layout = new GridLayout();
		layout.numColumns = 0; // this is incremented by createButton
		layout.makeColumnsEqualWidth = true;
		layout.marginWidth = convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_MARGIN);
		layout.marginHeight = convertVerticalDLUsToPixels(IDialogConstants.VERTICAL_MARGIN);
		layout.horizontalSpacing = convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_SPACING);
		layout.verticalSpacing = convertVerticalDLUsToPixels(IDialogConstants.VERTICAL_SPACING);
		composite.setLayout(layout);
		//GridData data = new GridData(GridData.HORIZONTAL_ALIGN_END
		//		| GridData.VERTICAL_ALIGN_CENTER);
		//composite.setLayoutData(data);
        GridDataBuilder.create(composite).hFill().vCenter().grab();
		composite.setFont(parent.getFont());
		composite.setText("Configure");

		// Add the buttons to the button bar.
		createButtonsForButtonBar(composite);
		return composite;
	}

	private void createButtonsForButtonBar(Composite composite) {
        signingButton = createButton(composite, SIGNING_ID, "Signing");
		signingButton.setEnabled(false);
		signingButton.setToolTipText("Click to change release signing configuration");
	}

	/**
	 * Creates a new button with the given id.
	 * <p>
	 * The <code>Dialog</code> implementation of this framework method creates
	 * a standard push button, registers it for selection events including
	 * button presses, and registers default buttons with its shell. The button
	 * id is stored as the button's client data. If the button id is
	 * <code>IDialogConstants.CANCEL_ID</code>, the new button will be
	 * accessible from <code>getCancelButton()</code>. If the button id is
	 * <code>IDialogConstants.OK_ID</code>, the new button will be accesible
	 * from <code>getOKButton()</code>. Note that the parent's layout is
	 * assumed to be a <code>GridLayout</code> and the number of columns in
	 * this layout is incremented. Subclasses may override.
	 * </p>
	 * <p>
	 * Note: The common button order is: <b>{other buttons}</b>, <b>OK</b>, <b>Cancel</b>.
	 * On some platforms, {@link #initializeBounds()} will move the default button to the right.
	 * </p>
	 *
	 * @param parent
	 *            the parent composite
	 * @param id
	 *            the id of the button (see <code>IDialogConstants.*_ID</code>
	 *            constants for standard dialog button ids)
	 * @param label
	 *            the label from the button
	 * @param defaultButton
	 *            <code>true</code> if the button is to be the default button,
	 *            and <code>false</code> otherwise
	 *
	 * @return the new button
	 *
	 * @see #getCancelButton
	 * @see #getOKButton()
	 */
	private Button createButton(Composite parent, int id, String label) {
		// increment the number of columns in the button bar
		((GridLayout) parent.getLayout()).numColumns++;
		Button button = new Button(parent, SWT.PUSH);
		button.setText(label);
		button.setFont(JFaceResources.getDialogFont());
		button.setData(Integer.valueOf(id));
		button.addSelectionListener(widgetSelectedAdapter(event -> buttonPressed(((Integer) event.widget.getData()).intValue())));
		setButtonLayoutData(button);
		return button;
	}

    /**
     * Create all the images and ensure their disposal
     */
    private void createImages(Shell shell) {
    	useImages = false;
        ImageDescriptor descriptor = resourceProvider.descriptorFromPath("icons/" + IMG_WARNING + ".png");
        if (descriptor != null)
        	warnImage = descriptor.createImage();
        else
        	return;
        descriptor = resourceProvider.descriptorFromPath("icons/" + IMG_ERROR + ".png");
        if (descriptor != null)
        	errorImage = descriptor.createImage();
        else
        	return;
        descriptor = resourceProvider.descriptorFromPath("icons/" + IMG_MATCH + ".png"); 
        if (descriptor != null) {
        	okImage = descriptor.createImage();
        	useImages = (okImage != null) && (errorImage != null) && (warnImage != null);
        }
        shell.addDisposeListener(new DisposeListener(){

			@Override
			public void widgetDisposed(DisposeEvent e) {
		    	disposeImages();
			}});
    }
 
    /**
     * Dispose images
     */
    private void disposeImages() {
    	if (warnImage != null)
    		warnImage.dispose();
    	if (errorImage != null)
    		errorImage.dispose();
    	if (okImage != null)
    		okImage.dispose();
   }
}
