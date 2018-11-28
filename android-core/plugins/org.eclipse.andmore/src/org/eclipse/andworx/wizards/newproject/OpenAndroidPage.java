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
package org.eclipse.andworx.wizards.newproject;

import static com.android.SdkConstants.ATTR_NAME;
import static org.eclipse.andworx.project.AndroidConfiguration.VOID_PROJECT_ID;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import org.apache.maven.model.Model;
import org.eclipse.andmore.AndmoreAndroidPlugin;
import org.eclipse.andmore.base.resources.PluginResourceProvider;
import org.eclipse.andmore.internal.editors.layout.gle2.DomUtilities;
import org.eclipse.andmore.internal.wizards.newproject.NewProjectCreator;
import org.eclipse.andmore.internal.wizards.newproject.NewProjectWizardState;
import org.eclipse.andmore.internal.wizards.newproject.WorkingSetGroup;
import org.eclipse.andmore.internal.wizards.newproject.WorkingSetHelper;
import org.eclipse.andworx.AndworxConstants;
import org.eclipse.andworx.config.AndroidConfig;
import org.eclipse.andworx.polyglot.AndroidConfigurationBuilder;
import org.eclipse.andworx.project.AndroidProjectOpener;
import org.eclipse.andworx.project.AndroidWizardListener;
import org.eclipse.andworx.project.ProjectField;
import org.eclipse.andworx.project.ProjectProfile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.events.TraverseEvent;
import org.eclipse.swt.events.TraverseListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkingSet;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.android.annotations.Nullable;
import com.android.builder.model.ProductFlavor;
import com.android.ide.common.xml.ManifestData;
import com.android.sdkuilib.ui.GridDataBuilder;
import com.android.sdkuilib.ui.GridLayoutBuilder;
import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.io.Files;

/**
* Wizard page for import of an existing Android project  
 */
public class OpenAndroidPage extends WizardPage implements SelectionListener, KeyListener, TraverseListener, AndroidWizardListener {

	/** Default group ID if none specified or project name clash to be avoided */
	private static final String ANDWORX_GROUP_ID = "org.eclipse.andworx";

	/** Performs background tasks and notifies this page of results  throught the AndroidWizardListener interface */
	private final AndroidProjectOpener androidProjectOpener;
	/** Image resources */
	private final PluginResourceProvider resourceProvider;
    /** Make icon usage subject to all images available */
    private boolean useImages;
    /** Optional working set */
    private WorkingSetGroup workingSetGroup;
    /** Set of existing project names to check for uniqueness */
    private Set<String> existingProjectNames;
    /** Location of project being imported */
    private File location;
    /** Project name - default format incorporates group ID to assist in uniqueness, but user can change it */
    private String projectName;
    /** Data extracted by parsing the AndroidManifest.xml file */
    private ManifestData manifestData;
    /** Project profile with target and dependencies resolved */
    private ProjectProfile resolvedProjectProfile;
    /** Completion task created as soon as the configuration is available, but, to pick up user changes, only runs when the user clicks "Finish" */
    private Callable<Boolean> commitTask;
    private Map<ProjectField, Text> textFieldMap;

    // Controls and images
    private Text directoryText;
    private Button browseButton;
    private Button copyCheckBox;
    private Button refreshButton;
    private Text projectNameText;
    private Text manifestPackageText;
    private Text groupIdText;
    private Text artifactIdText;
    private Text compileSdkText;
    private Text minSdkText;
    private Text targetSdkText;
    private Image newItemImage;
    private Image errorImage;
    private Image okImage;
    private Label dependenciesStatus;
    private Label dependenciesLabel;
 
