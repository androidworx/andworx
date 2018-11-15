/*
 * Copyright (C) 2007 The Android Open Source Project
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

package org.eclipse.andmore.internal.build.builders;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.eclipse.andmore.AndmoreAndroidConstants;
import org.eclipse.andmore.AndmoreAndroidPlugin;
import org.eclipse.andmore.internal.build.Messages;
import org.eclipse.andmore.internal.build.RsSourceChangeHandler;
import org.eclipse.andmore.internal.build.SourceProcessor;
import org.eclipse.andmore.internal.lint.EclipseLintClient;
import org.eclipse.andmore.internal.preferences.AdtPrefs.BuildVerbosity;
import org.eclipse.andmore.internal.project.BaseProjectHelper;
import org.eclipse.andmore.internal.project.ProjectHelper;
import org.eclipse.andmore.internal.resources.manager.ProjectResources;
import org.eclipse.andmore.internal.resources.manager.ResourceManager;
import org.eclipse.andworx.build.AndworxFactory;
import org.eclipse.andworx.context.VariantContext;
import org.eclipse.andworx.log.SdkLogger;
import org.eclipse.andworx.registry.ProjectState;
import org.eclipse.andworx.task.TaskFactory;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceStatus;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;

import com.android.SdkConstants;
import com.android.builder.core.BuilderConstants;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.build.legacy.RenderScriptChecker;
import com.android.utils.FileUtils;

/**
 * Pre Java Compiler.
 * This incremental builder performs multiple tasks, inclding:
 * <ul>
 * <li>compiles the resources located in the res/ folder, along with the
 * AndroidManifest.xml file into the R.java class.</li>
 * <li>compiles any .aidl files into a corresponding java file.</li>
 * </ul>
 * This class is a PreCompilerContext implementation which facilitates the transfer of
 * context information between tasks. 
 */
public class PreCompilerBuilder extends BaseBuilder implements PreCompilerContext {

    /** This ID is used in plugin.xml and in each project's .project file.
     * It cannot be changed even if the class is renamed/moved */
    public static final String ID = "org.eclipse.andmore.PreCompilerBuilder"; //$NON-NLS-1$

    protected static final String PROPERTY_COMPILE_RESOURCES = "compileResources"; //$NON-NLS-1$
    private static final String PROPERTY_PACKAGE = "manifestPackage"; //$NON-NLS-1$
    private static final String PROPERTY_MERGE_MANIFEST = "mergeManifest"; //$NON-NLS-1$
    private static final String PROPERTY_COMPILE_BUILDCONFIG = "createBuildConfig"; //$NON-NLS-1$
    private static final String PROPERTY_BUILDCONFIG_MODE = "buildConfigMode"; //$NON-NLS-1$

    static final boolean MANIFEST_MERGER_ENABLED_DEFAULT = false;
    static final String MANIFEST_MERGER_PROPERTY = "manifestmerger.enabled"; //$NON-NLS-1$

    private static SdkLogger logger = SdkLogger.getLogger(PreCompilerBuilder.class.getName());
    
    /**
     * Progress monitor waiting the end of the process to set a persistent value
     * in a file. This is typically used in conjunction with <code>IResource.refresh()</code>,
     * since this call is asynchronous, and we need to wait for it to finish for the file
     * to be known by eclipse, before we can call <code>resource.setPersistentProperty</code> on
     * a new file.
     */
    private static class DerivedProgressMonitor implements IProgressMonitor {
        private boolean mCancelled = false;
        private boolean mDone = false;
        private final IFolder genFolder;

        public DerivedProgressMonitor(IFolder genFolder) {
            this.genFolder = genFolder;
        }

        void reset() {
            mDone = false;
        }

        @Override
        public void beginTask(String name, int totalWork) {
        }

        @Override
        public void done() {
            if (mDone == false) {
                mDone = true;
                processChildrenOf(genFolder);
            }
        }

        private void processChildrenOf(IFolder folder) {
            IResource[] list;
            try {
                list = folder.members();
            } catch (CoreException e) {
                return;
            }

            for (IResource member : list) {
                if (member.exists()) {
                    if (member.getType() == IResource.FOLDER) {
                        processChildrenOf((IFolder) member);
                    }

                    try {
                        member.setDerived(true, new NullProgressMonitor());
                    } catch (CoreException e) {
                        // This really shouldn't happen since we check that the resource
                        // exist.
                        // Worst case scenario, the resource isn't marked as derived.
                    }
                }
            }
        }

