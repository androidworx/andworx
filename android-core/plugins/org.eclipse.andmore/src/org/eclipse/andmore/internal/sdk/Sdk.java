/*
 * Copyright (C) 2008 The Android Open Source Project
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

package org.eclipse.andmore.internal.sdk;

import static com.android.SdkConstants.DOT_XML;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.eclipse.andmore.AndmoreAndroidConstants;
import org.eclipse.andmore.AndmoreAndroidPlugin;
import org.eclipse.andmore.internal.editors.common.CommonXmlEditor;
import org.eclipse.andmore.internal.preferences.AdtPrefs;
import org.eclipse.andmore.internal.project.BaseProjectHelper;
import org.eclipse.andmore.internal.project.LibraryClasspathContainerInitializer;
import org.eclipse.andmore.internal.project.ProjectHelper;
import org.eclipse.andmore.internal.resources.manager.GlobalProjectMonitor;
import org.eclipse.andmore.internal.resources.manager.GlobalProjectMonitor.IFileListener;
import org.eclipse.andmore.internal.resources.manager.GlobalProjectMonitor.IProjectListener;
import org.eclipse.andmore.internal.resources.manager.GlobalProjectMonitor.IResourceEventListener;
import org.eclipse.andworx.build.AndworxContext;
import org.eclipse.andworx.build.AndworxFactory;
import org.eclipse.andworx.build.SdkTracker;
import org.eclipse.andworx.sdk.SdkProfile;
import org.eclipse.andworx.sdk.SdkTargetDataMap;
import org.eclipse.andworx.model.CodeSource;
import org.eclipse.andworx.registry.LibraryState;
import org.eclipse.andworx.registry.ProjectRegistry;
import org.eclipse.andworx.registry.ProjectState;
import org.eclipse.andworx.registry.ProjectState.LibraryDifference;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.ui.IEditorDescriptor;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPartSite;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ddmlib.IDevice;
import com.android.ide.common.rendering.LayoutLibrary;
import com.android.ide.common.sdk.LoadStatus;
import com.android.io.StreamException;
import com.android.repository.api.RepoManager;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.DeviceManager;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.sdklib.repository.AndroidSdkHandler;

/**
 * Central point to load, manipulate and deal with the Android SDK. Only one SDK can be used
 * at the same time.
 *
 * To start using an SDK, call {@link #loadSdk(String)} which returns the instance of
 * the Sdk object.
 *
 * To get the list of platforms or add-ons present in the SDK, call {@link #getTargets()}.
 */
public final class Sdk  {
    private final static boolean DEBUG = false;

    /**
     * Classes implementing this interface will receive notification when targets are changed.
     */
    public interface ITargetChangeListener {
        /**
         * Sent when project has its target changed.
         */
        void onProjectTargetChange(IProject changedProject);

        /**
         * Called when the targets are loaded (either the SDK finished loading when Eclipse starts,
         * or the SDK is changed).
         */
        void onTargetLoaded(IAndroidTarget target);

        /**
         * Called when the base content of the SDK is parsed.
         */
        void onSdkLoaded();
    }

    /**
     * Basic abstract implementation of the ITargetChangeListener for the case where both
     * {@link #onProjectTargetChange(IProject)} and {@link #onTargetLoaded(IAndroidTarget)}
     * use the same code based on a simple test requiring to know the current IProject.
     */
    public static abstract class TargetChangeListener implements ITargetChangeListener {
        /**
         * Returns the {@link IProject} associated with the listener.
         */
        public abstract IProject getProject();

        /**
         * Called when the listener needs to take action on the event. This is only called
         * if {@link #getProject()} and the {@link IAndroidTarget} associated with the project
         * match the values received in {@link #onProjectTargetChange(IProject)} and
         * {@link #onTargetLoaded(IAndroidTarget)}.
         */
        public abstract void reload();

        @Override
        public void onProjectTargetChange(IProject changedProject) {
            if (changedProject != null && changedProject.equals(getProject())) {
                reload();
            }
        }

        @Override
        public void onTargetLoaded(IAndroidTarget target) {
            IAndroidTarget projectTarget = AndworxFactory.instance().getTarget(getProject());
            if (target != null && target.equals(projectTarget)) {
                reload();
            }
        }

