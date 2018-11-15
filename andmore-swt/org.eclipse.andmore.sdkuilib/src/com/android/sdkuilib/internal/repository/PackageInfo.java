/*
 * Copyright (C) 2009-2017 The Android Open Source Project
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
package com.android.sdkuilib.internal.repository;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.RemotePackage;

/**
 * Represents package that we want to install.
 * <p/>
 * A new package is a remote package that needs to be downloaded and then
 * installed. It can replace an existing local one. It can also depend on another
 * (new or local) package, which means the dependent package needs to be successfully
 * installed first. Finally this package can also be a dependency for another one.
 * Note: Tracking the "dependency for" relationship has been discontinued.
 * <p/>
 * The accepted and rejected flags are used by {@code SdkUpdaterChooserDialog} to follow
 * user choices. The installer should never install something that is not accepted.
 */
public class PackageInfo implements Comparable<PackageInfo> {

	/** Remote package to be installed */
    private final RemotePackage mNewPackage;
    /** Local package being updated or null if none */
    private LocalPackage mReplaced;
    /** License terms acceptted by user */
    private boolean mAccepted;
    /** License terms not accepted by user */
    private boolean mRejected;

    /**
     * Construct PackageInfo object
     * @param newPackage The package to be installed
     */
    public PackageInfo(@NonNull RemotePackage newPackage) {
    	 this(newPackage, null);
    }

    /**
     * Construct PackageInfo object where the {@code newPackage} will replace the
     * currently installed {@code replaced} package.
     * @param newPackage The package to be installed 
     * @param replaced The existing local package to be updated or null if there is none
     */
   public PackageInfo(@NonNull RemotePackage newPackage, @Nullable LocalPackage replaced) {
    	mNewPackage = newPackage;
   	 	mReplaced = replaced;
    }
    
    /**
     * Returns the package to be installed.
     * @return RemotePackage object
     */
    public RemotePackage getNewPackage() {
        return mNewPackage;
    }

    /**
     * Returns an local package to be replaced, if any.
     * @return LocalPackage object or null
     */
    public LocalPackage getReplaced() {
        return mReplaced;
    }

    /**
     * Sets flag for package is accepted  for installation.
     */
    public void setAccepted(boolean accepted) {
        mAccepted = accepted;
    }

    /**
     * Returns flag for package is accepted for installation.
     * @return boolean
     */
    public boolean isAccepted() {
        return mAccepted;
    }

    /**
     * Sets flag for package was rejected for installation.
     */
    public void setRejected(boolean rejected) {
        mRejected = rejected;
    }

    /**
     * Returns flag for package was rejected for installation
     * @return boolean
     */
    public boolean isRejected() {
        return mRejected;
    }

    /**
     * PackageInfos are compared using their "new package" ordering.
     * #param rhs Package to compare
     * @see Package#compareTo(Package)
     */
    @Override
    public int compareTo(PackageInfo rhs) {
        if (mNewPackage != null && rhs != null) {
            return mNewPackage.compareTo(rhs.mNewPackage);
        }
        return -1;
    }

    /**
     * Returns hash code
     */
	@Override
	public int hashCode() {
		return mNewPackage != null ? mNewPackage.hashCode() : super.hashCode();
	}

	/**
	 * Returns true if given object equals this object
	 */
	@Override
	public boolean equals(Object obj) {
		if ((obj == null) || !(obj instanceof PackageInfo))
			return false;
		PackageInfo other = (PackageInfo)obj;
		if (mNewPackage != null)
			return mNewPackage.equals(other.mNewPackage);
		return false;
	}

}
