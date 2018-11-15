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

import static com.android.SdkConstants.FD_RES;
import static com.android.SdkConstants.FD_ASSETS;
import static com.android.SdkConstants.FN_ANDROID_MANIFEST_XML;

import java.io.File;
import java.io.FileFilter;
import java.util.HashMap;
import java.util.Map;

import com.android.annotations.Nullable;

/**
 * Source Set type enum, includes attributes supporting implementation of source sets
 */
public enum CodeSource {
    javaSource("Java source", "/java", false, true),
    javaResources("Java resources", "/resources", false, true),
    manifest("manifest", "/" + FN_ANDROID_MANIFEST_XML, true, false),
    assets("assets", "/" + FD_ASSETS, false, false),
    res("resources", "/" + FD_RES , false, false),
    aidl("aidl", "/aidl", false, false),
    renderscript("renderscript", "/rs", false, false),
    jni("jni", "/jni", false, false),
    jniLibs("jniLibs", "/jniLibs", false, false),
    shaders("shaders", "/shaders", false, false);

    public boolean isFile;
    public boolean hasFilter;
    public String defaultPath;
    public String description;
    /** Keywords used in gradle.build configuration */
    public static Map<String, CodeSource> KEYWORD_MAP;
    
    static {
    	KEYWORD_MAP = new HashMap<>();
    	KEYWORD_MAP.put("java", javaSource);
    	KEYWORD_MAP.put("resources", javaResources);
    	KEYWORD_MAP.put("manifest", manifest);
    	KEYWORD_MAP.put("assets", assets);
    	KEYWORD_MAP.put("res", res);
    	KEYWORD_MAP.put("aidl", aidl);
    	KEYWORD_MAP.put("renderscript", renderscript);
    	KEYWORD_MAP.put("jni", jni);
    	KEYWORD_MAP.put("jniLibs", jniLibs);
    	KEYWORD_MAP.put("shaders", shaders);
    }
    
    CodeSource(String description, String defaultPath, boolean isFile, boolean hasFilter) {
    	this.description = description;
    	this.defaultPath = defaultPath;
    	this.isFile = isFile;
    	this.hasFilter = hasFilter;
    }
    
    public static @Nullable FileFilter getFileFilter(CodeSource codeSource) {
		String JAVA_EXTENSION = ".java";
    	switch (codeSource) 
    	{
    	case javaSource: return new FileFilter() {

			@Override
			public boolean accept(File pathname) {
				return pathname.getName().endsWith(JAVA_EXTENSION);
			}};
    	case javaResources: return new FileFilter() {

			@Override
			public boolean accept(File pathname) {
				return !pathname.getName().endsWith(JAVA_EXTENSION);
			}};
		default:
			return null;
    	}
    }
}
