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
package org.eclipse.andworx.project;

import java.util.ArrayList;
import java.util.Set;

import com.android.ide.common.xml.ManifestData;
import com.android.ide.common.xml.ManifestData.Activity;
import com.android.ide.common.xml.ManifestData.Instrumentation;
import com.android.ide.common.xml.ManifestData.KeepClass;
import com.android.ide.common.xml.ManifestData.SupportsScreens;
import com.android.ide.common.xml.ManifestData.UsesConfiguration;
import com.android.ide.common.xml.ManifestData.UsesFeature;
import com.android.ide.common.xml.ManifestData.UsesLibrary;

/**
 * Proxy for Android SDK ManifestData
 * @author andrew
 *
 */
public class AndroidManifestData {
    /** Application package */
	public String manifestPackage;
    /** Application version Code, null if the attribute is not present. */
	public Integer versionCode = null;
    /** Default Dex process */
	public String defaultProcess;
    /** List of all activities */
	public final ArrayList<Activity> activities = new ArrayList<Activity>();
    /** List of all activities, services, receivers and providers to keep for Proguard and Dex * */
	public final ArrayList<KeepClass> keepClasses = new ArrayList<KeepClass>();
    /** Launcher activity */
	public Activity launcherActivity = null;
    /** list of process names declared by the manifest */
	public Set<String> processes = null;
    /** debuggable attribute value. If null, the attribute is not present. */
	public Boolean debuggable = null;
    /** API level requirement. if null the attribute was not present. */
	public String minSdkVersionString = null;
    /** API level requirement. Default is 1 even if missing. If value is a codename, then it'll be
     * 0 instead. */
	public int minSdkVersion = 1;
	public int targetSdkVersion = 0;
    /** List of all instrumentations declared by the manifest */
	public final ArrayList<Instrumentation> instrumentations = new ArrayList<Instrumentation>();
    /** List of all libraries in use declared by the manifest */
	public final ArrayList<UsesLibrary> libraries = new ArrayList<UsesLibrary>();
    /** List of all feature in use declared by the manifest */
	public final ArrayList<UsesFeature> features = new ArrayList<UsesFeature>();

	public SupportsScreens supportsScreensFromManifest;
	public SupportsScreens supportsScreensValues;
	public UsesConfiguration usesConfiguration;

	public AndroidManifestData() {
		
	}
	
	public AndroidManifestData(ManifestData manifestData) {
		manifestPackage = manifestData.getPackage();
		versionCode = manifestData.getVersionCode();
		defaultProcess = manifestData.getDefaultProcess();
		for (Activity activity: manifestData.getActivities())
			activities.add(activity);
		for (KeepClass keepClass: manifestData.getKeepClasses())
			keepClasses.add(keepClass);
		
		launcherActivity = manifestData.getLauncherActivity();
		for (String process: manifestData.getProcesses())
			processes.add(process);
		debuggable = manifestData.getDebuggable();
		minSdkVersionString = manifestData.getMinSdkVersionString();
		targetSdkVersion = manifestData.getTargetSdkVersion();
		for (Instrumentation instrumentation: manifestData.getInstrumentations())
			instrumentations.add(instrumentation);
		for (UsesLibrary usesLibrary: manifestData.getUsesLibraries()) {
			libraries.add(usesLibrary);
		}
		
		for (UsesFeature usesFeature: manifestData.getUsesFeatures())
			features.add(usesFeature);
		supportsScreensFromManifest = manifestData.getSupportsScreensFromManifest();
		supportsScreensValues = manifestData.getSupportsScreensValues();
		usesConfiguration = manifestData.getUsesConfiguration();
	}
	

}
