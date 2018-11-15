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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.andmore.sdktool.SdkContext;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.viewers.CheckStateChangedEvent;
import org.eclipse.jface.viewers.CheckboxTreeViewer;
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.ICheckStateListener;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.ITreeSelection;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.jface.window.ToolTip;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;

import com.android.repository.api.RemotePackage;
import com.android.sdkuilib.internal.repository.LoadPackagesRequest;
import com.android.sdkuilib.internal.repository.LoadPackagesTask;
import com.android.sdkuilib.internal.repository.PackageDeleteTask;
import com.android.sdkuilib.internal.repository.PackageInstallListener;
import com.android.sdkuilib.internal.repository.PackageManager;
import com.android.sdkuilib.internal.repository.Settings;
import com.android.sdkuilib.internal.repository.content.CategoryKeyType;
import com.android.sdkuilib.internal.repository.content.INode;
import com.android.sdkuilib.internal.repository.content.PackageAnalyser;
import com.android.sdkuilib.internal.repository.content.PackageAnalyser.PkgState;
import com.android.sdkuilib.internal.repository.content.PackageContentProvider;
import com.android.sdkuilib.internal.repository.content.PackageFilter;
import com.android.sdkuilib.internal.repository.content.PackageInstaller;
import com.android.sdkuilib.internal.repository.content.PackageType;
import com.android.sdkuilib.internal.repository.content.PkgCategory;
import com.android.sdkuilib.internal.repository.content.PkgCellAgent;
import com.android.sdkuilib.internal.repository.content.PkgCellLabelProvider;
import com.android.sdkuilib.internal.repository.content.PkgItem;
import com.android.sdkuilib.internal.repository.content.PkgTreeColumnViewerLabelProvider;
import com.android.sdkuilib.internal.repository.content.ViewTreeRootNode;
import com.android.sdkuilib.internal.tasks.ILogUiProvider;
import com.android.sdkuilib.internal.widgets.PackageTypesSelector;
import com.android.sdkuilib.ui.GridDataBuilder;
import com.android.sdkuilib.ui.GridLayoutBuilder;

/**
 * Page that displays both locally installed packages as well as all known
 * remote available packages. This gives an overview of what is installed
 * vs what is available and allows the user to update or install packages.
 */
public final class PackagesPage extends Composite {

    enum MenuAction {
        RELOAD  (SWT.NONE,  "Reload"),
        TOGGLE_SHOW_OBSOLETE_PKG  (SWT.CHECK, "Show Obsolete Packages"),
        TOGGLE_FORCE_HTTP (SWT.CHECK, "Force HTTP protocol"),
        TOGGLE_SHOW_NEW_PKG  (SWT.CHECK, "Show New Packages"),
        FILTER_PACKAGES  (SWT.NONE,  "Filter Packages");

        private final int mMenuStyle;
        private final String mMenuTitle;

        MenuAction(int menuStyle, String menuTitle) {
            mMenuStyle = menuStyle;
            mMenuTitle = menuTitle;
        }

        public int getMenuStyle() {
            return mMenuStyle;
        }

        public String getMenuTitle() {
            return mMenuTitle;
        }
    };

    /**
     * Package types required to install a platform
     */
	public static final PackageType[] TOOLS_PACKAGE_TYPES = {
			PackageType.tools,
			PackageType.platform_tools,
			PackageType.build_tools
	};
	private static final List<RemotePackage> EMPTY_PACKAGE_LIST = Collections.emptyList();
	private static final String NONE_INSTALLED = "Done. Nothing was installed.";
	private static final String[] UPDATE_CHANNELS ;

	static {
		UPDATE_CHANNELS = new String[] { "Stable", "Beta", "Dev", "Canary" };
	}
	
	private final Map<MenuAction, MenuItem> mMenuActions = new HashMap<MenuAction, MenuItem>();

	/** SDK context - includes logger and process indicator */
    private final SdkContext mSdkContext;
    /** Builds and maintains the tree that underlies the SDK packages view */
    private final PackageAnalyser mPackageAnalyser;
    /** Package filter which allows user to select which package types are displayed */
    private PackageFilter mPackageFilter;
    /** A factory for various task types linked to a ProgressView control */
    private SdkProgressFactory factory;
    /** List of installed packages update on every packages installation */
    private List<RemotePackage> packagesInstalled;
    /** Flag to disable install and delete buttons after selection until operation completed */
    private volatile boolean mOperationPending;
    /** Shell of parent composite */
    private Shell shell;
    /** Composite enclosing packages view */
    private Composite mGroupPackages;
    /** Checkbox to show obsolete packages */
    private Button mCheckFilterObsolete;
    /** Checkbox to show packages availailable to install */
    private Button mCheckFilterNew;
    /** Checkbox to show all available packages, not just the latest versios */
    private Button mCheckAll;
    private Combo mComboChannel;
    /** Group exclosing options to show packages */
    private Composite mGroupOptions;
    /** Top composite */
    private Composite mGroupSdk;
    /** Delete packages button */
    private Button mButtonDelete;
    /** Install packages button */
    private Button mButtonInstall;
    /** Package types selection button */
    private Button mButtonPkgTypes;
    /** Cancel/OK button */
    private Button mButtonCancel;
    /** Font used to render italic text */
    private Font mTreeFontItalic;
    /** Tree view */
    private CheckboxTreeViewer mTreeViewer;

