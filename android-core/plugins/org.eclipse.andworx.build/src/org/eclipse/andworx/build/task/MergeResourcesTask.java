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
package org.eclipse.andworx.build.task;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.eclipse.andworx.aapt.Aapt2Executor;
import org.eclipse.andworx.aapt.MergeResourcesVectorDrawableRenderer;
import org.eclipse.andworx.aapt.MergedResourceProcessor;
import org.eclipse.andworx.api.attributes.ArtifactCollection;
import org.eclipse.andworx.context.VariantContext;
import org.eclipse.andworx.core.AndworxVariantConfiguration;
import org.eclipse.andworx.helper.BuildHelper;
import org.eclipse.andworx.log.SdkLogger;
import org.eclipse.andworx.options.BooleanOption;
import org.eclipse.andworx.project.AndworxProject;
import org.eclipse.andworx.task.StandardBuildTask;
import org.eclipse.andworx.task.TaskFactory;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.core.BuilderConstants;
import com.android.builder.model.VectorDrawablesOptions;
import com.android.ide.common.blame.MergingLog;
import com.android.ide.common.res2.GeneratedResourceSet;
import com.android.ide.common.res2.MergedResourceWriter;
import com.android.ide.common.res2.NoOpResourcePreprocessor;
import com.android.ide.common.res2.ResourceMerger;
import com.android.ide.common.res2.ResourcePreprocessor;
import com.android.ide.common.res2.ResourceSet;
import com.android.ide.common.res2.SingleFileProcessor;
import com.android.resources.Density;
import com.android.utils.ILogger;
import com.google.common.util.concurrent.SettableFuture;

public class MergeResourcesTask extends StandardBuildTask {
	
	public static final String TASK_NAME = "merge resources";
	
	private static ILogger logger = SdkLogger.getLogger(MergeResourcesTask.class.getName());

	private final VariantContext variantScope;
    private final MergedResourceProcessor mergedResourceProcessor;
	private final Aapt2Executor aapt;
    /** Stores where file and text fragments within files came from */
	private final MergingLog mergingLog;
	private final BuildHelper buildHelper;
    private int minSdk;
    private boolean pseudoLocalesEnabled;
    private boolean crunchPng;
    private boolean disableVectorDrawables;
    private boolean vectorSupportLibraryIsUsed;
	/** Merged resources directory to write to (e.g. {@code intermediates/res/merged/debug}) */
	private @NonNull File rootFolder;
    /** Incremental build support */
    private File incrementalFolder;
    /** Where data binding exports its outputs after parsing layout files. */
    private File generatedPngsOutputDir;
    private File renderscriptResOutputDir;
    private File generatedResOutputDir;
    private File microApkResDirectory;
    /** Optional file to write out any publicly imported resource types and names */
    private File publicFile;
    private List<ResourceSet> resourceSets;
    private ArtifactCollection libraries;
    private Collection<File> extraGeneratedResFolders;
    private Collection<String> generatedDensities;
    private @Nullable File dataBindingLayoutInfoOutFolder;
    private @Nullable File mergedNotCompiledResourcesOutputDirectory;
    private @Nullable SingleFileProcessor dataBindingLayoutProcessor;

    /**
     * 
     * @param taskName
     * @param aapt
     * @param mergedResourceProcessor
     * @param mergingLog Object which stores where file and text fragments within files came from
     */
	public MergeResourcesTask(
			VariantContext variantScope,
			Aapt2Executor aapt, 
			MergedResourceProcessor mergedResourceProcessor,
			MergingLog mergingLog,
			BuildHelper buildHelper,
			TaskFactory taskFactory) {
		super(taskFactory);
		this.variantScope = variantScope;
		this.aapt = aapt;
    	this.mergedResourceProcessor = mergedResourceProcessor;
    	this.mergingLog = mergingLog;
		this.buildHelper = buildHelper;
		generatedDensities = Collections.emptyList();
		extraGeneratedResFolders = Collections.emptyList();
	}

