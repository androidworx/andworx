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
package org.eclipse.andmore.internal.build;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.andmore.AndmoreAndroidConstants;
import org.eclipse.andmore.AndmoreAndroidPlugin;
import org.eclipse.andmore.AndroidPrintStream;
import org.eclipse.andworx.build.AndworxFactory;
import org.eclipse.andworx.context.AndroidEnvironment;
import org.eclipse.andworx.registry.ProjectState;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;

import com.android.annotations.NonNull;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;

/**
 * Executes the Dx tool for dalvik code conversion. Adds Desugar to chain if project Java verson is 1.8.0 or above.
 * Designed to allow unit testing so has BuildFactory helper class to hide Eclipse workbench objects.
 * @see BuildFactory
 *
 */
public class ToolChain {
	/**
	 * Logs errors to Andmore console
	 */
	/** Dex option to bypass pre-dex stage */
	public static final String DISABLE_DEX_MERGER = "disableDexMerger";
	/** A temporary prefix on the print streams */
    private static final String CONSOLE_PREFIX_DX = "Dx";
    // Multidex currently not supported 
    //private static final String MULTIDEX_ENABLED_PROPERTY = "multidex.enabled";
    //private static final String MULTIDEX_MAIN_DEX_LIST_PROPERTY = "multidex.main-dex-list";
    /** Dex error message for when tool returns an error code - not very helpful */
	private static final String DALVIK_ERROR = "Conversion to Dalvik format failed with error %1$d";
	/** JDT Launch Configuration name for Dex - appears in "Run configurations..." */
	private static final String DEX_CONFIG_NAME = "dex";
	/** Dex main class */
	private static final String DX_CLASS = "com.android.dx.command.Main";
	/** Folder for files input to Dex for final classes.dex build */
	private static final String DEX_LIBS_FOLDER = "dexedLibs";
	/** JDT Launch Configuration name for Desugar - appears in "Run configurations..." */
	private static final String DESUGAR_CONFIG_NAME = "desugar";
	/** Desugar main class */
	private static final String DESUGAR_CLASS = "com.google.devtools.build.android.desugar.Desugar";
	/** Folder for files prouduced by desugar */
	private static final String DESUGAR_LIBS_FOLDER = "sourLibs";
	/** Desuguar bootclasspath command line switch */
	private static final String BOOTCLASSPATH = "--bootclasspath_entry";
	/** Desuguar input command line switch */
	private static final String INPUT_ENTRY = "--input";
	/** Desuguar output command line switch */
	private static final String OUTPUT_ENTRY = "--output";
	/** Desuguar classpath command line switch */
	private static final String CLASSPATH_ENTRY = "--classpath_entry";
	// System properties keywords
	private static final String FILE_ENCODING = "file.encoding";
	private static final String USER_COUNTRY = "user.country";
	private static final String USER_LANGUAGE = "user.language";
	private static final String USER_VARIANT = "user.variant"; 
	/** Desugar classpath files */
	private static final String[] CLASSPATH_ARCHIVES;
	/** Desugar VM arguments */
	private static final String[] VM_ARGS;

	static 
	{
		CLASSPATH_ARCHIVES =  new String[]
		{ 
			"javax.annotation-api-1.3.1.jar" 
		};
		
		VM_ARGS = new String[]
		{
			"-Djava.awt.headless=true",
	        "-Xmx64M"	
		};
	}
	
	/** The project being built */
    @NonNull
	protected final IJavaProject javaProject;
    /** The workbench factory - allows testing */
	private WorkbenchFactory workbenchFactory;

	/** Project state includes configuration attributes and SDK references */
    @NonNull
    private final ProjectState projectState;
    /** Flag set true if deetailed logging required */
    private boolean verbose;
    /** Flag set true to disable Dex merge */
    private boolean disableDexMerger;
    /** SDK build tool selection */
    private BuildToolInfo buildToolInfo;
    /** Location of desugar.jar - unfortunately not currently included in build tools */
    private File andmoreDesugarPath;
  
