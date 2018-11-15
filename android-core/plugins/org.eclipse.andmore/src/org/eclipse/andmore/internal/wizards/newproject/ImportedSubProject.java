/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.eclipse.andmore.internal.wizards.newproject;

import static com.android.SdkConstants.ATTR_NAME;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.andmore.AndmoreAndroidPlugin;
import org.eclipse.andmore.internal.editors.layout.gle2.DomUtilities;
import org.eclipse.andworx.polyglot.AndroidConfigurationBuilder;
import org.eclipse.andworx.project.Identity;
import org.eclipse.andworx.project.ProjectProfile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.xml.AndroidManifestParser;
import com.android.ide.common.xml.ManifestData;
import com.android.ide.common.xml.ManifestData.Activity;
import com.google.common.base.Charsets;
import com.google.common.io.Files;

/** An Android project to be imported */
public class ImportedSubProject implements ImportedProject {
    private final File mLocation;
    private String mActivityName;
    private ManifestData mManifest;
    private String mProjectName;
    private String mRelativePath;
    private ProjectProfile profile;

    public ImportedSubProject(File location, String relativePath) {
        super();
        mLocation = location;
        mRelativePath = relativePath;
    }

    @Override
    public File getLocation() {
        return mLocation;
    }

    @Override
    public String getRelativePath() {
        return mRelativePath;
    }

    @Override
    @Nullable
    public ManifestData getManifest() {
        if (mManifest == null) {
            try {
            	File xmlFile = new File(mLocation, SdkConstants.FN_ANDROID_MANIFEST_XML);
            	Path path = Paths.get(xmlFile.toURI());
                mManifest = AndroidManifestParser.parse(path);
            } catch (Exception e) {
                AndmoreAndroidPlugin.log(e, null);
                return null;
            }
        }

        return mManifest;
    }

    @Override
    public ProjectProfile getProjectProfile() {
    	if (profile == null) {
    		// TODO - Fix if this class is resurrected
        	profile = new ProjectProfile(new Identity("","",""));
    	}
    	return profile;
    }
    
    @Nullable
    public String getActivityName() {
        if (mActivityName == null) {
            // Compute the project name and the package name from the manifest
            ManifestData manifest = getManifest();
            if (manifest != null) {
                if (manifest.getLauncherActivity() != null) {
                    mActivityName = manifest.getLauncherActivity().getName();
                }
                if (mActivityName == null || mActivityName.isEmpty()) {
                    Activity[] activities = manifest.getActivities();
                    for (Activity activity : activities) {
                        mActivityName = activity.getName();
                        if (mActivityName != null && !mActivityName.isEmpty()) {
                            break;
                        }
                    }
                }
                if (mActivityName != null) {
                    int index = mActivityName.lastIndexOf('.');
                    mActivityName = mActivityName.substring(index + 1);
                }
            }
        }

        return mActivityName;
    }

    /* (non-Javadoc)
	 * @see org.eclipse.andmore.internal.wizards.newproject.ImportedProject#getProjectName()
	 */
    @Override
	@NonNull
    public String getProjectName() {
        if (mProjectName == null) {
            // Are we importing an Eclipse project? If so just use the existing project name
            mProjectName = findEclipseProjectName();
            if (mProjectName != null) {
                return mProjectName;
            }

            String activityName = getActivityName();
            if (activityName == null || activityName.isEmpty()) {
                // I could also look at the build files, say build.xml from ant, and
                // try to glean the project name from there
                mProjectName = mLocation.getName();
            } else {
                // Try to derive it from the activity name:
                IWorkspace workspace = ResourcesPlugin.getWorkspace();
                IStatus nameStatus = workspace.validateName(activityName, IResource.PROJECT);
                if (nameStatus.isOK()) {
                    mProjectName = activityName;
                } else {
                    // Try to derive it by escaping characters
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0, n = activityName.length(); i < n; i++) {
                        char c = activityName.charAt(i);
                        if (c != IPath.DEVICE_SEPARATOR && c != IPath.SEPARATOR && c != '\\') {
                            sb.append(c);
                        }
                    }
                    if (sb.length() == 0) {
                        mProjectName = mLocation.getName();
                    } else {
                        mProjectName = sb.toString();
                    }
                }
            }
        }

        return mProjectName;
    }

	@Override
	public AndroidConfigurationBuilder getAndroidConfig() {
		return null;
	}
	@Override
	public String getSourceFolder() {
		return SdkConstants.FD_SOURCES;
	}
	
    @Nullable
    private String findEclipseProjectName() {
        File projectFile = new File(mLocation, ".project"); //$NON-NLS-1$
        if (projectFile.exists()) {
            String xml;
            try {
                xml = Files.asCharSource(projectFile, Charsets.UTF_8).read();
                Document doc = DomUtilities.parseDocument(xml, false);
                if (doc != null) {
                    NodeList names = doc.getElementsByTagName(ATTR_NAME);
                    if (names.getLength() >= 1) {
                        Node nameElement = names.item(0);
                        String name = nameElement.getTextContent().trim();
                        if (!name.isEmpty()) {
                            return name;
                        }
                    }
                }
            } catch (IOException e) {
                // pass: don't attempt to read project name; must be some sort of unrelated
                // file with the same name, perhaps from a different editor or IDE
            }
        }

        return null;
    }

    public void setProjectName(@NonNull String newName) {
        mProjectName = newName;
    }


}