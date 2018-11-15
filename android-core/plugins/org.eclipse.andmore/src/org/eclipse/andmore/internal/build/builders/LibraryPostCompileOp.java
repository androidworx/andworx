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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Pattern;

import org.eclipse.andmore.AndmoreAndroidPlugin;
import org.eclipse.andmore.internal.build.builders.BaseBuilder.AbortBuildException;
import org.eclipse.andmore.internal.project.AndroidManifestHelper;
import org.eclipse.andmore.internal.project.BaseProjectHelper;
import org.eclipse.andmore.internal.project.LibraryClasspathContainerInitializer;
import org.eclipse.andworx.build.AndworxFactory;
import org.eclipse.andworx.registry.ProjectState;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;

import com.android.SdkConstants;
import com.android.ide.common.xml.ManifestData;
import com.android.sdklib.build.legacy.ApkBuilder;
import com.android.sdklib.build.legacy.ApkCreationException;
import com.android.sdklib.build.legacy.DuplicateFileException;
import com.android.sdklib.build.legacy.IArchiveBuilder;
import com.android.sdklib.build.legacy.SealedApkException;

public class LibraryPostCompileOp implements BuildOp<PostCompilerContext> {
    private static class JarBuilder implements IArchiveBuilder {

        private static Pattern R_PATTERN = Pattern.compile("R(\\$.*)?\\.class"); //$NON-NLS-1$
        private static String BUILD_CONFIG_CLASS = "BuildConfig.class"; //$NON-NLS-1$

        private final byte[] buffer = new byte[1024];
        private final JarOutputStream mOutputStream;
        private final String mAppPackage;

        JarBuilder(JarOutputStream outputStream, String appPackage) {
            mOutputStream = outputStream;
            mAppPackage = appPackage.replace('.', '/');
        }

        public void addFile(IFile file, IFolder rootFolder) throws ApkCreationException {
            // we only package class file from the output folder
            if (SdkConstants.EXT_CLASS.equals(file.getFileExtension()) == false) {
                return;
            }

            IPath packageApp = file.getParent().getFullPath().makeRelativeTo(
                    rootFolder.getFullPath());

            String name = file.getName();
            // Ignore the library's R/Manifest/BuildConfig classes.
            if (mAppPackage.equals(packageApp.toString()) &&
                            (BUILD_CONFIG_CLASS.equals(name) ||
                            R_PATTERN.matcher(name).matches())) {
                return;
            }

            IPath path = file.getFullPath().makeRelativeTo(rootFolder.getFullPath());
            try {
                addFile(file.getContents(), file.getLocalTimeStamp(), path.toString());
            } catch (ApkCreationException e) {
                throw e;
            } catch (Exception e) {
                throw new ApkCreationException(e, "Failed to add %s", file);
            }
        }

        @Override
        public void addFile(File file, String archivePath) throws ApkCreationException,
                SealedApkException, DuplicateFileException {
            try {
                FileInputStream inputStream = new FileInputStream(file);
                long lastModified = file.lastModified();
                addFile(inputStream, lastModified, archivePath);
            } catch (ApkCreationException e) {
                throw e;
            } catch (Exception e) {
                throw new ApkCreationException(e, "Failed to add %s", file);
            }
        }

        private void addFile(InputStream content, long lastModified, String archivePath)
                throws IOException, ApkCreationException {
            // create the jar entry
            JarEntry entry = new JarEntry(archivePath);
            entry.setTime(lastModified);

            try {
                // add the entry to the jar archive
                mOutputStream.putNextEntry(entry);

                // read the content of the entry from the input stream, and write
                // it into the archive.
                int count;
                while ((count = content.read(buffer)) != -1) {
                    mOutputStream.write(buffer, 0, count);
                }
            } finally {
                try {
                    if (content != null) {
                        content.close();
                    }
                } catch (Exception e) {
                    throw new ApkCreationException(e, "Failed to close stream");
                }
            }
        }
    }

	private IFolder androidOutputFolder;
	private final IProgressMonitor monitor;

	public LibraryPostCompileOp(IFolder androidOutputFolder, IProgressMonitor monitor) {
		this.androidOutputFolder = androidOutputFolder;
		this.monitor = monitor;
	}
	
