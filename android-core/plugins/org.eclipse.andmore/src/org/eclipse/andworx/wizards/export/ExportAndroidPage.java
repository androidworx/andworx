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

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import org.eclipse.andmore.AndmoreAndroidConstants;
import org.eclipse.andmore.AndmoreAndroidPlugin;
import org.eclipse.andmore.internal.build.builders.BaseBuilder;
import org.eclipse.andmore.internal.build.builders.PostCompilerBuilder;
import org.eclipse.andmore.internal.build.builders.PreCompilerBuilder;
import org.eclipse.andmore.internal.editors.IconFactory;
import org.eclipse.andmore.internal.project.AndroidManifestHelper;
import org.eclipse.andmore.internal.project.BaseProjectHelper;
import org.eclipse.andmore.internal.project.ProjectChooserHelper;
import org.eclipse.andmore.internal.project.ProjectChooserHelper.NonLibraryProjectOnlyFilter;
import org.eclipse.andmore.internal.project.ProjectHelper;
import org.eclipse.andworx.build.AndworxBuildPlugin;
import org.eclipse.core.resources.ICommand;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.events.TraverseEvent;
import org.eclipse.swt.events.TraverseListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.actions.WorkspaceModifyOperation;

import com.android.ide.common.xml.ManifestData;
import com.google.common.base.Throwables;

public class ExportAndroidPage extends WizardPage implements SelectionListener, KeyListener, TraverseListener {
	
    private final static String IMG_ERROR = "error.png"; //$NON-NLS-1$
    private final static String IMG_WARNING = "warning.png"; //$NON-NLS-1$
	private static final String EXPORT_TITLE = "Export Release APK";

    /** Completion task created as soon as the project is validated, but only runs when the user clicks "Finish" */
    private Callable<Boolean> commitTask;
    /** Project name */
    private String projectName;
    /** Flag set true if message is being displayed indicating an error */
    private boolean hasMessage;
    /** Project selector */
    private ProjectChooserHelper projectChooserHelper;

    // Controls and images
    private Composite container;
    private Text projectText;
    private Button browseButton;
    // TODO - mirror import page controls
    private Button copyCheckBox;
    private Button refreshButton;
    private Text projectNameText;
    private Text manifestPackageText;
    private Text groupIdText;
    private Text artifactIdText;
    private Text compileSdkText;
    private Text minSdkText;
    private Text targetSdkText;
    private Composite errorComposite;
    private Image error;
    private Image warning;


    /**
     * Construct ExportAndroidPage object
     */
	protected ExportAndroidPage() {
		super("exportAndroidApk");
		projectName = "";
		hasMessage = false;
        setTitle(EXPORT_TITLE);
        setDescription("Select an Android project from which to export the APK");
        // Set page complete flag false so wizard "Finish" button is not enabled at start
        setPageComplete(false);
	}

	/**
	 * Run commit task and set page complete flag to true if successful.
	 * This is a synchronous call but the wizard runs the task in a forked thread to keep the UI thread available for event processing.
	 */
	public void exportProject() {
        setPageComplete(false);
		try {
			setPageComplete(commitTask.call());
		} catch (Exception e) {
			AndmoreAndroidPlugin.logAndPrintError(e, projectName, Throwables.getStackTraceAsString(e));
            MessageDialog.openError(AndmoreAndroidPlugin.getShell(), "Error", e.getMessage());
		}
	}

	/**
	 * Callback from AndroidProjectOpener to notify of an error which caused a task to fail
	 * @param message Error message
	 */
	public void onError(String message) {
		Display.getDefault().asyncExec(new Runnable() {

			@Override
			public void run() {
				setErrorMessage(message);
			}});
	}

    /** 
     * Set selected project name 
     */
	public void setProjectName(String projectName) {
		this.projectName = projectName;
	}
	
