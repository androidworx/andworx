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
package org.eclipse.andworx.build;

import java.io.File;
import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.FilterData;
import com.android.build.OutputFile;
import com.android.ide.common.build.ApkData;
import com.android.ide.common.build.ApkInfo;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Data for a build output item like a merged manifest file, a jar file or an APK file.
 *
 * <p>Each output is characterized by its type, the intent APK in which this build output will
 * eventually be packaged in and its file location.
 * Note there are 2 OutputType classes: org.eclipse.andworx.build.OutputType outputType and 
 * com.android.build.VariantOutput.OutpotType.
 */
public class BuildElement implements OutputFile, Serializable {

	private static final long serialVersionUID = 1L;

	/** OutputType enum */
	@NonNull 
	private org.eclipse.andworx.build.OutputType outputType;
	/** APK information */
    @NonNull 
    private final ApkInfo apkInfo;
    
    // the right abstraction would be Path but it's not serializable so reconstruct the Path
    // instance from its String representation.
    /** Output file location */
    @NonNull 
    private final String path;
    /** Optional properties */
    @NonNull 
    private final Map<String, String> properties;

    /**
     * Construct BuildElement object
     * @param outputType Build output type
     * @param apkInfo APK information
     * @param outputFile Location of output file on file system
     */
    public BuildElement(
            @NonNull org.eclipse.andworx.build.OutputType outputType,
            @NonNull ApkInfo apkInfo,
            @NonNull File outputFile) {
        this(outputType, apkInfo, outputFile, ImmutableMap.of());
    }

    /**
     * Construct BuildElement object
     * @param outputType Build output type
     * @param apkInfo APK information
     * @param outputFile Location of output file on file system
     * @param properties Additional properties
     */
    public BuildElement(
            @NonNull org.eclipse.andworx.build.OutputType outputType,
            @NonNull ApkInfo apkInfo,
            @NonNull File outputFile,
            @NonNull Map<String, String> properties) {
        this(outputType, apkInfo, outputFile.toPath(), properties);
    }

    /**
     * Construct BuildElement object
     * @param outputType Build output type
     * @param apkInfo APK information
     * @param path Path of output file on file system
     * @param properties Additional properties
     */
    public BuildElement(
            @NonNull org.eclipse.andworx.build.OutputType outputType,
            @NonNull ApkInfo apkInfo,
            @NonNull Path outputPath,
            @NonNull Map<String, String> properties) {
        this.outputType = outputType;
        this.apkInfo = apkInfo;
        this.path = outputPath.toString();
        this.properties = properties;
    }

    /**
     * Returns the build output type (like an Android manifest file, a merged resource file, etc..)
     * @return OutputType enum
     */
    @NonNull 
    public org.eclipse.andworx.build.OutputType getType() {
		return outputType;
	}
	
    /**
     * Returns the final APK type this build output will be packaged in.
     * @return the apk in which this build output will be packaged in.
     */
    @NonNull 
	@Override
	public String getOutputType() {
		return apkInfo.getType().toString();
	}

    /**
     * Returns information about the APK that will package this build output. If the {@link
     * #getType()} is {@link OutputType#APK}, this build output is an APK and
     * this will provide metadata information about that APK.
     *
     * @return APK information about the APK in which this build output will be packaged into.
     */
    @NonNull
    public ApkInfo getApkInfo() {
        return apkInfo;
    }

    /**
     * Returns location of output file
     * @return File object
     */
    @NonNull
    @Override
    public File getOutputFile() {
        return Paths.get(path).toFile();
    }

    /**
     * Returns location of output file
     * @return Path object
     */
    public Path getOutputPath() {
        return Paths.get(path);
    }

    /**
     * Returns the list of filter types {@link OutputFile.FilterType} as Strings.
     *
     * @return the filter types as {@link String}
     */
    @NonNull
    @Override
    public Collection<String> getFilterTypes() {
        return apkInfo.getFilters()
                .stream()
                .map(FilterData::getFilterType)
                .collect(Collectors.toList());
    }

    /**
     * Returns the list of filters
     *
     * @return collection of FilterData objects
     */
    @NonNull
    @Override
    public Collection<FilterData> getFilters() {
        return apkInfo.getFilters();
    }

    /**
     * Returns filter for given filterType
     * @param filterType
     * @return filter
     */
    @Nullable
    public String getFilter(String filterType) {
        return ApkData.getFilter(apkInfo.getFilters(), FilterType.valueOf(filterType));
    }

    /**
     * There is no notion of a main output file.
     *
     * @return itself.
     */
    @NonNull
    @Override
    public OutputFile getMainOutputFile() {
        return this;
    }

    /**
     * Implemented for compatibility only. Returns the list of outputs for this VariantOutput, which
     * is itself.
     *
     * @return the list of output for this VariantOutput.
     */
    @NonNull
    @Override
    public Collection<? extends OutputFile> getOutputs() {
        return ImmutableList.of(this);
    }

    /**
     * Returns the version code, -1 if not set.
     *
     * @return the version code.
     */
    @Override
    public int getVersionCode() {
        return apkInfo.getVersionCode();
    }

    /**
     * Returns all the dynamic properties that can be attached to this build output. A dynamic
     * property is a free formed key value pair that are attributes of this build output.
     *
     * @return a map of key value pairs.
     */
    @NonNull
    public Map<String, String> getProperties() {
        return properties;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("apkInfo", apkInfo)
                .add("path", path)
                .add("properties", Joiner.on(",").join(properties.entrySet()))
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BuildElement that = (BuildElement) o;
        return outputType == that.outputType
                && Objects.equals(properties, that.properties)
                && Objects.equals(apkInfo, that.apkInfo)
                && Objects.equals(path, that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(outputType, apkInfo, path, properties);
    }

}
