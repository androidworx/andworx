/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.sdkuilib.internal.repository.content;

/**
 * PackageType enumerates the first-segment in package paths
 * @author Andrew Bowley
 *
 * 17-12-2017
 */
public enum PackageType {
	tools,
	platform_tools,
	build_tools,
	platforms,
	add_ons,
	system_images,
	sources,
	samples,
	docs,
    extras,
    emulator,
    cmake,
    lldb,
    ndk_bundle,
    patcher,
    generic,
    early_platforms;

	/** Label to display each type */
	public String label;
	
	static {
		tools.label = "Tools";
		platform_tools.label = "Platform tools";
		build_tools.label = "Build tools";
		platforms.label = "Platforms";
		add_ons.label = "Add ons";
		system_images.label = "System images";
		sources.label = "Sources";
		samples.label = "Samples";
		docs.label = "Documents";
	    extras.label = "Extras";
	    emulator.label = "Emulators";
	    cmake.label = "cmake";
	    lldb.label = "Layout Libraries";
	    ndk_bundle.label = "NDK bundle";
	    patcher.label = "Patcher";
	    generic.label = "Generic";
	    early_platforms.label = "Early Platforms";
	}
}
