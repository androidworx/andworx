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
package org.eclipse.andworx.config;

import com.android.builder.model.ProductFlavor;
import com.android.builder.model.SigningConfig;

/**
 * User configuration settings within the "android" block, excluding those shared by BuildType
 */
public interface AndroidConfig {

    /**
     * Specifies the version of the <a
     * href="https://developer.android.com/studio/releases/build-tools.html">SDK Build Tools</a> to
     * use when building your project.
     *
     * <p>Configuring this property is optional. 
     * By default, Andworx uses the minimum version of the build tools required by the <a
     * href="https://developer.android.com/studio/releases/gradle-plugin.html#revisions">version of
     * the plugin</a> you're using. To specify a different version of the build tools for the plugin
     * to use, specify the version as follows:
     *
     * <pre>
     * // Specifying this property is optional.
     * buildToolsVersion "26.0.0"
     * </pre>
     *
     * <p>For a list of build tools releases, read <a
     * href="https://developer.android.com/studio/releases/build-tools.html#notes">the release
     * notes</a>.
     *
     * <p>Note that the value assigned to this property is parsed and stored in a normalized form,
     * so reading it back may give a slightly different result.
     */
    String getBuildToolsVersion();

    /**
     * Specifies the project build API level. If this omitted, then it defaults to the API level against which
     * Andworx was built. 
     *
     * <p>This means your code can use only the Android APIs included in that API level and lower.
     * You can configure the compile sdk version by adding the following to the <code>android</code>
     * block: <code>compileSdkVersion 27</code>.
     *
     * <p>You should generally <a
     * href="https://developer.android.com/guide/topics/manifest/uses-sdk-element.html#ApiLevels">use
     * the most up-to-date API level</a> available. If you are planning to also support older API
     * levels, it's good practice to <a
     * href="https://developer.android.com/studio/write/lint.html">use the Lint tool</a> to check if
     * you are using APIs that are not available in earlier API levels.
     *
     * <p>The value you assign to this property is parsed and stored in a normalized form, so
     * reading it back may return a slightly different value.
     */
    String getCompileSdkVersion();

    /**
     * Specifies defaults for variant properties that the Android plugin applies to all build
     * variants.
     *
     * <p>You can override any <code>defaultConfig</code> property when <a
     * href="https://developer.android.com/studio/build/build-variants.html#product-flavors">configuring
     * product flavors</a>.
     *
     * @see com.android.build.gradle.internal.dsl.ProductFlavor
     */
    ProductFlavor getDefaultConfig();

    /**
     * Encapsulates signing configurations that you can apply to {@link
     * com.android.build.gradle.internal.dsl.BuildType} and {@link
     * com.android.build.gradle.internal.dsl.ProductFlavor} configurations.
     *
     * <p>Android requires that all APKs be digitally signed with a certificate before they can be
     * installed onto a device. When deploying a debug version of your project from Android Studio,
     * the Android plugin automatically signs your APK with a generic debug certificate. However, to
     * build an APK for release, you must <a
     * href="https://developer.android.com/studio/publish/app-signing.html">sign the APK</a> with a
     * release key and keystore. You can do this by either <a
     * href="https://developer.android.com/studio/publish/app-signing.html#sign-apk">using the
     * Android Studio UI</a> or manually <a
     * href="https://developer.android.com/studio/publish/app-signing.html#gradle-sign">configuring
     * your <code>build.gradle</code> file</a>.
     *
     * @see com.android.build.gradle.internal.dsl.SigningConfig
     */
    SigningConfig getSigningConfig(String name);
}
