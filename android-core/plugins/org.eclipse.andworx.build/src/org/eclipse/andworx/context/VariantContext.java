/*
 * Copyright (C) 2015 The Android Open Source Project
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
package org.eclipse.andworx.context;

import static com.android.SdkConstants.FD_COMPILED;
import static com.android.SdkConstants.FD_MERGED;
import static com.android.SdkConstants.FD_RES;
import static com.android.SdkConstants.FN_ANDROID_MANIFEST_XML;
import static com.android.SdkConstants.FN_RES_BASE;
import static com.android.SdkConstants.RES_QUALIFIER_SEP;
import static com.android.builder.model.AndroidProject.FD_GENERATED;
import static com.android.builder.model.AndroidProject.FD_INTERMEDIATES;
import static com.android.builder.model.AndroidProject.FD_OUTPUTS;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.andworx.api.attributes.AndroidArtifacts.ArtifactType;
import org.eclipse.andworx.api.attributes.ArtifactCollection;
import org.eclipse.andworx.build.ApkVariantOutput;
import org.eclipse.andworx.build.OutputType;
import org.eclipse.andworx.core.AndworxVariantConfiguration;
import org.eclipse.andworx.exception.AndworxException;
import org.eclipse.andworx.options.PostprocessingOptions;
import org.eclipse.andworx.options.StringOption;
import org.eclipse.andworx.project.AndworxProject;
import org.eclipse.andworx.sdk.SdkProfile;
import org.eclipse.andworx.task.StandardBuildTask;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.core.BuilderConstants;
import com.android.builder.core.VariantType;
import com.android.builder.dexing.DexingType;
import com.android.builder.sdk.TargetInfo;
import com.android.ide.common.build.ApkData;
import com.android.repository.Revision;
import com.android.repository.api.ProgressIndicator;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.targets.AndroidTargetManager;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.android.utils.StringHelper;
import com.google.common.collect.Maps;

import android.databinding.tool.LayoutXmlProcessor;

/**
 * Android variant context provides project resources and information. 
 * Included are model details for expected Outputs from a build.
 */
public class VariantContext implements OutputScope {
	// TODO - transfer to BooleanOptions
	private static final boolean CRUNCH_PNGS =  false;

	/** Android SDK logger */
	private final ILogger logger;
	/** Infomation on an Android SDK installation at a particular location and resources in the Android environment */
	private final SdkProfile sdkProfile;
	/** Project-specific Android configuration */
	private AndworxProject andworxProject;
	/** Project dependencies */
	private ArtifactCollection dependencies;
	/** Current build type - determines which VariantConfiguration is selected */
	private String buildType;
    /** Maps build type to variant configuration */
    private final Map<String, AndworxVariantConfiguration> variantConfigMap;
    /** Current variant configuration. Defaults to Debug */
	private AndworxVariantConfiguration variantConfiguration;
	/** File collection for extra generated resource folders */
	private Collection<File> extraGeneratedResFolders;
    /** Generate resources task */
    private StandardBuildTask resourceGenTask;
	/** Micro APK build task - ie Wear OS */
	private StandardBuildTask microApkTask;
	/** File for generated sources build folder */
	private File genBuildDir;
	/** File for intermediates build folder */
	private File intermediatesBuildDir;
	/** File for outputs build folder */
	private File outputsDir;
	/** Processes the layout XML, stripping the binding attributes and elements
     * and writes the information into an annotated class file for the annotation
     * processor to work with */
    @Nullable
    private LayoutXmlProcessor layoutXmlProcessor;
    /** APK variant output list */
    @NonNull 
    private final List<ApkData> apkDatas;
    /** Maps OutputType enum to output file */
    private final Map<OutputType, Collection<File>> outputMap =
            Maps.newHashMapWithExpectedSize(OutputType.ALL_CLASSES.ordinal() + 1);
    /** Multi-output policy */
    private final MultiOutputPolicy multiOutputPolicy;
    /** Default code shrinker selected */
    @Nullable 
    private CodeShrinker defaultCodeShrinker;
	//private DefaultConfiguration metadataValuesConfiguration;
	//private DefaultConfiguration annotationProcessorConfiguration;
	//private DefaultConfiguration runtimeClasspath;
	//private DefaultConfiguration compileClasspath;

