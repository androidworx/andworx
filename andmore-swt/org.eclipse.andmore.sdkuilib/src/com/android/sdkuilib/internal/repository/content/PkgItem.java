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

import org.eclipse.andmore.sdktool.Utilities;

import com.android.SdkConstants;
import com.android.annotations.Nullable;
import com.android.repository.Revision;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoPackage;
import com.android.repository.api.UpdatablePackage;
import com.android.repository.impl.meta.Archive;
import com.android.repository.impl.meta.TypeDetails;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.sdkuilib.internal.repository.content.PackageAnalyser.PkgState;
import com.android.sdkuilib.internal.repository.ui.PackagesPageIcons;

/**
 * A {@link PkgItem} represents one main {@link Package} combined with its state
 * and an optional update package. It is also a node of the tree view, a child of
 * a category node.
 * <p/>
 * The main package is final and cannot change since it's what "defines" this PkgItem.
 * The state or update package can change later.
 */
public class PkgItem extends INode implements Comparable<PkgItem> {
    private static final String ICON_PKG_OBSOLETE = "error_icon_16.png";

    /** Information about a package type */
	private final MetaPackage metaPackage;
	/** Package state - INSTALLED, NEW or DELETED */
    private PkgState state;
    /** The package wrapped by this object - can be a LocalPackage or RemotePackage */
    private RepoPackage mainPackage;
    /** Updatable package for case a local package can be updated */
    private UpdatablePackage updatePackage;
    /** The package identity, which is the package path minus version, if any */
    private String product;
    /** Version appended to path, if any */
    private String version;

    /**
     * Create a new {@link PkgItem} for this main package.
     * The main package is final and cannot change since it's what "defines" this PkgItem.
     * The state or update package can change later.
     */
    public PkgItem(PkgCategory<AndroidVersion> parent, RepoPackage mainPackage, MetaPackage metaPackage, PkgState state) {
    	super(parent);
    	this.mainPackage = mainPackage;
        this.metaPackage = metaPackage;
        this.state = state;
        analysePath();
    }

    /**
     * Returns the product
     * @return String
     */
	public String getProduct() {
		return product;
	}

	/** 
	 * Returns version
	 * @return String or null if version not appended to package path
	 */
	public String getVersion() {
		return version;
	}

	/**
	 * Returns flag set true if package is marked as obsolete
	 * @return boolean
	 */
	public boolean isObsolete() {
        return mainPackage.obsolete();
    }

	/**
	 * Returns update package
	 * @return UpdatablePackage object or null if none available
	 */
    public UpdatablePackage getUpdatePkg() {
        return updatePackage;
    }

    /**
     * Set updatable package
     * @param updatePkg The updatable package
     */
    public void setUpdatePkg(UpdatablePackage updatePkg) {
    	updatePackage = updatePkg;
    }

    /**
     * Return flag set true if this item has an update
     * @return boolean
     */
    public boolean hasUpdatePkg() {
        return updatePackage != null;
    }

    /**
     * Returns package name
     * @return String
     */
    public String getName() {
        return mainPackage.getDisplayName();
    }

    /**
     * Returns package revision
     * @return Revision object
     */
    public Revision getRevision() {
        return mainPackage.getVersion();
    }

    /**
     * Returns meta package
     * @return MetaPackage object
     */
    public MetaPackage getMetaPackage()
    {
    	return metaPackage;
    }
    
    /**
     * Returns package wrapped by this item
     * @return RepoPackage which will actually be either a LocalPackage or RemotePackage
     */
    public RepoPackage getMainPackage() {
        return mainPackage;
    }

    /**
     * Returns package state
     * @return PkgState enum
     */
    public PkgState getState() {
        return state;
    }

    /**
     * Returns Android version contained in package type details
     * @return AndroidVersion object or null if package type details does not include Android version
     */
    @Nullable
    public AndroidVersion getAndroidVersion() {
        return getAndroidVersion(mainPackage);
    }

    /**
     * Get new package archives
     * @return Archive array which is empty if the package is installed. The array will hold only one archive if populated.
     */
    public Archive[] getArchives() {
    	if (state == PkgState.NEW)
    		return new Archive[]{((RemotePackage)mainPackage).getArchive()};
        return new Archive[0];
    }