        @Override
        public void internalWorked(double work) {
        }

        @Override
        public boolean isCanceled() {
            return mCancelled;
        }

        @Override
        public void setCanceled(boolean value) {
            mCancelled = value;
        }

        @Override
        public void setTaskName(String name) {
        }

        @Override
        public void subTask(String name) {
        }

        @Override
        public void worked(int work) {
        }
    }

    private final TaskFactory taskFactory;
    /** Merge Manifest Flag. Computed from resource delta, reset after action is taken.
     * Stored persistently in the project. */
    private boolean mustMergeManifest = false;
    /** Resource compilation Flag. Computed from resource delta, reset after action is taken.
     * Stored persistently in the project. */
    private boolean mustCompileResources = false;
    /** BuildConfig Flag. Computed from resource delta, reset after action is taken.
     * Stored persistently in the project. */
    private boolean mustCreateBuildConfig = false;
    /** BuildConfig last more Flag. Computed from resource delta, reset after action is taken.
     * Stored persistently in the project. */
    private boolean lastBuildConfigMode;

    /** Java package defined in the manifest */
    private String manifestPackage;

    /** Output folder for generated Java Files. Created on the Builder init
     * @see #startupOnInitialize()
     */
    private IFolder genFolder;
    
    /** Status outcomes of AIDL and Renderscript builds */
    private int processorStatus = SourceProcessor.COMPILE_STATUS_NONE;

    /**
     * Progress monitor used at the end of every build to refresh the content of the 'gen' folder
     * and set the generated files as derived.
     */
    private DerivedProgressMonitor derivedProgressMonitor;

    /** AIDL processor executes AIDL compile application. TODO - Replace with AndroidBuilder implementation */
    //private AidlProcessor mAidlProcessor;
    /** Render script processor */
    private RsSourceChangeHandler renderScriptSourceChangeHandler;
    /** Build operation queue for executing tasks sequentially according to build kind and project change status */
    private Deque<BuildOp<PreCompilerContext>> buildOpQueue;

    /**
     * Default PreCompilerBuilder constructor required by Eclipse framwork
     */
    public PreCompilerBuilder() {
        super();
        buildOpQueue = new ArrayDeque<>();
        taskFactory = AndworxFactory.instance().getTaskFactory();
    }

	@Override
	public TaskFactory getTaskFactory() {
		return taskFactory;
	}
	
	@Override
	public boolean isMustMergeManifest() {
		return mustMergeManifest;
	}

	@Override
	public void setMustMergeManifest(boolean mustMergeManifest) {
		this.mustMergeManifest = mustMergeManifest;
	}

	@Override
	public void saveMustMergeManifest(boolean mustMergeManifest) {
		this.mustMergeManifest = mustMergeManifest;
        saveProjectBooleanProperty(PROPERTY_MERGE_MANIFEST, mustMergeManifest);
	}

	@Override
	public boolean isMustCompileResources() {
		return mustCompileResources;
	}

	@Override
	public void setMustCompileResources(boolean mustCompileResources) {
		this.mustCompileResources = mustCompileResources;
	}

	@Override
	public void saveMustCompileResources(boolean mustCompileResources) {
		this.mustCompileResources = mustCompileResources;
        saveProjectBooleanProperty(PROPERTY_COMPILE_RESOURCES, mustCompileResources);
	}

	@Override
	public boolean isMustCreateBuildConfig() {
		return mustCreateBuildConfig;
	}

	@Override
	public void setMustCreateBuildConfig(boolean mustCreateBuildConfig) {
		this.mustCreateBuildConfig = mustCreateBuildConfig;
	}
	@Override
	public void saveMustCreateBuildConfig(boolean mustCreateBuildConfig) {
		this.mustCreateBuildConfig = mustCreateBuildConfig;
        saveProjectBooleanProperty(PROPERTY_COMPILE_BUILDCONFIG, mustCreateBuildConfig);
	}

	@Override
	public String getManifestPackage (){
		return manifestPackage;
	}

