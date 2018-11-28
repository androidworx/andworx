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

package org.eclipse.andmore;

import static com.android.SdkConstants.CURRENT_PLATFORM;
import static com.android.SdkConstants.PLATFORM_DARWIN;
import static com.android.SdkConstants.PLATFORM_LINUX;
import static com.android.SdkConstants.PLATFORM_WINDOWS;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.eclipse.andmore.base.BaseContext;
import org.eclipse.andmore.base.BasePlugin;
import org.eclipse.andmore.base.SdkSelectionListener;
import org.eclipse.andmore.base.resources.PluginResourceProvider;
import org.eclipse.andmore.base.resources.PluginResourceRegistry;
import org.eclipse.andmore.internal.actions.SdkManagerAction;
import org.eclipse.andmore.internal.editors.AndroidXmlEditor;
import org.eclipse.andmore.internal.editors.IconFactory;
import org.eclipse.andmore.internal.editors.common.CommonXmlEditor;
import org.eclipse.andmore.internal.editors.layout.gle2.IncludeFinder;
import org.eclipse.andmore.internal.launch.AndroidLaunchController;
import org.eclipse.andmore.internal.lint.LintDeltaProcessor;
import org.eclipse.andmore.internal.preferences.AdtPrefs;
import org.eclipse.andmore.internal.preferences.AdtPrefs.BuildVerbosity;
import org.eclipse.andmore.internal.project.AndroidClasspathContainerInitializer;
import org.eclipse.andmore.internal.project.BaseProjectHelper;
import org.eclipse.andmore.internal.project.BaseProjectHelper.MarkerListener;
import org.eclipse.andmore.internal.project.ProjectHelper;
import org.eclipse.andmore.internal.resources.manager.GlobalProjectMonitor;
import org.eclipse.andmore.internal.resources.manager.GlobalProjectMonitor.IFileListener;
import org.eclipse.andmore.internal.resources.manager.GlobalProjectMonitor.IProjectListener;
import org.eclipse.andmore.internal.resources.manager.ResourceManager;
import org.eclipse.andmore.internal.sdk.AdtConsoleSdkLog;
import org.eclipse.andmore.internal.sdk.AndroidTargetData;
import org.eclipse.andmore.internal.sdk.Sdk;
import org.eclipse.andmore.internal.sdk.Sdk.ITargetChangeListener;
import org.eclipse.andmore.internal.ui.EclipseUiHelper;
import org.eclipse.andmore.internal.welcome.AdtStartup;
import org.eclipse.andmore.sdktool.SdkUserInterfacePlugin;
import org.eclipse.andmore.sdktool.install.SdkInstaller;
import org.eclipse.andworx.build.AndworxBuildPlugin;
import org.eclipse.andworx.build.AndworxContext;
import org.eclipse.andworx.build.AndworxFactory;
import org.eclipse.andworx.context.AndroidEnvironment;
import org.eclipse.andworx.event.AndworxEvents;
import org.eclipse.andworx.exception.AndworxException;
import org.eclipse.andworx.sdk.AndroidSdkPreferences;
import org.eclipse.andworx.sdk.AndroidSdkValidator;
import org.eclipse.andworx.sdk.CheckSdkErrorHandler;
import org.eclipse.andworx.sdk.SdkProfile;
import org.eclipse.andworx.sdk.SdkTargetDataMap;
import org.eclipse.core.commands.Command;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IMarkerDelta;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.IJobChangeListener;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.e4.core.services.events.IEventBroker;
import org.eclipse.e4.ui.internal.workbench.E4Workbench;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorDescriptor;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.browser.IWebBrowser;
import org.eclipse.ui.browser.IWorkbenchBrowserSupport;
import org.eclipse.ui.commands.ICommandService;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleConstants;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.console.MessageConsoleStream;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.eclipse.ui.texteditor.AbstractTextEditor;
import org.eclipse.wb.internal.core.DesignerPlugin;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.resources.ResourceFile;
import com.android.ide.common.sdk.LoadStatus;
import com.android.io.StreamException;
import com.android.resources.ResourceFolderType;
import com.android.sdklib.IAndroidTarget;
import com.android.sdkuilib.wizard.SelectSdkWizard;
import com.android.utils.ILogger;
import com.google.common.collect.Sets;
import com.google.common.io.Closeables;

/**
 * The activator class controls the plug-in life cycle
 */
public class AndmoreAndroidPlugin extends AbstractUIPlugin implements ILogger {
	private static final String SDK_LOC_SAVE_ERROR = "Error saving SDK location";

    /** singleton instance */
    private static class AndmoreAndroidPluginHolder {
        private AndmoreAndroidPlugin staticPlugin;
    	private volatile AndmoreAndroidPlugin dynamicPlugin;

    	public void setPlugin(AndmoreAndroidPlugin staticPlugin) {
			synchronized(this) {
				this.staticPlugin = staticPlugin;
				if (staticPlugin == null)
					dynamicPlugin = null;
			}
    	}
    	
