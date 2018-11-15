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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.andmore.AndmoreAndroidConstants;
import org.eclipse.andmore.internal.project.AndroidManifestHelper;
import org.eclipse.andworx.build.AndworxFactory;
import org.eclipse.andworx.context.AndroidEnvironment;
import org.eclipse.andworx.maven.Dependency;
import org.eclipse.andworx.polyglot.PolyglotAgent;
import org.eclipse.andworx.project.Identity;
import org.eclipse.andworx.project.ProjectProfile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.ui.IWorkingSet;

import com.android.SdkConstants;
import com.android.annotations.Nullable;
import com.android.ide.common.xml.ManifestData;
import com.android.ide.common.xml.ManifestData.Activity;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.internal.project.ProjectProperties;
import com.android.sdklib.internal.project.ProjectProperties.PropertyType;
import com.android.utils.Pair;

/**
 * The {@link NewProjectWizardState} holds the state used by the various pages
 * in the {@link NewProjectWizard} and its variations, and it can also be used
 * to pass project information to the {@link NewProjectCreator}.
 */
public class NewProjectWizardState  {
    /**
     * Type of project being offered/created by the wizard
     */
    public enum Mode {
        /** Create a sample project. Testing options are not presented. */
        SAMPLE,

        /**
         * Create a test project, either testing itself or some other project.
         * Note that even if in the {@link #ANY} mode, a test project can be
         * created as a *paired* project with the main project, so this flag
         * only means that we are creating *just* a test project
         */
        TEST,

        /**
         * Create an Android project, which can be a plain project, optionally
         * with a paired test project, or a sample project (the first page
         * contains toggles for choosing which
         */
        ANY;
    }
	
    /** The mode to run the wizard in: creating test, or sample, or plain project */
    private Mode mode;

    private String projectName;
    private ProjectProfile projectProfile;
    /**
     * If true, the project should be created from an existing codebase (pointed
     * to by the {@link #projectLocation} or in the case of sample projects, the
     * {@link #chosenSample}. Otherwise, create a brand new project from scratch.
     */
    private boolean useExisting;

    /**
     * Whether new projects should be created into the default project location
     * (e.g. in the Eclipse workspace) or not
     */
    private boolean useDefaultLocation = true;

    /** True if the user has manually modified the target */
    private boolean targetModifiedByUser;

    /** The location to store projects into */
    private File projectLocation = new File(Platform.getLocation().toOSString());
    /** True if the project location name has been manually edited by the user */
    private boolean projectLocationModifiedByUser;

     /** True if the project name has been manually edited by the user */
    private boolean projectNameModifiedByUser;

    /** The application name */
    private String applicationName;
    /** True if the application name has been manually edited by the user */
    private boolean applicationNameModifiedByUser;

    /** The package path */
    private String packageName;
    /** True if the package name has been manually edited by the user */
    private boolean packageNameModifiedByUser;

    /** True if a new activity should be created */
    private boolean createActivity;

    /** The name of the new activity to be created */
    private String activityName;
    /** True if the activity name has been manually edited by the user */
    private boolean activityNameModifiedByUser;

    /** The minimum SDK version to use with the project (may be null or blank) */
    private String minSdk;
    /** True if the minimum SDK version has been manually edited by the user */
    private boolean minSdkModifiedByUser;
    /**
     * A list of paths to each of the available samples for the current SDK.
     * The pair is (String: sample display name => File: sample directory).
     * Note we want a list, not a map since we might have duplicates.
     * */
    private List<Pair<String, File>> samples = new ArrayList<Pair<String, File>>();
    /** Path to the currently chosen sample */
    private File chosenSample;

    /** The name of the source folder, relative to the project root */
    private String sourceFolder = SdkConstants.FD_SOURCES;
    /** The set of chosen working sets to use when creating the project */
    private IWorkingSet[] workingSets = new IWorkingSet[0];

    /**
     * A reference to a different project that the current test project will be
     * testing.
     */
    private IProject testedProject;
    /**
     * If true, this test project should be testing itself, otherwise it will be
     * testing the project pointed to by {@link #testedProject}.
     */
    private boolean testingSelf;

