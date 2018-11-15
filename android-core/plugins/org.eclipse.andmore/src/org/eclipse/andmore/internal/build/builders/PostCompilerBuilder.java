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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import org.eclipse.andmore.AndmoreAndroidConstants;
import org.eclipse.andmore.AndmoreAndroidPlugin;
import org.eclipse.andmore.AndroidPrintStream;
import org.eclipse.andmore.internal.build.BuildHelper;
import org.eclipse.andmore.internal.build.BuildHelper.ResourceMarker;
import org.eclipse.andmore.internal.build.Messages;
import org.eclipse.andmore.internal.preferences.AdtPrefs;
import org.eclipse.andmore.internal.preferences.AdtPrefs.BuildVerbosity;
import org.eclipse.andmore.internal.project.BaseProjectHelper;
import org.eclipse.andmore.internal.project.ProjectHelper;
import org.eclipse.andworx.build.AndworxFactory;
import org.eclipse.andworx.build.OutputType;
import org.eclipse.andworx.context.VariantContext;
import org.eclipse.andworx.registry.ProjectState;
import org.eclipse.andworx.task.TaskFactory;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jdt.core.IJavaModelMarker;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.TransformInput;
import com.android.builder.core.BuilderConstants;
import com.android.sdklib.BuildToolInfo;

public class PostCompilerBuilder extends BaseBuilder implements PostCompilerContext {

    /** This ID is used in plugin.xml and in each project's .project file.
     * It cannot be changed even if the class is renamed/moved */
    public static final String ID = "org.eclipse.andmore.ApkBuilder"; //$NON-NLS-1$

    private static final String PROPERTY_CONVERT_TO_DEX = "convertToDex"; //$NON-NLS-1$
    private static final String PROPERTY_PACKAGE_RESOURCES = "packageResources"; //$NON-NLS-1$
    private static final String PROPERTY_BUILD_APK = "buildApk"; //$NON-NLS-1$

    /** Flag to pass to PostCompiler builder that sets if it runs or not.
     *  Set this flag whenever calling build if PostCompiler is to run
     */
    public final static String POST_C_REQUESTED = "RunPostCompiler"; //$NON-NLS-1$

    private final TaskFactory taskFactory;
    
    /**
     * Dex conversion flag. This is set to true if one of the changed/added/removed
     * file is a .class file. Upon visiting all the delta resource, if this
     * flag is true, then we know we'll have to make the "classes.dex" file.
     */
    private boolean convertToDex = false;

    /**
     * Final package build flag.
     */
    private boolean buildFinalPackage = false;

    private AndroidPrintStream outStream = null;
    private AndroidPrintStream errStream = null;
    // Benchmarking start
    private long startBuildTime = 0;

    /** Build operation queue for executing tasks sequentially according to build kind and project change status */
    private Deque<BuildOp<PostCompilerContext>> buildOpQueue;

    private List<TransformInput> pipelineList;
    private ResourceMarker resourceMarker = new ResourceMarker() {
        @Override
        public void setWarning(IResource resource, String message) {
            BaseProjectHelper.markResource(resource, AndmoreAndroidConstants.MARKER_PACKAGING,
                    message, IMarker.SEVERITY_WARNING);
        }
    };

    public PostCompilerBuilder() {
        super();
        buildOpQueue = new ArrayDeque<>();
        pipelineList = new ArrayList<>();
        taskFactory = AndworxFactory.instance().getTaskFactory();
    }

	@Override
	public IJavaProject getJavaProject() {
		return JavaCore.create(getProject());
	}

	@Override
	public TaskFactory getTaskFactory() {
		return taskFactory;
	}
	
	@Override
	public boolean isConvertToDex() {
		return convertToDex;
	}

	@Override
	public void setConvertToDex(boolean value) {
		convertToDex = value;
	}

	@Override
	public void saveConvertToDex(boolean value) {
		convertToDex = value;
        saveProjectBooleanProperty(PROPERTY_CONVERT_TO_DEX, value);
	}

	@Override
	public boolean isBuildFinalPackage() {
		return buildFinalPackage;
	}