    /**
     * Construct VariantContext object
     * @param andworxProject Project-specific Android configuration
     * @param sdkProfile Infomation on an Android SDK installation at a particular location & resources in the Android environment.
     */
	public VariantContext(
			AndworxProject andworxProject, 
			SdkProfile sdkProfile) {
		this.andworxProject = andworxProject;
		this.sdkProfile = sdkProfile;
		// SdkProfile logs errors on the build console as well as file logger
		this.logger = sdkProfile;
		buildType = BuilderConstants.DEBUG;
		variantConfigMap = new HashMap<>();
		extraGeneratedResFolders = new ArrayList<>();
		genBuildDir = new File(andworxProject.getBuildFolder(), FD_GENERATED);
		intermediatesBuildDir = new File(andworxProject.getBuildFolder(), FD_INTERMEDIATES);
		outputsDir=  new File(andworxProject.getBuildFolder(), FD_OUTPUTS);
		/** TODO - add Split APK feature. */
	    apkDatas = new ArrayList<>();
	    // Split APK not supported. For future reference: MULTI_APK allows minSdkVersion <21 and SPLITS does not. 
	    multiOutputPolicy = MultiOutputPolicy.MULTI_APK;
	    //metadataValuesConfiguration = new DefaultConfiguration("metadataValue");
	    //annotationProcessorConfiguration = new	DefaultConfiguration("annotationProcessor");	
	    //runtimeClasspath = new DefaultConfiguration("runtimeClasspath");
	    //compileClasspath = new DefaultConfiguration("compileClasspat");
	}

	/**
	 * Returns Android SDK logger
	 * @return ILogger object
	 */
	public ILogger getLogger() {
		return logger;
	}

	/**
	 * Set build type selection by name
	 * @param buildType Name of build type 
	 */
	public void setBuildType(String buildType) {
		this.buildType = buildType;
		variantConfiguration = getVariantConfiguration(this.buildType);
	}

	/**
	 * Returns current variant configuration 
	 * @return AndworxVariantConfiguration object
	 */
	public AndworxVariantConfiguration getVariantConfiguration() {
		if (variantConfiguration == null)
			throw new AndworxException("Variant configuratin not set");
		return variantConfiguration;
	}

	/**
	 * Returns variant configuration for given buildType name
	 * @return AndworxVariantConfiguration object
	 */
	public AndworxVariantConfiguration getVariantConfiguration(String buildType) {
		AndworxVariantConfiguration variantConfig = variantConfigMap.get(buildType);
		if (variantConfig == null)
			throw new AndworxException("Build type \"" + buildType + "\" not found");
		return variantConfig;
	}

	/**
	 * Put variantConfiguration mapped to given buildType as key
	 * @param buildType
	 * @param variantConfiguration
	 */
	public void putVariantConfiguration(String buildType, AndworxVariantConfiguration variantConfiguration) {
		variantConfigMap.put(buildType, variantConfiguration);
		if (this.variantConfiguration == null)
			this.variantConfiguration = variantConfiguration;
	}

	/**
	 * Returns Project-specific Android configuration
	 * @return AndworxProject object
	 */
	public AndworxProject getAndworxProject() {
		return andworxProject;
	}

	/**
	 * Returns SDK Target and build tools information needed to build a project
	 * @return TargetInfo object
	 */
	public TargetInfo getTargetInfo() {
		// Values are not cached as SDK platform is user configurable
        String hashString = AndroidTargetHash.PLATFORM_HASH_PREFIX + andworxProject.getApiVersion();
        AndroidSdkHandler androidSdkHandler = sdkProfile.getAndroidSdkHandler();
        ProgressIndicator progressIndicator = sdkProfile.getProgressIndicator();
		AndroidTargetManager targetManager = androidSdkHandler.getAndroidTargetManager(progressIndicator);
		IAndroidTarget target = targetManager.getTargetFromHashString(hashString, progressIndicator);
		BuildToolInfo buildToolInfo = andworxProject.getBuildToolInfo();
		return new TargetInfo(target, buildToolInfo);
	}
	
