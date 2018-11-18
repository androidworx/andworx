/*
 * Copyright (C) 2012 The Android Open Source Project
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

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

import org.eclipse.andworx.api.attributes.ArtifactCollection;
import org.eclipse.andworx.build.BuildElement;
import org.eclipse.andworx.build.OutputType;
import org.eclipse.andworx.context.OutputScope;
import org.eclipse.andworx.core.AndworxVariantConfiguration;
import org.eclipse.andworx.exception.AndworxException;
import org.eclipse.andworx.helper.BuildHelper;
import org.eclipse.andworx.log.SdkLogger;
import org.eclipse.andworx.task.ApplicationId;
import org.eclipse.andworx.task.ManifestMergeHandler;
import org.eclipse.andworx.task.StandardBuildTask;
import org.eclipse.andworx.task.TaskFactory;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.build.ApkData;
import com.android.manifmerger.ManifestMerger2;
import com.android.manifmerger.ManifestMerger2.Invoker;
import com.android.manifmerger.ManifestMerger2.Invoker.Feature;
import com.android.manifmerger.ManifestProvider;
import com.android.manifmerger.ManifestSystemProperty;
import com.android.manifmerger.MergingReport;
import com.android.manifmerger.XmlDocument;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.common.util.concurrent.SettableFuture;

/**
 * Merge manifests
 */
public class ManifestMergerTask extends StandardBuildTask {
	
	public static final String TASK_NAME = "Manifest merge";
	
    /**
     * Implementation of AndroidBundle that only contains a manifest.
     *
     * This is used to pass to the merger manifest snippet that needs to be added during
     * merge.
     */
    public static class ManifestProviderImpl implements ManifestProvider {

        @NonNull
        private final File manifest;

        @NonNull
        private final String name;

        public ManifestProviderImpl(@NonNull File manifest, @NonNull String name) {
            this.manifest = manifest;
            this.name = name;
        }

        @NonNull
        @Override
        public File getManifest() {
            return manifest;
        }

        @NonNull
        @Override
        public String getName() {
            return name;
        }
    }

    private static SdkLogger logger = SdkLogger.getLogger(ManifestMergerTask.class.getName());
    
    private final ManifestMergeHandler manifestMergeHandler;
    private final BuildHelper buildHelper;
    private File manifestOutputDirectory;
    private File instantRunManifestOutputDirectory;

    private File reportFile;
    private String minSdkVersion;
    private String targetSdkVersion;
    private Integer maxSdkVersion;
    private AndworxVariantConfiguration variantConfiguration;
    private ArtifactCollection manifests;
    //private ArtifactCollection featureManifests;
    private List<File> microApkManifest;
    //private Collection<File> compatibleScreensManifest;
    private List<File> packageManifest;
    private List<Feature> optionalFeatures;
    private OutputScope outputScope;

    private String featureName;

    public ManifestMergerTask(ManifestMergeHandler manifestMergeHandler, BuildHelper buildHelper, TaskFactory taskFactory) {
		super(taskFactory);
    	this.manifestMergeHandler = manifestMergeHandler;
		this.buildHelper = buildHelper;
    }
    
	public void setMinSdkVersion(String minSdkVersion) {
		this.minSdkVersion = minSdkVersion;
	}

	public void setTargetSdkVersion(String targetSdkVersion) {
		this.targetSdkVersion = targetSdkVersion;
	}

	public void setMaxSdkVersion(Integer maxSdkVersion) {
		this.maxSdkVersion = maxSdkVersion;
	}

	public void setVariantConfiguration(AndworxVariantConfiguration variantConfiguration) {
		this.variantConfiguration = variantConfiguration;
	}

	public void setManifests(ArtifactCollection manifests) {
		this.manifests = manifests;
	}

	public void setMicroApkManifest(List<File> microApkManifest) {
		this.microApkManifest = microApkManifest;
	}

	//public void setCompatibleScreensManifest(Collection<File> compatibleScreensManifest) {
	//	this.compatibleScreensManifest = compatibleScreensManifest;
	//}

	public void setPackageManifest(List<File> packageManifest) {
		this.packageManifest = packageManifest;
	}

