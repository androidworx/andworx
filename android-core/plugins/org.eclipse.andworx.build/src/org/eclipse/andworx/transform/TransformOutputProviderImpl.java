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

import static com.android.SdkConstants.DOT_JAR;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.android.annotations.NonNull;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.QualifiedContent.ContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.utils.FileUtils;

/**
 * Implementation of {@link TransformOutputProvider} passed to the transforms.
 * Supports only single content location
 */
public class TransformOutputProviderImpl implements TransformOutputProvider {

    @NonNull private final File rootFolder;
   // private final Set<ContentType> types;
   // private final Set<? super Scope> scopes;
    private AtomicInteger nextIndex;

    /**
     * Construct TransformOutputProviderImpl object
     * @param rootFolder Content location
     * @param types ContentType set
     * @param scopes Scopes
     */
    public TransformOutputProviderImpl(            
    		@NonNull File rootFolder,
            @NonNull Set<ContentType> types,
            @NonNull Set<? super Scope> scopes) {
        this.rootFolder = rootFolder;
        //this.types = types;
        //this.scopes = scopes;
        nextIndex = new AtomicInteger();
;
    }

    @Override
    public void deleteAll() throws IOException {
        FileUtils.cleanOutputDir(rootFolder);
    }

 	@Override
	public File getContentLocation(String name, Set<ContentType> types, Set<? super Scope> scopes, Format format) {
        return new File(rootFolder, computeFilename(format));
	}

    private String computeFilename(Format format) {
    	int index = nextIndex.getAndIncrement();
        if (format == Format.DIRECTORY) 
            return Integer.toString(index);
        return Integer.toString(index) + DOT_JAR;
    }
}