    // NOTE: These apply only to creating paired projects; when isTest is true
    // we're using
    // the normal fields above
    /**
     * If true, create a test project along with this plain project which will
     * be testing the plain project. (This flag only applies when creating
     * normal projects.)
     */
    private boolean createPairProject;
    /**
     * The application name of the test application (only applies when
     * {@link #createPairProject} is true)
     */
    private String testApplicationName;
    /**
     * True if the testing application name has been modified by the user (only
     * applies when {@link #createPairProject} is true)
     */
    private boolean testApplicationNameModified;
    /**
     * The package name of the test application (only applies when
     * {@link #createPairProject} is true)
     */
    private String testPackageName;
    /**
     * True if the testing package name has been modified by the user (only
     * applies when {@link #createPairProject} is true)
     */
    private boolean testPackageModified;
    /**
     * The project name of the test project (only applies when
     * {@link #createPairProject} is true)
     */
    private String testProjectName;
    /**
     * True if the testing project name has been modified by the user (only
     * applies when {@link #createPairProject} is true)
     */
    private boolean testProjectModified;
    /** Package name of the tested app */
    private String testTargetPackageName;

    /**
     * Copy project into workspace? This flag only applies when importing
     * projects (creating projects from existing source)
     */
    private boolean copyIntoWorkspace;

    /**
     * Creates a new {@link NewProjectWizardState}
     *
     * @param mode the mode to run the wizard in
     */
    public NewProjectWizardState(Mode mode) {
    	this(mode, new Identity("","",""));
    	projectName = "";
    }
    /**
     * Creates a new {@link NewProjectWizardState}
     *
     * @param mode the mode to run the wizard in
     */
    public NewProjectWizardState(Mode mode, Identity identity) {
    	projectProfile = new ProjectProfile(identity);
    	// Default project name to identity group ID . artifact ID
    	projectName = identity.getGroupId() + "." + identity.getArtifactId();
        this.mode = mode;
        if (mode == Mode.SAMPLE) {
            useExisting = true;
        } else if (mode == Mode.TEST) {
            createActivity = false;
        }
    }

    public Mode getMode() {
    	return mode;
    }
    public static Dependency getNewIdentity() {
		return PolyglotAgent.NEW_IDENTITY;
	}
	public boolean isUseExisting() {
		return useExisting;
	}
	public boolean isUseDefaultLocation() {
		return useDefaultLocation;
	}
	public boolean isCreateActivity() {
		return createActivity;
	}
	public boolean isCopyIntoWorkspace() {
		return copyIntoWorkspace;
	}
	/**
     * List of projects to be imported. Null if not importing projects.
     */
    @Nullable
    public List<ImportedProject> getImportProjects() {
    	return importProjects;
    }

    /** True if the user has manually modified the target */
    public boolean isTargetModifiedByUser() {
    	return targetModifiedByUser;
    }

    /** The location to store projects into */
    public File getProjectLocation() {
    	return projectLocation;
    }
    /** True if the project location name has been manually edited by the user */
    public boolean isProjectLocationModifiedByUser() {
    	return projectLocationModifiedByUser;
    }

    /** The name of the project */
    public String getProjectName() {
    	return projectName;
    }
    /** True if the project name has been manually edited by the user */
    public boolean isProjectNameModifiedByUser() {
    	return projectNameModifiedByUser;
    }

    /** The application name */
    public String getApplicationName() {
    	return applicationName;
    }
    /** True if the application name has been manually edited by the user */
    public boolean isApplicationNameModifiedByUser() {
    	return applicationNameModifiedByUser;
    }

    /** The package path */
    public String getPackageName() {
    	return packageName;
    }
    /** True if the package name has been manually edited by the user */
    public boolean isPackageNameModifiedByUser() {
    	return packageNameModifiedByUser;
    }

    /** True if a new activity should be created */
    public boolean createActivity() {
    	return createActivity;
    }

    /** The name of the new activity to be created */
    public String getActivityName() {
    	return activityName;
    }
    /** True if the activity name has been manually edited by the user */
    public boolean isActivityNameModifiedByUser() {
    	return activityNameModifiedByUser;
    }

    /** The minimum SDK version to use with the project (may be null or blank) */
    public String getMinSdk() {
    	return minSdk;
    }
    /** True if the minimum SDK version has been manually edited by the user */
    public boolean isMinSdkModifiedByUser() {
    	return minSdkModifiedByUser;
    }
    /**
     * A list of paths to each of the available samples for the current SDK.
     * The pair is (String: sample display name => File: sample directory).
     * Note we want a list, not a map since we might have duplicates.
     * */
    public List<Pair<String, File>> getSamples() {
    	return samples;
    }
    /** Path to the currently chosen sample */
    public File getChosenSample() {
    	return chosenSample;
    }

    /** The name of the source folder, relative to the project root */
    public String getSourceFolder() {
    	return sourceFolder;
    }
    /** The set of chosen working sets to use when creating the project */
    public IWorkingSet[] getWorkingSets() {
    	return workingSets;
    }

