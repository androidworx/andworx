/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not FilenameFilter filter this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
  http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.eclipse.andworx.helper;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.andworx.build.BuildElement;
import org.eclipse.andworx.build.OutputType;
import org.eclipse.andworx.gson.ApkInfoAdapter;
import org.eclipse.andworx.gson.OutputTypeTypeAdapter;

import com.android.ide.common.build.ApkInfo;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

/**
 * Factory to assemble  {@link BuildElement}s described by a save metadata file (.json).
 * Derived from gradle-core Kotlin ExistingBuildElements
 */
public class BuildElementFactory {

    /**
     * Returns a {@link BuildElement} collection created from a previous task execution metadata file collection.
     * @param elementType the expected element type of the BuildElements.
     * @param from the file collection containing the metadata file.
     */
    public Collection<BuildElement> from(OutputType elementType, Collection<File> from) {
        File metadataFile = getMetadataFileIfPresent(from);
        return doFrom(elementType, metadataFile);
    }
    
    /**
     * Returns a {@link BuildElement} created from a previous task execution metadata file.
     * @param elementType the expected element type of the BuildElements.
     * @param from the folder containing the metadata file.
     */
    public Collection<BuildElement> from(OutputType elementType, File from) {

        File metadataFile = getMetadataFileIfPresent(from);
        return doFrom(elementType, metadataFile);
    }

    /** 
     * Returns metadata file from specified location
     * @param folder Location
     * @return File object or null if file not found
     */
    public File getMetadataFileIfPresent(File folder) {
        File outputFile = getMetadataFile(folder);
        return outputFile.exists() ? outputFile : null;
    }

    /** 
     * Returns virtual metadata file for specified location
     * @param folder Location
     * @return File object 
     */
    public File getMetadataFile(File folder) {
        return new File(folder, "output.json");
    }

    /**
     * Serializes the specified build elements to a [String] using gson.
     * @param buildElements Collection of BuildElement objects
     * @param projectPath path to relativize output file paths against.
     * @return a json String.
     */
    public String persist(Collection<BuildElement> buildElements, Path projectPath)  {
    	GsonBuilder gsonBuilder = new GsonBuilder();
    	ApkInfoAdapter apkInfoAdapter = new ApkInfoAdapter();
        gsonBuilder.registerTypeHierarchyAdapter(ApkInfo.class, apkInfoAdapter);
        gsonBuilder.registerTypeAdapter(OutputType.class, new OutputTypeTypeAdapter());
        Gson gson = gsonBuilder.create();
        
        // Flatten and relativize the file paths to be persisted.
        List<BuildElement> relativized = new ArrayList<>();
        buildElements
                .forEach( buildElement -> 
                	relativized.add( new BuildElement(
                			buildElement.getType(),
                			buildElement.getApkInfo(),
                            projectPath.relativize(buildElement.getOutputPath()),
                            buildElement.getProperties())));
        return gson.toJson(relativized);
    }

    /**
     * Serializes  ApkInfo list
     * @param apkInfos List to persist
     * @return String
     */
    public String persistApkList(Collection<ApkInfo> apkInfos) {
    	GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeHierarchyAdapter(ApkInfo.class, new ApkInfoAdapter());
        Gson gson = gsonBuilder.create();
        return gson.toJson(apkInfos);
    }
 
    /**
     * Returns build element collection read from an IO reader
     * @param projectPath Absolute location to resolve project path
     * @param outputType Target output type
     * @param reader Open IO reader 
     * @return BuildElement collection
     */
    public Collection<BuildElement> load(
    		Path projectPath, 
    		OutputType outputType,
    		Reader reader) {
    	GsonBuilder gsonBuilder = new GsonBuilder();

        gsonBuilder.registerTypeAdapter(ApkInfo.class, new ApkInfoAdapter());
        gsonBuilder.registerTypeAdapter(OutputType.class, new OutputTypeTypeAdapter());
        Gson gson = gsonBuilder.create();
        Type recordType = new TypeToken<List<BuildElement>>() {}.getType();
        List<BuildElement> buildOutputs = gson.fromJson(reader, recordType);
        List<BuildElement> returnList = new ArrayList<>();
        // resolve the file path to the current project location.
        for (BuildElement buildElement: buildOutputs) {
        	if (buildElement == null)
        		continue; // This is possible if error occurred writing output.gson
        	if ((outputType == null) || (buildElement.getType() == outputType))
        		returnList.add(
                    new BuildElement(
                    		buildElement.getType(),
                    		buildElement.getApkInfo(),
                            projectPath.resolve(buildElement.getOutputPath()),
                            buildElement.getProperties()));
        }
        return returnList;
    }

    /**
     * Returns ApkInfo collection read from specified json file
     * @param file json file
     * @return ApkInfo collection
     * @throws FileNotFoundException
     */
    public Collection<ApkInfo> loadApkList(File file) throws FileNotFoundException {
    	GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeHierarchyAdapter(ApkInfo.class, new ApkInfoAdapter());
        gsonBuilder.registerTypeAdapter(OutputType.class, new OutputTypeTypeAdapter());
        Gson gson = gsonBuilder.create();
        TypeToken<List<ApkInfo>> recordType = new TypeToken<List<ApkInfo>>() {};
        return gson.fromJson(new FileReader(file), recordType.getType());
    }

    public BuildElement find(Collection<BuildElement> buildElements, ApkInfo apkInfo) {
    	for (BuildElement it: buildElements)
            if (it.getApkInfo().getType() == apkInfo.getType() &&
                it.getApkInfo().getFilters() == apkInfo.getFilters() &&
                it.getApkInfo().getFullName() == apkInfo.getFullName())
            	return it;
    	return null;
    }
    
    /**
     * Returns build elements filtered by output type from given metadata file 
     * @param elementType OutputType enum
     * @param metadataFile Gson file containing build elements
     * @return BuildElement collection
     */
    private Collection<BuildElement> doFrom(OutputType elementType, File metadataFile) {
        if (metadataFile == null || !metadataFile.exists()) {
            return ImmutableList.of();
        }
        
        try {
        	FileReader fileReader = new FileReader(metadataFile);
                return load(metadataFile.getParentFile().toPath(),
                        elementType,
                        fileReader);
        } catch (IOException e) {
            return ImmutableList.of();
        }
    }

    /**
     * Returns metadata file selected from given file collection
     * @param fileCollection File collection
     * @return File object
     */
    private File getMetadataFileIfPresent(Collection<File> fileCollection) {
    	if (fileCollection == null)
    		return null;
    	FileFilter filter = new FileFilter() {

			@Override
			public boolean accept(File pathname) {
				return pathname.getName().equals("output.json");
			}
        };
    	for (File file: fileCollection) {
    		File metadataFile = findFile(file, filter);
    		if (metadataFile != null)
    			return metadataFile;
    	}
        return null;
    }

    /**
     * Returns file found searching directory tree using given filter
     * @param pathname Absolute path to root of tree
     * @param filter File filter
     * @return File object or null if no file found matching the filter
     */
    private File findFile(File pathname, FileFilter filter) {
    	if (pathname.isFile()) {
    		if (filter.accept(pathname))
    			return pathname;
    	} else if (pathname.isDirectory()) {
    		File[] fileList = pathname.listFiles();
    		for (File file: fileList) {
        		File metadataFile = findFile(file, filter);
        		if (metadataFile != null)
        			return metadataFile;
    		}
    	}
		return null;
	}

}
