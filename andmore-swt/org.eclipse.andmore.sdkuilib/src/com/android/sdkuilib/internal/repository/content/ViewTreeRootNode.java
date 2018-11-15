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

import java.util.List;

/**
 * Root node of packages view tree. It's immediate children are categories.
 * @author Andrew Bowley
 *
 * 16-12-2017
 */
public class ViewTreeRootNode extends INode {

    /** Category container */
    private final CategoryContainer categoryContainer;

    /**
     * Construct ViewTreeRootNode object
     * @param categoryContainer the child nodes
     */
	public ViewTreeRootNode(CategoryContainer categoryContainer) {
		this.categoryContainer = categoryContainer;
	}

	/**
	 * Set package filter
	 * @param packageFilter Package filter
	 */
	public void setPackageFilter(PackageFilter packageFilter) {
		categoryContainer.setPackageFilter(packageFilter);
	}

	/**
	 * getChildren
	 * @see com.android.sdkuilib.internal.repository.content.INode#getChildren()
	 */
	@Override
	public List<? extends INode> getChildren() {
		return categoryContainer.getCategories();
	}

	/**
	 * Returns flag set true if this node has children. The return value should never be false or the view will be empty.
	 */
    @Override
	public boolean hasChildren() {
		return !categoryContainer.getCategories().isEmpty();
	}

}