    /**
     * Construct ToolChain objet
     * @param javaProject The project being built 
     * @param projectState Project state includes configuration attributes and SDK references
     * @param verbose Flag set true if deetailed logging required
     * @param options Set of tool options by nameGoldie09!
     * 
     */
    public ToolChain(IJavaProject javaProject, 
    		         ProjectState projectState,             
     		         boolean verbose,
    		         Set<String> options) {
    	this.javaProject = javaProject;
    	this.projectState = projectState;
    	this.verbose = verbose;
    	workbenchFactory = workbenchFactoryInstance();
    	disableDexMerger = options.contains(DISABLE_DEX_MERGER);
        buildToolInfo = workbenchFactory.getBuildToolInfo(projectState);
        // Get desugar location
        try {
        	AndroidEnvironment androidEnvironment = AndworxFactory.instance().getAndroidEnvironment();
        	File andmoreHomePath = androidEnvironment.getAndworxHome();
        	andmoreDesugarPath = new File(andmoreHomePath, "desugar");
        } catch (IllegalStateException e) { // This is not expected 
     		throw new RuntimeException(e.getMessage(), e);
        }
   }
 
	/**
     * Execute the Dx tool for dalvik code conversion.
     * @param inputPaths Input paths for DX
     * @param osOutFilePath Path of the dex file to create.
     *
     * @throws CoreException
     * @throws DexException
     */
    @SuppressWarnings("resource")
	public void executeDx(Collection<String> inputPaths,
            File osOutFilePath)
            throws CoreException, DexException {

    	AndroidPrintStream outStream = workbenchFactory.getOutStream();
    	AndroidPrintStream errStream = workbenchFactory.getErrStream();
    	// Flag whether Java version requires desugar to be included in the tool chain
    	String javaVersion = workbenchFactory.getJavaVersion(DEX_CONFIG_NAME);
    	AndmoreAndroidPlugin.printToConsole(javaProject.getElementName(), "Java version = " + javaVersion);
    	boolean isJava8 = javaVersion.compareTo("1.8.0") >= 0;
        try {
            // set a temporary prefix on the print streams.
            outStream.setPrefix(CONSOLE_PREFIX_DX);
            errStream.setPrefix(CONSOLE_PREFIX_DX);

/* Multidex not supported
            boolean multiDexEnabled = false;
            String mainDexListFileLocation = null;
            
            if (projectState.getProperty(MULTIDEX_ENABLED_PROPERTY) != null) {
            	multiDexEnabled = Boolean.parseBoolean(projectState.getProperty(MULTIDEX_ENABLED_PROPERTY));
            	
            	if (multiDexEnabled && projectState.getProperty(MULTIDEX_MAIN_DEX_LIST_PROPERTY) != null) {
            		// inform the user
            		outStream.println("Using --multi-dex");
            		
            		IFile mainDexListFile = javaProject.getProject().getFile(
	            			projectState.getProperty(MULTIDEX_MAIN_DEX_LIST_PROPERTY));
	            	if (mainDexListFile != null) {
	            		mainDexListFileLocation = mainDexListFile.getRawLocation().toOSString();
	            		
	            		// For multidex output to a folder
	                	osOutFilePath = new File(osOutFilePath).getParent();
	            	}
            	}
            }
*/            
            // Replace the libs by their dexed versions (dexing them if needed.)
            List<String> finalInputPaths = new ArrayList<String>(inputPaths.size());
            List<TransformPathPair> desugarList = null;
            if (disableDexMerger || /*multiDexEnabled ||*/ (!isJava8 && inputPaths.size() == 1)) {
                // only one input, no need to put a pre-dexed version, even if this path is
                // just a jar file (case for proguard'ed builds)
                finalInputPaths.addAll(inputPaths);
            } else 
                desugarList = processDexInputs(inputPaths, finalInputPaths, isJava8);
            if (isJava8 && (desugarList != null)) {
            	Collection<String> desugarInputList = new ArrayList<>();
            	for (TransformPathPair tPair: desugarList) {
            		desugarInputList.add(tPair.getInputPath());
            		finalInputPaths.add(tPair.getOutputPath());
            	}
				Collection<String> dexInputList = desugar(desugarInputList);
				Iterator<String> iterator = dexInputList.iterator();
            	for (TransformPathPair tPair: desugarList) {
            		desugarInputList.add(tPair.getInputPath());
                    int res = runDx(tPair.getOutputPath(), Collections.singleton(iterator.next()));
                    if (res != 0) {
                    	// System console meessages should appear in Android console to provide more information. This does not work for reason unknown.
                        String message = String.format(DALVIK_ERROR, res);
                        throw new DexException(message);
                    }
            	}
            }
            if (verbose) {
                for (String input : finalInputPaths) {
                    outStream.println("Input: " + input);
                }
            }
            int res = runDx(osOutFilePath, finalInputPaths);
            outStream.setPrefix(null);
            errStream.setPrefix(null);
            if (res != 0) {
                // output error message and marker the project.
                String message = String.format(DALVIK_ERROR, res);
                throw new DexException(message);
            }
        } catch (Throwable t) {
            String message = t.getMessage();
            if (message == null) {
                message = t.getClass().getCanonicalName();
            }
            message = String.format("Conversion to Dalvik format failed: %1$s", message);
            throw new DexException(message, t);
        }
    }