    /**
     * Construct OpenAndroidPage object
     * @param resourceProvider Image resources
     */
	public OpenAndroidPage(PluginResourceProvider resourceProvider) {
		super("importAndroidProject");
		this.resourceProvider = resourceProvider;
		androidProjectOpener = new AndroidProjectOpener(this);
        setTitle("Import Android Project");
        setDescription("Select a directory to search for an existing Android project");
        workingSetGroup = new WorkingSetGroup();
        workingSetGroup.setWorkingSets(new IWorkingSet[] {});

        // Record all project names in order to to ensure uniqueness
        IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();
        existingProjectNames = new HashSet<>();
        for (IProject project: workspaceRoot.getProjects())
        	existingProjectNames.add(project.getName());
        // Set page complete flag false so wizard "Finish" button does not appear immediately
        setPageComplete(false);
	}

	/**
	 * Run commit task and set page complete flag to true if successful.
	 * This is a synchronous call but the wizard runs the task in a forked thread to keep the UI thread available for event processing.
	 */
	public void createProject() {
        setPageComplete(false);
		try {
			setPageComplete(commitTask.call());
		} catch (Exception e) {
			AndmoreAndroidPlugin.logAndPrintError(e, projectName, Throwables.getStackTraceAsString(e));
            MessageDialog.openError(AndmoreAndroidPlugin.getShell(), "Error", e.getMessage());
		}
	}

	/**
	 * Callback from first of the open Android Project tasks 
	 * manifestData Data extracted by parsing the AndroidManifest.xml file of the imported project
	 */
	@Override
	public void onManifestParsed(ManifestData manifestData) {
		this.manifestData =  manifestData;
		// Display package from manifest, if available
		String manifestPackage = manifestData.getPackage();
		if (manifestPackage != null)
			Display.getDefault().asyncExec(new Runnable() {
	
				@Override
				public void run() {
					manifestPackageText.setText(manifestPackage);
				}});
	}