	public void setOutputScope(OutputScope outputScope) {
		this.outputScope = outputScope;
	}

	public void setFeatureName(String featureName) {
		this.featureName = featureName;
	}

	public void setManifestOutputDirectory(File manifestOutputDirectory) {
		this.manifestOutputDirectory = manifestOutputDirectory;
	}

	public void setInstantRunManifestOutputDirectory(File instantRunManifestOutputDirectory) {
		this.instantRunManifestOutputDirectory = instantRunManifestOutputDirectory;
	}

	public void setReportFile(File reportFile) {
		this.reportFile = reportFile;
	}

	@Override
	public String getTaskName() {
		return TASK_NAME;
	}

	public void setOptionalFeatures(List<Feature> optionalFeatures) {
		this.optionalFeatures = optionalFeatures;
	}

	@Override
	public Future<Void> doFullTaskAction() {
        final SettableFuture<Void> actualResult = SettableFuture.create();
        try {
            // Read the output of the compatible screen manifest.
     		//BuildElementFactory buildElementFactory = BuildElementFactory.instance();
            //Collection<BuildElement> compatibleScreenManifests =
            //		buildElementFactory.from(
            //                OutputType.COMPATIBLE_SCREEN_MANIFEST,
            //                compatibleScreensManifest);
            String packageOverride;
            if (packageManifest != null && !packageManifest.isEmpty()) {
                packageOverride =
                        ApplicationId.load(packageManifest.get(0)).getApplicationId();
            } else {
                packageOverride = variantConfiguration.getIdOverride();
            }
            @Nullable BuildElement compatibleScreenManifestForSplit;

            ImmutableList.Builder<BuildElement> mergedManifestOutputs = ImmutableList.builder();
            ImmutableList.Builder<BuildElement> irMergedManifestOutputs = ImmutableList.builder();

            // FIX ME : multi threading.
            // TODO : LOAD the APK_LIST FILE .....
            for (ApkData apkData : outputScope.getApkDatas()) {
                compatibleScreenManifestForSplit = null; //buildElementFactory.find(compatibleScreenManifests, apkData);
                File manifestOutputFile =
                    FileUtils.join(
                    		manifestOutputDirectory,
                            apkData.getDirName(),
                            SdkConstants.ANDROID_MANIFEST_XML);
                File instantRunManifestOutputFile =
                    FileUtils.join(
                            instantRunManifestOutputDirectory,
                            apkData.getDirName(),
                            SdkConstants.ANDROID_MANIFEST_XML);
                buildHelper.prepareDir(instantRunManifestOutputFile.getParentFile());
                MergingReport mergingReport = mergeManifestsForApplication(
					variantConfiguration.getMainManifest(),
					variantConfiguration.getManifestOverlays(),
				    computeFullProviderList(compatibleScreenManifestForSplit),
				    variantConfiguration.getNavigationFiles(),
				    featureName,
				    packageOverride,
				    apkData.getVersionCode(),
				    apkData.getVersionName(),
				    minSdkVersion,
				    targetSdkVersion,
				    maxSdkVersion,
				    manifestOutputFile.getAbsolutePath(),
				    // no aapt friendly merged manifest file necessary for applications.
				    null /* aaptFriendlyManifestOutputFile */,
				    instantRunManifestOutputFile.getAbsolutePath(),
				    ManifestMerger2.MergeType.APPLICATION,
				    variantConfiguration.getManifestPlaceholders(),
				    optionalFeatures,
				    reportFile);
                XmlDocument mergedXmlDocument =
                        mergingReport.getMergedXmlDocument(MergingReport.MergedManifestKind.MERGED);

                ImmutableMap<String, String> properties =
                    mergedXmlDocument != null ?
                        ImmutableMap.of(
                            "packageId",
                            mergedXmlDocument.getPackageName(),
                            "split",
                            mergedXmlDocument.getSplitName(),
                            SdkConstants.ATTR_MIN_SDK_VERSION,
                            mergedXmlDocument.getMinSdkVersion()) :
                        ImmutableMap.of();
                mergedManifestOutputs.add(
                    new BuildElement(
                            OutputType.MERGED_MANIFESTS,
                            apkData,
                            manifestOutputFile,
                            properties));
                irMergedManifestOutputs.add(
                    new BuildElement(
                            OutputType.INSTANT_RUN_MERGED_MANIFESTS,
                            apkData,
                            instantRunManifestOutputFile,
                            properties));
                manifestMergeHandler.onManifestMerge(mergingReport);
            }
            ImmutableList<BuildElement> buildElements = mergedManifestOutputs.build();
            buildHelper.saveBuildElements(buildElements, manifestOutputDirectory);
            buildElements = irMergedManifestOutputs.build();
            buildHelper.saveBuildElements(buildElements, instantRunManifestOutputDirectory);
	        actualResult.set(null);
        } catch(Exception x) {
        	actualResult.setException(x);
        }
		return actualResult;
	}