    /**
     * Returns the path of the local SDK,
     */
    @NonNull
    public File getSdkLocation() {
        return sdkProfile.getSdkDirectory();
    }

    /**
     * Returns compound task name consisting of prefix, full name and suffix
     * @param prefix Task name prefix
     * @param suffix Task name suffix
     * @return name
     */
    @NonNull
    public String getTaskName(@NonNull String prefix, @NonNull String suffix) {
        return StringHelper.appendCapitalized(prefix, variantConfiguration.getFullName(), suffix);
    }

    /**
     * Returns build tools info for given version
     * @param buildToolsVersion Version in text format
     * @return BuildToolInfo object
     */
	public BuildToolInfo getBuildToolsInfo(String buildToolsVersion) {
		return sdkProfile
				.getAndroidSdkHandler()
				.getBuildToolInfo(Revision
				.parseRevision(buildToolsVersion), sdkProfile.getProgressIndicator());
	}

	/**
	 * Return flag set true if png images are to be crunched
	 * @return boolean
	 */
	public boolean isCrunchPngs() {
		return CRUNCH_PNGS;
	}

	/**
	 * Returns project dependencies 
	 * @return ArtifactCollection object
	 */
	public ArtifactCollection getDependencies() {
		return dependencies;
	}

	/**
	 * Set project dependencies
	 * @param dependencies ArtifactCollection object
	 */
	public void setDependencies(ArtifactCollection dependencies) {
		this.dependencies = dependencies;
	}

	/**
	 * Register generated resource folders
	 * @param folders File collection
	 */
    public void registerGeneratedResFolders(@NonNull Collection<File> folders) {
        extraGeneratedResFolders.addAll(folders);
    }

    /**
     * Returns Micro APK build task - ie Wear OS
     * @return StandardBuildTask object
     */
    public StandardBuildTask getMicroApkTask() {
        return microApkTask;
    }

    /**
     * Set Micro APK build task - ie Wear OS 
     * @param microApkTask  StandardBuildTask object
     */
    public void setMicroApkTask(StandardBuildTask microApkTask) {
        this.microApkTask = microApkTask;
    }

    /**
     * Returns generate resource sources task
     * @return StandardBuildTask object
     */
    public StandardBuildTask getResourceGenTask() {
        return resourceGenTask;
    }

    /**
     * Set generate resource sources task
     * @param microApkTask  StandardBuildTask object
     */
    public void setResourceGenTask(StandardBuildTask resourceGenTask) {
        this.resourceGenTask = resourceGenTask;
    }

    /**
     * Returns multi-output policy
     * @return MultiOutputPolicy object
     */
    @NonNull
    public MultiOutputPolicy getMultiOutputPolicy() {
        return multiOutputPolicy;
    }

    /**
     * Returns intermediates build folder
     * @return File object
     */
    @NonNull
	public File getIntermediatesDir() {
		return intermediatesBuildDir;
	}

    /**
     * Returns generated sources build folder
     * @return File object
     */
    @NonNull
	public File getGeneratedDir() {
		return genBuildDir;
	}

    /**
     * Returns outputs build folder
     * @return File object
     */
    @NonNull
    public File getOutputsDir() {
        return outputsDir;
    }

    /**
     * Returns temp build folder
     * @return File object
     */
    @NonNull
    public File getTmpFolder() {
        return new File(intermediatesBuildDir, "tmp");
    }

	@Nullable
    public Collection<File> getExtraGeneratedResFolders() {
        return extraGeneratedResFolders;
    }
    
    @NonNull
    public File getRenderscriptResOutputDir() {
        return getGeneratedResourcesDir("rs");
    }
    
    @NonNull
    public File getGeneratedResOutputDir() {
        return getGeneratedResourcesDir("resValues");
    }

    @NonNull
    public File getGeneratedPngsOutputDir() {
        return getGeneratedResourcesDir("pngs");
    }

