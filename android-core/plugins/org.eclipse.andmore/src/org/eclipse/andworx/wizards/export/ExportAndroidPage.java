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
import org.eclipse.andmore.base.BasePlugin;
import org.eclipse.andmore.base.JavaProjectHelper;
import org.eclipse.andmore.base.resources.PluginResourceProvider;
import org.eclipse.andmore.base.resources.PluginResourceRegistry;
import org.eclipse.andmore.internal.build.builders.BaseBuilder;
import org.eclipse.andmore.internal.build.builders.PostCompilerBuilder;
import org.eclipse.andmore.internal.build.builders.PreCompilerBuilder;
import org.eclipse.andmore.internal.project.ProjectChooserHelper;
import org.eclipse.andmore.internal.project.ProjectChooserHelper.NonLibraryProjectOnlyFilter;
import org.eclipse.andmore.internal.project.ProjectHelper;
import org.eclipse.andworx.build.AndworxBuildPlugin;
import org.eclipse.andworx.build.AndworxContext;
import org.eclipse.andworx.config.ConfigContext;
import org.eclipse.andworx.config.SecurityController;
import org.eclipse.andworx.config.SecurityController.ErrorHandler;
import org.eclipse.andworx.config.SigningConfigField;
import org.eclipse.andworx.control.ErrorControl;
import org.eclipse.andworx.entity.SigningConfigBean;
import org.eclipse.andworx.project.AndroidManifestData;
import org.eclipse.andworx.project.AndroidProjectCollection;
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
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.window.IShellProvider;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.actions.WorkspaceModifyOperation;

import com.android.builder.model.SigningConfig;
import com.android.sdkuilib.ui.GridDataBuilder;
import com.android.sdkuilib.ui.GridLayoutBuilder;
import com.google.common.base.Throwables;

/**
 * Page to export a release APK from an Android project
 */
public class ExportAndroidPage extends WizardPage {
	
	private final static String EXPORT_TITLE = "Export Release APK";
	/** Signing button id */
	private final static int SIGNING_ID = 256;
    
	/** Manages state of all Android projects */
    private final ProjectRegistry projectRegistry;
    /** Performs control functions for security configuration: validate and persist */
    private final SecurityController securityController;
    /** plugin ImageDescriptor provider */
    private final PluginResourceProvider resourceProvider;
    /** JavaProject utilities to hide implementation details */
    private final JavaProjectHelper javaProjectHelper;
    /** Signing config entity bean for APK security */
    private SigningConfigBean signingConfig;
    /** Completion task created as soon as the project is validated, but only runs when the user clicks "Finish" */
    private Callable<Boolean> commitTask;
    /** Current Project or null if none selected */
    private IProject project;
    /** Flag set true if message is being displayed indicating an error */
    private boolean hasMessage;
    /** Project selector */
    private AndroidProjectCollection androidProjects;

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
    private Button defaultButton;

    /**
     * Construct ExportAndroidPage object
     */
	protected ExportAndroidPage(AndworxContext andworxContext) {
		super("exportAndroidApk");
        PluginResourceRegistry resourceRegistry = andworxContext.getPluginResourceRegistry();
        resourceProvider = resourceRegistry.getResourceProvider(BasePlugin.PLUGIN_ID);

        projectRegistry = andworxContext.getProjectRegistry();
        javaProjectHelper = andworxContext.getJavaProjectHelper();
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
		// Note ProjectChooserHelper is only used for logic, not as a control
		androidProjects = new AndroidProjectCollection(new NonLibraryProjectOnlyFilter());
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
        projectCombo = new ProjectChooserHelper.ProjectCombo(androidProjects, groupDetails, project);
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

	/**
	 * Sets initial project. Call proir to createControl()
	 * @param project
	 */
	void posfConstruct(IProject project) {
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
     * Checks the parameters for correctness, and updates the error message and buttons.
     */
    private void handleProjectNameChange() {
    	// Clear variables tied to previous project
    	signingConfig = null;
    	// Prepare to handle next project
    	IProject exportProject = null;
        setPageComplete(false);
        disposeErrorControl();
        // Check the project name
        String text = projectText.getText().trim();
        if (text.length() == 0) {
            setErrorMessage("Select project to export.");
        } else if (text.matches("[a-zA-Z0-9_ \\.-]+") == false) {
            setErrorMessage("Project name contains unsupported characters!");
        } else {
            IJavaProject[] projects = androidProjects.getAndroidProjects();
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
                }
            } else {
                setErrorMessage(String.format("There is no android project named '%1$s'", text));
            }
        }
        project = exportProject;
    }

