/*
 * Copyright (C) 2018 The Android Open Source Project
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
package org.eclipse.andworx.project;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.andworx.build.AndworxFactory;
import org.eclipse.andworx.config.AndroidConfig;
import org.eclipse.andworx.context.VariantContext;
import org.eclipse.andworx.model.ProjectSourceProvider;
import org.eclipse.andworx.options.ProjectOptions;
import org.eclipse.andworx.sdk.SdkProfile;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.core.BuilderConstants;
import com.android.builder.internal.aapt.AaptOptions;
import com.android.builder.model.AndroidProject;
//import com.android.builder.model.ApiVersion;
import com.android.builder.model.ArtifactMetaData;
import com.android.builder.model.BuildTypeContainer;
//import com.android.builder.model.ClassField;
import com.android.builder.model.JavaCompileOptions;
import com.android.builder.model.LintOptions;
import com.android.builder.model.NativeToolchain;
import com.android.builder.model.ProductFlavor;
import com.android.builder.model.ProductFlavorContainer;
import com.android.builder.model.SigningConfig;
import com.android.builder.model.SourceProvider;
import com.android.builder.model.SourceProviderContainer;
import com.android.builder.model.SyncIssue;
import com.android.builder.model.Variant;
//import com.android.builder.model.VectorDrawablesOptions;
import com.android.builder.model.Version;
import com.android.repository.Revision;
import com.android.repository.api.ProgressIndicator;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableMap;

/**
 * Partial implementation of Android project model
 */
public class AndworxProject implements AndroidProject {

	public static final String BUILD_DIR = "build";
	public static final String INTERMEDIATE_DIR = "intermediates";
	public static final String INCREMENTAL_DIR = "incremental";
	public static final String RESOURCE_BLAME_DIR = "res-blame";
	public static final AaptOptions DEFAULT_AAPT_OPTIONS = new AaptOptions();
	private static final int DEFAULT_API_VERSION = 27;
	private static final LintOptions DEFAULT_LINT_OPTIONS = new DefaultLintOptions();

	/** ProductFlavorContainer implementation */
	class DefaultProductFlavorContainer implements ProductFlavorContainer {
		final String name;
		public DefaultProductFlavorContainer(String name) {
			this.name = name;
		}
		@Override
		public ProductFlavor getProductFlavor() {
			return projectConfiguration.getProductFlavorMap().get(name);
		}

		@Override
		public SourceProvider getSourceProvider() {
			return defaultSourceSet;
		}
		@Override
		public Collection<SourceProviderContainer> getExtraSourceProviders() {
			return Collections.emptyList();
		}

	}
	
    @NonNull
    private final String name;
	/** Infomation on an Android SDK installation at a particular location and resources in the Android environment */
	private final SdkProfile sdkProfile;
    private final ProjectSourceProvider defaultSourceSet;
    private final AndroidConfig androidConfig;
    private final ProjectConfiguration projectConfiguration;
    /** Android variant context provides project resources and information */
    private final Map<String, VariantContext> contexts;
    @NonNull
    private final ProjectOptions projectOptions;
    @NonNull 
    private final File projectDirectory;
    @NonNull
    private Collection<String> bootClasspath;
    @NonNull
    private Collection<File> frameworkSource;
    @NonNull
    private Collection<SigningConfig> signingConfigs;
    @NonNull
    private AaptOptions aaptOptions;
    @NonNull
    private Collection<ArtifactMetaData> extraArtifacts;
    @NonNull
    private Collection<SyncIssue> syncIssues;

    private int generation;

    // TODO base split
    //private boolean baseSplit;

    @NonNull
    private LintOptions lintOptions;
    @NonNull
    private File buildFolder;
    @Nullable
    private String resourcePrefix;
    @NonNull
    private Collection<NativeToolchain> nativeToolchains;
    private int projectType;
    private int apiVersion;

    @NonNull
    private ProductFlavorContainer defaultConfig;