	@Override
	public String getTaskName() {
		return TASK_NAME;
	}

	@Override
	public Future<Void> doFullTaskAction() {
        final SettableFuture<Void> actualResult = SettableFuture.create();
        try {
        	// This is full run, clean the previous outputs
        	buildHelper.prepareDir(rootFolder);
        	buildHelper.prepareDir(dataBindingLayoutInfoOutFolder);
        	// Get resources preprocessor instance to allocate to resource sets and merge writer
            ResourcePreprocessor preprocessor = getPreprocessor();
            // Collect resource folders as configured for the project
            List<ResourceSet> resourceSets = getConfiguredResourceSets(preprocessor);
            // create a new merger and populate it with the sets.
            ResourceMerger merger = new ResourceMerger(minSdk);
            for (ResourceSet resourceSet : resourceSets) {
            	if (resourceSet.isEmpty())
            		resourceSet.loadFromFiles(logger);
                merger.addDataSet(resourceSet);
            }
            MergedResourceWriter writer =
                    new MergedResourceWriter(
                    		mergedResourceProcessor,
                    		rootFolder,
                            publicFile,
                            mergingLog,
                            preprocessor,
                            aapt,
                            incrementalFolder,
                            dataBindingLayoutProcessor,
                            mergedNotCompiledResourcesOutputDirectory,
                            pseudoLocalesEnabled,
                            crunchPng);
            
            merger.mergeData(writer, false); // Clean up set false to improve performance

            if (dataBindingLayoutProcessor != null) {
                dataBindingLayoutProcessor.end();
            }

            // No exception? Write the known state.
            //merger.writeBlobTo(getIncrementalFolder(), writer, false);
        	actualResult.set(null);
        } catch (Exception e) {
        	// TODO - Log MergingException text information
        	actualResult.setException(e);
        } finally {
        	try {
				aapt.close();
			} catch (IOException e) {
				logger.error(e, "Error closing AAP2 executor");
			}
        }
        
		return actualResult;
	}

	public MergeResourcesTask configure(
        @Nullable File outputLocation,
        @Nullable File mergedNotCompiledOutputDirectory,
        boolean includeDependencies,
        boolean processVectorDrawables) {
        setRootFolder(outputLocation);
        AndworxProject project = variantScope.getAndworxProject();
        AndworxVariantConfiguration variantConfig = variantScope.getVariantConfiguration();
        setMinSdk(
        	variantConfig.getMinSdkVersion().getApiLevel());
        setIncrementalFolder(variantScope.getIncrementalDir(variantScope.getTaskName("merge", "Resources")));
        setCrunchPng(variantScope.isCrunchPngs());

        VectorDrawablesOptions vectorDrawablesOptions = variantConfig.getDefaultConfig().getVectorDrawables();
        setGeneratedDensities(vectorDrawablesOptions.getGeneratedDensities());
        if (generatedDensities == null) {
            setGeneratedDensities(Collections.emptySet());
        }

        disableVectorDrawables =
                !processVectorDrawables || generatedDensities.isEmpty();

        // TODO: When support library starts supporting gradients (http://b/62421666), remove
        // the vectorSupportLibraryIsUsed field and set disableVectorDrawables when
        // the getUseSupportLibrary method returns TRUE.
        setVectorSupportLibraryIsUsed(
                Boolean.TRUE.equals(vectorDrawablesOptions.getUseSupportLibrary()));

        boolean validateEnabled =
                !project.getProjectOptions().get(BooleanOption.DISABLE_RESOURCE_VALIDATION);
        if (includeDependencies) {
            setLibraries(variantScope.getDependencies());
        }

        setResourceSets(variantConfig.getResourceSets(validateEnabled));
        setExtraGeneratedResFolders(variantScope.getExtraGeneratedResFolders());
        setRenderscriptResOutputDir(variantScope.getRenderscriptResOutputDir());

        setGeneratedResOutputDir(variantScope.getGeneratedResOutputDir());
        if (variantScope.getMicroApkTask() != null &&
                variantConfig.getBuildType().isEmbedMicroApp()) {
            setMicroApkResDirectory(variantScope.getMicroApkResDirectory());
        }
        setGeneratedPngsOutputDir(variantScope.getGeneratedPngsOutputDir());

/* TODO - Implement Data Binding (significant undertaking)
        if (project.getProjectOptions().getDataBinding().isEnabled()) {
            // Keep as an output.
            setDataBindingLayoutInfoOutFolder(
                    scope.getLayoutInfoOutputForDataBinding());

            dataBindingLayoutProcessor =
                    new SingleFileProcessor() {
                        final LayoutXmlProcessor processor =
                                variantData.getLayoutXmlProcessor();

                        @Override
                        public boolean processSingleFile(File file, File out) throws Exception {
                            return processor.processSingleFile(file, out);
                        }

                        @Override
                        public void processRemovedFile(File file) {
                            processor.processRemovedFile(file);
                        }

                        @Override
                        public void end() throws JAXBException {
                            processor.writeLayoutInfoFiles(
                                    dataBindingLayoutInfoOutFolder);
                        }
                    };
        }
*/
        setMergedNotCompiledResourcesOutputDirectory(mergedNotCompiledOutputDirectory);

        setPseudoLocalesEnabled(
                variantConfig.getBuildType().isPseudoLocalesEnabled());
		return this;
	}
	
