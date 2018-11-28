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
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.andmore.AndmoreAndroidConstants;
import org.eclipse.andmore.AndmoreAndroidPlugin;
import org.eclipse.andmore.internal.project.BaseProjectHelper;
import org.eclipse.andworx.api.attributes.ArtifactCollection;
import org.eclipse.andworx.build.AndworxContext;
import org.eclipse.andworx.build.AndworxFactory;
import org.eclipse.andworx.build.task.ManifestMergerTask;
import org.eclipse.andworx.context.VariantContext;
import org.eclipse.andworx.core.AndworxVariantConfiguration;
import org.eclipse.andworx.options.ProjectOptions;
import org.eclipse.andworx.options.StringOption;
import org.eclipse.andworx.registry.ProjectState;
import org.eclipse.andworx.task.ManifestMergeHandler;
import org.eclipse.andworx.task.TaskFactory;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.builder.dexing.DexingType;
import com.android.builder.model.ApiVersion;
import com.android.manifmerger.ManifestMerger2;
import com.android.manifmerger.ManifestMerger2.Invoker.Feature;
import com.android.manifmerger.MergingReport;
import com.android.manifmerger.MergingReport.Record;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;

public class MergeManifestOp implements BuildOp<PreCompilerContext> {

    private final AndworxContext objectFactory;
	
	public MergeManifestOp() {
		objectFactory = AndworxFactory.instance();
	}
	
	@Override
	public boolean execute(PreCompilerContext context) throws CoreException, InterruptedException {
        IProject project = context.getProject();
        if (context.isDebugLog()) {
            AndmoreAndroidPlugin.log(IStatus.INFO, "%s merging manifests!", project.getName());
        }
        File manifestOutputDir = outputSourceLocation(context);
        File outFile = new File(manifestOutputDir, SdkConstants.FN_ANDROID_MANIFEST_XML);
        if (!context.isMustMergeManifest() && outFile.exists())
        	// Nothing to do
        	return true;
        // remove existing markers from the manifest.
        // FIXME: only remove from manifest once the markers are put there.
        context.removeMarkersFromResource(project, AndmoreAndroidConstants.MARKER_MANIFMERGER);
        objectFactory.getPreManifestMergeTask(context.getVariantContext(), manifestOutputDir).schedule();
        ProjectState projectState = objectFactory.getProjectState(context.getProject());
    	File manifestPath = projectState.getAndworxProject().getDefaultConfig().getSourceProvider().getManifestFile();
		VariantContext variantScope = context.getVariantContext();
		AndworxVariantConfiguration variantConfig = variantScope.getVariantConfiguration();
        ImmutableList.Builder<ManifestMerger2.Invoker.Feature> optionalFeatures =
                ImmutableList.builder();
        //if (variantScope.isTestOnly()) {
        //    optionalFeatures.add(ManifestMerger2.Invoker.Feature.TEST_ONLY);
        //}

        if (variantScope.getVariantConfiguration().getDexingType() == DexingType.LEGACY_MULTIDEX) {
            optionalFeatures.add(
                    ManifestMerger2.Invoker.Feature.ADD_MULTIDEX_APPLICATION_IF_NO_NAME);
        }

        if (variantConfig.getBuildType().isDebuggable()) {
            optionalFeatures.add(ManifestMerger2.Invoker.Feature.DEBUGGABLE);
        }
        ProjectOptions projectOptions = variantScope.getAndworxProject().getProjectOptions();
        if (!getAdvancedProfilingTransforms(projectOptions).isEmpty()
                && variantConfig.getBuildType().isDebuggable()) {
            optionalFeatures.add(ManifestMerger2.Invoker.Feature.ADVANCED_PROFILING);
        }
        //if (variantConfig.isInstantRunBuild(globalScope)) {
        //    optionalFeatures.add(ManifestMerger2.Invoker.Feature.INSTANT_RUN_REPLACEMENT);
        //}
 		// Do manifest merge
    	final File reportFile = variantScope.getManifestReportFile();
        mergeManifestTask(context, manifestPath, optionalFeatures.build(), reportFile).schedule();
        TaskFactory taskFactory = context.getTaskFactory();
    	synchronized(taskFactory) {
    		taskFactory.wait();
    	}
        return true;
	}

	@Override
	public void commit(PreCompilerContext context) throws IOException {
        // Do not copy manifest file to project now, as it is subsequently deleted 
		// See MonitorResourcesOp
	}

	@Override
	public String getDescription() {
		return ManifestMergerTask.TASK_NAME;
	}
	
	private File outputSourceLocation(PreCompilerContext context) {
		return context.getVariantContext().getManifestOutputDirectory();
	}