        @Override
        public void onSdkLoaded() {
            // do nothing;
        }
    }

    /**
     * Data bundled using during the load of Target data.
     * <p/>This contains a list of projects that attempted
     * to compile before the loading was finished. Those projects will be recompiled
     * at the end of the loading.
     */
    private final static class TargetLoadProjects {
        final HashSet<IJavaProject> projectsToReload = new HashSet<IJavaProject>();
    }

    private final static Object LOCK = new Object();

    private static Sdk sCurrentSdk = null;

    private final AndworxContext objectFactory;
    private final AndroidSdkHandler androidSdkHandler;

    private final RepoManager repoManager;
    private final AvdManager avdManager;
    private final DeviceManager deviceManager;

    /** Map associating an {@link IAndroidTarget} to an {@link AndroidTargetData} */
    private final SdkTargetDataMap<AndroidTargetData> targetDataMap;
    /** Map associating an {@link IAndroidTarget} and its {@link TargetLoadProjects}. */
    private final HashMap<String, TargetLoadProjects> targetDataStatusMap;

    private final String docBaseUrl;

	private final ProjectRegistry projectRegistry;

    /** Delegate listener for file changes */
    private IFileListener fileListener;
    /** Delegate listener for project changes */
    private AndroidProjectListener projectListener;

    /** List of modified projects. This is filled in
     * {@link IProjectListener#projectOpened(IProject)},
     * {@link IProjectListener#projectOpenedWithWorkspace(IProject)},
     * {@link IProjectListener#projectClosed(IProject)}, and
     * {@link IProjectListener#projectDeleted(IProject)} and processed in
     * {@link IResourceEventListener#resourceChangeEventEnd()}.
     */
    private final List<ProjectState> modifiedProjects;
    private final List<ProjectState> modifiedChildProjects;
    /**
     * Delegate listener for resource changes. This is called before and after any calls to the
     * project and file listeners (for a given resource change event).
     */
    private IResourceEventListener mResourceEventListener = new IResourceEventListener() {
        @Override
        public void resourceChangeEventStart() {
            modifiedProjects.clear();
            modifiedChildProjects.clear();
        }

        @Override
        public void resourceChangeEventEnd() {
            if (modifiedProjects.size() == 0) {
                return;
            }

            // first make sure all the parents are updated
            updateParentProjects();

            // for all modified projects, update their library list
            // and gather their IProject
            final List<IJavaProject> projectList = new ArrayList<IJavaProject>();
            for (ProjectState state : modifiedProjects) {
                state.updateFullLibraryList();
                projectList.add(JavaCore.create(state.getProject()));
            }

            Job job = new Job("Android Library Update") { //$NON-NLS-1$
                @Override
                protected IStatus run(IProgressMonitor monitor) {
                    LibraryClasspathContainerInitializer.updateProjects(
                            projectList.toArray(new IJavaProject[projectList.size()]));

                    for (IJavaProject javaProject : projectList) {
                        try {
                            javaProject.getProject().build(IncrementalProjectBuilder.FULL_BUILD,
                                    monitor);
                        } catch (CoreException e) {
                            // pass
                        }
                    }
                    return Status.OK_STATUS;
                }
            };
            job.setPriority(Job.BUILD);
            job.setRule(ResourcesPlugin.getWorkspace().getRoot());
            job.schedule();
        }
    };


    private Sdk(AndroidSdkHandler androidSdkHandler, RepoManager repoManager, AvdManager avdManager, DeviceManager deviceManager) {
        this.androidSdkHandler = androidSdkHandler;
        this.repoManager = repoManager;
        this.avdManager = avdManager;
        this.deviceManager = deviceManager;
        modifiedProjects = new ArrayList<ProjectState>();
        modifiedChildProjects = new ArrayList<ProjectState>();
        objectFactory = AndworxFactory.instance();
        projectRegistry = objectFactory.getProjectRegistry(); 
        targetDataMap = (SdkTargetDataMap<AndroidTargetData>)objectFactory.get(SdkTargetDataMap.class);
;
        targetDataStatusMap = new HashMap<>();
        // listen to projects closing
        fileListener = new ProjectFileListener(this);
        projectListener = new AndroidProjectListener(this);
        projectRegistry.registerProjectListener(projectListener);
        GlobalProjectMonitor monitor = GlobalProjectMonitor.getMonitor();
        // need to register the resource event listener first because the project listener
        // is called back during registration with project opened in the workspace.
        monitor.addResourceEventListener(mResourceEventListener);
        monitor.addProjectListener(projectListener);
        monitor.addFileListener(fileListener,
                IResourceDelta.CHANGED | IResourceDelta.ADDED | IResourceDelta.REMOVED);

        // pre-compute some paths
        docBaseUrl = getDocumentationBaseUrl(repoManager.getLocalPath() +
                SdkConstants.OS_SDK_DOCS_FOLDER);

         // update whatever ProjectState is already present with new IAndroidTarget objects.
        projectRegistry.refresh();
    }