	@Override
	public void saveManifestPackage(String manifestPackage) {
		this.manifestPackage = manifestPackage;
        saveProjectStringProperty(PROPERTY_PACKAGE, manifestPackage);
	}

	@Override
	public boolean getLastBuildConfigMode() {
		return lastBuildConfigMode;
	}
	
	@Override
	public void saveLastBuildConfigMode(boolean lastBuildConfigMode) {
		this.lastBuildConfigMode = lastBuildConfigMode;
        saveProjectBooleanProperty(PROPERTY_BUILDCONFIG_MODE, this.lastBuildConfigMode = lastBuildConfigMode);
	}
	
	@Override
	public IFolder getGenFolder() {
		return genFolder;
	}

    /**
     * Returns an {@link IFolder} (located inside the 'gen' source folder), that matches the
     * package defined in the manifest. This {@link IFolder} may not actually exist
     * (aapt will create it anyway).
     * @return the {@link IFolder} that will contain the R class or null if
     * the folder was not found.
     * @throws CoreException
     */
	@Override
	public IFolder getGenManifestPackageFolder() throws CoreException {
        // Path for the package
        IPath packagePath = getJavaPackagePath(manifestPackage);
        // Return folder for this path under the 'gen' source folder.
        return genFolder.getFolder(packagePath);
    }

	@Override
	public IJavaProject getJavaProject() {
		return JavaCore.create(getProject());
	}

	@Override
	public VariantContext getVariantContext() {
		return projectRegistry.getProjectState(getProject()).getContext();
	}

	@Override
	public BuildToolInfo getBuildToolInfo() throws AbortBuildException {
        BuildToolInfo buildToolInfo = getBuildToolInfo(getJavaProject());
        if (buildToolInfo == null)
        	throw new AbortBuildException();
        return buildToolInfo;
	}

	@Override
	public IProgressMonitor getDerivedProgressMonitor() {
		return derivedProgressMonitor;
	}

	@Override
	public int getSourceProcessorStatus() {
		return processorStatus;
	}
	
	@Override
	public void addSourceProcessorStatus(int processorStatus) {
		processorStatus |= processorStatus;
	}
/*
	@Override
	public AidlProcessor initializeAidl(boolean isFullBuild) throws AbortBuildException {
		BuildToolInfo buildToolInfo = getBuildToolInfo();
        if (mAidlProcessor == null) {
            mAidlProcessor = new AidlProcessor(getJavaProject(), buildToolInfo, getGenFolder());
        } else { // BuildTool version may be project-specific
            mAidlProcessor.setBuildToolInfo(buildToolInfo);
        }
        if (isFullBuild)
        	mAidlProcessor.prepareFullBuild(getProject());
        return mAidlProcessor;
	}
*/
	@Override
	public RsSourceChangeHandler initializeRenderScript(RenderScriptChecker checker, boolean isFullBuild) {
        renderScriptSourceChangeHandler = new RsSourceChangeHandler(checker);
        if (isFullBuild)
            renderScriptSourceChangeHandler.prepareFullBuild();
       return renderScriptSourceChangeHandler;
	}

	@Override
	public void removeMarkersFromContainer(IFolder resFolder, String markerId) {
		super.removeMarkersFromContainer(resFolder, markerId);
	}

	@Override
	public boolean isDebugLog() {
		return DEBUG_LOG;
	}

	/**
	 * Request to clean project - only automattically applied at start of full build
	 */
	@Override
	public void cleanProject(IProgressMonitor monitor) throws CoreException {
        // force a clean
		IProject project = getProject();
        doClean(project, monitor);
        mustMergeManifest = true;
        mustCompileResources = true;
        mustCreateBuildConfig = true;
        //mAidlProcessor.prepareFullBuild(project);
        renderScriptSourceChangeHandler.prepareFullBuild();

        saveProjectBooleanProperty(PROPERTY_MERGE_MANIFEST, mustMergeManifest);
        saveProjectBooleanProperty(PROPERTY_COMPILE_RESOURCES, mustCompileResources);
        saveProjectBooleanProperty(PROPERTY_COMPILE_BUILDCONFIG, mustCreateBuildConfig);
	}

