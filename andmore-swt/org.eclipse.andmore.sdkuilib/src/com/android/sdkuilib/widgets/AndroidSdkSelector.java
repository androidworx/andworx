/*
 * Copyright (C) 2017 The Android Open Source Project
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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.andmore.sdktool.preferences.AndroidSdk;
import org.eclipse.andmore.sdktool.install.SdkInstallListener;
import org.eclipse.andmore.sdktool.install.SdkProfile;
import org.eclipse.andmore.sdktool.install.SdkProfileListener;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.window.Window;
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
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

import com.android.SdkConstants;
import com.android.repository.api.LocalPackage;
import com.android.sdklib.repository.meta.DetailsTypes.ApiDetailsType;
import com.android.sdkuilib.ui.GridDataBuilder;
import com.android.sdkuilib.ui.GridLayoutBuilder;

/**
 * The Android SDK selector renders a checkbox table that is added to the given parent composite.
 * Only one SDK can be checked at any time. A check against an SDK indicates that chosen to be the 
 * workspace SDK. The last item of the table is reserved for a configuring a new or existing SDK. 
 * Apart from the last item, the user has the choice of 3 operations: Rename, Remove or Update.
 * @author Andrew Bowley
 *
 * 13-12-2017
 */
public class AndroidSdkSelector implements SdkInstallListener {
	public static final String PACKAGES =" The SDK requires %d more package%s to be installed";
	public static final String CHECKING_PACKAGES = "Checking SDK packages";
    private static final String NEW_SDK_NAME = "--new--";
    private static final String UPDATE = "Update";
    private static final String CONFIGURE = "Configure";
    private static final String REMOVE = "Remove";
    private static final String RENAME = "Rename";
	private static final String NEW_OR_EXISTING_SDK = "New or existing SDK";
	private static final String NO_PLATFORMS = "Location has no Android platform";
	private static final String SINGLE_PLATFORM ="%s installed";
	private static final String PLATFORMS ="The SDK has %d Android platforms installed";
	private static final String REPAIR = "Repair";
	private static final String NAME_USED = "Name \"%s\" already used";
	
    /** Cache for {@link #getCheckboxWidth()} */
    private static int checkboxWidth = -1;
   
    /** List of Android SDKs to display */
	private final List<AndroidSdk> androidSdks;
	/** AndroidSdkSelector parent control */
	private final AndroidSdkComposite androidSdkComposite;
	/** Callback to handle events to install a new SDK or update an existing one */
	private final AndroidSdkListener androidSdkListener;
    /** Make icon usage subject to all images available */
    private boolean useImages;
	// Controls
	private Shell shell;
    private Table table;
    private Label statusImage;
    private Label description;
    private Composite innerGroup;
	private Group commandGroup;
    private Button commandButton;
    /** Remove hides the item but does not touch the SDK */
    private Button removeButton;
    private Button renameButton;
	private ImageDescriptor titleDescriptor;
    private Image newItemImage;
    private Image rejectImage;
    private Image okImage;
    private Image platformImage;


    /**
     * Construct an AndroidSdkSelector object
     *
     * @param androidSdkComposite AndroidSdkSelector parent control
     * @param androidSdkListener Callback to handle events to install a new SDK or update an existing one
     */
    public AndroidSdkSelector(AndroidSdkComposite androidSdkComposite, AndroidSdkListener androidSdkListener) {
    	this.androidSdkComposite = androidSdkComposite;
    	this.androidSdkListener = androidSdkListener;
       	this.androidSdks = new ArrayList<>();
    	shell = androidSdkComposite.getParent().getShell();
    	createControls(androidSdkComposite.getParent());
        titleDescriptor = androidSdkComposite.getResourceProvider().descriptorFromPath("android-64.png");
    }
    
    /**
     * Returns the layout data of the inner composite widget that contains the target selector.
     * By default the layout data is set to a {@link GridData} with a {@link GridData#FILL_BOTH}
     * mode.
     * <p/>
     * This can be useful if you want to change the {@link GridData#horizontalSpan} for example.
     */
    public Object getLayoutData() {
        return innerGroup.getLayoutData();
    }

    /**
     * Display status using image and message
     * @param isOk Flag to control the image, true = green tick, false = red cross
     * @param message Text to display
     */
	public void displayStatus(boolean isOk, String message) {
		if (useImages) {
			if (isOk)
				statusImage.setImage(okImage);
			else
				statusImage.setImage(rejectImage);
		}
		description.setText(message);
	}