	/**
	 * Callback from second of the open Android Project tasks 
	 * @param androidConfigurationBuilder Object cotaining results of parsing build.andworx config file
	 */
	@Override
	public void onConfigParsed(AndroidConfigurationBuilder androidConfigurationBuilder) {
    	setErrorMessage(null);
    	String groupId;
    	// Android config block contains compileSdkVersion
    	AndroidConfig androidConfig = androidConfigurationBuilder.getAndroidConfig();
    	// Model contains group and artifact IDs
    	Model model = androidConfigurationBuilder.getMavenModel();
    	if ((model.getGroupId() != null) && !model.getGroupId().isEmpty())
    	    groupId =  model.getGroupId();
    	else { // This is not expected
    		groupId =  manifestData.getPackage();
    		if ((groupId == null) || groupId.isEmpty())
    			groupId = ANDWORX_GROUP_ID;
    		model.setGroupId(groupId);
    	}
    	// Find first available source of a project name
    	// 1. Existing Eclipse project file
    	// 2. ArtifactId
    	// 3. Root location
    	// Then prepend groupId.
    	// If this name clashes with an existing project name, then append a version
    	String eclipseProjectName = findEclipseProjectName();
    	if ((eclipseProjectName != null) && !existingProjectNames.contains(eclipseProjectName)) {
    		projectName = eclipseProjectName;
	    	if (model.getArtifactId() == null) { 
	    		int pos = projectName.lastIndexOf('.');
	    		model.setArtifactId(pos == -1 ? projectName : projectName.substring(pos + 1));
	    	}
    	}
    	else {
    		if ((model.getArtifactId() != null) && !(model.getArtifactId().isEmpty()))
    		    projectName = model.getArtifactId();
	    	else {
	    		projectName = location.getName();
		    	if (model.getArtifactId() == null) 
		    		model.setArtifactId(projectName);
	    	}
	    	String trialName = groupId + "." + projectName;
	    	int index = 1;
	    	if (existingProjectNames.contains(trialName)) {
	    		trialName = ANDWORX_GROUP_ID + "." + projectName;
	    		if (existingProjectNames.contains(trialName))
		    		do {
		    			trialName = ANDWORX_GROUP_ID + "." + projectName + ".v" + Integer.toString(++index);
		    		} while (existingProjectNames.contains(trialName));
	    	}
	    	projectName = trialName;
    	}
    	String finalGroupId = groupId;
    	String finalArtifactId = model.getArtifactId();
    	ProductFlavor defaultConfig = androidConfig.getDefaultConfig();
		Display.getDefault().asyncExec(new Runnable() {

			@Override
			public void run() {
				projectNameText.setText(projectName);
				groupIdText.setText(finalGroupId);
				artifactIdText.setText(finalArtifactId);
				compileSdkText.setText(androidConfig.getCompileSdkVersion());
				minSdkText.setText(Integer.toString(defaultConfig.getMinSdkVersion().getApiLevel()));
				targetSdkText.setText(Integer.toString(defaultConfig.getTargetSdkVersion().getApiLevel()));
			}});
		// Get ready to complete import
		commitTask = new Callable<Boolean>() {

			@Override
			public Boolean call() throws Exception {
				// Create an identity from the page fields
				Map<ProjectField,String> fieldMap = getFieldMap();
				validate(fieldMap);
				// Create a copy of the resolved project profile with above identity
				ProjectProfile projectProfile = androidProjectOpener.getProjectProfile(fieldMap, resolvedProjectProfile);
				// Create object used to pass new project data to the project creation code
				NewProjectWizardState projectState = new NewProjectWizardState(NewProjectWizardState.Mode.ANY, projectProfile.getIdentity());
	        	projectState.setCopyIntoWorkspace(copyCheckBox.getSelection());
	        	projectState.setWorkingSets(getWorkingSets());
				projectState.setTarget(projectProfile.getTarget());
				AndworxImportedProject importedProject = new AndworxImportedProject();
				importedProject.setProjectName(projectName);
				importedProject.setLocation(location);
				importedProject.setProjectProfile(projectProfile);
				importedProject.setManifestData(manifestData);
				importedProject.setAndroidConfigBuilder(androidConfigurationBuilder);
				// Create entries in the configuration dataabase for the imported project.
				// This must be performed before the new project is created so the new project state can be configured.
			    // The project will be located by name the first time, and then by ID from then onwards to allow for project name change.
			    // Capture project ID so it can be written out to a new project ID file.
			    // Run task to populate the configuration database in forked thread
		        int projectId = androidProjectOpener.persistProjectConfigTask(projectName, projectProfile, androidConfigurationBuilder, getContainer());
				if (projectId == VOID_PROJECT_ID)
					return Boolean.FALSE;
				// Now create the new project in the workspace
		        NewProjectCreator creator = new NewProjectCreator(projectState, getContainer());
		        creator.importAndroidProject(importedProject);
		        // Allow for the option to copy project into the workspace
		        File newFileLocation;
		        if (projectState.isCopyIntoWorkspace()) {
		        	IWorkspace workspace = ResourcesPlugin.getWorkspace();
		        	IProject project = workspace.getRoot().getProject(projectName);
		        	newFileLocation = project.getLocation().makeAbsolute().toFile();
		        } else {
		        	newFileLocation = location;
		        }
		        // Write project ID to disk
		        androidProjectOpener.writeProjectIdFileTask(projectName, projectId, newFileLocation, getContainer());
		        return Boolean.TRUE;
			}};
	}

	/**
	 * Callback from third of the open Android Project tasks 
	 * projectProfile Project profile with dependencies and target platform resolved.
	 * The user is allowed to proceed even if resolution failed
	 * isResolved Flag set true whan resolution completed successfully
	 * message Message to display describing resolution outcome
	 */
	public void onProfileResolved(ProjectProfile projectProfile, boolean isResolved, String message) {
		resolvedProjectProfile = projectProfile;
		Display.getDefault().asyncExec(new Runnable() {

			@Override
			public void run() {
				if (useImages) {
					if (isResolved)
						dependenciesStatus.setImage(okImage); // Green tick
					else
						dependenciesStatus.setImage(errorImage); // Red cross
				}
		        dependenciesLabel.setText(message);
		        // Now set page complete flag 
		        setPageComplete(true);
			}});
	}

