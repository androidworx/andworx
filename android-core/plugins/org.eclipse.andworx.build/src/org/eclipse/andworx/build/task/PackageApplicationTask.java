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
package org.eclipse.andworx.build.task;

import static com.android.SdkConstants.EXT_ANDROID_PACKAGE;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.Future;

import org.eclipse.andworx.build.BuildElement;
import org.eclipse.andworx.build.OutputType;
import org.eclipse.andworx.context.MultiOutputPolicy;
import org.eclipse.andworx.context.PackagingScope;
import org.eclipse.andworx.context.VariantContext;
import org.eclipse.andworx.exception.AndworxException;
import org.eclipse.andworx.helper.BuildElementFactory;
import org.eclipse.andworx.helper.BuildHelper;
import org.eclipse.andworx.packaging.AndworxPackager;
import org.eclipse.andworx.task.StandardBuildTask;
import org.eclipse.andworx.task.TaskFactory;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.OutputFile;
import com.android.build.FilterData;
import com.android.builder.files.IncrementalRelativeFileSets;
import com.android.builder.files.RelativeFile;
import com.android.builder.internal.packaging.IncrementalPackager;
import com.android.builder.packaging.PackagingUtils;
import com.android.ide.common.build.ApkInfo;
import com.android.ide.common.res2.FileStatus;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.SettableFuture;

/** 
 * Task to package an Android application (APK). 
 */
public class PackageApplicationTask extends StandardBuildTask {

	public static final String TASK_NAME = "package " + EXT_ANDROID_PACKAGE;

	private final BuildHelper buildHelper;
	private final BuildElementFactory buildElementFactory;
    private PackagingScope packagingScope;
    private File outputFile;
    private ApkInfo apkInfo;
    private OutputType manifestType;
    private Collection<File> manifests;
    private File resourceFile;
    private Set<File> dexFiles;
    private File incrementalDir;
    private String createdBy;

	public PackageApplicationTask(PackagingScope packagingScope, BuildHelper buildHelper, BuildElementFactory buildElementFactory, TaskFactory taskFactory) {
		super(taskFactory);
		this.packagingScope = packagingScope;
		this.buildHelper = buildHelper;
		this.buildElementFactory = buildElementFactory;
	}
	
	public File configure(
			@NonNull OutputType resourceFilesInputType,
            @NonNull Collection<File> manifests,
            @NonNull OutputType manifestType,
            @NonNull Set<File> dexFiles,
            @NonNull File incrementalDir,
            @Nullable String createdBy) {
		this.manifests = manifests;
		this.manifestType = manifestType;
		this.dexFiles = dexFiles;
		this.incrementalDir = incrementalDir;
		this.createdBy = createdBy;
        Collection<BuildElement> buildElements = 
        		buildElementFactory.from(resourceFilesInputType, packagingScope.getVariantScope().getOutput(resourceFilesInputType));
        if (buildElements.isEmpty())
        	throw new AndworxException("Missing bundled resources");
        BuildElement resourceElement = buildElements.iterator().next();
		resourceFile = resourceElement.getOutputFile();
        apkInfo = resourceElement.getApkInfo();
        // If we are not dealing with possible splits, we can generate in the final folder directly.
        outputFile = getOutputFile(packagingScope.getVariantScope(), packagingScope.getAndworxProject().getName());
        return outputFile;
	}

	@Override
	public String getTaskName() {
		return TASK_NAME;
	}

	@Override
	public Future<Void> doFullTaskAction() {
        final SettableFuture<Void> actualResult = SettableFuture.create();
        try {
            Collection<BuildElement> manifestOutputs = 
            		buildElementFactory.from(manifestType, manifests);
            if (manifestOutputs.isEmpty())
            	throw new AndworxException("Missing merged manifest build file");
            buildHelper.prepareDir(outputFile.getParentFile());
            ImmutableMap<RelativeFile, FileStatus> updatedDex =
                    IncrementalRelativeFileSets.fromZipsAndDirectories(dexFiles);
            ImmutableMap<RelativeFile, FileStatus> updatedJavaResources = ImmutableMap.of();
            ImmutableMap<RelativeFile, FileStatus> updatedAssets = ImmutableMap.of();
            ImmutableMap<RelativeFile, FileStatus> updatedAndroidResources = 
                    IncrementalRelativeFileSets.fromZipsAndDirectories(Collections.singletonList(resourceFile));
            ImmutableMap<RelativeFile, FileStatus> updatedJniResources = ImmutableMap.of();
            File incrementalDirForSplit = new File(incrementalDir, apkInfo.getFullName());
            doTask(
                    incrementalDirForSplit,
                    //cacheByPath,
                    manifestOutputs,
                    updatedDex,
                    updatedJavaResources,
                    updatedAssets,
                    updatedAndroidResources,
                    updatedJniResources);
       	actualResult.set(null);
        } catch (Exception e) {
        	actualResult.setException(e);
        }
		return actualResult;
	}