    /**
     * Construct PackagesPage object
     * @param parent Parent composite
     * @param swtStyle SWT.NONE
     * @param sdkContext SDK context
     * @param packageTypeSet Set of package types on which to filter. An empty set indicates no filtering
     * @param tagFilter Tag to filter packages = applies only to system images
     */
    public PackagesPage(
            Composite parent,
            int swtStyle,
            SdkContext sdkContext,
            Set<PackageType> packageTypeSet,
            String tagFilter) 
    {
        super(parent, swtStyle);
        shell = getShell();
        mSdkContext = sdkContext;
        mPackageFilter = new PackageFilter(packageTypeSet);
        if ((tagFilter != null) && !tagFilter.isEmpty())
        	mPackageFilter.setTag(tagFilter);
        mPackageAnalyser = new PackageAnalyser(sdkContext);
        mPackageAnalyser.getRootNode().setPackageFilter(mPackageFilter);
        packagesInstalled = EMPTY_PACKAGE_LIST;
        createContents(this);
        postCreate();
    }

    /**
     * Handle event ProgressView is created. Loading of packages can commence.
     * @param factory A factory for various task types linked to a ProgressView control
     */
    public void onReady(SdkProgressFactory factory) {
    	this.factory = factory;
    	startLoadPackages();
    }

    /**
     * Handle event thet SDK has been reloaded
     */
    public void onSdkReload() {
    	startLoadPackages();
    }

    /**
     * Returns list of packages installed last time this operation was invoked
     * @return
     */
	public List<RemotePackage> getPackagesInstalled() {
		return packagesInstalled;
	}

