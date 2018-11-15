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
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import com.android.repository.api.RepoPackage;
import com.android.sdklib.AndroidVersion;
import com.android.sdkuilib.internal.repository.content.PackageAnalyser.PkgState;
import com.android.sdkuilib.internal.repository.ui.PackagesPageIcons;

/**
 * Container for categories which sorts by type and also by API level for Category Key Types API and EARLY_API 

 * @author Andrew Bowley
 *
 * 18-12-2017
 */
public class CategoryContainer {
	/** Platform version equal or greater than this level is placed in top grouping */
	public static AndroidVersion API_SPLIT_LEVEL = AndroidVersion.ART_RUNTIME;
	
	/** Categories collection */
    private final List<PkgCategory<AndroidVersion>> categories = new ArrayList<>();
    /** Package filter which allows user to select which package types to view */
    private PackageFilter packageFilter;
    /** Flag set true if view tree has changed */
    private boolean isDirty;

	/**
	 * Create CategoryContainer object
	 */
	public CategoryContainer() {
	   	isDirty = true;
	}

	/**
	 * Set package filter
	 * @param packageFilter Package filter
	 */
	public void setPackageFilter(PackageFilter packageFilter) {
		this.packageFilter = packageFilter;
	}

	public void setDirty(boolean isDirty) {
		this.isDirty = isDirty;
	}
	
    /**
	 * @return the isDirty
	 */
	public boolean isDirty() {
		return isDirty;
	}

	/** Removes all internal state. */
    public void clear() {
    	categories.clear();
    	isDirty = true;
    }

    /**
     * Removes all nodes marked for deletion
     */
    public void removeDeletedNodes() {
		for (PkgCategory<?> cat: categories)
			removeDeleted(cat, 0);
    }

    /**
     * Mark all PkgItems as not checked.
     */
    public void uncheckAllItems() {
		for (PkgCategory<?> cat: categories)
			uncheckNode(cat, 0);
    }
    
    /** 
     * Returns the sorted category list. 
     * @return list of package categories
     */
    public List<PkgCategory<AndroidVersion>> getCategories() {
    	if ((packageFilter != null) && packageFilter.isFilterOn())
    		return packageFilter.getFilteredApiCategories(categories);
    	return categories;
    }

    /**
     * Set all nodes checked which meet criteria. Navigates tree recursively.
     * @param selectUpdates Flag set true if updates to be checked
     * @param topApiLevel Flag set true if top API category to be checked
     */
    public void checkSelections(boolean selectUpdates, int topApiLevel) {
		for (PkgCategory<?> cat: categories)
			checkNode(cat, selectUpdates, topApiLevel, 0);
    }
    
    /** 
     * Returns the category key type for the given package 
     * @param pkg Package to be assigned a category
     * @return CategoryKeyType enum
     */
	public CategoryKeyType getCategoryKeyType(RepoPackage pkg) {
        // If the package has an Android version, then sort by API.
		// Package types which qualify include platforms, addons and system images
        AndroidVersion androidVersion = PkgItem.getAndroidVersion(pkg);
        if (androidVersion != null) {
        	// There are 2 API categories to allow early APIs to be last in sort order
        	if (API_SPLIT_LEVEL.compareTo(androidVersion) <= 0)
        		return CategoryKeyType.API;
        	else
        		return CategoryKeyType.EARLY_API;

        } else if (pkg.getPath().indexOf("tools") != -1) {
            if (PkgItem.isPreview(pkg)) {
                return CategoryKeyType.TOOLS_PREVIEW;
            } else {
                return CategoryKeyType.TOOLS;
            }
        } else if (pkg.getPath().indexOf("extras") != -1) {
            return CategoryKeyType.EXTRA;
        } else {
        	// The remaining packages are covered by blanket "generic"
            return CategoryKeyType.GENERIC;
        }
	}

	/*
	 * Returns the category key value for the given package.
     * @param pkg Package to interrogate for Android version
     * @return AndroidVersion or null
	 */
	public AndroidVersion getCategoryKeyValue(RepoPackage pkg) {
        // Sort by API if package has Android version
        return PkgItem.getAndroidVersion(pkg);
	}