	public void setMinSdk(int minSdk) {
		this.minSdk = minSdk;
	}

	public void setPseudoLocalesEnabled(boolean pseudoLocalesEnabled) {
		this.pseudoLocalesEnabled = pseudoLocalesEnabled;
	}

	public void setCrunchPng(boolean crunchPng) {
		this.crunchPng = crunchPng;
	}

	public void setDisableVectorDrawables(boolean disableVectorDrawables) {
		this.disableVectorDrawables = disableVectorDrawables;
	}

	public void setVectorSupportLibraryIsUsed(boolean vectorSupportLibraryIsUsed) {
		this.vectorSupportLibraryIsUsed = vectorSupportLibraryIsUsed;
	}

	public void setRootFolder(File rootFolder) {
		this.rootFolder = rootFolder;
	}

	public void setIncrementalFolder(File incrementalFolder) {
		this.incrementalFolder = incrementalFolder;
	}

	public void setGeneratedPngsOutputDir(File generatedPngsOutputDir) {
		this.generatedPngsOutputDir = generatedPngsOutputDir;
	}

	public void setRenderscriptResOutputDir(File renderscriptResOutputDir) {
		this.renderscriptResOutputDir = renderscriptResOutputDir;
	}

	public void setGeneratedResOutputDir(File generatedResOutputDir) {
		this.generatedResOutputDir = generatedResOutputDir;
	}

	public void setMicroApkResDirectory(File microApkResDirectory) {
		this.microApkResDirectory = microApkResDirectory;
	}

	public void setPublicFile(File publicFile) {
		this.publicFile = publicFile;
	}

	public void setResourceSets(List<ResourceSet> resourceSets) {
		this.resourceSets = resourceSets;
	}

	public void setLibraries(ArtifactCollection libraries) {
		this.libraries = libraries;
	}

	public void setExtraGeneratedResFolders(Collection<File> extraGeneratedResFolders) {
		this.extraGeneratedResFolders = extraGeneratedResFolders;
	}

	public void setGeneratedDensities(Collection<String> generatedDensities) {
		this.generatedDensities = generatedDensities;
	}

	public void setDataBindingLayoutInfoOutFolder(File dataBindingLayoutInfoOutFolder) {
		this.dataBindingLayoutInfoOutFolder = dataBindingLayoutInfoOutFolder;
	}