	/**
	 * Create controls. Invoked from the constructor.
	 * @param parent Parent composite
	 */
    private void createContents(Composite parent) 
    {
    	Color foreColor = parent.getForeground();
    	Color backColor = parent.getBackground();
    	Display display = parent.getDisplay();
    	Color hiForeColor = display.getSystemColor(SWT.COLOR_INFO_FOREGROUND);
       	Color hiBackColor = display.getSystemColor(SWT.COLOR_INFO_BACKGROUND);
    	GridLayoutBuilder.create(parent).noMargins().columns(2);

        mGroupSdk = new Composite(parent, SWT.NONE);
        GridDataBuilder.create(mGroupSdk).hFill().vCenter().hGrab().hSpan(2);
        GridLayoutBuilder.create(mGroupSdk).columns(2);

        Group groupPackages = new Group(parent, SWT.NONE);
        mGroupPackages = groupPackages;
        GridDataBuilder.create(mGroupPackages).fill().grab().hSpan(2);
        groupPackages.setText("Packages by Category");
        GridLayoutBuilder.create(groupPackages).columns(2);

        mTreeViewer = new CheckboxTreeViewer(groupPackages, SWT.BORDER);
        mTreeViewer.addFilter(new ViewerFilter() 
        {
            @Override
            public boolean select(Viewer viewer, Object parentElement, Object element) {
                return filterViewerItem(element);
            }
        });

        mTreeViewer.addCheckStateListener(new ICheckStateListener() 
        {
            @Override
            public void checkStateChanged(CheckStateChangedEvent event) {
                onTreeCheckStateChanged(event); //$hide$
            }
        });

        mTreeViewer.addDoubleClickListener(new IDoubleClickListener() 
        {
            @Override
            public void doubleClick(DoubleClickEvent event) {
                onTreeDoubleClick(event); //$hide$
            }
        });

        Tree tree = mTreeViewer.getTree();
        tree.setLinesVisible(true);
        tree.setHeaderVisible(true);
        GridDataBuilder.create(tree).hSpan(2).fill().grab();

        // column name icon is set when loading depending on the current filter type
        // (e.g. API level or source)
        TreeViewerColumn columnName = new TreeViewerColumn(mTreeViewer, SWT.CENTER);
        TreeColumn treeColumn1 = columnName.getColumn();
        treeColumn1.setText("Name");
        treeColumn1.setWidth(400);
        // Android icon placed on left of Name column is a remnant of a feature which has been removed
        //treeColumn1.setImage(getImage(PackagesPageIcons.ICON_SORT_BY_API));

        TreeViewerColumn columnApi = new TreeViewerColumn(mTreeViewer, SWT.NONE);
        TreeColumn treeColumn2 = columnApi.getColumn();
        treeColumn2.setText("API");
        treeColumn2.setAlignment(SWT.LEFT);
        treeColumn2.setWidth(35);

        TreeViewerColumn columnRevision = new TreeViewerColumn(mTreeViewer, SWT.NONE);
        TreeColumn treeColumn3 = columnRevision.getColumn();
        treeColumn3.setText("Revision");
        treeColumn3.setToolTipText("Revision currently installed");
        treeColumn3.setAlignment(SWT.LEFT);
        treeColumn3.setWidth(80);


        TreeViewerColumn columnStatus = new TreeViewerColumn(mTreeViewer, SWT.NONE);
        TreeColumn treeColumn4 = columnStatus.getColumn();
        treeColumn4.setText("Status");
        treeColumn4.setAlignment(SWT.LEAD);
        treeColumn4.setWidth(205);

        mGroupOptions = new Group(groupPackages, SWT.SHADOW_NONE);
        GridDataBuilder.create(mGroupOptions).hFill().vCenter().hGrab();
        GridLayoutBuilder.create(mGroupOptions).columns(8).noMargins();
 
        // Options line 1, 8 columns

        Label label3 = new Label(mGroupOptions, SWT.NONE);
        label3.setText("Show:");
        GridDataBuilder.create(label3).vSpan(2).vTop();
        Composite groupShow = new Composite(mGroupOptions, SWT.NONE);
        GridLayoutBuilder.create(groupShow).columns(2).noMargins();
        GridDataBuilder.create(groupShow).hFill().vCenter();
        mCheckFilterNew = new Button(groupShow, SWT.CHECK);
        GridDataBuilder.create(mCheckFilterNew).vTop();
        mCheckFilterNew.setText("New");
        mCheckFilterNew.setToolTipText("Show latest available new packages");
        mCheckFilterNew.addSelectionListener(new SelectionAdapter() 
        {
            @Override
            public void widgetSelected(SelectionEvent event) {
                // Only enable check all if "New" is checked
                mCheckAll.setEnabled(((Button)event.getSource()).getSelection());
                refreshViewerInput();
            }
        });
        mCheckFilterNew.setSelection(true);
        mCheckAll = new Button(groupShow, SWT.CHECK);
        GridDataBuilder.create(mCheckAll).vSpan(2).vTop();
        mCheckAll.setText("All");
        mCheckAll.setToolTipText("Show all available new packages");
        mCheckAll.addSelectionListener(new SelectionAdapter() 
        {
            @Override
            public void widgetSelected(SelectionEvent e) {
                refreshViewerInput();
            }
        });
        Label label4 = new Label(mGroupOptions, SWT.NONE);
        label4.setText("Select:");
        GridDataBuilder.create(label4).vSpan(2).vTop();
        Button buttonSelectUpdates = new Button(mGroupOptions, SWT.FLAT);
        GridDataBuilder.create(buttonSelectUpdates).vTop().wHint(100).hHint(22);
        buttonSelectUpdates.setText("Updates");
        buttonSelectUpdates.setToolTipText("Selects all items that are updates.");
        buttonSelectUpdates.addSelectionListener(new SelectionAdapter() 
        {
            @Override
            public void widgetSelected(SelectionEvent e) {
                super.widgetSelected(e);
                onSelectPackages(true, false); // selectTop
            }
        });

        Label label6 = new Label(mGroupOptions, SWT.NONE);
        label6.setText("Categories:");
        GridDataBuilder.create(label6).vTop();
        mButtonPkgTypes = new Button(mGroupOptions, SWT.NONE);
        mButtonPkgTypes.setText("Filter...");  
        mButtonPkgTypes.setToolTipText("Click to filter by category");
        GridDataBuilder.create(mButtonPkgTypes).wHint(100).hHint(22).vTop();
        mButtonPkgTypes.addSelectionListener(new SelectionAdapter() 
        {
            @Override
            public void widgetSelected(SelectionEvent e) {
            	selectPackages();
            }
        });
		mButtonInstall = new Button(mGroupOptions, SWT.NONE);
        mButtonInstall.setText("Install package");  // filled in updateButtonsState()
        mButtonInstall.setToolTipText("Install one or more packages");
        GridDataBuilder.create(mButtonInstall).vCenter().wHint(150).hFill().hGrab().hRight();
        mButtonInstall.addSelectionListener(new SelectionAdapter() 
        {
            @Override
            public void widgetSelected(SelectionEvent e) {
                onButtonInstall();  
            }
        });
        mButtonCancel = new Button(mGroupOptions, SWT.NONE);
        mButtonCancel.setText("OK");  
        GridDataBuilder.create(mButtonCancel).wHint(100).vSpan(2).vBottom();
        mButtonCancel.addSelectionListener(new SelectionAdapter() 
        {
            @Override
            public void widgetSelected(SelectionEvent e) {
                mSdkContext.getProgressIndicator().cancel();  
                if (!mOperationPending)
                    shell.close();
            }
        });
        mCheckFilterObsolete = new Button(mGroupOptions, SWT.CHECK);
        GridDataBuilder.create(mCheckFilterObsolete).vTop();
        mCheckFilterObsolete.setText("Obsolete");
        mCheckFilterObsolete.setToolTipText("Also show obsolete packages");
        mCheckFilterObsolete.addSelectionListener(new SelectionAdapter() 
        {
            @Override
            public void widgetSelected(SelectionEvent event) {
            	if (((Button)event.getSource()).getSelection()) {
            		mCheckFilterObsolete.setForeground(hiForeColor);
            		mCheckFilterObsolete.setBackground(hiBackColor);
            	} else {
            		mCheckFilterObsolete.setForeground(foreColor);
            		mCheckFilterObsolete.setBackground(backColor);
            	}
                refreshViewerInput();
            }
        });
        mCheckFilterObsolete.setSelection(false);

        Button buttonDeselect = new Button(mGroupOptions, SWT.FLAT);
        GridDataBuilder.create(buttonDeselect).vTop().wHint(100).hHint(22);
        buttonDeselect.setText("Deselect All");
        buttonDeselect.setToolTipText("Deselects all the currently selected items");
        buttonDeselect.addSelectionListener(new SelectionAdapter() 
        {
            @Override
            public void widgetSelected(SelectionEvent e) {
                super.widgetSelected(e);
                onDeselectAll();
            }
        });
        
        Label label5 = new Label(mGroupOptions, SWT.NONE);
        label5.setText("Channel:");
        GridDataBuilder.create(label5).vTop();
        mComboChannel = new Combo(mGroupOptions, SWT.DROP_DOWN | SWT.READ_ONLY);
        GridDataBuilder.create(mComboChannel).wHint(80).vTop();
        mComboChannel.setToolTipText("Select update channel. \"Canary\" is the least stable.");
        for (String channelName: UPDATE_CHANNELS)
        	mComboChannel.add(channelName);
        mComboChannel.select(0);
		mComboChannel.addModifyListener(new ModifyListener(){

			@Override
			public void modifyText(ModifyEvent arg0) {
				int index = mComboChannel.getSelectionIndex();
				if (index != -1) {
					mSdkContext.getSettings().setChannel(index);
					startLoadPackages();
				}
			}});
        mButtonDelete = new Button(mGroupOptions, SWT.NONE);
        mButtonDelete.setText("Delete package");  // filled in updateButtonsState()
        mButtonDelete.setToolTipText("Delete one ore more installed packages");
        GridDataBuilder.create(mButtonDelete).vCenter().wHint(150).hFill().hGrab().hRight();
        mButtonDelete.addSelectionListener(new SelectionAdapter() 
        {
            @Override
            public void widgetSelected(SelectionEvent e) {
                onButtonDelete();  
            }
        });
        mGroupOptions.pack();
        FontData fontData = tree.getFont().getFontData()[0];
        fontData.setStyle(SWT.ITALIC);
        mTreeFontItalic = new Font(tree.getDisplay(), fontData);
        tree.addDisposeListener(new DisposeListener() {
            @Override
            public void widgetDisposed(DisposeEvent e) {
                mTreeFontItalic.dispose();
                mTreeFontItalic = null;
            }
        });
        mTreeViewer.setContentProvider(new PackageContentProvider(mPackageFilter));
        PkgCellAgent pkgCellAgent = new PkgCellAgent(mSdkContext, mPackageAnalyser, mTreeFontItalic);
        columnApi.setLabelProvider(
                new PkgTreeColumnViewerLabelProvider(new PkgCellLabelProvider(pkgCellAgent, PkgCellAgent.API)));
        columnName.setLabelProvider(
                new PkgTreeColumnViewerLabelProvider(new PkgCellLabelProvider(pkgCellAgent, PkgCellAgent.NAME)));
        columnStatus.setLabelProvider(
                new PkgTreeColumnViewerLabelProvider(new PkgCellLabelProvider(pkgCellAgent, PkgCellAgent.STATUS)));
        columnRevision.setLabelProvider(
                new PkgTreeColumnViewerLabelProvider(new PkgCellLabelProvider(pkgCellAgent, PkgCellAgent.REVISION)));
    }

