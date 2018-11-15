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
package org.eclipse.andworx.packaging;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.apkzlib.zfile.ApkCreatorFactory;
import com.android.apkzlib.zfile.ApkZFileCreatorFactory;
import com.android.apkzlib.zfile.NativeLibrariesPackagingMode;
import com.android.apkzlib.zip.ZFileOptions;
import com.android.apkzlib.zip.compress.BestAndDefaultDeflateExecutorCompressor;
import com.android.apkzlib.zip.compress.DeflateExecutionCompressor;
import com.android.builder.internal.packaging.IncrementalPackager;
import com.android.builder.model.SigningConfig;
import com.android.builder.packaging.PackagerException;
import com.android.builder.packaging.PackagingUtils;
import com.android.ide.common.signing.CertificateInfo;
import com.android.ide.common.signing.KeystoreHelper;
import com.android.ide.common.signing.KeytoolException;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.zip.Deflater;

import org.eclipse.andworx.exception.AndworxException;

/**
 * Packages APKs
 */
public class AndworxPackager {
    /** Time after which background compression threads should be discarded */
    private static final long BACKGROUND_THREAD_DISCARD_TIME_MS = 100;
    /** Maximum number of compression threads */
    private static final int MAXIMUM_COMPRESSION_THREADS = 2;

    /** Signing key. {@code null} if not defined. */
    @Nullable
    private PrivateKey key;
    /** Signing certificate. {@code null} if not defined */
    private X509Certificate certificate;
    /** Is V1 signing enabled? */
    private boolean v1SigningEnabled;
    /** Is V2 signing enabled? */
    private boolean v2SigningEnabled;
    /** The output file.*/
    @Nullable
    private File outputFile;
    /** The minimum SDK.*/
    private int minSdk;
    /** How should native libraries be packaged. If not defined, it can be inferred if
     * {@link #manifest} is defined. */
    @Nullable
    private NativeLibrariesPackagingMode nativeLibrariesPackagingMode;
    /**
     * The no-compress predicate: returns {@code true} for paths that should not be compressed.
     * If not defined, but {@link #aaptOptions} and {@link #manifest} are both defined, it can be inferred.
     */
    @Nullable
    private Predicate<String> noCompressPredicate;

    /** Directory for intermediate contents. */
    @Nullable
    private File intermediateDir;
    /** Created-By. */
    @Nullable
    private String createdBy;
    /** Is the build debuggable? */
    private boolean debuggableBuild;
   /** Is the build JNI-debuggable? */
    private boolean jniDebuggableBuild;
    /** ABI filters. Empty if none. */
    @NonNull
    private Set<String> abiFilters;
    /** Manifest. */
    @Nullable
    private File manifest;
    /** aapt options no compress config. */
    @Nullable private Collection<String> aaptOptionsNoCompress;
    
    /**
     * Create AndworxPackager object
     */
    public AndworxPackager() {
        minSdk = 1;
        abiFilters = new HashSet<>();
    }

    /**
     * Sets the signing configuration information for the incremental packager.
     *
     * @param signingConfig the signing config; if {@code null} then the APK will not be signed
     * @return {@code this} for use with fluent-style notation
     */
    @NonNull
    public AndworxPackager withSigning(@Nullable SigningConfig signingConfig) {
        try {
            if (signingConfig != null && signingConfig.isSigningReady()) {
                CertificateInfo certificateInfo = KeystoreHelper.getCertificateInfo(
                        signingConfig.getStoreType(),
                        Preconditions.checkNotNull(signingConfig.getStoreFile()),
                        Preconditions.checkNotNull(signingConfig.getStorePassword()),
                        Preconditions.checkNotNull(signingConfig.getKeyPassword()),
                        Preconditions.checkNotNull(signingConfig.getKeyAlias()));
                key = certificateInfo.getKey();
                certificate = certificateInfo.getCertificate();
                v1SigningEnabled = signingConfig.isV1SigningEnabled();
                v2SigningEnabled = signingConfig.isV2SigningEnabled();
            }
        } catch (KeytoolException|FileNotFoundException e) {
            throw new AndworxException(signingConfig.getName() + " signing certificate not found", e);
        }
        return this;
    }

    /**
     * Sets the output file for the APK.
     *
     * @param f the output file
     * @return {@code this} for use with fluent-style notation
     */
    @NonNull
    public AndworxPackager withOutputFile(@NonNull File outputFile) {
        this.outputFile = outputFile;
        return this;
    }

    /**
     * Sets the minimum SDK.
     *
     * @param minSdk the minimum SDK
     * @return {@code this} for use with fluent-style notation
     */
    @NonNull
    public AndworxPackager withMinSdk(int minSdk) {
        this.minSdk = minSdk;
        return this;
    }

    /**
     * Sets the packaging mode for native libraries.
     *
     * @param packagingMode the packging mode
     * @return {@code this} for use with fluent-style notation
     */
    @NonNull
    public AndworxPackager withNativeLibraryPackagingMode(
            @NonNull NativeLibrariesPackagingMode packagingMode) {
        nativeLibrariesPackagingMode = packagingMode;
        return this;
    }

    /**
     * Sets the manifest. While the manifest itself is not used for packaging, information on
     * the native libraries packaging mode can be inferred from the manifest.
     *
     * @param manifest the manifest
     * @return {@code this} for use with fluent-style notation
     */
    public AndworxPackager withManifest(@NonNull File manifest) {
        this.manifest = manifest;
        return this;
    }

    /**
     * Sets the no-compress predicate. This predicate returns {@code true} for files that should
     * not be compressed
     *
     * @param noCompressPredicate the predicate
     * @return {@code this} for use with fluent-style notation
     */
    @NonNull
    public AndworxPackager withNoCompressPredicate(
            @NonNull Predicate<String> noCompressPredicate) {
        this.noCompressPredicate = noCompressPredicate;
        return this;
    }

