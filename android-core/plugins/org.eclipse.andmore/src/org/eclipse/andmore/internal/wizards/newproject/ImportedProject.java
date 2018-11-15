package org.eclipse.andmore.internal.wizards.newproject;

import java.io.File;

import org.eclipse.andworx.polyglot.AndroidConfigurationBuilder;
import org.eclipse.andworx.project.ProjectProfile;

import com.android.ide.common.xml.ManifestData;

public interface ImportedProject {

	String getProjectName();
	File getLocation();
	ProjectProfile getProjectProfile();
	ManifestData getManifest();
	String getRelativePath();
	String getSourceFolder();
	AndroidConfigurationBuilder getAndroidConfig();
}