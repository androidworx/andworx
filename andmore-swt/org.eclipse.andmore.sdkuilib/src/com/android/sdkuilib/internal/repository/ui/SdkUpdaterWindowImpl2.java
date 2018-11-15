/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.sdkuilib.internal.repository.ui;


import com.android.SdkConstants;
import com.android.repository.api.RemotePackage;
import com.android.sdkuilib.internal.repository.AboutDialog;
import com.android.sdkuilib.internal.repository.Settings;
import com.android.sdkuilib.internal.repository.content.PackageType;

import org.eclipse.andmore.base.resources.ImageFactory;
import com.android.sdkuilib.internal.repository.ui.PackagesPage.MenuAction;
import com.android.sdkuilib.internal.widgets.ImgDisabledButton;
import com.android.sdkuilib.internal.widgets.ToggleButton;
import com.android.sdkuilib.repository.AvdManagerWindow.AvdInvocationContext;
import com.android.sdkuilib.repository.ISdkChangeListener;
import com.android.sdkuilib.repository.SdkUpdaterWindow.SdkInvocationContext;
import com.android.utils.ILogger;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.andmore.sdktool.SdkContext;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Shell;

/**
 * This is the private implementation of the UpdateWindow
 * for the second version of the SDK Manager.
 * <p/>
 * This window features only one embedded page, the combined installed+available package list.
 */
public class SdkUpdaterWindowImpl2 {

    public static final String APP_NAME = "Android SDK Manager";
    private static final String SIZE_POS_PREFIX = "sdkman2"; //$NON-NLS-1$
	private static final String ANDROID_SDK_LOCATION_PROMPT = "Android SDK Location" ;
	private static final String SDK_PATH = "SDK Path: %s";

    private final Shell parentShell;
    /** Application context */
    private final SdkInvocationContext appContext;
    /** SDK handler and repo manager */
    private final SdkContext sdkContext;
	/** Set of package types on which to filter. An empty set indicates no filtering */
	private Set<PackageType> packageTypeSet;
	/** Tag to filter on - applies to system images */
	private String tag;

    // --- UI members ---

    protected Shell shell;
    private PackagesPage packagesPage;
    private ProgressBar progressBar;
    private Label statusText;
    private Label verboseLine;
    private ImgDisabledButton buttonStop;
    private ToggleButton buttonShowLog;
    private LogWindow logWindow;


    /**
     * Creates a new window. Caller must call open(), which will block.
     *
     * @param parentShell Parent shell.
     * @param sdkLog Logger. Cannot be null.
     * @param sdkContext SDK handler and repo manager
     * @param appContext The {@link SdkInvocationContext} to change the behavior depending on who's
     *  opening the SDK Manager.
     */
    public SdkUpdaterWindowImpl2(
            Shell parentShell,
            SdkContext sdkContext,
            SdkInvocationContext appContext) {
        this.parentShell = parentShell;
        this.appContext = appContext;
        this.sdkContext = sdkContext;
    }

    public void addPackageFilter(PackageType packageType) {
    	if (packageTypeSet == null)
    		packageTypeSet = new TreeSet<PackageType>();
    	packageTypeSet.add(packageType);
    }

	public void setTagFilter(String tag) {
		this.tag = tag;
	}