	/**
	 * Returns currently selected SDK, excluding the new item
	 * @return AndroidSdk object or null if none or new item selected
	 */
	public AndroidSdk getSelectedAndroidSdk() {
		AndroidSdk androidSdk = getCheckedItem();
		// Do not include the new item
		if ((androidSdk != null) && NEW_SDK_NAME.equals(androidSdk.getName()))
			androidSdk = null;
		return androidSdk;
	}

	/**
	 * Set SDK items to display
	 * @param sdkList Android SDK list
	 * @param param selection Which SDK location to highlight
	 */
    public void setSdkList(List<AndroidSdk> sdkList, String selection) {
    	boolean isNewList = androidSdks.isEmpty();
    	if (!isNewList)
    		androidSdks.clear();
		androidSdks.addAll(sdkList);
		// Add new item to end of selection
		AndroidSdk newSdk = new AndroidSdk(new File(""), NEW_SDK_NAME);
		androidSdks.add(newSdk);
    	Display.getDefault().syncExec(new Runnable(){

			@Override
			public void run() {
				try {
			        // There is no ordering of items to be displayed. They appear in given order.
			        fillTable();
		        	// Highlight selection item
		        	setHighLight(selection);
				} catch (Exception e) {
					androidSdkComposite.getLogger().error(e, "Error displaying items");
				}
			}});
	}

	/**
	 * Handle SDK install/update event
	 * @param newAndroidSdk New Android SDK specification
	 */
	@Override
	public void onSdkInstall(AndroidSdk newAndroidSdk) {
		if (newAndroidSdk != null) {
			AndroidSdk androidSdk = androidSdks.get(androidSdks.size() - 1);
			androidSdk.setName(newAndroidSdk.getName());
			androidSdk.setSdkLocation(newAndroidSdk.getSdkLocation());
			Display.getDefault().syncExec(new Runnable(){

				@Override
				public void run() {
			    	// Uncheck previous selection
			        for (TableItem item : table.getItems()) {
			            if (item.getChecked()) {
			            	item.setChecked(false);
			            	break;
			            }
			        }
					growTable();
			        // Check first item
			       	TableItem item = table.getItem(0);
			        item.setChecked(true);
				}});
			// Add new item
	    	AndroidSdk newSdk = new AndroidSdk(new File(""), NEW_SDK_NAME);
	    	this.androidSdks.add(newSdk);
			androidSdkComposite.onSdkChange(androidSdk);
		}
	}

	@Override
	public void onSdkAssign(AndroidSdk androidSdk) {
		// Not used
	}

	@Override
	public void onCancel() {
        if (table != null && !table.isDisposed()) {
			Display.getDefault().syncExec(new Runnable(){

				@Override
				public void run() {
			        int selectionIndex = table.getSelectionIndex();
			        if (selectionIndex == -1)
			        	selectionIndex = 0;
			        TableItem item = table.getItem(selectionIndex);
                	table.select(selectionIndex);
                	AndroidSdk androidSdk = updateDescription(item, false);
                	if (androidSdk != null)
                		setCommand(androidSdk);
				}});
        }
	}

	/**
     * Sets the SDK to be checked. Also updates description text and buttons.
     * <p/>
     * If the selection is actually changed, this will invoke the selection listener
     * (if any) with a null event.
     *
     * @param andriodSdk the SDK to be selected
     * @return true if the target could be selected, false otherwise.
     */
	private boolean setCheckedItem(AndroidSdk andriodSdk) {
        boolean found = false;
        if (table != null && !table.isDisposed()) {
            for (TableItem i : table.getItems()) {
                if (andriodSdk.equals(i.getData())) {
                    found = true;
                    if (!i.getChecked()) {
                         i.setChecked(true);
                         setDescription(andriodSdk);
                         setCommand(andriodSdk);
                    }
                } else if (i.getChecked()) {
                    i.setChecked(false);
                }
            }
        }
        return found;
    }

	/**
     * Sets the current SDK highlight. Also updates description text and buttons.
     * @param item the table item to be highlighted
     */
    private void setHighLight(TableItem item) {
        if (table != null && !table.isDisposed()) {
        	int index = 0;
            for (TableItem i : table.getItems()) {
                if (item.equals(i)) {
                	table.select(index);
                	AndroidSdk androidSdk = updateDescription(i);
                	if (androidSdk != null)
                		setCommand(androidSdk);
                	break;
                }
                ++index;
            }
        }
    }
    