    /**
     * Returns Workbench factory instance
     * @return
     */
    protected WorkbenchFactory workbenchFactoryInstance() {
		return new BuildFactory(javaProject);
	}

    /**
     * Runs the dex command.
     * @param osOutFilePath the OS path to the outputfile (classes.dex
     * @param osFilenames list of input source files (.class and .jar files)
     * @return the integer return code of com.android.dx.command.dexer.Main.run()
     * @throws CoreException
     * @throws IOException 
     */
    protected int runDx(
            File osOutFilePath, 
            Collection<String> osFilenames) throws CoreException, IOException
    {
    	return runDx(osOutFilePath.getAbsolutePath(), osFilenames);
    }
    
   /**
     * Runs the dex command.
     * @param osOutFilePath the OS path to the outputfile (classes.dex
     * @param osFilenames list of input source files (.class and .jar files)
     * @return the integer return code of com.android.dx.command.dexer.Main.run()
     * @throws CoreException
     * @throws IOException 
     */
    protected int runDx(
            String osOutFilePath, 
            Collection<String> osFilenames) throws CoreException, IOException
    {
        List<String> command = new ArrayList<String>();
        command.add("--dex");
        if (verbose)
            command.add("--verbose"); // --num-threads=4 
        command.add("--output");
        command.add(osOutFilePath);
        for (String fileName: osFilenames)
            command.add(fileName);
        String dxFilePath = buildToolInfo.getPath(BuildToolInfo.PathId.DX_JAR);
        List<Path> dxJarPath = Collections.singletonList(new Path(dxFilePath));
    	String javaVersion = workbenchFactory.getJavaVersion(DEX_CONFIG_NAME);
    	boolean isJava9 = javaVersion.compareTo("9.0.0") >= 0;
    	String vmArgs = "-Djava.awt.headless=true -Xmx1024M -Dfile.encoding=UTF-8";
    	if (isJava9) // Dex on Java9 requires system modules 
    		vmArgs = vmArgs + " --add-modules=ALL-SYSTEM";
        return runJavaMain(DEX_CONFIG_NAME, DX_CLASS, vmArgs, dxJarPath, command);
    }

    /**
     * Rusns the desugar command
     * @param transformPairs Input/output paths for desugar transformation
     * @param classpathList List of classpath items
     * @return Return code
     * @throws CoreException
     */
	protected int runDesugar(List<TransformPathPair> transformPairs, List<String> classpathList) throws CoreException {
		List<String> vmArguments = new ArrayList<>();
		appendVmArgs(vmArguments);
		appendSystemProps(vmArguments);
		List<String> arguments = new ArrayList<>();
		for (TransformPathPair pair: transformPairs) {
			arguments.add(INPUT_ENTRY);
			arguments.add(pair.getInputPath());
			arguments.add(OUTPUT_ENTRY);
			arguments.add(pair.getOutputPath());
		}
		for (String classpath: classpathList) {
			arguments.add(CLASSPATH_ENTRY);
		    arguments.add(classpath);
		}
		appendClasspathArchives(arguments);
		appendJreBootFiles(arguments);
		appendAndroidBootFiles(arguments);
		appendDesugarFlags(arguments);
		// desugar.jar and "libs" dependency folders are located in user's Android home location in folder ".andmore/desugar"
        File desugarFilePath = new File(andmoreDesugarPath, "desugar.jar");
		return runJar(DESUGAR_CONFIG_NAME, desugarFilePath, DESUGAR_CLASS, asString(vmArguments), arguments);
	}