    /**
     * Returns the lock object used to synchronize all operations dealing with SDK, targets and
     * projects.
     */
    @NonNull
    public static final Object getLock() {
        return LOCK;
    }

    /**
     * Loads an SDK and returns an {@link Sdk} object if success.
     * <p/>If the SDK failed to load, it displays an error to the user.
     * @param sdkLocation the OS path to the SDK.
     */
    @Nullable
    public static Sdk loadSdk(String sdkLocation) {
    	// AndworxBuildPlugin validates the SDK location and stores details
    	// Note the following call may block at start up if the plugin has not completed initialization
    	SdkTracker tracker = AndworxFactory.instance().getSdkTracker();
        SdkProfile sdkProfile = tracker.setCurrentSdk(sdkLocation);
        return changeCurrentSdk(sdkProfile);
    }
    
    private static Sdk changeCurrentSdk(SdkProfile sdkProfile) {
         synchronized (LOCK) {
        	Sdk sdk = sCurrentSdk;
            sCurrentSdk = null;
            if (sdkProfile.isValid()) {
                if (sdk != null) {
                	sdk.dispose();
                }
                sCurrentSdk = new Sdk(sdkProfile.getAndroidSdkHandler(), sdkProfile.getManager(), sdkProfile.getAvdManager(), sdkProfile.getDeviceManager());
                return sCurrentSdk;
            }
            sCurrentSdk = sdk;
            return sdk;
        }
    }

    /**
     * Returns the current {@link Sdk} object.
     */
    @Nullable
    public static Sdk getCurrent() {
        synchronized (LOCK) {
            return sCurrentSdk;
        }
    }

    /**
     * Returns the location of the current SDK as an OS path string.
     * Guaranteed to be terminated by a platform-specific path separator.
     * <p/>
     * Due to {@link File} canonicalization, this MAY differ from the string used to initialize
     * the SDK path.
     *
     * @return The SDK OS path or null if no SDK is setup.
     * @deprecated Consider using {@link #getSdkFileLocation()} instead.
     * @see #getSdkFileLocation()
     */
    @Deprecated
    @Nullable
    public String getSdkOsLocation() {
        String path = repoManager == null ? null : repoManager.getLocalPath().toString();
        if (path != null) {
            // For backward compatibility make sure it ends with a separator.
            // This used to be the case when the SDK Manager was created from a String path
            // but now that a File is internally used the trailing dir separator is lost.
            if (path.length() > 0 && !path.endsWith(File.separator)) {
                path = path + File.separator;
            }
        }
        return path;
    }

    /**
     * Returns the location of the current SDK as a {@link File} or null.
     *
     * @return The SDK OS path or null if no SDK is setup.
     */
    @Nullable
    public File getSdkFileLocation() {
        if (androidSdkHandler == null) {
            return null;
        }
        return androidSdkHandler.getLocation();
    }

    /**
     * Returns the URL to the local documentation.
     * Can return null if no documentation is found in the current SDK.
     *
     * @return A file:// URL on the local documentation folder if it exists or null.
     */
    @Nullable
    public String getDocumentationBaseUrl() {
        return docBaseUrl;
    }

    /**
     * Returns the list of targets that are available in the SDK.
     */
    @NonNull
    public Collection<IAndroidTarget> getTargets() {
        return androidSdkHandler.getAndroidTargetManager(new FakeProgressIndicator()).getTargets(new FakeProgressIndicator());
    }

