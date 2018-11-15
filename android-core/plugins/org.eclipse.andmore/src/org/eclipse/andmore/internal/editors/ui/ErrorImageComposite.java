/*
 * Copyright (C) 2010 The Android Open Source Project
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

package org.eclipse.andmore.internal.editors.ui;

import static org.eclipse.ui.ISharedImages.IMG_DEC_FIELD_ERROR;
import static org.eclipse.ui.ISharedImages.IMG_DEC_FIELD_WARNING;
import static org.eclipse.ui.ISharedImages.IMG_OBJS_ERROR_TSK;
import static org.eclipse.ui.ISharedImages.IMG_OBJS_WARN_TSK;

import org.eclipse.jface.resource.CompositeImageDescriptor;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.DecorationOverlayIcon;
import org.eclipse.swt.graphics.Device;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.internal.DPIUtil;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.graphics.ImageDataProvider;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;

/**
 * ImageDescriptor that adds a error marker.
 * Based on {@link DecorationOverlayIcon} only available in Eclipse 3.3
 */
public class ErrorImageComposite extends CompositeImageDescriptor {
	public static final class AutoScaleImageDataProvider implements ImageDataProvider {
		Device device;
		ImageData imageData;
		int currentZoom;
		public AutoScaleImageDataProvider(Device device, ImageData data, int zoom){
			this.device = device;
			this.imageData = data;
			this.currentZoom = zoom;
		}
		@Override
		public ImageData getImageData(int zoom) {
			return DPIUtil.autoScaleImageData(device, imageData, zoom, currentZoom);
		}
	}

    private Image mBaseImage;
    private ImageDescriptor mErrorImageDescriptor;
    private Point mSize;

    /**
     * Creates a new {@link ErrorImageComposite}
     *
     * @param baseImage the base image to overlay an icon on top of
     */
    public ErrorImageComposite(Image baseImage) {
        this(baseImage, false);
    }

    /**
     * Creates a new {@link ErrorImageComposite}
     *
     * @param baseImage the base image to overlay an icon on top of
     * @param warning if true, add a warning icon, otherwise an error icon
     */
    public ErrorImageComposite(Image baseImage, boolean warning) {
        mBaseImage = baseImage;
        ISharedImages sharedImages = PlatformUI.getWorkbench().getSharedImages();
        mErrorImageDescriptor = sharedImages.getImageDescriptor(
                warning ? IMG_DEC_FIELD_WARNING : IMG_DEC_FIELD_ERROR);
        if (mErrorImageDescriptor == null) {
            mErrorImageDescriptor = sharedImages.getImageDescriptor(
                    warning ? IMG_OBJS_WARN_TSK : IMG_OBJS_ERROR_TSK);
        }
        mSize = new Point(baseImage.getBounds().width, baseImage.getBounds().height);
    }

    @Override
    protected void drawCompositeImage(int width, int height) {
        ImageData baseData = mBaseImage.getImageData();
        ImageDataProvider imageDataProvider = new AutoScaleImageDataProvider(Display.getDefault(), baseData, 100);
        drawImage(imageDataProvider, 0, 0);

        ImageData overlayData = mErrorImageDescriptor.getImageData(100);
        if (overlayData.width == baseData.width && overlayData.height == baseData.height) {
            overlayData = overlayData.scaledTo(14, 14);
            imageDataProvider = new AutoScaleImageDataProvider(Display.getDefault(), overlayData, 100);
            drawImage(imageDataProvider, -3, mSize.y - overlayData.height + 3);
        } else {
            drawImage(imageDataProvider, 0, mSize.y - overlayData.height);
        }
    }

    @Override
    protected Point getSize() {
        return mSize;
    }
}