	/**
     * Sets the current SDK highlight Also updates description text and buttons.
     * @param selection The SDK location to be highlighted
     */
    private void setHighLight(String selection) {
        if (table != null && !table.isDisposed()) {
        	int index = 0;
            for (TableItem item : table.getItems()) {
            	AndroidSdk androidSdk = (AndroidSdk)item.getData();
                if (androidSdk.getSdkLocation().toString().equals(selection)) {
                	table.select(index);
                    setDescription(androidSdk);
                    setCommand(androidSdk);
                	break;
                }
                ++index;
            }
        }
    }
    
    /**
     * Returns the checked item.
     *
     * @return AndroidSdk object or null if error
     */
    private AndroidSdk getCheckedItem() {
        if (table == null || table.isDisposed()) {
            return null;
        }

        for (TableItem item : table.getItems()) {
            if (item.getChecked()) {
                return (AndroidSdk) item.getData();
            }
        }
        return null;
    }

    /**
     * Returns the current item, not necessarily the checked one.
     *
     * @return AndroidSdk object or null if error
     */
    private AndroidSdk getCurrentSdk() {
        if (table == null || table.isDisposed()) {
            return null;
        }
        int selectionIndex = table.getSelectionIndex();
        if (selectionIndex == -1)
        	selectionIndex = 0;
        TableItem item = table.getItem(selectionIndex);
        return (AndroidSdk) item.getData();
    }

	/**
	 * Create controls
	 * @param parent Parent composite
	 */
    private void createControls(Composite parent) {
    	createImages();
        innerGroup = new Composite(parent, SWT.NONE);
        GridLayoutBuilder.create(innerGroup);
        GridDataBuilder.create(innerGroup).fill().grab();
        innerGroup.setFont(parent.getFont());

        int style = SWT.BORDER | SWT.SINGLE | SWT.FULL_SELECTION | SWT.CHECK;
        table = new Table(innerGroup, style);
        table.setHeaderVisible(true);
        table.setLinesVisible(false);
        table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        commandGroup = new Group(innerGroup, SWT.NONE);
        GridDataBuilder.create(commandGroup).hGrab().hFill();
        GridLayoutBuilder.create(commandGroup).columns(2);
        statusImage = new Label(commandGroup, SWT.NONE);
        // Set image so the label is not size = 0 when layout occurs
        if (newItemImage != null)
        	statusImage.setImage(newItemImage);
        description = new Label(commandGroup, SWT.WRAP);
        GridDataBuilder.create(description).hGrab().hFill();
        Composite buttonBar = new Composite(innerGroup, SWT.NONE);
        GridDataBuilder.create(buttonBar).vBottom().hGrab().hFill();
        GridLayoutBuilder.create(buttonBar).columns(3);
        buttonBar.setFont(parent.getFont());
        renameButton = new Button(buttonBar, SWT.PUSH);
        GridDataBuilder.create(renameButton).wHint(80).hGrab().hFill().hRight();
		renameButton.setFont(parent.getFont());
		renameButton.setText(RENAME);
		renameButton.setEnabled(false);
		renameButton.addSelectionListener(new SelectionListener(){

			@Override
			public void widgetSelected(SelectionEvent e) {
				doRename();
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
				widgetSelected(e);
			}});
		removeButton = new Button(buttonBar, SWT.PUSH);
        GridDataBuilder.create(removeButton).wHint(80);
        removeButton.setFont(parent.getFont());
        removeButton.setText(REMOVE);
        removeButton.setEnabled(false);
        removeButton.addSelectionListener(new SelectionListener(){

			@Override
			public void widgetSelected(SelectionEvent e) {
				doRemove();
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
				widgetSelected(e);
			}});
		commandButton = new Button(buttonBar, SWT.PUSH);
        GridDataBuilder.create(commandButton).wHint(80);
		commandButton.setFont(parent.getFont());
		commandButton.setText(CONFIGURE);
		commandButton.addSelectionListener(new SelectionListener(){

			@Override
			public void widgetSelected(SelectionEvent e) {
        		doCommand();
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
				widgetSelected(e);
			}});

        // create the table columns
        final TableColumn column0 = new TableColumn(table, SWT.NONE);
        column0.setText("SDK Name");
        final TableColumn column1 = new TableColumn(table, SWT.NONE);
        column1.setText("Location");

        adjustColumnsWidth(table, column0, column1);
        setupSelectionListener(table);
        setupTooltip(table);
    }