    /** 
     * Invoke the Manifest Merger version 2. 
     * Adpated from AndroidBuilder method of same name so that a MergingReport object is returned, if available.
     */
    private MergingReport mergeManifestsForApplication(
            @NonNull File mainManifest,
            @NonNull List<File> manifestOverlays,
            @NonNull List<? extends ManifestProvider> dependencies,
            @NonNull List<File> navigationFiles,
            @Nullable String featureName,
            String packageOverride,
            int versionCode,
            String versionName,
            @Nullable String minSdkVersion,
            @Nullable String targetSdkVersion,
            @Nullable Integer maxSdkVersion,
            @NonNull String outManifestLocation,
            @Nullable String outAaptSafeManifestLocation,
            @Nullable String outInstantRunManifestLocation,
            ManifestMerger2.MergeType mergeType,
            Map<String, Object> placeHolders,
            @NonNull List<Invoker.Feature> optionalFeatures,
            @Nullable File reportFile) {

        try {
            @SuppressWarnings({ "rawtypes", "unchecked" })
			Invoker manifestMergerInvoker =
                    ManifestMerger2.newMerger(mainManifest, logger, mergeType)
                            .setPlaceHolderValues(placeHolders)
                            .addFlavorAndBuildTypeManifests(
                                    manifestOverlays.toArray(new File[manifestOverlays.size()]))
                            .addManifestProviders(dependencies)
                            .addNavigationFiles(navigationFiles)
                            .withFeatures(
                                    optionalFeatures.toArray(
                                            new Invoker.Feature[optionalFeatures.size()]))
                            .setMergeReportFile(reportFile)
                            .setFeatureName(featureName);

            if (mergeType == ManifestMerger2.MergeType.APPLICATION) {
                manifestMergerInvoker.withFeatures(Invoker.Feature.REMOVE_TOOLS_DECLARATIONS);
            }

            //noinspection VariableNotUsedInsideIf
            if (outAaptSafeManifestLocation != null) {
                manifestMergerInvoker.withFeatures(Invoker.Feature.MAKE_AAPT_SAFE);
            }

            setInjectableValues(manifestMergerInvoker,
                    packageOverride, versionCode, versionName,
                    minSdkVersion, targetSdkVersion, maxSdkVersion);

            MergingReport mergingReport = manifestMergerInvoker.merge();
            logger.verbose("Merging result: %1$s", mergingReport.getResult());
            switch (mergingReport.getResult()) {
                case WARNING:
                    mergingReport.log(logger);
                    // fall through since these are just warnings.
                case SUCCESS:
                    String xmlDocument = mergingReport.getMergedDocument(
                            MergingReport.MergedManifestKind.MERGED);
                    String annotatedDocument = mergingReport.getMergedDocument(
                            MergingReport.MergedManifestKind.BLAME);
                    if (annotatedDocument != null) {
                        logger.verbose(annotatedDocument);
                    }
                    save(xmlDocument, new File(outManifestLocation));
                    logger.verbose("Merged manifest saved to " + outManifestLocation);

                    if (outAaptSafeManifestLocation != null) {
                        save(mergingReport.getMergedDocument(MergingReport.MergedManifestKind.AAPT_SAFE),
                                new File(outAaptSafeManifestLocation));
                    }

                    if (outInstantRunManifestLocation != null) {
                        String instantRunMergedManifest = mergingReport.getMergedDocument(
                                MergingReport.MergedManifestKind.INSTANT_RUN);
                        if (instantRunMergedManifest != null) {
                            save(instantRunMergedManifest, new File(outInstantRunManifestLocation));
                        }
                    }
                    break;
                case ERROR:
                default:
            }
            return mergingReport;
        } catch (ManifestMerger2.MergeFailureException e) {
            throw new AndworxException("Merge Manifest failed for " + mainManifest.getAbsolutePath(), e);
        }
    }

