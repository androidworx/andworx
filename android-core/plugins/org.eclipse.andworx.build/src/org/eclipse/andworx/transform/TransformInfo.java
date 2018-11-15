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
import com.android.build.api.transform.TransformInput;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import java.util.Collection;


/**
 * Immutable version of {@link TransformInput}.
 */
public class TransformInfo implements TransformInput {

    private final Collection<JarInput> jarInputs;
    private final Collection<DirectoryInput> directoryInputs;

    /**
     * Construct TransformInfo object
     * @param jarInputs JarInput collection
     * @param directoryInputs DirectoryInput collection
     */
    public TransformInfo(
            @NonNull Collection<JarInput> jarInputs,
            @NonNull Collection<DirectoryInput> directoryInputs) {
        this.jarInputs = ImmutableList.copyOf(jarInputs);
        this.directoryInputs = ImmutableList.copyOf(directoryInputs);
    }

    @NonNull
    @Override
    public Collection<JarInput> getJarInputs() {
        return jarInputs;
    }

    @NonNull
    @Override
    public Collection<DirectoryInput> getDirectoryInputs() {
        return directoryInputs;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("jarInputs", jarInputs)
                .add("folderInputs", directoryInputs)
                .toString();
    }

}