    @Nullable
    public BuildToolInfo getLatestBuildTool() {
        return androidSdkHandler.getLatestBuildTool(new FakeProgressIndicator(), true);
    }

    /**
     * Initializes a new project with a target. This creates the <code>project.properties</code>
     * file.
     * @param project the project to initialize
     * @param target the project's target.
     * @throws IOException if creating the file failed in any way.
     * @throws StreamException if processing the project property file fails
     */
    public void initProject(@Nullable IProject project, @Nullable IAndroidTarget target)
            throws IOException, StreamException {
        if (project == null || target == null) {
            return;
        }
/* TODO - Polyglot persistence
        synchronized (LOCK) {
            // check if there's already a state?
            ProjectState state = getProjectState(project);

            ProjectPropertiesWorkingCopy properties = null;

            if (state != null) {
                properties = state.getProperties().makeWorkingCopy();
            }

            if (properties == null) {
                IPath location = project.getLocation();
                if (location == null) {  // can return null when the project is being deleted.
                    // do nothing and return null;
                    return;
                }

                properties = ProjectProperties.create(location.toOSString(), PropertyType.PROJECT);
            }

            // save the target hash string in the project persistent property
            properties.setProperty(ProjectProperties.PROPERTY_TARGET, target.hashString());
            properties.save();
        }
*/        
    }

    public RepoManager getRepoManager()
    {
    	return repoManager;
    }

	public void reloadLibraries(ProjectState state, LibraryDifference diff, boolean wasLibrary) {
        // reload the libraries if needed
        if (diff.hasDiff()) {
            if (diff.added) { 
                synchronized (LOCK) {
                    for (ProjectState projectState : projectRegistry.projectStateCollection()) {
                    	// Defensive programming required
                        if ((projectState != state) && 
                        	(projectState.getProject() != null) && 
                        	(projectState.getProject().getLocation() != null)){
                            // need to call needs to do the libraryState link,
                            // but no need to look at the result, as we'll compare
                            // the result of getFullLibraryProjects()
                            // this is easier to due to indirect dependencies.
                            state.needs(projectState);
                        }
                    }
                }
                // Update pending library lists
                for (ProjectState projectState : projectRegistry.projectStateCollection()) {
                    if ((projectState != state)){
                    	projectState.resolve(state);
                    }
                }
            }

            markProject(state, wasLibrary || state.isLibrary());
        }
	}
	
