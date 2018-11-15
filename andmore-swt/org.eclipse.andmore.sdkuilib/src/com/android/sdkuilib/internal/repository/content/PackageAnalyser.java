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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.andmore.sdktool.SdkContext;

import com.android.repository.Revision;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoPackage;
import com.android.repository.api.UpdatablePackage;
import com.android.repository.impl.meta.TypeDetails;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.sdkuilib.internal.repository.PackageManager.UpdatablePackageHandler;

/**
 * PackageAnalyser builds and maintains the tree that underlies the SDK packages view. Packages are grouped by category.
 * This class creates API Level categories sorts them into descending level order so the highest API appears on top.
 * @author Andrew Bowley
 *
 * 15-12-2017
 */
public class PackageAnalyser {
	/** Default category for packages of generic type which do not fit in any other category */
	public static final String GENERIC = "generic";
	/** Root of view tree */
	private final ViewTreeRootNode rootNode;
    /** SDK context */	
    private final SdkContext sdkContext;
    /** Category container */
    private final CategoryContainer categoryContainer;
    /** Map of package type name (first segment in package path) to MetaPackage */
    private final Map<String, MetaPackage> metaPackageMap = new HashMap<>();
    
    /**
     * Package state is normally INSTALLED or NEW. DELETED is transient.
     */
    public static enum PkgState { INSTALLED, NEW, DELETED };

    /**
     * Construct a PackageAnalyser object. It is populated by calling {@link #loadPackages()}
     * @param sdkContext SDK context
     */
    public PackageAnalyser(SdkContext sdkContext)
    {
    	this.sdkContext = sdkContext;
    	categoryContainer = new CategoryContainer();
    	initMetaPackages();
    	rootNode = new ViewTreeRootNode(categoryContainer);
    }

    /**
     * Returns root of view tree
     * @return ViewTreeRootNode object
     */
    public ViewTreeRootNode getRootNode() {
    	return rootNode;
    }
 
    /**
     * Populate view tree with consolidated packages ie. combined installed with update, if available and new
     */
	public void loadPackages() {
		UpdatablePackageHandler updateHandler = new UpdatablePackageHandler(){

			@Override
			public void onPackageLoaded(UpdatablePackage updatePackage) {
				updateApiItem(updatePackage);
			}};
	    // All previous entries must be deleted or duplicates will occur		
		categoryContainer.clear();
		sdkContext.getPackageManager().loadConsolidatedPackages(updateHandler);
		// Sort categories and the packages in each category
		categoryContainer.sortCategoryList();
	}
	
    /**
     * Returns meta package for given name
     * @param name Package type name (first segment in package path)
     * @return MetaPackage or null if name invalid
     */
    public MetaPackage getMetaPackage(String name)
    {
    	return metaPackageMap.get(name);
    }

    /**
     * Mark all new and update PkgItems as checked.
     *
     * @param selectUpdates If true, select all update packages.
     * @param selectTop If true, select the top platform. All new packages are selected, excluding system images and 
     *    rc/preview. Packages to update are selected regardless.
     */
    public void checkNewUpdateItems(
            boolean selectUpdates,
            boolean selectTop) {
    	int apiLevel = 0;
    	if (selectTop) { // Find highest API level
	    	for (PkgCategory<AndroidVersion> cat: categoryContainer.getCategories())
	    	{
	    		// Find first API category to get top API
	    		if (cat.getKeyType() == CategoryKeyType.API) {
	    			PkgCategoryApi pkgCategoryApi = (PkgCategoryApi)cat;
	    			apiLevel = pkgCategoryApi.getKeyValue().getApiLevel();
	    			break;
	    		}
	    	}
    	}
    	// Find highest build tools version and check it if new or updatable
    	// and similarly check tools and platform tools.
    	// Exclude preview versions
    	PkgItem buildToolsItem = null;
    	for (PkgCategory<AndroidVersion> cat: categoryContainer.getCategories())
    		if (cat.getKeyType() == CategoryKeyType.TOOLS) {
    			List<PkgItem> packageItems = cat.getItems();
    			for (PkgItem packageItem: packageItems) {
    				if (PkgItem.isPreview(packageItem.getMainPackage()))
    					continue;
    				PackageType packageType = packageItem.getMetaPackage().getPackageType();
    				if (packageType == PackageType.build_tools) {
    					Revision revision = packageItem.getRevision();
    					if (buildToolsItem == null) {
    						buildToolsItem = packageItem;
    					}
    					else if (revision.compareTo(buildToolsItem.getRevision()) > 0)
    						buildToolsItem = packageItem;
    				} else if ((packageType == PackageType.tools) || (packageType == PackageType.platform_tools)) {
    		    		if (packageItem.getState() == PkgState.NEW)
    		    			packageItem.setChecked(true);
    		    		else if (selectUpdates && packageItem.hasUpdatePkg()) 
    		    			packageItem.setChecked(true);
    				}
    			}
    			break;
    		} 
    	if (buildToolsItem != null) {
    		if (buildToolsItem.getState() == PkgState.NEW)
    			buildToolsItem.setChecked(true);
    		else if (selectUpdates && buildToolsItem.hasUpdatePkg()) 
    			buildToolsItem.setChecked(true);
    	}
    	categoryContainer.checkSelections(selectUpdates, apiLevel);
    }

