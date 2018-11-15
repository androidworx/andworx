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
package org.eclipse.andworx.file;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.andworx.entity.AndroidSourceBean;
import org.eclipse.andworx.model.CodeSource;
import org.eclipse.andworx.model.SourcePath;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

/**
 * An AndroidSourceSet represents a logical group of Java, aidl and RenderScript sources
 * as well as Android and non-Android (Java-style) resources.
 */
public class AndroidSourceSet {
    public static final String DEFAULT_PROJECT_ROOT = "src/main";
    
	private final AndroidSourceBean androidSourceBean;
	private final Map<CodeSource, List<SourcePath>> sourceSet;
	/** Root of the source sets to a given path */
	private File rootPath;
	
	public AndroidSourceSet(@Nullable AndroidSourceBean androidSourceBean, File rootPath) {
		this.androidSourceBean = androidSourceBean;
		this.rootPath = rootPath;
		sourceSet = new HashMap<>();
		// Default relative path in project for source files. 
		// Change to match that of manifest, if configured.
		String defaultProjectRoot  = DEFAULT_PROJECT_ROOT;
		if (androidSourceBean !=  null) {
			for (SourcePath sourcePath: androidSourceBean.getSourcePaths()) {
				CodeSource codeSource = sourcePath.getCodeSource();
				List<SourcePath> sourcePathList = sourceSet.get(codeSource);
				if (sourcePathList == null) {
					sourcePathList = new ArrayList<>();
					sourceSet.put(codeSource, sourcePathList);
				}
				sourcePathList.add(sourcePath);
				if (codeSource == CodeSource.manifest) {
					int pos = sourcePath.getPath().lastIndexOf('/');
					if (pos == -1)
						defaultProjectRoot = "";
					else
						defaultProjectRoot = sourcePath.getPath().substring(0, pos);
				}
			}
		}
		// Add remaining required items to make the set complete
		completeSourceSet(defaultProjectRoot);
	}
	
    /**
     * Returns the name of this source set.
     * @return name
     */
    @NonNull
    public String getName() {
    	return androidSourceBean.getName();
    }

    /**
     * Returns project absolute path
     * @return File object
     */
    public File getRootPath() {
    	return rootPath;
    }

    /**
     * Returns source path list for given CodeSource enum
     * @param codeSource CodeSource enum
     * @return SourcePath list
     */
    public List<SourcePath> getSourcePathList(CodeSource codeSource) {
    	return sourceSet.get(codeSource);
    }
    
    /**
     * Returns the directory set for the specified source set.
     * @return AndroidSourceDirectorySet object
     */
    public AndroidSourceDirectorySet getDirectorySet(CodeSource codeSource) {
		if (codeSource.isFile)
			throw new IllegalArgumentException(codeSource.description + " is a file");
    	return new AndroidSourceDirectorySet(this, getSourcePathList(codeSource));
    }
    
    /**
     * Returns the Android Manifest file
     * @return File object
     */
    @NonNull
    public File getManifest() {
    	String path = sourceSet.get(CodeSource.manifest).get(0).getPath();
    	return new File(rootPath, path);
    }
    /**
     * Create source set if it does not exist
     * @param defaultPathPrefix Default path to prepend to source folder
     */
    private void completeSourceSet(String defaultPathPrefix) {
		for (CodeSource codeSource: CodeSource.values()) {
			List<SourcePath> sourcePathList = sourceSet.get(codeSource);
			if (sourcePathList == null) { // Add default
				sourcePathList = new ArrayList<>();
				sourceSet.put(codeSource, sourcePathList);
				sourcePathList.add(new SourcePath() {

					@Override
					public CodeSource getCodeSource() {
						return codeSource;
					}

					@Override
					public String getPath() {
						return defaultPathPrefix + codeSource.defaultPath;
					}

					@Override
					public List<String> getIncludes() {
						return null;
					}

					@Override
					public List<String> getExcludes() {
						return null;
					}});
			}
		}
    }
}