    //private Collection<BuildTypeContainer> buildTypes;
    private Collection<ProductFlavorContainer> productFlavors;
    private Collection<Variant> variants;

    @NonNull
    private Collection<String> flavorDimensions;

    /**
     * Construct AndworxProject object
     * @param projectConfiguration Android project configuration extracted from database
     * @param sdkProfile Infomation on an Android SDK installation at a particular location & resources in the Android environment.
     */
    public AndworxProject(ProjectConfiguration projectConfiguration, SdkProfile sdkProfile) {
    	this.projectConfiguration = projectConfiguration;
    	this.sdkProfile = sdkProfile;
    	//this.projectOptions = projectOptions;
        this.name = projectConfiguration.getName();
   	    this.projectDirectory = projectConfiguration.getProjectDirectory();
   	    this.androidConfig = projectConfiguration.getAndroidConfig();
        //this.buildTypes = projectConfiguration.getBuildTypes();
        defaultSourceSet = projectConfiguration.getDefaultSourceSet();
        this.defaultConfig = new DefaultProductFlavorContainer(BuilderConstants.MAIN);
        this.flavorDimensions = ImmutableList.of();
        Builder<ProductFlavorContainer> builder = ImmutableList.builder();
   	    for (ProductFlavor productFlavor: projectConfiguration.getProductFlavorMap().values())
   	    	if (!productFlavor.getName().equals(BuilderConstants.MAIN))
   	    	    builder.add(new DefaultProductFlavorContainer(productFlavor.getName()));
        this.productFlavors = builder.build();
        this.variants = new HashSet<>();
        //this.compileTarget = DEFAULT_COMPILE_TARGET;
        this.bootClasspath = ImmutableList.of();
        this.frameworkSource = ImmutableList.of();
        this.signingConfigs = ImmutableList.of();
        this.aaptOptions = DEFAULT_AAPT_OPTIONS;
        this.extraArtifacts = ImmutableList.of();
        this.syncIssues = ImmutableList.of();
        this.lintOptions = DEFAULT_LINT_OPTIONS;
        this.buildFolder = absoluteDir(BUILD_DIR);
        this.resourcePrefix = "resourcePrefix";
        this.projectType = PROJECT_TYPE_APP;
        this.apiVersion = DEFAULT_API_VERSION;
        this.generation = 1;
        this.nativeToolchains = ImmutableList.of();
        //this.baseSplit = false;
        ImmutableMap.Builder<String, Object> optionsBuilder = ImmutableMap.builder();
        //optionsBuilder.put(key, value);
        projectOptions = new ProjectOptions(optionsBuilder.build());
      	contexts = AndworxFactory.instance().createVariantContextMap(this, projectConfiguration);
    }

    public Map<String, VariantContext> getContexts() {
		return contexts;
	}

	public ProjectSourceProvider getProjectSourceProvider() {
    	return defaultSourceSet;
    }
    
    public ProjectOptions getProjectOptions() {
		return projectOptions;
	}

	/**
     * Returns an absolute path for given directory path relative to project location
     * @return
     */
    public @Nullable File absoluteDir(String folder) {
    	return new File(projectDirectory, folder);
    }
    
    /**
     * Returns an absolute path for given file path relative to project location
     * @return
     */
    public @Nullable File absoluteFile(String file) {
    	return new File(projectDirectory, file);
    }
    
	public Set<File> files(List<File> fileList) {
		Set<File> fileSet = new TreeSet<>();
		for (File file: fileList)
			fileSet.add(absoluteFile(file.getPath()));
		return fileSet;
	}
	
    @Override
    @NonNull
    public String getModelVersion() {
        return Version.ANDROID_GRADLE_PLUGIN_VERSION;
    }

    @Override
    public int getApiVersion() {
        return apiVersion;
    }

    @Override
    @NonNull
    public String getName() {
        return name;
    }