	public void setMergedNotCompiledResourcesOutputDirectory(File mergedNotCompiledResourcesOutputDirectory) {
		this.mergedNotCompiledResourcesOutputDirectory = mergedNotCompiledResourcesOutputDirectory;
	}

	public void setDataBindingLayoutProcessor(SingleFileProcessor dataBindingLayoutProcessor) {
		this.dataBindingLayoutProcessor = dataBindingLayoutProcessor;
	}

	/**
     * Only one pre-processor for now. The code will need slight changes when we add more.
     */
    @NonNull
    private ResourcePreprocessor getPreprocessor() {
        if (disableVectorDrawables) {
            // If the user doesn't want any PNGs, leave the XML file alone as well.
            return NoOpResourcePreprocessor.INSTANCE;
        }

        Collection<Density> densities =
                generatedDensities.stream().map(Density::getEnum).collect(Collectors.toList());
        return new MergeResourcesVectorDrawableRenderer(
                minSdk,
                vectorSupportLibraryIsUsed,
                generatedPngsOutputDir,
                densities,
                logger);
    }

    @NonNull
    private List<ResourceSet> getConfiguredResourceSets(ResourcePreprocessor preprocessor) {
    	List<ResourceSet> processedInputs = computeResourceSetList();
        List<ResourceSet> generatedSets = new ArrayList<>(processedInputs.size());

        for (ResourceSet resourceSet : processedInputs) {
            resourceSet.setPreprocessor(preprocessor);
            ResourceSet generatedSet = new GeneratedResourceSet(resourceSet);
            resourceSet.setGeneratedSet(generatedSet);
            generatedSets.add(generatedSet);
        }

        // We want to keep the order of the inputs. Given inputs:
        // (A, B, C, D)
        // We want to get:
        // (A-generated, A, B-generated, B, C-generated, C, D-generated, D).
        // Therefore, when later in {@link DataMerger} we look for sources going through the
        // list backwards, B-generated will take priority over A (but not B).
        // A real life use-case would be if an app module generated resource overrode a library
        // module generated resource (existing not in generated but bundled dir at this stage):
        // (lib, app debug, app main)
        // We will get:
        // (lib generated, lib, app debug generated, app debug, app main generated, app main)
        for (int i = 0; i < generatedSets.size(); ++i) {
            processedInputs.add(2 * i, generatedSets.get(i));
        }
        return processedInputs;
    }

    @NonNull
    private List<ResourceSet> computeResourceSetList() {
        int size = resourceSets.size() + 4;
        if (libraries != null) {
            size += libraries.getArtifactFiles().size();
        }

        List<ResourceSet> resourceSetList = new ArrayList<>(size);

        // add at the beginning since the libraries are less important than the folder based
        // resource sets.
        // get the dependencies first
        if (libraries != null) {
            List<ResourceSet> libArtifacts = libraries.getResourceSetList();
            // the order of the artifact is descending order, so we need to reverse it.
            for (ResourceSet resourceSet : libArtifacts) {
                // add to 0 always, since we need to reverse the order.
                resourceSetList.add(0,resourceSet);
            }
        }

        // Add the folder based next
        resourceSetList.addAll(resourceSets);

        // We add the generated folders to the main set
        List<File> generatedResFolders = new ArrayList<>();

        if (renderscriptResOutputDir != null)
        	generatedResFolders.add(renderscriptResOutputDir);
        if (generatedResOutputDir != null)
        	generatedResFolders.add(generatedResOutputDir);

        Collection<File> extraFolders = extraGeneratedResFolders;
        if (extraFolders != null) {
            generatedResFolders.addAll(extraFolders);
        }
        if (microApkResDirectory != null) {
            generatedResFolders.add(microApkResDirectory);
        }

        // Add the generated files to the main set.
        final ResourceSet mainResourceSet = resourceSets.get(0);
        assert mainResourceSet.getConfigName().equals(BuilderConstants.MAIN);
        mainResourceSet.addSources(generatedResFolders);

        return resourceSetList;
    }

}