    /**
     * Packages the application incrementally. In case of instant run packaging, this is not a
     * perfectly incremental task as some files are always rewritten even if no change has occurred.
     *
     * @param incrementalDirForSplit
     * @param manifestOutputs
     * @param changedDex incremental dex packaging data
     * @param changedJavaResources incremental java resources
     * @param changedAssets incremental assets
     * @param changedAndroidResources incremental Android resource
     * @param changedNLibs incremental native libraries changed
     * @throws IOException failed to package the APK
     */
    private void doTask(
            @NonNull File incrementalDirForSplit,
            //@NonNull FileCacheByPath cacheByPath,
            @NonNull Collection<BuildElement> manifestOutputs,
            @NonNull ImmutableMap<RelativeFile, FileStatus> changedDex,
            @NonNull ImmutableMap<RelativeFile, FileStatus> changedJavaResources,
            @NonNull ImmutableMap<RelativeFile, FileStatus> changedAssets,
            @NonNull ImmutableMap<RelativeFile, FileStatus> changedAndroidResources,
            @NonNull ImmutableMap<RelativeFile, FileStatus> changedNLibs)
            throws IOException {

        ImmutableMap.Builder<RelativeFile, FileStatus> javaResourcesForApk =
                ImmutableMap.builder();
        javaResourcesForApk.putAll(changedJavaResources);

        final ImmutableMap<RelativeFile, FileStatus> dexFilesToPackage = changedDex;

        String filter = null;
        FilterData abiFilter = apkInfo.getFilter(OutputFile.FilterType.ABI);
        if (abiFilter != null) {
            filter = abiFilter.getIdentifier();
        }

        // find the manifest file for this split.
        BuildElement manifestForSplit = null;
        for (BuildElement element: manifestOutputs) {
        	ApkInfo it = element.getApkInfo();
        	if (it.getType().equals(apkInfo.getType()) &&
                //it.getFilters() == apkData.getFilters() &&
                it.getFullName().equals(apkInfo.getFullName())) {
        		manifestForSplit = element;
        		break;
        	}
        }
        if (manifestForSplit == null) {
            throw new AndworxException(
                    "Found a .ap_ for split "
                            + apkInfo
                            + " but no "
                            + manifestType
                            + " associated manifest file");
        }
        buildHelper.prepareDir(incrementalDirForSplit);

        try (IncrementalPackager packager =
                new AndworxPackager()
                        .withOutputFile(outputFile)
                        .withSigning(packagingScope.getSigningConfig())
                        .withCreatedBy(createdBy)
                        .withMinSdk(packagingScope.getMinSdkVersion().getApiLevel())
                        .withNativeLibraryPackagingMode(
                                PackagingUtils.getNativeLibrariesLibrariesPackagingMode(
                                        manifestForSplit.getOutputFile()))
                        .withNoCompressPredicate(
                                PackagingUtils.getNoCompressPredicate(
                                		packagingScope.getAaptOptions().getNoCompress(), manifestForSplit.getOutputFile()))
                        .withIntermediateDir(incrementalDirForSplit)
                        .withDebuggableBuild(packagingScope.isDebuggable())
                        .withAcceptedAbis(filter == null ? packagingScope.getAbiFilters() : ImmutableSet.of(filter))
                        .withJniDebuggableBuild(packagingScope.isJniDebuggable())
                        .build()) {
            packager.updateDex(dexFilesToPackage);
            packager.updateJavaResources(changedJavaResources);
            packager.updateAssets(changedAssets);
            packager.updateAndroidResources(changedAndroidResources);
            packager.updateNativeLibraries(changedNLibs);
        }
    }

	private File getOutputFile(VariantContext variantScope, String projectName) {
        final boolean splitsArePossible = 
        		variantScope.getMultiOutputPolicy() == MultiOutputPolicy.SPLITS;
		File outputDir = splitsArePossible ?
               variantScope.getFullApkPackagesOutputDirectory() :
               variantScope.getApkLocation();
        //return new File(outputDir, apkInfo.getOutputFileName());
        return new File(outputDir, projectName + "-" + variantScope.getVariantConfiguration().getBaseName() + "." + SdkConstants.EXT_ANDROID_PACKAGE);
	}
}