    /**
     * Handle command button pressed
     */
	private void doCommand() {
		AndroidSdk currentSdk = getCurrentSdk();
		if (currentSdk == null) {
			// This is not expected
			commandButton.setEnabled(false);
			return;
		}
		if (commandButton.getText().equals(CONFIGURE))
			doNewSdk(currentSdk);
		else
			doUpdateSdk(currentSdk);
	}

	/**
	 * Handle remove button pressed
	 */
	protected void doRemove() {
		int selected = table.getSelectionIndex();
		if (selected != -1) {
			AndroidSdk androidSdk = androidSdks.get(selected);
			table.remove(selected);
			androidSdks.remove(selected);
			setCheckedItem(androidSdks.get(0));
			androidSdk.setHidden(true);
			androidSdkComposite.onSdkChange(androidSdk);
		}
	}

	/**
	 * Handle configure button pressed
	 * @param androidSdk
	 */
	private void doNewSdk(AndroidSdk androidSdk) {
		AndroidSdkPrompt androidSdkPrompt = new AndroidSdkPrompt(shell, titleDescriptor, androidSdkComposite.getLogger(), true);
		androidSdkPrompt.setBlockOnOpen(true);
		if (androidSdkPrompt.open() == Window.OK) {
			androidSdkComposite.setPageComplete(false);
			commandButton.setEnabled(false);
			AndroidSdk newAndroidSdk = androidSdkPrompt.getAndroidSdk();
			// Determine in this is an existing SDK or new one based on the directory being empty
			if (androidSdkPrompt.getCreateNewSdk()) {
				// Use the copy of the SDK specification and update original only if install succeeds
     		    androidSdkListener.onAndroidSdkCommand(newAndroidSdk, this, true);
			} else {
				androidSdk.setName(newAndroidSdk.getName());
				File sdkPath = newAndroidSdk.getSdkLocation();
				androidSdk.setSdkLocation(sdkPath);
				SdkProfile sdkProfile = new SdkProfile(androidSdkComposite.getLogger());
				sdkProfile.evaluate(sdkPath, getSdkProfileListener());
				statusImage.setImage(platformImage);
		        description.setText(CHECKING_PACKAGES);
			}
		}
	}

    /**
     * Returns callback on SDK profile request
     * sdkProfileListener Wizard listener needing notification of SDK profile is good
     * @return SdkProfileListener object
     */
    private SdkProfileListener getSdkProfileListener() {
    	return  new SdkProfileListener(){

			@Override
			public void onProfile(SdkProfile sdkProfile) {
				// Update controls using profile
				Display.getDefault().syncExec(new Runnable(){

					@Override
					public void run() {
						processSdkProfile(sdkProfile);
					}
				});
			}
		};
    }
 