	/**
	 * Callback from AndroidProjectOpener to notify of an error which caused a task to fail
	 * @param message Error message
	 */
	@Override
	public void onError(String message) {
		Display.getDefault().asyncExec(new Runnable() {

			@Override
			public void run() {
				setErrorMessage(message);
			}});
	}

	@Override
	public void createControl(Composite parent) {
    	createImages(parent.getShell());
        Composite container = new Composite(parent, SWT.NULL);
        setControl(container);
        container.setLayout(new GridLayout(3, false));

        Label directoryLabel = new Label(container, SWT.NONE);
        GridData gdDirectoryLabel = new GridData(SWT.FILL, SWT.CENTER, false, false, 3, 1);
        gdDirectoryLabel.widthHint = 600;
        directoryLabel.setLayoutData(gdDirectoryLabel);
        directoryLabel.setText("Project Directory:");
        directoryText = new Text(container, SWT.BORDER);
        directoryText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        directoryText.addKeyListener(this);
        directoryText.addTraverseListener(this);

        browseButton = new Button(container, SWT.NONE);
        browseButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
        browseButton.setText("Browse...");
        browseButton.addSelectionListener(this);
        refreshButton = new Button(container, SWT.NONE);
        refreshButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
        refreshButton.setText("Refresh");
        refreshButton.addSelectionListener(this);
        new Label(container, SWT.NONE);
        
        Group groupDetails = new Group(container, SWT.NONE);
        GridDataBuilder.create(groupDetails).hFill().vCenter().hGrab().hSpan(3);
        GridLayoutBuilder.create(groupDetails).columns(2);
        groupDetails.setFont(container.getFont());
        groupDetails.setText("Project details");
        
        Label nameLabel = new Label(groupDetails, SWT.NONE); 
        nameLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
        nameLabel.setText("Project name");
        projectNameText = new Text(groupDetails, SWT.BORDER);
        projectNameText.setData(nameLabel);
        projectNameText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));        
        Label groupIdLabel = new Label(groupDetails, SWT.NONE); 
        groupIdLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
        groupIdLabel.setText("Group ID");
        groupIdText = new Text(groupDetails, SWT.BORDER);
        groupIdText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        Label artifactIdLabel = new Label(groupDetails, SWT.NONE); 
        artifactIdLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
        artifactIdLabel.setText("Artifact ID");
        artifactIdText = new Text(groupDetails, SWT.BORDER);
        artifactIdText.setData(artifactIdLabel);
        artifactIdText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        
        Group groupApis = new Group(container, SWT.NONE);
        GridDataBuilder.create(groupApis).hFill().vCenter().hGrab().hSpan(2);
        GridLayoutBuilder.create(groupApis).columns(6);
        groupApis.setFont(container.getFont());
        groupApis.setText("API details");

        Label compileSdkLabel = new Label(groupApis, SWT.NONE); 
        compileSdkLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
        compileSdkLabel.setText("Compile SDK");
        compileSdkText = new Text(groupApis, SWT.BORDER);
        compileSdkText.setData(compileSdkLabel);
        compileSdkText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));        
        Label minSdkLabel = new Label(groupApis, SWT.NONE); 
        minSdkLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
        minSdkLabel.setText("Min SDK");
        minSdkText = new Text(groupApis, SWT.BORDER);
        minSdkText.setData(minSdkLabel);
        minSdkText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        Label targetSdkLabel = new Label(groupApis, SWT.NONE); 
        targetSdkLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
        targetSdkLabel.setText("Target SDK");
        targetSdkText = new Text(groupApis, SWT.BORDER);
        targetSdkText.setData(targetSdkLabel);
        targetSdkText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        
        Label packageLabel = new Label(groupDetails, SWT.NONE); 
        packageLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
        packageLabel.setText("Package");
        manifestPackageText = new Text(groupDetails, SWT.BORDER | SWT.READ_ONLY);
        manifestPackageText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        Display display = projectNameText.getDisplay();
        // Grey background to indicate field is non-editable
        manifestPackageText.setBackground(display.getSystemColor(SWT.COLOR_WIDGET_BACKGROUND));
        
        Group groupDeps = new Group(container, SWT.NONE);
        GridDataBuilder.create(groupDeps).hFill().vCenter().hGrab().hSpan(2);
        GridLayoutBuilder.create(groupDeps).columns(2);
        groupDeps.setFont(container.getFont());
        groupDeps.setText("Dependency status");
        
        dependenciesStatus = new Label(groupDeps, SWT.NONE);
        dependenciesStatus.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
        // Set image so the label is not size = 0 when layout occurs
        if (newItemImage != null)
        	dependenciesStatus.setImage(newItemImage);
        dependenciesLabel = new Label(groupDeps, SWT.NONE); 
        dependenciesLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

        new Label(container, SWT.NONE);
        copyCheckBox = new Button(container, SWT.CHECK);
        copyCheckBox.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 3, 1));
        copyCheckBox.setText("Copy project into workspace");
        copyCheckBox.addSelectionListener(this);

        Composite group = workingSetGroup.createControl(container);
        group.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 3, 1));
        textFieldMap = new HashMap<>();
        textFieldMap.put(ProjectField.groupId, groupIdText);
        textFieldMap.put(ProjectField.artifactId, artifactIdText);
        textFieldMap.put(ProjectField.projectName, projectNameText);
        textFieldMap.put(ProjectField.compileSdk, compileSdkText);
        textFieldMap.put(ProjectField.minSdk, minSdkText);
        textFieldMap.put(ProjectField.targetSdk, targetSdkText);
	}

    // ---- Implements SelectionListener ----

    @Override
    public void widgetSelected(SelectionEvent e) {
        Object source = e.getSource();
        if (source == browseButton) {
            // Choose directory
            DirectoryDialog dialog = new DirectoryDialog(getShell(), SWT.OPEN);
            String path = directoryText.getText().trim();
            if (path.length() > 0) {
                dialog.setFilterPath(path);
            }
            String file = dialog.open();
            if (file != null) {
            	directoryText.setText(file);
            	onDirectorySet(new File(file));
            }
        } else if (source == refreshButton || source == directoryText) {
            refresh();
        }
        validatePage();
    }

	@Override
    public void widgetDefaultSelected(SelectionEvent e) {
    }

    // ---- KeyListener ----

    @Override
    public void keyPressed(KeyEvent e) {
        if (e.getSource() == directoryText) {
            if (e.keyCode == SWT.CR) {
                refresh();
            }
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
    }

    // ---- TraverseListener ----

    @Override
    public void keyTraversed(TraverseEvent e) {
        // Prevent Return from running through the wizard; return is handled by
        // key listener to refresh project list instead
        if (SWT.TRAVERSE_RETURN == e.detail) {
            e.doit = false;
        }
    }

	/**
	 * Initialize working sets
	 * @param selection Working set selection
	 * @param activePart Current active part
	 */
    void init(IStructuredSelection selection, IWorkbenchPart activePart) {
    	IWorkingSet[] workingSets = WorkingSetHelper.getSelectedWorkingSet(selection, activePart);
    	if (workingSets != null)
    		workingSetGroup.setWorkingSets(workingSets);
    }

	private void validate(Map<ProjectField, String> fieldMap) {
		for (Map.Entry<ProjectField, String> entry: fieldMap.entrySet()) {
			if (!validateNotEmpty(entry.getKey(), entry.getValue()))
				return;
		}
		setMessage(null);
	}
	
	private boolean validateNotEmpty(ProjectField key, String value) {
		if (value.isEmpty()) {
			Text text = textFieldMap.get(key);
			Label label = (Label) text.getData();
			setMessage(label.getText() + " is a required field", IMessageProvider.ERROR );
			text.setFocus();
			return false;
		}
		return true;	
	}

	private Map<ProjectField, String> getFieldMap() {
		Map<ProjectField,String> fieldMap = new HashMap<>();
		for (Map.Entry<ProjectField, Text> entry: textFieldMap.entrySet()) 
			fieldMap.put(entry.getKey(), entry.getValue().getText());
		return fieldMap;
	}
	
   /**
     * Refresh page to update content
     */
    private void refresh() {
    	String directory = directoryText.getText().trim();
    	if (!directory.isEmpty()) {
    		File folder = new File(directory);
    		if (!folder.exists()) {
    			setMessage(directory.toString() + " not found", IMessageProvider.ERROR );
    			directoryText.setFocus();
    		} else if (!folder.isDirectory()) {
    			setMessage(directory.toString() + " is not a directory", IMessageProvider.ERROR );
    			directoryText.setFocus();
    		} else
    			onDirectorySet(folder);
    	}
    }

    /** 
     * Validate project folder
     * @param folder
     */
    private void onDirectorySet(File folder) {
    	File andworxBuildFile = new File(folder, AndworxConstants.FN_BUILD_ANDWORX);
    	if (!andworxBuildFile.exists())
    		setErrorMessage("Project file \"" + AndworxConstants.FN_BUILD_ANDWORX + "\" not found");
    	else {
    		location = folder;
    		androidProjectOpener.runOpenTasks(andworxBuildFile);
        	setErrorMessage(null);
    	}
	}

    /**
     * Validate content
     */
    private void validatePage() {
    }
    
    /**
     * Returns the working sets to which the new project should be added.
     *
     * @return the selected working sets to which the new project should be added
     */
    private IWorkingSet[] getWorkingSets() {
        return workingSetGroup.getSelectedWorkingSets();
    }

    /** 
     * Extract project name from project file
     * @return prject name or null if name not found
     */
    @Nullable
    private String findEclipseProjectName() {
        File projectFile = new File(location, ".project");
        if (projectFile.exists()) {
            String xml;
            try {
                xml = Files.asCharSource(projectFile, Charsets.UTF_8).read();
                Document doc = DomUtilities.parseDocument(xml, false);
                if (doc != null) {
                    NodeList names = doc.getElementsByTagName(ATTR_NAME);
                    if (names.getLength() >= 1) {
                        Node nameElement = names.item(0);
                        String name = nameElement.getTextContent().trim();
                        if (!name.isEmpty()) {
                            return name;
                        }
                    }
                }	
            } catch (IOException e) {
                // pass: don't attempt to read project name; must be some sort of unrelated
                // file with the same name, perhaps from a different editor or IDE
            }
        }
        return null;
    }

    /**
     * Create all the images and ensure their disposal
     */
    private void createImages(Shell shell) {
    	useImages = false;
        ImageDescriptor descriptor = resourceProvider.descriptorFromPath("nopkg_icon_16.png");
        if (descriptor != null)
        	newItemImage = descriptor.createImage();
        else
        	return;
        descriptor = resourceProvider.descriptorFromPath("reject_icon16.png");
        if (descriptor != null)
        	errorImage = descriptor.createImage();
        else
        	return;
        descriptor = resourceProvider.descriptorFromPath("status_ok_16.png"); 
        if (descriptor != null) {
        	okImage = descriptor.createImage();
        	useImages = (okImage != null) && (errorImage != null) && (newItemImage != null);
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
    	if (newItemImage != null)
    		newItemImage.dispose();
    	if (errorImage != null)
    		errorImage.dispose();
    	if (okImage != null)
    		okImage.dispose();
    }

}