    /**
     * Sets the {@code aapt} options no compress predicate.
     *
     * <p>The no-compress predicate can be computed if this and the manifest (see {@link
     * #withManifest(File)}) are both defined.
     */
    @NonNull
    public AndworxPackager withAaptOptionsNoCompress(
            @Nullable Collection<String> aaptOptionsNoCompress) {
        this.aaptOptionsNoCompress = aaptOptionsNoCompress;
        return this;
    }

    /**
     * Sets the intermediate directory used to store information for incremental builds.
     *
     * @param intermediateDir the intermediate directory
     * @return {@code this} for use with fluent-style notation
     */
    @NonNull
    public AndworxPackager withIntermediateDir(@NonNull File intermediateDir) {
        this.intermediateDir = intermediateDir;
        return this;
    }

    /**
     * Sets the created-by parameter.
     *
     * @param createdBy the optional value for created-by
     * @return {@code this} for use with fluent-style notation
     */
    @NonNull
    public AndworxPackager withCreatedBy(@Nullable String createdBy) {
        this.createdBy = createdBy;
        return this;
    }

    /**
     * Sets whether the build is debuggable or not.
     *
     * @param debuggableBuild is the build debuggable?
     * @return {@code this} for use with fluent-style notation
     */
    @NonNull
    public AndworxPackager withDebuggableBuild(boolean debuggableBuild) {
        this.debuggableBuild = debuggableBuild;
        return this;
    }

    /**
     * Sets whether the build is JNI-debuggable or not.
     *
     * @param jniDebuggableBuild is the build JNI-debuggable?
     * @return {@code this} for use with fluent-style notation
     */
    @NonNull
    public AndworxPackager withJniDebuggableBuild(boolean jniDebuggableBuild) {
        this.jniDebuggableBuild = jniDebuggableBuild;
        return this;
    }

    /**
     * Sets the set of accepted ABIs.
     *
     * @param acceptedAbis the accepted ABIs; if empty then all ABIs are accepted
     * @return {@code this} for use with fluent-style notation
     */
    public AndworxPackager withAcceptedAbis(@NonNull Set<String> acceptedAbis) {
        this.abiFilters = ImmutableSet.copyOf(acceptedAbis);
        return this;
    }


    /**
     * Creates the packager, verifying that all the minimum data has been provided. The required
     * information are:
     *
     * <ul>
     *    <li>{@link #withOutputFile(File)}
     *    <li>{@link #withProject(Project)}
     *    <li>{@link #withIntermediateDir(File)}
     * </ul>
     *
     * @return the incremental packager
     */
    @NonNull
    public IncrementalPackager build() {
        Preconditions.checkState(outputFile != null, "outputFile == null");
        Preconditions.checkState(intermediateDir != null, "intermediateDir == null");

        if (noCompressPredicate == null) {
            if (manifest != null) {
                noCompressPredicate =
                        PackagingUtils.getNoCompressPredicate(aaptOptionsNoCompress, manifest);
            } else {
                noCompressPredicate = path -> false;
            }
        }

        if (nativeLibrariesPackagingMode == null) {
            if (manifest != null) {
                nativeLibrariesPackagingMode =
                        PackagingUtils.getNativeLibrariesLibrariesPackagingMode(manifest);
            } else {
                nativeLibrariesPackagingMode = NativeLibrariesPackagingMode.COMPRESSED;
            }
        }

        ApkCreatorFactory.CreationData creationData =
                new ApkCreatorFactory.CreationData(
                        outputFile,
                        key,
                        certificate,
                        v1SigningEnabled,
                        v2SigningEnabled,
                        null,
                        createdBy,
                        minSdk,
                        nativeLibrariesPackagingMode,
                        noCompressPredicate);

        try {
            return new IncrementalPackager(
                    creationData,
                    intermediateDir,
                    fromProjectProperties(
                            debuggableBuild),
                    abiFilters,
                    jniDebuggableBuild);
        } catch (PackagerException|IOException e) {
            throw new RuntimeException(e);
        }
    }
 
    /**
     * Creates an {@link ApkCreatorFactory} based on the definitions in the project. This is  only
     * to be used with the incremental packager.
     *
     * @param project the project whose properties will be checked
     * @param debuggableBuild whether the {@link ApkCreatorFactory} will be used to create a
     *                        debuggable archive
     * @return the factory
     */
    @NonNull
    public static ApkCreatorFactory fromProjectProperties(
            boolean debuggableBuild) {
    	// TODO - make this configurable
        boolean keepTimestamps = false;

        ZFileOptions options = new ZFileOptions();
        options.setNoTimestamps(!keepTimestamps);
        options.setCoverEmptySpaceUsingExtraField(true);

        ThreadPoolExecutor compressionExecutor =
                new ThreadPoolExecutor(
                        0, /* Number of always alive threads */
                        MAXIMUM_COMPRESSION_THREADS,
                        BACKGROUND_THREAD_DISCARD_TIME_MS,
                        TimeUnit.MILLISECONDS,
                        new LinkedBlockingDeque<>());

        if (debuggableBuild) {
            options.setCompressor(
                    new DeflateExecutionCompressor(
                            compressionExecutor,
                            options.getTracker(),
                            Deflater.BEST_SPEED));
        } else {
            options.setCompressor(
                    new BestAndDefaultDeflateExecutorCompressor(
                            compressionExecutor,
                            options.getTracker(),
                            1.0));
            options.setAutoSortFiles(true);
        }

        return new ApkZFileCreatorFactory(options);
    }
}