    /**
     * A reference to a different project that the current test project will be
     * testing.
     */
    public IProject getTestedProject() {
    	return testedProject;
    }
    /**
     * If true, this test project should be testing itself, otherwise it will be
     * testing the project pointed to by {@link #testedProject}.
     */
    public boolean isTestingSelf() {
    	return testingSelf;
    }

    // NOTE: These apply only to creating paired projects; when isTest is true
    // we're using
    // the normal fields above
    /**
     * If true, create a test project along with this plain project which will
     * be testing the plain project. (This flag only applies when creating
     * normal projects.)
     */
    public boolean isCreatePairProject() {
    	return createPairProject;
    }
    /**
     * The application name of the test application (only applies when
     * {@link #createPairProject} is true)
     */
    public String getTestApplicationName() {
    	return testApplicationName;
    }
    /**
     * True if the testing application name has been modified by the user (only
     * applies when {@link #createPairProject} is true)
     */
    public boolean isTestApplicationNameModified() {
    	return testApplicationNameModified;
    }
    /**
     * The package name of the test application (only applies when
     * {@link #createPairProject} is true)
     */
    public String getTestPackageName() {
    	return testPackageName;
    }
    /**
     * True if the testing package name has been modified by the user (only
     * applies when {@link #createPairProject} is true)
     */
    public boolean isTestPackageModified() {
    	return testPackageModified;
    }
    /**
     * The project name of the test project (only applies when
     * {@link #createPairProject} is true)
     */
    public String getTestProjectName() {
    	return testProjectName;
    }
    /**
     * True if the testing project name has been modified by the user (only
     * applies when {@link #createPairProject} is true)
     */
    public boolean isTestProjectModified() {
    	return testProjectModified;
    }
    /** Package name of the tested app */
    public String getTestTargetPackageName() {
    	return testTargetPackageName;
    }

    /**
     * Copy project into workspace? This flag only applies when importing
     * projects (creating projects from existing source)
     */
    public boolean CopyIntoWorkspace() {
    	return copyIntoWorkspace;
    }

    /**
     * List of projects to be imported. Null if not importing projects.
     */
    @Nullable
    public List<ImportedProject> importProjects;


    /**
     * Extract information (package name, application name, minimum SDK etc) from
     * the Android manifest in the given Android project location.
     *
     * @param path the path to the project to extract information from
     */
    public void extractFromAndroidManifest(Path path) {
        String osPath = path.append(SdkConstants.FN_ANDROID_MANIFEST_XML).toOSString();
        if (!(new File(osPath).exists())) {
            return;
        }

        ManifestData manifestData = AndroidManifestHelper.parseForData(osPath);
        if (manifestData == null) {
            return;
        }

        String newPackageName = null;
        Activity activity = null;
        String newActivityName = null;
        String minSdkVersion = null;
        try {
            newPackageName = manifestData.getPackage();
            minSdkVersion = manifestData.getMinSdkVersionString();

            // try to get the first launcher activity. If none, just take the first activity.
            activity = manifestData.getLauncherActivity();
            if (activity == null) {
                Activity[] activities = manifestData.getActivities();
                if (activities != null && activities.length > 0) {
                    activity = activities[0];
                }
            }
        } catch (Exception e) {
            // ignore exceptions
        }

        if (newPackageName != null && newPackageName.length() > 0) {
            packageName = newPackageName;
        }

        if (activity != null) {
            newActivityName = extractActivityName(activity.getName(),
                    newPackageName);
        }

        if (newActivityName != null && newActivityName.length() > 0) {
            activityName = newActivityName;
            // we are "importing" an existing activity, not creating a new one
            createActivity = false;

            // If project name and application names are empty, use the activity
            // name as a default. If the activity name has dots, it's a part of a
            // package specification and only the last identifier must be used.
            if (newActivityName.indexOf('.') != -1) {
                String[] ids = newActivityName.split(AndmoreAndroidConstants.RE_DOT);
                newActivityName = ids[ids.length - 1];
            }
            if (projectName.isEmpty() || !projectNameModifiedByUser) {
            	projectName = newActivityName;
                projectNameModifiedByUser = false;
            }
            if (applicationName == null || applicationName.length() == 0 ||
                    !applicationNameModifiedByUser) {
                applicationNameModifiedByUser = false;
                applicationName = newActivityName;
            }
        } else {
            activityName = ""; //$NON-NLS-1$

            // There is no activity name to use to fill in the project and application
            // name. However if there's a package name, we can use this as a base.
            if (newPackageName != null && newPackageName.length() > 0) {
                // Package name is a java identifier, so it's most suitable for
                // an application name.

                if (applicationName == null || applicationName.length() == 0 ||
                        !applicationNameModifiedByUser) {
                    applicationName = newPackageName;
                }

                if (projectName.isEmpty() || !projectNameModifiedByUser) {
                    int lastDotIndex = newPackageName.lastIndexOf('.');
                	projectName = lastDotIndex == -1 ? newPackageName : newPackageName.substring(lastDotIndex + 1);
                }
            }
        }

        if (mode == Mode.ANY && useExisting) {
            updateSdkTargetToMatchProject(path.toFile());
        }

        minSdk = minSdkVersion;
        minSdkModifiedByUser = false;
    }

