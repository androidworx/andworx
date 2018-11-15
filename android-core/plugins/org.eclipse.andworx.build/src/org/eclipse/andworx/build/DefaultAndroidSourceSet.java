/*
 * Copyright (C) 2018 The Android Open Source Project
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
package org.eclipse.andworx.build;

import java.io.File;
import java.util.Collection;
import java.util.List;

import com.android.annotations.Nullable;
import org.eclipse.andworx.file.AndroidSourceSet;
import org.eclipse.andworx.model.CodeSource;
import org.eclipse.andworx.model.ProjectSourceProvider;
import org.eclipse.andworx.model.SourcePath;

/**
 * 
 * @author andrew
 *
 */
/*
      * Encapsulates source set configurations for all variants.
     *
     * <p>The Android plugin looks for your project's source code and resources in groups of
     * directories called <i><a
     * href="https://developer.android.com/studio/build/index.html#sourcesets">source sets</a></i>.
     * Each source set also determines the scope of build outputs that should consume its code and
     * resources. For example, when creating a new project from Android Studio, the IDE creates
     * directories for a <code>main/</code> source set that contains the code and resources you want
     * to share between all your build variants.
     *
     * <p>You can then define basic functionality in the <code>main/</code> source set, but use
     * product flavor source sets to change only the branding of your app between different clients,
     * or include special permissions and logging functionality to only "debug" versions of your
     * app.
     *
     * <p>The Android plugin expects you to organize files for source set directories a certain way,
     * similar to the <code>main/</code> source set. For example, Gradle expects Java class files
     * that are specific to your "debug" build type to be located in the <code>src/debug/java/
     * </code> directory.
     *
     * <p>Gradle provides a useful task to shows you how to organize your files for each build
     * type-, product flavor-, and build variant-specific source set. you can run this task from the
     * command line as follows:
     *
     * <pre>./gradlew sourceSets</pre>
     *
     * <p>The following sample output describes where Gradle expects to find certain files for the
     * "debug" build type:
     *
     * <pre>
     * ------------------------------------------------------------
     * Project :app
     * ------------------------------------------------------------
     *
     * ...
     *
     * debug
     * ----
     * Compile configuration: compile
     * build.gradle name: android.sourceSets.debug
     * Java sources: [app/src/debug/java]
     * Manifest file: app/src/debug/AndroidManifest.xml
     * Android resources: [app/src/debug/res]
     * Assets: [app/src/debug/assets]
     * AIDL sources: [app/src/debug/aidl]
     * RenderScript sources: [app/src/debug/rs]
     * JNI sources: [app/src/debug/jni]
     * JNI libraries: [app/src/debug/jniLibs]
     * Java-style resources: [app/src/debug/resources]
     * </pre>
     *
     * <p>If you have sources that are not organized into the default source set directories that
     * Gradle expects, as described in the sample output above, you can use the <code>sourceSet
     * </code> block to change where Gradle looks to gather files for each component of a given
     * source set. You don't need to relocate the files; you only need to provide Gradle with the
     * path(s), relative to the module-level <code>build.gradle</code> file, where Gradle should
     * expect to find files for each source set component.

     * <p>The following code sample maps sources from the <code>app/other/</code> directory to
     * certain components of the <code>main</code> source set and changes the root directory of the
     * <code>androidTest</code> source set:
     *
     * <pre>
     * android {
     *   ...
     *   sourceSets {
     *     // Encapsulates configurations for the main source set.
     *     main {
     *         // Changes the directory for Java sources. The default directory is
     *         // 'src/main/java'.
     *         java.srcDirs = ['other/java']
     *
     *         // If you list multiple directories, Gradle uses all of them to collect
     *         // sources. Because Gradle gives these directories equal priority, if
     *         // you define the same resource in more than one directory, you get an
     *         // error when merging resources. The default directory is 'src/main/res'.
     *         res.srcDirs = ['other/res1', 'other/res2']
     *
     *         // Note: You should avoid specifying a directory which is a parent to one
     *         // or more other directories you specify. For example, avoid the following:
     *         // res.srcDirs = ['other/res1', 'other/res1/layouts', 'other/res1/strings']
     *         // You should specify either only the root 'other/res1' directory, or only the
     *         // nested 'other/res1/layouts' and 'other/res1/strings' directories.
     *
     *         // For each source set, you can specify only one Android manifest.
     *         // By default, Android Studio creates a manifest for your main source
     *         // set in the src/main/ directory.
     *         manifest.srcFile 'other/AndroidManifest.xml'
     *         ...
     *     }
     *
     *     // Create additional blocks to configure other source sets.
     *     androidTest {
     *         // If all the files for a source set are located under a single root
     *         // directory, you can specify that directory using the setRoot property.
     *         // When gathering sources for the source set, Gradle looks only in locations
     *         // relative to the root directory you specify. For example, after applying the
     *         // configuration below for the androidTest source set, Gradle looks for Java
     *         // sources only in the src/tests/java/ directory.
     *         setRoot 'src/tests'
     *         ...
     *     }
     *   }
     * }
     * </pre>
     *
 */
