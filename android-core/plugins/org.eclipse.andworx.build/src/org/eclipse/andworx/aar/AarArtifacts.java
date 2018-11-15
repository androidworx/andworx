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
package org.eclipse.andworx.aar;

import static android.databinding.tool.DataBindingBuilder.DATA_BINDING_CLASS_LOG_ROOT_FOLDER_IN_AAR;
import static com.android.SdkConstants.FD_AIDL;
import static com.android.SdkConstants.FD_ASSETS;
import static com.android.SdkConstants.FD_JARS;
import static com.android.SdkConstants.FD_JNI;
import static com.android.SdkConstants.FD_RENDERSCRIPT;
import static com.android.SdkConstants.FD_RES;
import static com.android.SdkConstants.FN_ANNOTATIONS_ZIP;
import static com.android.SdkConstants.FN_LINT_JAR;
import static com.android.SdkConstants.FN_PROGUARD_TXT;
import static com.android.SdkConstants.FN_PUBLIC_TXT;
import static com.android.SdkConstants.FN_RESOURCE_SHARED_STATIC_LIBRARY;
import static com.android.SdkConstants.FN_RESOURCE_STATIC_LIBRARY;
import static com.android.SdkConstants.FN_RESOURCE_TEXT;
import static com.android.SdkConstants.FN_R_CLASS_JAR;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.andworx.api.attributes.AndroidArchiveSet;
import org.eclipse.andworx.api.attributes.AndroidArtifacts.ArtifactType;

import com.android.annotations.NonNull;
import com.android.utils.FileUtils;

/**
 * Maps ArtifactType to AndroidArchiveSet 
 */
public class AarArtifacts {
	
    @NonNull
    public static ArtifactType[] getAarTypes() {
        return new ArtifactType[] {
            ArtifactType.CLASSES,
            ArtifactType.SHARED_CLASSES,
            ArtifactType.JAVA_RES,
            ArtifactType.SHARED_JAVA_RES,
            ArtifactType.JAR,
            ArtifactType.MANIFEST,
            ArtifactType.ANDROID_RES,
            ArtifactType.ASSETS,
            ArtifactType.SHARED_ASSETS,
            ArtifactType.JNI,
            ArtifactType.SHARED_JNI,
            ArtifactType.AIDL,
            ArtifactType.RENDERSCRIPT,
            ArtifactType.PROGUARD_RULES,
            ArtifactType.LINT,
            ArtifactType.ANNOTATIONS,
            ArtifactType.PUBLIC_RES,
            ArtifactType.SYMBOL_LIST,
            ArtifactType.DATA_BINDING_ARTIFACT,
            ArtifactType.DATA_BINDING_BASE_CLASS_LOG_ARTIFACT,
            ArtifactType.COMPILE_ONLY_NAMESPACED_R_CLASS_JAR,
            ArtifactType.RES_STATIC_LIBRARY,
            ArtifactType.RES_SHARED_STATIC_LIBRARY,
        };
    }

    private static class ClassesAndroidArchiveSet extends AndroidArchiveSet {
    	
		@Override
		public void add(File explodedAar) {
            // Even though resources are supposed to only be in the main jar of the AAR, this
            // is not necessarily enforced by all build systems generating AAR so it's safer to
            // read all jars from the manifest.
            // For shared libraries, these are provided via SHARED_CLASSES and SHARED_JAVA_RES.
            if (!isShared(explodedAar))
                files.addAll(getJars(explodedAar));
		}
    }
    
    private static class SharedClassesAndroidArchiveSet extends AndroidArchiveSet {
    	
		@Override
		public void add(File explodedAar) {
            if (isShared(explodedAar))
                files.addAll(getJars(explodedAar));
		}
    }

    private static class SingleAndroidArchive extends AndroidArchiveSet {

    	private String resource;
    
    	public SingleAndroidArchive(String resource) {
    		this.resource = resource;
    	}
    	
		@Override
		public void add(File explodedAar) {
			listIfExists(new File(explodedAar, resource));
		}
    	
    }
	private final Map<ArtifactType, AndroidArchiveSet> typeMap;
	