    /**
     * Try to find an SDK Target that matches the current MinSdkVersion.
     *
     * There can be multiple targets with the same sdk api version, so don't change
     * it if it's already at the right version. Otherwise pick the first target
     * that matches.
     */
    public void updateSdkTargetToMatchMinSdkVersion() {
        IAndroidTarget currentTarget = projectProfile.getTarget();
        if (currentTarget != null && currentTarget.getVersion().getApiLevel() == (Integer.parseInt(minSdk))) {
            return;
        }

        AndroidEnvironment env = AndworxFactory.instance().getAndroidEnvironment();
        if (env.isValid()) {
        	Collection<IAndroidTarget> targets = env.getAndroidTargets();
            for (IAndroidTarget t : targets) {
                if (t.getVersion().getApiLevel() == (Integer.parseInt(minSdk))) {
                    return;
                }
            }
        }
    }

	/**
     * Updates the SDK to reflect the SDK required by the project at the given
     * location
     *
     * @param location the location of the project
     */
    public void updateSdkTargetToMatchProject(File location) {
        // Select the target matching the manifest's sdk or build properties, if any
        IAndroidTarget foundTarget = null;
        // This is the target currently in the UI
        IAndroidTarget currentTarget = projectProfile.getTarget();
        String projectPath = location.getPath();

        // If there's a current target defined, we do not allow to change it when
        // operating in the create-from-sample mode -- since the available sample list
        // is tied to the current target, so changing it would invalidate the project we're
        // trying to load in the first place.
        if (!targetModifiedByUser) {
            ProjectProperties p = ProjectProperties.load(projectPath,
                    PropertyType.PROJECT);
            AndworxFactory objectFactory = AndworxFactory.instance();
            AndroidEnvironment env = objectFactory.getAndroidEnvironment();
            if ((p != null) && env.isValid()) {
                String v = p.getProperty(ProjectProperties.PROPERTY_TARGET);
                IAndroidTarget desiredTarget = objectFactory.getAvailableTarget(v);
                // We can change the current target if:
                // - we found a new desired target
                // - there is no current target
                // - or the current target can't run the desired target
                if (desiredTarget != null &&
                        (currentTarget == null || !desiredTarget.canRunOn(currentTarget))) {
                    foundTarget = desiredTarget;
                }
            }

            Collection<IAndroidTarget> targets = env.getAndroidTargets();
            if (foundTarget == null && minSdk != null) {
                // Otherwise try to match the requested min-sdk-version if we find an
                // exact match, regardless of the currently selected target.
                for (IAndroidTarget existingTarget : targets) {
                    if (existingTarget != null &&
                            existingTarget.getVersion().getApiLevel() == (Integer.parseInt(minSdk))) {
                        foundTarget = existingTarget;
                        break;
                    }
                }
            }

            if (foundTarget == null) {
                // Or last attempt, try to match a sample project location and use it
                // if we find an exact match, regardless of the currently selected target.
                for (IAndroidTarget existingTarget : targets) {
                    if (existingTarget != null &&
                            projectPath.startsWith(existingTarget.getLocation())) {
                        foundTarget = existingTarget;
                        break;
                    }
                }
            }
        }

        if (foundTarget != null) {
        	projectProfile.setTarget(foundTarget);
        }
    }


