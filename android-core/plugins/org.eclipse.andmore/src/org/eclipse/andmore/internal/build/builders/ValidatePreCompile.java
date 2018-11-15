/*
 * Copyright (C) 2007 The Android Open Source Project
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
package org.eclipse.andmore.internal.build.builders;

import java.io.File;
import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;

import org.eclipse.andmore.AndmoreAndroidConstants;
import org.eclipse.andmore.AndmoreAndroidPlugin;
import org.eclipse.andmore.internal.build.Messages;
import org.eclipse.andmore.internal.build.builders.BaseBuilder.AbortBuildException;
import org.eclipse.andmore.internal.preferences.AdtPrefs.BuildVerbosity;
import org.eclipse.andmore.internal.project.AndroidManifestHelper;
import org.eclipse.andmore.internal.project.BaseProjectHelper;
import org.eclipse.andmore.internal.project.FixLaunchConfig;
import org.eclipse.andmore.internal.project.XmlErrorHandler.BasicXmlErrorListener;
import org.eclipse.andworx.build.AndworxFactory;
import org.eclipse.andworx.model.CodeSource;
import org.eclipse.andworx.registry.ProjectState;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.xml.sax.SAXException;

import com.android.SdkConstants;
import com.android.ide.common.xml.ManifestData;
import com.android.io.FileWrapper;
import com.android.io.StreamException;
import com.android.repository.Revision;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.xml.AndroidManifest;

public class ValidatePreCompile {

	private PreCompilerContext builderContext;
	private IProgressMonitor monitor;
	private int minSdkValue = -1;
	private PreparePreCompile preparePreCompile;
    private String javaPackage;
    private String minSdkVersion;


    public ValidatePreCompile(PreCompilerContext builderContext, PreparePreCompile preparePreCompile, IProgressMonitor monitor) {
    	this.builderContext = builderContext;
    	this.preparePreCompile = preparePreCompile;
    	this.monitor = monitor;
    	javaPackage = preparePreCompile.getJavaPackage();
    	minSdkVersion = preparePreCompile.getMinSdkVersion();
    }
    
	public int getMinSdkValue() {
		return minSdkValue;
	}

	public String getJavaPackage() {
		return javaPackage;
	}

	public String getMinSdkVersion() {
		return minSdkVersion;
	}

	public boolean isProjectValid() throws AbortBuildException, CoreException {
		IProject project = builderContext.getProject();
		BuildToolInfo buildToolInfo = builderContext.getBuildToolInfo();
        // If there was some XML errors, we just return w/o doing
        // anything since we've put some markers in the files anyway.
		PreCompilerDeltaVisitor dv = preparePreCompile.getPreCompilerDeltaVisitor();
        if (dv != null && dv.mXmlError) {
            AndmoreAndroidPlugin.printErrorToConsole(project, Messages.Xml_Error);
            return false;
        }
        ProjectState projectState = AndworxFactory.instance().getProjectState(project);
        if (projectState.getRenderScriptSupportMode()) {
            Revision minBuildToolsRev = new Revision(19,0,3);
            if (buildToolInfo.getRevision().compareTo(minBuildToolsRev) == -1) {
                String msg = "RenderScript support mode requires Build-Tools 19.0.3 or later.";
                AndmoreAndroidPlugin.printErrorToConsole(project, msg);
                builderContext.markProject(AndmoreAndroidConstants.MARKER_ADT, msg, IMarker.SEVERITY_ERROR);
                return false;
            }
        }

        // Get the manifest file
    	File manifestPath = projectState.getAndworxProject().getDefaultConfig().getSourceProvider().getManifestFile();
    	IFile manifestFile = project.getFile(projectState.getProjectSourceFolder(CodeSource.manifest));

        if ((manifestPath == null) || !manifestPath.exists() || !manifestPath.isFile()){
            String msg = String.format(Messages.s_File_Missing,
                    SdkConstants.FN_ANDROID_MANIFEST_XML);
            AndmoreAndroidPlugin.printErrorToConsole(project, msg);
            builderContext.markProject(AndmoreAndroidConstants.MARKER_ADT, msg, IMarker.SEVERITY_ERROR);
            return false;

            // TODO: document whether code below that uses manifest (which is now guaranteed
            // to be null) will actually be executed or not.
        }

        // lets check the XML of the manifest first, if that hasn't been done by the
        // resource delta visitor yet.
        if (dv == null || dv.getCheckedManifestXml() == false) {
            BasicXmlErrorListener errorListener = new BasicXmlErrorListener();
            try {
                ManifestData parser = AndroidManifestHelper.parseUnchecked(
                        new FileWrapper(manifestPath),
                        true /*gather data*/,
                        errorListener);

                if (errorListener.mHasXmlError == true) {
                    // There was an error in the manifest, its file has been marked
                    // by the XmlErrorHandler. The stopBuild() call below will abort
                    // this with an exception.
                    String msg = String.format(Messages.s_Contains_Xml_Error,
                            SdkConstants.FN_ANDROID_MANIFEST_XML);
                    AndmoreAndroidPlugin.printBuildToConsole(BuildVerbosity.VERBOSE, project, msg);
                    builderContext.markProject(AndmoreAndroidConstants.MARKER_ADT, msg, IMarker.SEVERITY_ERROR);

                    return false;
                }

                // Get the java package from the parser.
                // This can be null if the parsing failed because the resource is out of sync,
                // in which case the error will already have been logged anyway.
                if (parser != null) {
                    javaPackage = parser.getPackage();
                    minSdkVersion = parser.getMinSdkVersionString();
                }
            } catch (StreamException e) {
            	builderContext.handleStreamException(e);

                return false;
            } catch (ParserConfigurationException e) {
                String msg = String.format(
                        "Bad parser configuration for %s: %s",
                        manifestPath.toString(),
                        e.getMessage());

                builderContext.handleException(e, msg);
                return false;

            } catch (SAXException e) {
                String msg = String.format(
                        "Parser exception for %s: %s",
                        manifestPath.toString(),
                        e.getMessage());

                builderContext.handleException(e, msg);
                return false;
            } catch (IOException e) {
                String msg = String.format(
                        "I/O error for %s: %s",
                        manifestPath.toString(),
                        e.getMessage());

                builderContext.handleException(e, msg);
                return false;
            }
        }

        IAndroidTarget projectTarget = projectState.getProfile().getTarget();
        if (minSdkVersion != null) {
            try {
                minSdkValue = Integer.parseInt(minSdkVersion);
            } catch (NumberFormatException e) {
                // it's ok, it means minSdkVersion contains a (hopefully) valid codename.
            }

            AndroidVersion targetVersion = projectTarget.getVersion();

            // remove earlier marker from the manifest
            builderContext.removeMarkersFromResource(manifestFile, AndmoreAndroidConstants.MARKER_ADT);

            if (minSdkValue != -1) {
                String codename = targetVersion.getCodename();
                if (codename != null) {
                    // integer minSdk when the target is a preview => fatal error
                    String msg = String.format(
                            "Platform %1$s is a preview and requires application manifest to set %2$s to '%1$s'",
                            codename, AndroidManifest.ATTRIBUTE_MIN_SDK_VERSION);
                    AndmoreAndroidPlugin.printErrorToConsole(project, msg);
                    BaseProjectHelper.markResource(manifestFile, AndmoreAndroidConstants.MARKER_ADT,
                            msg, IMarker.SEVERITY_ERROR);
                    return false;
                } else if (minSdkValue > targetVersion.getApiLevel()) {
                    // integer minSdk is too high for the target => warning
                    String msg = String.format(
                            "Attribute %1$s (%2$d) is higher than the project target API level (%3$d)",
                            AndroidManifest.ATTRIBUTE_MIN_SDK_VERSION,
                            minSdkValue, targetVersion.getApiLevel());
                    AndmoreAndroidPlugin.printBuildToConsole(BuildVerbosity.VERBOSE, project, msg);
                    BaseProjectHelper.markResource(manifestFile, AndmoreAndroidConstants.MARKER_ADT,
                            msg, IMarker.SEVERITY_WARNING);
                }
            } else {
                // looks like the min sdk is a codename, check it matches the codename
                // of the platform
                String codename = targetVersion.getCodename();
                if (codename == null) {
                    // platform is not a preview => fatal error
                    String msg = String.format(
                            "Manifest attribute '%1$s' is set to '%2$s'. Integer is expected.",
                            AndroidManifest.ATTRIBUTE_MIN_SDK_VERSION, minSdkVersion);
                    AndmoreAndroidPlugin.printErrorToConsole(project, msg);
                    BaseProjectHelper.markResource(manifestFile, AndmoreAndroidConstants.MARKER_ADT,
                            msg, IMarker.SEVERITY_ERROR);
                    return false;
                } else if (codename.equals(minSdkVersion) == false) {
                    // platform and manifest codenames don't match => fatal error.
                    String msg = String.format(
                            "Value of manifest attribute '%1$s' does not match platform codename '%2$s'",
                            AndroidManifest.ATTRIBUTE_MIN_SDK_VERSION, codename);
                    AndmoreAndroidPlugin.printErrorToConsole(project, msg);
                    BaseProjectHelper.markResource(manifestFile, AndmoreAndroidConstants.MARKER_ADT,
                            msg, IMarker.SEVERITY_ERROR);
                    return false;
                }

                // if we get there, the minSdkVersion is a codename matching the target
                // platform codename. In this case we set minSdkValue to the previous API
                // level, as it's used by source processors.
                minSdkValue = targetVersion.getApiLevel();
            }
        } else if (projectTarget.getVersion().isPreview()) {
            // else the minSdkVersion is not set but we are using a preview target.
            // Display an error
            String codename = projectTarget.getVersion().getCodename();
            String msg = String.format(
                    "Platform %1$s is a preview and requires application manifests to set %2$s to '%1$s'",
                    codename, AndroidManifest.ATTRIBUTE_MIN_SDK_VERSION);
            AndmoreAndroidPlugin.printErrorToConsole(project, msg);
            BaseProjectHelper.markResource(manifestFile, AndmoreAndroidConstants.MARKER_ADT, msg,
                    IMarker.SEVERITY_ERROR);
            return false;
        }

        if (javaPackage == null || javaPackage.isEmpty()) {
            // looks like the AndroidManifest file isn't valid.
            String msg = String.format(Messages.s_Doesnt_Declare_Package_Error,
                    SdkConstants.FN_ANDROID_MANIFEST_XML);
            AndmoreAndroidPlugin.printErrorToConsole(project, msg);
            BaseProjectHelper.markResource(manifestFile, AndmoreAndroidConstants.MARKER_ADT,
                    msg, IMarker.SEVERITY_ERROR);

            return false;
        } else if (javaPackage.indexOf('.') == -1) {
            // The application package name does not contain 2+ segments!
            String msg = String.format(
                    "Application package '%1$s' must have a minimum of 2 segments.",
                    SdkConstants.FN_ANDROID_MANIFEST_XML);
            AndmoreAndroidPlugin.printErrorToConsole(project, msg);
            BaseProjectHelper.markResource(manifestFile, AndmoreAndroidConstants.MARKER_ADT,
                    msg, IMarker.SEVERITY_ERROR);

            return false;
        }
        
        String manifestPackage = builderContext.getManifestPackage();
        // at this point we have the java package. We need to make sure it's not a different
        // package than the previous one that were built.
        if (!javaPackage.equals(manifestPackage)) {
            // The manifest package has changed, the user may want to update
            // the launch configuration
            if (manifestPackage != null) {
                AndmoreAndroidPlugin.printBuildToConsole(BuildVerbosity.VERBOSE, project,
                        Messages.Checking_Package_Change);

                FixLaunchConfig flc = new FixLaunchConfig(project, manifestPackage, javaPackage);
                flc.start();
            }

            // record the new manifest package, and save it.
            builderContext.saveManifestPackage(javaPackage);
            builderContext.cleanProject(monitor);
        }
        return true;
	}
}