	/**
	 * Process Dex inputs and return desugar tranform pair list
	 * @param inputPaths Dex inputs
	 * @param finalInputPaths List of inputs to final Dex classes.dex run
	 * @param isJava8 Java level is 1.8.0 or above
	 * @return TransformPathPair list or null if desugar not required
	 * @throws IOException
	 * @throws CoreException
	 * @throws DexException
	 */
    @SuppressWarnings("resource")
	private List<TransformPathPair>  processDexInputs(Collection<String> inputPaths, List<String> finalInputPaths, boolean isJava8) throws IOException, CoreException, DexException {
        List<TransformPathPair> desugarList = null;
         IFolder binFolder = workbenchFactory.getAndroidOutputFolder();
        File binFile = binFolder.getLocation().toFile();
        File dexedLibs = new File(binFile, DEX_LIBS_FOLDER);
        if (!dexedLibs.exists() && !dexedLibs.mkdirs()) {
        	throw new DexException("Cannot create directory \"" + dexedLibs.getAbsolutePath() + "\"");
        }
       	AndroidPrintStream outStream = workbenchFactory.getOutStream();
        int classesIndex = 0;
        for (String input : inputPaths) {
            File inputFile = new File(input);
            if (!isJava8 && inputFile.isDirectory()) {
            	// classes directory and desugar not required
                finalInputPaths.add(input);
            } else {
                String fileName = null;
                
                if (!inputFile.isDirectory())
                	// Archive
                	fileName = getDexFileName(inputFile);
                else
                	// Classes directory - output path is a unique classes archive
                	fileName = getDexFileName(new File(inputFile, "classes_" + Integer.toString(++classesIndex) + ".jar"));
                File dexedLib = new File(dexedLibs, fileName);
                String dexedLibPath = dexedLib.getAbsolutePath();
                // Do not over-write file if it exists and is not out of date to avoid unnessary work
                if (!(dexedLib.exists() && dexedLib.isFile()) ||
                     dexedLib.lastModified() < inputFile.lastModified()) {

                    if (verbose) {
                        outStream.println(
                                String.format("Pre-Dexing %1$s -> %2$s", input, fileName));
                    }

                    if (dexedLib.isFile()) {
                        dexedLib.delete();
                    }
                	if (isJava8) {
                		// Add to desugar list
                		File newFile = new File(dexedLibPath);
                		if (!newFile.createNewFile()) {
                            String message = String.format(DALVIK_ERROR, "Cannot create " + dexedLibPath);
                            throw new DexException(message);
               		    }
                		if (desugarList == null)
                			desugarList = new ArrayList<>();
                		desugarList.add(new TransformPathPair(input, dexedLibPath));
                	}
                	else {
                        finalInputPaths.add(dexedLibPath);
                        int res = runDx(dexedLibPath, Collections.singleton(input));

                        if (res != 0) {
                            // output error message and mark the project.
                            String message = String.format(DALVIK_ERROR, res);
                            throw new DexException(message);
                        }
                	}
                } else {
                    finalInputPaths.add(dexedLibPath);
                    if (verbose) {
                        outStream.println(
                                String.format("Using Pre-Dexed %1$s <- %2$s",
                                        fileName, input));
                    }
                }
            }
        }
        return desugarList;
    }
 
    /**
     * Conver string list to single string with items separated by a space character
     * @param vmArguments
     * @return
     */
	private String asString(List<String> vmArguments) {
		StringBuilder builder = new StringBuilder();
		Iterator<String> iterator = vmArguments.iterator();
		boolean firstTime = true;
		while (iterator.hasNext()) {
			if (firstTime)
				firstTime = false;
			else 
				builder.append(' ');
			builder.append(iterator.next());
		}
		return builder.toString();
	}

	/**
	 * Run desugar for given list of inputs
	 * @param inputs Desugar input list
	 * @return Output collection with sequence corresponding to inputs 
	 * @throws CoreException
	 */
	private Collection<String> desugar(Collection<String> inputs) throws CoreException {
		List<TransformPathPair> transformPairs = new ArrayList<>();
		List<String> classpathList = new ArrayList<>();;
		Collection<String> outputFiles = new ArrayList<>(inputs.size());
		// Place output files in "sourlibs" bin subdirectory
        IFolder binFolder = workbenchFactory.getAndroidOutputFolder();
        File binFile = binFolder.getLocation().toFile();
        File sourLibs = new File(binFile, DESUGAR_LIBS_FOLDER);
        if (!sourLibs.exists()) {
        	sourLibs.mkdir();
        }
		for (String filepath: inputs) {
			File inputFile = new File(filepath);
            String fileName = getDexFileName(inputFile);

            File sourLib = new File(sourLibs, fileName);
            String dexedLibPath = sourLib.getAbsolutePath();

            if (!sourLib.isFile() ||
            		sourLib.lastModified() < inputFile.lastModified()) {
                if (sourLib.isFile()) 
                	sourLib.delete();
                transformPairs.add(new TransformPathPair(filepath, sourLib.getAbsolutePath()));
            }
            classpathList.add(filepath);
            outputFiles.add(dexedLibPath);
		}
		if (!transformPairs.isEmpty()) {
			runDesugar(transformPairs, classpathList);
		}
		return outputFiles;
	}