    /**
     * Given a fully qualified activity name (e.g. com.foo.test.MyClass) and given a project
     * package base name (e.g. com.foo), returns the relative activity name that would be used
     * the "name" attribute of an "activity" element.
     *
     * @param fullActivityName a fully qualified activity class name, e.g. "com.foo.test.MyClass"
     * @param packageName The project base package name, e.g. "com.foo"
     * @return The relative activity name if it can be computed or the original fullActivityName.
     */
    @Nullable
    public static String extractActivityName(
            @Nullable String fullActivityName,
            @Nullable String packageName) {
        if (packageName != null && fullActivityName != null) {
            if (!packageName.isEmpty() && fullActivityName.startsWith(packageName)) {
                String name = fullActivityName.substring(packageName.length());
                if (!name.isEmpty() && name.charAt(0) == '.') {
                    return name;
                }
            }
        }

        return fullActivityName;
    }
    
	public void setSampleMode() {
		mode = Mode.SAMPLE;
	}
	public void setAnyMode() {
		mode = Mode.ANY;
	}
	public void setMinSdk(String minSdk) {
		this.minSdk = minSdk;
	}
	public void setMinSdkModifiedByUser(boolean minSdkModifiedByUser) {
		this.minSdkModifiedByUser = minSdkModifiedByUser;
	}
	public void setActivityName(String activityName) {
		this.activityName = activityName;
	}
	public void setActivityNameModifiedByUser(boolean activityNameModifiedByUser) {
		this.activityNameModifiedByUser = activityNameModifiedByUser;
	}
	public void setApplicationName(String applicationName) {
		this.applicationName = applicationName;
	}
	public void setApplicationNameModifiedByUser(boolean applicationNameModifiedByUser) {
		this.applicationNameModifiedByUser = applicationNameModifiedByUser;
	}
	public void setTestApplicationName(String testApplicationName) {
		this.testApplicationName = testApplicationName;
	}
	public void setPackageName(String packageName) {
		this.packageName = packageName;
	}
	public void setPackageNameModifiedByUser(boolean packageNameModifiedByUser) {
		this.packageNameModifiedByUser = packageNameModifiedByUser;
	}
	public void setTestPackageName(String testPackageName) {
		this.testPackageName = testPackageName;
	}
	public void setTestApplicationNameModified(boolean testApplicationNameModified) {
		this.testApplicationNameModified = testApplicationNameModified;
	}
	public void setTestPackageModified(boolean testPackageModified) {
		this.testPackageModified = testPackageModified;
	}
	public void setTestProjectName(String testProjectName) {
		this.testProjectName = testProjectName;
	}
	public void setTestProjectModified(boolean testProjectModified) {
		this.testProjectModified = testProjectModified;
	}
	public void setCreateActivity(boolean createActivity) {
		this.createActivity = createActivity;
	}
	public void setUseExisting(boolean useExisting) {
		this.useExisting = useExisting;
	}
	public void setUseDefaultLocation(boolean useDefaultLocation) {
		this.useDefaultLocation = useDefaultLocation;
	}
	public void setTargetModifiedByUser(boolean targetModifiedByUser) {
		this.targetModifiedByUser = targetModifiedByUser;
	}
	public void setProjectLocation(File projectLocation) {
		this.projectLocation = projectLocation;
	}
	public void setProjectLocationModifiedByUser(boolean projectLocationModifiedByUser) {
		this.projectLocationModifiedByUser = projectLocationModifiedByUser;
	}
	public void setProjectNameModifiedByUser(boolean projectNameModifiedByUser) {
		this.projectNameModifiedByUser = projectNameModifiedByUser;
	}
	public void setSourceFolder(String sourceFolder) {
		this.sourceFolder = sourceFolder;
	}
	public void setWorkingSets(IWorkingSet[] workingSets) {
		this.workingSets = workingSets;
	}
	public void setTestedProject(IProject testedProject) {
		this.testedProject = testedProject;
	}
	public void setTestingSelf(boolean testingSelf) {
		this.testingSelf = testingSelf;
	}
	public void setCreatePairProject(boolean createPairProject) {
		this.createPairProject = createPairProject;
	}
	public void setTestTargetPackageName(String testTargetPackageName) {
		this.testTargetPackageName = testTargetPackageName;
	}
	public void setCopyIntoWorkspace(boolean copyIntoWorkspace) {
		this.copyIntoWorkspace = copyIntoWorkspace;
	}
	public void setImportProjects(List<ImportedProject> importProjects) {
		this.importProjects = importProjects;
	}
	public void setChosenSample(File chosenSample) {
		this.chosenSample = chosenSample;
	}
	public void setProjectName(String name) {
		projectName = name;;
	}
	public File getCosenSample() {
		return chosenSample;
	}
	public IAndroidTarget getTarget() {
		return projectProfile.getTarget();
	}
	public void setTarget(IAndroidTarget target) {
		projectProfile.setTarget(target);
	}

}
