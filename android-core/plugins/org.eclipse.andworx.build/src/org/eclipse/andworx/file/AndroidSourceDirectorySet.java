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

import com.android.annotations.NonNull;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.eclipse.andworx.model.CodeSource;
import org.eclipse.andworx.model.FilterPatterns;
import org.eclipse.andworx.model.SourcePath;

/**
 * An AndroidSourceDirectorySet represents a set of directory inputs for an Android project.
 */
public class AndroidSourceDirectorySet {

	/** Excludes and includes filter patterns */
	private static class SourceFilterPatterns implements FilterPatterns {
    	List<String> includes;
    	List<String> excludes;

		public SourceFilterPatterns(List<String> includes, List<String> excludes) {
			this.includes = includes;
			this.excludes = excludes;
		}
		
		@Override
		public List<String> getExcludes() {
			return excludes;
		}
		@Override
		public List<String> getIncludes() {
			return includes;
		}
	}
	
	public static List<String> EMPTY_LIST = Collections.emptyList();

	/** Owner */
	private AndroidSourceSet androidSourceSet;
	/** Paths */
	private List<SourcePath> sourcePaths;
	/** Source type */
	private CodeSource codeSource;

	/**
	 * Construct AndroidSourceDirectorySet object
	 * @param androidSourceSet Owner
	 * @param sourcePaths 
	 */
	public AndroidSourceDirectorySet(AndroidSourceSet androidSourceSet, List<SourcePath> sourcePaths) {
		this.androidSourceSet = androidSourceSet;
		this.sourcePaths = sourcePaths;
		codeSource = sourcePaths.get(0).getCodeSource();
	}

    /**
     * A concise name for the source directory (typically used to identify it in a collection).
     */
    @NonNull
    public String getName() {
    	return androidSourceSet.getName() + " " + codeSource.description;
    }

    /**
     * Returns the filter used to select the source from the source directories.
     * @return {@link SourceFilterPatterns} object
     */
    @NonNull
    public FilterPatterns getFilter() {
    	List<String> includes = EMPTY_LIST;
    	List<String> excludes = EMPTY_LIST;
    	for (SourcePath sourcePath: sourcePaths) {
    		if (!sourcePath.getExcludes().isEmpty()) {
    			if (excludes.isEmpty())
    				excludes = new ArrayList<>();
    		    excludes.addAll(sourcePath.getExcludes());
    		}
    		if (!sourcePath.getIncludes().isEmpty()) {
    			if (includes.isEmpty())
    				includes = new ArrayList<>();
    		    includes.addAll(sourcePath.getIncludes());
    		}
     	}
		return new SourceFilterPatterns(includes, excludes);
    }

    /**
     * Returns the resolved directories.
     * @return a non null set of File objects.
     */
    @NonNull
    public Set<File> getSrcDirs() {
    	Builder<File> builder = ImmutableSet.builder();
    	for (SourcePath sourcePath: sourcePaths) {
    		builder.add(new File(androidSourceSet.getRootPath(), sourcePath.getPath()));
    	}
    	return builder.build();
    }
}