    	public AndmoreAndroidPlugin getPlugin() {
    		while (dynamicPlugin == null) {
    			synchronized(this) {
    	    		if ((dynamicPlugin == null) && (staticPlugin != null)) {
    	    			dynamicPlugin = staticPlugin;
                        return dynamicPlugin;
    	    		}
    			}
    			try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					break;
				}
    		}
    		// Will return null if thread is interrupted before pluin start() is called
    		return dynamicPlugin;
    	}
    }

    private static class SafeLog {
        private AndmoreAndroidPlugin staticPlugin;
 
        public synchronized void setPlugin(AndmoreAndroidPlugin staticPlugin) {
        	this.staticPlugin = staticPlugin;
        }
  
        public synchronized void log(Throwable exception, String format, Object ... args) {
        	doLog(IStatus.INFO, exception, format, args);
        }
        
        public synchronized void log(int severity, String format, Object ... args) {
        	doLog(severity, null, format, args);
        }
        
        private void doLog(int severity, Throwable exception, String format, Object ... args) {
            String message = null;
            if (format != null) {
                message = String.format(format, args);
            } else {
                message = "";
            }
            Status status = new Status(severity, AndmoreAndroidConstants.PLUGIN_ID, message, exception);

            if (staticPlugin != null) {
            	staticPlugin.getLog().log(status);
            } else {
                // During UnitTests, we generally don't have a plugin object. It's ok
                // to log to stderr in this case.
                (severity < IStatus.ERROR ? System.out : System.err).println(status.toString());
            }
        }

    }
    
    private static AndmoreAndroidPluginHolder andmoreAndroidPluginHolder = new AndmoreAndroidPluginHolder();
    private static SafeLog safeLog = new SafeLog();
    private static Image sAndroidLogo;
    private static ImageDescriptor sAndroidLogoDesc;

    /** Job to display SDK install dialog */
    private final Job sdkInstallJob;
    /** Preferences persisted with Eclipse preference store of this plugin */
    private volatile AdtPrefs adtPrefs;
    /** The global android console */
    private MessageConsole mAndroidConsole;

    /** Stream to write in the android console */
    private MessageConsoleStream mAndroidConsoleStream;

    /** Stream to write error messages to the android console */
    private MessageConsoleStream mAndroidConsoleErrorStream;

    /** Color used in the error console */
    private Color mRed;
    /** Load status of the SDK. Any access MUST be in a synchronized(mPostLoadProjects) block */
    private LoadStatus mSdkLoadedStatus;
    /** Project to update once the SDK is loaded.
     * Any access MUST be in Sdk.LOCK synchronized block */
    private final Set<IJavaProject> mPostLoadProjectsToResolve = Sets.newHashSet();
    /** Project to check validity of cache vs actual once the SDK is loaded.
     * Any access MUST be in Sdk.LOCK  synchronized block */
    private final ArrayList<IJavaProject> mPostLoadProjectsToCheck = new ArrayList<IJavaProject>();

    /** Map associating an {@link IAndroidTarget} to an {@link AndroidTargetData} */
    private final SdkTargetDataMap<AndroidTargetData> targetDataMap;
    private GlobalProjectMonitor mResourceMonitor;
    private ArrayList<ITargetChangeListener> mTargetChangeListeners =
            new ArrayList<ITargetChangeListener>();
    private ArrayList<MarkerListener> mMarkerListeners =
            new ArrayList<MarkerListener>();
    private AndworxContext objectFactory;
    /** Event broker service */
    private IEventBroker eventBroker;
    /**
     * This variable indicates that the job inside parseSdkContent() is currently
     * trying to load the SDK, to avoid re-entrance.
     * To check whether this succeeds or not, please see {@link #getSdkLoadStatus()}.
     */
    private volatile boolean mParseSdkContentIsRunning;

    /**
     * The constructor
     */
    public AndmoreAndroidPlugin() {
    	andmoreAndroidPluginHolder.setPlugin(this);
    	safeLog.setPlugin(this);
    	objectFactory = AndworxFactory.instance();
    	targetDataMap = new SdkTargetDataMap<>(objectFactory);
    	objectFactory.getSdkTracker().setTargetLoadStatusMonitor(targetDataMap);
    	objectFactory.put(SdkTargetDataMap.class, targetDataMap);
    	mSdkLoadedStatus = LoadStatus.LOADING;
    	sdkInstallJob = new Job("SDK Install") {
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                try {
                	showSdkInstallWizard();
                } catch (Throwable t) {
                    log(t, "Unknown exception in " + getName());
                    return new Status(IStatus.ERROR, AndmoreAndroidConstants.PLUGIN_ID,
                    		getName() + " failed", t);
                } finally {
                    if (monitor != null) {
                        monitor.done();
                    }
                }
                return Status.OK_STATUS;
            }
        };
        IJobChangeListener listener = new JobChangeAdapter(){
			@Override
			public void done(IJobChangeEvent event) {
		        String sdkLocation = objectFactory.getAndroidSdkPreferences().getSdkLocationValue();
		        if (!sdkLocation.trim().isEmpty() &&
		        	checkSdkLocationAndId())
		        	parseSdkContent(0);
			}};
		sdkInstallJob.addJobChangeListener(listener );
		sdkInstallJob.setPriority(Job.INTERACTIVE);
	    BaseContext baseContext = BasePlugin.getBaseContext();
    }

    /**
     * Returns the shared instance
     *
     * @return the shared instance
     */
    public static AndmoreAndroidPlugin getDefault() {
        return andmoreAndroidPluginHolder.getPlugin();
    }

    public AdtPrefs getAdtPrefs() {
    	if (adtPrefs == null) {
    		synchronized(this) {
    	    	if (adtPrefs == null) 
    	            adtPrefs = new AdtPrefs(getPreferenceStore());
    		}
    	}
		return adtPrefs;
	}

	/*
     * (non-Javadoc)
     * @see org.eclipse.ui.plugin.AbstractUIPlugin#start(org.osgi.framework.BundleContext)
     */
    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
    	IEclipseContext serviceContext = E4Workbench.getServiceContext();
    	eventBroker = (IEventBroker) serviceContext.get(IEventBroker.class.getName());
    	objectFactory.put(AndroidLaunchController.class, new AndroidLaunchController(objectFactory, eventBroker));

        // set the default android console.
        mAndroidConsole = new MessageConsole("Android", null); //$NON-NLS-1$
        ConsolePlugin.getDefault().getConsoleManager().addConsoles(
                new IConsole[] { mAndroidConsole });

        // get the stream to write in the android console.
        mAndroidConsoleStream = mAndroidConsole.newMessageStream();
        mAndroidConsoleErrorStream = mAndroidConsole.newMessageStream();
        // Listen for SDK location changes
        AndroidSdkPreferences sdkPrefs = objectFactory.getAndroidSdkPreferences();
        sdkPrefs.addPropertyChangeListener(
            new IPropertyChangeListener() {
	            @Override
	            public void propertyChange(PropertyChangeEvent event) {
                    // get the SDK location and build id.
                    if (checkSdkLocationAndId()) {
                        reparseSdk();
                    }
                }
	        });
        Job job = new Job("Start bundle " + AndmoreAndroidConstants.PLUGIN_ID) {
			
			@Override
			protected IStatus run(IProgressMonitor monitor) {
		        try { 
					AdtStartup adtStartup = new AdtStartup();
					adtStartup.earlyStartup();
					// Post a DebuggerConnector object to DDMS
					eventBroker.post(AndworxEvents.DEVICE_DEBUG_REQUEST, new DebuggerConnector());
			
			        // initialize property-sheet library
			        DesignerPlugin.initialize(
			        		AndmoreAndroidPlugin.this,
			        		AndmoreAndroidConstants.PLUGIN_ID,
			                CURRENT_PLATFORM == PLATFORM_WINDOWS,
			                CURRENT_PLATFORM == PLATFORM_DARWIN,
			                CURRENT_PLATFORM == PLATFORM_LINUX);
			        //AdtStartup.waitForWorkbenchStart();
		        	info( "Workbench started");
			        // Perform operations conditional on workbench has started
			        workbenchStarted();
			        //info("Start editors"); 
			        // initialize editors
			        startEditors();
			
			        // Listen on resource file edits for updates to file inclusion
			        IncludeFinder.start();
		        } catch (Exception e) {
		        	error(e, "Error in %s", getName());
		        	return Status.CANCEL_STATUS;
		        }
		        //info("Job Start bundle completed successfully");
				return Status.OK_STATUS;
			}};
		job.setPriority(Job.LONG);
		AndworxBuildPlugin.instance().scheduleJob(job);
		EventHandler eventHandler = new EventHandler() {
			@Override
			public void handleEvent(Event event) {
				sdkInstallJob.schedule();
			}};
		eventBroker.subscribe(AndworxEvents.INSTALL_SDK_REQUEST, eventHandler);
    }

    /*
     * (non-Javadoc)
     *
     * @see org.eclipse.ui.plugin.AbstractUIPlugin#stop(org.osgi.framework.BundleContext)
     */
    @Override
    public void stop(BundleContext context) throws Exception {
        super.stop(context);
        stopEditors();
        IncludeFinder.stop();

        DesignerPlugin.dispose();

        if (mRed != null) {
            mRed.dispose();
            mRed = null;
        }

        andmoreAndroidPluginHolder.setPlugin(null); 
    }

    /** Operations conditional on the workbench having been started */
    public void workbenchStarted() {
    	Display display = getDisplay();
    	mRed = new Color(display, 0xFF, 0x00, 0x00);
        //info("Setting error console color");
        // because this can be run, in some cases, by a non ui thread, and because
        // changing the console properties update the ui, we need to make this change
        // in the ui thread.
        display.asyncExec(new Runnable() {
            @Override
            public void run() {
                mAndroidConsoleErrorStream.setColor(mRed);
            }
        });
        // load preferences.
        getAdtPrefs().loadValues(null /*event*/);
        // Parse the SDK content.
        // This is deferred in separate jobs to avoid blocking the bundle start.
        if (objectFactory.getAndroidEnvironment().isValid()) {
        	//info("Parsing sdk content.");
            // parse the SDK resources.
            // Wait 2 seconds before starting the job. This leaves some time to the
            // other bundles to initialize.
            parseSdkContent(2000 /*milliseconds*/);
        } else {
        	checkSdkLocationAndId();
        }
    }

    /**
     * Returns the current display, if any
     *
     * @return the display
     */
    @NonNull
    public static Display getDisplay() {
    	AndmoreAndroidPlugin plugin = andmoreAndroidPluginHolder.getPlugin();
    	if (plugin != null) 
	        synchronized (AndmoreAndroidPlugin.class) {
                IWorkbench bench = plugin.getWorkbench();
                if (bench != null) {
                    Display display = bench.getDisplay();
                    if (display != null) {
                        return display;
                    }
                }
	        }

        Display display = Display.getCurrent();
        if (display != null) {
            return display;
        }

        return Display.getDefault();
    }

    /**
     * Returns the shell, if any
     *
     * @return the shell, if any
     */
    @Nullable
    public static Shell getShell() {
        Display display = AndmoreAndroidPlugin.getDisplay();
        Shell[] shell = new Shell[] {null};
        display.syncExec(new Runnable() {

			@Override
			public void run() {
				shell[0] = display.getActiveShell();
		        if (shell[0] == null) {
		            Shell[] shells = display.getShells();
		            if (shells.length > 0) {
		                shell[0] = shells[0];
		            }
		        }
			}});
        return shell[0];
    }

    /** Returns the adb path relative to the sdk folder */
    public static String getOsRelativeAdb() {
        return SdkConstants.OS_SDK_PLATFORM_TOOLS_FOLDER + SdkConstants.FN_ADB;
    }

    /** Returns the emulator path relative to the sdk folder */
    public static String getOsRelativeEmulator() {
        return SdkConstants.OS_SDK_TOOLS_FOLDER + SdkConstants.FN_EMULATOR;
    }

    /** Returns the adb path relative to the sdk folder */
    public static String getOsRelativeProguard() {
        return SdkConstants.OS_SDK_TOOLS_PROGUARD_BIN_FOLDER + SdkConstants.FN_PROGUARD;
    }

    /** Returns the absolute adb path */
    public String getOsAbsoluteAdb() {
        return getOsSdkFolder() + getOsRelativeAdb();
    }

    /** Returns the absolute traceview path */
    public String getOsAbsoluteTraceview() {
        return getOsSdkFolder() + SdkConstants.OS_SDK_TOOLS_FOLDER +
                AndmoreAndroidConstants.FN_TRACEVIEW;
    }

    /** Returns the absolute emulator path */
    public String getOsAbsoluteEmulator() {
        return getOsSdkFolder() + getOsRelativeEmulator();
    }

    public String getOsAbsoluteHprofConv() {
        return getOsSdkFolder() + SdkConstants.OS_SDK_PLATFORM_TOOLS_FOLDER +
                AndmoreAndroidConstants.FN_HPROF_CONV;
    }

    /** Returns the absolute proguard path */
    public String getOsAbsoluteProguard() {
        return getOsSdkFolder() + getOsRelativeProguard();
    }

    /**
     * Returns a Url file path to the javaDoc folder.
     */
    public String getUrlDoc() {
    	AndroidEnvironment androidEnv = objectFactory.getAndroidEnvironment();
    	if (!androidEnv.isValid())
    		throw new AndworxException(SdkProfile.SDK_NOT_AVAILABLE_ERROR);
    	File javaDocFolder = new File(androidEnv.getAndroidSdkHandler().getLocation(), AndmoreAndroidConstants.WS_JAVADOC_FOLDER_LEAF);
        return ProjectHelper.getJavaDocPath(javaDocFolder.getAbsolutePath());
    }

    /**
     * Returns the SDK folder terminated with path separator.
     * Guaranteed to be terminated by a platform-specific path separator.
     */
    public String getOsSdkFolder() {
    	File sdkPath = objectFactory.getAndroidSdkPreferences().getSdkLocation();
    	if (sdkPath == null)
    		throw new IllegalStateException("Android SDK Preferences not available");
    	String sdkPathValue = sdkPath.getAbsolutePath();
        if (sdkPathValue.endsWith(File.separator))
        	return sdkPathValue;
        return sdkPathValue + File.separator;
    }

    //public String getOsSdkToolsFolder() {
    //    return getOsSdkFolder() + SdkConstants.OS_SDK_TOOLS_FOLDER;
    //}

    /**
     * Returns an image descriptor for the image file at the given
     * plug-in relative path
     *
     * @param path the path
     * @return the image descriptor
     */
    public static ImageDescriptor getImageDescriptor(String path) {
        return imageDescriptorFromPlugin(AndmoreAndroidConstants.PLUGIN_ID, path);
    }

    /**
     * Reads the contents of an {@link IFile} and return it as a String
     *
     * @param file the file to be read
     * @return the String read from the file, or null if there was an error
     */
    @Nullable
    public static String readFile(@NonNull IFile file) {
        InputStream contents = null;
        InputStreamReader reader = null;
        try {
            contents = file.getContents();
            String charset = file.getCharset();
            reader = new InputStreamReader(contents, charset);
            return readFile(reader);
        } catch (CoreException e) {
            // pass -- ignore files we can't read
        } catch (IOException e) {
            // pass -- ignore files we can't read.

            // Note that IFile.getContents() indicates it throws a CoreException but
            // experience shows that if the file does not exists it really throws
            // IOException.
            // New InputStreamReader() throws UnsupportedEncodingException
            // which is handled by this IOException catch.

        } finally {
        	try {
				Closeables.close(reader, true);
				Closeables.close(contents, true);
			} catch (IOException e) {
			}
        }

        return null;
    }

    /**
     * Reads the contents of an {@link File} and return it as a String
     *
     * @param file the file to be read
     * @return the String read from the file, or null if there was an error
     */
    public static String readFile(File file) {
        try {
            return readFile(new FileReader(file));
        } catch (FileNotFoundException e) {
            AndmoreAndroidPlugin.log(e, "Can't read file %1$s", file); //$NON-NLS-1$
        }

        return null;
    }

    /**
     * Writes the given content out to the given {@link File}. The file will be deleted if
     * it already exists.
     *
     * @param file the target file
     * @param content the content to be written into the file
     */
    public static void writeFile(File file, String content) {
        if (file.exists()) {
            file.delete();
        }
        FileWriter fw = null;
        try {
            fw = new FileWriter(file);
            fw.write(content);
        } catch (IOException e) {
            AndmoreAndroidPlugin.log(e, null);
        } finally {
            if (fw != null) {
                try {
                    fw.close();
                } catch (IOException e) {
                    AndmoreAndroidPlugin.log(e, null);
                }
            }
        }
    }

    /**
     * Returns true iff the given file contains the given String.
     *
     * @param file the file to look for the string in
     * @param string the string to be searched for
     * @return true if the file is found and contains the given string anywhere within it
     */
    @SuppressWarnings("resource") // Closed by streamContains
    public static boolean fileContains(IFile file, String string) {
        InputStream contents = null;
        try {
            contents = file.getContents();
            String charset = file.getCharset();
            return streamContains(new InputStreamReader(contents, charset), string);
        } catch (Exception e) {
            AndmoreAndroidPlugin.log(e, "Can't read file %1$s", file); //$NON-NLS-1$
        }

        return false;
    }

    /**
     * Returns true iff the given file contains the given String.
     *
     * @param file the file to look for the string in
     * @param string the string to be searched for
     * @return true if the file is found and contains the given string anywhere within it
     */
    public static boolean fileContains(File file, String string) {
        try {
            return streamContains(new FileReader(file), string);
        } catch (Exception e) {
            AndmoreAndroidPlugin.log(e, "Can't read file %1$s", file); //$NON-NLS-1$
        }

        return false;
    }

    /**
     * Returns true iff the given input stream contains the given String.
     *
     * @param r the stream to look for the string in
     * @param string the string to be searched for
     * @return true if the file is found and contains the given string anywhere within it
     */
    public static boolean streamContains(Reader r, String string) {
        if (string.length() == 0) {
            return true;
        }

        PushbackReader reader = null;
        try {
            reader = new PushbackReader(r, string.length());
            char first = string.charAt(0);
            while (true) {
                int c = reader.read();
                if (c == -1) {
                    return false;
                } else if (c == first) {
                    boolean matches = true;
                    for (int i = 1; i < string.length(); i++) {
                        c = reader.read();
                        if (c == -1) {
                            return false;
                        } else if (string.charAt(i) != (char)c) {
                            matches = false;
                            // Back up the characters that did not match
                            reader.backup(i-1);
                            break;
                        }
                    }
                    if (matches) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            AndmoreAndroidPlugin.log(e, "Can't read stream"); //$NON-NLS-1$
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
                AndmoreAndroidPlugin.log(e, "Can't read stream"); //$NON-NLS-1$
            }
        }

        return false;

    }

    /**
     * A special reader that allows backing up in the input (up to a predefined maximum
     * number of characters)
     * <p>
     * NOTE: This class ONLY works with the {@link #read()} method!!
     */
    private static class PushbackReader extends BufferedReader {
        /**
         * Rolling/circular buffer. Can be a char rather than int since we never store EOF
         * in it.
         */
        private char[] mStorage;

        /** Points to the head of the queue. When equal to the tail, the queue is empty. */
        private int mHead;

        /**
         * Points to the tail of the queue. This will move with each read of the actual
         * wrapped reader, and the characters previous to it in the circular buffer are
         * the most recently read characters.
         */
        private int mTail;

        /**
         * Creates a new reader with a given maximum number of backup characters
         *
         * @param reader the reader to wrap
         * @param max the maximum number of characters to allow rollback for
         */
        public PushbackReader(Reader reader, int max) {
            super(reader);
            mStorage = new char[max + 1];
        }

        @Override
        public int read() throws IOException {
            // Have we backed up? If so we should serve characters
            // from the storage
            if (mHead != mTail) {
                char c = mStorage[mHead];
                mHead = (mHead + 1) % mStorage.length;
                return c;
            }
            assert mHead == mTail;

            // No backup -- read the next character, but stash it into storage
            // as well such that we can retrieve it if we must.
            int c = super.read();
            mStorage[mHead] = (char) c;
            mHead = mTail = (mHead + 1) % mStorage.length;
            return c;
        }

        /**
         * Backs up the reader a given number of characters. The next N reads will yield
         * the N most recently read characters prior to this backup.
         *
         * @param n the number of characters to be backed up
         */
        public void backup(int n) {
            if (n >= mStorage.length) {
                throw new IllegalArgumentException("Exceeded backup limit");
            }
            assert n < mStorage.length;
            mHead -= n;
            if (mHead < 0) {
                mHead += mStorage.length;
            }
        }
    }

    /**
     * Reads the contents of a {@link ResourceFile} and returns it as a String
     *
     * @param file the file to be read
     * @return the contents as a String, or null if reading failed
     */
    public static String readFile(ResourceFile file) {
        InputStream contents = null;
        try {
            contents = file.getFile().getContents();
            return readFile(new InputStreamReader(contents));
        } catch (StreamException e) {
            // pass -- ignore files we can't read
        } finally {
            try {
                if (contents != null) {
                    contents.close();
                }
            } catch (IOException e) {
                AndmoreAndroidPlugin.log(e, "Can't read layout file"); //$NON-NLS-1$
            }
        }

        return null;
    }

    /**
     * Reads the contents of a {@link Reader} and return it as a String. This
     * method will close the input reader.
     *
     * @param reader the reader to be read from
     * @return the String read from reader, or null if there was an error
     */
    public static String readFile(Reader reader) {
        BufferedReader bufferedReader = null;
        try {
            bufferedReader = new BufferedReader(reader);
            StringBuilder sb = new StringBuilder(2000);
            while (true) {
                int c = bufferedReader.read();
                if (c == -1) {
                    return sb.toString();
                } else {
                    sb.append((char)c);
                }
            }
        } catch (IOException e) {
            // pass -- ignore files we can't read
        } finally {
            try {
                if (bufferedReader != null) {
                    bufferedReader.close();
                }
            } catch (IOException e) {
                AndmoreAndroidPlugin.log(e, "Can't read input stream"); //$NON-NLS-1$
            }
        }

        return null;
    }

    /**
     * Reads and returns the content of a text file embedded in the plugin jar
     * file.
     * @param filepath the file path to the text file
     * @return null if the file could not be read
     */
    public static String readEmbeddedTextFile(String filepath) {
    	InputStream is = null;
        try {
            is = readEmbeddedFileAsStream(filepath);
            if (is != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                try {
                    String line;
                    StringBuilder total = new StringBuilder(reader.readLine());
                    while ((line = reader.readLine()) != null) {
                        total.append('\n');
                        total.append(line);
                    }

                    return total.toString();
                } finally {
                    reader.close();
                }
            }
        } catch (IOException e) {
            AndmoreAndroidPlugin.log(e, "Failed to read text file '%s'", filepath);  //$NON-NLS-1$
        } finally {
        	if (is != null) {
        		try {
					is.close();
				} catch (IOException e) {
				}
        	}
        }

        return null;
    }

    /**
     * Reads and returns the content of a binary file embedded in the plugin jar
     * file.
     * @param filepath the file path to the text file
     * @return null if the file could not be read
     */
    public static byte[] readEmbeddedFile(String filepath) {
    	InputStream is = null;
        try {
            is = readEmbeddedFileAsStream(filepath);
            if (is != null) {
                // create a buffered reader to facilitate reading.
                BufferedInputStream stream = new BufferedInputStream(is);
                try {
                    // get the size to read.
                    int avail = stream.available();

                    // create the buffer and reads it.
                    byte[] buffer = new byte[avail];
                    stream.read(buffer);

                    // and return.
                    return buffer;
                } finally {
                    stream.close();
                }
            }
        } catch (IOException e) {
            // we'll just return null;.
            AndmoreAndroidPlugin.log(e, "Failed to read binary file '%s'", filepath);  //$NON-NLS-1$
        } finally {
        	if (is != null) {
        		try {
					is.close();
				} catch (IOException e) {
				}
        	}
        }

        return null;
    }

    /**
     * Reads and returns the content of a binary file embedded in the plugin jar
     * file.
     * @param filepath the file path to the text file
     * @return null if the file could not be read
     */
    public static InputStream readEmbeddedFileAsStream(String filepath) {
        // attempt to read an embedded file
        try {
            URL url = getEmbeddedFileUrl(AndmoreAndroidConstants.WS_SEP + filepath);
            if (url != null) {
                return url.openStream();
            }
        } catch (MalformedURLException e) {
            // we'll just return null.
            AndmoreAndroidPlugin.log(e, "Failed to read stream '%s'", filepath);  //$NON-NLS-1$
        } catch (IOException e) {
            // we'll just return null;.
            AndmoreAndroidPlugin.log(e, "Failed to read stream '%s'", filepath);  //$NON-NLS-1$
        }

        return null;
    }

    /**
     * Returns the URL of a binary file embedded in the plugin jar file.
     * @param filepath the file path to the text file
     * @return null if the file was not found.
     */
    public static URL getEmbeddedFileUrl(String filepath) {
        Bundle bundle = null;
        AndmoreAndroidPlugin plugin = andmoreAndroidPluginHolder.getPlugin();
        if (plugin != null) 
	        synchronized (AndmoreAndroidPlugin.class) {
	            bundle = plugin.getBundle();
            } else {
                AndmoreAndroidPlugin.log(IStatus.WARNING, "ADT Plugin is missing");    //$NON-NLS-1$
                return null;
            }
	
        // attempt to get a file to one of the template.
        String path = filepath;
        if (!path.startsWith(AndmoreAndroidConstants.WS_SEP)) {
            path = AndmoreAndroidConstants.WS_SEP + path;
        }

        URL url = bundle.getEntry(path);

        if (url == null) {
            AndmoreAndroidPlugin.log(IStatus.INFO, "Bundle file URL not found at path '%s'", path); //$NON-NLS-1$
        }

        return url;
    }

    /**
     * Displays an error dialog box. This dialog box is ran asynchronously in the ui thread,
     * therefore this method can be called from any thread.
     * @param title The title of the dialog box
     * @param message The error message
     */
    public final static void displayError(final String title, final String message) {
        // get the current Display
        final Display display = getDisplay();

        // dialog box only run in ui thread..
        display.asyncExec(new Runnable() {
            @Override
            public void run() {
                Shell shell = display.getActiveShell();
                MessageDialog.openError(shell, title, message);
            }
        });
    }

    /**
     * Displays a warning dialog box. This dialog box is ran asynchronously in the ui thread,
     * therefore this method can be called from any thread.
     * @param title The title of the dialog box
     * @param message The warning message
     */
    public final static void displayWarning(final String title, final String message) {
        // get the current Display
        final Display display = getDisplay();

        // dialog box only run in ui thread..
        display.asyncExec(new Runnable() {
            @Override
            public void run() {
                Shell shell = display.getActiveShell();
                MessageDialog.openWarning(shell, title, message);
            }
        });
    }

    /**
     * Display a yes/no question dialog box. This dialog is opened synchronously in the ui thread,
     * therefore this message can be called from any thread.
     * @param title The title of the dialog box
     * @param message The error message
     * @return true if OK was clicked.
     */
    public final static boolean displayPrompt(final String title, final String message) {
        // get the current Display and Shell
        final Display display = getDisplay();

        // we need to ask the user what he wants to do.
        final boolean[] result = new boolean[1];
        display.syncExec(new Runnable() {
            @Override
            public void run() {
                Shell shell = display.getActiveShell();
                result[0] = MessageDialog.openQuestion(shell, title, message);
            }
        });
        return result[0];
    }

    /**
     * Logs a message to the default Eclipse log.
     *
     * @param severity The severity code. Valid values are: {@link IStatus#OK},
     * {@link IStatus#ERROR}, {@link IStatus#INFO}, {@link IStatus#WARNING} or
     * {@link IStatus#CANCEL}.
     * @param format The format string, like for {@link String#format(String, Object...)}.
     * @param args The arguments for the format string, like for
     * {@link String#format(String, Object...)}.
     */
    public static void log(int severity, String format, Object ... args) {
        if (format == null) {
            return;
        }
        safeLog.log(severity, format, args);
    }

    /**
     * Logs an exception to the default Eclipse log.
     * <p/>
     * The status severity is always set to ERROR.
     *
     * @param exception the exception to log.
     * @param format The format string, like for {@link String#format(String, Object...)}.
     * @param args The arguments for the format string, like for
     * {@link String#format(String, Object...)}.
     */
    public static void log(Throwable exception, String format, Object ... args) {
        safeLog.log(exception, format, args);
    }

    /**
     * Returns whether the {@link IAndroidTarget}s have been loaded from the SDK.
     */
    public final LoadStatus getSdkLoadStatus() {
        synchronized (Sdk.getLock()) {
        	AndroidEnvironment androidEnv = objectFactory.getAndroidEnvironment();
        	if (androidEnv.isValid() && androidEnv.getAndroidTargets().size() > 0) {
        		mSdkLoadedStatus = LoadStatus.LOADED;
        	}
            return mSdkLoadedStatus !=  null ? mSdkLoadedStatus : LoadStatus.FAILED;
        }
    }

    /**
     * Sets the given {@link IJavaProject} to have its target resolved again once the SDK finishes
     * to load.
     */
    public final void setProjectToResolve(IJavaProject javaProject) {
        synchronized (Sdk.getLock()) {
            mPostLoadProjectsToResolve.add(javaProject);
        }
    }

    /**
     * Sets the given {@link IJavaProject} to have its target checked for consistency
     * once the SDK finishes to load. This is used if the target is resolved using cached
     * information while the SDK is loading.
     */
    public final void setProjectToCheck(IJavaProject javaProject) {
        // only lock on
        synchronized (Sdk.getLock()) {
            mPostLoadProjectsToCheck.add(javaProject);
        }
    }

    /**
     * Checks the location of the SDK in the prefs is valid.
     * If it is not, display a warning dialog to the user and try to display
     * some useful link to fix the situation (setup the preferences, perform an
     * update, etc.)
     *
     * @return True if the SDK location points to an SDK.
     *  If false, the user has already been presented with a modal dialog explaining that.
     */
    private boolean checkSdkLocationAndId() {
        File sdkLocation = null;
        AndroidSdkPreferences preferences = objectFactory.getAndroidSdkPreferences();
        if (preferences.isSdkSpecified())
        	sdkLocation = preferences.getSdkLocation();
        AndroidSdkValidator androidSdkValidator = new AndroidSdkValidator(preferences);
        return androidSdkValidator.checkSdkLocationAndId(sdkLocation, new CheckSdkErrorHandler() {
            private String mTitle = "Android SDK";

            /**
             * Handle an error, which is the case where the check did not find any SDK.
             * This returns false to {@link AndmoreAndroidPlugin#checkSdkLocationAndId()}.
             */
            @Override
            public boolean handleError(Solution solution, String message) {
                displayMessage(solution, message, MessageDialog.ERROR);
                return false;
            }

            /**
             * Handle an warning, which is the case where the check found an SDK
             * but it might need to be repaired or is missing an expected component.
             *
             * This returns true to {@link AndmoreAndroidPlugin#checkSdkLocationAndId()}.
             */
            @Override
            public boolean handleWarning(Solution solution, String message) {
                displayMessage(solution, message, MessageDialog.WARNING);
                return true;
            }

            private void displayMessage(
                    final Solution solution,
                    final String message,
                    final int dialogImageType) {
                final Display disp = getDisplay();
                disp.asyncExec(new Runnable() {
                    @Override
                    public void run() {
                        Shell shell = disp.getActiveShell();
                        if (shell == null) {
                            shell = AndmoreAndroidPlugin.getShell();
                        }
                        if (shell == null) {
                            return;
                        }

                        String customLabel = null;
                        switch(solution) {
                        case OPEN_ANDROID_PREFS:
                            customLabel = "Open Preferences";
                            break;
                        case OPEN_P2_UPDATE:
                            customLabel = "Check for Updates";
                            break;
                        case OPEN_SDK_MANAGER:
                            customLabel = "Open SDK Manager";
                            break;
						default:
							break;
                        }

                        String btnLabels[] = new String[customLabel == null ? 1 : 2];
                        btnLabels[0] = customLabel;
                        btnLabels[btnLabels.length - 1] = IDialogConstants.CLOSE_LABEL;

                        MessageDialog dialog = new MessageDialog(
                                shell, // parent
                                mTitle,
                                null, // dialogTitleImage
                                message,
                                dialogImageType,
                                btnLabels,
                                btnLabels.length - 1);
                        int index = dialog.open();

                        if (customLabel != null && index == 0) {
                            switch(solution) {
                            case OPEN_ANDROID_PREFS:
                                openAndroidPrefs();
                                break;
                            case OPEN_P2_UPDATE:
                                openP2Update();
                                break;
                            case OPEN_SDK_MANAGER:
                                openSdkManager();
                                break;
							default:
								break;
                            }
                        }
                    }
                });
            }

            private void openSdkManager() {
                // Open the standalone external SDK Manager since we know
                // that ADT on Windows is bound to be locking some SDK folders.
                //
                // Also when this is invoked because SdkManagerAction.run() fails, this
                // test will fail and we'll fallback on using the internal one.
                if (SdkManagerAction.openExternalSdkManager()) {
                    return;
                }

                // Otherwise open the regular SDK Manager bundled within ADT
                if (!SdkManagerAction.openAdtSdkManager()) {
                    // We failed because the SDK location is undefined. In this case
                    // let's open the preferences instead.
                    openAndroidPrefs();
                }
            }

            private void openP2Update() {
                Display disp = getDisplay();
                
                disp.asyncExec(new Runnable() {
                    @Override
                    public void run() {
                        String cmdId = "org.eclipse.equinox.p2.ui.sdk.update";  //$NON-NLS-1$
                        IWorkbench wb = PlatformUI.getWorkbench();
                        if (wb == null) {
                            return;
                        }

                        ICommandService cs = (ICommandService) wb.getService(ICommandService.class);
                        IHandlerService is = (IHandlerService) wb.getService(IHandlerService.class);
                        if (cs == null || is == null) {
                            return;
                        }

                        Command cmd = cs.getCommand(cmdId);
                        if (cmd != null && cmd.isDefined()) {
                            try {
                                is.executeCommand(cmdId, null/*event*/);
                            } catch (Exception ignore) {
                                AndmoreAndroidPlugin.log(ignore, "Failed to execute command %s", cmdId);
                            }
                        }
                    }
                });
            }

            private void openAndroidPrefs() {
                PreferenceDialog dialog = PreferencesUtil.createPreferenceDialogOn(
                        getDisplay().getActiveShell(),
                        "org.eclipse.andmore.preferences.main", //$NON-NLS-1$ preferencePageId
                        null,  // displayedIds
                        null); // data
                dialog.open();
            }
        });
    }

    private void showSdkInstallWizard() {
        final IWorkbench workbench = PlatformUI.getWorkbench();
		PluginResourceProvider resourceProvider = new PluginResourceProvider(){
			@Override
			public ImageDescriptor descriptorFromPath(String imagePath) {
				return SdkUserInterfacePlugin.instance().getImageDescriptor("icons/" + imagePath);
			}};
		SdkInstaller sdkInstaller = new SdkInstaller(new AdtConsoleSdkLog());
		SelectSdkWizard selectSdkWizard = new SelectSdkWizard(resourceProvider, sdkInstaller, "" /* no initial selection */);
		selectSdkWizard.setSdkSelectionListener(new SdkSelectionListener(){
			@Override
			public void onSdkSelectionChange(File newSdkLocation) {
				try {
					sdkInstaller.updateSdkLocation(newSdkLocation);
				} catch (IOException e) {
					sdkInstaller.getLogger().error(e, SDK_LOC_SAVE_ERROR);
					onSelectionError(SDK_LOC_SAVE_ERROR);
				}
			}
			@Override
			public void onSelectionError(String message) {
				Display.getDefault().syncExec(new Runnable(){
					@Override
					public void run() {
		                IWorkbenchWindow window = workbench.getActiveWorkbenchWindow();
						MessageDialog.openError(window.getShell(), SelectSdkWizard.WINDOW_TITLE, message);
					}});
			}});
        workbench.getDisplay().asyncExec(new Runnable() {
            @Override
            public void run() {
                IWorkbenchWindow window = workbench.getActiveWorkbenchWindow();
	    		WizardDialog dialog = new WizardDialog(window.getShell(), selectSdkWizard);
                dialog.open();
            }
        });
    }
    
    /**
     * Parses the SDK resources.
     */
    private void parseSdkContent(long delay) {
        // Perform the update in a thread (here an Eclipse runtime job)
        // since this should never block the caller (especially the start method)
    	//info("Creating load SDK job");
        Job job = new Job(Messages.AdtPlugin_Android_SDK_Content_Loader) {
            @SuppressWarnings("unchecked")
            @Override
            protected IStatus run(IProgressMonitor monitor) {
         
                try {

                    if (mParseSdkContentIsRunning) {
                        return new Status(IStatus.WARNING, AndmoreAndroidConstants.PLUGIN_ID,
                                "An Android SDK is already being loaded. Please try again later.");
                    }
                    mParseSdkContentIsRunning = true;
                    SubMonitor progress = SubMonitor.convert(monitor,
                            "Initialize SDK Manager", 100);
                    AndroidSdkPreferences sdkPrefs = objectFactory.getAndroidSdkPreferences();
                    Sdk sdk = Sdk.loadSdk(sdkPrefs.getSdkLocationValue());
                    if (sdk != null) {
                        // Post SDK location to DDMS 
    			        eventBroker.post(AndworxEvents.SDK_LOADED, sdk.getSdkFileLocation());
                        Collection<IAndroidTarget> targets = objectFactory.getAndroidEnvironment().getAndroidTargets();
                        synchronized (Sdk.getLock()) {
                            mSdkLoadedStatus = LoadStatus.LOADED;
                            //progress.setTaskName("Check Projects");

                        }
                        for (IAndroidTarget target: targets) 
                        	if (target.isPlatform())
                        		onTargetAvailable(target);
                        // check the projects that need checking.
                        // The method modifies the list (it removes the project that
                        // do not need to be resolved again).
                        //info("Checking projects");
                        AndroidClasspathContainerInitializer containerInitializer = 
                        		objectFactory.get(AndroidClasspathContainerInitializer.class);
                        if (containerInitializer != null)
                        	containerInitializer.checkProjectsCache(mPostLoadProjectsToCheck);
                        //info("Updating projects");
                        // update the project that needs recompiling.
                        if (!mPostLoadProjectsToCheck.isEmpty()) {
                            IJavaProject[] array = mPostLoadProjectsToCheck.toArray(
                                    new IJavaProject[mPostLoadProjectsToCheck.size()]);
                            ProjectHelper.updateProjects(array);
                        }
                        progress.worked(10);
                    } else {
                        // SDK failed to Load!
                        // Sdk#loadSdk() has already displayed an error.
                        synchronized (Sdk.getLock()) {
                            mSdkLoadedStatus = LoadStatus.FAILED;
                        }
                    }

                    // Notify resource changed listeners
                    progress.setTaskName("Refresh UI");
                    progress.setWorkRemaining(mTargetChangeListeners.size());

                    // Clone the list before iterating, to avoid ConcurrentModification
                    // exceptions
                    final List<ITargetChangeListener> listeners =
                        (List<ITargetChangeListener>)mTargetChangeListeners.clone();
                    final SubMonitor progress2 = progress;
                    //info("Notifying SDK listeners");
                    AndmoreAndroidPlugin.getDisplay().asyncExec(new Runnable() {
                        @Override
                        public void run() {
                            for (ITargetChangeListener listener : listeners) {
                                try {
                                    listener.onSdkLoaded();
                                } catch (Exception e) {
                                    AndmoreAndroidPlugin.log(e, "Failed to update a TargetChangeListener."); //$NON-NLS-1$
                                } finally {
                                    progress2.worked(1);
                                }
                            }
                        }
                    });
                } catch (Throwable t) {
                    log(t, "Unknown exception in parseSdkContent.");    //$NON-NLS-1$
                    return new Status(IStatus.ERROR, AndmoreAndroidConstants.PLUGIN_ID,
                            "parseSdkContent failed", t);               //$NON-NLS-1$

                } finally {
                    mParseSdkContentIsRunning = false;
                    if (monitor != null) {
                        monitor.done();
                    }
                }
                //info("Parse SDK job completed");
                return Status.OK_STATUS;
            }
        };
        //info("Set parse SDK job priority");
        job.setPriority(Job.BUILD); // build jobs are run after other interactive jobs
        job.setRule(ResourcesPlugin.getWorkspace().getRoot());
        //info("Scheuling SDK job priority");
        if (delay > 0) {
            job.schedule(delay);
        } else {
            job.schedule();
        }
    }

    /** Returns the global android console */
    public MessageConsole getAndroidConsole() {
        return mAndroidConsole;
    }

    // ----- Methods for Editors -------

    public void startEditors() {
        sAndroidLogoDesc = imageDescriptorFromPlugin(AndmoreAndroidConstants.PLUGIN_ID,
                "/icons/android.png"); //$NON-NLS-1$
        sAndroidLogo = sAndroidLogoDesc.createImage();

        // Add a resource listener to handle compiled resources.
        IWorkspace ws = ResourcesPlugin.getWorkspace();
        mResourceMonitor = GlobalProjectMonitor.startMonitoring(ws);

        if (mResourceMonitor != null) {
            try {
                setupEditors(mResourceMonitor);
                ResourceManager.setup(mResourceMonitor);
                LintDeltaProcessor.startListening(mResourceMonitor);
            } catch (Throwable t) {
                log(t, "ResourceManager.setup failed"); //$NON-NLS-1$
            }
        }
    }

    /**
     * The <code>AbstractUIPlugin</code> implementation of this <code>Plugin</code>
     * method saves this plug-in's preference and dialog stores and shuts down
     * its image registry (if they are in use). Subclasses may extend this
     * method, but must send super <b>last</b>. A try-finally statement should
     * be used where necessary to ensure that <code>super.shutdown()</code> is
     * always done.
     *
     * @see org.eclipse.ui.plugin.AbstractUIPlugin#stop(org.osgi.framework.BundleContext)
     */
    public void stopEditors() {
    	if (sAndroidLogo != null) {
    		sAndroidLogo.dispose();
            LintDeltaProcessor.stopListening(mResourceMonitor);
        }

        IconFactory.getInstance().dispose();



        // Remove the resource listener that handles compiled resources.
        IWorkspace ws = ResourcesPlugin.getWorkspace();
        GlobalProjectMonitor.stopMonitoring(ws);

        if (mRed != null) {
            mRed.dispose();
            mRed = null;
        }
    }

    /**
     * Returns an Image for the small Android logo.
     *
     * Callers should not dispose it.
     */
    public static Image getAndroidLogo() {
        return sAndroidLogo;
    }

    /**
     * Returns an {@link ImageDescriptor} for the small Android logo.
     *
     * Callers should not dispose it.
     */
    public static ImageDescriptor getAndroidLogoDesc() {
        return sAndroidLogoDesc;
    }

    /**
     * Returns the ResourceMonitor object.
     */
    public GlobalProjectMonitor getResourceMonitor() {
        return mResourceMonitor;
    }

    /**
     * Sets up the editor resource listener.
     * <p>
     * The listener handles:
     * <ul>
     * <li> Discovering newly created files, and ensuring that if they are in an Android
     *      project, they default to the right XML editor.
     * <li> Discovering deleted files, and closing the corresponding editors if necessary.
     *      This is only done for XML files, since other editors such as Java editors handles
     *      it on their own.
     * <ul>
     *
     * This is called by the {@link AndmoreAndroidPlugin} during initialization.
     *
     * @param monitor The main Resource Monitor object.
     */
    public void setupEditors(GlobalProjectMonitor monitor) {
        monitor.addFileListener(new IFileListener() {
            @Override
            public void fileChanged(@NonNull IFile file, @NonNull IMarkerDelta[] markerDeltas,
                    int kind, @Nullable String extension, int flags, boolean isAndroidProject) {
                if (!isAndroidProject) {
                    return;
                }
                if (flags == IResourceDelta.MARKERS || !SdkConstants.EXT_XML.equals(extension)) {
                    // ONLY the markers changed, or not XML file: not relevant to this listener
                    return;
                }

                if (kind == IResourceDelta.REMOVED) {
                    AdtUtils.closeEditors(file, false /*save*/);
                    return;
                }

                // The resources files must have a file path similar to
                //    project/res/.../*.xml
                // There is no support for sub folders, so the segment count must be 4
                if (file.getFullPath().segmentCount() == 4) {
                    // check if we are inside the res folder.
                    String segment = file.getFullPath().segment(1);
                    if (segment.equalsIgnoreCase(SdkConstants.FD_RESOURCES)) {
                        // we are inside a res/ folder, get the ResourceFolderType of the
                        // parent folder.
                        String[] folderSegments = file.getParent().getName().split(
                                SdkConstants.RES_QUALIFIER_SEP);

                        // get the enum for the resource type.
                        ResourceFolderType type = ResourceFolderType.getTypeByName(
                                folderSegments[0]);

                        if (type != null) {
                            if (kind == IResourceDelta.ADDED) {
                                // A new file {@code /res/type-config/some.xml} was added.
                                // All the /res XML files are handled by the same common editor now.
                                IDE.setDefaultEditor(file, CommonXmlEditor.ID);
                            }
                        } else {
                            // if the res folder is null, this means the name is invalid,
                            // in this case we remove whatever android editors that was set
                            // as the default editor.
                            IEditorDescriptor desc = IDE.getDefaultEditor(file);
                            String editorId = desc.getId();
                            if (editorId.startsWith(AndmoreAndroidConstants.EDITORS_NAMESPACE)) {
                                // reset the default editor.
                                IDE.setDefaultEditor(file, null);
                            }
                        }
                    }
                }
            }
        }, IResourceDelta.ADDED | IResourceDelta.REMOVED);

        monitor.addProjectListener(new IProjectListener() {
            @Override
            public void projectClosed(IProject project) {
                // Close any editors referencing this project
                AdtUtils.closeEditors(project, true /*save*/);
            }

            @Override
            public void projectDeleted(IProject project) {
                // Close any editors referencing this project
                AdtUtils.closeEditors(project, false /*save*/);
            }

            @Override
            public void projectOpenedWithWorkspace(IProject project) {
            }

            @Override
            public void allProjectsOpenedWithWorkspace() {
            }

            @Override
            public void projectOpened(IProject project) {
            }

            @Override
            public void projectRenamed(IProject project, IPath from) {
            }
        });
    }

    /**
     * Adds a new {@link ITargetChangeListener} to be notified when a new SDK is loaded, or when
     * a project has its target changed.
     */
    public void addTargetListener(ITargetChangeListener listener) {
        mTargetChangeListeners.add(listener);
    }

    /**
     * Removes an existing {@link ITargetChangeListener}.
     * @see #addTargetListener(ITargetChangeListener)
     */
    public void removeTargetListener(ITargetChangeListener listener) {
        mTargetChangeListeners.remove(listener);
    }

    /**
     * Adds a new {@link MarkerListener} to be notified when a new SDK is loaded, or when
     * a project has its target changed.
     */
    public void addMarkerListener(MarkerListener listener) {
    	synchronized(mMarkerListeners) {
    		mMarkerListeners.add(listener);
    	}
    }

    /**
     * Removes an existing {@link MarkerListener}.
     * @see #addMarkerListener(MarkerListener)
     */
    public void removeMarkerListener(MarkerListener listener) {
    	synchronized(mMarkerListeners) {
    		mMarkerListeners.remove(listener);
    	}
    }

    public void notifyMarkerListeners(IMarker marker) {
    	synchronized(mMarkerListeners) {
    		for (MarkerListener listener: mMarkerListeners)
    			listener.onMarker(marker);
    	}
    }
    
    /**
     * Updates all the {@link ITargetChangeListener}s that a target has changed for a given project.
     * <p/>Only editors related to that project should reload.
     */
    @SuppressWarnings("unchecked")
    public void updateTargetListeners(final IProject project) {
        final List<ITargetChangeListener> listeners =
            (List<ITargetChangeListener>)mTargetChangeListeners.clone();

        AndmoreAndroidPlugin.getDisplay().asyncExec(new Runnable() {
            @Override
            public void run() {
                for (ITargetChangeListener listener : listeners) {
                    try {
                        listener.onProjectTargetChange(project);
                    } catch (Exception e) {
                        AndmoreAndroidPlugin.log(e, "Failed to update a TargetChangeListener.");  //$NON-NLS-1$
                    }
                }
            }
        });
    }

    /**
     * Updates all the {@link ITargetChangeListener}s that a target data was loaded.
     * <p/>Only editors related to a project using this target should reload.
     */
    @SuppressWarnings("unchecked")
    public void updateTargetListeners(final IAndroidTarget target) {
        final List<ITargetChangeListener> listeners =
            (List<ITargetChangeListener>)mTargetChangeListeners.clone();

        Display display = AndmoreAndroidPlugin.getDisplay();
        if (display == null || display.isDisposed()) {
            return;
        }
        display.asyncExec(new Runnable() {
            @Override
            public void run() {
                for (ITargetChangeListener listener : listeners) {
                    try {
                        listener.onTargetLoaded(target);
                    } catch (Exception e) {
                        AndmoreAndroidPlugin.log(e, "Failed to update a TargetChangeListener.");  //$NON-NLS-1$
                    }
                }
            }
        });
    }

    /**
     * Sets the named persistent property for the given file to the given value
     *
     * @param file the file to associate the property with
     * @param qname the name of the property
     * @param value the new value, or null to clear the property
     */
    public static void setFileProperty(IFile file, QualifiedName qname, String value) {
        try {
            file.setPersistentProperty(qname, value);
        } catch (CoreException e) {
            log(e, "Cannot set property %1$s to %2$s", qname, value);
        }
    }

    /**
     * Gets the named persistent file property from the given file
     *
     * @param file the file to look up properties for
     * @param qname the name of the property to look up
     * @return the property value, or null
     */
    public static String getFileProperty(IFile file, QualifiedName qname) {
        try {
            return file.getPersistentProperty(qname);
        } catch (CoreException e) {
            log(e, "Cannot get property %1$s", qname);
        }

        return null;
    }

    /**
     * Conditionally reparses the content of the SDK if it has changed on-disk
     * and updates opened projects.
     * <p/>
     * The operation is asynchronous and happens in a background eclipse job.
     * <p/>
     * This operation is called in multiple places and should be reasonably
     * cheap and conservative. The goal is to automatically refresh the SDK
     * when it is obvious it has changed so when not sure the code should
     * tend to not reload and avoid reloading too often (which is an expensive
     * operation that has a lot of user impact.)
     */
    public void refreshSdk() {
        // SDK can't have changed if we haven't loaded it yet.
        AndroidEnvironment env = objectFactory.getAndroidEnvironment();
        if (!env.isValid()) 
        	return;

        Job job = new Job("Check Android SDK") {
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                // SDK has changed if its location path is different.
                File location = env.getAndroidSdkHandler().getLocation();
                boolean changed = location == null || !location.isDirectory();

                if (!changed) {
                    assert location != null;
                    AndroidSdkPreferences sdkPrefs = objectFactory.getAndroidSdkPreferences();
                    String sdkLocationValue = sdkPrefs.getSdkLocationValue();
                    if (!sdkLocationValue.isEmpty()) {
                    	File prefLocation = new File(sdkLocationValue);
                    	changed = !location.equals(prefLocation);

	                    if (changed) {
	                        // Basic file path comparison indicates they are not the same.
	                        // Let's dig a bit deeper.
	                        try {
	                            location     = location.getCanonicalFile();
	                            prefLocation = prefLocation.getCanonicalFile();
	                            changed = !location.equals(prefLocation);
	                        } catch (IOException ignore) {
	                            // There's no real reason for the canonicalization to fail
	                            // if the paths map to actual directories. And if they don't
	                            // this should have been caught above.
	                        }
	                    }
                    }
                }

               // if (!changed) {
                    /**
                     * Queries the underlying SDK Manager to check whether the platforms or addons
                     * directories have changed on-disk. Does not reload the SDK.
                     * <p/>
                     * TODO - monitor SDK location for changes so this check is possible
                     */
                //    changed = sdk.haveTargetsChanged();
                //}

                if (changed) {
                    monitor.setTaskName("Reload Android SDK");
                    reparseSdk();
                }

                monitor.done();
                return Status.OK_STATUS;
            }
        };
        job.setRule(ResourcesPlugin.getWorkspace().getRoot());
        job.setPriority(Job.SHORT); // a short background job, not interactive.
        job.schedule();
    }

    /**
     * Reparses the content of the SDK and updates opened projects.
     * The operation is asynchronous and happens in a background eclipse job.
     * <p/>
     * This reloads the SDK all the time. To only perform this when it has potentially
     * changed, call {@link #refreshSdk()} instead.
     */
    public void reparseSdk() {
        // add all the opened Android projects to the list of projects to be updated
        // after the SDK is reloaded
        synchronized (Sdk.getLock()) {
            // get the project to refresh.
            IJavaProject[] androidProjects = BaseProjectHelper.getAndroidProjects(null /*filter*/);
            mPostLoadProjectsToResolve.addAll(Arrays.asList(androidProjects));
        }

        // parse the SDK resources at the new location
        parseSdkContent(0 /*immediately*/);
    }

    public static OutputStream getOutStream() {
        synchronized(AndroidPrintStream.class) {
            return getDefault().mAndroidConsoleStream;
        }
    }

    public static OutputStream getErrorStream() {
        synchronized(AndroidPrintStream.class) {
            return getDefault().mAndroidConsoleErrorStream;
        }
    }

    /**
     * Prints messages, associated with a project to the specified stream
     * @param stream The stream to write to
     * @param tag The tag associated to the message. Can be null
     * @param objects The objects to print through their toString() method (or directly for
     * {@link String} objects.
     */
    public static void printToStream(MessageConsoleStream stream, String tag,
            Object... objects) {
        getDefault().doPrintToStream(stream, tag, objects);
    }

    /**
     * This is a mix between log(Throwable) and printErrorToConsole.
     * <p/>
     * This logs the exception with an ERROR severity and the given printf-like format message.
     * The same message is then printed on the Android error console with the associated tag.
     *
     * @param exception the exception to log.
     * @param format The format string, like for {@link String#format(String, Object...)}.
     * @param args The arguments for the format string, like for
     * {@link String#format(String, Object...)}.
     */
    public static void logAndPrintError(Throwable exception, String tag,
        String format, Object ... args) {
		getDefault().doLogAndPrintError(exception, tag, format, args);
    }

    /**
     * Prints one or more error message to the android console.
     * @param tag A tag to be associated with the message. Can be null.
     * @param objects the objects to print through their <code>toString</code> method.
     */
    public static void printErrorToConsole(String tag, Object... objects) {
    	getDefault().doPrintError(tag, objects);
        showAndroidConsole();
    }

    /**
     * Prints one or more error message to the android console.
     * @param objects the objects to print through their <code>toString</code> method.
     */
    public static void printErrorToConsole(Object... objects) {
        printErrorToConsole((String)null, objects);
    }

    /**
     * Prints one or more error message to the android console.
     * @param project The project to which the message is associated. Can be null.
     * @param objects the objects to print through their <code>toString</code> method.
     */
    public static void printErrorToConsole(IProject project, Object... objects) {
        String tag = project != null ? project.getName() : null;
        printErrorToConsole(tag, objects);
    }

    /**
     * Prints one or more build messages to the android console, filtered by Build output verbosity.
     * @param level {@link BuildVerbosity} level of the message.
     * @param project The project to which the message is associated. Can be null.
     * @param objects the objects to print through their <code>toString</code> method.
     * @see BuildVerbosity#ALWAYS
     * @see BuildVerbosity#NORMAL
     * @see BuildVerbosity#VERBOSE
     */
    public static void printBuildToConsole(BuildVerbosity level, IProject project,
            Object... objects) {
        getDefault().doPrintBuildToConsole(level, project, objects);
    }

    /**
     * Prints one or more message to the android console.
     * @param tag The tag to be associated with the message. Can be null.
     * @param objects the objects to print through their <code>toString</code> method.
     */
    public static void printToConsole(String tag, Object... objects) {
        getDefault().doPrintToConsole(tag, objects);
     }

    /**
     * Prints one or more message to the android console.
     * @param project The project to which the message is associated. Can be null.
     * @param objects the objects to print through their <code>toString</code> method.
     */
    public static void printToConsole(IProject project, Object... objects) {
        String tag = project != null ? project.getName() : null;
        printToConsole(tag, objects);
    }

    /** Force the display of the android console */
    public static void showAndroidConsole() {
        // first make sure the console is in the workbench
        EclipseUiHelper.showView(IConsoleConstants.ID_CONSOLE_VIEW, true);

        // now make sure it's not docked.
        ConsolePlugin.getDefault().getConsoleManager().showConsoleView(
                AndmoreAndroidPlugin.getDefault().getAndroidConsole());
    }

    private void doPrintToStream(MessageConsoleStream stream, String tag,
            Object... objects) {
        String dateTag = AndroidPrintStream.getMessageTag(tag);
        synchronized(AndroidPrintStream.class) {
	        for (Object obj : objects) {
	            stream.print(dateTag);
	            stream.print(" "); //$NON-NLS-1$
	            if (obj instanceof String) {
	                stream.println((String)obj);
	            } else if (obj == null) {
	                stream.println("(null)");  //$NON-NLS-1$
	            } else {
	                stream.println(obj.toString());
	            }
	        }
        }
    }

    private void doLogAndPrintError(Throwable exception, String tag,
            String format, Object ... args) {
            String message = String.format(format, args);
            Status status = new Status(IStatus.ERROR, AndmoreAndroidConstants.PLUGIN_ID, message, exception);
            getLog().log(status);
            doPrintError(tag, message);
            showAndroidConsole();
         }

    private void doPrintBuildToConsole(BuildVerbosity level, IProject project,
            Object... objects) {
        if (level.getLevel() <= getAdtPrefs().getBuildVerbosity().getLevel()) {
            String tag = project != null ? project.getName() : null;
            doPrintToConsole(tag, objects);
        }
    }

    private void doPrintError(String tag, Object... objects) {
        doPrintToStream(mAndroidConsoleErrorStream, tag, objects);
    }

    private void doPrintToConsole(String tag, Object... objects) {
        doPrintToStream(mAndroidConsoleStream, tag, objects);
    }
    // --------- ILogger methods -----------

    @Override
    public void error(@Nullable Throwable t, @Nullable String format, Object... args) {
        if (t != null) {
            log(t, format, args);
        } else {
            log(IStatus.ERROR, format, args);
        }
    }

    @Override
    public void info(@NonNull String format, Object... args) {
        log(IStatus.INFO, format, args);
    }

    @Override
    public void verbose(@NonNull String format, Object... args) {
        log(IStatus.INFO, format, args);
    }

    @Override
    public void warning(@NonNull String format, Object... args) {
        log(IStatus.WARNING, format, args);
    }

    /**
     * Opens the given URL in a browser tab
     *
     * @param url the URL to open in a browser
     */
    public static void openUrl(URL url) {
        IWorkbenchBrowserSupport support = PlatformUI.getWorkbench().getBrowserSupport();
        IWebBrowser browser;
        try {
            browser = support.createBrowser(AndmoreAndroidConstants.PLUGIN_ID);
            browser.openURL(url);
        } catch (PartInitException e) {
            log(e, null);
        }
    }

    /**
     * Opens a Java class for the given fully qualified class name
     *
     * @param project the project containing the class
     * @param fqcn the fully qualified class name of the class to be opened
     * @return true if the class was opened, false otherwise
     */
    public static boolean openJavaClass(IProject project, String fqcn) {
        if (fqcn == null) {
            return false;
        }

        // Handle inner classes
        if (fqcn.indexOf('$') != -1) {
            fqcn = fqcn.replaceAll("\\$", "."); //$NON-NLS-1$ //$NON-NLS-2$
        }

        try {
            if (project.hasNature(JavaCore.NATURE_ID)) {
                IJavaProject javaProject = JavaCore.create(project);
                IJavaElement result = javaProject.findType(fqcn);
                if (result != null) {
                    return JavaUI.openInEditor(result) != null;
                }
            }
        } catch (Throwable e) {
            log(e, "Can't open class %1$s", fqcn); //$NON-NLS-1$
        }

        return false;
    }

    /**
     * For a stack trace entry, specifying a class, method, and optionally
     * fileName and line number, open the corresponding line in the editor.
     *
     * @param fqcn the fully qualified name of the class
     * @param method the method name
     * @param fileName the file name, or null
     * @param lineNumber the line number or -1
     * @return true if the target location could be opened, false otherwise
     */
    public static boolean openStackTraceLine(@Nullable String fqcn,
            @Nullable String method, @Nullable String fileName, int lineNumber) {
        return new SourceRevealer().revealMethod(fqcn + '.' + method, fileName, lineNumber, null);
    }

    /**
     * Opens the given file and shows the given (optional) region in the editor (or
     * if no region is specified, opens the editor tab.)
     *
     * @param file the file to be opened
     * @param region an optional region which if set will be selected and shown to the
     *            user
     * @throws PartInitException if something goes wrong
     */
    public static void openFile(IFile file, IRegion region) throws PartInitException {
        openFile(file, region, true);
    }

    // TODO: Make an openEditor which does the above, and make the above pass false for showEditor

    /**
     * Opens the given file and shows the given (optional) region
     *
     * @param file the file to be opened
     * @param region an optional region which if set will be selected and shown to the
     *            user
     * @param showEditorTab if true, front the editor tab after opening the file
     * @return the editor that was opened, or null if no editor was opened
     * @throws PartInitException if something goes wrong
     */
    public static IEditorPart openFile(IFile file, IRegion region, boolean showEditorTab)
            throws PartInitException {
        IWorkbenchPage page = AdtUtils.getActiveWorkbenchPage();
        if (page == null) {
            return null;
        }
        IEditorPart targetEditor = IDE.openEditor(page, file, true);
        if (targetEditor instanceof AndroidXmlEditor) {
            AndroidXmlEditor editor = (AndroidXmlEditor) targetEditor;
            if (region != null) {
                editor.show(region.getOffset(), region.getLength(), showEditorTab);
            } else if (showEditorTab) {
                editor.setActivePage(AndroidXmlEditor.TEXT_EDITOR_ID);
            }
        } else if (targetEditor instanceof AbstractTextEditor) {
            AbstractTextEditor editor = (AbstractTextEditor) targetEditor;
            if (region != null) {
                editor.setHighlightRange(region.getOffset(), region.getLength(),
                        true /* moveCursor*/);
            }
        }

        return targetEditor;
    }

    private void onTargetAvailable(IAndroidTarget target) {
		Set<IJavaProject> targetReadyProjects = 
				objectFactory.getProjectRegistry().onTargetLoaded(target, mPostLoadProjectsToResolve);
        // Remove projects with loaded targets
        if (!targetReadyProjects.isEmpty()) {
            // update the project that needs recompiling.
            IJavaProject[] array = targetReadyProjects.toArray(
                    new IJavaProject[targetReadyProjects.size()]);
            if (!ProjectHelper.updateProjects(array))
            	error(null, "Failed to update projects");
        	mPostLoadProjectsToResolve.removeAll(targetReadyProjects);
        }
	}
    
}