	/**
     * Removes all the internal state and resets the object.
     * Intended only for testing.
     */
    public void clear() {
        categoryContainer.clear();
    }

    /**
     * Mark all PkgItems as not checked.
     */
    public void uncheckAllItems() {
    	categoryContainer.uncheckAllItems();
    }
 
    /**
     * Returns list containing all categories
     * @return PkgCategory list
     */
    public List<PkgCategory<AndroidVersion>> getApiCategories() {
        return categoryContainer.getCategories();
    }

    /**
     * Returns list containing all packages
     * @return PkgItem list
     */
    public List<PkgItem> getAllPkgItems() {
        List<PkgItem> items = new ArrayList<PkgItem>();

        List<PkgCategory<AndroidVersion>> cats = getApiCategories();
        synchronized (cats) {
            for (PkgCategory<AndroidVersion> cat : cats) {
                items.addAll(cat.getItems());
            }
        }
        return items;
    }

    /**
     * Remove all deleted nodes from the tree
     */
	public void removeDeletedNodes() {
		categoryContainer.removeDeletedNodes();
	}

    /**
     * Returns first segment of package path
     * @param path Pacakge path
     * @return String
     */
    public static String getNameFromPath(String path)
    {
    	int pos = path.indexOf(RepoPackage.PATH_SEPARATOR);
    	return pos == -1 ? path : path.substring(0, pos);
    }

    /**
     * Return Android version of package
     * @param pkg Package to inspect
     * @return AndroidVersion object or null if not API package
     */
    public static AndroidVersion getAndroidVersion(RepoPackage pkg) {
        TypeDetails details = pkg.getTypeDetails();
        if (details instanceof DetailsTypes.ApiDetailsType) {
        	return ((DetailsTypes.ApiDetailsType)details).getAndroidVersion();
        }
        return null;
    }

    /***
     * Returns flag set true if tree view has changed since last refresh
     * @return boolean
     */
	public boolean isTreeDirty() {
		return categoryContainer.isDirty();
	}

	/**
	 * Sets dirty flag
	 * @param isDirty Flag set false following view refresh
	 */
	public void setDirty(boolean isDirty) {
		categoryContainer.setDirty(isDirty);
	}

	/**
	 * Returns flag set true if tree has at least one checked item
	 * @return booean
	 */
	public boolean hasCheckedItem() {
        for (PkgCategory<AndroidVersion> cat : getApiCategories()) {
            for (PkgItem packageItem: cat.getItems()) {
            	if (packageItem.isChecked())
            		return true;
            }
        }
        return false;
	}

	/**
	 * Return list of packages which are checked and are new or have an update
	 * @return PkgItem list
	 */
	public List<PkgItem> getPackagesToInstall(PackageFilter packageFilter) {
		List<PkgItem> packageItems = new ArrayList<>();
        List<PkgCategory<AndroidVersion>> cats;
        boolean doFilter = (packageFilter != null) && packageFilter.isFilterOn();
    	if (doFilter)
    		cats = packageFilter.getFilteredApiCategories(categoryContainer.getCategories());
    	else
    		cats = categoryContainer.getCategories();
        synchronized (cats) {
            for (PkgCategory<AndroidVersion> cat : cats) {
                for (PkgItem packageItem: cat.getItems()) {
                	if (((packageItem.getState() == PkgState.NEW) || packageItem.hasUpdatePkg()) &&
                	    packageItem.isChecked()) {
                    	if (doFilter) {
                    		if (packageFilter.selectItem(packageItem))
                        		packageItems.add(packageItem);
                    	}
                    	else
                    		packageItems.add(packageItem);
                	}
                }
            }
        }
		return packageItems;
	}

	/**
	 * Returns list of packages which are checked and installed
	 * @return PkgItem list
	 */
	public List<PkgItem> getPackagesToDelete(PackageFilter packageFilter) {
		List<PkgItem> packageItems = new ArrayList<>();
        List<PkgCategory<AndroidVersion>> cats;
        boolean doFilter = (packageFilter != null) && packageFilter.isFilterOn();
    	if (doFilter)
    		cats = packageFilter.getFilteredApiCategories(categoryContainer.getCategories());
    	else
    		cats = categoryContainer.getCategories();
        synchronized (cats) {
            for (PkgCategory<AndroidVersion> cat : cats) {
                for (PkgItem packageItem: cat.getItems()) {
                	if ((packageItem.getState() == PkgState.INSTALLED) &&
                	    packageItem.isChecked()) {
                    	if (doFilter) {
                    		if (packageFilter.selectItem(packageItem))
                        		packageItems.add(packageItem);
                    	}
                    	else
                    		packageItems.add(packageItem);
                	}
                }
            }
        }
		return packageItems;
	}
	