public class DefaultAndroidSourceSet implements ProjectSourceProvider {

	/** Source specifications grouped by compilation type */
    private final AndroidSourceSet androidSourceSet;

    /**
     * Construct DefaultAndroidSourceSet object
     * @param androidSourceSet Object containing source specifications grouped by compilation type
     */
	public DefaultAndroidSourceSet(AndroidSourceSet androidSourceSet) {
		this.androidSourceSet = androidSourceSet;
	}

	/**
	 * Returns source specifications grouped by compilation type
	 * @return AndroidSourceSet object
	 */
	public AndroidSourceSet getAndroidSourceSet() {
		return androidSourceSet;
	}

	/**
	 * Returns source set name 
	 * @return name
	 */
	@Override
	public String getName() {
		return androidSourceSet.getName();
	}

	/**
	 * Returns Android manifest
	 * @return File object
	 */
	@Override
	public @Nullable File getManifestFile() {
		return androidSourceSet.getManifest();
	}

	/**
	 * Returns Java source folders
	 * @return collection of File object
	 */
	@Override
	public Collection<File> getJavaDirectories() {
		return androidSourceSet.getDirectorySet(CodeSource.javaSource).getSrcDirs();
	}

	/**
	 * Returns Java resources folders
	 * @return collection of File object
	 */
	@Override
	public Collection<File> getResourcesDirectories() {
		return androidSourceSet.getDirectorySet(CodeSource.javaResources).getSrcDirs();
	}

	/**
	 * Returns aidl folders
	 * @return collection of File object
	 */
	@Override
	public Collection<File> getAidlDirectories() {
		return androidSourceSet.getDirectorySet(CodeSource.aidl).getSrcDirs();
	}

	/**
	 * Returns renderscript folders
	 * @return collection of File object
	 */
	@Override
	public Collection<File> getRenderscriptDirectories() {
		return androidSourceSet.getDirectorySet(CodeSource.renderscript).getSrcDirs();
	}

	/**
	 * Returns C source folders
	 * @return collection of File object
	 */
	@Override
	public Collection<File> getCDirectories() {
		return androidSourceSet.getDirectorySet(CodeSource.jni).getSrcDirs();
	}

	/**
	 * Returns C++ source folders
	 * @return collection of File object
	 */
	@Override
	public Collection<File> getCppDirectories() {
		// The C and C++ directories are currently the same.
		return androidSourceSet.getDirectorySet(CodeSource.jni).getSrcDirs();
	}

	/**
	 * Returns android resources folders
	 * @return collection of File object
	 */
	@Override
	public Collection<File> getResDirectories() {
		return androidSourceSet.getDirectorySet(CodeSource.res).getSrcDirs();
	}

	/**
	 * Returns  android assets folders
	 * @return collection of File object
	 */
	@Override
	public Collection<File> getAssetsDirectories() {
		return androidSourceSet.getDirectorySet(CodeSource.assets).getSrcDirs();
	}

	/**
	 * Returns  native libs folders
	 * @return collection of File object
	 */
	@Override
	public Collection<File> getJniLibsDirectories() {
		return androidSourceSet.getDirectorySet(CodeSource.jniLibs).getSrcDirs();
	}

	/**
	 * Returns shader folders
	 * @return collection of File object
	 */
	@Override
	public Collection<File> getShadersDirectories() {
		return androidSourceSet.getDirectorySet(CodeSource.shaders).getSrcDirs();
	}

	/**
	 * Returns paths relative to project root for given CodeSource enum
	 * @return list of SourcePath objects
	 */
	@Override
	public List<SourcePath> getSourcePathList(CodeSource codeSource) {
		return androidSourceSet.getSourcePathList(codeSource);
	}

}
