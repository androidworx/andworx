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
package org.eclipse.andworx;

import com.android.builder.utils.FileCache;

/**
 * System constants
 */
public class AndworxConstants {
    /** Nature of Android projects */
    public final static String ANDROID_NATURE = "org.eclipse.andmore.AndroidNature";
	/** Project build file name */
	public static final String FN_BUILD_ANDWORX = "build.andworx";
	/** Log4j file location property key used by log4j.xml */
	public static final String LOG4J_LOG_DIR_KEY = "log4j.log.dir";
	/** "Plugin version" input parameter required when using {@link FileCache} */
	public static String ANDWORX_BUILD_VERSION = "1.0";
	/** Name of Desugar deploy jar */
    public static final String DESUGAR_JAR = "desugar_deploy.jar";
	/** Android dependencies identifier */
    public final static String CONTAINER_DEPENDENCIES = "org.eclipse.andmore.DEPENDENCIES";
    /** Nature of default Android projects */
    public final static String NATURE_DEFAULT = "org.eclipse.andmore.AndroidNature"; 
    /** Environment flag for debugging. Used to make wait states last indefinitely */
    public final static String DEVELOPMENT_MODE = "DEVELOPMENT_MODE";
	public static final String TEMP_AAPT_DIR = "aapt-temp";
	public static final int MERGED_RESOURCES_PROCESSOR_TIMEOUT = 20;
	/** Launch configuration constant for application name */
	public final static String ATTR_LAUNCH_APPLICATION = "org.eclipse.andworx.application";
}
