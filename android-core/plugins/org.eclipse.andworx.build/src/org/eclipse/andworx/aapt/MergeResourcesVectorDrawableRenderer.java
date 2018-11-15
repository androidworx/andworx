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
package org.eclipse.andworx.aapt;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.function.Supplier;

import org.eclipse.andworx.exception.AndworxException;

import com.android.annotations.NonNull;
import com.android.builder.png.VectorDrawableRenderer;
import com.android.ide.common.vectordrawable.ResourcesNotSupportedException;
import com.android.resources.Density;
import com.android.utils.ILogger;

/**
 * Generates PNG images (and XML copies) from VectorDrawable files.
 * Also generation gradle-specific error message for ResourcesNotSupportedException.
 */
public class MergeResourcesVectorDrawableRenderer extends VectorDrawableRenderer{

	private static final long serialVersionUID = 1L;

	/**
	 * Construct MergeResourcesVectorDrawableRenderer object
	 * @param minSdk
	 * @param supportLibraryIsUsed
	 * @param outputDir
	 * @param densities
	 * @param logger
	 */
	public MergeResourcesVectorDrawableRenderer(
            int minSdk,
            boolean supportLibraryIsUsed,
            File outputDir,
            Collection<Density> densities,
            ILogger logger) {
        super(minSdk, supportLibraryIsUsed, outputDir, densities, new Supplier<ILogger>() {

			@Override
			public ILogger get() {
				return logger;
			}});
    }

    @Override
    public void generateFile(@NonNull File toBeGenerated, @NonNull File original)
            throws IOException {
        try {
            super.generateFile(toBeGenerated, original);
        } catch (ResourcesNotSupportedException e) {
            // Add gradle-specific error message.
            throw new AndworxException(
                    String.format(
                            "Can't process attribute %1$s=\"%2$s\": "
                                    + "references to other resources are not supported by "
                                    + "build-time PNG generation. "
                                    + "See http://developer.android.com/tools/help/vector-asset-studio.html "
                                    + "for details.",
                            e.getName(), e.getValue()));
        }
    }
}
