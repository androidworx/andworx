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
package org.eclipse.andworx.control;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.graphics.FontMetrics;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;

/**
 * StatusItemLayoutData
 * Customises Status Line layout data to adjust height and width hints according to font
 * @author Andrew Bowley
 * 25 May 2016
 */
public class StatusItemLayoutData
{
    /**
     * The <code>widthHint</code> specifies a minimum width for
     * the <code>Control</code>. 
     */
    public int widthHint;

    /**
     * The <code>heightHint</code> specifies a minimum height for
     * the <code>Control</code>.
     */
    public int heightHint;

    /**
     * Create default StatusItemLayoutData object
     */
    public StatusItemLayoutData()
    {
        super();
        widthHint = SWT.DEFAULT;
        heightHint = SWT.DEFAULT;
    }
    
    /**
     * Create StatusItemLayoutData object
     * @param label Custom Label
     * @param specification Custom Label specification
     */
    public StatusItemLayoutData(CLabel label, int width)
    {
        this();
        Composite parent = label.getParent();
        GC gc = new GC(parent);
        try
        {
            gc.setFont(parent.getFont());
            init(label, width, gc);
        }
        finally
        {
            gc.dispose();
        }
    }

    /**
     * Create StatusItemLayoutData object
     * @param label Custom Label
     * @param specification Custom Label specification
     * @param gc GC object with font set same as label
     */
    public void init(CLabel label,  int width, GC gc)
    {
        FontMetrics fontMetrics = gc.getFontMetrics();
        String text = label.getText();
        if (width > 0) 
            widthHint = width * (int)fontMetrics.getAverageCharacterWidth();
        else if (width == 0)
        {
            widthHint = label.getLeftMargin() + label.getRightMargin(); 
            if ((text != null) && !text.isEmpty())
                widthHint += gc.textExtent(text).x;
        }
        Image image = label.getImage();
        if ((image != null) && (widthHint >= 0))
            widthHint += image.getBounds().width + 5;
        heightHint = fontMetrics.getHeight();
        int pos = -1;
        int count = 0;
        String trunc = text;
        while ((pos = trunc.indexOf('\n')) != -1 ) {
        	++count;
        	trunc = trunc.substring(pos + 1);
        }
        if (count > 0)
        	heightHint *= count + 1;
    }
}