    /**
     * Opens the window.
     * @wbp.parser.entryPoint
     */
    public void open() {
        if (parentShell == null) {
            Display.setAppName(APP_NAME); //$hide$ (hide from SWT designer)
        }

        createShell();
    	File sdkLocation = sdkContext.getLocation();
    	boolean sdkLocationExists = sdkLocation.exists();
    	boolean sdkLocationIsDir = sdkLocation.isDirectory();
    	if (!sdkLocationExists) {
    		sdkContext.getSdkLog().error(null, "Android SDK directory not found: %s", sdkLocation.getPath());
    		sdkLocation = promptForSdkLocation(ANDROID_SDK_LOCATION_PROMPT) ;
    	} else if (!sdkLocationIsDir) {
    		sdkContext.getSdkLog().error(null, "Android SDK location is not a directory: %s", sdkLocation.getPath());
    		sdkLocation = promptForSdkLocation(ANDROID_SDK_LOCATION_PROMPT);
    	}
    	if (sdkLocation == null)
    		return;
    	if (!sdkLocationExists || !sdkLocationIsDir) {
    		sdkContext.setLocation(sdkLocation);
    	}
        preCreateContent();
        createContents();
        createMenuBar();
        createLogWindow();
        shell.open();
        shell.layout();

        if (postCreateContent()) {    //$hide$ (hide from SWT designer)
            Display display = Display.getDefault();
            while (!shell.isDisposed()) {
                if (!display.readAndDispatch()) {
                    display.sleep();
                }
            }
        }
        dispose();  //$hide$
    }

    private void createShell() {
        // The SDK Manager must use a shell trim when standalone
        // or a dialog trim when invoked from somewhere else.
        int style = SWT.SHELL_TRIM;
        if (appContext != SdkInvocationContext.STANDALONE) {
            style |= SWT.APPLICATION_MODAL;
        }

        shell = new Shell(parentShell, style);
        shell.addDisposeListener(new DisposeListener() {
            @Override
            public void widgetDisposed(DisposeEvent e) {
                ShellSizeAndPos.saveSizeAndPos(shell, SIZE_POS_PREFIX);
            }
        });

        GridLayout glShell = new GridLayout(2, false);
        glShell.verticalSpacing = 0;
        glShell.horizontalSpacing = 0;
        glShell.marginWidth = 0;
        glShell.marginHeight = 0;
        shell.setLayout(glShell);

        shell.setMinimumSize(new Point(600, 300));
        shell.setSize(700, 500);
        shell.setText(APP_NAME);

        ShellSizeAndPos.loadSizeAndPos(shell, SIZE_POS_PREFIX);
    }

    private File promptForSdkLocation(String title) {
    	DirectoryDialog dialog = new DirectoryDialog (shell);
    	dialog.setText(title);
    	String platform = SWT.getPlatform();
    	dialog.setFilterPath (platform.equals("win32") ? "c:\\" : "/");
    	String sdkLocation = dialog.open ();
    	if (sdkLocation != null)
    		return new File(sdkLocation);
    	return null;
	}

