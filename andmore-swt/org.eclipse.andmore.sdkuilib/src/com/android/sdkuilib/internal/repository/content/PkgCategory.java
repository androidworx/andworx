/*
 * Copyright (C) 2011 The Android Open Source Project
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.android.sdkuilib.internal.repository.content.PackageAnalyser.PkgState;

/**
 * PkgCategory is a collection of packages belonging to the same classification.
 * It is also a view tree node which sits directly under the root node. A PkgCategory
 * is assigned a type (CategoryKeyType) which reflects the type of packages it contains. 
 * The type determines placement in the view tree and also Android version for those
 * packages which have this property. 
 * 
 * This class contains a generic key value. The concept of having a category key is a throw back
 * to a time when there was an option to view packages according to origin and does not serve
 * any purpose now apart to highlight the significance of Android version in ordering categories.
 * 
 * The term "product" has been coined to describe a package path minus any trailing version information.
 * Packages sharing the same product can be filtered so only the one with the highest version is
 * selected. 
 *  
 * @author Andrew Bowley
 *
 * 10-11-2017
 */
public abstract class PkgCategory<K> extends INode {
	/** Category type - reflects package classification */
	protected final CategoryKeyType keyType;
	/** Android version or null if the contained packages do not have this property */
	protected final K keyValue;
	/* File name of image to display with this category */
	protected final String imageReference;
	/** List of package items. Each item references a package which is installed or available for download */
	protected final List<PkgItem> packageList = new ArrayList<PkgItem>();
	/** Maps product to item with the higest version of that product. */
	protected final Map<String, PkgItem> productMap = new TreeMap<>();
	/** Name to be displayed for this category */
	protected String label;
	/** Option to not filter products. This option causes visual clutter and is off by default */
	protected boolean selectAllPackages = false;

	/**
	 * Construct PkgCategory object
	 * @param keyType Category type (which determines if key value is set)
	 * @param keyValue Android version or null if not API or EARLY_API type
	 * @param imageReference Name of image file
	 */
    public PkgCategory(CategoryKeyType keyType, K keyValue, String imageReference) {
    	super();
    	this.keyType = keyType;
    	this.keyValue = keyValue;
    	this.imageReference = imageReference;
    	label = keyType.label;
    }

    /**
     * Returns category type  (which determines if key value is set)
     * @return CategoryKeyType enum
     */
    public CategoryKeyType getKeyType()
    {
    	return keyType;
    }

    /**
     * Returns Android version
     * @return AndroidVersion or null if not API or EARLY_API type
     */
    public K getKeyValue() {
        return keyValue;
    }

    /**
     * Returns name to display
     * @return String
     */
    public String getLabel() {
        return label;
    }

    /**
     * Set option to not filter products - produces cluttered pacakges display
     * @param selectAllPackages
     */
    public void setSelectAllPackages(boolean selectAllPackages) {
		this.selectAllPackages = selectAllPackages;
	}

    /**
     * Returns packages
     * @return list of package items
     */
	public List<PkgItem> getItems() {
        return packageList;
    }

	/**
	 * Clear product map in preparation to recreate it
	 */
    public void clearProducts() {
    	productMap.clear();
    }
 
    /**
     * Put product in product map
     * @param product The product
     * @param item The package item
     */
    public void putProduct(String product, PkgItem item) {
    	productMap.put(product, item);
    }

    /** 
     * Returns package item for given product
     * @param product The product
     * @return PkgItem object
     */
    public PkgItem getProduct(String product) {
    	return productMap.get(product);
    }
    
	/**
	 * Returns the text for the label of this node.
	 * @param element Not used
	 * @param columnIndex The index of the column being displayed
	 * @return the text string used to label the element
	 */
    @Override
	public String getText(Object element, int columnIndex) {
		return columnIndex == PkgCellAgent.NAME ? getLabel() : VOID;
	}

	/**
	 * Returns the image resource value for the label of the given element.  
	 * @param element Not used
	 * @param columnIndex The index of the column being displayed
	 * @return the resource value of image used to label the element
	 */
    @Override
	public String getImage(Object element, int columnIndex) {
    	if (columnIndex == PkgCellAgent.NAME)
		    return imageReference;
        return VOID;
	}

	/**
	 * Returns list of children, using product filtering, if this option is selected
	 * @return INode list
	 */
    @Override
	public List<? extends INode> getChildren() {
    	if (!selectAllPackages && !packageList.isEmpty()) {
    		// Product filtering relies on sorting packages by path to get latest version
    		// Every time the product changes while iterating through the list, the
    		// package encountered is the highest available version.
    		String lastProduct = null;
    		List<PkgItem> filteredPackageList = new ArrayList<PkgItem>();
    		Iterator<PkgItem> iterator = packageList.iterator();
    		// Do not filter installed packages. Filtering only applies to new packages.
    		while (iterator.hasNext()) {
    			PkgItem packageItem = iterator.next();
    			String product = packageItem.getProduct();
    			boolean isInstalled = packageItem.getState() != PkgState.NEW;
    			if (isInstalled || !product.equals(lastProduct)) {
    				filteredPackageList.add(packageItem);
    				product = packageItem.getProduct();
    			}
    			lastProduct = product;
    		}
			return filteredPackageList;
    	}
        return packageList;
	}

    /**
     * Returns flag set true if this category has children
     */
    @Override
	public boolean hasChildren() {
		return !packageList.isEmpty();
	}

    /** {@link PkgCategory}s are equal if their types and internal keys are equal. */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((keyValue == null) ? keyType.hashCode() : keyValue.hashCode());
        return result;
    }

    /** {@link PkgCategory}s are equal if their types and internal keys are equal. */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        PkgCategory<?> other = (PkgCategory<?>) obj;
        if (keyType != other.keyType)
        	return false;
        if (keyValue == null) {
            if (other.keyValue != null) 
            	return false;
        } else if (!keyValue.equals(other.keyValue)) 
        	return false;
        return true;
    }
}
