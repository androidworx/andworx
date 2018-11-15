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
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdkuilib.internal.repository.AboutDialog;
import com.android.sdkuilib.internal.repository.avd.SdkTargets;
import com.android.sdkuilib.internal.repository.ui.DeviceManagerPage.IAvdCreatedListener;
import com.android.sdkuilib.repository.SdkUpdaterWindow;

import com.android.sdkuilib.repository.AvdManagerWindow.AvdInvocationContext;
import com.android.sdkuilib.ui.GridDataBuilder;
import com.android.sdkuilib.ui.GridLayoutBuilder;
import com.android.utils.ILogger;

import org.eclipse.andmore.base.resources.ImageFactory;
import org.eclipse.andmore.sdktool.SdkContext;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;

/**
 * This is an intermediate version of the {@link AvdManagerPage}
 * wrapped in its own standalone window for use from the SDK Manager 2.
 */
public class AvdManagerWindowImpl1 implements ManagerControls {

	private enum Tab {
		AVD, 
		DEVICE
	}
	
    private static final String APP_NAME = "Android Virtual Device (AVD) Manager";
    private static final String SIZE_POS_PREFIX = "avdman1"; //$NON-NLS-1$
    /**
     * Min Y location for dialog. Need to deal with the menu bar on mac os.
     */
    private final static int MIN_Y =
        SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_DARWIN ? 20 : 0;
	private static final String INVALID_INDEX = "Index %d is invalid";

    private final Shell mParentShell;
    private final AvdInvocationContext mContext;
    private final SdkContext mSdkContext;
    private final SelectionAdapter[] refreshListeners = new SelectionAdapter[2];
    private final SelectionAdapter[] closeListeners = new SelectionAdapter[2];

    // --- UI members ---

    protected Shell mShell;
    private AvdManagerPage mAvdPage;
    private TabFolder mTabFolder;
    private Button mRefreshButton;
    private Button mOkButton;

    /**
     * Creates a new window. Caller must call open(), which will block.
     *
     * @param parentShell Parent shell.
     * @param sdkLog Logger. Cannot be null.
     * @param sdkContext SDK handler and repo manager
     * @param context The {@link AvdInvocationContext} to change the behavior depending on who's
     *  opening the SDK Manager.
     */
    public AvdManagerWindowImpl1(
            Shell parentShell,
            ILogger sdkLog,
            SdkContext sdkContext,
            AvdInvocationContext context) {
        mParentShell = parentShell;
        mSdkContext = sdkContext;
        mContext = context;
    }

    /**
     * Creates a new window. Caller must call open(), which will block.
     * <p/>
     * This is to be used when the window is opened from {@link SdkUpdaterWindowImpl2}
     * to share the same {@link SwtUpdaterData} structure.
     *
     * @param parentShell Parent shell.
     * @param sdkContext SDK handler and repo manager
     * @param context The {@link AvdInvocationContext} to change the behavior depending on who's
     *  opening the SDK Manager.
     */
    public AvdManagerWindowImpl1(
            Shell parentShell,
            SdkContext sdkContext,
            AvdInvocationContext context) {
        mParentShell = parentShell;
        mContext = context;
        mSdkContext = sdkContext;
    }

    /**
     * Opens the window.
     * @wbp.parser.entryPoint
     */
    public void open() {
        if (mParentShell == null) {
            Display.setAppName(APP_NAME); //$hide$ (hide from SWT designer)
        }

        createShell();
        preCreateContent();
        createContents();
        createMenuBar();
        mShell.open();
        mShell.layout();

        boolean ok = postCreateContent();

        if (ok && mContext == AvdInvocationContext.STANDALONE) {
            Display display = Display.getDefault();
            while (!mShell.isDisposed()) {
                if (!display.readAndDispatch()) {
                    display.sleep();
                }
            }
        }
    }

	@Override
	public void enableRefresh(boolean isEnabled) {
		mRefreshButton.setEnabled(isEnabled);
	}

	@Override
	public boolean isRefreshEnabled() {
		return mRefreshButton.isEnabled();
	}

	@Override
	public void addRefreshListener(int index, SelectionAdapter refreshListener) {
		if ((index == Tab.AVD.ordinal()) || (index == Tab.DEVICE.ordinal()))
			refreshListeners[index] = refreshListener;
		else
			throw new IllegalArgumentException(String.format(INVALID_INDEX, index));
	}

