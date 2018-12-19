package org.eclipse.andworx.test;

import java.io.File;

import org.eclipse.andworx.sdk.AndroidSdkPreferences;
import org.eclipse.jface.util.IPropertyChangeListener;

public class TestAndroidSdkPreferences implements AndroidSdkPreferences {

	@Override
	public File getLastSdkPath() {
		return null;
	}

	@Override
	public File getSdkLocation() {
		return null;
	}

	@Override
	public void setSdkLocation(File location) {

	}

	@Override
	public boolean isSdkSpecified() {
		return false;
	}

	@Override
	public void addPropertyChangeListener(IPropertyChangeListener listener) {

	}

	@Override
	public String getSdkLocationValue() {
		return null;
	}

	@Override
	public boolean save() {
		return false;
	}

}
