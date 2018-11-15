package org.eclipse.andmore.internal.build.builders;

import java.io.File;
import java.io.IOException;

import org.eclipse.andmore.AndmoreAndroidConstants;
import org.eclipse.andmore.AndmoreAndroidPlugin;
import org.eclipse.andmore.internal.build.Messages;
import org.eclipse.andmore.internal.build.builders.BaseBuilder.AbortBuildException;
import org.eclipse.andmore.internal.preferences.AdtPrefs.BuildVerbosity;
import org.eclipse.andmore.internal.project.BaseProjectHelper;
import org.eclipse.andmore.internal.project.ProjectHelper;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;

import com.android.SdkConstants;
import com.android.utils.FileUtils;

public class MonitorResourcesOp implements BuildOp<PostCompilerContext> {

	private IFolder androidOutputFolder;
	private final IProgressMonitor monitor;

	public MonitorResourcesOp(IFolder androidOutputFolder, IProgressMonitor monitor) {
		this.androidOutputFolder = androidOutputFolder;
		this.monitor = monitor;
	}
	
	@Override
	public boolean execute(PostCompilerContext context) throws CoreException, AbortBuildException {
		IProject project = context.getProject();
        // Do some extra check, in case the output files are not present. This
        // will force to recreate them.
        IResource tmp = null;
        boolean convertToDex = context.isConvertToDex();
        // Check classes.dex is present. If not we force to recreate it.
        if (!convertToDex) {
            tmp = androidOutputFolder.findMember(SdkConstants.FN_APK_CLASSES_DEX);
            if (tmp == null || !tmp.exists()) {
                convertToDex = true;
            }
        }
        boolean buildFinalPackage = context.isBuildFinalPackage();
        // Check the final file(s)
        String finalPackageName = ProjectHelper.getApkFilename(project, null /*config*/);
        if (!buildFinalPackage) {
            tmp = androidOutputFolder.findMember(finalPackageName);
            if (tmp == null || (tmp instanceof IFile &&
                    tmp.exists() == false)) {
                String msg = String.format(Messages.s_Missing_Repackaging, finalPackageName);
                AndmoreAndroidPlugin.printBuildToConsole(BuildVerbosity.VERBOSE, project, msg);
                buildFinalPackage = true;
            }
        }

        // At this point we know if we need to recreate the temporary apk
        // or the dex file, but we don't know if we simply need to recreate them
        // because they are missing

        // Refresh the output directory first
        IContainer ic = androidOutputFolder.getParent();
        if (ic != null) {
            ic.refreshLocal(IResource.DEPTH_ONE, monitor);
        }

        // Test all three flags, as we may need to make the final package
        // but not the intermediary ones.
        if (convertToDex || buildFinalPackage) {

            IPath androidBinLocation = androidOutputFolder.getLocation();
            if (androidBinLocation == null) {
               context. markProject(AndmoreAndroidConstants.MARKER_PACKAGING, Messages.Output_Missing,
                        IMarker.SEVERITY_ERROR);
                return false;
            }
            String osAndroidBinPath = androidBinLocation.toOSString();

            // Check for generated manifest
            File manifestFile = new File(generatedManifestLocation(context), SdkConstants.FN_ANDROID_MANIFEST_XML);
            if (!manifestFile.exists()) {
                // mark project and exit
                String msg = String.format(Messages.s_File_Missing,
                        SdkConstants.FN_ANDROID_MANIFEST_XML);
                context.markProject(AndmoreAndroidConstants.MARKER_PACKAGING, msg, IMarker.SEVERITY_ERROR);
                return false;
            }
            // Remove the old .apk.
            // This make sure that if the apk is corrupted, then dx (which would attempt
            // to open it), will not fail.
            String osFinalPackagePath = osAndroidBinPath + File.separator + finalPackageName;
            File finalPackage = new File(osFinalPackagePath);

            // If delete failed, this is not really a problem, as the final package generation
            // handle already present .apk, and if that one failed as well, the user will be
            // notified.
            finalPackage.delete();
        }
	    context.setConvertToDex(convertToDex);
	    context.setBuildFinalPackage(buildFinalPackage);
		return true;
	}

	@Override
	public void commit(PostCompilerContext context) throws IOException {
        // Copy built manifest file to project now as it is deleted if copied prior
    	IFolder projectFolder = BaseProjectHelper.getAndroidOutputFolder(context.getProject());
		File destinationDir = projectFolder.getLocation().toFile();
		File oldFile = new File(destinationDir, SdkConstants.FN_ANDROID_MANIFEST_XML);
		File manifestOutputDir = generatedManifestLocation(context);
		File newFile = new File(manifestOutputDir, SdkConstants.FN_ANDROID_MANIFEST_XML);
		if (oldFile.exists())
			oldFile.delete();
		else if (!destinationDir.exists() && !destinationDir.mkdirs())
			throw new IOException("Failed to create path " + destinationDir.toString());
		FileUtils.copyFile(newFile, oldFile);
	}

	@Override
	public String getDescription() {
		return "Monitor post compile resources status";
	}

	private File generatedManifestLocation(PostCompilerContext context) {
		return context.getVariantContext().getManifestOutputDirectory();
	}
}
