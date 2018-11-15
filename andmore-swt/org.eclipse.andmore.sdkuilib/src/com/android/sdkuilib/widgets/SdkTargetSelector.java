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

package com.android.sdkuilib.widgets;

import com.android.SdkConstants;
import com.android.sdklib.IAndroidTarget;
import com.android.sdkuilib.ui.GridDataBuilder;
import com.android.sdkuilib.ui.GridLayoutBuilder;

import org.eclipse.andmore.base.resources.PluginResourceProvider;
import org.eclipse.andmore.sdktool.SdkUserInterfacePlugin;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;


/**
 * The SDK target selector is a table that is added to the given parent composite. 
 * A status line under the table is included for SDK location information.
 * <p/>
 * To use, create it using {@link #SdkTargetSelector(Composite, IAndroidTarget[], boolean)} then
 * call {@link #setSelection(IAndroidTarget)}, {@link #setSelectionListener(SelectionListener)}
 * and finally use {@link #getSelected()} to retrieve the
 * selection.
 */
public class SdkTargetSelector {
	protected static final String LOCATION_NOT_EXISTS = "Location does not exist";
	protected static final String LOCATION_NOT_DIR = "Location is not a directory";

    /** Cache for {@link #getCheckboxWidth()} */
    private static int sCheckboxWidth = -1;

    /** The list of targets. This is <em>not</em> copied, the caller must not modify **/
    private IAndroidTarget[] mTargets;
    /** Make icon usage subject to all images available */
    private boolean useImages;
    private final boolean mAllowSelection;
    private SelectionListener mSelectionListener;
    // Controls
    private Table mTable;
    private Composite mInnerGroup;
    private Label statusImage;
    private Label description;
    private Image newItemImage;
    private Image rejectImage;
    private Image okImage;
    private Image platformImage;

    /**
     * Creates a new SDK Target Selector.
     *
     * @param parent The parent composite where the selector will be added.
     */
    public SdkTargetSelector(Composite parent) {
        this(parent, true /*allowSelection*/);
    }

    /**
     * Creates a new SDK Target Selector.
     *
     * @param parent The parent composite where the selector will be added.
     * @param allowSelection True if selection is enabled.
     */
    public SdkTargetSelector(Composite parent, boolean allowSelection) {
		createImages();
        mAllowSelection = allowSelection;
        createControls(parent);
    }

    /**
     * Returns the layout data of the inner composite widget that contains the target selector.
     * By default the layout data is set to a {@link GridData} with a {@link GridData#FILL_BOTH}
     * mode.
     * <p/>
     * This can be useful if you want to change the {@link GridData#horizontalSpan} for example.
     */
    public Object getLayoutData() {
        return mInnerGroup.getLayoutData();
    }