	/**
	 * Runs this builder in the specified manner. Subclasses should implement
	 * this method to do the processing they require.
	 * <p>
	 * If the build kind is {@link #INCREMENTAL_BUILD} or
	 * {@link #AUTO_BUILD}, the <code>getDelta</code> method can be
	 * used during the invocation of this method to obtain information about
	 * what changes have occurred since the last invocation of this method. Any
	 * resource delta acquired is valid only for the duration of the invocation
	 * of this method.  A {@link #FULL_BUILD} has no associated build delta.
	 * </p>
	 * <p>
	 * After completing a build, this builder may return a list of projects for
	 * which it requires a resource delta the next time it is run. This
	 * builder's project is implicitly included and need not be specified. The
	 * build mechanism will attempt to maintain and compute deltas relative to
	 * the identified projects when asked the next time this builder is run.
	 * Builders must re-specify the list of interesting projects every time they
	 * are run as this is not carried forward beyond the next build. Projects
	 * mentioned in return value but which do not exist will be ignored and no
	 * delta will be made available for them.
	 * </p>
	 * <p>
	 * This method is long-running; progress and cancellation are provided by
	 * the given progress monitor. All builders should report their progress and
	 * honor cancel requests in a timely manner. Cancelation requests should be
	 * propagated to the caller by throwing
	 * <code>OperationCanceledException</code>.
	 * </p>
	 * <p>
	 * All builders should try to be robust in the face of trouble. In
	 * situations where failing the build by throwing <code>CoreException</code>
	 * is the only option, a builder has a choice of how best to communicate the
	 * problem back to the caller. One option is to use the
	 * {@link IResourceStatus#BUILD_FAILED} status code along with a suitable message;
	 * another is to use a {@link MultiStatus} containing finer-grained problem
	 * diagnoses.
	 * </p>
	 *
	 * @param kind the kind of build being requested. Valid values are
	 * <ul>
	 * <li>{@link #FULL_BUILD} - indicates a full build.</li>
	 * <li>{@link #INCREMENTAL_BUILD}- indicates an incremental build.</li>
	 * <li>{@link #AUTO_BUILD} - indicates an automatically triggered
	 * incremental build (autobuilding on).</li>
	 * </ul>
	 * @param args a table of builder-specific arguments keyed by argument name
	 * (key type: <code>String</code>, value type: <code>String</code>);
	 * <code>null</code> is equivalent to an empty map
	 * @param monitor a progress monitor, or <code>null</code> if progress
	 * reporting and cancellation are not desired
	 * @return the list of projects for which this builder would like deltas the
	 * next time it is run or <code>null</code> if none
	 * @exception CoreException if this build fails.
	 * @see IProject#build(int, String, Map, IProgressMonitor)
	 */
    @Override
    protected IProject[] build(
            int kind,
            Map<String,String> args,
            IProgressMonitor monitor)
            throws CoreException {
        // get a project object
        IProject project = getProject();
		if (logger.isLoggable(Level.INFO))
            logger.info("%s BUILD(PRE)", project.getName());

        // For the PreCompiler, only the library projects are considered Referenced projects,
        // as only those projects have an impact on what is generated by this builder.
        IProject[] result = null;

        IFolder resOutFolder = null;
        try {
            assert derivedProgressMonitor != null;
            derivedProgressMonitor.reset();

            // get the project info
            ProjectState projectState = projectRegistry.getProjectState(project);
            VariantContext variantContext = projectState.getContext();
            if (args.containsKey(RELEASE_REQUESTED))
				variantContext.setBuildType(BuilderConstants.RELEASE);
            else
				variantContext.setBuildType(BuilderConstants.DEBUG);
            if (projectState.isLibrary())
            	// Do not build libraries until AAR development supported
            	return result;
            IJavaProject javaProject = getJavaProject();
            // Top level check to make sure the build can move forward.
            abortOnBadSetup(javaProject, projectState);
            resOutFolder = BaseProjectHelper.getAndroidOutputFolder(project);
            PreparePreCompile preparePreCompile = new PreparePreCompile(this,kind, monitor);
    		if (logger.isLoggable(Level.FINEST))
    			logger.verbose("Start pre-compile for project %s", project.getName());
            if (!preparePreCompile.prepareProject())
            	return result;
    		if (logger.isLoggable(Level.FINEST))
    			logger.verbose("Validating pre-compile for project %s", project.getName());
            ValidatePreCompile validatePreCompile = new ValidatePreCompile(this, preparePreCompile, monitor);
            if (!validatePreCompile.isProjectValid())
            	return result;
            // Generate build config source file
            buildOpQueue.push(new BuildConfigOp());
            List<IProject> libProjects = new LinkedList<IProject>(projectState.getFullLibraryProjects());
            for (Iterator<IProject> iter = libProjects.iterator(); iter.hasNext();) {
                IProject libProject = iter.next();
                if (!libProject.isOpen()) {
                    iter.remove();
                }
            }
           // Merge the manifests
            buildOpQueue.push(new MergeManifestOp());
            // Compile AIDL source
            buildOpQueue.push(new AidlOp());
            // Compile Render scrips
            buildOpQueue.push(new RenderScriptOp());
            // Compile resources
        	buildOpQueue.push(new ResourcesOp(monitor));
        	buildOpQueue.push(new BindResourcesOp());
        	int count = buildOpQueue.size();
    		taskFactory.start();
        	while (count > 0) {
        		BuildOp<PreCompilerContext> buildOp = buildOpQueue.removeLast();
        		if (logger.isLoggable(Level.FINEST))
        			logger.verbose("Executing %s for project %s", buildOp.getDescription(), project.getName());
        		if (!buildOp.execute(this)) {
            		if (logger.isLoggable(Level.INFO))
            			logger.info("%s failed", buildOp.getDescription());
	                break;
        		}
	        	// Place buildOp back on queue for commit
	        	buildOpQueue.push(buildOp);
	        	--count;
       	    }
        	if (count > 0) {
        		System.err.println("Pre-compile failed");
        		return result;
        	}
        	File sourceOutputDir = getGenFolder().getLocation().toFile();
        	if (sourceOutputDir.exists())
				try {
					FileUtils.cleanOutputDir(sourceOutputDir);
				} catch (IOException e) {
					// Report error but continue for best effort
					handleException(e, "Error cleaning directory " + sourceOutputDir);
				}
        	while (!buildOpQueue.isEmpty()) {
        		BuildOp<PreCompilerContext> buildOp = null;
        		try {
        			buildOp = buildOpQueue.removeLast();
            		if (logger.isLoggable(Level.FINEST))
            			logger.verbose("Commiting %s for project %s", buildOp.getDescription(), project.getName());
       			    buildOp.commit(this);
					System.out.println(buildOp.getDescription() + " completed");
				} catch (IOException | SecurityException e) {
					// Report error but continue for best effort
					handleException(e, buildOp.getDescription() + " commit failed due to file system error");
				}
        	}
        } catch (AbortBuildException e) {
            return result;
        } catch (InterruptedException e) {
        	Thread.interrupted();
            return result;
        } finally {
        	taskFactory.stop();
            // refresh the 'gen' source folder. Once this is done with the custom progress
            // monitor to mark all new files as derived
            genFolder.refreshLocal(IResource.DEPTH_INFINITE, derivedProgressMonitor);
            if (resOutFolder != null) {
                resOutFolder.refreshLocal(IResource.DEPTH_INFINITE, derivedProgressMonitor);
            }
        }
        return result;
    }