	/**
	 * Creates the category for the given key and returns it
	 * @param catKeyType Category key type
	 * @param catKeyValue Android version or null according to first parameter
	 * @reaturn PkgCategory object
	 */
	public PkgCategory<AndroidVersion> createCategory(CategoryKeyType catKeyType, AndroidVersion catKeyValue) {
        // Create API category.
        PkgCategory<AndroidVersion> cat = null;
        String iconReference = catKeyType == 
        	CategoryKeyType.API ? 
        	PackagesPageIcons.ICON_CAT_PLATFORM : 
        	PackagesPageIcons.ICON_CAT_OTHER;
        cat = new PkgCategoryApi(
        	catKeyType,
        	catKeyValue,
        	iconReference);
        return cat;
	}

	/**
	 * Sorts the category list and the packages in each category
	 */
	public void sortCategoryList() {
        // Sort the categories list. First order is by type and then by level for API types
        synchronized (categories) {
            Collections.sort(categories, new Comparator<PkgCategory<AndroidVersion>>() {
                @Override
                public int compare(PkgCategory<AndroidVersion> cat1, PkgCategory<AndroidVersion> cat2) {
                	boolean isCat1Api = cat1.getKeyType() == CategoryKeyType.API;
                 	boolean isCat2Api = cat2.getKeyType() == CategoryKeyType.API;
                	boolean isCat1EarlyApi = cat1.getKeyType() == CategoryKeyType.EARLY_API;
                 	boolean isCat2EarlyApi = cat2.getKeyType() == CategoryKeyType.EARLY_API;
                	if ((isCat1Api && isCat2Api) || (isCat1EarlyApi && isCat2EarlyApi) ) { 
                		return cat2.getKeyValue().compareTo(cat1.getKeyValue());
                	}
                	else
                        return cat1.getKeyType().ordinal() - cat2.getKeyType().ordinal();
                }
            });
            for (PkgCategory<AndroidVersion> cat: categories)
            	sortPackages(cat);
        }
	}

	/**
	 * Sort packages in given category. Sort first on package type, then on name 
	 * and finally on path in reverse to get top version down ordering.
	 * @param cat Package category object
	 */
	private void sortPackages(PkgCategory<AndroidVersion> cat)
	{
		synchronized (cat)
		{
			Collections.sort(cat.getItems(),  new Comparator<PkgItem>() {

				@Override
				public int compare(PkgItem item1, PkgItem item2) {
					int ordinal1 = item1.getMetaPackage().getPackageType().ordinal();
					int ordinal2 = item2.getMetaPackage().getPackageType().ordinal(); 
					int comparison1 =  ordinal1 - ordinal2;
					if (comparison1 != 0)
						return comparison1;
					String path1 = item1.getMainPackage().getPath();
					String path2 = item2.getMainPackage().getPath();
			    	String name1 = PackageAnalyser.getNameFromPath(path1);
			    	String name2 = PackageAnalyser.getNameFromPath(path2);
			    	int comparison2 = name1.compareTo(name2);
			    	if (comparison2 != 0)
			    		return comparison2;
			    	// If names are same, then match on segments up to last
			    	String SEPARATOR = Character.toString(RepoPackage.PATH_SEPARATOR);
			    	String[] segments1 = path1.split(SEPARATOR);
			    	String[] segments2 = path1.split(SEPARATOR);
			    	int stop = Math.min(segments1.length, segments2.length);
			    	for (int i = 1; i < stop - 1; i++) {
			    		int comparison3 = segments1[i].compareTo(segments2[i]);
			    		if (comparison3 != 0)
			    			return comparison3;
			    	}
			    	if (segments1.length != segments2.length) 
			    		// Order by number of segments if mismatched (not expected)
			    		return segments1.length - segments2.length;
			    	// Use reverse lexical order of paths for same package types to get top down version ordering
					return path2.compareTo(path1);
				}
				
			});
		}
		// Generate product map to support filtering on latest version
		String product = null;
		cat.clearProducts();
		Iterator<PkgItem> iterator = cat.getItems().iterator();
		while (iterator.hasNext()) {
			PkgItem packageItem = iterator.next();
			if ((packageItem.getState() == PkgState.NEW) &&
					!packageItem.getProduct().equals(product)) {
				product = packageItem.getProduct();
				cat.putProduct(product, packageItem);
			}
		}
	}

