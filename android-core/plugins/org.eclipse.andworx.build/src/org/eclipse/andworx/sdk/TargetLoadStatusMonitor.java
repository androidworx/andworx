package org.eclipse.andworx.sdk;

import com.android.ide.common.sdk.LoadStatus;

/**
 * Interface for object which tracks Target Data Load Status
 */
public interface TargetLoadStatusMonitor {

	/**
	 * Returns Target Data Load Status for target specified by hashstring
	 * @param hashString
	 * @return {@link LoadStatus} enum
	 */
	LoadStatus getLoadStatus(String hashString);

}