    /**
     * Sets status of SDK location and enables command button if it needs repair
     * @param sdkProfile The SDK profile
     * @return flag set true if SDK location is good
     */
    private boolean processSdkProfile(SdkProfile sdkProfile) {
		// Leave finish button in disabled state unless SDK has good profile
		commandButton.setText(UPDATE);
		commandButton.setEnabled(true);
    	boolean needsPackages = false;
		if (!sdkProfile.getSuccess()) {
			// Error while obtaining profile. This is not expected.
			displayStatus(false, sdkProfile.getStatusMessage());
		    return false;
		}
		// Check for at least one platform installed
		int platformCount = sdkProfile.getPlatforms().size();
		if (platformCount == 0) {
			displayStatus(false, NO_PLATFORMS);
			needsPackages = true;
		}
		else {
			// Check if required packages are missing
			int requiredPackageCount = sdkProfile.getRequiredPackageCount();
			needsPackages = requiredPackageCount != 0;
			if (requiredPackageCount > 0) {
			    String plural = requiredPackageCount > 1 ? "s" : "";
			    displayStatus(false, String.format(PACKAGES, requiredPackageCount, plural));
			} else if (platformCount == 1) {
				// Show details for a single platform
				LocalPackage platform = sdkProfile.getPlatforms().iterator().next();
				ApiDetailsType typeDetails = (ApiDetailsType)platform.getTypeDetails();
				displayStatus(true, String.format(SINGLE_PLATFORM, String.format("Android SDK Platform %s", typeDetails.getAndroidVersion().toString())));
			}
			else // Just show number of platforms if more than one installed
				displayStatus(true, String.format(PLATFORMS, platformCount));
		}
		if (needsPackages)
			commandButton.setText(REPAIR);
		androidSdkComposite.setPageComplete(!needsPackages);
    	// Uncheck previous selection
        for (TableItem item : table.getItems()) {
            if (item.getChecked()) {
            	item.setChecked(false);
            	break;
            }
        }
        // Check if new SDK is a duplicate
		boolean isDuplicate = false;
     	AndroidSdk androidSdk = androidSdks.get(androidSdks.size() - 1);
     	if (androidSdks.size() > 1)
	     	for (int index = 0; index < androidSdks.size() - 2; ++index) {
			    AndroidSdk sdk = androidSdks.get(index);
				if (sdk.getSdkLocation().equals(androidSdk.getSdkLocation())) {
					isDuplicate = true;
					sdk.setHidden(false);
					if (sdk.getName() != androidSdk.getName()) {
						sdk.setName(androidSdk.getName());
						androidSdkListener.onAndroidSdkDirty(sdk);
					}
					if (index != 0) {
						// Move SDK to top of the list
						androidSdks.add(0, androidSdks.remove(index));
						// Change last SDK bank to new item
						androidSdk.setName(NEW_SDK_NAME);
						androidSdk.setSdkLocation(new File(""));
						fillTable();
					}
					break;
				}
		}
		if (!isDuplicate)
			growTable();
        // Check first item
       	TableItem item = table.getItem(0);
        item.setChecked(true);
		return !needsPackages; 
	}

	/**
	 * Handle update button pressed
	 * @param androidSdk Selected SDK to update
	 */
    protected void doUpdateSdk(AndroidSdk androidSdk) {
		androidSdkListener.onAndroidSdkCommand(androidSdk, this, commandButton.getText().equals(REPAIR));
	}

    /**
     * Handle rename button pressed
     */
	private void doRename() {
		int selected = table.getSelectionIndex();
		if (selected != -1) {
			AndroidSdk androidSdk = androidSdks.get(selected);
			FieldPrompt fieldPrompt = 
					new FieldPrompt(
							shell, 
						"Rename SDK", 
						AndroidSdkPrompt.SDK_NAME, 
						androidSdk.getName(), 
						titleDescriptor);
			fieldPrompt.setBlockOnOpen(true);
			if (fieldPrompt.open() == Window.OK) {
				String newName = fieldPrompt.getValue();
				if (!newName.isEmpty()) {
					for (AndroidSdk sdk: androidSdks)
						if (newName.equals(sdk.getName()) && !sdk.equals(androidSdk)) {
						    displayStatus(false, String.format(NAME_USED, newName));
							return;
						}
				}
				androidSdk.setName(newName);
				TableItem item = table.getItem(selected);
				if (item != null)
					item.setText(0, androidSdk.getName());
				description.setText(androidSdk.getName());
				androidSdkComposite.onSdkChange(androidSdk);
			}
		}
	}

