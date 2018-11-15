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

import java.util.Collections;
import java.util.List;

/**
 * Tree node which provides the text, font and image for the label of a given tree element
 * and supports navigation of the view tree. All methods are implemented to provide defaults,
 * so sub classes need only override when the default is not correct.
 * @author Andrew Bowley
 *
 */
public abstract class INode {
    /** Font can be normal or italic */
	static enum LabelFont { normal, italic }
	
	static final List<INode> EMPTY_NODE_LIST;
	static final String VOID = "";
	
	static
	{
		EMPTY_NODE_LIST = Collections.emptyList();
	}
 
	/** Flag to mirror node checkbox state */
	protected boolean isChecked;
	/** Parent node. This will be null for the root node. */
	protected INode parentNode;

	/**
	 * Default constructor for first-level node without parent
	 */
	public INode() {
	}

	/**
	 * Construct INode object to sit below root node
	 * @param parentNode
	 */
	public INode(INode parentNode) {
		this.parentNode = parentNode;
	}
	
	/** 
	 * Returns parent of this node
	 * @return INode object or null
	 */
	public INode getParent() {
		return parentNode;
	}
	
	/**
	 * Returns the image resource value for the label of the given element.  
	 * @param element Target
	 * @param columnIndex The index of the column being displayed
	 * @return the resource value of image used to label the element, or VOID if there is no image for the given object
	 */
	public String getImage(Object element, int columnIndex) {
		return VOID;
	}

	/**
	 * Returns the text for the label of the given element.
	 * @param element Target
	 * @param columnIndex The index of the column being displayed
	 * @return the text string used to label the element, or VOID if there is no text label for the given object
	 */
	public String getText(Object element, int columnIndex) {
		return VOID;
	}

	/**
	 * Provides a font for the given element at index columnIndex.
	 * @param element Target
	 * @param columnIndex The index of the column being displayed
	 * @return LabelFont.normal or LabelFont.italic
	 */
	public LabelFont getFont(Object element, int columnIndex) {
		return LabelFont.normal;
	}

	/**
	 * Get the text displayed in the tool tip for given element 
	 * @param element Target
	 * @return the tooltop text, or VOID for no text to display
	 */
	public String getToolTipText(Object element) {
        return VOID;
	}

	/**
	 * Returns list of descendents
	 * @return INode list
	 */
	public List<? extends INode> getChildren() {
		return EMPTY_NODE_LIST;
	}

	/**
	 * Returns checkbox state 
	 * @return boolean
	 */
	public boolean isChecked() {
		return isChecked;
	}

	/**
	 * Returns flag set true if this node has children
	 * @return
	 */
	public boolean hasChildren() {
		return false;
	}
	
	/**
	 * Sets checkbox state
	 * @param isChecked Value to set checkbox on next refresh
	 */
	public void setChecked(boolean isChecked) {
		this.isChecked = isChecked;
	}

    /**
     * Mark item as checked according to given criteria. Force uncheck if no criteria specified.
     * @param selectUpdates If true, select all update packages
     * @param topApiLevel If greater than 0, select platform packages of this api level
     * @return flag set true if item is checked
      */
	public boolean checkSelections(
            boolean selectUpdates,
            int topApiLevel)
	{
		return false;
	}

	/**
	 * Mark item as deleted. This is a transient state on the path to removal from the collection to which it belongs
	 */
	public void markDeleted()
	{
	}

	/**
	 * Returns true if item has been marked for deletion
	 * @return boolean
	 */
	public boolean isDeleted()
	{
		return false; 
	}
}
