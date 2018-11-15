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
package org.eclipse.andworx.api.attributes;

import static com.android.SdkConstants.FN_SHARED_LIBRARY_ANDROID_MANIFEST_XML;
import static com.android.SdkConstants.FN_CLASSES_JAR;
import static com.android.SdkConstants.LIBS_FOLDER;
import static com.android.SdkConstants.FD_JARS;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import com.android.utils.FileUtils;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.google.common.collect.Lists;

/**
 * Files of a specific archive type gathered from an artifact collection
 */
abstract public class AndroidArchiveSet {
	
	protected final List<File> files;
	protected boolean sharedLibSupport;
	
	public AndroidArchiveSet() {
		files = new ArrayList<>();
	}

	public void setSharedLibSupport(boolean sharedLibSupport) {
		this.sharedLibSupport = sharedLibSupport;
	}

	public List<File> getFiles() {
		return files;
	}

	public abstract void add(@NonNull File explodedAar);
	
	protected boolean isShared(@NonNull File explodedAar) {
        return sharedLibSupport
                && new File(explodedAar, FN_SHARED_LIBRARY_ANDROID_MANIFEST_XML).exists();
    }

    @NonNull
    protected void listIfExists(@NonNull File file) {
        if (file.exists())
        	files.add(file);
    }

    @NonNull
    protected void listIfExists(@NonNull Stream<File> fileStrean) {
        files.addAll(fileStrean.filter(File::exists).collect(Collectors.toList()));
    }

    protected List<File> getJars(@NonNull File explodedAar) {
        List<File> files = Lists.newArrayList();
        File jarFolder = new File(explodedAar, FD_JARS);

        File file = FileUtils.join(jarFolder, FN_CLASSES_JAR);
        if (file.isFile()) {
            files.add(file);
        }

        // local jars
        final File localJarFolder = new File(jarFolder, LIBS_FOLDER);
        File[] jars = localJarFolder.listFiles((dir, name) -> name.endsWith(SdkConstants.DOT_JAR));

        if (jars != null) {
            files.addAll((Arrays.asList(jars)));
        }
        return files;
    }
}