    @NonNull
    public File getAidlSourceOutputDir() {
        return new File(genBuildDir,
                "source/aidl/" + variantConfiguration.getDirName());
    }

    @NonNull
    public File getPackagedAidlDir() {
        return intermediate("packaged-aidl");
    }
    
    @NonNull
    public File getLayoutInfoOutputForDataBinding() {
        return dataBindingIntermediate("layout-info");
    }

    @NonNull
    public File getRenderscriptSourceOutputDir() {
        return new File(genBuildDir,
                "source/rs/" + variantConfiguration.getDirName());
    }

    @NonNull
    public File getRenderscriptObjOutputDir() {
        return FileUtils.join(
        		intermediatesBuildDir,
                StringHelper.toStrings(
                        "rs",
                        variantConfiguration.getDirectorySegments(),
                        "obj"));
    }
    
    @NonNull
    public File getRenderscriptLibOutputDir() {
        return new File(intermediatesBuildDir,
                "rs/" + variantConfiguration.getDirName() + "/lib");
    }
    
    @NonNull
    public File getClassOutputForDataBinding() {
        return new File(
        		genBuildDir,
                "source/dataBinding/trigger/" + variantConfiguration.getDirName());
    }

    @NonNull
    public File getRClassSourceOutputDir() {
        return new File(genBuildDir,
                "source/r/" + variantConfiguration.getDirName());
    }

    @NonNull
    private File getGeneratedResourcesDir(String name) {
        return FileUtils.join(
        		genBuildDir,
                StringHelper.toStrings(
                        "res",
                        name,
                        variantConfiguration.getDirectorySegments()));
    }

    @NonNull
    public File getResourceBlameLogDir() {
        return FileUtils.join(
        		intermediatesBuildDir,
                StringHelper.toStrings(
                        "blame", "res", variantConfiguration.getDirectorySegments()));
    }

    @NonNull
    public File getMicroApkResDirectory() {
        return FileUtils.join(
        		genBuildDir,
                "res",
                "microapk",
                variantConfiguration.getDirName());
    }

    @NonNull
    public File getMicroApkManifestFile() {
        return FileUtils.join(
        		genBuildDir,
                "manifests",
                "microapk",
                getVariantConfiguration().getDirName(),
                FN_ANDROID_MANIFEST_XML);
    }

    @NonNull
    public File getDefaultMergeResourcesOutputDir() {
        return FileUtils.join(
        		intermediatesBuildDir,
                FD_RES,
                FD_MERGED,
                variantConfiguration.getDirName());
    }

    @NonNull
    public File getSymbolsOutputDir() {
        return FileUtils.join(
        		intermediatesBuildDir,
                "symbols", 
                variantConfiguration.getDirName());
    }
    
    @NonNull
    public File getBundleResourcesOutputFile() {
        return FileUtils.join(
        		intermediatesBuildDir,
                "res-bundle",
                variantConfiguration.getDirName(),
                "bundled-res.ap_");
    }
    
    @NonNull
    public File getIncrementalDir(String name) {
        return FileUtils.join(
        		intermediatesBuildDir,
                "incremental",
                name);
    }

    
    public File getManifestOutputDirectory() {
        switch (variantConfiguration.getType()) {
            case DEFAULT:
            case FEATURE:
            case LIBRARY:
                return FileUtils.join(
                		intermediatesBuildDir,
                        "manifests",
                        "full",
                        variantConfiguration.getDirName());
            case ANDROID_TEST:
                return FileUtils.join(
                		intermediatesBuildDir,
                        "manifest",
                        variantConfiguration.getDirName());
            default:
                throw new RuntimeException(
                        "getManifestOutputDirectory called for an unexpected variant.");
        }
   }

    @NonNull
    public File getManifestReportFile() {
        return FileUtils.join(
                outputsDir,
                "logs",
                "manifest-merger-"
                        + variantConfiguration.getBaseName()
                        + "-report.txt");
    }

   @NonNull
   public File getCompiledResourcesOutputDir() {
        return FileUtils.join(
        		intermediatesBuildDir,
                FD_RES,
                FD_COMPILED,
                variantConfiguration.getDirName());
   }

