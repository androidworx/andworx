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
package org.eclipse.andworx.transform;

import com.android.annotations.NonNull;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.api.transform.QualifiedContent.ContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.utils.FileUtils;
import com.android.utils.StringHelper;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import com.google.common.hash.Hashing;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;

import org.eclipse.andworx.context.VariantContext;

/**
 * Functions necessary to perform pipeline transforms
 */
public class TransformAgent {
    private static final String FD_TRANSFORMS = "transforms";

    /**
     * Construct TransformAgent object
     */
	public TransformAgent() {
		super();
	}

	/**
	 * Returns jar and directory information describing the transform inputs
	 * @param files Individual jars or directories containg Java compile output
	 * @param contentTypes Content types
	 * @param scopes Scopes
	 * @return TransformInput object
	 */
	public TransformInput createTransformInput(
			Set<File> files, 
			@NonNull Set<ContentType> contentTypes,
            @NonNull Set<? super Scope> scopes) {
        List<JarInput> jarInputs;
        List<DirectoryInput> directoryInputs;
        jarInputs =
            files.stream()
                .filter(File::isFile)
                .map(
                    file ->
                        new JarInfo(
                                getUniqueInputName(file),
                                file,
                                Status.NOTCHANGED,
                                contentTypes,
                                scopes))
                .collect(Collectors.toList());

        directoryInputs =
            files.stream()
                .filter(File::isDirectory)
                .map(
                    file ->
                        new DirectoryInfo(
                                getUniqueInputName(file),
                                file,
                                contentTypes,
                                scopes))
                .collect(Collectors.toList());
        return new TransformInfo(jarInputs, directoryInputs);
	}

	/**
	 * Returms the aggregation of all inputs required for a transformation
	 * @param inputs Transform input collection
	 * @param outputDir Target directory
	 * @param transform Transform attributes
	 * @return Transvocation object (abbreviation for "TransformInvocation")
	 */
	public Transvocation createTransformInvocation(Collection<TransformInput> inputs, File outputDir, Transform transform) {
	    Collection<TransformInput> referencedInputs = ImmutableList.of();
	    TransformOutputProvider transformOutputProvider = new TransformOutputProviderImpl(outputDir, transform.getInputTypes(), transform.getReferencedScopes());
		return new Transvocation(inputs, referencedInputs, transformOutputProvider);
	}

	/**
	 * Returns Root location for transform in given context
	 * @param taskName Task name assigned to transform
	 * @param variantContext Variant confext
	 * @return File object defining an absolute operating system directory location
	 */
	public File getOutputRootDir(String taskName, VariantContext variantContext) {
        return FileUtils.join(
        		variantContext.getIntermediatesDir(), 
        		StringHelper.toStrings(
                FD_TRANSFORMS,
                taskName,
                variantContext.getVariantConfiguration().getDirectorySegments()));
	}

	/**
	 * Returns a generated unique name for a given file
	 * @param file File
	 * @return String
	 */
    @SuppressWarnings("deprecation")
	@NonNull
    public String getUniqueInputName(@NonNull File file) {
        return Hashing.sha1().hashString(file.getPath(), Charsets.UTF_16LE).toString();
    }

}