	/**
	 * Notifies that this dialog's button with the given id has been pressed.
	 * @param buttonId Button id
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
					// Prepare to update status display by disposing of the previous one
					disposeErrorControl();
					if (validateProject())
		        		commitTask = getCommitTask();       
				}
			}
		}
	}

	/**
	 * Returns Callable to run APK build task
	 * @return Callable object
	 */
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

	/**
	 * Returns flag set true if  selected project is valid. Also updates status message
	 * @return boolean
	 */
    private boolean validateProject() {
    	boolean isProjectValid = false;
        setErrorMessage(null);
        setMessage(null);
        // Assume error.
        setPageComplete(false);
        hasMessage = false;
        errorControl = new ErrorControl(statusGroup, resourceProvider);

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
                }
            } catch (CoreException e) {
                // unable to access nature
                addError("Unable to get project nature");
            }
        }

        if (hasMessage) {
        	// When there is an error, dsable configure button
			signingButton.setEnabled(false);
        } else {
        	isProjectValid = true;
        	if (validateSigning()) {
        		errorControl.displyNoErrors();;
				signingButton.setEnabled(true);
        		// Page is valid, so enable Finish button and set focus on it
                setPageComplete(true);
				defaultButton.setFocus();
        	}
        } 
        container.layout();
        return isProjectValid;
    }

    /**
     * Returns flag set true if Signing configuration is valid
     * @return boolean
     */
    private boolean validateSigning() {
    	if (signingConfig == null) { // Lazy initialize Signing configuration object
    		ProjectState projectState = projectRegistry.getProjectState(project);
    		signingConfig = new SigningConfigBean(getSigningConfig(projectState));
    	}
    	// Callback for when valication fails
		ErrorHandler errorHandler = new ErrorHandler() {

			@Override
			public void onVailidationFail(SigningConfigField field, String message) {
				addWarning(message + " - Click Signing.");
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

    /**
     * Dispose error control
     */
	private void disposeErrorControl() {
        if (errorControl != null) {
            errorControl.dispose();
            errorControl = null;
        }
	}

	/**
	 * Refress display of project details
	 */
	private void refresh() {
    	ProjectState projectState = projectRegistry.getProjectState(project);
    	ProjectProfile profile = projectState.getProfile();
    	groupIdText.setText(profile.getIdentity().getGroupId());
    	artifactIdText.setText(profile.getIdentity().getArtifactId());
    	versionText.setText(profile.getIdentity().getVersion());
    }
 
	/**
	 * Returns release Signing configuation
	 * @param projectState Project state
	 * @return SigningConfig object
	 */
    private SigningConfig getSigningConfig(ProjectState projectState) {
        AndworxProject andworxProject = projectState.getAndworxProject();
		return andworxProject.getContext(RELEASE).getVariantConfiguration().getSigningConfig();
    }

    /**
     * Log an error and display it in the build console
     * @param e
     */
	private void reportError(Throwable throwable) {
		String projectName = project != null ? project.getName() : "?";
    	String message = "Error creating project " + projectName + ": " + Throwables.getRootCause(throwable).getMessage();
    	onError(message);
    	AndworxBuildPlugin.instance().logAndPrintError(throwable, projectName, message);
	}
	
	/**
	 * Creates and returns the contents of this dialog's button bar.
	 * @param parent Pparent composite to contain the button bar
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
	 * button presses. The button id is stored as the button's client data. 
	 * Note that the parent's layout is assumed to be a <code>GridLayout</code> 
	 * and the number of columns in this layout is incremented.
	 * </p>
	 * <p>
	 * @param parent Parent composite
	 * @param id Button id
	 * @param label Button text
	 * @return the new button
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

}
