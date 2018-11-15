/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.sdkuilib.internal.widgets;

import java.awt.DisplayMode;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.andmore.base.resources.ImageFactory;
import org.eclipse.andmore.sdktool.SdkContext;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.DecorationOverlayIcon;
import org.eclipse.jface.viewers.IDecoration;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

import com.android.annotations.NonNull;
import com.android.prefs.AndroidLocation.AndroidLocationException;
import com.android.repository.api.LocalPackage;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdInfo.AvdStatus;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.sdklib.repository.IdDisplay;
import com.android.sdklib.repository.meta.DetailsTypes.PlatformDetailsType;
import com.android.sdklib.repository.targets.SystemImage;
import com.android.sdkuilib.internal.repository.avd.AvdAgent;
import com.android.sdkuilib.internal.repository.avd.SdkTargets;
import com.android.sdkuilib.internal.repository.avd.SystemImageInfo;
import com.android.sdkuilib.internal.repository.ui.AvdManagerWindowImpl1;
import com.android.sdkuilib.internal.repository.ui.ManagerControls;
import com.android.sdkuilib.internal.tasks.EmulatorLauncher;
import com.android.sdkuilib.repository.AvdManagerWindow.AvdInvocationContext;
import com.android.sdkuilib.ui.AdtUpdateDialog;
import com.android.sdkuilib.ui.AvdDisplayMode;
import com.android.sdkuilib.widgets.MessageBoxLog;
import com.android.utils.ILogger;
import com.android.utils.Pair;


/**
 * The AVD selector is a table that is added to the given parent composite.
 * <p/>
 * After using one of the constructors, call {@link #setSelection(AvdInfo)},
 * {@link #setSelectionListener(SelectionListener)} and finally use
 * {@link #getSelected()} to retrieve the selection.
 * <p/>
 * The parent control must implement the ManagerControls interface to bind
 * Refresh and OK buttons to this table.
 */
public final class AvdSelector {
    /** Device tags which have dedicated images */
    private static final String[] DEVICE_IMAGE_TAGS = { "android-tv", "android-wear" };

	private static int NUM_COL = 2;

	/** MANAGER, SIMPLE_CHECK and SIMPLE_SELECTION */
    private final AvdDisplayMode mDisplayMode;
    /** Container for Android targets and System images */
    private final SdkTargets mSdkTargets;
    /** Binds to manager refresh and close buttons */
    private final ManagerControls managerControls;
    /** Android Virtual Device Manager for AVD creation, maintenance and storage */
    private AvdManager mAvdManager;
    /** External selection listener */
    private SelectionListener mSelectionListener;
    /** Optional target filter */
    private IAvdFilter mTargetFilter;

    /** Defaults to true. Changed by the {@link #setEnabled(boolean)} method to represent the
     * "global" enabled state on this composite. */
    private boolean mIsEnabled = true;
    /** Guard against refresh recursion */
    private boolean mInternalRefresh;
    /** SDK context */
    private final SdkContext mSdkContext;
    /** Image factory. Images are not to be disposed by callers */
    private ImageFactory mImageFactory;
   
    // Controls
    private Shell mShell;
    private Table mTable;
    private Button mDeleteButton;
    private Button mDetailsButton;
    private Button mNewButton;
    private Button mEditButton;
    private Button mManagerButton;
    private Button mRepairButton;
    private Button mStartButton;
    private Image mBrokenImage;
    private Image mInvalidImage;


    /**
     * A filter to control the whether or not an AVD should be displayed by the AVD Selector.
     */
    public interface IAvdFilter {
        /**
         * Called before {@link #accept(AvdInfo)} is called for any AVD.
         */
        void prepare();

        /**
         * Called to decided whether an AVD should be displayed.
         * @param avdAgent Agent containing the AVD to test.
         * @return true if the AVD should be displayed.
         */
        boolean accept(AvdAgent avdAgent);

        /**
         * Called after {@link #accept(AvdInfo)} has been called on all the AVDs.
         */
        void cleanup();
    }

    /**
     * Internal implementation of {@link IAvdFilter} to filter out the AVDs that are not
     * running an image compatible with a specific target.
     */
    private final static class TargetBasedFilter implements IAvdFilter {
        private final IAndroidTarget mTarget;

        TargetBasedFilter(IAndroidTarget target) {
            mTarget = target;
        }

        @Override
        public void prepare() {
            // nothing to prepare
        }

        @Override
        public boolean accept(AvdAgent avdAgent) {
            if ((avdAgent != null) && (avdAgent.getTarget() != null)) {
                return mTarget.canRunOn(avdAgent.getTarget());
            }

            return false;
        }

        @Override
        public void cleanup() {
            // nothing to clean up
        }
    }