	/**
	 * Clean is an opportunity for a builder to discard any additional state that has
	 * been computed as a result of previous builds. It is recommended that builders
	 * override this method to delete all derived resources created by previous builds,
	 * and to remove all markers of type {@link IMarker#PROBLEM} that
	 * were created by previous invocations of the builder. The platform will
	 * take care of discarding the builder's last built state (there is no need
	 * to call <code>forgetLastBuiltState</code>).
	 * </p>
	 * <p>
	 * This method is called as a result of invocations of
	 * <code>IWorkspace.build</code> or <code>IProject.build</code> where
	 * the build kind is {@link #CLEAN_BUILD}.
	 * <p>
	 * This default implementation does nothing. Subclasses may override.
	 * <p>
	 * This method is long-running; progress and cancellation are provided by
	 * the given progress monitor. All builders should report their progress and
	 * honor cancel requests in a timely manner. Cancelation requests should be
	 * propagated to the caller by throwing
	 * <code>OperationCanceledException</code>.
	 * </p>
	 *
	 * @param monitor a progress monitor, or <code>null</code> if progress
	 * reporting and cancellation are not desired
	 * @exception CoreException if this build fails.
	 * @see IWorkspace#build(int, IProgressMonitor)
	 * @see #CLEAN_BUILD
	 */
    @Override
    protected void clean(IProgressMonitor monitor) throws CoreException {
        super.clean(monitor);

        doClean(getProject(), monitor);
        if (genFolder != null) {
            genFolder.refreshLocal(IResource.DEPTH_INFINITE, monitor);
        }
    }