	/**
	 * Returns the image resource value for the node label 
	 * @param element Not used
	 * @param columnIndex The index of the column being displayed
	 * @return the resource value of image used to label the element
	 */
    @Override
	public String getImage(Object element, int columnIndex) {
    	if (columnIndex == PkgCellAgent.NAME)
		    return metaPackage.getIconResource();
    	else if (columnIndex == PkgCellAgent.STATUS) {
            switch(state) {
            case INSTALLED:
            	if (isObsolete())
            		return ICON_PKG_OBSOLETE;
                if (updatePackage != null) {
                    return PackagesPageIcons.ICON_PKG_UPDATE;
                } else {
                    return PackagesPageIcons.ICON_PKG_INSTALLED;
                }
            case NEW:
            	if (isObsolete())
            		return ICON_PKG_OBSOLETE;
                return PackagesPageIcons.ICON_PKG_NEW;
            case DELETED:
            	return PackagesPageIcons.ICON_PKG_INCOMPAT;
            }
    	}
        return VOID;
	}


	/**
	 * Returns the text for the node label
	 * @param element Not used
	 * @param columnIndex The index of the column being displayed
	 * @return the text string used to label the element, or VOID if there is no text label for the given object
	 */
    @Override
	public String getText(Object element, int columnIndex) {
    	switch (columnIndex)
    	{
    	case PkgCellAgent.NAME: 
    		return decorate(getPkgItemName());
    	case PkgCellAgent.API:  
    	{
    		AndroidVersion version = getAndroidVersion();
     		return version == null ? VOID : getAndroidVersion().getApiString();
    	}
    	case PkgCellAgent.REVISION: 
    		// Do use version from path, if available to pick up alpha/beta
    		if (version != VOID)
    			return version;
    		return mainPackage.getVersion().toString();
    	case PkgCellAgent.STATUS:   
    		return getStatusText();
    	default:
    	}
		return VOID;
	}

    /**
     * Decorate name - only obsolete packages affected
     * @param pkgItemName Unadorned package name
     * @return decorated name
     */
	private String decorate(String pkgItemName) {
		if (isObsolete())
			return pkgItemName + "(obsolete)";
		return pkgItemName;
	}

	/**
	 * Get the text displayed in the tool tip for given element 
	 * @param element Target
	 * @return the tooltop text, or VOID for no text to display
	 */
    @Override
	public String getToolTipText(Object element) {
        String s = getTooltipDescription(mainPackage);

        if ((updatePackage != null) && updatePackage.isUpdate()) {
            s += "\n-----------------" +        //$NON-NLS-1$
                 "\nUpdate Available:\n" +      //$NON-NLS-1$
                 getTooltipDescription(updatePackage.getRemote());
        }
        return s;
	}

    /**
     * Mark item as checked according to given criteria. Do not change check state if criteria not satisfied.
     * @param selectUpdates If true, select all update packages
     * @param topApiLevel If greater than 0, select platform packages of this api level
      */
    @Override
	public boolean checkSelections(
            boolean selectUpdates,
            int topApiLevel)
	{
		boolean hasUpdate = (state == PkgState.INSTALLED) && (updatePackage != null);
		if (selectUpdates  && hasUpdate) {
			    setChecked(true);
			    return true;
		}
		if (topApiLevel > 0) {
			if (hasUpdate || // or new packages excluding system images and previews
					((state == PkgState.NEW) && (metaPackage.getPackageType() != PackageType.system_images))) {
    		    AndroidVersion version = getAndroidVersion();
    		    if ((version != null) && (version.getApiLevel() == topApiLevel) && !version.isPreview()) {
    			    setChecked(true);
    			    return true;
    			}
			}
		}
		return false;
	}

    /**
     * Mark item as deleted. This is a transient state leading to removal from the view tree
     */
    @Override
	public void markDeleted()
	{
		state = PkgState.DELETED;
		setChecked(false);
		updatePackage = null;
	}

    /**
     * Returns flag set true if this item is deleted
     */
    @Override
	public boolean isDeleted()
	{
		return state == PkgState.DELETED; 
	}

    /** Returns a string representation of this item, useful when debugging. */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append('<');

        if (isChecked) {
            sb.append(" * "); //$NON-NLS-1$
        }

        sb.append(state.toString());

        if (mainPackage != null) {
            sb.append(", pkg:"); //$NON-NLS-1$
            sb.append(mainPackage.toString());
        }

        if (updatePackage != null) {
            sb.append(", updated by:"); //$NON-NLS-1$
            sb.append(updatePackage.toString());
        }