    /**
     * Checks and loads (if needed) the data for a given target.
     * <p/> The data is loaded in a separate {@link Job}, and opened editors will be notified
     * through their implementation of {@link ITargetChangeListener#onTargetLoaded(IAndroidTarget)}.
     * <p/>An optional project as second parameter can be given to be recompiled once the target
     * data is finished loading.
     * <p/>The return value is non-null only if the target data has already been loaded (and in this
     * case is the status of the load operation)
     * @param target the target to load.
     * @param project an optional project to be recompiled when the target data is loaded.
     * If the target is already loaded, nothing happens.
     * @return The load status if the target data is already loaded.
     */
    @NonNull
    public LoadStatus checkAndLoadTargetData(final IAndroidTarget target, IJavaProject project) {
        boolean loadData = false;
        LoadStatus loadStatus = LoadStatus.FAILED;
        String hashString = AndroidTargetHash.getTargetHashString(target);
        synchronized (LOCK) {
            loadStatus = targetDataMap.getLoadStatus(hashString);
            if (loadStatus == LoadStatus.LOADING) {
	        	TargetLoadProjects bundle = targetDataStatusMap.get(hashString);
	        	if (bundle == null) {
	        		// First time this target is hit
	        		loadData = true;
	        		bundle = new TargetLoadProjects();
	           		// add project to bundle
	        		if (project != null)
	        			bundle.projectsToReload.add(project);
	       	     	targetDataStatusMap.put(hashString, bundle);
	             }
            }
        }
        if (loadData) {
            Job job = new Job(String.format("Loading data for %1$s", target.getFullName())) {
                @Override
                protected IStatus run(IProgressMonitor monitor) {
                    AndmoreAndroidPlugin plugin = AndmoreAndroidPlugin.getDefault();
                    AndroidTargetData targetData = new AndroidTargetData(target);
                    try {
                        IStatus status = new AndroidTargetParser(target).run(targetData, monitor);

                        IJavaProject[] javaProjectArray = null;

                        synchronized (LOCK) {
                            TargetLoadProjects bundle = targetDataStatusMap.get(AndroidTargetHash.getTargetHashString(target));

                            if (status.getCode() != IStatus.OK) {
                            	targetDataMap.setFailedStatus(hashString);
                                bundle.projectsToReload.clear();
                            } else {
                            	targetDataMap.setTargetData(target, targetData);

                                // Prepare the array of project to recompile.
                                // The call is done outside of the synchronized block.
                                javaProjectArray = bundle.projectsToReload.toArray(
                                        new IJavaProject[bundle.projectsToReload.size()]);

                                // and update the UI of the editors that depend on the target data.
                                plugin.updateTargetListeners(target);
                            }
                        }

                        if ((javaProjectArray != null)  && (javaProjectArray.length > 0)) {
                            ProjectHelper.updateProjects(javaProjectArray);
                        }

                        return status;
                    } catch (Throwable t) {
                        synchronized (LOCK) {
                        	targetDataMap.setFailedStatus(hashString);
                        }

                        AndmoreAndroidPlugin.log(t, "Exception in checkAndLoadTargetData.");    //$NON-NLS-1$
                        return new Status(IStatus.ERROR, AndmoreAndroidConstants.ANDMORE_ID,
                                String.format(
                                        "Parsing Data for %1$s failed", //$NON-NLS-1$
                                        target.hashString()),
                                t);
                    } finally {
                    	targetDataStatusMap.remove(hashString);
                    }
                }
            };
            job.setPriority(Job.BUILD); // build jobs are run after other interactive jobs
            job.setRule(ResourcesPlugin.getWorkspace().getRoot());
            job.schedule();
        }
        return loadStatus;
    }

    /**
     * Return the {@link AndroidTargetData} for a given {@link IAndroidTarget}.
     */
    @Nullable
    public AndroidTargetData getTargetData(IAndroidTarget target) {
    	if (target == null)
    		return null;
        return targetDataMap.getTargetData(AndroidTargetHash.getTargetHashString(target));
    }

    /**
     * Return the {@link AndroidTargetData} for a given {@link IProject}.
     */
    @Nullable
    public AndroidTargetData getTargetData(IProject project) {
    	if (!projectRegistry.hasProjectState(project))
    		// Information not available while project is being opened
    		return null;
        synchronized (LOCK) {
            IAndroidTarget target = AndworxFactory.instance().getTarget(project);
            if (target != null) {
                return getTargetData(target);
            }
        }

        return null;
    }

    @NonNull
    public AndroidSdkHandler getAndroidSdkHandler() {
        return androidSdkHandler;
    }

    @Nullable
    public static AndroidVersion getDeviceVersion(@NonNull IDevice device) {
        try {
            String apiLevel = device.getSystemProperty(IDevice.PROP_BUILD_API_LEVEL).get();
            if (apiLevel == null) {
                return null;
            }
            String buildCodename = device.getSystemProperty(IDevice.PROP_BUILD_CODENAME).get();
            return new AndroidVersion(Integer.parseInt(apiLevel), buildCodename);
        } catch (NumberFormatException e) {
            return null;
        } catch (InterruptedException e) {
            return null;
		} catch (ExecutionException e) {
            return null;
		}
    }

    public Map<File, String> getExtraSamples() {
        Map<File, String> samples = new HashMap<File, String>();
        
        // TODO how do we find these properly?
        return samples;
    }

    /**
     * Unload the SDK's target data.
     *
     * If <var>preventReload</var>, this effect is final until the SDK instance is changed
     * through {@link #loadSdk(String)}.
     *
     * The goal is to unload the targets to be able to replace existing targets with new ones,
     * before calling {@link #loadSdk(String)} to fully reload the SDK.
     *
     * @param preventReload prevent the data from being loaded again for the remaining live of
     *   this {@link Sdk} instance.
     */
    public void unloadTargetData(boolean preventReload) {
        synchronized (LOCK) {
            // Dispose of the target data.
            targetDataMap.dispose();
        }
    }

