/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.eclipse.andworx.build.task;

import static com.android.SdkConstants.FD_RES;
import static com.android.SdkConstants.FN_RESOURCE_TEXT;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.eclipse.andworx.aapt.Aapt2Executor;
import org.eclipse.andworx.api.attributes.AndroidArtifacts;
import org.eclipse.andworx.build.BuildElement;
import org.eclipse.andworx.build.OutputType;
import org.eclipse.andworx.context.VariantContext;
import org.eclipse.andworx.core.AndworxVariantConfiguration;
import org.eclipse.andworx.exception.AndworxException;
import org.eclipse.andworx.helper.BuildElementFactory;
import org.eclipse.andworx.helper.BuildHelper;
import org.eclipse.andworx.log.SdkLogger;
import org.eclipse.andworx.options.StringOption;
import org.eclipse.andworx.task.StandardBuildTask;
import org.eclipse.andworx.task.TaskFactory;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.FilterData;
import com.android.build.OutputFile;
import com.android.builder.core.VariantType;
import com.android.builder.internal.aapt.AaptException;
import com.android.builder.internal.aapt.AaptOptions;
import com.android.builder.internal.aapt.AaptPackageConfig;
import com.android.builder.sdk.TargetInfo;
import com.android.ide.common.build.ApkData;
import com.android.ide.common.build.ApkInfo;
import com.android.ide.common.symbols.RGeneration;
import com.android.ide.common.symbols.SymbolIo;
import com.android.ide.common.symbols.SymbolTable;
import com.android.ide.common.symbols.SymbolUtils;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.SettableFuture;

/**
 * Task to link application resources using AAPT2 in non-namespaced mode
 */
public class NonNamespacedLinkResourcesTask extends StandardBuildTask {

	public static final String TASK_NAME = "bind resources";
	private static ILogger logger = SdkLogger.getLogger(NonNamespacedLinkResourcesTask.class.getName());

	private final VariantContext variantScope;
	private final BuildHelper buildHelper;
	private final BuildElementFactory buildElementFactory;
	private final Aapt2Executor aapt;
	private File manifestFileDirectory;
	private File srcOut;
	private File resourceOutputApk;
	private File symbolOutputDir;
    private File inputResourcesDir;
    private File proguardOutputFile;
    private File mainDexListProguardOutputFile;
    private File symbolsWithPackageNameOutputFile;
	private String packageForR;
    private VariantType variantType;
    private OutputType taskInputType;
	private TargetInfo targetInfo;
	private Set<File> dependencies;
	private Collection<File> manifestFiles;
	private Collection<File> sharedLibraryDependencies;
    private String buildTargetDensity;
    private boolean pseudoLocalesEnabled;
    private boolean isLibrary;
    private boolean generateLegacyMultidexMainDexProguardRules;

	public NonNamespacedLinkResourcesTask(VariantContext variantScope, Aapt2Executor aapt, BuildHelper buildHelper, BuildElementFactory buildElementFactory, TaskFactory taskFactory) {
		super(taskFactory);
		this.variantScope = variantScope;
		this.aapt = aapt;
		this.buildHelper = buildHelper;
		this.buildElementFactory = buildElementFactory;
	}
	
	public void setVariantType(VariantType variantType) {
		this.variantType = variantType;
	}

	public void setTaskInputType(OutputType taskInputType) {
		this.taskInputType = taskInputType;
	}

	public void setPackageForR(String packageForR) {
		this.packageForR = packageForR;
	}

	public void setSymbolOutputDir(File symbolOutputDir) {
		this.symbolOutputDir = symbolOutputDir;
	}

	public void setInputResourcesDir(File inputResourcesDir) {
		this.inputResourcesDir = inputResourcesDir;
	}

	public void setResourceOutputApk(File resourceOutputApk) {
		this.resourceOutputApk = resourceOutputApk;
	}
	
	public void setDependencies(Set<File> dependencies) {
		this.dependencies = dependencies;
	}

	public void setSharedLibraryDependencies(Collection<File> sharedLibraryDependencies) {
		this.sharedLibraryDependencies = sharedLibraryDependencies;
	}

	public void setManifestFileDirectory(File manifestFileDirectory) {
		this.manifestFileDirectory = manifestFileDirectory;
	}

	public void setTargetInfo(TargetInfo targetInfo) {
		this.targetInfo = targetInfo;
	}

    public void setAaptMainDexListProguardOutputFile(File mainDexListProguardOutputFile) {
        this.mainDexListProguardOutputFile = mainDexListProguardOutputFile;
    }

    public void setProguardOutputFile(File proguardOutputFile) {
        this.proguardOutputFile = proguardOutputFile;
    }

    public void setPseudoLocalesEnabled(boolean pseudoLocalesEnabled) {
        this.pseudoLocalesEnabled = pseudoLocalesEnabled;
    }

	public void setBuildTargetDensity(String buildTargetDensity) {
		this.buildTargetDensity = buildTargetDensity;
	}