	@Override
	public void addCloseListener(int index, SelectionAdapter closeListener) {
		if ((index == Tab.AVD.ordinal()) || (index == Tab.DEVICE.ordinal()))
			closeListeners[index] = closeListener;
		else
			throw new IllegalArgumentException(String.format(INVALID_INDEX, index));
	}
	
    private void createShell() {
        // The AVD Manager must use a shell trim when standalone
        // or a dialog trim when invoked from somewhere else.
        int style = SWT.SHELL_TRIM;
        if (mContext != AvdInvocationContext.STANDALONE) {
            style |= SWT.APPLICATION_MODAL;
        }

        mShell = new Shell(mParentShell, style);
        mShell.addDisposeListener(new DisposeListener() {
            @Override
            public void widgetDisposed(DisposeEvent e) {
                ShellSizeAndPos.saveSizeAndPos(mShell, SIZE_POS_PREFIX);    //$hide$
                mAvdPage.dispose();                                         //$hide$
            }
        });

        GridLayout glShell = new GridLayout(1, false);
        glShell.verticalSpacing = 0;
        glShell.horizontalSpacing = 0;
        glShell.marginWidth = 0;
        glShell.marginHeight = 0;
        mShell.setLayout(glShell);

        mShell.setMinimumSize(new Point(600, 300));
        mShell.setSize(700, 500);
        mShell.setText(APP_NAME);

        ShellSizeAndPos.loadSizeAndPos(mShell, SIZE_POS_PREFIX);
    }