        sb.append('>');
        return sb.toString();
    }

    @Override
    public int compareTo(PkgItem other) {
    	if (other == null)
    		return Integer.MIN_VALUE;
    	int comparison1 = state.ordinal() - other.getState().ordinal();
    	if (comparison1 != 0)
    		return comparison1;
    	if (hasUpdatePkg() && other.hasUpdatePkg())
    		return updatePackage.compareTo(other.getUpdatePkg());
        return mainPackage.compareTo(other.getMainPackage());
    }

    /**
     * Equality is defined as {@link #isSameItemAs(PkgItem)}: state, main package
     * and update package must be the similar.
     */
    @Override
    public boolean equals(Object obj) {
        return (obj instanceof PkgItem) && (compareTo((PkgItem) obj) == 0);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + state.hashCode();
        result = prime * result + mainPackage.hashCode();
        result = prime * result + ((updatePackage == null) ? 0 : updatePackage.hashCode());
        return result;
    }

    /**
     * Returns Android version contained in given package type details
     * @param repoPackage The package 
     * @return AndroidVersion object or null if type details do not contain Android version
     */
    @Nullable
    public static AndroidVersion getAndroidVersion(RepoPackage repoPackage) {
        TypeDetails details = repoPackage.getTypeDetails();
        if (details instanceof DetailsTypes.ApiDetailsType) {
        	return ((DetailsTypes.ApiDetailsType)details).getAndroidVersion();
        }
        return null;
    }
    
    /**
     * Returns flag set true if given package type details contains codename
     * @param repoPackage The package 
     * @return boolean
     */
    public static boolean isPreview(RepoPackage repoPackage) {
        TypeDetails details = repoPackage.getTypeDetails();
        if (details instanceof DetailsTypes.ApiDetailsType) {
        	return ((DetailsTypes.ApiDetailsType)details).getCodename() != null;
        }
        return false;
    }

    /**
     * Analyse package path to extract product and version
     */
    private void analysePath() {
		// Parse package path to separate product from version
    	String path = mainPackage.getPath();
    	int index = path.lastIndexOf(';');
    	if (index != -1) {
    		String candidateVersion = path.substring(index + 1);
    		if (Character.isDigit(candidateVersion.charAt(0)))
    			version = candidateVersion;
    		else if (candidateVersion.startsWith("android-")) 
    			version = candidateVersion.substring(8);
    	}
    	if (version == null) {
    		version = VOID;
    		product = path;
    	} else {
    		product = path.substring(0,  index);
    	}	
	}

    /**
     * Returns status text
     * @return String
     */
    private String getStatusText() {
       switch(state) {
       case INSTALLED:
           if (updatePackage != null) {
               return String.format(
                       "Update available: rev. %1$s",
                       updatePackage.getRemote().getVersion().toString());
           }
           return "Installed";

       case NEW:
           if (((RemotePackage)mainPackage).getArchive().isCompatible()) {
               return "Not installed";
           } else {
               return String.format("Not compatible with %1$s",
                       SdkConstants.currentPlatformName());
           }
       case DELETED:
    	   return "Deleted";
       }
       return state.toString();
	}

    /**
     * Returns tooltip description for given package
     * @param repoPackage The package
     * @return String
     */
    private String getTooltipDescription(RepoPackage repoPackage) {
    	String s = repoPackage.getDisplayName();
    	if (repoPackage instanceof RemotePackage) {
    		RemotePackage remote = (RemotePackage) repoPackage;
    		// For non-installed item get download size
    		long fileSize = remote.getArchive().getComplete().getSize();
    		s += '\n' + Utilities.formatFileSize(fileSize);
    		s += String.format("\nProvided by %1$s", remote.getSource().getUrl());
    	}
    	return s;
    }

    /**
     * Returns name of package. Normally package display name but version is removed
     * to avoid duplication in the displayed details. An exception is made for platforms as
     * the name from the package is not suitable in this case. 
     * @return
     */
    private String getPkgItemName() {
	    if (metaPackage.getPackageType() == PackageType.platforms)
	    	return "Platform SDK";
	    String displayName = mainPackage.getDisplayName();
	    if (version != VOID) {
	    	int index = displayName.indexOf(version);
	    	if (index != -1)
	    		return displayName.substring(0,  index);
	    }
		return displayName;
	}


}
