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
package org.eclipse.andworx.model;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import com.android.builder.core.DefaultVectorDrawablesOptions;
import com.android.builder.model.ApiVersion;
import com.android.builder.model.SigningConfig;

/**
 * Encapsulates all product flavor configurations for a project, excluding those shared by BuildType.
 */
public interface ProductFlavorAtom {

	String getName();

	/**
	 * Returns the application ID.
	 *
	 * <p>See <a href="https://developer.android.com/studio/build/application-id.html">Set the Application ID</a>
	 */
	String getApplicationId();

	/**
	 * Version code.
	 *
	 * <p>See <a href="http://developer.android.com/tools/publishing/versioning.html">Versioning Your Application</a>
	 */
	Integer getVersionCode();

	/**
	 * Version name.
	 *
	 * <p>See <a href="http://developer.android.com/tools/publishing/versioning.html">Versioning Your Application</a>
	 */
	String getVersionName();

	/**
	 * Min SDK version.
	 */
	ApiVersion getMinSdkVersion();

	/**
	 * Target SDK version.
	 */
	ApiVersion getTargetSdkVersion();

	Integer getMaxSdkVersion();

	Integer getRenderscriptTargetApi();

	Boolean getRenderscriptSupportModeEnabled();

	Boolean getRenderscriptSupportModeBlasEnabled();

	Boolean getRenderscriptNdkModeEnabled();

	/**
	 * Test application ID.
	 *
	 * <p>See <a href="https://developer.android.com/studio/build/application-id.html">Set the Application ID</a>
	 */
	String getTestApplicationId();

	/**
	 * Test instrumentation runner class name.
	 *
	 * <p>This is a fully qualified class name of the runner, e.g.
	 * <code>android.test.InstrumentationTestRunner</code>
	 *
	 * <p>See <a href="http://developer.android.com/guide/topics/manifest/instrumentation-element.html">
	 * instrumentation</a>.
	 */
	String getTestInstrumentationRunner();

	/**
	 * Test instrumentation runner custom arguments.
	 *
	 * <p>e.g. <code>[key: "value"]</code> will give <code>
	 * adb shell am instrument -w <b>-e key value</b> com.example</code>...".
	 *
	 * <p>See <a
	 * href="http://developer.android.com/guide/topics/manifest/instrumentation-element.html">
	 * instrumentation</a>.
	 *
	 * <p>Test runner arguments can also be specified from the command line:
	 *
	 * <p>
	 *
	 * <pre>
	 * ./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.size=medium
	 * ./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.foo=bar
	 * </pre>
	 */
	Map<String, String> getTestInstrumentationRunnerArguments();

	/**
	 * See <a href="http://developer.android.com/guide/topics/manifest/instrumentation-element.html">
	 * instrumentation</a>.
	 */
	Boolean getTestHandleProfiling();

	/**
	 * See <a href="http://developer.android.com/guide/topics/manifest/instrumentation-element.html">
	 * instrumentation</a>.
	 */
	Boolean getTestFunctionalTest();

	/**
	 * Signing config used by this product flavor.
	 */
	SigningConfig getSigningConfig();

	/**
	 * Returns the VectorDrawablesOptions generatedDensities set.
	 * The set is initiated on bean construction and then updated upon
	 * every getVectorDrawables() call.
	 * @return
	 */
	Set<String> getGeneratedDensities();

	/**
	 * Options to configure the build-time support for {@code vector} drawables.
	 */
	DefaultVectorDrawablesOptions getVectorDrawables();

	/**
	 * Returns whether to enable unbundling mode for embedded wear app.
	 *
	 * If true, this enables the app to transition from an embedded wear app to one
	 * distributed by the play store directly.
	 */
	Boolean getWearAppUnbundled();

	/**
	 * Adds a res config filter (for instance 'hdpi')
	 */
	Collection<String> getResourceConfigurations();

}