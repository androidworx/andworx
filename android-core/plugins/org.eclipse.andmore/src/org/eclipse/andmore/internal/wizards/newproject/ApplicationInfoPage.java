/*
 * Copyright (C) 2011 The Android Open Source Project
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
package org.eclipse.andmore.internal.wizards.newproject;

import java.io.File;
import java.io.FileFilter;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;

import org.eclipse.andmore.AndmoreAndroidConstants;
import org.eclipse.andmore.AndmoreAndroidPlugin;
import org.eclipse.andmore.internal.sdk.Sdk.ITargetChangeListener;
import org.eclipse.andmore.internal.wizards.newproject.NewProjectWizardState.Mode;
import org.eclipse.andworx.build.AndworxFactory;
import org.eclipse.andworx.context.AndroidEnvironment;
import org.eclipse.core.filesystem.URIUtil;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.JavaConventions;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import com.android.SdkConstants;
import com.android.sdklib.IAndroidTarget;

/** Page where you choose the application name, activity name, and optional test project info */
public class ApplicationInfoPage extends WizardPage implements SelectionListener, ModifyListener,
        ITargetChangeListener {
    private static final String JDK_STANDARD = "8"; //$NON-NLS-1$
    private final static String DUMMY_PACKAGE = "your.package.namespace";

    /** Suffix added by default to activity names */
    static final String ACTIVITY_NAME_SUFFIX = "Activity"; //$NON-NLS-1$

    private final NewProjectWizardState mValues;

    private Text mApplicationText;
    private Text mPackageText;
    private Text mActivityText;
    private Button mCreateActivityCheckbox;
    private Combo mSdkCombo;

    private boolean mIgnore;
    private Button mCreateTestCheckbox;
    private Text mTestProjectNameText;
    private Text mTestApplicationText;
    private Text mTestPackageText;
    private Label mTestProjectNameLabel;
    private Label mTestApplicationLabel;
    private Label mTestPackageLabel;

    /**
     * Create the wizard.
     */
    ApplicationInfoPage(NewProjectWizardState values) {
        super("appInfo"); //$NON-NLS-1$
        mValues = values;

        setTitle("Application Info");
        setDescription("Configure the new Android Project");
        AndmoreAndroidPlugin.getDefault().addTargetListener(this);
    }

    /**
     * Create contents of the wizard.
     */
    @Override
    @SuppressWarnings("unused") // Eclipse marks SWT constructors with side effects as unused
    public void createControl(Composite parent) {
        Composite container = new Composite(parent, SWT.NULL);
        container.setLayout(new GridLayout(2, false));

        Label applicationLabel = new Label(container, SWT.NONE);
        applicationLabel.setText("Application Name:");

        mApplicationText = new Text(container, SWT.BORDER);
        mApplicationText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        mApplicationText.addModifyListener(this);

        Label packageLabel = new Label(container, SWT.NONE);
        packageLabel.setText("Package Name:");

        mPackageText = new Text(container, SWT.BORDER);
        mPackageText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        mPackageText.addModifyListener(this);

        if (mValues.getMode() != Mode.TEST) {
            mCreateActivityCheckbox = new Button(container, SWT.CHECK);
            mCreateActivityCheckbox.setText("Create Activity:");
            mCreateActivityCheckbox.addSelectionListener(this);

            mActivityText = new Text(container, SWT.BORDER);
            mActivityText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
            mActivityText.addModifyListener(this);
        }

        Label minSdkLabel = new Label(container, SWT.NONE);
        minSdkLabel.setText("Minimum SDK:");

        mSdkCombo = new Combo(container, SWT.NONE);
        GridData gdSdkCombo = new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1);
        gdSdkCombo.widthHint = 200;
        mSdkCombo.setLayoutData(gdSdkCombo);
        mSdkCombo.addSelectionListener(this);
        mSdkCombo.addModifyListener(this);

        onSdkLoaded();

        setControl(container);
        new Label(container, SWT.NONE);
        new Label(container, SWT.NONE);

        mCreateTestCheckbox = new Button(container, SWT.CHECK);
        mCreateTestCheckbox.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));
        mCreateTestCheckbox.setText("Create a Test Project");
        mCreateTestCheckbox.addSelectionListener(this);

        mTestProjectNameLabel = new Label(container, SWT.NONE);
        mTestProjectNameLabel.setText("Test Project Name:");

        mTestProjectNameText = new Text(container, SWT.BORDER);
        mTestProjectNameText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        mTestProjectNameText.addModifyListener(this);

        mTestApplicationLabel = new Label(container, SWT.NONE);
        mTestApplicationLabel.setText("Test Application:");

        mTestApplicationText = new Text(container, SWT.BORDER);
        mTestApplicationText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        mTestApplicationText.addModifyListener(this);

        mTestPackageLabel = new Label(container, SWT.NONE);
        mTestPackageLabel.setText("Test Package:");

        mTestPackageText = new Text(container, SWT.BORDER);
        mTestPackageText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        mTestPackageText.addModifyListener(this);
    }

    /** Controls whether the options for creating a paired test project should be shown */
    private void showTestOptions(boolean visible) {
        if (mValues.getMode() == Mode.SAMPLE) {
            visible = false;
        }

        mCreateTestCheckbox.setVisible(visible);
        mTestProjectNameLabel.setVisible(visible);
        mTestProjectNameText.setVisible(visible);
        mTestApplicationLabel.setVisible(visible);
        mTestApplicationText.setVisible(visible);
        mTestPackageLabel.setVisible(visible);
        mTestPackageText.setVisible(visible);
    }

    /** Controls whether the options for creating a paired test project should be enabled */
    private void enableTestOptions(boolean enabled) {
        mTestProjectNameLabel.setEnabled(enabled);
        mTestProjectNameText.setEnabled(enabled);
        mTestApplicationLabel.setEnabled(enabled);
        mTestApplicationText.setEnabled(enabled);
        mTestPackageLabel.setEnabled(enabled);
        mTestPackageText.setEnabled(enabled);
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);

        if (visible) {
            try {
                mIgnore = true;
                if (mValues.getApplicationName() != null) {
                    mApplicationText.setText(mValues.getApplicationName());
                }
                if (mValues.getPackageName() != null) {
                    mPackageText.setText(mValues.getTestPackageName());
                } else {
                    mPackageText.setText(DUMMY_PACKAGE);
                }

                if (mValues.getMode() != Mode.TEST) {
                    mCreateActivityCheckbox.setSelection(mValues.createActivity());
                    mActivityText.setEnabled(mValues.createActivity());
                    if (mValues.getActivityName() != null) {
                        mActivityText.setText(mValues.getActivityName());
                    }
                }
                if (mValues.getMinSdk() != null && mValues.getMinSdk().length() > 0) {
                    mSdkCombo.setText(mValues.getMinSdk());
                }

                showTestOptions(mValues.getMode() == Mode.ANY);
                enableTestOptions(mCreateTestCheckbox.getSelection());

                if (mValues.getTestProjectName() != null) {
                    mTestProjectNameText.setText(mValues.getTestProjectName());
                }
                if (mValues.getTestApplicationName() != null) {
                    mTestApplicationText.setText(mValues.getTestApplicationName());
                }
                if (mValues.getTestProjectName() != null) {
                    mTestPackageText.setText(mValues.getTestProjectName());
                }
            } finally {
                mIgnore = false;
            }
        }

        // Start focus with the package name, since the other fields are typically assigned
        // reasonable defaults
        mPackageText.setFocus();
        mPackageText.selectAll();

        validatePage();
    }

    protected void setSdkTargets(Collection<IAndroidTarget> targets, IAndroidTarget target) {
        if (targets == null) {
            targets = Collections.emptySet();
        }
        int selectionIndex = -1;
        String[] items = new String[targets.size()];
        int i = -1;
        for (IAndroidTarget candidate : targets) {
            items[++i] = targetLabel(candidate);
            if (candidate == target) {
                selectionIndex = i;
            }
        }
        try {
            mIgnore = true;
            mSdkCombo.setItems(items);
            mSdkCombo.setData(targets);
            if (selectionIndex != -1) {
                mSdkCombo.select(selectionIndex);
            }
        } finally {
            mIgnore = false;
        }
    }

    private String targetLabel(IAndroidTarget target) {
        // In the minimum SDK chooser, show the targets with api number and description,
        // such as "11 (Android 3.0)"
        return String.format("%1$s (%2$s)", target.getVersion().getApiString(),
                target.getFullName());
    }

    @Override
    public void dispose() {
        AndmoreAndroidPlugin.getDefault().removeTargetListener(this);
        super.dispose();
    }

    @Override
    public boolean isPageComplete() {
        // This page is only needed when creating new projects
        if (mValues.isUseExisting() || mValues.getMode() != Mode.ANY) {
            return true;
        }

        // Ensure that we reach this page
        if (mValues.getPackageName()== null) {
            return false;
        }

        return super.isPageComplete();
    }

    @Override
    public void modifyText(ModifyEvent e) {
        if (mIgnore) {
            return;
        }

        Object source = e.getSource();
        if (source == mSdkCombo) {
            mValues.setMinSdk(mSdkCombo.getText().trim());
            IAndroidTarget[] targets = (IAndroidTarget[]) mSdkCombo.getData();
            // An editable combo will treat item selection the same way as a user edit,
            // so we need to see if the string looks like a labeled version
            int index = mSdkCombo.getSelectionIndex();
            if (index != -1) {
                if (index >= 0 && index < targets.length) {
                    IAndroidTarget target = targets[index];
                    if (targetLabel(target).equals(mValues.getMinSdk())) {
                        mValues.setMinSdk(target.getVersion().getApiString());
                    }
                }
            }

            // Ensure that we never pick up the (Android x.y) suffix shown in combobox
            // for readability
            int separator = mValues.getMinSdk().indexOf(' ');
            if (separator != -1) {
                mValues.setMinSdk(mValues.getMinSdk().substring(0, separator));
            }
            mValues.setMinSdkModifiedByUser(true);
            mValues.updateSdkTargetToMatchMinSdkVersion();
        } else if (source == mApplicationText) {
            mValues.setApplicationName(mApplicationText.getText().trim());
            mValues.setApplicationNameModifiedByUser(true);

            if (!mValues.isTestApplicationNameModified()) {
                mValues.setTestApplicationName(suggestTestApplicationName(mValues.getApplicationName()));
                try {
                    mIgnore = true;
                    mTestApplicationText.setText(mValues.getTestApplicationName());
                } finally {
                    mIgnore = false;
                }
            }

        } else if (source == mPackageText) {
            mValues.setPackageName(mPackageText.getText().trim());
            mValues.setPackageNameModifiedByUser(true);

            if (!mValues.isTestPackageModified()) {
                mValues.setTestPackageName(suggestTestPackage(mValues.getPackageName()));
                try {
                    mIgnore = true;
                    mTestPackageText.setText(mValues.getTestPackageName());
                } finally {
                    mIgnore = false;
                }
            }
        } else if (source == mActivityText) {
            mValues.setActivityName(mActivityText.getText().trim());
            mValues.setActivityNameModifiedByUser(true);
        } else if (source == mTestApplicationText) {
            mValues.setTestApplicationName(mTestApplicationText.getText().trim());
            mValues.setTestApplicationNameModified(true);
        } else if (source == mTestPackageText) {
            mValues.setTestPackageName(mTestPackageText.getText().trim());
            mValues.setTestPackageModified(true);
        } else if (source == mTestProjectNameText) {
            mValues.setTestProjectName(mTestProjectNameText.getText().trim());
            mValues.setTestProjectModified(true);
        }

        validatePage();
    }

    @Override
    public void widgetSelected(SelectionEvent e) {
        if (mIgnore) {
            return;
        }

        Object source = e.getSource();

        if (source == mCreateActivityCheckbox) {
            mValues.setCreateActivity(mCreateActivityCheckbox.getSelection());
            mActivityText.setEnabled(mValues.isCreateActivity());
        } else if (source == mSdkCombo) {
            int index = mSdkCombo.getSelectionIndex();
            IAndroidTarget[] targets = (IAndroidTarget[]) mSdkCombo.getData();
            if (index != -1) {
                if (index >= 0 && index < targets.length) {
                    IAndroidTarget target = targets[index];
                    // Even though we are showing the logical version name, we place the
                    // actual api number as the minimum SDK
                    mValues.setMinSdk(target.getVersion().getApiString());
                }
            } else {
                String text = mSdkCombo.getText();
                boolean found = false;
                for (IAndroidTarget target : targets) {
                    if (targetLabel(target).equals(text)) {
                        mValues.setMinSdk(target.getVersion().getApiString());
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    mValues.setMinSdk(text);
                }
            }
        } else if (source == mCreateTestCheckbox) {
            mValues.setCreatePairProject(mCreateTestCheckbox.getSelection());
            enableTestOptions(mValues.isCreatePairProject());
            if (mValues.isCreatePairProject()) {
                if (mValues.getTestProjectName() == null || mValues.getTestProjectName().length() == 0) {
                    mValues.setTestProjectName(suggestTestProjectName(mValues.getProjectName()));
                }
                if (mValues.getTestApplicationName() == null ||
                        mValues.getTestApplicationName().length() == 0) {
                    mValues.setTestApplicationName(suggestTestApplicationName(mValues.getApplicationName()));
                }
                if (mValues.getTestPackageName() == null || mValues.getTestPackageName().length() == 0) {
                    mValues.setTestApplicationName(suggestTestPackage(mValues.getProjectName()));
                }

                try {
                    mIgnore = true;
                    mTestProjectNameText.setText(mValues.getTestProjectName());
                    mTestApplicationText.setText(mValues.getTestApplicationName());
                    mTestPackageText.setText(mValues.getTestPackageName());
                } finally {
                    mIgnore = false;
                }
            }
        }

        validatePage();
    }

    @Override
    public void widgetDefaultSelected(SelectionEvent e) {
    }

    private void validatePage() {
        IStatus status = validatePackage(mValues.getPackageName());
        if (status == null || status.getSeverity() != IStatus.ERROR) {
            IStatus validActivity = validateActivity();
            if (validActivity != null) {
                status = validActivity;
            }
        }
        if (status == null || status.getSeverity() != IStatus.ERROR) {
            IStatus validMinSdk = validateMinSdk();
            if (validMinSdk != null) {
                status = validMinSdk;
            }
        }

        if (status == null || status.getSeverity() != IStatus.ERROR) {
            IStatus validSourceFolder = validateSourceFolder();
            if (validSourceFolder != null) {
                status = validSourceFolder;
            }
        }

        // If creating a test project to go along with the main project, also validate
        // the additional test project parameters
        if (status == null || status.getSeverity() != IStatus.ERROR) {
            if (mValues.isCreatePairProject()) {
                IStatus validTestProject = ProjectNamePage.validateProjectName(
                        mValues.getTestProjectName());
                if (validTestProject != null) {
                    status = validTestProject;
                }

                if (status == null || status.getSeverity() != IStatus.ERROR) {
                    IStatus validTestLocation = validateTestProjectLocation();
                    if (validTestLocation != null) {
                        status = validTestLocation;
                    }
                }

                if (status == null || status.getSeverity() != IStatus.ERROR) {
                    IStatus validTestPackage = validatePackage(mValues.getTestPackageName());
                    if (validTestPackage != null) {
                        status = new Status(validTestPackage.getSeverity(),
                                AndmoreAndroidConstants.PLUGIN_ID,
                                validTestPackage.getMessage() + " (in test package)");
                    }
                }

                if (status == null || status.getSeverity() != IStatus.ERROR) {
                    if (mValues.getProjectName().equals(mValues.getTestProjectName())) {
                        status = new Status(IStatus.ERROR, AndmoreAndroidConstants.PLUGIN_ID,
                             "The main project name and the test project name must be different.");
                    }
                }
            }
        }

        // -- update UI & enable finish if there's no error
        setPageComplete(status == null || status.getSeverity() != IStatus.ERROR);
        if (status != null) {
            setMessage(status.getMessage(),
                    status.getSeverity() == IStatus.ERROR
                        ? IMessageProvider.ERROR : IMessageProvider.WARNING);
        } else {
            setErrorMessage(null);
            setMessage(null);
        }
    }

    private IStatus validateTestProjectLocation() {
        assert mValues.isCreatePairProject();

        // Validate location
        Path path = new Path(mValues.getProjectLocation().getPath());
        if (!mValues.isUseExisting()) {
            if (!mValues.isUseDefaultLocation()) {
                // If not using the default value validate the location.
                URI uri = URIUtil.toURI(path.toOSString());
                IWorkspace workspace = ResourcesPlugin.getWorkspace();
                IProject handle = workspace.getRoot().getProject(mValues.getTestProjectName());
                IStatus locationStatus = workspace.validateProjectLocationURI(handle, uri);
                if (!locationStatus.isOK()) {
                    return locationStatus;
                }
                // The location is valid as far as Eclipse is concerned (i.e. mostly not
                // an existing workspace project.) Check it either doesn't exist or is
                // a directory that is empty.
                File f = path.toFile();
                if (f.exists() && !f.isDirectory()) {
                    return new Status(IStatus.ERROR, AndmoreAndroidConstants.PLUGIN_ID,
                            "A directory name must be specified.");
                } else if (f.isDirectory()) {
                    // However if the directory exists, we should put a
                    // warning if it is not empty. We don't put an error
                    // (we'll ask the user again for confirmation before
                    // using the directory.)
                    String[] l = f.list();
                    if (l != null && l.length != 0) {
                        return new Status(IStatus.WARNING, AndmoreAndroidConstants.PLUGIN_ID,
                                "The selected output directory is not empty.");
                    }
                }
            } else {
                IPath destPath = path.removeLastSegments(1).append(mValues.getTestProjectName());
                File dest = destPath.toFile();
                if (dest.exists()) {
                    return new Status(IStatus.ERROR, AndmoreAndroidConstants.PLUGIN_ID,
                            String.format(
                                    "There is already a file or directory named \"%1$s\" in the selected location.",
                            mValues.getTestProjectName()));
                }
            }
        }

        return null;
    }

    private IStatus validateSourceFolder() {
        // This check does nothing when creating a new project.
        // This check is also useless when no activity is present or created.
        mValues.setSourceFolder(SdkConstants.FD_SOURCES);
        if (!mValues.isUseExisting() || !mValues.isCreateActivity()) {
            return null;
        }

        String osTarget = mValues.getActivityName();
        if (osTarget.indexOf('.') == -1) {
            osTarget = mValues.getPackageName() + File.separator + osTarget;
        } else if (osTarget.indexOf('.') == 0) {
            osTarget = mValues.getPackageName() + osTarget;
        }
        osTarget = osTarget.replace('.', File.separatorChar) + SdkConstants.DOT_JAVA;

        File projectDir = mValues.getProjectLocation();
        File[] allDirs = projectDir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname.isDirectory();
            }
        });
        if (allDirs != null) {
            boolean found = false;
            for (File f : allDirs) {
                Path path = new Path(f.getAbsolutePath());
                File java_activity = path.append(osTarget).toFile();
                if (java_activity.isFile()) {
                    mValues.setSourceFolder(f.getName());
                    found = true;
                    break;
                }
            }

            if (!found) {
                String projectPath = projectDir.getPath();
                if (allDirs.length > 0) {
                    return new Status(IStatus.ERROR, AndmoreAndroidConstants.PLUGIN_ID,
                            String.format("%1$s can not be found under %2$s.", osTarget,
                            projectPath));
                } else {
                    return new Status(IStatus.ERROR, AndmoreAndroidConstants.PLUGIN_ID,
                            String.format("No source folders can be found in %1$s.",
                            projectPath));
                }
            }
        }

        return null;
    }

    private IStatus validateMinSdk() {
        // Validate min SDK field
        // If the min sdk version is empty, it is always accepted.
        if (mValues.getMinSdk() == null || mValues.getMinSdk().length() == 0) {
            return null;
        }

        IAndroidTarget target = mValues.getTarget();
        if (target == null) {
            return null;
        }

        // If the current target is a preview, explicitly indicate minSdkVersion
        // must be set to this target name.
        boolean isTargetMinSdk = target.getVersion().getApiLevel() == Integer.valueOf(mValues.getMinSdk()).intValue();
        if (target.getVersion().isPreview() && !isTargetMinSdk) {
            return new Status(IStatus.ERROR, AndmoreAndroidConstants.PLUGIN_ID,
                    String.format(
                            "The SDK target is a preview. Min SDK Version must be set to '%s'.",
                            target.getVersion().getCodename()));
        }

        if (!isTargetMinSdk) {
            return new Status(target.getVersion().isPreview() ? IStatus.ERROR : IStatus.WARNING,
                    AndmoreAndroidConstants.PLUGIN_ID,
                    "The API level for the selected SDK target does not match the Min SDK Version."
                    );
        }

        return null;
    }

    public static IStatus validatePackage(String packageFieldContents) {
        // Validate package
        if (packageFieldContents == null || packageFieldContents.length() == 0) {
            return new Status(IStatus.ERROR, AndmoreAndroidConstants.PLUGIN_ID,
                    "Package name must be specified.");
        } else if (packageFieldContents.equals(DUMMY_PACKAGE)) {
            // The dummy package name is just a placeholder package (which isn't even valid
            // because it contains the reserved Java keyword "package") but we want to
            // make the error message say that a proper package should be entered rather than
            // what's wrong with this specific package. (And the reason we provide a dummy
            // package rather than a blank line is to make it more clear to beginners what
            // we're looking for.
            return new Status(IStatus.ERROR, AndmoreAndroidConstants.PLUGIN_ID,
                    "Package name must be specified.");
        }
        // Check it's a valid package string
        IStatus status = JavaConventions.validatePackageName(packageFieldContents, JDK_STANDARD,
                JDK_STANDARD);
        if (!status.isOK()) {
            return status;
        }

        // The Android Activity Manager does not accept packages names with only one
        // identifier. Check the package name has at least one dot in them (the previous rule
        // validated that if such a dot exist, it's not the first nor last characters of the
        // string.)
        if (packageFieldContents.indexOf('.') == -1) {
            return new Status(IStatus.ERROR, AndmoreAndroidConstants.PLUGIN_ID,
                    "Package name must have at least two identifiers.");
        }

        return null;
    }

    public static IStatus validateClass(String className) {
        if (className == null || className.length() == 0) {
            return new Status(IStatus.ERROR, AndmoreAndroidConstants.PLUGIN_ID,
                    "Class name must be specified.");
        }
        if (className.indexOf('.') != -1) {
            return new Status(IStatus.ERROR, AndmoreAndroidConstants.PLUGIN_ID,
                    "Enter just a class name, not a full package name");
        }
        return JavaConventions.validateJavaTypeName(className, JDK_STANDARD, JDK_STANDARD);
    }

    private IStatus validateActivity() {
        // Validate activity (if creating an activity)
        if (!mValues.isCreateActivity()) {
            return null;
        }

        return validateActivity(mValues.getActivityName());
    }

    /**
     * Validates the given activity name
     *
     * @param activityFieldContents the activity name to validate
     * @return a status for whether the activity name is valid
     */
    public static IStatus validateActivity(String activityFieldContents) {
        // Validate activity field
        if (activityFieldContents == null || activityFieldContents.length() == 0) {
            return new Status(IStatus.ERROR, AndmoreAndroidConstants.PLUGIN_ID,
                    "Activity name must be specified.");
        } else if (ACTIVITY_NAME_SUFFIX.equals(activityFieldContents)) {
            return new Status(IStatus.ERROR, AndmoreAndroidConstants.PLUGIN_ID, "Enter a valid activity name");
        } else if (activityFieldContents.contains("..")) { //$NON-NLS-1$
            return new Status(IStatus.ERROR, AndmoreAndroidConstants.PLUGIN_ID,
                    "Package segments in activity name cannot be empty (..)");
        }
        // The activity field can actually contain part of a sub-package name
        // or it can start with a dot "." to indicates it comes from the parent package
        // name.
        String packageName = "";  //$NON-NLS-1$
        int pos = activityFieldContents.lastIndexOf('.');
        if (pos >= 0) {
            packageName = activityFieldContents.substring(0, pos);
            if (packageName.startsWith(".")) { //$NON-NLS-1$
                packageName = packageName.substring(1);
            }

            activityFieldContents = activityFieldContents.substring(pos + 1);
        }

        // the activity field can contain a simple java identifier, or a
        // package name or one that starts with a dot. So if it starts with a dot,
        // ignore this dot -- the rest must look like a package name.
        if (activityFieldContents.length() > 0 && activityFieldContents.charAt(0) == '.') {
            activityFieldContents = activityFieldContents.substring(1);
        }

        // Check it's a valid activity string
        IStatus status = JavaConventions.validateTypeVariableName(activityFieldContents, JDK_STANDARD,
                JDK_STANDARD);
        if (!status.isOK()) {
            return status;
        }

        // Check it's a valid package string
        if (packageName.length() > 0) {
            status = JavaConventions.validatePackageName(packageName, JDK_STANDARD, JDK_STANDARD);
            if (!status.isOK()) {
                return new Status(IStatus.ERROR, AndmoreAndroidConstants.PLUGIN_ID,
                        status.getMessage() + " (in the activity name)");
            }
        }

        return null;
    }

    // ---- Implement ITargetChangeListener ----

    @Override
    public void onSdkLoaded() {
        if (mSdkCombo == null) {
            return;
        }

        // Update the sdk target selector with the new targets

        // get the targets from the sdk
        Collection<IAndroidTarget> targets = null;
        AndroidEnvironment env = AndworxFactory.instance().getAndroidEnvironment();
       	targets = env.getAndroidTargets();
        setSdkTargets(targets, mValues.getTarget());
    }

    @Override
    public void onProjectTargetChange(IProject changedProject) {
        // Ignore
    }

    @Override
    public void onTargetLoaded(IAndroidTarget target) {
        // Ignore
    }

    public static String suggestTestApplicationName(String applicationName) {
        if (applicationName == null) {
            applicationName = ""; //$NON-NLS-1$
        }
        if (applicationName.indexOf(' ') != -1) {
            return applicationName + " Test"; //$NON-NLS-1$
        } else {
            return applicationName + "Test"; //$NON-NLS-1$
        }
    }

    public static String suggestTestProjectName(String projectName) {
        if (projectName == null) {
            projectName = ""; //$NON-NLS-1$
        }
        if (projectName.length() > 0 && Character.isUpperCase(projectName.charAt(0))) {
            return projectName + "Test"; //$NON-NLS-1$
        } else {
            return projectName + "-test"; //$NON-NLS-1$
        }
    }


    public static String suggestTestPackage(String packagePath) {
        if (packagePath == null) {
            packagePath = ""; //$NON-NLS-1$
        }
        return packagePath + ".test"; //$NON-NLS-1$
    }
}