    // -- Start of internal part ----------
    // Hide everything down-below from SWT designer
    //$hide>>$


    /**
     * Returns image specified by name
     * @param filename Image filename to be located by SDK context helper
     * @return Image object - do not dispose - or null if image not found
     */
/*
	private Image getImage(String filename) {
            ImageFactory imgFactory = mSdkContext.getSdkHelper().getImageFactory();
            if (imgFactory != null) {
                return imgFactory.getImageByName(filename);
            }
        return null;
    }
*/

    // --- menu interactions ---
	/**
	 * Register menu action with corresponding menu item
	 * @param action Menu action
	 * @param item Menu item
	 */
    protected void registerMenuAction(final MenuAction action, MenuItem item) {
    	item.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                 Button button = null;

                switch (action) {
                case RELOAD:
                	startLoadPackages();
                    break;
                case TOGGLE_SHOW_OBSOLETE_PKG:
                    button = mCheckFilterObsolete;
                    break;
                case TOGGLE_SHOW_NEW_PKG:
                    button = mCheckFilterNew;
                    break;
                case FILTER_PACKAGES:
                	selectPackages();
                	break;
                case TOGGLE_FORCE_HTTP:
                	Settings settings = mSdkContext.getSettings();
                	boolean value = !settings.getForceHttp();
                	settings.setForceHttp(value);
                	factory.setSecondaryText((value ? "F" : "Do not f") + "orce http");
                }

                if (button != null && !button.isDisposed()) {
                    // Toggle this button (radio or checkbox)

                    boolean value = button.getSelection();

                    // SWT doesn't automatically switch radio buttons when using the
                    // Widget#setSelection method, so we'll do it here manually.
                    if (!value && (button.getStyle() & SWT.RADIO) != 0) {
                        // we'll be selecting this radio button, so deselect all ther other ones
                        // in the parent group.
                        for (Control child : button.getParent().getChildren()) {
                            if (child instanceof Button &&
                                    child != button &&
                                    (child.getStyle() & SWT.RADIO) != 0) {
                                ((Button) child).setSelection(value);
                            }
                        }
                    }

                    button.setSelection(!value);

                    // SWT doesn't actually invoke the listeners when using Widget#setSelection
                    // so let's run the actual action.
                    button.notifyListeners(SWT.Selection, new Event());
                }

                updateMenuCheckmarks();
             }
        });

        mMenuActions.put(action, item);
    }

    // --- internal methods ---

    /**
     * Update checkboxes tied to menu items
     */
    private void updateMenuCheckmarks() {
        for (Entry<MenuAction, MenuItem> entry : mMenuActions.entrySet()) {
            MenuAction action = entry.getKey();
            MenuItem item = entry.getValue();

            if (action.getMenuStyle() == SWT.NONE) {
                continue;
            }

            boolean value = false;
            Button button = null;

            switch (action) {
            case TOGGLE_SHOW_OBSOLETE_PKG:
                button = mCheckFilterObsolete;
                break;
            case TOGGLE_SHOW_NEW_PKG:
                button = mCheckFilterNew;
                break;
            case TOGGLE_FORCE_HTTP:
                value = mSdkContext.getSettings().getForceHttp();
                break;
            case RELOAD:
            case FILTER_PACKAGES:
                // No checkmark to update
                break;
            }

            if (button != null && !button.isDisposed()) {
                value = button.getSelection();
            }

            if (!item.isDisposed()) {
                item.setSelection(value);
            }
        }
    }

    /**
     * Complete PackagesPage construction after all controls created. This is invoked from the constructor.
     */
    private void postCreate() {
        ColumnViewerToolTipSupport.enableFor(mTreeViewer, ToolTip.NO_RECREATE);
    }

    /**
     * Start package load. Both local and remote packages are (re-)loaded using a LoadPackagesTask object.
     */
	private void startLoadPackages() {
        // Packages will be loaded after onReady() is called
        if (factory == null)
        	return;
        // The package manager controls package download and installation
        PackageManager packageManager = mSdkContext.getPackageManager();
        // The "load packages" task adapts the Android Repository API to work with the package manager, package filter and package analyser
        LoadPackagesTask loadPackagesTask = new LoadPackagesTask(packageManager, mPackageAnalyser, mPackageFilter, factory) {

        	/**
        	 * Task to run after packages are installed
        	 */
			@Override
			public void onLoadComplete() {
                // If first time, automatically select all new and update packages.
                boolean hasCheckedItem = mPackageAnalyser.hasCheckedItem();
                Display.getDefault().syncExec(new Runnable(){
					@Override
					public void run() {
	                    if (!hasCheckedItem) {
	                        onSelectPackages(
	                                true,  //selectUpdates,
	                                true); //selectTop
	                        // set the initial expanded state
	                        expandInitial(mTreeViewer.getInput());
	                        updateButtonsState();
	                        updateMenuCheckmarks();
	                    }
	                    else
	                    	refreshViewerInput();
					}});
			}};
    	LoadPackagesRequest loadPackagesRequest = new LoadPackagesRequest(factory);
		//loadPackagesRequest.setOnLocalComplete(Collections.singletonList(onLocalComplete));
    	loadPackagesRequest.setOnSuccess(Collections.singletonList(loadPackagesTask));
    	loadPackagesRequest.setOnError(Collections.singletonList(loadPackagesTask));
    	packageManager.requestRepositoryPackages(loadPackagesRequest);
    }

	/**
	 * Refresh view
	 */
    private void refreshViewerInput() {
        if (!mGroupPackages.isDisposed()) {
            try {
                setViewerInput();
            } catch (Exception ignore) {}

            // set the initial expanded state
            expandInitial(mTreeViewer.getInput());

            updateButtonsState();
            updateMenuCheckmarks();
        }
    }
    
    /**
     * Invoked from {@link #refreshViewerInput()} to actually either set the
     * input of the tree viewer or refresh it depending on view status
     */
    private void setViewerInput() {
        ViewTreeRootNode rootNode = mPackageAnalyser.getRootNode();
        boolean isTreeDirty = mPackageAnalyser.isTreeDirty();
        if (isTreeDirty || mPackageFilter.isFilterOn()) {
            // set initial input
            mTreeViewer.setInput(rootNode);
            if (isTreeDirty)
            	mPackageAnalyser.setDirty(false);
        } else {
            // refresh existing, which preserves the expanded state, the selection
            // and the checked state.
            mTreeViewer.refresh();
        }
    }

    /**
     * Decide whether to keep an item in the current tree based on user-chosen filter options.
     * @param treeElement Tree item to filter
     * @return Flag set true if element should be retained
     */
    private boolean filterViewerItem(Object treeElement) {
    	boolean selectNew = mCheckFilterNew.getSelection();
        if (treeElement instanceof PkgCategory) {
            PkgCategory<?> cat = (PkgCategory<?>) treeElement;
            // Select all new items only if both "New" and "All" checkboxes ticked
            cat.setSelectAllPackages(selectNew && mCheckAll.getSelection());
            if (!cat.getItems().isEmpty()) {
                // A category is hidden if all of its content is hidden.
                // However empty categories are always visible.
                for (PkgItem item : cat.getItems()) {
                    if (filterViewerItem(item)) {
                        // We found at least one element that is visible.
                        return true;
                    }
                }
                cat.setChecked(false);
                return false;
            }
        }
        if (treeElement instanceof PkgItem) {
            PkgItem item = (PkgItem) treeElement;
            if (!mCheckFilterObsolete.getSelection()) {
                if (item.isObsolete()) {
                	item.setChecked(false);
                    return false;
                }
            }
            if (!selectNew) {
                if (item.getState() == PkgState.NEW ) {
                	item.setChecked(false);
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Performs the initial expansion of the tree. This expands categories that contain
     * at least one installed item and collapses the ones with nothing installed.
     *
     * TODO: change this to only change the expanded state on categories that have not
     * been touched by the user yet. Once we do that, call this every time the list is reloaded.
     */
    private void expandInitial(Object elem) {
        if (elem == null) {
            return;
        }
        if (mTreeViewer != null && !mTreeViewer.getTree().isDisposed()) {

            boolean enablePreviews =
                mSdkContext.getSettings().getEnablePreviews();

            mTreeViewer.setExpandedState(elem, true);
            nextCategory: for (Object pkg :
                    ((ITreeContentProvider) mTreeViewer.getContentProvider()).
                        getChildren(elem)) {
                if (pkg instanceof PkgCategory) {
                    PkgCategory<?> cat = (PkgCategory<?>) pkg;
                    // Always expand the Tools category (and the preview one, if enabled)
                    if ((cat.getKeyType() == CategoryKeyType.TOOLS) ||
                            (enablePreviews &&
                                    (cat.getKeyType() == CategoryKeyType.TOOLS_PREVIEW))) {
                        expandInitial(pkg);
                        continue nextCategory;
                    }
                    for (PkgItem item : cat.getItems()) {
                        if (item.getState() == PkgState.INSTALLED) {
                            expandInitial(pkg);
                            continue nextCategory;
                        }
                    }
                }
            }
        }
    }

    /**
     * Handle checking and unchecking of the tree items.
     *
     * When unchecking, all sub-tree items checkboxes are cleared too.
     * When checking a source, all of its packages are checked too.
     */
    private void onTreeCheckStateChanged(CheckStateChangedEvent event) {
        boolean checked = event.getChecked();
        Object elem = event.getElement();

        assert event.getSource() == mTreeViewer;

        // When selecting, we want to expand the super nodes.
        checkAndExpandItem(elem, checked, true/*fixChildren*/, true/*fixParent*/);
        updateButtonsState();
    }

    /**
     * Handle double-clicking on tree
     * @param event Event
     */
    private void onTreeDoubleClick(DoubleClickEvent event) {
        assert event.getSource() == mTreeViewer;
        ISelection sel = event.getSelection();
        if (sel.isEmpty() || !(sel instanceof ITreeSelection)) {
            return;
        }
        ITreeSelection tsel = (ITreeSelection) sel;
        Object elem = tsel.getFirstElement();
        if (elem == null) {
            return;
        }

        ITreeContentProvider provider =
            (ITreeContentProvider) mTreeViewer.getContentProvider();
        Object[] children = provider.getElements(elem);
        if (children == null) {
            return;
        }

        if (children.length > 0) {
            // If the element has children, expand/collapse it.
            if (mTreeViewer.getExpandedState(elem)) {
                mTreeViewer.collapseToLevel(elem, 1);
            } else {
                mTreeViewer.expandToLevel(elem, 1);
            }
        } else {
            // If the element is a terminal one, select/deselect it.
            checkAndExpandItem(
                    elem,
                    !mTreeViewer.getChecked(elem),
                    false /*fixChildren*/,
                    true /*fixParent*/);
            updateButtonsState();
        }
    }

    /**
     * Check and expand one item - recursive
     * @param elem Tree item
     * @param checked Flag set true if item is checked
     * @param fixChildren Flag set true if children to be processed
     * @param fixParent Flag set true if parent to be proecessed
     */
    private void checkAndExpandItem(
            Object elem,
            boolean checked,
            boolean fixChildren,
            boolean fixParent) {
        ITreeContentProvider provider =
            (ITreeContentProvider) mTreeViewer.getContentProvider();

        // fix the item itself
        INode node = (INode)elem;
        if (checked != mTreeViewer.getChecked(elem)) {
            mTreeViewer.setChecked(elem, checked);
        } 
        if (checked != node.isChecked()) {
            node.setChecked(checked);
        }
        if (!checked) {
            if (fixChildren) {
                // when de-selecting, we deselect all children too
                mTreeViewer.setSubtreeChecked(elem, checked);
                for (Object child : provider.getChildren(elem)) {
                    checkAndExpandItem(child, checked, fixChildren, false/*fixParent*/);
                }
            }

            // fix the parent when deselecting
            if (fixParent) {
                Object parent = provider.getParent(elem);
                if (parent != null && mTreeViewer.getChecked(parent)) {
                    mTreeViewer.setChecked(parent, false);
                }
            }
            return;
        }

        // When selecting, we also select sub-items (for a category)
        if (fixChildren) {
            if (elem instanceof PkgCategory) {
                Object[] children = provider.getChildren(elem);
                for (Object child : children) {
                    checkAndExpandItem(child, true, fixChildren, false/*fixParent*/);
                }
                // only fix the parent once the last sub-item is set
                if (children.length > 0) {
                    checkAndExpandItem(
                            children[0], true, false/*fixChildren*/, true/*fixParent*/);
                } else {
                    mTreeViewer.setChecked(elem, false);
                }
            }
        }
        // For case all packages in same state, set owning category to that state
        if (fixParent && checked && elem instanceof PkgItem) {
            Object parent = provider.getParent(elem);
            if (!mTreeViewer.getChecked(parent)) {
                Object[] children = provider.getChildren(parent);
                boolean allChecked = children.length > 0;
                for (Object e : children) {
                    if (!mTreeViewer.getChecked(e)) {
                        allChecked = false;
                        break;
                    }
                }
                boolean isCategoryChecked = mTreeViewer.getChecked(parent);
                if (allChecked != isCategoryChecked) {
                    mTreeViewer.setChecked(parent, allChecked);
                }
            }
        }
    }

    /**
     * Handle event "Select Packages" button pressed.
     */
    protected void selectPackages() {
    	// Allow user to select packages from a dialog
    	PackageTypesSelector pkgTypesSelector = new PackageTypesSelector(shell, mPackageFilter.getPackageTypes());
    	if (pkgTypesSelector.open()) {
    		// Refresh initial view
    		mPackageFilter.setPackageTypes(pkgTypesSelector.getPackageTypeSet());
    		onSelectPackages(true /* check updates */, false /* check top API */);
    	}
	}

    /**
     * Mark packages as checked according to selection criteria.
     * @param selectUpdates Flag set true if updates to be checked
     * @param selectTop Flag set true if top API platform to be checked if not installed
     */
    private void onSelectPackages(boolean selectUpdates, boolean selectTop) {
        // This will update the tree's "selected" state and then invoke syncViewerSelection()
        // which will in turn update tree.
        mPackageAnalyser.checkNewUpdateItems(
                selectUpdates,
                selectTop);
        mTreeViewer.setInput(mPackageAnalyser.getRootNode());
        syncViewerSelection();
        updateButtonsState();
    }

    /**
     * Deselect all checked packages.
     */
    private void onDeselectAll() {
        mPackageAnalyser.uncheckAllItems();
        syncViewerSelection();
        updateButtonsState();
    }

    /**
     * Synchronize the 'checked' state of packages in the tree with their internal checked state.
     */
    private void syncViewerSelection() {
        ITreeContentProvider provider = (ITreeContentProvider) mTreeViewer.getContentProvider();

        Object input = mTreeViewer.getInput();
        if (input != null) {
            for (Object cat : provider.getElements(input)) {
                Object[] children = provider.getElements(cat);
                boolean allChecked = children.length > 0;
                for (Object child : children) {
                    if (child instanceof PkgItem) {
                        PkgItem item = (PkgItem) child;
                        boolean checked = item.isChecked();
                        allChecked &= checked;

                        if (checked != mTreeViewer.getChecked(item)) {
                            if (checked) {
                                if (!mTreeViewer.getExpandedState(cat)) {
                                    mTreeViewer.setExpandedState(cat, true);
                                }
                            }
                            checkAndExpandItem(
                                    item,
                                    checked,
                                    true/*fixChildren*/,
                                    false/*fixParent*/);
                        }
                    }
                }
                boolean isCategoryChecked = mTreeViewer.getChecked(cat);
                if (allChecked != isCategoryChecked) {
                    mTreeViewer.setChecked(cat, allChecked);
                }
            }
        }
    }

    /**
     * Indicate an install/delete operation is pending.
     * This disables the install/delete buttons.
     * Use {@link #endOperationPending()} to revert, typically in a {@code try..finally} block.
     */
    private void beginOperationPending() {
        mOperationPending = true;
        updateButtonsState();
		Display.getDefault().syncExec(new Runnable(){
			
			@Override
			public void run() {
				// Change OK to Stop
				// Note startLoadPackages() will update button staate on completion
                mButtonCancel.setText("Stop"); 
			}});
    }

    /**
     * Cancel operation pending state
     */
    private void endOperationPending() {
        mOperationPending = false;
    }

    /**
     * Updates the Install and Delete Package buttons.
     */
    private void updateButtonsState() {
    	// Do as job as searching for packages is not suited to Display thread
        Job job = new Job("Update install/delete buttons") {
            @Override
            protected IStatus run(IProgressMonitor m) {
            	try {
			        if (!mButtonInstall.isDisposed()) {
			            int packageCount = mPackageAnalyser.getPackagesToInstall(mPackageFilter).size();
			            Display.getDefault().syncExec(new Runnable() {
	
							@Override
							public void run() {
					            mButtonInstall.setEnabled((packageCount > 0) && !mOperationPending);
					            mButtonInstall.setData(packageCount);
					            mButtonInstall.setText(
					            		packageCount == 0 ? "Install packages..." :          // disabled button case
					            			packageCount == 1 ? "Install 1 package..." :
					                            String.format("Install %d packages...", packageCount));
			                }
			            });
			        }
			        if (!mButtonDelete.isDisposed()) {
			            // We can only delete local archives
			            List<PkgItem> packageItems = mPackageAnalyser.getPackagesToDelete(mPackageFilter);
			            int packageCount = packageItems.size();
			            Display.getDefault().syncExec(new Runnable(){
	
							@Override
							public void run() {
					            mButtonDelete.setEnabled((packageCount > 0) && !mOperationPending);
					            mButtonDelete.setData(packageCount);
					            mButtonDelete.setText(
					            		packageCount == 0 ? "Delete packages..." :           // disabled button case
					            			packageCount == 1 ? "Delete 1 package..." :
					                            String.format("Delete %d packages...", packageCount));
							}});
			        }
			        return Status.OK_STATUS;
            	} catch (Exception e) {
            		mSdkContext.getSdkLog().error(e,"Button update error");
            		return Status.CANCEL_STATUS;
            	}
            }
        };
        job.setPriority(Job.INTERACTIVE);
        job.schedule();
    }

    /**
     * Handle event "Install Package" button is selected.
     * Collects the packages to be installed and shows the installation window.
     */
    private void onButtonInstall() {
        beginOperationPending();
    	// Do as job as searching for packages is not suited to Display thread
        Job job = new Job("Install packages") {
            @Override
            protected IStatus run(IProgressMonitor m) {
            	try {
			    	List<PkgItem> requiredPackages = mPackageAnalyser.getPackagesToInstall(mPackageFilter);
			    	PackageInstaller packageInstaller = new PackageInstaller(requiredPackages, factory);
			    	if (packagesInstalled != EMPTY_PACKAGE_LIST)
			    		packagesInstalled.clear();
			    	ILogUiProvider sdkProgressControl = factory.getProgressControl();
			    	PackageInstallListener installListener = new PackageInstallListener(){
			
						@Override
						public void onPackageInstalled(RemotePackage packageInstalled) {
					    	if (packagesInstalled == EMPTY_PACKAGE_LIST)
					    		packagesInstalled = new ArrayList<>();
					    	packagesInstalled.add(packageInstalled);
					    	sdkProgressControl.setDescription(String.format("Installed package %s", packageInstalled.getDisplayName()));
						}
			
						@Override
						public void onInstallComplete(int packageCount) {
			            	boolean success = packageCount > 0;
							if (!success)
								sdkProgressControl.setDescription(NONE_INSTALLED);
							else {
					        	sdkProgressControl.setDescription(String.format("Done. %1$d %2$s installed.",
					        			packageCount,
					        			packageCount == 1 ? "package" : "packages"));
							}
				            endOperationPending();
							if (success) 
								// The local package list has changed, make sure to refresh it
								startLoadPackages();
							onPackageInstall(success);
						}};
			    	packageInstaller.installPackages(shell, mSdkContext, installListener);
			    	return Status.OK_STATUS;
            	} catch (Exception e) {
            		mSdkContext.getSdkLog().error(e, "Error while installing packages");
		            endOperationPending();
					onPackageInstall(false);
            		return Status.CANCEL_STATUS;
            	}
            }
        };
        job.setPriority(Job.INTERACTIVE);
        job.schedule();
    }

    /**
     * Handle event "Delete Package" button is selected.
     * Collects the packages to be deleted, prompt the user for confirmation
     * and actually performs the deletion.
     */
    private void onButtonDelete() {
    	PackageDeleteTask packageDeleteTask = new PackageDeleteTask(shell, mSdkContext, mPackageAnalyser, mPackageFilter);
        beginOperationPending();
        Runnable onCompletion = new Runnable(){

			@Override
			public void run() {
				boolean success = false;
				try {
	                endOperationPending();
	                // The local package list has changed, make sure to refresh it
	            	startLoadPackages();
	            	success = true;
				} catch (Exception e) {
					mSdkContext.getSdkLog().error(e, "Error after package delete operation");
		            endOperationPending();
				} finally {
					onPackageInstall(success);
				}
			}};
        factory.start("Delete Package", packageDeleteTask, onCompletion);
    }

    /**
     * Handle event package install/delete completed
     * @param success Flag set true if update completed successfully
     */
    private void onPackageInstall(boolean success) {
		Display.getDefault().syncExec(new Runnable(){
			
			@Override
			public void run() {
				// Change Cancel to OK
				// Note startLoadPackages() will update button staate on completion
				if (!mButtonCancel.isDisposed())
					mButtonCancel.setText("OK");  
				// Restore Install and Delete button states
				if (!mButtonInstall.isDisposed()) {
					int installCount = ((Integer)mButtonInstall.getData()).intValue();
					if (installCount > 0)
						mButtonInstall.setEnabled(true);
				}
				if (!mButtonDelete.isDisposed()) {
					int deleteCount = ((Integer)mButtonDelete.getData()).intValue();
					if (deleteCount > 0)
						mButtonDelete.setEnabled(true);
				}
			}});
    }


     // --- End of hiding from SWT Designer ---
    //$hide<<$
}