	/**
     * Launch a Java program from a jar which has dependencies in a "libs" subdirectory
     * @param cfg the name of the launch configuration to use
     * @param jar Filepath of Jar to execute
     * @param main Main class
     * @param vmArgs VM arguments
     * @param arguments Application arguments
     * @throws CoreException
     */
    protected int runJar(String configName, File jar, String main, String vmArgs,  Collection<String> arguments) throws CoreException {
    	File libsPath = new File(jar.getParentFile(), "libs");
        FilenameFilter filter = new FilenameFilter() {

			@Override
			public boolean accept(File dir, String name) {
				return name.endsWith(".jar");
			}};
		File[] cpFiles = libsPath.listFiles(filter);
    	List<Path> classpath = new ArrayList<>();
    	classpath.add(new Path(jar.getAbsolutePath()));
    	for (File file: cpFiles) {
    		classpath.add(new Path(file.getAbsolutePath()));
    	}
        ILaunchConfigurationWorkingCopy wc = getLaunchConfig(configName, vmArgs, classpath, main, arguments);
        final ILaunchConfiguration config = wc.doSave();   
        Launcher launcher = workbenchFactory.getLauncher();
        int returnCode = -1;
        try
        {
        	returnCode = launcher.launch(config);
        }
        catch (DebugException e)
        {
            String message = String.format("Unable to execute %1$s: %2$s", jar.getName(), e.getMessage());
            throw new CoreException(new Status(IStatus.ERROR, AndmoreAndroidConstants.PLUGIN_ID, message, e));
        }
        return returnCode;
    }
    
	/**
     * Launch a Java program
     * @param cfg the name of the launch configuration to use
     * @param main Main class
     * @param vmArgs VM arguments
     * @param classpathList Classpath as Path list
     * @param arguments Application arguments
     * @throws CoreException
     */
    protected int runJavaMain(String configName, String main, String vmArgs, List<Path> classpathList, Collection<String> arguments) throws CoreException {
        ILaunchConfigurationWorkingCopy wc = getLaunchConfig(configName, vmArgs, classpathList, main, arguments);
        final ILaunchConfiguration config = wc.doSave();   
        Launcher launcher = workbenchFactory.getLauncher();
        int returnCode = -1;
        try
        {
        	returnCode = launcher.launch(config);
        }
        catch (DebugException e)
        {
            String message = String.format("Unable to execute %1$s: %2$s", main, e.getMessage());
            throw new CoreException(new Status(IStatus.ERROR, AndmoreAndroidConstants.PLUGIN_ID, message, e));
        }
        return returnCode;
    }