	/**
	 * Informs this builder that it is being started by the build management
	 * infrastructure. By the time this method is run, the builder's project is
	 * available and <code>setInitializationData</code> has been called. The
	 * default implementation should be called by all overriding methods.
	 *
	 * @see #setInitializationData(IConfigurationElement, String, Object)
	 */
    @Override
    protected void startupOnInitialize() {
        try {
            super.startupOnInitialize();

            IProject project = getProject();
            // load the previous IFolder and java package.
            manifestPackage = loadProjectStringProperty(PROPERTY_PACKAGE);

            // get the source folder in which all the Java files are created
            genFolder = project.getFolder(SdkConstants.FD_GEN_SOURCES);
            derivedProgressMonitor = new DerivedProgressMonitor(genFolder);

            // Load the current compile flags. We ask for true if not found to force a recompile.
            mustMergeManifest = loadProjectBooleanProperty(PROPERTY_MERGE_MANIFEST, true);
            mustCompileResources = loadProjectBooleanProperty(PROPERTY_COMPILE_RESOURCES, true);
            mustCreateBuildConfig = loadProjectBooleanProperty(PROPERTY_COMPILE_BUILDCONFIG, true);
            Boolean buildConfigMode = ProjectHelper.loadBooleanProperty(project, PROPERTY_BUILDCONFIG_MODE);
            if (buildConfigMode == null) {
                // no previous build config mode? force regenerate
                mustCreateBuildConfig = true;
            } else {
                lastBuildConfigMode = buildConfigMode;
            }

        } catch (Throwable throwable) {
            AndmoreAndroidPlugin.log(throwable, "Failed to finish PrecompilerBuilder#startupOnInitialize()");
        }
    }

    private void doClean(IProject project, IProgressMonitor monitor) throws CoreException {
        AndmoreAndroidPlugin.printBuildToConsole(BuildVerbosity.VERBOSE, project,
                Messages.Removing_Generated_Classes);

		if (logger.isLoggable(Level.FINEST))
			logger.verbose("%s CLEAN(PRE)", project.getName());
        // remove all the derived resources from the 'gen' source folder.
        if (genFolder != null && genFolder.exists()) {
            // gen folder should not be derived, but previous version could set it to derived
            // so we make sure this isn't the case (or it'll get deleted by the clean)
            genFolder.setDerived(false, monitor);

            removeDerivedResources(genFolder, monitor);
        }

        // Clear the project of the generic markers
        removeMarkersFromContainer(project, AndmoreAndroidConstants.MARKER_AAPT_COMPILE);
        removeMarkersFromContainer(project, AndmoreAndroidConstants.MARKER_XML);
        removeMarkersFromContainer(project, AndmoreAndroidConstants.MARKER_AIDL);
        removeMarkersFromContainer(project, AndmoreAndroidConstants.MARKER_RENDERSCRIPT);
        removeMarkersFromContainer(project, AndmoreAndroidConstants.MARKER_MANIFMERGER);
        removeMarkersFromContainer(project, AndmoreAndroidConstants.MARKER_ANDROID);

        // Also clean up lint
        EclipseLintClient.clearMarkers(project);

        // clean the project repo
        ProjectResources res = ResourceManager.getInstance().getProjectResources(project);
        res.clear();
    }

    /**
     * Creates a relative {@link IPath} from a java package.
     * @param javaPackageName the java package.
     */
    private IPath getJavaPackagePath(String javaPackageName) {
        // convert the java package into path
        String[] segments = javaPackageName.split(AndmoreAndroidConstants.RE_DOT);

        StringBuilder path = new StringBuilder();
        for (String s : segments) {
           path.append(AndmoreAndroidConstants.WS_SEP_CHAR);
           path.append(s);
        }

        return new Path(path.toString());
    }

}
