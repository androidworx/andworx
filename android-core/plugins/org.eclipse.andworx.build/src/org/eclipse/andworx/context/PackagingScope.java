/*
 * Copyright (C) 2016 The Android Open Source Project
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


import static com.android.SdkConstants.FN_APK_CLASSES_DEX;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import org.eclipse.andworx.build.OutputType;
import org.eclipse.andworx.core.VariantConfiguration;
import org.eclipse.andworx.options.ProjectOptions;
import org.eclipse.andworx.project.AndworxProject;
import org.eclipse.andworx.transform.TransformAgent;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.internal.aapt.AaptOptions;
import com.android.builder.model.PackagingOptions;
import com.android.builder.model.SigningConfig;
import com.android.ide.common.build.ApkData;
import com.android.sdklib.AndroidVersion;
import com.android.utils.FileUtils;

/** 
 * Encapsulates the data required to perform packaging tasks
 * TODO - Some android config options to be implemented for splits and packaging options
 */
public class PackagingScope {

	/** Android variant context provides project resources and information */
    private final VariantContext variantScope;
    /** Variant configuration */
    private final VariantConfiguration<?> variantConfig;
    /** Functions necessary to perform pipeline transforms */
    private final TransformAgent transformAgent;

    /**
     * Construct PackagingScope object
     * @param variantScope Android variant context provides project resources and information
     */
	public PackagingScope(VariantContext variantScope) {
		this.variantScope = variantScope;
		variantConfig = variantScope.getVariantConfiguration();
		transformAgent = new TransformAgent();
	}

    public VariantContext getVariantScope() {
		return variantScope;
	}

    @NonNull
    public String getFullVariantName() {
        return variantConfig.getFullName();
    }

    @NonNull
    public AndroidVersion getMinSdkVersion() {
        return variantConfig.getMinSdkVersion();
    }

    @NonNull
    public Collection<File> getDexFolders() {
    	File dexFile = FileUtils.join(getDexMergeDir(variantScope), "0", FN_APK_CLASSES_DEX);
        return Collections.singletonList(dexFile);
    }

    @NonNull
    public Collection<File> getJavaResources() {
    	// No StreamFilter.RESOURCES referenced in gradle-core
        return Collections.emptyList();
    }

    @NonNull
    public Collection<File> getJniFolders() {
    	// No treamFilter.NATIVE_LIBS referenced in gradle-core
        return Collections.emptyList();
    }

    @NonNull
    public MultiOutputPolicy getMultiOutputPolicy() {
        return variantScope.getMultiOutputPolicy();
    }

    @NonNull
    public Set<String> getAbiFilters() {
        return Collections.emptySet(); //androidConfig.getSplits().getAbiFilters();
    }

    @Nullable
    public Set<String> getSupportedAbis() {
    	return Collections.emptySet(); //androidConfig.getDefaultConfig().getNdkOptions().getAbiFilters();
    }

    public boolean isDebuggable() {
        return variantConfig.getBuildType().isDebuggable();
    }

    public boolean isJniDebuggable() {
        return variantConfig.getBuildType().isJniDebuggable();
    }

    @Nullable
    public SigningConfig getSigningConfig() {
    	return variantConfig.getSigningConfig();
    }

    @NonNull
    public PackagingOptions getPackagingOptions() {
        return new PackagingOptions(){

			@Override
			public Set<String> getExcludes() {
				return Collections.emptySet();
			}

			@Override
			public Set<String> getPickFirsts() {
				return Collections.emptySet();
			}

			@Override
			public Set<String> getMerges() {
				return Collections.emptySet();
			}

			@Override
			public Set<String> getDoNotStrip() {
				return Collections.emptySet();
			}}; //androidConfig.getAppExtension().getPackagingOptions();
    }
    
    @NonNull
    public String getTaskName(@NonNull String name) {
        return variantScope.getTaskName(name, "");
    }

    @NonNull
    public String getTaskName(@NonNull String prefix, @NonNull String suffix) {
        return variantScope.getTaskName(prefix, suffix);
    }

    @NonNull
    public AndworxProject getProject() {
        return variantScope.getAndworxProject();
    }

    public String getProjectBaseName() {
        return variantScope.getAndworxProject().getName();
    }
    
    @NonNull
    public File getInstantRunSplitApkOutputFolder() {
        return variantScope.getInstantRunSplitApkOutputFolder();
    }

    @NonNull
    public String getApplicationId() {
        return variantScope.getVariantConfiguration().getApplicationId();
    }

    public int getVersionCode() {
        // FIX ME : DELETE this API and have everyone use the concept of mainSplit.
        ApkData mainApkData = variantScope.getMainSplit();
        if (mainApkData != null) {
            return mainApkData.getVersionCode();
        }
        return variantConfig.getVersionCode();
    }

    @Nullable
    public String getVersionName() {
        return variantConfig.getVersionName();
    }

    @NonNull
    public AaptOptions getAaptOptions() {
        return AndworxProject.DEFAULT_AAPT_OPTIONS;
    }

    public ProjectOptions getProjectOptions() {
        return variantScope.getAndworxProject().getProjectOptions();
    }

    public OutputScope getOutputScope() {
        return variantScope;
    }

    @NonNull
    public Collection<File> getOutput(@NonNull OutputType outputType) {
        return variantScope.getOutput(outputType);
    }

    public boolean hasOutput(@NonNull OutputType outputType) {
        return variantScope.hasOutput(outputType);
    }

    public Collection<File> addOutput(
            @NonNull OutputType outputType, @NonNull Collection<File> file, @Nullable String taskName) {
        return variantScope.addOutput(outputType, file, taskName);
    }

    public boolean isAbiSplitsEnabled() {
        return false; //androidConfig.getSplits().getAbi().isEnabled();
    }

    public boolean isDensitySplitsEnabled() {
        return false; //androidConfig.getSplits().getDensity().isEnabled();
    }

	private File getDexMergeDir(VariantContext variantScope) {
		return transformAgent.getOutputRootDir("dexMerger", variantScope);
	}
}
