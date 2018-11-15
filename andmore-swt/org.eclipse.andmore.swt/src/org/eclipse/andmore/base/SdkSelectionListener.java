/**
 * 
 */
package org.eclipse.andmore.base;

import java.io.File;

/**
 * Callback for user selection of SDK changed
 * @author andrew
 *
 */
public interface SdkSelectionListener {
	void onSdkSelectionChange(File newSdkLocation);
	void onSelectionError(String message);
}