    @Override
    @NonNull
    public ProductFlavorContainer getDefaultConfig() {
    	return defaultConfig;
    }

    @Override
    @NonNull
    public Collection<BuildTypeContainer> getBuildTypes() {
        return projectConfiguration.getBuildTypes();
    }

    @Override
    @NonNull
    public Collection<ProductFlavorContainer> getProductFlavors() {
        return productFlavors;
    }

    @Override
    @NonNull
    public Collection<Variant> getVariants() {
        return variants;
    }

    @NonNull
    @Override
    public Collection<String> getFlavorDimensions() {
        return flavorDimensions;
    }

    @NonNull
    @Override
    public Collection<ArtifactMetaData> getExtraArtifacts() {
        return extraArtifacts;
    }

    @Override
    public boolean isLibrary() {
        return getProjectType() == PROJECT_TYPE_LIBRARY;
    }

    @Override
    public int getProjectType() {
        return projectType;
    }

    @Override
    @NonNull
    public String getCompileTarget() {
        return androidConfig.getCompileSdkVersion();
    }

    @Override
    @NonNull
    public Collection<String> getBootClasspath() {
        return bootClasspath;
    }

    @Override
    @NonNull
    public Collection<File> getFrameworkSources() {
        return frameworkSource;
    }

    @Override
    @NonNull
    public Collection<SigningConfig> getSigningConfigs() {
        return signingConfigs;
    }

    @Override
    @NonNull
    public com.android.builder.model.AaptOptions getAaptOptions() {
    	DefaultAaptOptions defaultAaptOptions = new DefaultAaptOptions();
    	List<String> additionalParams = aaptOptions.getAdditionalParameters();
    	if (additionalParams != null)
    		defaultAaptOptions.setAdditionalParameters(additionalParams);
    	defaultAaptOptions.setFailOnMissingConfigEntry(aaptOptions.getFailOnMissingConfigEntry());
        return defaultAaptOptions;
    }

    @Override
    @NonNull
    public LintOptions getLintOptions() {
        return lintOptions;
    }

    @Override
    @NonNull
    public Collection<String> getUnresolvedDependencies() {
        return ImmutableList.of();
    }

    @NonNull
    @Override
    public Collection<SyncIssue> getSyncIssues() {
        return syncIssues;
    }

    @Override
    @NonNull
    public JavaCompileOptions getJavaCompileOptions() {
    	// Not currently used by Andworx so just return default
        return new JavaCompileOptions() {

			@Override
			public String getEncoding() {
				return "UTF-8";
			}

			@Override
			public String getSourceCompatibility() {
				return "1.8";
			}

			@Override
			public String getTargetCompatibility() {
				return "1.8";
			}};
    }

    @Override
    @NonNull
    public File getBuildFolder() {
        return buildFolder;
    }

    @Override
    @Nullable
    public String getResourcePrefix() {
        return resourcePrefix;
    }

    @NonNull
    @Override
    public Collection<NativeToolchain> getNativeToolchains() {
        return nativeToolchains;
    }

    @NonNull
    @Override
    public String getBuildToolsVersion() {
        return getBuildToolInfo().getRevision().toString();
    }

    @NonNull
    public BuildToolInfo getBuildToolInfo() {
        ProgressIndicator progressIndicator = sdkProfile.getProgressIndicator();
		Revision revision = Revision.parseRevision(androidConfig.getBuildToolsVersion());
		AndroidSdkHandler androidSdkHandler = sdkProfile.getAndroidSdkHandler();
		BuildToolInfo buildToolInfo = androidSdkHandler.getBuildToolInfo(revision , progressIndicator);
		if (buildToolInfo == null)
			 buildToolInfo = androidSdkHandler.getLatestBuildTool(progressIndicator, true /*allowPreview*/);
        return buildToolInfo;
    }
    
    @Override
    public int getPluginGeneration() {
        return generation;
    }

    @Override
    public boolean isBaseSplit() {
        return false;
    }

}