    /**
     * Sets the {@link ManifestSystemProperty} that can be injected
     * in the manifest file.
     */
    private static void setInjectableValues(
            ManifestMerger2.Invoker<?> invoker,
            String packageOverride,
            int versionCode,
            String versionName,
            @Nullable String minSdkVersion,
            @Nullable String targetSdkVersion,
            @Nullable Integer maxSdkVersion) {

        if (!Strings.isNullOrEmpty(packageOverride)) {
            invoker.setOverride(ManifestSystemProperty.PACKAGE, packageOverride);
        }
        if (versionCode > 0) {
            invoker.setOverride(ManifestSystemProperty.VERSION_CODE,
                    String.valueOf(versionCode));
        }
        if (!Strings.isNullOrEmpty(versionName)) {
            invoker.setOverride(ManifestSystemProperty.VERSION_NAME, versionName);
        }
        if (!Strings.isNullOrEmpty(minSdkVersion)) {
            invoker.setOverride(ManifestSystemProperty.MIN_SDK_VERSION, minSdkVersion);
        }
        if (!Strings.isNullOrEmpty(targetSdkVersion)) {
            invoker.setOverride(ManifestSystemProperty.TARGET_SDK_VERSION, targetSdkVersion);
        }
        if (maxSdkVersion != null) {
            invoker.setOverride(ManifestSystemProperty.MAX_SDK_VERSION, maxSdkVersion.toString());
        }
    }
    
    /**
     * Saves the {@link com.android.manifmerger.XmlDocument} to a file in UTF-8 encoding.
     * @param xmlDocument xml document to save.
     * @param out file to save to.
     */
    private static void save(String xmlDocument, File out) {
        try {
            Files.createParentDirs(out);
            Files.asCharSink(out, Charsets.UTF_8).write(xmlDocument);
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Compute the final list of providers based on the manifest file collection and the other
     * providers.
     *
     * @return the list of providers.
     */
    private List<ManifestProvider> computeFullProviderList(
            @Nullable BuildElement compatibleScreenManifestForSplit) {
        final Set<File> artifacts = manifests.getArtifactFiles();
        List<ManifestProvider> providers = Lists.newArrayListWithCapacity(artifacts.size() + 2);

        for (File artifact : artifacts) {
            providers.add(new ManifestProviderImpl(
                    artifact,
                    artifact.getPath()));
        }

        if ((microApkManifest != null) && !microApkManifest.isEmpty()) {
            // this is now always present if embedding is enabled, but it doesn't mean
            // anything got embedded so the file may not run (the file path exists and is
            // returned by the FC but the file doesn't exist.
            File microManifest = microApkManifest.get(0);
            if (microManifest.isFile()) {
                providers.add(new ManifestProviderImpl(
                        microManifest,
                        "Wear App sub-manifest"));
            }
        }

        if (compatibleScreenManifestForSplit != null){
            providers.add(
                    new ManifestProviderImpl(
                            compatibleScreenManifestForSplit.getOutputFile(),
                            "Compatible-Screens sub-manifest"));

        }
/*
        if (featureManifests != null) {
            final Set<ResolvedArtifactResult> featureArtifacts = featureManifests.getArtifacts();
            for (ResolvedArtifactResult artifact : featureArtifacts) {
                File directory = artifact.getFile();

                BuildElements splitOutputs =
                        ExistingBuildElements.from(
                                VariantScope.TaskOutputType.MERGED_MANIFESTS, directory);
                if (splitOutputs.isEmpty()) {
                    throw new AndworxException("Could not load manifest from " + directory);
                }

                providers.add(
                        new ConfigAction.ManifestProviderImpl(
                                splitOutputs.iterator().next().getOutputFile(),
                                getArtifactName(artifact)));
            }
        }
*/
        return providers;
    }

}