	@Override
	public void createControl(Composite parent) {
		projectChooserHelper = new ProjectChooserHelper(parent.getShell(),
                new NonLibraryProjectOnlyFilter());
        container = new Composite(parent, SWT.NULL);
        setControl(container);
        container.setLayout(new GridLayout(3, false));

        Label projectLabel = new Label(container, SWT.NONE);
        GridData gdProjectLabel = new GridData(SWT.FILL, SWT.CENTER, false, false, 3, 1);
        gdProjectLabel.widthHint = 600;
        projectLabel.setLayoutData(gdProjectLabel);
        projectLabel.setText("Select the project to export:");
        new Label(container, SWT.NONE).setText("Project:");

        projectText = new Text(container, SWT.BORDER);
        projectText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        if (projectName != null)
        	projectText.setText(projectName);
        projectText.addModifyListener(new ModifyListener() {
            @Override
            public void modifyText(ModifyEvent e) {
                handleProjectNameChange();
            }
        });

        //projectText.addKeyListener(this);
        //projectText.addTraverseListener(this);

        browseButton = new Button(container, SWT.NONE);
        browseButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
        browseButton.setText("Browse...");
        browseButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                IJavaProject javaProject = projectChooserHelper.chooseJavaProject(
                        projectText.getText().trim(),
                        "Please select a project to export");

                if (javaProject != null) {
                    IProject project = javaProject.getProject();

                    // set the new name in the text field. The modify listener will take
                    // care of updating the status and the ExportWizard object.
                    projectText.setText(project.getName());
                }
            }
        });
	}

	@Override
	public void keyTraversed(TraverseEvent e) {
		
	}

	@Override
	public void keyPressed(KeyEvent e) {
		
	}

	@Override
	public void keyReleased(KeyEvent e) {
		
	}

	@Override
	public void widgetSelected(SelectionEvent e) {
		
	}

	@Override
	public void widgetDefaultSelected(SelectionEvent e) {
		
	}

    /**
     * Checks the parameters for correctness, and update the error message and buttons.
     */
    private void handleProjectNameChange() {
        setPageComplete(false);

        if (errorComposite != null) {
            errorComposite.dispose();
            errorComposite = null;
        }

        // update the wizard with the new project
        //mWizard.setProject(null);

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
                if (validateProject(exportProject)) {
	        		// Get ready to complete import
	        		commitTask = getCommitTask(exportProject);               
	        	}
            } else {
                setErrorMessage(String.format("There is no android project named '%1$s'", text));
            }
        }
    }

	private Callable<Boolean> getCommitTask(IProject project) {
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

	private void reportError(Throwable e) {
    	String message = "Error creating project " + projectName + ": " + Throwables.getRootCause(e).getMessage();
    	onError(message);
    	AndworxBuildPlugin.instance().logAndPrintError(e, projectName, message);
	}
 
    private boolean validateProject(IProject project) {
    	boolean isProjectValid = false;
        // Show description the first time
        setErrorMessage(null);
        setMessage(null);
        setPageComplete(true);
        hasMessage = false;

        // composite parent for the warning/error
        GridLayout gl = null;
        errorComposite = new Composite(container, SWT.NONE);
        errorComposite.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        gl = new GridLayout(2, false);
        gl.marginHeight = gl.marginWidth = 0;
        gl.verticalSpacing *= 3; // more spacing than normal.
        errorComposite.setLayout(gl);

        if (project == null) {
            setErrorMessage("Select project to export.");
            hasMessage = true;
        } else {
            try {
                if (project.hasNature(AndmoreAndroidConstants.NATURE_DEFAULT) == false) {
                    addError(errorComposite, "Project is not an Android project.");
                } else {
                    // check for errors
                    if (ProjectHelper.hasError(project, true))  {
                        addError(errorComposite, "Project has compilation error(s)");
                    }

                    // check the project output
                    IFolder outputIFolder = BaseProjectHelper.getJavaOutputFolder(project);
                    if (outputIFolder == null) {
                        addError(errorComposite,
                                "Unable to get the output folder of the project!");
                    }

                    // project is an android project, we check the debuggable attribute.
                    ManifestData manifestData = AndroidManifestHelper.parseForData(project);
                    Boolean debuggable = null;
                    if (manifestData != null) {
                        debuggable = manifestData.getDebuggable();
                    }

                    if (debuggable != null && debuggable == Boolean.TRUE) {
                        addWarning(errorComposite,
                                "The manifest 'debuggable' attribute is set to true.\n" +
                                "You should set it to false for applications that you release to the public.\n\n" +
                                "Applications with debuggable=true are compiled in debug mode always.");
                    }

                    // check for mapview stuff
                }
            } catch (CoreException e) {
                // unable to access nature
                addError(errorComposite, "Unable to get project nature");
            }
        }

        if (hasMessage == false) {
        	isProjectValid = true;
            Label label = new Label(errorComposite, SWT.NONE);
            GridData gd = new GridData(GridData.FILL_HORIZONTAL);
            gd.horizontalSpan = 2;
            label.setLayoutData(gd);
            label.setText("No errors found. Click Finish.");
        }
        container.layout();
        return isProjectValid;
    }

    /**
     * Adds an error label to a {@link Composite} object.
     * @param parent the Composite parent.
     * @param message the error message.
     */
    private void addError(Composite parent, String message) {
        if (error == null) {
            error = IconFactory.getInstance().getIcon(IMG_ERROR);
        }

        new Label(parent, SWT.NONE).setImage(error);
        Label label = new Label(parent, SWT.NONE);
        label.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        label.setText(message);

        setErrorMessage("Application cannot be exported due to the error(s) below.");
        setPageComplete(false);
        hasMessage = true;
    }

    /**
     * Adds a warning label to a {@link Composite} object.
     * @param parent the Composite parent.
     * @param message the warning message.
     */
    private void addWarning(Composite parent, String message) {
        if (warning == null) {
            warning = IconFactory.getInstance().getIcon(IMG_WARNING);
        }

        new Label(parent, SWT.NONE).setImage(warning);
        Label label = new Label(parent, SWT.NONE);
        label.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        label.setText(message);

        hasMessage = true;
    }


}