   /**
     * Create package item for updatable package. The item will will be either INSTALLED
     * or NEW depending on whether the updatable package contains a local package.
     * @param updatePackage Package to be added to tree
     */
	private void updateApiItem(UpdatablePackage updatePackage) {
		LocalPackage local = updatePackage.getLocal();
		RemotePackage remote = updatePackage.getRemote();
		PkgCategory<AndroidVersion> cat = null;
		PkgItem item = null;
		if (local != null) {
			cat = categoryContainer.getPkgCategory(rootNode, local);
	        item = new PkgItem(cat, local, metaPackageFromPackage(local), PkgState.INSTALLED);
	        if (updatePackage.isUpdate())
	        	item.setUpdatePkg(updatePackage);
		}
		else {
			cat = categoryContainer.getPkgCategory(rootNode, remote);
	        item = getPkgItem(cat, remote);
		}
        cat.getItems().add(item);
	}

	/**
	 * Returns package item for a remote package
	 * @param remote Remote package
	 * @return PkgItem object
	 */
	private PkgItem getPkgItem(PkgCategory<AndroidVersion> parent, RemotePackage remote) {
		PkgItem pkgItem = new PkgItem(parent, remote, metaPackageFromPackage(remote), PkgState.NEW);
		return pkgItem;
	}

    /**
     * Returns meta package for given package
     * @param repoPackage Local or remote package
     * @return MetaPackage object 
     */
    private MetaPackage metaPackageFromPackage(RepoPackage repoPackage)
    {
    	String name = PackageAnalyser.getNameFromPath(repoPackage.getPath());
    	MetaPackage metaPackage = getMetaPackage(name);
    	return metaPackage != null ? metaPackage : getMetaPackage(GENERIC);
    }

    /**
     * Initialize meta packages map
     */
    private void initMetaPackages() {
    	MetaPackage metaPackage = new MetaPackage(PackageType.tools, "tool_pkg_16.png");
    	metaPackageMap.put(metaPackage.getName(), metaPackage);
    	
    	metaPackage = new MetaPackage(PackageType.platform_tools, "platformtool_pkg_16.png");
    	metaPackageMap.put(metaPackage.getName(), metaPackage);

    	metaPackage = new MetaPackage(PackageType.build_tools, "buildtool_pkg_16.png");
    	metaPackageMap.put(metaPackage.getName(), metaPackage);

    	metaPackage = new MetaPackage(PackageType.platforms, "platform_pkg_16.png");
    	metaPackageMap.put(metaPackage.getName(), metaPackage);

    	metaPackage = new MetaPackage(PackageType.add_ons, "addon_pkg_16.png");
    	metaPackageMap.put(metaPackage.getName(), metaPackage);

    	metaPackage = new MetaPackage(PackageType.system_images, "sysimg_pkg_16.png");
    	metaPackageMap.put(metaPackage.getName(), metaPackage);

    	metaPackage = new MetaPackage(PackageType.sources, "source_pkg_16.png");
    	metaPackageMap.put(metaPackage.getName(), metaPackage);

    	metaPackage = new MetaPackage(PackageType.samples, "source_pkg_16.png");
    	metaPackageMap.put(metaPackage.getName(), metaPackage);

    	metaPackage = new MetaPackage(PackageType.docs, "doc_pkg_16.png");
    	metaPackageMap.put(metaPackage.getName(), metaPackage);

        metaPackage = new MetaPackage(PackageType.extras, "extra_pkg_16.png");
    	metaPackageMap.put(metaPackage.getName(), metaPackage);

        metaPackage = new MetaPackage(PackageType.emulator, "tool_pkg_16.png");
    	metaPackageMap.put(metaPackage.getName(), metaPackage);

        metaPackage = new MetaPackage(PackageType.cmake, "tool_pkg_16.png");
    	metaPackageMap.put(metaPackage.getName(), metaPackage);

        metaPackage = new MetaPackage(PackageType.lldb, "tag_default_16.png");
    	metaPackageMap.put(metaPackage.getName(), metaPackage);

        metaPackage = new MetaPackage(PackageType.ndk_bundle, "tag_default_16.png");
    	metaPackageMap.put(metaPackage.getName(), metaPackage);

        metaPackage = new MetaPackage(PackageType.patcher, "tool_pkg_16.png");
    	metaPackageMap.put(metaPackage.getName(), metaPackage);

        metaPackage = new MetaPackage(PackageType.generic, "tag_default_16.png");
    	metaPackageMap.put(metaPackage.getName(), metaPackage);	
    }


}