    protected void setManifestFiles(Collection<File> manifestFiles) {
        this.manifestFiles = manifestFiles;
    }
    
	@Override
	public String getTaskName() {
		return TASK_NAME;
	}

	@Override
	public Future<Void> doFullTaskAction() {
        final SettableFuture<Void> actualResult = SettableFuture.create();
        try {
	        AndworxVariantConfiguration variantConfig = variantScope.getVariantConfiguration();
	        // Prepare generated R root location in case package has changed
	        buildHelper.prepareDir(srcOut);
	        // Prepare generated R location
	        File rPath = new File(srcOut, packageForR.replaceAll("\\.", "/"));
	        buildHelper.prepareDir(rPath);
	        File resPackageOutputDir = variantScope.getProcessResourcePackageOutputDirectory();
			buildHelper.prepareDir(resPackageOutputDir);
	        File symbolFile = new File(variantScope.getSymbolsOutputDir(), FN_RESOURCE_TEXT);
			setSymbolOutputDir(symbolFile.getParentFile());
	        variantScope.addOutput(OutputType.SYMBOL_LIST, Collections.singletonList(symbolFile), TASK_NAME);
	        buildHelper.prepareDir(symbolFile.getParentFile());
	        symbolsWithPackageNameOutputFile =
	                FileUtils.join(
	                		variantScope.getIntermediatesDir(),
	                        FD_RES,
	                        "symbol-table-with-package",
	                        variantConfig.getDirName(),
	                        "package-aware-r.txt");
			buildHelper.prepareDir(symbolsWithPackageNameOutputFile.getParentFile());
	        resourceOutputApk = variantScope.getProcessResourcePackageOutputFile();
	        variantScope.addOutput(
	                OutputType.NOT_NAMESPACED_R_CLASS_SOURCES,
	                Collections.singletonList(resPackageOutputDir),
	                TASK_NAME);
	        variantScope.addOutput(
	                OutputType.PROCESSED_RES,
	                Collections.singletonList(resPackageOutputDir),
	                TASK_NAME);
	    	// Write output metadata file and register in variant context
			ApkData apkData = variantScope.getMainSplit();
			buildHelper.writeOutput(OutputType.PROCESSED_RES, resourceOutputApk, apkData);
	        boolean shrinkCode = variantScope.getCodeShrinker() != null;
	        if (shrinkCode || generateLegacyMultidexMainDexProguardRules) {
			    if (shrinkCode) {
			    	File proguradOutput = variantScope.getProcessAndroidResourcesProguardOutputFile();
			    	buildHelper.prepareDir(proguradOutput.getParentFile());
			        setProguardOutputFile(proguradOutput);
			    }
			
			    if (generateLegacyMultidexMainDexProguardRules) {
			    	File keepList = variantScope.getManifestKeepListProguardFile();
			    	buildHelper.prepareDir(keepList.getParentFile());
			        setAaptMainDexListProguardOutputFile(keepList);
			    }
	        }
	        setDependencies(variantScope.getDependencies()
	        		.getTansformCollection(
	        				AndroidArtifacts.ArtifactType.SYMBOL_LIST_WITH_PACKAGE_NAME));
	        setSharedLibraryDependencies(
	        		variantScope.getArtifactFileCollection(AndroidArtifacts.ArtifactType.RES_SHARED_STATIC_LIBRARY));
	        Collection<BuildElement> manifestBuildElements = 
	        	buildElementFactory.from(taskInputType, manifestFiles);
	        if (manifestBuildElements.size() != 1)
	        	throw new AndworxException("Incorrect manifest count of " + manifestBuildElements.size());
	        ApkInfo apkInfo = manifestBuildElements.iterator().next().getApkInfo();
	        FilterData densityFilterData = apkInfo.getFilter(OutputFile.FilterType.DENSITY);
	        String preferredDensity =
	                densityFilterData != null ?
	                   densityFilterData.getIdentifier() :
	                   buildTargetDensity;
	 	    AaptPackageConfig.Builder builder =  new AaptPackageConfig.Builder();
		    builder.setAndroidTarget(targetInfo.getTarget());
		    builder.setManifestFile(new File(manifestFileDirectory,  SdkConstants.ANDROID_MANIFEST_XML));
		    builder.setOptions(new AaptOptions(null, false, null));
		    builder.setLibrarySymbolTableFiles(dependencies);
		    builder.setResourceDir(inputResourcesDir);
		    builder.setImports(ImmutableList.copyOf(sharedLibraryDependencies));
		    builder.setSourceOutputDir(srcOut);
	        builder.setCustomPackageForR(packageForR);
	        builder.setSymbolOutputDir(symbolOutputDir);
		    // Not static library is default 
		    //builder.setStaticLibrary(false);
	        builder.setProguardOutputFile(proguardOutputFile);
	        builder.setMainDexListProguardOutputFile(mainDexListProguardOutputFile);
	        builder.setResourceOutputApk(resourceOutputApk);
	        builder.setVariantType(variantType);
	        builder.setPseudoLocalize(pseudoLocalesEnabled);
	        if (densityFilterData != null)
	        	builder.setPreferredDensity(preferredDensity);
	
	    	AaptPackageConfig aaptConfig = builder.build();
	        try {
	        	Future<Void> futureResult = aapt.makeValidatedPackage(aaptConfig);
	        	futureResult.get();
	        	generateSource(aaptConfig);
	            if ((isLibrary || !dependencies.isEmpty()) &&
	                (symbolsWithPackageNameOutputFile != null)) {
	                File textSymbolOutputFile = new File(symbolOutputDir, SdkConstants.R_CLASS + SdkConstants.DOT_TXT);
					SymbolIo.writeSymbolTableWithPackage(
	                        Preconditions.checkNotNull(textSymbolOutputFile ).toPath(),
	                        aaptConfig.getManifestFile().toPath(),
	                        symbolsWithPackageNameOutputFile.toPath());
	            }
	        	actualResult.set(null);
			} catch (AaptException | IOException e) {
				actualResult.setException(e);
	        } catch (InterruptedException e) {
	            Thread.interrupted();
	            actualResult.setException(e);
	        } catch (ExecutionException e) {
	            actualResult.setException(e.getCause());
	        }
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

    public NonNamespacedLinkResourcesTask configure(
	        boolean generateLegacyMultidexMainDexProguardRules,
	        @NonNull OutputType sourceTaskOutputType,
	        @Nullable String baseName,
	        boolean isLibrary) {
    	this.generateLegacyMultidexMainDexProguardRules = 
    			generateLegacyMultidexMainDexProguardRules;
        this.srcOut = variantScope.getRClassSourceOutputDir();
		this.isLibrary = isLibrary;
        AndworxVariantConfiguration variantConfig = variantScope.getVariantConfiguration();
        setVariantType(variantConfig.getType());
        if (!variantScope.hasOutput(sourceTaskOutputType))
        	throw new AndworxException("Missing output type " + sourceTaskOutputType);
        setInputResourcesDir(variantScope.getOutput(sourceTaskOutputType).iterator().next());

	    boolean aaptFriendlyManifestsFilePresent =
	    		variantScope.hasOutput(OutputType.AAPT_FRIENDLY_MERGED_MANIFESTS);
	    OutputType taskInputType =
	            aaptFriendlyManifestsFilePresent
	                    ? OutputType.AAPT_FRIENDLY_MERGED_MANIFESTS
	                    : OutputType.MERGED_MANIFESTS;
	    setTaskInputType(taskInputType);
	    setManifestFiles(variantScope.getOutput(taskInputType));
	    setTargetInfo(variantScope.getTargetInfo());
       	File manifestFileDir = null;
        if (variantScope.hasOutput(OutputType.AAPT_FRIENDLY_MERGED_MANIFESTS)) {
        	manifestFileDir = variantScope.getOutput(OutputType.AAPT_FRIENDLY_MERGED_MANIFESTS).iterator().next();
        } else {
        	manifestFileDir = variantScope.getOutput(OutputType.MERGED_MANIFESTS).iterator().next();
        }
        setManifestFileDirectory(manifestFileDir);
        setPackageForR(variantConfig.getOriginalApplicationId());
	    setPseudoLocalesEnabled(variantConfig.getBuildType().isPseudoLocalesEnabled());
		setBuildTargetDensity(variantScope.getAndworxProject().getProjectOptions().get(StringOption.IDE_BUILD_TARGET_DENSITY));
    	return this;
    }

    // From com.android.builder.core.AndroidBuilder
    private void generateSource(AaptPackageConfig aaptConfig) throws IOException {
        // Figure out what the main symbol file's package is.
        String mainPackageName = aaptConfig.getCustomPackageForR();
        if (mainPackageName == null) {
            mainPackageName =
                    SymbolUtils.getPackageNameFromManifest(aaptConfig.getManifestFile());
        }

        // Load the main symbol file.
        File mainRTxt = new File(aaptConfig.getSymbolOutputDir(), "R.txt");
        SymbolTable mainSymbols =
                mainRTxt.isFile()
                        ? SymbolIo.readFromAapt(mainRTxt, mainPackageName)
                        : SymbolTable.builder().tablePackage(mainPackageName).build();

        // For each dependency, load its symbol file.
        Set<SymbolTable> depSymbolTables =
                SymbolUtils.loadDependenciesSymbolTables(
                        aaptConfig.getLibrarySymbolTableFiles());

        boolean finalIds = true;
        if (aaptConfig.getVariantType() == VariantType.LIBRARY) {
            finalIds = false;
        }

        RGeneration.generateRForLibraries(mainSymbols, depSymbolTables, aaptConfig.getSourceOutputDir(), finalIds);
    }

}