    /**
     * Returns editable launch configuration for given parameters
     * @param configName Configuration name
     * @param vmArgs VM arguments
     * @param classpathList Classpath as Path list
     * @param main Main class
     * @param arguments Application arguments
     * @return
     * @throws CoreException
     */
    private ILaunchConfigurationWorkingCopy getLaunchConfig(String configName,  String vmArgs, List<Path> classpathList, String main, Collection<String> arguments) throws CoreException {
        javaProject.open(null);
        StringBuilder builder = new StringBuilder();
        boolean firstTime = true;
        for (String arg: arguments) {
        	if (firstTime)
        		firstTime = false;
        	else
        		builder.append(' ');
            builder.append(arg);
        }
        DebugPlugin debugPlugin = workbenchFactory.getDefaultDebugPlugin();
        ILaunchManager launchManager = debugPlugin.getLaunchManager();
        ILaunchConfigurationType launchConfigType = launchManager.getLaunchConfigurationType(
             IJavaLaunchConfigurationConstants.ID_JAVA_APPLICATION);
        ILaunchConfigurationWorkingCopy wc = launchConfigType.newInstance(null, configName);
        // Set attribute to capture console output
        wc.setAttribute(DebugPlugin.ATTR_CAPTURE_OUTPUT, true);
        wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, javaProject.getElementName());
        wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_VM_ARGUMENTS, vmArgs);
        wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME, main);
        wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_PROGRAM_ARGUMENTS, builder.toString());
        List<String> classpath = new ArrayList<String>();
        for (Path path: classpathList) {
        	classpath.add(workbenchFactory.getMomento(path));
        }
        classpath.add(workbenchFactory.getJreMemento());
        classpath.add(workbenchFactory.getDefaulClasspathMemento());
        wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_CLASSPATH, classpath);
        wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_DEFAULT_CLASSPATH, false);
        return wc;
    }

    /**
     * Returns hashed file name version of given file
     * @param inputFile File to process
     * @return
     */
    private String getDexFileName(File inputFile) {
        // get the filename
        String name = inputFile.getName();
        // remove the extension
        int pos = name.lastIndexOf('.');
        if (pos != -1) {
            name = name.substring(0, pos);
        }
        return name + "-" + workbenchFactory.getHashCode(inputFile) + ".jar";
    }
 
    /**
     * Append Android target boot archives to given command line parameters
     * @param commandSequence
     */
	private void appendAndroidBootFiles(Collection<String> commandSequence) {
		// Include Android jar for current target
		File androidJar = projectState.getProfile().getTarget().getFile(IAndroidTarget.ANDROID_JAR);
		if (!androidJar.exists())
			throw new RuntimeException("Android jar \"" + androidJar.getAbsolutePath() + "\" not found");
		commandSequence.add(BOOTCLASSPATH);
		commandSequence.add(androidJar.getPath());
		// Add associated optional archives
		File optional = new File(androidJar.getParentFile(), "optional");
		if (optional.exists() && optional.isDirectory()) {
			FilenameFilter filter = new FilenameFilter(){
	
				@Override
				public boolean accept(File dir, String name) {
					return name.endsWith(".jar");
				}};
			String[] optionFiles = optional.list(filter);
			for (String file: optionFiles) {
				commandSequence.add(BOOTCLASSPATH);
				File filepath = new File(optional, file);
				commandSequence.add(filepath.getPath());
			}
		}
	}

    /**
     * Append Java boot archives to given command line parameters
     * @param commandSequence
     */
	private void appendJreBootFiles(Collection<String> commandSequence) {
		// "sun.boot.class.path" not supported after Jre SE 1.8
		String bootClassPath = System.getProperty("sun.boot.class.path");
		if (bootClassPath != null) {
			String[] jreBootFiles = bootClassPath.split(File.pathSeparator);
			for (String file: jreBootFiles) {
				// Skip exceptions - clases and the obsolete RSA encryption jar
				if (file.endsWith("classes") || file.endsWith("sunrsasign.jar"))
					continue;
				commandSequence.add(BOOTCLASSPATH);
				commandSequence.add(file);
			}
		}
	}

    /**
     * Append desugar classpath archives to given command line parameters
     * @param commandSequence
     */
	private void appendClasspathArchives(Collection<String> commandSequence) {
		// desugar.jar and "libs" dependency floder are located in user's Android home location in folder ".andmore/desugar"
		File libsDir = new File(andmoreDesugarPath, "libs");
		for (String archive: CLASSPATH_ARCHIVES) {
			commandSequence.add(CLASSPATH_ENTRY);
			commandSequence.add(new File(libsDir, archive).getAbsolutePath());
		}
	}

    /**
     * Append desugar VM arguments to given command line parameters
     * @param commandSequence
     */
	private void appendVmArgs(Collection<String> commandSequence) {
		for (String arg: VM_ARGS) {
			commandSequence.add(arg);
		}
	}

    /**
     * Append environmental parameters to given command line parameters
     * @param commandSequence
     */
	private void appendSystemProps(Collection<String> commandSequence) {
		String fileEncoding = System.getProperty(FILE_ENCODING, "UTF-8");
		commandSequence.add("-D" + FILE_ENCODING + "=" + fileEncoding);
		String country = System.getProperty(USER_COUNTRY, "US");
		commandSequence.add("-D" + USER_COUNTRY + "=" + country);
		String language = System.getProperty(USER_LANGUAGE, "en");
		commandSequence.add("-D" + USER_LANGUAGE + "=" + language);
		String variant = System.getProperty(USER_VARIANT, "");
		if (!variant.isEmpty())
			variant += "=";
		commandSequence.add("-D" + USER_VARIANT + variant);
	}

	/**
	 * Append Desugar flags
	 */
	private void appendDesugarFlags(Collection<String> commandSequence) {
		commandSequence.add("--min_sdk_version");
		commandSequence.add("11"); 
		commandSequence.add("--desugar_try_with_resources_if_needed");
		commandSequence.add("--desugar_try_with_resources_omit_runtime_classes");
	}
	
	
}
