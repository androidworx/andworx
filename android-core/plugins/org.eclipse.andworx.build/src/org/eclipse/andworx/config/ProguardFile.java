/*
 * Copyright (C) 2018 The Android Open Source Project
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
package org.eclipse.andworx.config;

import java.io.File;

import org.eclipse.andworx.file.FileManager;
import org.eclipse.andworx.project.AndworxProject;

import com.android.annotations.NonNull;
import com.android.builder.model.AndroidProject;
import com.android.utils.FileUtils;

/**
 * Default ProGuard files generation and copying
 */
public class ProguardFile {
	
	/** Default Proguard file options and their associated filenames */
    public enum DefaultProguardFile {
        /** Default when not using the "postProcessing" DSL block. */
        DONT_OPTIMIZE("proguard-android.txt"),

        /** Variant of the above which does not disable optimizations. */
        OPTIMIZE("proguard-android-optimize.txt"),

        /**
         * Does not disable any actions, includes optimizations config. To be used with the new
         * "postProcessing" DSL block.
         */
        NO_ACTIONS("proguard-defaults.txt"),
        ;

        @NonNull public final String fileName;

        DefaultProguardFile(@NonNull String fileName) {
            this.fileName = fileName;
        }

        public static DefaultProguardFile forFilename(String fileName) {
	    	for (DefaultProguardFile proguardFile: values())
	    		if (proguardFile.fileName.equals(fileName))
	    			return proguardFile;
	    	return null;
        }
    }

    /** Proguard fragment files included in Build bundle */
    public static final String[] TEXT_FILENAMES = new String[] {
    		"proguard-header.txt",
    		"proguard-optimizations.txt",
    		"proguard-common.txt"
    };
    // File indexes
    private static final int HEADER = 0;
    private static final int OPTIMIZATIONS = 1;
    private static final int COMMON = 2;
    
    public static final String UNKNOWN_FILENAME_MESSAGE =
            "Supplied proguard configuration file name %s is unsupported";

    /** Manager of file cache and bundle files */
	private final FileManager fileManager;

	/**
	 * Construct ProguardFile object
	 * @param fileManager Manager of file cache and bundle filea
	 */
	public ProguardFile(FileManager fileManager) {
		this.fileManager = fileManager;
	}

    /**
     * Creates and returns a new {@link File} with the requested default ProGuard file contents.
     *
     * <p><b>Note:</b> If the file is already there it just returns it.
     *
     * <p>There are 2 default rules files
     * <ul>
     *     <li>proguard-android.txt
     *     <li>proguard-android-optimize.txt
     * </ul>
     *
     * @param name the name of the default ProGuard file.
     * @param project used to determine the output location.
     */
    public File getDefaultProguardFile(@NonNull String name, @NonNull File projectLocation) {
    	DefaultProguardFile proguardFile = DefaultProguardFile.forFilename(name);
        if (proguardFile == null) {
            throw new IllegalArgumentException(UNKNOWN_FILENAME_MESSAGE);
        }
        File buildDir = new File(projectLocation, AndworxProject.BUILD_DIR);
        return FileUtils.join(
        		buildDir,
                AndroidProject.FD_INTERMEDIATES,
                "proguard-files",
                getProjectFilename(proguardFile));
    }
 
    /**
     * Generate default files if not contained in the File Manager
     */
    public void initialize() {
    	for (DefaultProguardFile proguardFile: DefaultProguardFile.values()) {
    		String fileName = getProjectFilename(proguardFile);
    		if (!fileManager.containsFile(fileName))
    			createProguardFile(proguardFile);
    	}
    }
  
    /**
     * Returns name of proguard file to be deployed to a project
     * @param proguardFile DefaultProguardFile enum
     * @return filename
     */
	public String getProjectFilename(DefaultProguardFile proguardFile) {
    	return proguardFile.fileName  + "-" + fileManager.getPluginVersion();
    }

	/**
	 * Writes a default Proguard file to a location determined by the FileManager
	 * @param proguardFile DefaultProguardFile enum
	 */
    private void createProguardFile(DefaultProguardFile proguardFile) {
        StringBuilder sb = new StringBuilder();
        append(sb, TEXT_FILENAMES[HEADER]);
        sb.append("\n");
        switch (proguardFile) {
            case DONT_OPTIMIZE:
                sb.append(
                        "# Optimization is turned off by default. Dex does not like code run\n"
                                + "# through the ProGuard optimize steps (and performs some\n"
                                + "# of these optimizations on its own).\n"
                                + "# Note that if you want to enable optimization, you cannot just\n"
                                + "# include optimization flags in your own project configuration file;\n"
                                + "# instead you will need to point to the\n"
                                + "# \"proguard-android-optimize.txt\" file instead of this one from your\n"
                                + "# project.properties file.\n"
                                + "-dontoptimize\n");
                break;
            case OPTIMIZE:
                sb.append(
                        "# Optimizations: If you don't want to optimize, use the proguard-android.txt configuration file\n"
                                + "# instead of this one, which turns off the optimization flags.\n");
                append(sb, TEXT_FILENAMES[OPTIMIZATIONS]);
                break;
            case NO_ACTIONS:
                sb.append(
                        "# Optimizations can be turned on and off in the 'postProcessing' DSL block.\n"
                                + "# The configuration below is applied if optimizations are enabled.\n");
                append(sb, TEXT_FILENAMES[OPTIMIZATIONS]);
                break;
        }

        sb.append("\n");
        append(sb, TEXT_FILENAMES[COMMON]);
        fileManager.writeAsCharSink(proguardFile.fileName, sb.toString());
	}

    /**
     * Appends content of file with given name to a StringBuilder object
     * @param sb String builder for the creation of text content
     * @param filename Name of file controlled by FileManager
     */
	private void append(StringBuilder sb, String filename) {
		if (fileManager.containsFile(filename)) {
			sb.append(fileManager.readAsCharSource(filename));
		}
	}

}
