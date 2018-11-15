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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.Viewer;

import com.android.sdklib.AndroidVersion;

/**
 * PackageContentProvider is an {@link INode} content provider
 * @author Andrew Bowley
 *
 * 17-12-2017
 */
public class PackageContentProvider implements ITreeContentProvider {

	/** Package filter - allows the user to select which package types to view */
    private final PackageFilter mPackageFilter;

    /**
     * Construct PackageContentProvider object
     * @param packageFilter Package filter
     */
    public PackageContentProvider(PackageFilter packageFilter)
	{
        this.mPackageFilter = packageFilter;
    }

    /**
     * Returns the child elements of the given parent element.
     * <p>
     * The difference between this method and <code>IStructuredContentProvider.getElements</code>
     * is that <code>getElements</code> is called to obtain the
     * tree viewer's root elements, whereas <code>getChildren</code> is used
     * to obtain the children of a given parent element in the tree (including a root).
     * </p>
     * The result is not modified by the viewer.
     *
     * @param parentElement the parent element
     * @return an array of child elements
     */
   @Override
    public Object[] getChildren(Object parentElement) {
    	// Special case for package filtering
    	if (mPackageFilter.isFilterOn() && (parentElement instanceof PkgCategory)) {
	       	List<INode> selectItems = new ArrayList<>();
			@SuppressWarnings("unchecked")
			PkgCategory<AndroidVersion> pkgCategory = (PkgCategory<AndroidVersion>)parentElement;
			// Use PkgCategory.getChildren() to filter on "All new" option
	       	for (INode child: pkgCategory.getChildren()) {
	       		if (mPackageFilter.selectItem(child)) 
	       			selectItems.add(child);	
	       	}
            return selectItems.toArray();
        }
    	INode node = (INode)parentElement;
        return node.getChildren().toArray();
    }

   /**
    * Returns the elements to display in the viewer
    * when its input is set to the given element.
    * These elements can be presented as rows in a table, items in a list, etc.
    * The result is not modified by the viewer.
    *
    * @param inputElement the input element
    * @return the array of elements to display in the viewer
    */
    @Override
    public Object[] getElements(Object inputElement) {
        return getChildren(inputElement);
    }

    /**
     * Returns the parent for the given element, or <code>null</code>
     * indicating that the parent can't be computed.
     * In this case the tree-structured viewer can't expand
     * a given node correctly if requested.
     *
     * @param element the element
     * @return the parent element, or <code>null</code> if it
     *   has none or if the parent cannot be computed
     */
    @Override
    public Object getParent(Object element) {
        INode node = (INode)element;
        return node.getParent();
    }

    /**
     * Returns whether the given element has children.
     * <p>
     * Intended as an optimization for when the viewer does not
     * need the actual children.  Clients may be able to implement
     * this more efficiently than <code>getChildren</code>.
     * </p>
     *
     * @param element the element
     * @return <code>true</code> if the given element has children,
     *  and <code>false</code> if it has no children
     */
    @Override
    public boolean hasChildren(Object parentElement) {
    	INode node = (INode)parentElement;
        return node.hasChildren();
    }

	@Override
	public void dispose() {
	}

	@Override
	public void inputChanged(Viewer arg0, Object arg1, Object arg2) {
	}

}