    private void createContents() {
    	
        packagesPage = new PackagesPage(shell, SWT.NONE, sdkContext, packageTypeSet, tag);
        packagesPage.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1));

        Composite composite1 = new Composite(shell, SWT.NONE);
        composite1.setLayout(new GridLayout(1, false));
        composite1.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));


        statusText = new Label(composite1, SWT.NONE);
        //statusText.setText("Status Placeholder");  //$NON-NLS-1$ placeholder
        statusText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        Composite progressGroup = new Composite(composite1, SWT.NONE);
        progressGroup.setLayout(new GridLayout(2, true));
        progressGroup.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        verboseLine = new Label(progressGroup, SWT.BORDER);
        verboseLine.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        progressBar = new ProgressBar(progressGroup, SWT.NONE);
        progressBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

        Composite composite2 = new Composite(shell, SWT.NONE);
        composite2.setLayout(new GridLayout(1, false));
        composite2.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, false, false, 1, 1));

        /*        buttonStop = new ImgDisabledButton(composite2, SWT.NONE,
                getImage("stop_enabled_16.png"),    //$NON-NLS-1$
                getImage("stop_disabled_16.png"),   //$NON-NLS-1$
                "Click to abort the current task",
                "");                                //$NON-NLS-1$ nothing to abort
        buttonStop.addListener(SWT.Selection, new Listener() {
            @Override
            public void handleEvent(Event event) {
                onStopSelected();
            }
        });
*/
        buttonShowLog = new ToggleButton(composite2, SWT.NONE,
                getImage("log_off_16.png"),         //$NON-NLS-1$
                getImage("log_on_16.png"),          //$NON-NLS-1$
                "Click to show the log window",     // tooltip for state hidden=>shown
                "Click to hide the log window");    // tooltip for state shown=>hidden
        buttonShowLog.addListener(SWT.Selection, new Listener() {
            @Override
            public void handleEvent(Event event) {
                onToggleLogWindow();
            }
        });
    }

    private void createMenuBar() {

        Menu menuBar = new Menu(shell, SWT.BAR);
        shell.setMenuBar(menuBar);

        MenuItem menuBarPackages = new MenuItem(menuBar, SWT.CASCADE);
        menuBarPackages.setText("Packages");

        Menu menuPkgs = new Menu(menuBarPackages);
        menuBarPackages.setMenu(menuPkgs);
        MenuItem showUpdatesNew = new MenuItem(menuPkgs,
                MenuAction.TOGGLE_SHOW_NEW_PKG.getMenuStyle());
        showUpdatesNew.setText(
                MenuAction.TOGGLE_SHOW_NEW_PKG.getMenuTitle());
        packagesPage.registerMenuAction(
                MenuAction.TOGGLE_SHOW_NEW_PKG, showUpdatesNew);

        //MenuItem showInstalled = new MenuItem(menuPkgs,
        //        MenuAction.TOGGLE_SHOW_INSTALLED_PKG.getMenuStyle());
        //showInstalled.setText(
        //        MenuAction.TOGGLE_SHOW_INSTALLED_PKG.getMenuTitle());
        //packagesPage.registerMenuAction(
        //        MenuAction.TOGGLE_SHOW_INSTALLED_PKG, showInstalled);

        MenuItem showObsoletePackages = new MenuItem(menuPkgs,
                MenuAction.TOGGLE_SHOW_OBSOLETE_PKG.getMenuStyle());
        showObsoletePackages.setText(
                MenuAction.TOGGLE_SHOW_OBSOLETE_PKG.getMenuTitle());
        packagesPage.registerMenuAction(
                MenuAction.TOGGLE_SHOW_OBSOLETE_PKG, showObsoletePackages);

        new MenuItem(menuPkgs, SWT.SEPARATOR);

        MenuItem reload = new MenuItem(menuPkgs,
                MenuAction.RELOAD.getMenuStyle());
        reload.setText(
                MenuAction.RELOAD.getMenuTitle());
        packagesPage.registerMenuAction(
                MenuAction.RELOAD, reload);
        
        MenuItem menuBarNetwork = new MenuItem(menuBar, SWT.CASCADE);
        menuBarNetwork.setText("Network");
        Menu menuNetwork = new Menu(menuBarNetwork);
        menuBarNetwork.setMenu(menuNetwork);
        MenuItem forceHttp = new MenuItem(menuNetwork,
                MenuAction.TOGGLE_FORCE_HTTP.getMenuStyle());
        forceHttp.setText(
                MenuAction.TOGGLE_FORCE_HTTP.getMenuTitle());
        packagesPage.registerMenuAction(
                MenuAction.TOGGLE_FORCE_HTTP, forceHttp);

        MenuItem menuBarTools = new MenuItem(menuBar, SWT.CASCADE);
        menuBarTools.setText("Tools");
        Menu menuTools = new Menu(menuBarTools);
        menuBarTools.setMenu(menuTools);
        

        if (appContext == SdkInvocationContext.STANDALONE) {
            MenuItem manageAvds = new MenuItem(menuTools, SWT.NONE);
            manageAvds.setText("Manage AVDs...");
            manageAvds.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent event) {
                    onAvdManager();
                }
            });
        }
        if (appContext == SdkInvocationContext.STANDALONE || appContext == SdkInvocationContext.IDE) {
            MenuItem aboutTools = new MenuItem(menuTools, SWT.NONE);
            aboutTools.setText("&About...");
            aboutTools.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent event) {
                    AboutDialog ad = new AboutDialog(shell, sdkContext);
                    ad.open();
                }
            });
        	/*
            try {
                new MenuBarWrapper(APP_NAME, menuTools) {
                    @Override
                    public void onPreferencesMenuSelected() {

                        // capture a copy of the initial settings
                        Settings settings1 = sdkContext.getSettings().copy();

                        // open the dialog and wait for it to close
                        SettingsDialog sd = new SettingsDialog(shell, sdkContext);
                        sd.open();

                        // get the new settings
                        Settings settings2 = sdkContext.getSettings();

                        // We need to reload the package list if the http mode or the preview
                        // modes have changed.
                        if (settings1.getForceHttp() != settings2.getForceHttp() ||
                                settings1.getEnablePreviews() != settings2.getEnablePreviews()) {
                            packagesPage.onSdkReload();
                        }
                    }

                    @Override
                    public void onAboutMenuSelected() {
                        AboutDialog ad = new AboutDialog(shell, sdkContext);
                        ad.open();
                    }

                    @Override
                    public void printError(String format, Object... args) {
                        if (sdkContext != null) {
                            sdkContext.getSdkLog().error(null, format, args);
                        }
                    }
                };
            } catch (Throwable e) {
                sdkContext.getSdkLog().error(e, "Failed to setup menu bar");
                e.printStackTrace();
            }
            */
        }
    }

    private Image getImage(String filename) {
        if (sdkContext != null) {
            ImageFactory imgFactory = sdkContext.getSdkHelper().getImageFactory();
            if (imgFactory != null) {
                return imgFactory.getImageByName(filename);
            }
        }
        return null;
    }

    /**
     * Creates the log window.
     * <p/>
     * If this is invoked from an IDE, we also define a secondary logger so that all
     * messages flow to the IDE log. This may or may not be what we want in the end
     * (e.g. a middle ground would be to repeat error, and ignore normal/verbose)
     */
    private void createLogWindow() {
        logWindow = new LogWindow(shell,
                appContext == SdkInvocationContext.IDE ? sdkContext.getSdkLog() : null);
        logWindow.open();
    }


    // -- Start of internal part ----------
    // Hide everything down-below from SWT designer
    //$hide>>$

    // --- Public API -----------

    /**
     * Adds a new listener to be notified when a change is made to the content of the SDK.
     */
    public void addListener(ISdkChangeListener listener) {
        sdkContext.getSdkHelper().addListeners(listener);
    }

    /**
     * Removes a new listener to be notified anymore when a change is made to the content of
     * the SDK.
     */
    public void removeListener(ISdkChangeListener listener) {
        sdkContext.getSdkHelper().removeListener(listener);
    }

	public List<RemotePackage> getPackagesInstalled() {
		return packagesPage.getPackagesInstalled();
	}

    // --- Internals & UI Callbacks -----------

    /**
     * Called before the UI is created.
     */
    private void preCreateContent() {
    }

    /**
     * Once the UI has been created, initializes the content.
     * This creates the pages, selects the first one, setups sources and scans for local folders.
     *
     * Returns true if we should show the window.
     */
    private boolean postCreateContent() {

    	File sdkLocation = sdkContext.getLocation();
    	// Only show SDK location if is valid
    	if (sdkLocation.exists() && sdkLocation.isDirectory())
            verboseLine.setText(String.format(SDK_PATH, sdkLocation.toString()));
        // This class delegates all logging to the logWindow window
        // and filters errors to make sure the window is visible when
        // an error is logged.
        SdkProgressFactory.ISdkLogWindow logAdapter = new SdkProgressFactory.ISdkLogWindow() {
            @Override
            public void setDescription(String description) {
                logWindow.setDescription(description);
            }

            @Override
            public void log(String log) {
                logWindow.log(log);
            }

            @Override
            public void logVerbose(String log) {
                logWindow.logVerbose(log);
            }

            @Override
            public void logError(String log) {
                logWindow.logError(log);
            }
            @Override
            public void show()
            {
                // Run the window visibility check/toggle on the UI thread.
                // Note: at least on Windows, it seems ok to check for the window visibility
                // on a sub-thread but that doesn't seem cross-platform safe. We shouldn't
                // have a lot of error logging, so this should be acceptable. If not, we could
                // cache the visibility state.
                if (shell != null && !shell.isDisposed()) {
                    shell.getDisplay().syncExec(new Runnable() {
                        @Override
                        public void run() {
                            if (!logWindow.isVisible()) {
                                // Don't toggle the window visibility directly.
                                // Instead use the same action as the log-toggle button
                                // so that the button's state be kept in sync.
                                onToggleLogWindow();
                            }
                        }
                    });
                }
            }
        };
        // SdkProgressFactory provides progress reporting in an ITaskMonitor or ProgressIndicator
        SecondaryLog secondaryLog = new SecondaryLog(){

			@Override
			public void setSecondaryText(String message) {
				if ((message != null) && !message.isEmpty())
					Display.getDefault().syncExec(new Runnable(){
	
						@Override
						public void run() {
							if (!verboseLine.isDisposed())
								verboseLine.setText(message);
						}});
			}};
        SdkProgressFactory factory = new SdkProgressFactory(statusText, progressBar, buttonStop, logAdapter, secondaryLog);
        setWindowImage(shell);
        if (!initializeSettings())
            return false;
        if (sdkContext.hasError())
        {
        	ILogger logger = (ILogger)factory;
        	Iterator<String> iterator = sdkContext.getLogMessages().iterator();
        	while(iterator.hasNext())
        		logger.error(null, iterator.next());
        	return false;
        	
        }
        sdkContext.setSdkLogger(factory);
        sdkContext.setSdkProgressIndicator(factory);

        // Display packages
        packagesPage.onReady(factory);
        return true;
    }

    /**
     * Creates the icon of the window shell.
     *
     * @param shell The shell on which to put the icon
     */
    private void setWindowImage(Shell shell) {
        String imageName = "android_icon_16.png"; //$NON-NLS-1$
        if (SdkConstants.currentPlatform() == SdkConstants.PLATFORM_DARWIN) {
            imageName = "android_icon_128.png"; //$NON-NLS-1$
        }

        if (sdkContext != null) {
            ImageFactory imgFactory = sdkContext.getSdkHelper().getImageFactory();
            if (imgFactory != null) {
                shell.setImage(imgFactory.getImageByName(imageName));
            }
        }
    }

    /**
     * Called by the main loop when the window has been disposed.
     */
    private void dispose() {
        logWindow.close();
    }

    /**
     * Initializes settings.
     * This must be called after addExtraPages(), which created a settings page.
     * Iterate through all the pages to find the first (and supposedly unique) setting page,
     * and use it to load and apply these settings.
     */
    private boolean initializeSettings() {
    	Settings settings = new Settings(sdkContext.getSdkLog());
        if (settings.initialize())
        {
        	sdkContext.setSettings(settings);
        	return true;
        }
        return false;
    }

    private void onToggleLogWindow() {
        // toggle visibility
        if (!buttonShowLog.isDisposed()) {
            logWindow.setVisible(!logWindow.isVisible());
            buttonShowLog.setState(logWindow.isVisible() ? 1 : 0);
        }
    }

    private void onStopSelected() {
        sdkContext.getProgressIndicator().cancel();
    }

    private void onAvdManager() {
        try {
            AvdManagerWindowImpl1 win = new AvdManagerWindowImpl1(
                    shell,
                    sdkContext,
                    AvdInvocationContext.DIALOG);

            win.open();
        } catch (Exception e) {
            sdkContext.getSdkLog().error(e, "AVD Manager window error");
        }    
    }


    // End of hiding from SWT Designer
    //$hide<<$
}