	/**
	 * Finds or creates category for given package
	 * @param pkg Package to add to tree
	 * @param rootNode Root of view tree which will be parent to all categories
	 * @return PkgCategory object
	 */
	public PkgCategory<AndroidVersion> getPkgCategory(ViewTreeRootNode rootNode, RepoPackage pkg)
	{
        CategoryKeyType catKeyType = getCategoryKeyType(pkg);
        PkgCategory<AndroidVersion> cat = null;
        AndroidVersion catKeyValue = null;
        switch (catKeyType)
        {
        case API: 
        case EARLY_API: 
         	catKeyValue = getCategoryKeyValue(pkg);
        	cat = findCurrentCategory(categories, catKeyType, catKeyValue);
        	break;
        default:	
        	cat = findCurrentCategory(categories, catKeyType);
        }
        if (cat == null) {
            // This is a new category. Create it and add it to the list.
            cat =createCategory(catKeyType, catKeyValue);
            cat.parentNode = rootNode;
            synchronized (categories) {
            	categories.add(cat);
            }
        }
        return cat;
	}

	/**
	 * Returns category in given list of given type
	 * @param currentCategories List of existing categories
	 * @param catKeyType Category key type
	 * @return PkgCategory object or null if category not found
	 */
    private PkgCategory<AndroidVersion> findCurrentCategory(
            List<PkgCategory<AndroidVersion>> currentCategories,
            CategoryKeyType catKeyType) {
        for (PkgCategory<AndroidVersion> cat : currentCategories) {
            if (cat.getKeyType() == catKeyType) {
                return cat;
            }
        }
        return null;
    }

	/**
	 * Returns category in given list of given type and key value
	 * @param currentCategories List of existing categories
	 * @param catKeyType Category key type
	 * @param categoryKeyValue Category key value, which will be of type AndroidVersion
	 * @return PkgCategory object or null if category not found
	 */
    private PkgCategory<AndroidVersion> findCurrentCategory(
            List<PkgCategory<AndroidVersion>> currentCategories,
            CategoryKeyType catKeyType, Object categoryKeyValue) {
        for (PkgCategory<AndroidVersion> cat : currentCategories) {
            if ((cat.getKeyType() == catKeyType) && (cat.getKeyValue().equals(categoryKeyValue)))
                return cat;
            }
        return null;
    }

	/**
	 * Remove deleted children from given node
	 * @param node Node to operate on
	 * @param level Level in tree
	 */
    private void removeDeleted(INode node, int level) {
    	List<INode> removeList = null;
    	for (INode childNode: node.getChildren()) {
    		if (childNode.isDeleted()) {
    			if (removeList == null)
    				removeList = new ArrayList<>();
    			removeList.add(childNode);
    		}
    		removeDeleted(childNode, level + 1);
    	}
        if (removeList != null) {
        	for (INode removeNode: removeList) {
        		node.getChildren().remove(removeNode);
        	}
        	isDirty = true;
    	}
	}

	/**
	 * Uncheck every node in the tree. Navigates tree recursively.
     * @param node Tree node
     * @param level Level in tree
	 */
	private void uncheckNode(INode node, int level) {
		node.setChecked(false);
		for (INode child: node.getChildren())
			uncheckNode(child, level+ 1);
    	isDirty = true;
	}

    /**
     * Set tree node checked if criteria met. Navigates tree recursively.
     * @param node Tree node
     * @param selectUpdates Flag set true if updates to be checked
     * @param topApiLevel Flag set true if top API category to be checked
     * @param level Level in tree
     */
	private void checkNode(INode node, boolean selectUpdates, int topApiLevel, int level) 
	{
		if (node.checkSelections(selectUpdates, topApiLevel))
			isDirty = true;
    	boolean doFilter = (packageFilter != null) && packageFilter.isFilterOn();
		for (INode child: node.getChildren())
			if (!doFilter || packageFilter.selectItem(child))
				checkNode(child, selectUpdates, topApiLevel, level+ 1);
	}

}