    /**
     * Changes the targets of the SDK Target Selector.
     *
     * @param targets The list of targets. This is <em>not</em> copied, the caller must not modify.
     */
    public void setTargets(IAndroidTarget[] targets) {
        mTargets = targets;
        if (mTargets != null) {
            Arrays.sort(mTargets, new Comparator<IAndroidTarget>() {
                @Override
                public int compare(IAndroidTarget o1, IAndroidTarget o2) {
                    return o1.compareTo(o2);
                }
            });
        }
        fillTable();
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
     * @param target the target to be selection
     * @return true if the target could be selected, false otherwise.
     */
    public boolean setSelection(IAndroidTarget target) {
        if (!mAllowSelection) {
            return false;
        }

        boolean found = false;
        boolean modified = false;

        if (mTable != null && !mTable.isDisposed()) {
            for (TableItem i : mTable.getItems()) {
                if ((IAndroidTarget) i.getData() == target) {
                    found = true;
                    if (!i.getChecked()) {
                        modified = true;
                        i.setChecked(true);
                    }
                } else if (i.getChecked()) {
                    modified = true;
                    i.setChecked(false);
                }
            }
        }

        if (modified && mSelectionListener != null) {
            mSelectionListener.widgetSelected(null);
        }

        return found;
    }

    /**
     * Returns the selected item.
     *
     * @return The selected item or null.
     */
    public IAndroidTarget getSelected() {
        if (mTable == null || mTable.isDisposed()) {
            return null;
        }

        for (TableItem i : mTable.getItems()) {
            if (i.getChecked()) {
                return (IAndroidTarget) i.getData();
            }
        }
        return null;
    }

    /**
     * Display good/bad status using image and message
     * @param isOk Flag to control the image, true = green tick, false = red cross
     * @param message Text to display
     */
	public void displayStatus(boolean isOk, String message) {
		Display.getDefault().syncExec(new Runnable(){

			@Override
			public void run() {
				if (useImages) {
					if (isOk)
						statusImage.setImage(okImage);
					else
						statusImage.setImage(rejectImage);
				}
				description.setText(message);
			}});
	}

	/**
	 * Display status of a directory location. A good location is shown with no message and an Android icon.
	 * @param sdkLocation Directory path as File object
	 */
	public void displayLocationStatus(File sdkLocation) {
		boolean locationExists = sdkLocation.exists();
		boolean locationDirectory = locationExists && sdkLocation.isDirectory();
		Display.getDefault().syncExec(new Runnable(){

			@Override
			public void run() {
				if (useImages) {
					if (!locationExists || !locationDirectory)
						statusImage.setImage(rejectImage);
					else
						statusImage.setImage(platformImage);
				}
				String message = "";
				if (!locationExists) 
					message = LOCATION_NOT_EXISTS;
				else if (!locationDirectory)
                    message = LOCATION_NOT_DIR;
				description.setText(message);
			}});
	}
	
    /**
     * Display package check in progress
     */
    public void displayPendingStatus() {
		Display.getDefault().syncExec(new Runnable(){

			@Override
			public void run() {
				if (useImages)
					statusImage.setImage(newItemImage);
				description.setText(AndroidSdkSelector.CHECKING_PACKAGES);
			}});
	}

	/**
	 * Create controls
	 * @param parent Parent composite
	 */
    private void createControls(Composite parent) {
		// Layout has 1 column
        mInnerGroup = new Composite(parent, SWT.NONE);
        mInnerGroup.setLayout(new GridLayout());
        mInnerGroup.setLayoutData(new GridData(GridData.FILL_BOTH));
        mInnerGroup.setFont(parent.getFont());
        int style = SWT.BORDER | SWT.SINGLE | SWT.FULL_SELECTION;
        if (mAllowSelection) {
            style |= SWT.CHECK;
        }
        mTable = new Table(mInnerGroup, style);
        mTable.setHeaderVisible(true);
        mTable.setLinesVisible(false);

        GridData data = new GridData();
        data.grabExcessVerticalSpace = true;
        data.grabExcessHorizontalSpace = true;
        data.horizontalAlignment = GridData.FILL;
        data.verticalAlignment = GridData.FILL;
        mTable.setLayoutData(data);

        //mDescription = new Label(mInnerGroup, SWT.WRAP);
        //mDescription.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        Group statusGroup = new Group(mInnerGroup, SWT.NONE);
        GridLayoutBuilder.create(statusGroup).columns(2);
        GridDataBuilder.create(statusGroup).hGrab().hFill();
        statusImage = new Label(statusGroup, SWT.NONE);
        // Set image so the label is not size = 0 when layout occurs
        if (newItemImage != null)
       	   statusImage.setImage(newItemImage);
        description = new Label(statusGroup,  SWT.NONE);
        description.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        // create the table columns
        final TableColumn column0 = new TableColumn(mTable, SWT.NONE);
        column0.setText("Target Name");
        final TableColumn column1 = new TableColumn(mTable, SWT.NONE);
        column1.setText("Vendor");
        final TableColumn column2 = new TableColumn(mTable, SWT.NONE);
        column2.setText("Platform");
        final TableColumn column3 = new TableColumn(mTable, SWT.NONE);
        column3.setText("API Level");

        adjustColumnsWidth(mTable, column0, column1, column2, column3);
        setupSelectionListener();
        setupTooltip();
        parent.getShell().addDisposeListener(new DisposeListener(){

			@Override
			public void widgetDisposed(DisposeEvent arg0) {
		    	disposeImages();
			}});
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
            final TableColumn column3) {
        // Add a listener to resize the column to the full width of the table
        table.addControlListener(new ControlAdapter() {
            @Override
            public void controlResized(ControlEvent e) {
                Rectangle r = table.getClientArea();
                int width = r.width;

                // On the Mac, the width of the checkbox column is not included (and checkboxes
                // are shown if mAllowSelection=true). Subtract this size from the available
                // width to be distributed among the columns.
                if (mAllowSelection
                        && SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_DARWIN) {
                    width -= getCheckboxWidth();
                }

                column0.setWidth(width * 30 / 100); // 30%
                column1.setWidth(width * 40 / 100); // 40%
                column2.setWidth(width * 15 / 100); // 15%
                column3.setWidth(width * 15 / 100); // 15%
            }
        });
    }


    /**
     * Creates a selection listener that will check or uncheck the whole line when
     * double-clicked (aka "the default selection").
     */
    private void setupSelectionListener() {
        if (!mAllowSelection) {
            return;
        }

        // Add a selection listener that will check/uncheck items when they are double-clicked
        mTable.addSelectionListener(new SelectionListener() {
            /** Default selection means double-click on "most" platforms */
            @Override
            public void widgetDefaultSelected(SelectionEvent e) {
                if (e.item instanceof TableItem) {
                    TableItem i = (TableItem) e.item;
                    i.setChecked(!i.getChecked());
                    enforceSingleSelection(i);
                    updateDescription(i);
                }

                if (mSelectionListener != null) {
                    mSelectionListener.widgetDefaultSelected(e);
                }
            }

            @Override
            public void widgetSelected(SelectionEvent e) {
                if (e.item instanceof TableItem) {
                    TableItem i = (TableItem) e.item;
                    enforceSingleSelection(i);
                    updateDescription(i);
                }

                if (mSelectionListener != null) {
                    mSelectionListener.widgetSelected(e);
                }
            }

            /**
             * If we're not in multiple selection mode, uncheck all other
             * items when this one is selected.
             */
            private void enforceSingleSelection(TableItem item) {
                if (item.getChecked()) {
                    Table parentTable = item.getParent();
                    for (TableItem i2 : parentTable.getItems()) {
                        if (i2 != item && i2.getChecked()) {
                            i2.setChecked(false);
                        }
                    }
                }
            }
        });
    }


    /**
     * Fills the table with all SDK targets.
     * The table columns are:
     * <ul>
     * <li>column 0: sdk name
     * <li>column 1: sdk vendor
     * <li>column 2: sdk platform
     * <li>column 3: sdk version
     * </ul>
     */
    private void fillTable() {

        if (mTable == null || mTable.isDisposed()) {
            return;
        }

        mTable.removeAll();

        if (mTargets != null && mTargets.length > 0) {
        	mTable.setEnabled(true);
            for (IAndroidTarget target : mTargets) {
                TableItem item = new TableItem(mTable, SWT.NONE);
                item.setData(target);
                item.setText(0, target.getName());
                item.setText(1, target.getVendor());
                String platform = target.getVersionName();
                if (platform == null)
                	platform = "";
                item.setText(2, platform);
                item.setText(3, target.getVersion().getApiString());
            }
        } else {
        	mTable.setEnabled(false);
            TableItem item = new TableItem(mTable, SWT.NONE);
            item.setData(null);
            item.setText(0, "--");
            item.setText(1, "No target available");
            item.setText(2, "--");
            item.setText(3, "--");
        }
    }

    /**
     * Sets up a tooltip that displays the current item description.
     * <p/>
     * Displaying a tooltip over the table looks kind of odd here. Instead we actually
     * display the description in a label under the table.
     */
    private void setupTooltip() {

        if (mTable == null || mTable.isDisposed()) {
            return;
        }

        /*
         * Reference:
         * http://dev.eclipse.org/viewcvs/index.cgi/org.eclipse.swt.snippets/src/org/eclipse/swt/snippets/Snippet125.java?view=markup
         */

        final Listener listener = new Listener() {
            @Override
            public void handleEvent(Event event) {

                switch(event.type) {
                case SWT.KeyDown:
                case SWT.MouseDown:
                    return;

                case SWT.MouseHover:
                    updateDescription(mTable.getItem(new Point(event.x, event.y)));
                    break;

                case SWT.MouseExit:
                	// Describe highlighted item, if any, otherwise the checked item
                	IAndroidTarget target = null;
                	int selection = mTable.getSelectionIndex();
                	if (selection != -1)
                		target = (IAndroidTarget)mTable.getItem(selection).getData();
                    if (target == null)
                    	target = getSelected();
                    if (target != null)
                    	updateDescription(target);
                    break;

                case SWT.Selection:
                    if (event.item instanceof TableItem) {
                        updateDescription((TableItem) event.item);
                    }
                    break;

                default:
                    return;
                }

            }
        };

        mTable.addListener(SWT.Dispose, listener);
        mTable.addListener(SWT.KeyDown, listener);
        mTable.addListener(SWT.MouseMove, listener);
        mTable.addListener(SWT.MouseExit, listener);
        mTable.addListener(SWT.MouseHover, listener);
    }

    /**
     * Updates the description label with the description of the item's android target, if any.
     * @param item The table item
     */
    private void updateDescription(TableItem item) {
        if (item != null) {
            Object data = item.getData();
            if (data instanceof IAndroidTarget) {
            	updateDescription((IAndroidTarget) data);
            }
        }
    }

    /**
     * Updates the description label with the description of the android target
     * @param target The Android target
     */
    private void updateDescription(IAndroidTarget target) {
        String newTooltip = target.getDescription();
        description.setText(newTooltip == null ? "" : newTooltip);  //$NON-NLS-1$
    }

    /** Enables or disables the controls. */
    public void setEnabled(boolean enabled) {
        if (mInnerGroup != null && mTable != null && !mTable.isDisposed()) {
            enableControl(mInnerGroup, enabled);
        }
    }

    /** Enables or disables controls; recursive for composite controls. */
    private void enableControl(Control c, boolean enabled) {
        c.setEnabled(enabled);
        if (c instanceof Composite)
        for (Control c2 : ((Composite) c).getChildren()) {
            enableControl(c2, enabled);
        }
    }

    /** Computes the width of a checkbox */
    private int getCheckboxWidth() {
        if (sCheckboxWidth == -1) {
            Shell shell = new Shell(mTable.getShell(), SWT.NO_TRIM);
            Button checkBox = new Button(shell, SWT.CHECK);
            sCheckboxWidth = checkBox.computeSize(SWT.DEFAULT, SWT.DEFAULT).x;
            shell.dispose();
        }

        return sCheckboxWidth;
    }

    /**
     * Create all the images and ensure their disposal
     */
    private void createImages() {
		PluginResourceProvider resourceProvider = new PluginResourceProvider(){

			@Override
			public ImageDescriptor descriptorFromPath(String imagePath) {
				return SdkUserInterfacePlugin.instance().getImageDescriptor("icons/" + imagePath);
			}};
    	useImages = false;
        ImageDescriptor descriptor = resourceProvider.descriptorFromPath("nopkg_icon_16.png");
        if (descriptor != null)
        	newItemImage = descriptor.createImage();
        else
        	return;
        descriptor = resourceProvider.descriptorFromPath("reject_icon16.png");
        if (descriptor != null)
        	rejectImage = descriptor.createImage();
        else
        	return;
        descriptor = resourceProvider.descriptorFromPath("platform_pkg_16.png");
        if (descriptor != null)
        	platformImage = descriptor.createImage();
        else
        	return;
        descriptor = resourceProvider.descriptorFromPath("status_ok_16.png"); 
        if (descriptor != null) {
        	okImage = descriptor.createImage();
        	useImages = (okImage != null) && (rejectImage != null) && (newItemImage != null) && (platformImage != null);
        }
    }
 
    /**
     * Dispose images
     */
    private void disposeImages() {
    	if (newItemImage != null)
    		newItemImage.dispose();
    	if (rejectImage != null)
    		rejectImage.dispose();
    	if (okImage != null)
    		okImage.dispose();
    	if (platformImage != null)
    		platformImage.dispose();
    }

}