   @NonNull
   public File getProcessResourcePackageOutputDirectory() {
        return FileUtils.join(intermediatesBuildDir, FD_RES, variantConfiguration.getDirName());
   }

   @NonNull
   public File getProcessResourcePackageOutputFile() {
        return new File(
        		getProcessResourcePackageOutputDirectory(),
        		FN_RES_BASE + RES_QUALIFIER_SEP +
        		getMainSplit().getFullName() + SdkConstants.DOT_RES);
   }

   @NonNull
   public File getBuildConfigSourceOutputDir() {
       return new File(genBuildDir + "/source/buildConfig/"
               + variantConfiguration.getDirName());
   }

   @NonNull
   public File getProcessAndroidResourcesProguardOutputFile() {
        return new File(intermediatesBuildDir,
                "/proguard-rules/" + variantConfiguration.getDirName() + "/aapt_rules.txt");
   }
    
    @NonNull
    public File getManifestKeepListProguardFile() {
        return new File(intermediatesBuildDir, "multi-dex/" + variantConfiguration.getDirName()
                + "/manifest_keep.txt");
    }

    @NonNull
    public File getInstantRunSplitApkOutputFolder() {
        return new File(intermediatesBuildDir, "/split-apk/" + variantConfiguration.getDirName());
    }

    @NonNull
    public File getInstantRunManifestOutputDirectory() {
        return FileUtils.join(
        		intermediatesBuildDir,
                "manifests",
                "instant-run",
                variantConfiguration.getDirName());
    }
    
    /**
     * Obtains the location where APKs should be placed.
     * @return the location for APKs
     */
    @NonNull
    public File getApkLocation() {
        String override = andworxProject.getProjectOptions().get(StringOption.IDE_APK_LOCATION);
        File defaultLocation = getDefaultApkLocation();

        File baseDirectory =
                override != null ?
                andworxProject.absoluteFile(override) :
                defaultLocation;

        return new File(baseDirectory, variantConfiguration.getDirName());
    }
    
    /**
     * Obtains the default location for APKs.
     * @return File object
     */
    @NonNull
    private File getDefaultApkLocation() {
        return new File(outputsDir, "apk");
    }

    @NonNull
    public File getFullApkPackagesOutputDirectory() {
        return FileUtils.join(
        		outputsDir, "splits", "full", variantConfiguration.getDirName());
    }
    
    /**
     * Returns the enabled splits for this variant. A split can be disabled due to build
     * optimization.
     *
     * @return list of splits to process for this variant.
     */
    @NonNull
    @Override
    public List<ApkData> getApkDatas() {
        return apkDatas.stream().filter(ApkData::isEnabled).collect(Collectors.toList());
    }

    /**
     * Returns main split
     * @return ApkData object
     */
    @NonNull
    @Override
    public ApkData getMainSplit() {
    	synchronized(apkDatas) {
    		if (apkDatas.isEmpty()) {
    			ApkVariantOutput apkData = new ApkVariantOutput();
       			apkData.setType(com.android.build.VariantOutput.OutputType.MAIN);
       			apkData.setVersionCode(variantConfiguration.getVersionCode());
       			apkData.setVersionName(variantConfiguration.getVersionName());
    			apkData.setBaseName(variantConfiguration.getBaseName());
    			apkData.setFullName(variantConfiguration.getFullName());
    			apkData.setDirName("");
    			apkData.setOutputFileName(andworxProject.getName() + "-" + apkData.getBaseName() + "." + SdkConstants.EXT_ANDROID_PACKAGE);
    			apkDatas.add(apkData);
    		}
    	}
    	return apkDatas.get(0);
    }

    /**
     * Returns splits selected by output type
     * @param VariantOutput.outputType OutputType enum - MAIN, FULL_SPLIT, SPLIT
     */
	@Override
	public List<ApkData> getSplitsByType(com.android.build.VariantOutput.OutputType outputType) {
        return apkDatas.stream()
                .filter(split -> split.getType() == outputType)
                .collect(Collectors.toList());
	}