    private void createContents() {
        Composite parent = new Composite(mShell, SWT.NONE);
        GridLayoutBuilder.create(parent).noMargins();
        mTabFolder = new TabFolder(parent, SWT.NONE);
        GridDataBuilder.create(mTabFolder).fill().grab();
        Group buttonBar = new Group(parent, SWT.NONE);
        GridDataBuilder.create(buttonBar).vCenter().fill().grab();
        GridLayoutBuilder.create(buttonBar).columns(2);
        mRefreshButton = new Button(buttonBar, SWT.PUSH | SWT.FLAT);
        GridDataBuilder.create(mRefreshButton).vCenter().wHint(150).hFill().hGrab().hRight();
        mRefreshButton.setText("Refresh");
        mRefreshButton.setToolTipText("Reloads the list");
        mRefreshButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent event) {
                doRefresh(event);
            }
        });
    	mOkButton = new Button(buttonBar, SWT.PUSH | SWT.FLAT);
        GridDataBuilder.create(mOkButton).vCenter().wHint(150).hRight();
    	mOkButton.setText("OK");
    	mOkButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent event) {
            	doClose(event);
                mShell.close();
             }
        });
    	mShell.setDefaultButton(mOkButton);
        // avd tab
        TabItem avdTabItem = new TabItem(mTabFolder, SWT.NONE);
        avdTabItem.setText("Android Virtual Devices");
        avdTabItem.setToolTipText(avdTabItem.getText());
        createAvdTab(mTabFolder, avdTabItem);

        // device tab
        TabItem devTabItem = new TabItem(mTabFolder, SWT.NONE);
        devTabItem.setText("Device Definitions");
        devTabItem.setToolTipText(devTabItem.getText());
        createDeviceTab(mTabFolder, devTabItem);
    }

	private void doRefresh(SelectionEvent event) {
		int selection = mTabFolder.getSelectionIndex();
		if ((selection != -1) && (refreshListeners[selection] != null))
			refreshListeners[selection].widgetSelected(event);
	}

	private void doClose(SelectionEvent event) {
		int selection = mTabFolder.getSelectionIndex();
		if ((selection != -1) && (closeListeners[selection] != null))
			closeListeners[selection].widgetSelected(event);
	}

    private void createAvdTab(TabFolder tabFolder, TabItem avdTabItem) {
        Composite root = new Composite(tabFolder, SWT.NONE);
        avdTabItem.setControl(root);
        GridLayoutBuilder.create(root).columns(1);

        mAvdPage = new AvdManagerPage(root, SWT.NONE, mSdkContext, this);
        GridDataBuilder.create(mAvdPage).fill().grab();
    }

    private void createDeviceTab(TabFolder tabFolder, TabItem devTabItem) {
        Composite root = new Composite(tabFolder, SWT.NONE);
        devTabItem.setControl(root);
        GridLayoutBuilder.create(root).columns(1);

        DeviceManagerPage devicePage =
            new DeviceManagerPage(root, SWT.NONE, mSdkContext, new SdkTargets(mSdkContext), (ManagerControls)this);
        GridDataBuilder.create(devicePage).fill().grab();

        devicePage.setAvdCreatedListener(new IAvdCreatedListener() {
            @Override
            public void onAvdCreated(AvdInfo avdInfo) {
                if (avdInfo != null) {
                    mTabFolder.setSelection(0);      // display mAvdPage
                    mAvdPage.selectAvd(avdInfo, true /*reloadAvdList*/);
                }
            }
        });
    }

    // MenuBarWrapper works using side effects
    private void createMenuBar() {
        Menu menuBar = new Menu(mShell, SWT.BAR);
        mShell.setMenuBar(menuBar);

        // Only create the tools menu when running as standalone.
        // We don't need the tools menu when invoked from the IDE, or the SDK Manager
        // or from the AVD Chooser dialog. The only point of the tools menu is to
        // get the about box, and invoke Tools > SDK Manager, which we don't
        // need to do in these cases.
        if (mContext == AvdInvocationContext.STANDALONE) {

            MenuItem menuBarTools = new MenuItem(menuBar, SWT.CASCADE);
            menuBarTools.setText("Tools");

            Menu menuTools = new Menu(menuBarTools);
            menuBarTools.setMenu(menuTools);

            MenuItem manageSdk = new MenuItem(menuTools, SWT.NONE);
            manageSdk.setText("Manage SDK...");
            manageSdk.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent event) {
                    onSdkManager();
                }
            });

            MenuItem aboutTools = new MenuItem(menuTools, SWT.NONE);
            aboutTools.setText("&About...");
            aboutTools.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent event) {
                    AboutDialog ad = new AboutDialog(mShell, mSdkContext);
                    ad.open();
                }
            });
        }
    }


    // -- Start of internal part ----------
    // Hide everything down-below from SWT designer
    //$hide>>$

    // --- Internals & UI Callbacks -----------

    /**
     * Called before the UI is created.
     */
    private void preCreateContent() {
    }

    /**
     * Once the UI has been created, initializes the content.
     * This creates the pages, selects the first one, setup sources and scan for local folders.
     *
     * Returns true if we should show the window.
     */
    private boolean postCreateContent() {
        setWindowImage(mShell);

        if (!mSdkContext.getSettings().initialize()) {
            return false;
        }
        positionShell();
        return true;
    }

    /**
     * Centers the dialog in its parent shell.
     */
    private void positionShell() {
        // Centers the dialog in its parent shell
        Shell child = mShell;
        Shell parent = mParentShell;
        if (child != null && parent != null) {
            // get the parent client area with a location relative to the display
            Rectangle parentArea = parent.getClientArea();
            Point parentLoc = parent.getLocation();
            int px = parentLoc.x;
            int py = parentLoc.y;
            int pw = parentArea.width;
            int ph = parentArea.height;

            Point childSize = child.getSize();
            int cw = childSize.x;
            int ch = childSize.y;

            int x = px + (pw - cw) / 2;
            if (x < 0) x = 0;

            int y = py + (ph - ch) / 2;
            if (y < MIN_Y) y = MIN_Y;

            child.setLocation(x, y);
            child.setSize(cw, ch);
        }
        mShell.layout(true, true);
        final Point newSize = mShell.computeSize(SWT.DEFAULT, SWT.DEFAULT, true);  
        mShell.setSize(newSize);   
    }

    /**
     * Creates the icon of the window shell.
     *
     * @param shell The shell on which to put the icon
     */
    private void setWindowImage(Shell shell) {
        String imageName = "android_icon_16.png"; //$NON-NLS-1$
        if (SdkConstants.currentPlatform() == SdkConstants.PLATFORM_DARWIN) {
            imageName = "android_icon_128.png";
        }

        if (mSdkContext != null) {
            ImageFactory imgFactory = mSdkContext.getSdkHelper().getImageFactory();
            if (imgFactory != null) {
                shell.setImage(imgFactory.getImageByName(imageName));
            }
        }
    }

    /**
     * Handle SDK Manager selected
     */
    private void onSdkManager() {
        try {
            SdkUpdaterWindowImpl2 win = new SdkUpdaterWindowImpl2(
                    mShell,
                    mSdkContext,
                    SdkUpdaterWindow.SdkInvocationContext.AVD_MANAGER);

            win.open();
        } catch (Exception e) {
            mSdkContext.getSdkLog().error(e, "SDK Manager window error");
        }
    }

}
