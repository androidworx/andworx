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
package org.eclipse.andworx.model;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.SigningConfig;

/**
 * Encapsulates all build type configurations for a project, excluding those shared by ProductFlavor.
 *
 * <p>Unlike using {@link com.android.build.gradle.internal.dsl.ProductFlavor} to create
 * different versions of your project that you expect to co-exist on a single device, build
 * types determine how Gradle builds and packages each version of your project. Developers
 * typically use them to configure projects for various stages of a development lifecycle. For
 * example, when creating a new project from Android Studio, the Android plugin configures a
 * 'debug' and 'release' build type for you. By default, the 'debug' build type enables
 * debugging options and signs your APK with a generic debug keystore. Conversely, The 'release'
 * build type strips out debug symbols and requires you to <a
 * href="https://developer.android.com/studio/publish/app-signing.html#sign-apk">create a
 * release key and keystore</a> for your app. You can then combine build types with product
 * flavors to <a href="https://developer.android.com/studio/build/build-variants.html">create
 * build variants</a>.
 *
 * @see com.android.builder.model.BuildType
 */
public interface BuildTypeAtom {
    /**
     * Describes how code postProcessing is configured. Do not allow mixing the old and new DSLs.
     */
    public enum PostprocessingConfiguration {
        POSTPROCESSING_BLOCK,
        OLD_DSL,
    }

	void setSigningConfig(SigningConfig signingConfig);

	/**
     * Returns the name of the build type.
     *
     * @return the name of the build type.
     */
    @NonNull
    String getName();

    /**
     * Returns whether the build type is configured to generate a debuggable apk.
     *
     * @return true if the apk is debuggable
     */
    boolean isDebuggable();

    /**
     * Returns whether the build type is configured to be build with support for code coverage.
     *
     * @return true if code coverage is enabled.
     */
    boolean isTestCoverageEnabled();

    /**
     * Returns whether the build type is configured to be build with support for pseudolocales.
     *
     * @return true if code coverage is enabled.
     */
    boolean isPseudoLocalesEnabled();

    /**
     * Returns whether the build type is configured to generate an apk with debuggable native code.
     *
     * @return true if the apk is debuggable
     */
    boolean isJniDebuggable();

    /**
     * Returns whether the build type is configured to generate an apk with debuggable
     * renderscript code.
     *
     * @return true if the apk is debuggable
     */
    boolean isRenderscriptDebuggable();

    /**
     * Returns the optimization level of the renderscript compilation.
     *
     * @return the optimization level.
     */
    int getRenderscriptOptimLevel();

    /**
     * Specifies whether to enable code shrinking for this build type.
     *
     * <p>By default, when you enable code shrinking by setting this property to <code>true</code>,
     * the Android plugin uses ProGuard. However while deploying your app using Android Studio's <a
     * href="https://d.android.com/studio/run/index.html#instant-run">Instant Run</a> feature, which
     * doesn't support ProGuard, the plugin switches to using a custom experimental code shrinker.
     *
     * <p>If you experience issues using the experimental code shrinker, you can disable code
     * shrinking while using Instant Run by setting <a
     * href="http://google.github.io/android-gradle-dsl/current/com.android.build.gradle.internal.dsl.BuildType.html#com.android.build.gradle.internal.dsl.BuildType:useProguard">
     * <code>useProguard</code></a> to <code>true</code>.
     *
     * <p>To learn more, read <a
     * href="https://developer.android.com/studio/build/shrink-code.html">Shrink Your Code and
     * Resources</a>.
     *
     * @return true if code shrinking is enabled.
     */
    boolean isMinifyEnabled();

    /**
     * Return whether zipalign is enabled for this build type.
     *
     * @return true if zipalign is enabled.
     */
    boolean isZipAlignEnabled();

    /**
     * Returns whether the variant embeds the micro app.
     * @return boolean
     */
    boolean isEmbedMicroApp();

    /**
     * Returns the associated signing config or null if none are set on the build type.
     */
    @Nullable
    SigningConfig getSigningConfig();

    /**
     * Whether to crunch PNGs.
     *
     * <p>Setting this property to <code>true</code> reduces of PNG resources that are not already
     * optimally compressed. However, this process increases build times.
     *
     * <p>PNG crunching is enabled by default in the release build type and disabled by default in
     * the debug build type.
     */
    @Nullable
    Boolean isCrunchPngs();

    /**
     * Flag set true if shrink resources enabled
     * @return boolean
     */
	boolean isShrinkResources();

}
