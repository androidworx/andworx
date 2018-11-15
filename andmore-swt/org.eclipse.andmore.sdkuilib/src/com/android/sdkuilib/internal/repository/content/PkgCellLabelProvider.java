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
package com.android.sdkuilib.internal.repository.content;

import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ITableFontProvider;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;

import com.android.sdkuilib.internal.repository.content.INode.LabelFont;
import org.eclipse.andmore.base.resources.ImageFactory;

/**
 * Label provider for PackagesPage CheckboxTreeViewer object
 * @author Andrew Bowley
 *
 * 18-12-2017
 */
public class PkgCellLabelProvider extends ColumnLabelProvider implements ITableFontProvider {

	/** Index values are defined in { @link PkgCellAgent } */
    private final int columnIndex;
    /** Intermediary between PkgCellLabelProvider and the application */
    private final PkgCellAgent agent;
 
    /**
     * Construct PkgCellLabelProvider object 
     * @param agent Intermediary between PkgCellLabelProvider and the application
     * @param columnIndex Column index 
     */
    public PkgCellLabelProvider(PkgCellAgent agent, int columnIndex) {
        super();
        this.columnIndex = columnIndex;
        this.agent = agent;
    }

    /**
     * Returns the text for the label of the given element.
     *
     * @param element the element for which to provide the label text
     * @return the text string used to label the element, or <code>null</code>
     *   if there is no text label for the given object
     */
    @Override
    public String getText(Object element) {
    	INode node = (INode)element;
    	String text = node.getText(element, columnIndex);
    	return text != INode.VOID ? text : null;
    }
    
    /**
     * The image is owned by the label provider and must not be disposed directly.
     */
    @Override
    public Image getImage(Object element) {
        ImageFactory imgFactory = agent.getImgFactory();
        if (imgFactory != null) {
        	INode node = (INode)element;
        	String reference = node.getImage(element, columnIndex);
        	if (reference != INode.VOID)
        		return imgFactory.getImageByName(reference);
        }
        return super.getImage(element);
    }

    // -- ITableFontProvider

    /**
     * Provides a font for the given element.
     *
     * @param element the element
     * @return the font for the element, or <code>null</code>
     *   to use the default font
     */
    @Override
    public Font getFont(Object element, int columnIndex) {
    	INode node = (INode)element;
    	LabelFont fontType = node.getFont(element, columnIndex);
    	if (fontType == LabelFont.italic)
            return agent.getTreeFontItalic();
        return super.getFont(element);
    }

    // -- Tooltip support 
    @Override
    public String getToolTipText(Object element) {
    	INode node = (INode)element;
    	String text = node.getToolTipText(element);
    	if (text != INode.VOID)
    		return text;
        return super.getToolTipText(element);
    }

    @Override
    public Point getToolTipShift(Object object) {
        return new Point(15, 5);
    }

    @Override
    public int getToolTipDisplayDelayTime(Object object) {
        return 500;
    }
}
