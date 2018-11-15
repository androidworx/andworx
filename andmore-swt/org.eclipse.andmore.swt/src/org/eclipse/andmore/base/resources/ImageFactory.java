/*
 * Copyright (C) 2017 The Android Open Source Project
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
package org.eclipse.andmore.base.resources;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

public interface ImageFactory {
    /**
     * Produces an edited version of given image
     * {@link ImageFactory#getImageByName(String, String, ImageEditor)}.
     */
    public interface ImageEditor {
        /**
         * The editor implementation needs to create an image data object based on a given image, or <br/>
         * if no modification is necessary, return null.  <br/>
         * <p/>
         *
         * @param source A non-null source image.
         * @return {@link ImageData} object, which can be null if no change required
         */
        @NonNull public ImageData edit(@NonNull Image source);
    }

    /**
     * Produces an replacement version of given image
     * {@link ImageFactory#getImageByName(String, String, Filter)}.
     */
    public interface ReplacementImager {
        /**
         * The editor implementation needs to create an image data object based on a given image, or <br/>
         * if no modification is necessary, return null.  <br/>
         * <p/>
         *
         * @param source A non-null source image.
         * @return {@link ImageData} object, which can be null if no change required
         */
        @NonNull public ImageData create();
    }

	/**
	 * Loads an image given its filename (with its extension).
	 * Might return null if the image cannot be loaded.  <br/>
	 * The image is cached. Successive calls will return the <em>same</em> object. <br/>
	  *
	 * @param imageName The filename (with extension) of the image to load.
	 * @return {@link Image} object or null if the image file is not found. The caller must NOT dispose the image.
	 */
	@Nullable
	Image getImageByName(String imageName);

	/**
	 * Returns an image descriptor given its filename (with its extension).
	 * Might return null if the image descriptor cannot be loaded.  <br/>
	 * @param imageName The filename (with extension) of the image to load.
	 * @return {@link ImageDescriptor} object or null if the image file is not found.
	 */
	@Nullable
	ImageDescriptor getDescriptorByName(String imageName);

	/**
	 * Loads an image given its filename (with its extension), caches it using the given
	 * {@code KeyName} name and applies a filter to it.
	 * Might return null if the image cannot be loaded.
	 * The image is cached. Successive calls using {@code KeyName} will return the <em>same</em>
	 * object directly (the filter is not re-applied in this case.) <br/>
	 * <p/>
	 * @param imageName Filename (with extension) of the image to load.
	 * @param keyName Image key reference
	 * @param imageEditor Image editor
	 * @return {@link Image} or null if the image file is not found. The caller must NOT dispose the image.
	 */
	@Nullable
	Image getImageByName(String imageName,String keyName, ImageEditor imageEditor);

	/**
	 * Loads an image given its filename (with its extension) and if not found,
	 * uses supplied {@code ReplacementImager} to create a replacement.
	 * <p/>
	 * @param imageName Filename (with extension) of the image to load.
	 * @param replacementImager Provides replacement image
	 * @return {@link Image}. The caller must NOT dispose the image.
	 */
	@Nullable
	Image getImageByName(String imageName, ReplacementImager replacementImager);

	/**
	 * Returns image for given image file path
	 * @param imagePath A valid file system path relative to the bundle location eg. "icons/smile.gif"
	 * @return {@link Image} object or null if the image file is not found. The caller must NOT dispose the image
	 */
	Image getImage(String imagePath);

	/**
	 * Dispose all image resources
	 */
	void dispose();

}