	/**
     * Adds a listener to adjust the columns width when the parent is resized.
     * <p/>
     * If we need something more fancy, we might want to use this:
     * http://dev.eclipse.org/viewcvs/index.cgi/org.eclipse.swt.snippets/src/org/eclipse/swt/snippets/Snippet77.java?view=co
     */
    private void adjustColumnsWidth(final Table table,
            final TableColumn column0,
            final TableColumn column1) {
        // Add a listener to resize the column to the full width of the table
        table.addControlListener(new ControlAdapter() {
            @Override
            public void controlResized(ControlEvent e) {
                Rectangle r = table.getClientArea();
                int width = r.width;

                // On the Mac, the width of the checkbox column is not included (and checkboxes
                // are shown if mAllowSelection=true). Subtract this size from the available
                // width to be distributed among the columns.
                if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_DARWIN) {
                    width -= getCheckboxWidth();
                }

                column0.setWidth(width * 30 / 100); // 30%
                column1.setWidth(width * 70 / 100); // 70%
            }
        });
    }

    /** Computes the width of a checkbox */
    private int getCheckboxWidth() {
        if (checkboxWidth == -1) {
            Shell shell = new Shell(table.getShell(), SWT.NO_TRIM);
            Button checkBox = new Button(shell, SWT.CHECK);
            checkboxWidth = checkBox.computeSize(SWT.DEFAULT, SWT.DEFAULT).x;
            shell.dispose();
        }
        return checkboxWidth;
    }

    /**
     * Creates a selection listener that will check or uncheck the whole line when
     * double-clicked (aka "the default selection").
     */
    private void setupSelectionListener(final Table table) {
        // Add a selection listener that will check/uncheck items when they are double-clicked
        table.addSelectionListener(new SelectionListener() {
            /** Default selection means double-click on "most" platforms */
            @Override
            public void widgetDefaultSelected(SelectionEvent e) {
                if (e.item instanceof TableItem) {
                    TableItem i = (TableItem) e.item;
                    i.setChecked(!i.getChecked());
                    enforceSingleSelection(i);
                    setHighLight(i);
                }
            }

            @Override
            public void widgetSelected(SelectionEvent e) {
                if (e.item instanceof TableItem) {
                    TableItem i = (TableItem) e.item;
                    enforceSingleSelection(i);
                    setHighLight(i);
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
     * Updates the description label with the description of the item's android SDK
     */
    private AndroidSdk updateDescription(TableItem item) {
    	return updateDescription(item, true);
    }
    
    /**
     * Sets the icon, description and command buttons for given table item 
     * @param item The table item
     * @param isSelect Flag set true if item is highlighted
     */
    private AndroidSdk updateDescription(TableItem item, boolean isSelect) {
        if (item != null) {
            Object data = item.getData();
            if (data instanceof AndroidSdk) { 
            	AndroidSdk androidSdk = (AndroidSdk)data;
            	updateDescription(androidSdk, isSelect);
            	return androidSdk;
            }
        }
        return null;
    }

    /**
     * Sets the icon, description and command buttons for given Android SDK description and selection state
     * @param androidSdk The Android SDK description
     * @param isSelect Flag set true if item is highlighted
     */
    private void updateDescription(AndroidSdk androidSdk, boolean isSelect) {
    	boolean isCurrentSelection = false;
		AndroidSdk currentSdk = getCheckedItem();
    	isCurrentSelection = androidSdk.equals(currentSdk);
     	if (!isSelect && !isCurrentSelection) {
     		// Update current SDK selection variable
    		if (currentSdk != null)
    			androidSdk = currentSdk;
    		else { // This is not expected
                description.setText("");
                commandButton.setEnabled(false);
                return;
    		}
    	}
     	setDescription(androidSdk);
    }
    
    /**
     * Sets the icon, description and command buttons for given Android SDK description
     * @param androidSdk
     */
    private void setDescription(AndroidSdk androidSdk) {
        String newTooltip;
        if (androidSdk.getName().equals(NEW_SDK_NAME)) {
        	newTooltip = NEW_OR_EXISTING_SDK;
        } else {
        	newTooltip = androidSdk.getDetails();
        }
		statusImage.setImage(newItemImage);
        description.setText(newTooltip);
    }
    
    /**
     * Sets the icon, description and command buttons for given Android SDK description
     * @param androidSdk
     */
    private void setCommand(AndroidSdk androidSdk) {
        Image image = newItemImage;
        // Make new installation a special case where reject icon is not used
        boolean isNewInstall = androidSdks.size() == 1;
        boolean isTicked = androidSdk.equals(getCheckedItem());
        if (androidSdk.getName().equals(NEW_SDK_NAME)) {
    		commandButton.setText(CONFIGURE);
    		removeButton.setEnabled(false);
    		renameButton.setEnabled(false);
	        if (isTicked && !isNewInstall)
	        	image = rejectImage;
        } else {
    		commandButton.setText(UPDATE);
    		if (table.getSelectionIndex() != -1) {
    			removeButton.setEnabled(true);
    			renameButton.setEnabled(true);
 	        	if (isTicked)
	        		image = okImage;
     		} 
        }
        commandButton.setEnabled(true);
        // Update wizard finish button enable
        AndroidSdk checkedSdk = getCheckedItem();
        boolean isCheckedSdk = (checkedSdk != null) && androidSdk.equals(checkedSdk);
        if (isCheckedSdk) {
        	androidSdkComposite.setPageComplete(!isNewInstall && (image != rejectImage));
    		statusImage.setImage(image);
        }
    }
    
	/**
     * Fills the table with all Android SDKs.
     * The table columns are:
     * <ul>
     * <li>column 0: sdk name
     * <li>column 1: sdk location
     * </ul>
     */
    private void fillTable() {

        if (table == null || table.isDisposed()) {
            return;
        }
        table.removeAll();
        if (androidSdks.size() > 0) {
            table.setEnabled(true);
            for (AndroidSdk androidSdk: androidSdks) {
            	if (androidSdk.isHidden())
            		continue;
                TableItem item = new TableItem(table, SWT.NONE);
                item.setData(androidSdk);
                item.setText(0, androidSdk.getName());
                if (androidSdk.getName().equals(NEW_SDK_NAME)) {
                	item.setText(1, "");
                	item.setGrayed(true);
                }
                else
                	item.setText(1, androidSdk.getTruncatedLocation());
             }
        } else { // Not expected
            table.setEnabled(false);
            TableItem item = new TableItem(table, SWT.NONE);
            item.setData(null);
            item.setText(0, "--");
            item.setText(1, "No SDK available");
        }
    }

    /**
     * Insert just configured item at the top of the table, set it to checked state and append a "--new--" item.
     */
    private void growTable() {
        // Transfer configured AndroidSdk to top of list
     	AndroidSdk androidSdk = androidSdks.remove(androidSdks.size() - 1);
     	// Insert it at the top of the list
     	androidSdks.add(0, androidSdk);
		// Add new item to end of selection
		AndroidSdk newSdk = new AndroidSdk(new File(""), NEW_SDK_NAME);
		androidSdks.add(newSdk);
        // Re-fill table
        fillTable();
    }
    
    /**
     * Sets up a tooltip that displays the current item description.
     * <p/>
     * Displaying a tooltip over the table looks kind of odd here. Instead we actually
     * display the description in a label under the table.
     */
    private void setupTooltip(final Table table) {

        if (table == null || table.isDisposed()) {
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
                	// Describe item under mouse
                    updateDescription(table.getItem(new Point(event.x, event.y)), event.type == SWT.MouseHover);
                    break;

                case SWT.MouseExit:
                	// Describe highlighted item, if any, otherwise the checked item
                	AndroidSdk currentSdk = null;
                	int selection = table.getSelectionIndex();
                	if (selection != -1)
                		currentSdk = (AndroidSdk)table.getItem(selection).getData();
                    if (currentSdk == null)
                    	currentSdk = getCheckedItem();
                    if (currentSdk != null)
                    	updateDescription(currentSdk, true);
                    break;

                case SWT.Selection:
                    if (event.item instanceof TableItem) {
                    	// Describe selected item
                        updateDescription((TableItem) event.item);
                    }
                    break;

                default:
                    return;
                }

            }
        };
        table.addListener(SWT.Dispose, listener);
        table.addListener(SWT.KeyDown, listener);
        table.addListener(SWT.MouseMove, listener);
        table.addListener(SWT.MouseExit, listener);
        table.addListener(SWT.MouseHover, listener);
    }

    /**
     * Create all the images and ensure their disposal
     */
    private void createImages() {
    	useImages = false;
        ImageDescriptor descriptor = androidSdkComposite.getResourceProvider().descriptorFromPath("nopkg_icon_16.png");
        if (descriptor != null)
        	newItemImage = descriptor.createImage();
        else
        	return;
        descriptor = androidSdkComposite.getResourceProvider().descriptorFromPath("reject_icon16.png");
        if (descriptor != null)
        	rejectImage = descriptor.createImage();
        else
        	return;
        descriptor = androidSdkComposite.getResourceProvider().descriptorFromPath("platform_pkg_16.png");
        if (descriptor != null)
        	platformImage = descriptor.createImage();
        else
        	return;
        descriptor = androidSdkComposite.getResourceProvider().descriptorFromPath("status_ok_16.png"); 
        if (descriptor != null) {
        	okImage = descriptor.createImage();
        	useImages = (okImage != null) && (rejectImage != null) && (newItemImage != null) && (platformImage != null);
        }
        shell.addDisposeListener(new DisposeListener(){

			@Override
			public void widgetDisposed(DisposeEvent e) {
		    	disposeImages();
			}});
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
