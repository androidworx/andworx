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
package org.eclipse.andworx.transform;

import java.io.File;
import java.util.Map;
import java.util.Set;

import com.android.annotations.NonNull;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Status;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/**
 * DirectoryInput implementation which does not support incremental builds
 *  - describes one Java class directory in a transform pipeline

 * DirectoryInfo is a {@link QualifiedContent} of type "directory".
 * <p>
 * This means the {@link #getFile()} is the root directory containing the content.
 */
public class DirectoryInfo implements DirectoryInput {

    @NonNull
    private final Map<File, Status> changedFiles;
    /** Name of the content */
    @NonNull
    private final String name;
    /** Location of the directory */
    @NonNull
    private final File file;
    /** Type of content that the pipeline stream represents - CLASSES or RESOURCES */
    @NonNull
    private final Set<ContentType> contentTypes;
    /** Scope of the content */
    @NonNull
    private final Set<? super Scope> scopes;

    /**
     * Construct DirectoryInfo object
     * @param name Name of the content
     * @param file Location of the directory
     * @param contentTypes Type of content that the pipeline stream represents - CLASSES or RESOURCES
     * @param scopes Scope of the content
     */
    public DirectoryInfo(
            @NonNull String name,
            @NonNull File file,
            @NonNull Set<ContentType> contentTypes,
            @NonNull Set<? super Scope> scopes) {
        this.name = name;
        this.file = file;
        this.contentTypes = ImmutableSet.copyOf(contentTypes);
        this.scopes = ImmutableSet.copyOf(scopes);
        this.changedFiles = ImmutableMap.of();
	}

    /**
     * Returns the type of content that the pipeline stream represents.
     * <p>
     * Even though this may return only {@link DefaultContentType#RESOURCES} or
     * {@link DefaultContentType#CLASSES}, the actual content may
     * contain files representing other content types.
     * <p>
     * For each input, transforms should always take care to read and process only the files
     * associated with the types returned by this method.
     *
     * @return a set of one or more types, never null nor empty.
     */
	@Override
	public Set<ContentType> getContentTypes() {
		return contentTypes;
	}

    /**
     * Returns the location of the directory
     *
     * @return File object
     */
	@Override
	public File getFile() {
		return file;
	}

    /**
     * Returns the name of the content. Can be used to differentiate different content using
     * the same scope.
     *
     * This is not reliably usable at every stage of the transformations, but can be used for
     * logging for instance.
     *
     * @return the name
     */
	@Override
	public String getName() {
		return name;
	}

    /**
     * Returns the scope of the content.
     *
     * @return a set of one or more scopes, never null nor empty.
     */
	@Override
	public Set<? super Scope> getScopes() {
		return scopes;
	}

    /**
     * Returns the changed files. This is only valid if the transform is in incremental mode.
     * @return Map
     */
	@Override
	public Map<File, Status> getChangedFiles() {
		return changedFiles;
	}

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("name", getName())
                .add("file", getFile())
                .add("contentTypes", Joiner.on(',').join(getContentTypes()))
                .add("scopes", Joiner.on(',').join(getScopes()))
                //.add("changedFiles", changedFiles)
                .toString();
    }
}