	@Override
	public void setBuildFinalPackage(boolean value) {
		buildFinalPackage=  value;
	}

	@Override
	public void saveBuildFinalPackage(boolean value) {
		 buildFinalPackage=  value;
         saveProjectBooleanProperty(PROPERTY_BUILD_APK, value);
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
	public void setStartBuildTime(long value) {
		startBuildTime = value;
	}

	@Override
	public boolean isDebugLog() {
		return DEBUG_LOG;
	}

	@Override
	public ResourceMarker getResourceMarker() {
		return resourceMarker;
	}
	
	@Override
	public AndroidPrintStream getOutStream() {
		return outStream;
	}
	
	@Override
	public AndroidPrintStream getErrStream() {
		return errStream;
	}
    
    @Override
    protected void clean(IProgressMonitor monitor) throws CoreException {
        super.clean(monitor);

        // Get the project.
        IProject project = getProject();

        if (DEBUG_LOG) {
            AndmoreAndroidPlugin.log(IStatus.INFO, "%s CLEAN(POST)", project.getName());
        }

        // Clear the project of the generic markers
        removeMarkersFromContainer(project, AndmoreAndroidConstants.MARKER_AAPT_PACKAGE);
        removeMarkersFromContainer(project, AndmoreAndroidConstants.MARKER_PACKAGING);

        // also remove the files in the output folder (but not the Eclipse output folder).
        IFolder javaOutput = BaseProjectHelper.getJavaOutputFolder(project);
        IFolder androidOutput = BaseProjectHelper.getAndroidOutputFolder(project);

        if (javaOutput.equals(androidOutput) == false) {
            // get the content
            IResource[] members = androidOutput.members();
            for (IResource member : members) {
                if (member.equals(javaOutput) == false) {
                    member.delete(true /*force*/, monitor);
                }
            }
        }
    }

    // build() returns a list of project from which this project depends for future compilation.
    @Override
    protected IProject[] build(
            int kind,
            @SuppressWarnings("rawtypes") Map args,
            IProgressMonitor monitor)
            throws CoreException {
        // Delay build if project target is being loaded
        IProject project = getProject();
        // list of referenced projects. This is a mix of java projects and library projects
        // and is computed below.
        IProject[] allRefProjects = null;

        try {
            VariantContext variantScope = getVariantContext();
            if (!variantScope.hasOutput(OutputType.MERGED_MANIFESTS))
            	// Build cannot proceed
        		return allRefProjects;
    		AdtPrefs adtPrefs = AndmoreAndroidPlugin.getDefault().getAdtPrefs();
         	PreparePostCompile preparePostCompile = new PreparePostCompile(this, kind, monitor);
        	if (preparePostCompile.prepareProject()) {
                // First thing we do is go through the resource delta to not
                // lose it if we have to abort the build for any reason.
                if (!(args.containsKey(POST_C_REQUESTED) &&
                        adtPrefs.getBuildSkipPostCompileOnFileSave())) 
                 	preparePostCompile.setBuildFlags();
        	} else // Error exit
        		return allRefProjects;
            // Top level check to make sure the build can move forward. Only do this after recording
            // delta changes.
            IJavaProject javaProject = JavaCore.create(project);
            ProjectState projectState = projectRegistry.getProjectState(project);
            VariantContext variantContext = projectState.getContext();
            if (args.containsKey(RELEASE_REQUESTED))
				variantContext.setBuildType(BuilderConstants.RELEASE);
            else
				variantContext.setBuildType(BuilderConstants.DEBUG);
            abortOnBadSetup(javaProject, projectState);
            // Get the libraries
            List<IProject> libProjects = projectState.getFullLibraryProjects();
            // get the list of referenced projects.
            List<IProject> javaProjects = ProjectHelper.getReferencedProjects(project);
            // mix the java project and the library projects
            final int size = libProjects.size() + javaProjects.size();
            ArrayList<IProject> refList = new ArrayList<IProject>(size);
            refList.addAll(libProjects);
            refList.addAll(javaProjects);
            allRefProjects = refList.toArray(new IProject[size]);

            // Get the output stream. Since the builder is created for the life of the
            // project, they can be kept around.
            if (outStream == null) {
                outStream = new AndroidPrintStream(project.getName(), null /*prefix*/,
                        AndmoreAndroidPlugin.getOutStream());
                errStream = new AndroidPrintStream(project.getName(), null /*prefix*/,
                        AndmoreAndroidPlugin.getOutStream());
            }

            // remove older packaging markers.
            removeMarkersFromContainer(project, AndmoreAndroidConstants.MARKER_PACKAGING);

            // Special case of the library
            BuildToolInfo buildToolInfo = getBuildToolInfo();
             // get the android output folder
            IFolder androidOutputFolder = BaseProjectHelper.getAndroidOutputFolder(project);
            BuildHelper helper = 
                	new BuildHelper(
                		AndworxFactory.instance().getProjectRegistry(),
                        projectState,
                        buildToolInfo,
                        outStream, errStream,
                        true, // debugMode
                        adtPrefs.getBuildVerbosity() == BuildVerbosity.VERBOSE,
                        resourceMarker);
            if (projectState.isLibrary()) 
            	buildOpQueue.push(new LibraryPostCompileOp(androidOutputFolder, monitor));
            else {
            	/* TODO - Determine if APK should be packaged every build
	            // Check to see if we're going to launch or export. If not, we can skip
	            // the packaging and dexing process.
	            if (!args.containsKey(POST_C_REQUESTED)
	                    && adtPrefs.getBuildSkipPostCompileOnFileSave()) {
	                AndmoreAndroidPlugin.printBuildToConsole(BuildVerbosity.VERBOSE, project,
	                        Messages.Skip_Post_Compiler);
	                return allRefProjects;
	            } else */ {
	                AndmoreAndroidPlugin.printBuildToConsole(BuildVerbosity.VERBOSE, project,
	                        Messages.Start_Full_Post_Compiler);
	            }
	            // Check for presence of files before proceeding 
	            MonitorResourcesOp monitorResourcesOp = new MonitorResourcesOp(androidOutputFolder, monitor);
	            monitorResourcesOp.execute(this);
	            if (convertToDex)
	            	buildFinalPackage = true;
                // then we check if we need to package the .class into classes.dex
                if (convertToDex) {
                	//buildOpQueue.push(new DexOp(taskFactory, helper));
    	            buildOpQueue.push(new DesugarOp());
    	            buildOpQueue.push(new D8Op());
                }
                if (buildFinalPackage) {
    	            File outputDirectory = androidOutputFolder.getLocation().toFile();
    	            buildOpQueue.push(new PackageApplicationOp(outputDirectory, monitor));
                }
            }
        	int count = buildOpQueue.size();
       		taskFactory.start();
        	while (count > 0) {
        		BuildOp<PostCompilerContext> buildOp = buildOpQueue.removeLast();
        		if (!buildOp.execute(this))
	                break;
	        	// Place buildOp back on queue for commit
	        	buildOpQueue.push(buildOp);
	        	--count;
      	    }
        	if (count > 0) {
        		// TODO - fix
        		System.err.println("Post-compile failed");
        		return allRefProjects;
        	}
        	while (!buildOpQueue.isEmpty()) {
        		BuildOp<PostCompilerContext> buildOp = null;
        		try {
        			buildOp = buildOpQueue.removeLast();
        			buildOp.commit(this);
					System.out.println(buildOp.getDescription() + " completed");
				} catch (IOException | SecurityException e) {
					// Report error but continue for best effort
					handleException(e, buildOp.getDescription() + " commit failed due to file system error");
				}
        	}
        	if (buildOpQueue.isEmpty()) {
                // Refresh the bin folder content with no recursion.
                androidOutputFolder.refreshLocal(IResource.DEPTH_ONE, monitor);
        	}
        } catch (AbortBuildException e) {
            return allRefProjects;
        } catch (InterruptedException e) {
        	Thread.interrupted();
            return allRefProjects;
        } catch (Exception exception) {
            // try to catch other exception to actually display an error. This will be useful
            // if we get an NPE or something so that we can at least notify the user that something
            // went wrong.

            // first check if this is a CoreException we threw to cancel the build.
            if (exception instanceof CoreException) {
                if (((CoreException)exception).getStatus().getSeverity() == IStatus.CANCEL) {
                    // Project is already marked with an error. Nothing to do
                    return allRefProjects;
                }
            }

            String msg = exception.getMessage();
            if (msg == null) {
                msg = exception.getClass().getCanonicalName();
            }

            msg = String.format("Unknown error: %1$s", msg);
            AndmoreAndroidPlugin.logAndPrintError(exception, project.getName(), msg);
            markProject(AndmoreAndroidConstants.MARKER_PACKAGING, msg, IMarker.SEVERITY_ERROR);
        } finally {
        	taskFactory.stop();
        }

        // Benchmarking end
        if (BuildHelper.BENCHMARK_FLAG) {
            String msg = "BENCHMARK ADT: Ending PostCompilation. \n BENCHMARK ADT: Time Elapsed: " + //$NON-NLS-1$
                         ((System.nanoTime() - startBuildTime)/Math.pow(10, 6)) + "ms";              //$NON-NLS-1$
            AndmoreAndroidPlugin.printBuildToConsole(BuildVerbosity.ALWAYS, project, msg);
            // End Overall Timer
            msg = "BENCHMARK ADT: Done with everything! \n BENCHMARK ADT: Time Elapsed: " +          //$NON-NLS-1$
                  (System.nanoTime() - BuildHelper.sStartOverallTime)/Math.pow(10, 6) + "ms";        //$NON-NLS-1$
            AndmoreAndroidPlugin.printBuildToConsole(BuildVerbosity.ALWAYS, project, msg);
        }

        return allRefProjects;
    }

    @Override
    protected void startupOnInitialize() {
        super.startupOnInitialize();

        // load the build status. We pass true as the default value to
        // force a recompile in case the property was not found
        convertToDex = loadProjectBooleanProperty(PROPERTY_CONVERT_TO_DEX, true);
        buildFinalPackage = loadProjectBooleanProperty(PROPERTY_BUILD_APK, true);
    }

    @Override
    protected void abortOnBadSetup(
            @NonNull IJavaProject javaProject,
            @Nullable ProjectState projectState) throws AbortBuildException, CoreException {
        super.abortOnBadSetup(javaProject, projectState);

        IProject iProject = getProject();

        // do a (hopefully quick) search for Precompiler type markers. Those are always only
        // errors.
        stopOnMarker(iProject, AndmoreAndroidConstants.MARKER_AAPT_COMPILE, IResource.DEPTH_INFINITE,
                false /*checkSeverity*/);
        stopOnMarker(iProject, AndmoreAndroidConstants.MARKER_AIDL, IResource.DEPTH_INFINITE,
                false /*checkSeverity*/);
        stopOnMarker(iProject, AndmoreAndroidConstants.MARKER_RENDERSCRIPT, IResource.DEPTH_INFINITE,
                false /*checkSeverity*/);
        stopOnMarker(iProject, AndmoreAndroidConstants.MARKER_ANDROID, IResource.DEPTH_ZERO,
                false /*checkSeverity*/);

        // do a search for JDT markers. Those can be errors or warnings
        stopOnMarker(iProject, IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER,
                IResource.DEPTH_INFINITE, true /*checkSeverity*/);
        stopOnMarker(iProject, IJavaModelMarker.BUILDPATH_PROBLEM_MARKER,
                IResource.DEPTH_INFINITE, true /*checkSeverity*/);
    }

	@Override
	public Collection<TransformInput> getPipelineInput() {
		return pipelineList;
	}

	@Override
	public void setPipelineInput(Collection<TransformInput> transformInput) {
		pipelineList.clear();
		pipelineList.addAll(transformInput);
	}

	@Override
	public void clearPipeline() {
		pipelineList.clear();
	}

}