	/**
     * Adds or edit a build tools marker from the given project. This is done through a Job.
     * @param project the project
     * @param markerMessage the message. if null the marker is removed.
     */
    void handleBuildToolsMarker(final IProject project, final String markerMessage) {
        Job markerJob = new Job("Android SDK: Build Tools Marker") {
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                try {
                    if (project.isAccessible()) {
                        // always delete existing marker first
                        project.deleteMarkers(AndmoreAndroidConstants.MARKER_BUILD_TOOLS, true,
                                IResource.DEPTH_ZERO);

                        // add the new one if needed.
                        if (markerMessage != null) {
                            BaseProjectHelper.markProject(project,
                                    AndmoreAndroidConstants.MARKER_BUILD_TOOLS,
                                    markerMessage, IMarker.SEVERITY_ERROR,
                                    IMarker.PRIORITY_HIGH);
                        }
                    }
                } catch (CoreException e2) {
                    AndmoreAndroidPlugin.log(e2, null);
                    // Don't return e2.getStatus(); the job control will then produce
                    // a popup with this error, which isn't very interesting for the
                    // user.
                }

                return Status.OK_STATUS;
            }
        };

        // build jobs are run after other interactive jobs
        markerJob.setPriority(Job.BUILD);
        markerJob.setRule(ResourcesPlugin.getWorkspace().getRoot());
        markerJob.schedule();
    }

    /**
     *  Cleans and unloads the SDK.
     */
    private void dispose() {
        GlobalProjectMonitor monitor = GlobalProjectMonitor.getMonitor();
        monitor.removeProjectListener(projectListener);
        monitor.removeFileListener(fileListener);
        monitor.removeResourceEventListener(mResourceEventListener);

        // the IAndroidTarget objects are now obsolete so update the project states.
        synchronized (LOCK) {
            projectRegistry.refresh();
            // dispose of the target data.
            targetDataMap.dispose();
       }
    }

    /**
     * Returns the URL to the local documentation.
     * Can return null if no documentation is found in the current SDK.
     *
     * @param osDocsPath Path to the documentation folder in the current SDK.
     *  The folder may not actually exist.
     * @return A file:// URL on the local documentation folder if it exists or null.
     */
    private String getDocumentationBaseUrl(String osDocsPath) {
        File f = new File(osDocsPath);

        if (f.isDirectory()) {
            try {
                // Note: to create a file:// URL, one would typically use something like
                // f.toURI().toURL().toString(). However this generates a broken path on
                // Windows, namely "C:\\foo" is converted to "file:/C:/foo" instead of
                // "file:///C:/foo" (i.e. there should be 3 / after "file:"). So we'll
                // do the correct thing manually.

                String path = f.getAbsolutePath();
                if (File.separatorChar != '/') {
                    path = path.replace(File.separatorChar, '/');
                }

                // For some reason the URL class doesn't add the mandatory "//" after
                // the "file:" protocol name, so it has to be hacked into the path.
                URL url = new URL("file", null, "//" + path);  //$NON-NLS-1$ //$NON-NLS-2$
                String result = url.toString();
                return result;
            } catch (MalformedURLException e) {
                // ignore malformed URLs
            }
        }

        return null;
    }

    void markProject(ProjectState projectState, boolean updateParents) {
        if (modifiedProjects.contains(projectState) == false) {
            if (DEBUG) {
                System.out.println("\tMARKED: " + projectState.getProject().getName());
            }
            modifiedProjects.add(projectState);
        }

        // if the project is resolved also add it to this list.
        if (updateParents) {
            if (modifiedChildProjects.contains(projectState) == false) {
                if (DEBUG) {
                    System.out.println("\tMARKED(child): " + projectState.getProject().getName());
                }
                modifiedChildProjects.add(projectState);
            }
        }
    }

    /**
     * Updates all existing projects with a given list of new/updated libraries.
     * This loops through all opened projects and check if they depend on any of the given
     * library project, and if they do, they are linked together.
     */
    private void updateParentProjects() {
        if (modifiedChildProjects.size() == 0) {
            return;
        }

        ArrayList<ProjectState> childProjects = new ArrayList<ProjectState>(modifiedChildProjects);
        modifiedChildProjects.clear();
        synchronized (LOCK) {
            // for each project for which we must update its parent, we loop on the parent
            // projects and adds them to the list of modified projects. If they are themselves
            // libraries, we add them too.
            for (ProjectState state : childProjects) {
                if (DEBUG) {
                    System.out.println(">>> Updating parents of " + state.getProject().getName());
                }
                List<ProjectState> parents = state.getParentProjects();
                for (ProjectState parent : parents) {
                    markProject(parent, parent.isLibrary());
                }
                if (DEBUG) {
                    System.out.println("<<<");
                }
            }
        }

        // done, but there may be parents that are also libraries. Need to update their parents.
        updateParentProjects();
    }

    /**
     * Fix editor associations for the given project, if not already done.
     * <p/>
     * Eclipse has a per-file setting for which editor should be used for each file
     * (see {@link IDE#setDefaultEditor(IFile, String)}).
     * We're using this flag to pick between the various XML editors (layout, drawable, etc)
     * since they all have the same file name extension.
     * <p/>
     * Unfortunately, the file setting can be "wrong" for two reasons:
     * <ol>
     *   <li> The editor type was added <b>after</b> a file had been seen by the IDE.
     *        For example, we added new editors for animations and for drawables around
     *        ADT 12, but any file seen by ADT in earlier versions will continue to use
     *        the vanilla Eclipse XML editor instead.
     *   <li> A bug in ADT 14 and ADT 15 (see issue 21124) meant that files created in new
     *        folders would end up with wrong editor associations. Even though that bug
     *        is fixed in ADT 16, the fix only affects new files, it cannot retroactively
     *        fix editor associations that were set incorrectly by ADT 14 or 15.
     * </ol>
     * <p/>
     * This method attempts to fix the editor bindings retroactively by scanning all the
     * resource XML files and resetting the editor associations.
     * Since this is a potentially slow operation, this is only done "once"; we use a
     * persistent project property to avoid looking repeatedly. In the future if we add
     * additional editors, we can rev the scanned version value.
     */
    void fixEditorAssociations(final IProject project) {
        QualifiedName KEY = new QualifiedName(AndmoreAndroidConstants.ANDMORE_ID, "editorbinding"); //$NON-NLS-1$

        try {
            String value = project.getPersistentProperty(KEY);
            int currentVersion = 0;
            if (value != null) {
                try {
                    currentVersion = Integer.parseInt(value);
                } catch (Exception ingore) {
                }
            }

            // The target version we're comparing to. This must be incremented each time
            // we change the processing here so that a new version of the plugin would
            // try to fix existing user projects.
            final int targetVersion = 2;

            if (currentVersion >= targetVersion) {
                return;
            }

            // Set to specific version such that we can rev the version in the future
            // to trigger further scanning
            project.setPersistentProperty(KEY, Integer.toString(targetVersion));

            // Now update the actual editor associations.
            Job job = new Job("Update Android editor bindings") { //$NON-NLS-1$
                @Override
                protected IStatus run(IProgressMonitor monitor) {
                 	ProjectState projectState = AndworxFactory.instance().getProjectState(project);
                	String resDirectory = projectState.getProjectSourceFolder(CodeSource.res);
                    try {
                        for (IResource folderResource : project.getFolder(resDirectory).members()) {
                            if (folderResource instanceof IFolder) {
                                IFolder folder = (IFolder) folderResource;

                                for (IResource resource : folder.members()) {
                                    if (resource instanceof IFile &&
                                            resource.getName().endsWith(DOT_XML)) {
                                        fixXmlFile((IFile) resource);
                                    }
                                }
                            }
                        }

                        // TODO change AndroidManifest.xml ID too

                    } catch (CoreException e) {
                        AndmoreAndroidPlugin.log(e, null);
                    }

                    return Status.OK_STATUS;
                }

                /**
                 * Attempt to fix the editor ID for the given /res XML file.
                 */
                private void fixXmlFile(final IFile file) {
                    // Fix the default editor ID for this resource.
                    // This has no effect on currently open editors.
                    IEditorDescriptor desc = IDE.getDefaultEditor(file);

                    if (desc == null || !CommonXmlEditor.ID.equals(desc.getId())) {
                        IDE.setDefaultEditor(file, CommonXmlEditor.ID);
                    }
                }
            };
            job.setPriority(Job.BUILD);
            job.schedule();
        } catch (CoreException e) {
            AndmoreAndroidPlugin.log(e, null);
        }
    }

    /**
     * Tries to fix all currently open Android legacy editors.
     * <p/>
     * If an editor is found to match one of the legacy ids, we'll try to close it.
     * If that succeeds, we try to reopen it using the new common editor ID.
     * <p/>
     * This method must be run from the UI thread.
     */
    void fixOpenLegacyEditors() {

        AndmoreAndroidPlugin adt = AndmoreAndroidPlugin.getDefault();
        if (adt == null) {
            return;
        }

        final IPreferenceStore store = adt.getPreferenceStore();
        int currentValue = store.getInt(AdtPrefs.PREFS_FIX_LEGACY_EDITORS);
        // The target version we're comparing to. This must be incremented each time
        // we change the processing here so that a new version of the plugin would
        // try to fix existing editors.
        final int targetValue = 1;

        if (currentValue >= targetValue) {
            return;
        }

        // To be able to close and open editors we need to make sure this is done
        // in the UI thread, which this isn't invoked from.
        PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {
            @Override
            public void run() {
                HashSet<String> legacyIds =
                    new HashSet<String>(Arrays.asList(CommonXmlEditor.LEGACY_EDITOR_IDS));

                for (IWorkbenchWindow win : PlatformUI.getWorkbench().getWorkbenchWindows()) {
                    for (IWorkbenchPage page : win.getPages()) {
                        for (IEditorReference ref : page.getEditorReferences()) {
                            try {
                                IEditorInput input = ref.getEditorInput();
                                if (input instanceof IFileEditorInput) {
                                    IFile file = ((IFileEditorInput)input).getFile();
                                    IEditorPart part = ref.getEditor(true /*restore*/);
                                    if (part != null) {
                                        IWorkbenchPartSite site = part.getSite();
                                        if (site != null) {
                                            String id = site.getId();
                                            if (legacyIds.contains(id)) {
                                                // This editor matches one of legacy editor IDs.
                                                fixEditor(page, part, input, file, id);
                                            }
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                // ignore
                            }
                        }
                    }
                }

                // Remember that we managed to do fix all editors
                store.setValue(AdtPrefs.PREFS_FIX_LEGACY_EDITORS, targetValue);
            }

            private void fixEditor(
                    IWorkbenchPage page,
                    IEditorPart part,
                    IEditorInput input,
                    IFile file,
                    String id) {
                IDE.setDefaultEditor(file, CommonXmlEditor.ID);

                boolean ok = page.closeEditor(part, true /*save*/);

                AndmoreAndroidPlugin.log(IStatus.INFO,
                    "Closed legacy editor ID %s for %s: %s", //$NON-NLS-1$
                    id,
                    file.getFullPath(),
                    ok ? "Success" : "Failed");//$NON-NLS-1$ //$NON-NLS-2$

                if (ok) {
                    // Try to reopen it with the new ID
                    try {
                        page.openEditor(input, CommonXmlEditor.ID);
                    } catch (PartInitException e) {
                        AndmoreAndroidPlugin.log(e,
                            "Failed to reopen %s",          //$NON-NLS-1$
                            file.getFullPath());
                    }
                }
            }
        });
    }

    // Clear the layout lib cache associated with this project
	public void clearCaches(IProject removedProject) {
        IAndroidTarget target = projectRegistry.getProjectState(removedProject).getProfile().getTarget();
        if (target != null) {
            // get the bridge for the target, and clear the cache for this project.
            AndroidTargetData data = targetDataMap.getTargetData(AndroidTargetHash.getTargetHashString(target));
            if (data != null) {
                LayoutLibrary layoutLib = data.getLayoutLibrary();
                if (layoutLib != null && layoutLib.getStatus() == LoadStatus.LOADED) {
                    layoutLib.clearCaches(removedProject);
                }
            }
        }
	}

	public void closeLibrary(LibraryState libState) {
        libState.close();

        // record that this project changed, and in case it's a library
        // that its parents need to be updated as well.
        ProjectState projectState = libState.getProjectState();
        markProject(projectState, projectState.isLibrary());
	}

}