	/**
	 * Returns File collection for given OutputType enum
	 * @param outputType OutputType enum 
	 * @return File collection
	 * @throws AndworxException
	 */
    @NonNull
    public Collection<File> getOutput(@NonNull OutputType outputType)
            throws AndworxException {
        Collection<File> outputFiles = outputMap.get(outputType);
        if (outputFiles == null) {
            throw new AndworxException(outputType.name() + " output file collection not found");
        }
        return outputFiles;
    }

    /**
     * Return flag set true if output of given type is contained in this context
     * @param outputType OutputType enum
     * @return boolean
     */
    public boolean hasOutput(@NonNull OutputType outputType) {
        return outputMap.containsKey(outputType);
    }

    /**
     * Add Output using given parameters
     * @param outputType OutputType enum
     * @param files File collection containing one or more output files
     * @param taskName Name of task which generated the outputs
     * @return copy of input file collection
     * @throws AndworxException
     */
    public Collection<File> addOutput(
            @NonNull OutputType outputType, 
            @NonNull Collection<File> files, 
            @Nullable String taskName)
            throws AndworxException {
        if (outputMap.containsKey(outputType)) {
            //throw new AndworxException(outputType.name() + " output type already registered");
        	return outputMap.get(outputType);
        }

        final Collection<File> collection = new ArrayList<>();
        collection.addAll(files);
        outputMap.put(outputType, collection);
        return collection;
    }

    /**
     * Returns code shrinker selection
     * @return CodeShrinker enum
     */
    @Nullable
    public CodeShrinker getCodeShrinker() {
        boolean isForTesting = variantConfiguration.getType().isForTesting();

        //noinspection ConstantConditions - getType() will not return null for a testing variant.
        if (isForTesting && variantConfiguration.getTestedType() == VariantType.LIBRARY) {
            // For now we seem to include the production library code as both program and library
            // input to the test ProGuard run, which confuses it.
            return null;
        }

        PostprocessingOptions postprocessingOptions = variantConfiguration.getPostprocessingOptions();
        // New DSL used exclusively
        CodeShrinker chosenShrinker = postprocessingOptions.getCodeShrinkerType();
        if (chosenShrinker == null) {
            chosenShrinker = getDefaultCodeShrinker();
        }

        switch (chosenShrinker) {
            case PROGUARD:
                if (!isForTesting) {
                    boolean somethingToDo =
                            postprocessingOptions.isRemoveUnusedCode() ||
                            postprocessingOptions.isObfuscate() ||
                            postprocessingOptions.isOptimizeCode();
                    return somethingToDo ? CodeShrinker.PROGUARD : null;
                } else {
                    // For testing code, we only run ProGuard if main code is obfuscated.
                    return postprocessingOptions.isObfuscate() ? CodeShrinker.PROGUARD : null;
                }
            case ANDROID_GRADLE:
                if (isForTesting) {
                    return null;
                } else {
                    return postprocessingOptions.isRemoveUnusedCode() ? CodeShrinker.ANDROID_GRADLE : null;
                }
            default:
                throw new AssertionError("Unknown value " + chosenShrinker);
        }
     }

    /**
     * Return flag set true if in instant run mode
     * @return boolean
     */
    public boolean isInInstantRunMode() {
    	// Instant run not supported
        return false;
    }

    /**
     * Returns dexing type
     * @return DexingType enum
     */
    @NonNull
    public DexingType getDexingType() {
        //if (getInstantRunBuildContext().isInInstantRunMode()) {
        //    return DexingType.NATIVE_MULTIDEX;
        //} else {
            return variantConfiguration.getDexingType();
        //}
    }
    
    /**
     * Determine if the feature module is the base feature module.
     * @return true if this feature module is the base feature module. False otherwise.
     */
    public boolean isBaseFeature() {
    	// Base feature not supported
        return false;
    }

