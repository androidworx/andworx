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
package org.eclipse.andmore.android.wizards.project;

import org.eclipse.andmore.AndmoreAndroidPlugin;
import org.eclipse.andmore.android.AndroidPlugin;
import org.eclipse.andmore.android.Sample;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE.SharedImages;
import org.eclipse.andmore.base.resources.JFaceImageLoader;
import org.eclipse.andmore.base.resources.PluginResourceProvider;

import com.android.sdklib.IAndroidTarget;

/**
 * Label Provider for the Tree of Project Templates
 */
public class TreeLabelProvider extends LabelProvider {
	private static final String MOTOROLA_BRAND = "motorola";

	private static final Image category = new Image(null, PlatformUI.getWorkbench().getSharedImages()
			.getImage(SharedImages.IMG_OBJ_PROJECT).getImageData());

	private static final Image studio;

	private static final Image android = AndmoreAndroidPlugin.getAndroidLogo();

	static {
		JFaceImageLoader imageLoader = new JFaceImageLoader(new PluginResourceProvider() {

			@Override
			public ImageDescriptor descriptorFromPath(String imagePath) {
				return AndroidPlugin.getImageDescriptor(imagePath);
			}});
		studio = imageLoader.getImage(AndroidPlugin.ANDROID_MOTOROLA_BRAND_ICON_PATH);
	}
	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.jface.viewers.LabelProvider#getImage(java.lang.Object)
	 */
	@Override
	public Image getImage(Object obj) {

		Image image = null;
		if (obj instanceof Sample) {
			String targetName = ((Sample) obj).getTarget().getVendor();
			if (targetName.toLowerCase().contains(MOTOROLA_BRAND)) {
				image = studio;
			} else {
				image = android;
			}
		} else if (obj instanceof IAndroidTarget) {
			image = category;
		}
		return image;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.jface.viewers.LabelProvider#getText(java.lang.Object)
	 */
	@Override
	public String getText(Object obj) {
		String text;
		if (obj instanceof Sample) {
			text = ((Sample) obj).getName();
		} else if (obj instanceof IAndroidTarget) {
			text = ((IAndroidTarget) obj).getName();
		} else {
			text = ""; //$NON-NLS-1$
		}
		return text;
	}
}
