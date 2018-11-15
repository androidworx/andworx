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
package com.android.ddmuilib;

import org.eclipse.andmore.base.resources.ImageFactory.ReplacementImager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.widgets.Display;

public class ReplacementImageFactory implements ReplacementImager {

	private Display display;
	private int width;
	private int height;
	private Color color;
	
	/**
     * @param display Display object
     * @param width Width to create replacement Image
     * @param height Height to create replacement Image
     * @param color Optional color to create replacement Image. If null, Blue
     *            color will be used.
	 */
	public ReplacementImageFactory(Display display, int width, int height, Color color)
	{
		this.display = display;
		this.width = width;
		this.height = height;
		this.color = color != null ? color : display.getSystemColor(SWT.COLOR_BLUE);
	}
	
	@Override
	public ImageData create() {
		Image img = getImage();
        ImageData imageData = img.getImageData();
        img.dispose();
		return imageData;
	}

	public Image getImage() {
        Image img = new Image(display, width, height);
        GC gc = new GC(img);
        gc.setForeground(color);
        gc.drawLine(0, 0, width, height);
        gc.drawLine(0, height - 1, width, -1);
        gc.dispose();
		return img;
	}

}
