/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not FilenameFilter filter this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
  http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.eclipse.andworx.task.java;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.andworx.AndworxConstants;
import org.eclipse.andworx.build.AndworxFactory;
import org.eclipse.andworx.exception.AndworxException;
import org.eclipse.andworx.file.CacheManager;
import org.eclipse.andworx.helper.ProjectBuilder;
import org.eclipse.andworx.process.java.JvmParameters;
import org.eclipse.debug.core.ILaunchConfiguration;

import com.android.annotations.NonNull;
import com.android.builder.core.DesugarProcessArgs;
import com.android.builder.core.DesugarProcessBuilder;
import com.android.ide.common.process.JavaProcessInfo;
import com.android.ide.common.process.ProcessEnvBuilder;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessInfoBuilder;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;

/**
 * Desugar process builder that holds environment variable information
 */
public class DesugarJavaBuilder extends ProcessEnvBuilder<DesugarProcessBuilder> {
    private static final String DESUGAR_MAIN = "com.google.devtools.build.android.desugar.Desugar";
	private static final String JVM_MEMORY = "-Xmx64M";

	/** Flag set true if logging level is verbose */
    @NonNull 
    private final boolean verbose;
    /** Maps each desugar input file to a corresponding output file */
    @NonNull 
    private final Map<Path, Path> inputsToOutputs;
    /** Classpath */
    @NonNull 
    private final List<Path> classpath;
    /** Java boot classpath */
    @NonNull 
    private final List<Path> bootClasspath;
    /** Minumum SDK version */
    private final int minSdkVersion;
    /** Temporary directory */
    @NonNull private final Path tmpDir;
 
    /**
     * 
     * @param verbose Flag set true if logging level is verbose
     * @param inputsToOutputs Maps each desugar input file to a corresponding output file
     * @param classpath Classpath
     * @param bootClasspath Java boot classpath
     * @param minSdkVersion Minumum SDK version
     * @param tmpDir Temporary directory
     */
	public DesugarJavaBuilder(
	            boolean verbose,
	            @NonNull Map<Path, Path> inputsToOutputs,
	            @NonNull List<Path> classpath,
	            @NonNull List<Path> bootClasspath,
	            int minSdkVersion,
	            @NonNull Path tmpDir) {
        this.verbose = verbose;
        this.inputsToOutputs = ImmutableMap.copyOf(inputsToOutputs);
        this.classpath = classpath;
        this.bootClasspath = bootClasspath;
        this.minSdkVersion = minSdkVersion;
        this.tmpDir = tmpDir;
	}

	/**
	 * Returns Java process information for desugar operation
	 * @return JavaProcessInfo object
	 */
	public static JavaProcessInfo getJavaProcessInfo() {
		CacheManager cacheManager = AndworxFactory.instance().getCacheManager();
		File desugarArchive = cacheManager.getFile(AndworxConstants.DESUGAR_JAR);
		if (desugarArchive == null)
			throw new AndworxException("File " + AndworxConstants.DESUGAR_JAR + " not found");
		return new JavaProcessInfo() {

			@Override
			public String getExecutable() {
				return null;
			}

			@Override
			public List<String> getArgs() {
				return Collections.emptyList();
			}

			@Override
			public Map<String, Object> getEnvironment() {
				return null;
			}

			@Override
			public String getDescription() {
				return ProjectBuilder.DESUGAR;
			}

			@Override
			public String getClasspath() {
				return desugarArchive.getAbsolutePath();
			}

			@Override
			public String getMainClass() {
				return DESUGAR_MAIN;
			}

			@Override
			public List<String> getJvmArgs() {
				return Collections.singletonList(JVM_MEMORY);
			}
		};
	}

	/**
	 * Configure this object
	 * @param configuration JDT launch configuration caontaining defaults
	 * @param isWindows Flag set true if operating system is Windows, which has a command line content restriction
	 * @return JvmParameters object
	 * @throws ProcessException
	 * @throws IOException
	 */
    @NonNull
    public JvmParameters build(ILaunchConfiguration configuration, boolean isWindows) throws ProcessException, IOException {

        ProcessInfoBuilder builder = new ProcessInfoBuilder();
        builder.addEnvironments(mEnvironment);
        // Set main class and classpath to pass validation
    	JavaProcessInfo javaProcessInfo = getJavaProcessInfo();
        builder.setMain(javaProcessInfo.getMainClass());
        builder.setClasspath(javaProcessInfo.getClasspath());
        builder.addJvmArg(JVM_MEMORY);

        int pathArgs = 2 * inputsToOutputs.size() + classpath.size() + bootClasspath.size();

        List<String> args = new ArrayList<>(8 * pathArgs + 5);

        if (verbose) {
            args.add("--verbose");
        }
        inputsToOutputs.forEach(
                (in, out) -> {
                    args.add("--input");
                    args.add(in.toString());
                    args.add("--output");
                    args.add(out.toString());
                });
        classpath.forEach(
                c -> {
                    args.add("--classpath_entry");
                    args.add(c.toString());
                });
        bootClasspath.forEach(
                b -> {
                    args.add("--bootclasspath_entry");
                    args.add(b.toString());
                });

        args.add("--min_sdk_version");
        args.add(Integer.toString(minSdkVersion));
        if (minSdkVersion < DesugarProcessArgs.MIN_SUPPORTED_API_TRY_WITH_RESOURCES) {
            args.add("--desugar_try_with_resources_if_needed");
        } else {
            args.add("--nodesugar_try_with_resources_if_needed");
        }
        args.add("--desugar_try_with_resources_omit_runtime_classes");
        int[] commandLineLength = new int[] {0};
        if (isWindows)
        	args.forEach(arg ->  { commandLineLength[0] += arg.length(); } );
        if (commandLineLength[0] > DesugarProcessArgs.MAX_CMD_LENGTH_FOR_WINDOWS) {
            if (!Files.exists(tmpDir)) {
                Files.createDirectories(tmpDir);
            }
            Path argsFile = Files.createTempFile(tmpDir, "desugar_args", "");
            Files.write(argsFile, args, Charsets.UTF_8);

            builder.addArgs("@" + argsFile.toString());
        } else {
            builder.addArgs(args);
        }

        javaProcessInfo = builder.createJavaProcess();
        JvmParameters jvmParameters = new JvmParameters(configuration);
        jvmParameters.setArgs(javaProcessInfo.getArgs());
        return jvmParameters;
    }
}