    /**
     * Creates a new SDK Target Selector, and fills it with a list of {@link AvdInfo}, filtered
     * by a {@link IAndroidTarget}.
     * <p/>Only the {@link AvdInfo} able to run application developed for the given
     * {@link IAndroidTarget} will be displayed.
     *
     * @param parent The parent composite where the selector will be added.
     * @param sdkContext SDK handler and repo manager
     * @param filter When non-null, will allow filtering the AVDs to display.
     * @param displayMode The display mode ({@link DisplayMode}).
     * @param managerControls Binds to manager refresh and close buttons
     */
    public AvdSelector(Composite parent,
            SdkContext sdkContext,
            IAvdFilter filter,
            AvdDisplayMode displayMode,
            ManagerControls managerControls) {
    	mSdkTargets = new SdkTargets(sdkContext);
        mSdkContext = sdkContext;
        mAvdManager = sdkContext.getAvdManager();
        mTargetFilter = filter;
        mDisplayMode = displayMode;
        this.managerControls = managerControls;
        managerControls.addRefreshListener(0, new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent event) {
                refresh(true);
            }
        });
        mShell = parent.getShell();
        mImageFactory = mSdkContext.getSdkHelper().getImageFactory();
        mBrokenImage = mImageFactory.getImageByName("warning_icon16.png");
        mInvalidImage = mImageFactory.getImageByName("reject_icon16.png");
        createContent(parent, displayMode);
    }
    
    /**
     * Creates a new SDK Target Selector, and fills it with a list of {@link AvdInfo}.
     *
     * @param parent The parent composite where the selector will be added.
     * @param sdkContext SDK handler and repo manager
     * @param displayMode The display mode ({@link DisplayMode}).
     * @param managerControls Binds to manager refresh and close buttons
     */
    public AvdSelector(Composite parent,
            SdkContext sdkContext,
            AvdDisplayMode displayMode,
            ManagerControls managerControls) {
        this(parent, sdkContext, (IAvdFilter)null /* filter */, displayMode, managerControls);
    }

    /** 
     * Returns a flag set true if this control is disposed 
     * @return boolean
     */
    public boolean isDisposed() {
    	return mShell.isDisposed();
    }
 
    /**
     * Returns container for Android targets and System images
     * @return SdkTargets object
     */
    public SdkTargets getSdkTargets() {
		return mSdkTargets;
	}

	/**
     * Sets the table grid layout data.
     *
     * @param heightHint If > 0, the height hint is set to the requested value.
     */
    public void setTableHeightHint(int heightHint) {
        GridData data = new GridData();
        if (heightHint > 0) {
            data.heightHint = heightHint;
        }
        data.grabExcessVerticalSpace = true;
        data.grabExcessHorizontalSpace = true;
        data.horizontalAlignment = GridData.FILL;
        data.verticalAlignment = GridData.FILL;
        mTable.setLayoutData(data);
    }

    /**
     * Refresh the display of Android Virtual Devices.
     * Tries to keep the selection.
     * <p/>
     * This does not need to be called from the UI thread.
     *
     * @param reload if true, the AVD manager will reload the AVD from the disk.
     * @return true as this is now run asynchronously.
     */
    public boolean refresh(boolean reload) {
    	// Guard against refresh while refresh is in progress
    	final boolean[] skip = new boolean[]{false};
    	Display.getDefault().syncExec(new Runnable(){

			@Override
			public void run() {
				skip[0] = !managerControls.isRefreshEnabled();
				if (!skip[0]) {
					// Disable refresh button
					managerControls.enableRefresh(false);
					// Clear the table so the user gets feedback
				    mTable.removeAll();
				}
			}
    	});
    	if (skip[0])
    		return true;
    	// Some work must now be done in the background as it involves file operations
    	Job job = new Job("Refresh AVD Manager"){

			@Override
			protected IStatus run(IProgressMonitor arg0) {
		        if (!mInternalRefresh) {
	                // Note that AvdManagerPage.onDevicesChange() will trigger a
	                // refresh while the AVDs are being reloaded so prevent from
	                // having a recursive call to here.
	                mInternalRefresh = true;
		            try {
		            	// After a repair operation, the targets and systerm imaages may have changed
		            	mSdkTargets.reload();
		                if (reload) {
		                    try {
		                        mAvdManager.reloadAvds(mSdkContext.getSdkLog());
		                    } catch (AndroidLocationException e) { // This is not expected
		                    	mSdkContext.getSdkLog().error(e, "Error while refreshing AVDs");
		                        return Status.CANCEL_STATUS;
		                    }
		                }

		            	Display.getDefault().syncExec(new Runnable(){

		        			@Override
		        			public void run() {
				                AvdAgent selected = getSelected();
				                fillTable(mTable);
				                setSelection(selected);
								managerControls.enableRefresh(true);
		        			}
		            	});
		            } finally {
		                mInternalRefresh = false;
		            }
		        }
				return Status.OK_STATUS;
			};
        };
        job.setPriority(Job.BUILD);
        job.schedule();
        return true;
    }

    /**
     * Sets a new AVD manager
     * This does not refresh the display. Call {@link #refresh(boolean)} to do so.
     * @param manager the AVD manager.
     */
    public void setManager(AvdManager manager) {
        mAvdManager = manager;
    }

    /**
     * Sets a new AVD filter.
     * This does not refresh the display. Call {@link #refresh(boolean)} to do so.
     * @param filter An IAvdFilter. If non-null, this will filter out the AVD to not display.
     */
    public void setFilter(IAvdFilter filter) {
        mTargetFilter = filter;
    }

    /**
     * Sets a new Android Target-based AVD filter.
     * This does not refresh the display. Call {@link #refresh(boolean)} to do so.
     * @param target An IAndroidTarget. If non-null, only AVD whose target are compatible with the
     * filter target will displayed an available for selection.
     */
    public void setFilter(IAndroidTarget target) {
        if (target != null) {
            mTargetFilter = new TargetBasedFilter(target);
        } else {
            mTargetFilter = null;
        }
    }

    /**
     * Sets a selection listener. Set it to null to remove it.
     * The listener will be called <em>after</em> this table processed its selection
     * events so that the caller can see the updated state.
     * <p/>
     * The event's item contains a {@link TableItem}.
     * The {@link TableItem#getData()} contains an {@link IAndroidTarget}.
     * <p/>
     * It is recommended that the caller uses the {@link #getSelected()} method instead.
     * <p/>
     * The default behavior for double click (when not in {@link DisplayMode#SIMPLE_CHECK}) is to
     * display the details of the selected AVD.<br>
     * To disable it (when you provide your own double click action), set
     * {@link SelectionEvent#doit} to false in
     * {@link SelectionListener#widgetDefaultSelected(SelectionEvent)}
     *
     * @param selectionListener The new listener or null to remove it.
     */
    public void setSelectionListener(SelectionListener selectionListener) {
        mSelectionListener = selectionListener;
    }

	/**
     * Sets the current target selection.
     * <p/>
     * If the selection is actually changed, this will invoke the selection listener
     * (if any) with a null event.
     *
     * @param target the target to be selected. Use null to deselect everything.
     * @return true if the target could be selected, false otherwise.
     */
	public boolean setSelection(AvdInfo avd) {
		AvdAgent avdAgent = avdAgentInstance(avd);
       	if (avdAgent != null)
    		return setSelection(avdAgent);
        // Ignore AVD if there is no system image and Android version not supported
       	return false;
	}

	/**
     * Sets the current target selection.
     * <p/>
     * If the selection is actually changed, this will invoke the selection listener
     * (if any) with a null event.
     *
     * @param target the target to be selected. Use null to deselect everything.
     * @return true if the target could be selected, false otherwise.
     */
    public boolean setSelection(AvdAgent target) {
        boolean found = false;
        boolean modified = false;

        int selIndex = mTable.getSelectionIndex();
        int index = 0;
        for (TableItem i : mTable.getItems()) {
        	AvdAgent avdAgent = (AvdAgent)i.getData();
            if (mDisplayMode == AvdDisplayMode.SIMPLE_CHECK) {
                if (avdAgent == target) {
                    found = true;
                    if (!i.getChecked()) {
                        modified = true;
                        i.setChecked(true);
                    }
                } else if (i.getChecked()) {
                    modified = true;
                    i.setChecked(false);
                }
            } else {
                if (avdAgent == target) {
                    found = true;
                    if (index != selIndex) {
                        mTable.setSelection(index);
                        modified = true;
                    }
                    break;
                }

                index++;
            }
        }
        if (modified && mSelectionListener != null) {
            mSelectionListener.widgetSelected(null);
        }
        enableActionButtons();
        return found;
    }

    /**
     * Returns the currently selected item. In {@link DisplayMode#SIMPLE_CHECK} mode this will
     * return the {@link AvdInfo} that is checked instead of the list selection.
     *
     * @return The currently selected item or null.
     */
    public AvdAgent getSelected() {
        if (mDisplayMode == AvdDisplayMode.SIMPLE_CHECK) {
            for (TableItem i : mTable.getItems()) {
                if (i.getChecked()) {
                    return (AvdAgent) i.getData();
                }
            }
        } else {
            int selIndex = mTable.getSelectionIndex();
            if (selIndex >= 0) {
                return (AvdAgent) mTable.getItem(selIndex).getData();
            }
        }
        return null;
    }

    /**
     * Enables the receiver if the argument is true, and disables it otherwise.
     * A disabled control is typically not selectable from the user interface
     * and draws with an inactive or "grayed" look.
     *
     * @param enabled the new enabled state.
     */
    public void setEnabled(boolean enabled) {
        // We can only enable widgets if the AVD Manager is defined.
        mIsEnabled = enabled && mAvdManager != null;

        mTable.setEnabled(mIsEnabled);
        managerControls.enableRefresh(mIsEnabled);

        if (mNewButton != null) {
            mNewButton.setEnabled(mIsEnabled);
        }
        if (mManagerButton != null) {
            mManagerButton.setEnabled(mIsEnabled);
        }

        enableActionButtons();
    }

    /**
     * Returns flag set true if control is enabled
     * @return boolean
     */
    public boolean isEnabled() {
        return mIsEnabled;
    }

    /**
     * Create control content
     * @param parent Parent composite
     * @param displayMode AVD disply mode
     */
    private void createContent(Composite parent, AvdDisplayMode displayMode) {
        // Layout has 2 columns
        Composite group = new Composite(parent, SWT.NONE);
        GridLayout gl;
        group.setLayout(gl = new GridLayout(NUM_COL, false /*makeColumnsEqualWidth*/));
        gl.marginHeight = gl.marginWidth = 0;
        group.setLayoutData(new GridData(GridData.FILL_BOTH));
        group.setFont(parent.getFont());

        int style = SWT.FULL_SELECTION | SWT.SINGLE | SWT.BORDER;
        if (displayMode == AvdDisplayMode.SIMPLE_CHECK) {
            style |= SWT.CHECK;
        }
        mTable = new Table(group, style);
        mTable.setHeaderVisible(true);
        mTable.setLinesVisible(false);
        setTableHeightHint(0);

        Composite buttons = new Composite(group, SWT.NONE);
        buttons.setLayout(gl = new GridLayout(1, false /*makeColumnsEqualWidth*/));
        gl.marginHeight = gl.marginWidth = 0;
        buttons.setLayoutData(new GridData(GridData.FILL_VERTICAL));
        buttons.setFont(group.getFont());

        if (displayMode == AvdDisplayMode.MANAGER) {
            mNewButton = new Button(buttons, SWT.PUSH | SWT.FLAT);
            mNewButton.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
            mNewButton.setText("Create...");
            mNewButton.setToolTipText("Creates a new AVD.");
            mNewButton.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent arg0) {
                    onNew();
                }
            });
        }

        mStartButton = new Button(buttons, SWT.PUSH | SWT.FLAT);
        mStartButton.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        mStartButton.setText("Start...");
        mStartButton.setToolTipText("Starts the selected AVD.");
        mStartButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent arg0) {
                onStart();
            }
        });

        @SuppressWarnings("unused")
        Label spacing = new Label(buttons, SWT.NONE);

        if (displayMode == AvdDisplayMode.MANAGER) {
            mEditButton = new Button(buttons, SWT.PUSH | SWT.FLAT);
            mEditButton.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
            mEditButton.setText("Edit...");
            mEditButton.setToolTipText("Edit an existing AVD.");
            mEditButton.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent arg0) {
                    onEdit();
                }
            });

            mRepairButton = new Button(buttons, SWT.PUSH | SWT.FLAT);
            mRepairButton.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
            mRepairButton.setText("Repair...");
            mRepairButton.setToolTipText("Repairs the selected AVD.");
            mRepairButton.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent arg0) {
                    onRepair();
                }
            });

            mDeleteButton = new Button(buttons, SWT.PUSH | SWT.FLAT);
            mDeleteButton.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
            mDeleteButton.setText("Delete...");
            mDeleteButton.setToolTipText("Deletes the selected AVD.");
            mDeleteButton.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent arg0) {
                    onDelete();
                }
            });
        }

        mDetailsButton = new Button(buttons, SWT.PUSH | SWT.FLAT);
        mDetailsButton.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        mDetailsButton.setText("Details...");
        mDetailsButton.setToolTipText("Displays details of the selected AVD.");
        mDetailsButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent arg0) {
                onDetails();
            }
        });

        Composite padding = new Composite(buttons, SWT.NONE);
        padding.setLayoutData(new GridData(GridData.FILL_VERTICAL));
        if (displayMode != AvdDisplayMode.MANAGER) {
            mManagerButton = new Button(buttons, SWT.PUSH | SWT.FLAT);
            mManagerButton.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
            mManagerButton.setText("Manager...");
            mManagerButton.setToolTipText("Launches the AVD manager.");
            mManagerButton.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    onAvdManager();
                }
            });
        } else {
             Composite legend = new Composite(group, SWT.NONE);
            legend.setLayout(gl = new GridLayout(4, false /*makeColumnsEqualWidth*/));
            gl.marginHeight = gl.marginWidth = 0;
            legend.setLayoutData(new GridData(GridData.FILL, GridData.BEGINNING, true, false,
                    NUM_COL, 1));
            legend.setFont(group.getFont());

            new Label(legend, SWT.NONE).setImage(mBrokenImage);
            new Label(legend, SWT.NONE).setText("A repairable Android Virtual Device.");
            new Label(legend, SWT.NONE).setImage(mInvalidImage);
            new Label(legend, SWT.NONE).setText("An Android Virtual Device that failed to load. Click 'Details' to see the error.");
        }

        // create the table columns
        final TableColumn column0 = new TableColumn(mTable, SWT.NONE);
        column0.setText("AVD Name");
        final TableColumn column1 = new TableColumn(mTable, SWT.NONE);
        column1.setText("Target Name");
        final TableColumn column2 = new TableColumn(mTable, SWT.NONE);
        column2.setText("Platform");
        final TableColumn column3 = new TableColumn(mTable, SWT.NONE);
        column3.setText("API Level");
        final TableColumn column4 = new TableColumn(mTable, SWT.NONE);
        column4.setText("CPU/ABI");

        adjustColumnsWidth(mTable, column0, column1, column2, column3, column4);
        setupSelectionListener(mTable);
        fillTable(mTable);
        setEnabled(true);
    }

    /**
     * Adds a listener to adjust the columns width when the parent is resized.
     * <p/>
     * If we need something more fancy, we might want to use this:
     * http://dev.eclipse.org/viewcvs/index.cgi/org.eclipse.swt.snippets/src/org/eclipse/swt/snippets/Snippet77.java?view=co
     */
    private void adjustColumnsWidth(final Table table,
            final TableColumn column0,
            final TableColumn column1,
            final TableColumn column2,
            final TableColumn column3,
            final TableColumn column4) {
        // Add a listener to resize the column to the full width of the table
        table.addControlListener(new ControlAdapter() {
            @Override
            public void controlResized(ControlEvent e) {
                Rectangle r = table.getClientArea();
                column0.setWidth(r.width * 20 / 100); // 20%
                column1.setWidth(r.width * 30 / 100); // 30%
                column2.setWidth(r.width * 10 / 100); // 10%
                column3.setWidth(r.width * 10 / 100); // 10%
                column4.setWidth(r.width * 30 / 100); // 30%
            }
        });
    }

    /**
     * Creates a selection listener that will check or uncheck the whole line when
     * double-clicked (aka "the default selection").
     */
    private void setupSelectionListener(final Table table) {
        // Add a selection listener that will check/uncheck items when they are double-clicked
        table.addSelectionListener(new SelectionListener() {

            /**
             * Handles single-click selection on the table.
             * {@inheritDoc}
             */
            @Override
            public void widgetSelected(SelectionEvent e) {
                if (e.item instanceof TableItem) {
                    TableItem i = (TableItem) e.item;
                    enforceSingleSelection(i);
                }

                if (mSelectionListener != null) {
                    mSelectionListener.widgetSelected(e);
                }

                enableActionButtons();
            }

            /**
             * Handles double-click selection on the table.
             * Note that the single-click handler will probably already have been called.
             *
             * On double-click, <em>always</em> check the table item.
             *
             * {@inheritDoc}
             */
            @Override
            public void widgetDefaultSelected(SelectionEvent e) {
                if (e.item instanceof TableItem) {
                    TableItem i = (TableItem) e.item;
                    if (mDisplayMode == AvdDisplayMode.SIMPLE_CHECK) {
                        i.setChecked(true);
                    }
                    enforceSingleSelection(i);

                }

                // whether or not we display details. default: true when not in SIMPLE_CHECK mode.
                boolean showDetails = mDisplayMode != AvdDisplayMode.SIMPLE_CHECK;

                if (mSelectionListener != null) {
                    mSelectionListener.widgetDefaultSelected(e);
                    showDetails &= e.doit; // enforce false in SIMPLE_CHECK
                }

                if (showDetails) {
                    onDetails();
                }

                enableActionButtons();
            }

            /**
             * To ensure single selection, uncheck all other items when this one is selected.
             * This makes the chekboxes act as radio buttons.
             */
            private void enforceSingleSelection(TableItem item) {
                if (mDisplayMode == AvdDisplayMode.SIMPLE_CHECK) {
                    if (item.getChecked()) {
                        Table parentTable = item.getParent();
                        for (TableItem i2 : parentTable.getItems()) {
                            if (i2 != item && i2.getChecked()) {
                                i2.setChecked(false);
                            }
                        }
                    }
                } else {
                    // pass
                }
            }
        });
    }

    /**
     * Fills the table with all AVD.
     * The table columns are:
     * <ul>
     * <li>column 0: sdk name
     * <li>column 1: sdk target
     * <li>column 2: sdk platform
     * <li>column 3: sdk API level
     * <li>column 4: CPU/ABI
     * </ul>
     */
    private void fillTable(final Table table) {
        table.removeAll();

        // get the AVDs
        AvdInfo avds[] = null;
        if (mAvdManager != null) {
            if (mDisplayMode == AvdDisplayMode.MANAGER) {
                avds = mAvdManager.getAllAvds();
            } else {
                avds = mAvdManager.getValidAvds();
            }
        }
        if (avds != null && avds.length > 0) {
            Arrays.sort(avds, new Comparator<AvdInfo>() {
                @Override
                public int compare(AvdInfo o1, AvdInfo o2) {
                    return o1.compareTo(o2);
                }
            });
            table.setEnabled(true);
            if (mTargetFilter != null) {
                mTargetFilter.prepare();
            }
            for (AvdInfo avd : avds) {
                AvdAgent avdAgent = avdAgentInstance(avd);
               	if (avdAgent == null)
                	continue; // Ignore AVD if there is no system image and Android version not supported
                if (mTargetFilter == null || mTargetFilter.accept(avdAgent)) {
                    TableItem item = new TableItem(table, SWT.NONE);
                    item.setData(avdAgent);
                    item.setText(0, avd.getName());
                    if (mDisplayMode == AvdDisplayMode.MANAGER) {
                        AvdStatus status = avd.getStatus();

                        boolean isOk = (status == AvdStatus.OK) && (avdAgent.getTarget() != null);
                        boolean isRepair = isAvdRepairable(status);
                        if (!isRepair && (avdAgent.getPlatformPath() != null) && (avdAgent.getSystemImagePath() != null))
                        	isRepair = true;
                        boolean isInvalid = !isOk && !isRepair;

                        Image img = getTagImage(avd.getTag(), isOk, isRepair, isInvalid);
                        item.setImage(0, img);
                    }
                    item.setText(1, avdAgent.getTargetFullName());
                    item.setText(2, avdAgent.getTargetVersionName());
                    // AvdInfo may not contain a valid Android version, so we use the Agent value obtained from the system image
                    AndroidVersion version = avdAgent.getAndroidVersion();
                    item.setText(3, version != null ? version.toString() : "");
                    item.setText(4, AvdInfo.getPrettyAbiType(avd));
                }
            }
            if (mTargetFilter != null) {
                mTargetFilter.cleanup();
            }
        }
        if (table.getItemCount() == 0) {
            table.setEnabled(false);
            TableItem item = new TableItem(table, SWT.NONE);
            item.setData(null);
            item.setText(0, "--");
            item.setText(1, "No AVD available");
            item.setText(2, "--");
            item.setText(3, "--");
        }
    }

    /**
     * Returns an AVD Agent to wrap give AVD Info object
     * @param avd The AVD Info object
     * @return AvdAgent object
     */
    private AvdAgent avdAgentInstance(AvdInfo avd) {
        AvdAgent avdAgent = null;
        // Extract system image information from AvdInfo data
        SystemImageInfo systemImageInfo = new SystemImageInfo(avd);
        // The system image can be null if not found by AVD Manager
        if (systemImageInfo.hasSystemImage()) {
        	// Look up targets to find one with same Android version as this system image
        	IAndroidTarget target = mSdkTargets.getTargetForSysImage(systemImageInfo.getSystemImage());
        	if (target != null)
        		// The AVD Agent binds the target to the AVD Info object
        		avdAgent = new AvdAgent(target, avd);
        } 
        if (avdAgent == null) {
        	// The reason for the system image not found may be fixable.
        	// The starting point is to derive the platform and and system image paths from the system image location 
            String imageSysDir = avd.getProperties().get(AvdManager.AVD_INI_IMAGES_1);
            if (imageSysDir != null) {
            	// Extract Android platform and and systme image path from directory
            	imageSysDir = imageSysDir.replace('\\', '/');
            	Pattern pattern = Pattern.compile("^system-images/([^/]+)/(.*)/$");
            	Matcher matcher = pattern.matcher(imageSysDir);
            	if (matcher.matches()){
            		String platformPath = "platforms;" + matcher.group(1);
            		String systemImagePath = "system-images;" + matcher.group(1) + ";" + matcher.group(2).replaceAll("/", ";");
        	        // System image will need to be installed and possibly platform too.
            		// The AVD will now be shown as repairable
        	        avdAgent = new AvdAgent(platformPath, systemImagePath, avd);
            	}
            }
            if (avdAgent == null) {
            	// The AVD is still worthy of repair if the AVD Info Android version is supported
            	// It looks like this property is derived from the system image, so it is unlikely for the following code will succeed.
            	// Note the APL level when unknown is set to 1 so any attempt to find a matching target fails
	        	IAndroidTarget target = mSdkTargets.getTargetForAndroidVersion(avd.getAndroidVersion());
	        	if (target != null)
	        	    avdAgent = new AvdAgent(target, avd);
	    		// Return null if there is no system image and Android version not supported or unknown
            }
        }
        return avdAgent;
    }
 
    /**
     * Returns a tag image. The special tags are android-tv" and "android-wear". 
     * A status image is rendered over the top for a broken AVD.
     * @param tag
     * @param isOk
     * @param isRepair
     * @param isInvalid
     * @return
     */
    @NonNull
    private Image getTagImage(IdDisplay tag,
                              final boolean isOk,
                              final boolean isRepair,
                              final boolean isInvalid) {
       String id = SystemImage.DEFAULT_TAG.getId();  
    	if (tag != null) {
    		// Some devices have distinctive images
        	for (String specialTag: DEVICE_IMAGE_TAGS) {
    		    if (tag.getId().equals(specialTag)) {
    		    	id = specialTag;
    		    	break;
    		    }
        	}
    	}
        String fname = String.format("tag_%s_32.png", id);
        String kname = String.format("%d%d%d_%s", (isOk ? 1 : 0),
                                                  (isRepair ? 1 : 0),
                                                  (isInvalid ? 1 : 0),
                                                  fname);
        if (isOk)
            return mImageFactory.getImageByName(fname);
        		
        return mImageFactory.getImageByName(fname, kname, new ImageFactory.ImageEditor() {
            @Override
            public ImageData edit(Image source) {
                Image overlayImg = isRepair ? mBrokenImage : mInvalidImage;
                ImageDescriptor overlayDesc = ImageDescriptor.createFromImage(overlayImg);

                DecorationOverlayIcon overlaid =
                        new DecorationOverlayIcon(source, overlayDesc, IDecoration.BOTTOM_RIGHT);
                return overlaid.getImageData(100);
            }
        });
    }

    /**
     * Returns the currently selected AVD in the table.
     * <p/>
     * Unlike {@link #getSelected()} this will always return the item being selected
     * in the list, ignoring the check boxes state in {@link DisplayMode#SIMPLE_CHECK} mode.
     */
    private AvdAgent getTableSelection() {
        int selIndex = mTable.getSelectionIndex();
        if (selIndex >= 0) {
            return (AvdAgent) mTable.getItem(selIndex).getData();
        }
        return null;
    }

    /**
     * Updates the enable state of the Details, Start, Delete, Edit and Repair buttons.
     */
    private void enableActionButtons() {
        if (mIsEnabled == false) {
            mDetailsButton.setEnabled(false);
            mStartButton.setEnabled(false);

            if (mEditButton != null) {
                mEditButton.setEnabled(false);
            }
            if (mDeleteButton != null) {
                mDeleteButton.setEnabled(false);
            }
            if (mRepairButton != null) {
                mRepairButton.setEnabled(false);
            }
        } else {
            AvdAgent selection = getTableSelection();
            boolean hasSelection = selection != null;
            boolean isRepairable = false;
        	if (hasSelection) {
        		isRepairable = isAvdRepairable(selection.getAvd().getStatus());
        		if (!isRepairable)
        			isRepairable = (selection.getTarget() == null) && 
        			(selection.getPlatformPath() != null) &&
        			(selection.getSystemImagePath() != null);
        	}

            mDetailsButton.setEnabled(hasSelection);
            mStartButton.setEnabled(hasSelection &&
                    (selection.getAvd().getStatus() == AvdStatus.OK) &&
                    (selection.getTarget() != null));

            if (mEditButton != null) {
                mEditButton.setEnabled(hasSelection && !isRepairable);
            }
            if (mDeleteButton != null) {
                mDeleteButton.setEnabled(hasSelection);
            }
            if (mRepairButton != null) {
                mRepairButton.setEnabled(isRepairable);
           }
        }
    }

    /**
     * Handle Create button hit
     */
    private void onNew() {
        AvdCreationDialog avdCreationDialog = new AvdCreationDialog(mTable.getShell(),
                mSdkContext,
                mSdkTargets,
                null);

        if (avdCreationDialog.open() == Window.OK) {
            refresh(false); //reload
        }
    }

    /**
     * Handle Edit button hit
     */
    private void onEdit() {
        AvdAgent avdAgent = getTableSelection();
        if (avdAgent == null)
        	return; // This is not expected
        AvdCreationDialog avdCreationDialog = null;
        if (!avdAgent.getAvd().getDeviceName().isEmpty()) {
        	avdCreationDialog = new AvdCreationDialog(mTable.getShell(),
            		mSdkContext,
            		mSdkTargets,
            		avdAgent);
        } else {
        	// Legacy configurations no longer supported - too hard to do
        	// Create a dialog with ok button and a warning icon
        	MessageBox dialog =
        	    new MessageBox(mTable.getShell(), SWT.ICON_WARNING| SWT.OK);
        	dialog.setText("Legacy device not supported");
        	dialog.setMessage(avdAgent.getAvd().getName() + " has is assigned a legacy device no longer supported by the Android SDK");
         	// Oopen dialog and await user selection
        	dialog.open();       
        }
        if ((avdCreationDialog != null) && (avdCreationDialog.open() == Window.OK)) {
        	// Delete old AVD if repair results in duplication
        	AvdInfo originalAvdInfo = avdAgent.getAvd();
        	avdAgent = avdAgentInstance(avdCreationDialog.getCreatedAvd());
        	if ((originalAvdInfo.getStatus() != AvdStatus.OK) && 
        		!originalAvdInfo.getName().equals(avdAgent.getAvd().getName()))
        		mAvdManager.deleteAvd(originalAvdInfo, mSdkContext.getSdkLog());
            int selIndex = mTable.getSelectionIndex();
            if (selIndex >= 0)
                 mTable.getItem(selIndex).setData(avdAgent);
            refresh(false); //reload
        }
    }

    /**
     * Handle Details button hit
     */
    private void onDetails() {
        AvdAgent avdAgent = getTableSelection();

        AvdDetailsDialog dlg = new AvdDetailsDialog(mTable.getShell(), mImageFactory, avdAgent);
        dlg.open();
    }

    /**
     * Handle Delete button hit. Applies only to User type AVDs.
     */
    private void onDelete() {
        final AvdAgent avdAgent = getTableSelection();

        // get the current Display
        final Display display = mTable.getDisplay();

        // check if the AVD is running
        if (mAvdManager.isAvdRunning(avdAgent.getAvd(), mSdkContext.getSdkLog())) {
            display.asyncExec(new Runnable() {
                @Override
                public void run() {
                    Shell shell = display.getActiveShell();
                    MessageDialog.openError(shell,
                            "Delete Android Virtual Device",
                            String.format(
                                    "The Android Virtual Device '%1$s' is currently running in an emulator and cannot be deleted.",
                                    avdAgent.getAvd().getName()));
                }
            });
            return;
        }

        // Confirm you want to delete this AVD
        final boolean[] result = new boolean[1];
        display.syncExec(new Runnable() {
            @Override
            public void run() {
                Shell shell = display.getActiveShell();
                result[0] = MessageDialog.openQuestion(shell,
                        "Delete Android Virtual Device",
                        String.format(
                                "Please confirm that you want to delete the Android Virtual Device named '%s'. This operation cannot be reverted.",
                                avdAgent.getAvd().getName()));
            }
        });

        if (result[0] == false) {
            return;
        }

        ILogger log = mSdkContext.getSdkLog();
		// log for this action.
        if (log  == null || log instanceof MessageBoxLog) {
            // If the current logger is a message box, we use our own (to make sure
            // to display errors right away and customize the title).
            log = new MessageBoxLog(
                String.format("Result of deleting AVD '%s':", avdAgent.getAvd().getName()),
                display,
                false /*logErrorsOnly*/);
        }

        // delete the AVD
        boolean success = mAvdManager.deleteAvd(avdAgent.getAvd(), log);

        // display the result
        if (log instanceof MessageBoxLog) {
            ((MessageBoxLog) log).displayResult(success);
        }

        if (success) {
            refresh(false /*reload*/);
        }
    }

    /**
     * Handle Repair button hit
     */
    private void onRepair() {
        final AvdAgent avdAgent = getTableSelection();

        // get the current Display
        final Display display = mTable.getDisplay();

        ILogger log = mSdkContext.getSdkLog();
        if (log == null || log instanceof MessageBoxLog) {
            // If the current logger is a message box, we use our own (to make sure
            // to display errors right away and customize the title).
            log = new MessageBoxLog(
                String.format("Result of updating AVD '%s':", avdAgent.getAvd().getName()),
                display,
                false /*logErrorsOnly*/);
        }

        if (avdAgent.getAvd().getStatus() == AvdStatus.ERROR_DEVICE_CHANGED) {
        	// Device configuration needs an update. The AVD Manager handles this.
            try {
                mAvdManager.updateDeviceChanged(avdAgent.getAvd(), log);
            } catch (IOException e) {
                log.error(e, null);
            }
            refresh(false /*reload*/);
        } else if (avdAgent.getAvd().getStatus() == AvdStatus.ERROR_DEVICE_MISSING) {
        	// The AVD configuration references a missing device so let the user choose another device
            onEdit();
        } else if (avdAgent.getTarget() == null) {
        	// System image and/or platform needs to be installed
        	String platformPath = avdAgent.getPlatformPath();
        	String systemImagePath = avdAgent.getSystemImagePath();
	        if (mSdkContext.getLocalPackages().get(platformPath) == null) {
	        	// Platform not found in local packages, so will have to be deployed
		        AdtUpdateDialog updater = new AdtUpdateDialog(
		                mShell,
		                mSdkContext);
		        Pair<Boolean, File> platformResult = updater.installPackage(platformPath);
		        if (!platformResult.getFirst()) {
	                log.error(null, "Repair failed: Platform install error");
		        	return;
		        }
	        }
	        SystemImageInfo systemImageInfo = new SystemImageInfo(avdAgent.getAvd());
	        if (!systemImageInfo.hasSystemImage()) {
		        AdtUpdateDialog updater = new AdtUpdateDialog(
		                mShell,
		                mSdkContext);
		        Pair<Boolean, File> systemImageResult = updater.installPackage(systemImagePath);
		        if (!systemImageResult.getFirst()) {
	                log.error(null, "Repair failed: System image install error");
		        	return;
		        }
	        }
			LocalPackage localPlatformPackage = mSdkContext.getLocalPackages().get(platformPath);
			if (localPlatformPackage == null) { // This is not expected
                log.error(null, "Repair failed: Platform package not found");
	        	return;
			}
			AndroidVersion version = ((PlatformDetailsType)localPlatformPackage.getTypeDetails()).getAndroidVersion();
        	IAndroidTarget target = mSdkTargets.getTargetForAndroidVersion(version);
        	if (target == null) {
                log.error(null, "Repair failed: Android version not found");
	        	return;
        	}
            try {
            	// Writeable properties required
            	Map<String, String> newProperties = new HashMap<>();
            	newProperties.putAll(avdAgent.getAvd().getProperties());
                mAvdManager.updateAvd(avdAgent.getAvd(), newProperties);
            } catch (IOException e) {
               log.error(e, null);
            }
        }
    }

    /**
     * Handle AVD manager button hit
     */
    private void onAvdManager() {

        // get the current Display
        Display display = mTable.getDisplay();

        // log for this action.
        ILogger log = null;
        if (log == null || log instanceof MessageBoxLog) {
            // If the current logger is a message box, we use our own (to make sure
            // to display errors right away and customize the title).
            log = new MessageBoxLog("Result of SDK Manager", display, true /*logErrorsOnly*/);
        }

        try {
            AvdManagerWindowImpl1 win = new AvdManagerWindowImpl1(
                    mTable.getShell(),
                    log,
                    mSdkContext,
                    AvdInvocationContext.DIALOG);

            win.open();
        } catch (Exception ignore) {}

        refresh(true /*reload*/); // UpdaterWindow uses its own AVD manager so this one must reload.

        if (log instanceof MessageBoxLog) {
            ((MessageBoxLog) log).displayResult(true);
        }
    }

    /**
     * Handle Start button hit 
     */
    private void onStart() {
    	AvdAgent avdAgent = getTableSelection();

        if (avdAgent == null) {
            return;
        }
        new EmulatorLauncher().launchEmulator(avdAgent, mTable.getShell());
    }

    /**
     * Returns flag set true if AVD is repairable base on it's status
     * @param avdStatus The status
     * @return boolean
     */
    private boolean isAvdRepairable(AvdStatus avdStatus) {
        return avdStatus == AvdStatus.ERROR_DEVICE_CHANGED ||
               avdStatus == AvdStatus.ERROR_DEVICE_MISSING;
    }

}