	public AarArtifacts(boolean sharedLibSupport) {
		typeMap = new HashMap<>();
		initialize();
		if (sharedLibSupport) 
			typeMap.values().forEach(androidArchiveSet -> androidArchiveSet.setSharedLibSupport(true));
	}

	public void addExplodedAar(File explodedAar) {
		for (Map.Entry<ArtifactType, AndroidArchiveSet> entry: typeMap.entrySet()) {
			// Some types share an AndroidArchiveSet object
			if (entry.getKey() == ArtifactType.JAVA_RES ||
			    entry.getKey() == ArtifactType.JAR ||
			    entry.getKey() == ArtifactType.SHARED_JAVA_RES)
                continue;
			entry.getValue().add(explodedAar);
		}
	}
	
	public AndroidArchiveSet getAndroidArchiveSet(ArtifactType type) {
		return typeMap.get(type);
	}

	private void initialize() {
		AndroidArchiveSet classesAndroidArchiveSet = new ClassesAndroidArchiveSet();
		AndroidArchiveSet sharedClassesAndroidArchiveSet = new SharedClassesAndroidArchiveSet();
		typeMap.put(ArtifactType.CLASSES, classesAndroidArchiveSet);
		typeMap.put(ArtifactType.JAVA_RES, classesAndroidArchiveSet);
		typeMap.put(ArtifactType.JAR, classesAndroidArchiveSet);
		typeMap.put(ArtifactType.SHARED_CLASSES, sharedClassesAndroidArchiveSet);
		typeMap.put(ArtifactType.SHARED_JAVA_RES, sharedClassesAndroidArchiveSet);
		typeMap.put(ArtifactType.LINT, new AndroidArchiveSet() {

			@Override
			public void add(File explodedAar) {
				listIfExists(FileUtils.join(explodedAar, FD_JARS, FN_LINT_JAR));
			}});
		typeMap.put(ArtifactType.ANDROID_RES, new SingleAndroidArchive(FD_RES));
		typeMap.put(ArtifactType.ASSETS, new SingleAndroidArchive(FD_ASSETS));
		typeMap.put(ArtifactType.JNI, new SingleAndroidArchive(FD_JNI));
		typeMap.put(ArtifactType.AIDL, new SingleAndroidArchive(FD_AIDL));
		typeMap.put(ArtifactType.RENDERSCRIPT, new SingleAndroidArchive(FD_RENDERSCRIPT));
		typeMap.put(ArtifactType.PROGUARD_RULES, new SingleAndroidArchive(FN_PROGUARD_TXT));
		typeMap.put(ArtifactType.ANNOTATIONS, new SingleAndroidArchive(FN_ANNOTATIONS_ZIP));
		typeMap.put(ArtifactType.PUBLIC_RES, new SingleAndroidArchive(FN_PUBLIC_TXT));
		typeMap.put(ArtifactType.SYMBOL_LIST, new SingleAndroidArchive(FN_RESOURCE_TEXT));
		typeMap.put(ArtifactType.RES_STATIC_LIBRARY, new AndroidArchiveSet() {

			@Override
			public void add(File explodedAar) {
				if (!isShared(explodedAar))
                    listIfExists(new File(explodedAar, FN_RESOURCE_STATIC_LIBRARY));
			}});
		typeMap.put(ArtifactType.RES_SHARED_STATIC_LIBRARY, new AndroidArchiveSet() {

			@Override
			public void add(File explodedAar) {
				if (isShared(explodedAar))
                     listIfExists(new File(explodedAar, FN_RESOURCE_SHARED_STATIC_LIBRARY));
			}});
		typeMap.put(ArtifactType.COMPILE_ONLY_NAMESPACED_R_CLASS_JAR, new SingleAndroidArchive(FN_R_CLASS_JAR));
		typeMap.put(ArtifactType.DATA_BINDING_ARTIFACT, new SingleAndroidArchive(DATA_BINDING_CLASS_LOG_ROOT_FOLDER_IN_AAR));
	}

}