    /**
     * Returns artifact file collection for given artifact type
     * @param artifactType
     * @return File collection
     */
    @NonNull
    public Collection<File> getArtifactFileCollection(
           // @NonNull ConsumedConfigType configType,
           // @NonNull ArtifactScope scope,
            @NonNull ArtifactType artifactType) {
        return computeArtifactCollection(artifactType);
    }
 
    /**
     * Return default code shrinker selection
     * @return CodeShrinker enum
     */
    @NonNull
    private CodeShrinker getDefaultCodeShrinker() {
        if (defaultCodeShrinker == null) {
        	defaultCodeShrinker = CodeShrinker.PROGUARD;
        	/*
            if (isInInstantRunMode()) {
                String message = "Using the built-in class shrinker for an Instant Run build.";
                PostprocessingFeatures postprocessingFeatures = getPostprocessingFeatures();
                if (postprocessingFeatures == null || postprocessingFeatures.isObfuscate()) {
                    message += " Build won't be obfuscated.";
                }
                //LOGGER.warning(message);

                defaultCodeShrinker = ANDROID_GRADLE;
            } else {
                defaultCodeShrinker = PROGUARD;
            }
            */
        }

        return defaultCodeShrinker;
    }

    /**
     * Returns flag set true if resources shrinker to be used
     * @return boolean
     */
    public boolean useResourceShrinker() {
        if (!variantConfiguration.getBuildType().isShrinkResources()) {
            return false;
        }
/* TODO - Implement checks
        if (variantData.getType() == VariantType.LIBRARY) {
            globalScope
                    .getErrorHandler()
                    .reportError(Type.GENERIC, "Resource shrinker cannot be used for libraries.");
            return false;
        }

        if (getCodeShrinker() == null) {
            globalScope
                    .getErrorHandler()
                    .reportError(
                            Type.GENERIC,
                            "Removing unused resources requires unused code shrinking to be turned on. See "
                                    + "http://d.android.com/r/tools/shrink-resources.html "
                                    + "for more information.");

            return false;
        }
*/
        return true;
    }

    /**
     * An intermediate directory for this variant.
     * <p>Of the form build/intermediates/dirName/variant/
     */
    @NonNull
    private File intermediate(@NonNull String directoryName) {
        return FileUtils.join(
        		intermediatesBuildDir,
                directoryName,
                variantConfiguration.getDirName());
    }
    
    /**
     * An intermediate file for this variant.
     * <p>Of the form build/intermediates/directoryName/variant/filename
     */
    @NonNull
    private File intermediate(@NonNull String directoryName, @NonNull String fileName) {
        return FileUtils.join(
        		intermediatesBuildDir,
                directoryName,
                variantConfiguration.getDirName(),
                fileName);
    }    @NonNull

    private File dataBindingIntermediate(String name) {
        return intermediate("data-binding", name);
    }
/*
    @NonNull
    private Configuration getConfiguration(@NonNull ConsumedConfigType configType) {
        switch (configType) {
            case COMPILE_CLASSPATH:
                return getCompileClasspath();
            case RUNTIME_CLASSPATH:
                return getRuntimeClasspath();
            case ANNOTATION_PROCESSOR:
                return getAnnotationProcessorConfiguration();
            case METADATA_VALUES:
                return Preconditions.checkNotNull(getMetadataValuesConfiguration());
            default:
                throw new RuntimeException("unknown ConfigType value " + configType);
        }
    }

	private Configuration getMetadataValuesConfiguration() {
		return metadataValuesConfiguration;
	}

	private Configuration getAnnotationProcessorConfiguration() {
		return annotationProcessorConfiguration;
	}

	private Configuration getRuntimeClasspath() {
		return runtimeClasspath;
	}

	private Configuration getCompileClasspath() {
		return compileClasspath;
	}
*/
    // TODO - computeArtifactCollection
    @NonNull
    private Collection<File> computeArtifactCollection(
            //@NonNull ConsumedConfigType configType,
            //@NonNull ArtifactScope scope,
            @NonNull ArtifactType artifactType) {

        //Configuration configuration = getConfiguration(configType);
        //return configuration.getIncoming(scope, artifactType);
    	return dependencies.getFiles(artifactType);
    }


}