	@Override
	public boolean execute(PostCompilerContext context) throws CoreException, AbortBuildException {
        IProject project = context.getProject();
        boolean convertToDex = context.isConvertToDex();
        IFile jarFile = null;
        if (!convertToDex) {
            // Check the jar output file is present, if not create it.
             jarFile = getJarFile(project);
        	if (!jarFile.exists()) {
                convertToDex = true;
            }
         }

/*      // Update the crunch cache always since aapt does it smartly only
        // on the files that need it.
        if (DEBUG_LOG) {
            AndmoreAndroidPlugin.log(IStatus.INFO, "%s running crunch!", project.getName());
        }
        // Crunch now performed on merge resources
        BuildHelper helper = new BuildHelper(
                projectState,
                buildToolInfo,
                mOutStream, mErrStream,
                true, // debugMode
                adtPrefs.getBuildVerbosity() == BuildVerbosity.VERBOSE,
                mResourceMarker);
        updateCrunchCache(project, helper);
        // refresh recursively bin/res folder
        resOutputFolder.refreshLocal(IResource.DEPTH_INFINITE, monitor);
*/

        if (convertToDex) { // In this case this means some class files changed and
                            // we need to update the jar file.
            if (context.isDebugLog()) {
                AndmoreAndroidPlugin.log(IStatus.INFO, "%s updating jar!", project.getName());
            }

            // Resource to the AndroidManifest.xml file
            IFile manifestFile = project.getFile(SdkConstants.FN_ANDROID_MANIFEST_XML);
            ManifestData manifestData = AndroidManifestHelper.parseForData(manifestFile);
            String appPackage = manifestData.getPackage();

            IFolder javaOutputFolder = BaseProjectHelper.getJavaOutputFolder(project);
            if (jarFile == null)
            	jarFile = getJarFile(project);

            writeLibraryPackage(jarFile, project, appPackage, javaOutputFolder);
            context.saveConvertToDex(false);

            // refresh the bin folder content with no recursion to update the library
            // jar file.
            androidOutputFolder.refreshLocal(IResource.DEPTH_ONE, monitor);

            // Also update the projects. The only way to force recompile them is to
            // reset the library container.
            ProjectState projectState = AndworxFactory.instance().getProjectState(project);
            List<ProjectState> parentProjects = projectState.getParentProjects();
            LibraryClasspathContainerInitializer.updateProject(parentProjects);
        }
		return true;
	}

	@Override
	public void commit(PostCompilerContext context) throws IOException {
	}

	@Override
	public String getDescription() {
		return "Library post compile";
	}

	private IFile getJarFile(IProject project) {
		return androidOutputFolder.getFile(
                project.getName().toLowerCase() + SdkConstants.DOT_JAR);
	}

    /**
     * Writes the library jar file.
     * @param jarIFile the destination file
     * @param project the library project
     * @param appPackage the library android package
     * @param javaOutputFolder the JDT output folder.
     */
    private void writeLibraryPackage(IFile jarIFile, IProject project, String appPackage,
            IFolder javaOutputFolder) {

        JarOutputStream jos = null;
        try {
            Manifest manifest = new Manifest();
            Attributes mainAttributes = manifest.getMainAttributes();
            mainAttributes.put(Attributes.Name.CLASS_PATH, "Android ADT"); //$NON-NLS-1$
            mainAttributes.putValue("Created-By", "1.0 (Android)"); //$NON-NLS-1$  //$NON-NLS-2$
            jos = new JarOutputStream(
                    new FileOutputStream(jarIFile.getLocation().toFile()), manifest);

            JarBuilder jarBuilder = new JarBuilder(jos, appPackage);

            // write the class files
            writeClassFilesIntoJar(jarBuilder, javaOutputFolder, javaOutputFolder);

            // now write the standard Java resources from the output folder
            ApkBuilder.addSourceFolder(jarBuilder, javaOutputFolder.getLocation().toFile());
        } catch (Exception e) {
            AndmoreAndroidPlugin.log(e, "Failed to write jar file %s", jarIFile.getLocation().toOSString());
        } finally {
            if (jos != null) {
                try {
                    jos.close();
                } catch (IOException e) {
                    // pass
                }
            }
        }
    }

    private void writeClassFilesIntoJar(JarBuilder builder, IFolder folder, IFolder rootFolder)
            throws CoreException, IOException, ApkCreationException {
        IResource[] members = folder.members();
        for (IResource member : members) {
            if (member.getType() == IResource.FOLDER) {
                writeClassFilesIntoJar(builder, (IFolder) member, rootFolder);
            } else if (member.getType() == IResource.FILE) {
                IFile file = (IFile) member;
                builder.addFile(file, rootFolder);
            }
        }
    }

}