    private ManifestMergerTask mergeManifestTask(PreCompilerContext context, File manifestPath,  List<Feature> optionalFeatures,
    File reportFile) throws CoreException {
		VariantContext variantScope = context.getVariantContext();
		AndworxVariantConfiguration variantConfig = variantScope.getVariantConfiguration();
		ManifestMergeHandler manifestMergeHandler = new ManifestMergeHandler() {

			@Override
			public void onManifestMerge(MergingReport mergeReport) {
                if (mergeReport.getResult().isError()) {
                    StringBuilder sb = new StringBuilder();
                    for (Record record : mergeReport.getLoggingRecords()) {
                        if (record.getSeverity() == MergingReport.Record.Severity.ERROR) {
                            sb.append(record.getMessage()).append('\n');
                        }
                    }
                    context.markProject(AndmoreAndroidConstants.MARKER_MANIFMERGER, sb.toString(), IMarker.SEVERITY_ERROR);
			    } else {
	                // Prepare for commit by deleting old file from project. Does not delete file on file system.
	            	IFolder projectFolder = BaseProjectHelper.getAndroidOutputFolder(context.getProject());
	                if (projectFolder.exists(new org.eclipse.core.runtime.Path(SdkConstants.FN_ANDROID_MANIFEST_XML))) {
	                	IFile toDelete = projectFolder.getFile(SdkConstants.FN_ANDROID_MANIFEST_XML);
	                	try {
							toDelete.delete(true, null);
						} catch (CoreException e) {
							// Do not report this unexpected error
						}
	                }
		            context.saveMustMergeManifest(false);
			    }
			}
                
			@Override
			public void onMergeFailed(Exception cause) {
            	context.handleException(cause, "Failed to write merge Manifest");
            	context.markProject(AndmoreAndroidConstants.MARKER_MANIFMERGER, "Unknown error merging manifest", IMarker.SEVERITY_ERROR);
			}
		};
    	ManifestMergerTask manifestMergerTask = objectFactory.getManifestMergerTask(manifestMergeHandler);
    	manifestMergerTask.setOptionalFeatures(optionalFeatures);
    	manifestMergerTask.setReportFile(reportFile);
        manifestMergerTask.setOutputScope(variantScope);
        manifestMergerTask.setVariantConfiguration(variantConfig);
        manifestMergerTask.setManifests(filterManifests(context.getVariantContext().getDependencies()));
        //AndworxProject andworxProject = variantScope.getAndworxProject();
        // optional manifest files too.
        if (variantScope.getMicroApkTask() != null &&
        		variantConfig.getBuildType().isEmbedMicroApp()) {
            manifestMergerTask.setMicroApkManifest(Collections.singletonList(variantScope.getMicroApkManifestFile()));
        }
        // manifestMergerTask.setCompatibleScreensManifest(
        //         variantScope.getOutput(OutputType.COMPATIBLE_SCREEN_MANIFEST));
        ApiVersion minSdk = variantConfig.getMergedFlavor().getMinSdkVersion();
        manifestMergerTask.setMinSdkVersion(minSdk == null ? null : minSdk.getApiString());
        ApiVersion targetSdk = variantConfig.getMergedFlavor().getTargetSdkVersion();
        manifestMergerTask.setTargetSdkVersion(targetSdk == null ? null : targetSdk.getApiString());
        Integer maxSdk = variantConfig.getMergedFlavor().getMaxSdkVersion();
        if (maxSdk != null)
        	manifestMergerTask.setMaxSdkVersion(maxSdk);
        manifestMergerTask.setManifestOutputDirectory(
                variantScope.getManifestOutputDirectory());
        manifestMergerTask.setInstantRunManifestOutputDirectory(
                variantScope.getInstantRunManifestOutputDirectory());
        return manifestMergerTask;
    }

    private ArtifactCollection filterManifests(ArtifactCollection dependencies) {
		Set<File> manifestFiles = new TreeSet<File>();
		for (File library: dependencies.getArtifactFiles()) {
			File manifest = new File(library, SdkConstants.FN_ANDROID_MANIFEST_XML);
			if (manifest.exists())
				manifestFiles.add(manifest);
		}
		return new ArtifactCollection(getDescription()) {
			@Override
		    public Set<File> getArtifactFiles() {
		    	return manifestFiles;
		    }
		};
	}

	@NonNull
    private static List<String> getAdvancedProfilingTransforms(@NonNull ProjectOptions options) {
        String string = options.get(StringOption.IDE_ANDROID_CUSTOM_CLASS_TRANSFORMS);
        if (string == null) {
            return ImmutableList.of();
        }
        return Splitter.on(',').splitToList(string);
    }

}
