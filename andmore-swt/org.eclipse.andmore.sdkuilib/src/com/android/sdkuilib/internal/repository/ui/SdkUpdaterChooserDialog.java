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

package com.android.sdkuilib.internal.repository.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.eclipse.andmore.sdktool.SdkContext;
import org.eclipse.andmore.sdktool.Utilities;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.repository.api.License;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepositorySource;
import com.android.repository.api.UpdatablePackage;
import com.android.repository.util.InstallerUtil;
import com.android.sdklib.AndroidVersion;
import com.android.sdkuilib.internal.repository.PackageInfo;
import com.android.sdkuilib.internal.repository.content.PackageAnalyser;
import org.eclipse.andmore.base.resources.ImageFactory;
import com.android.sdkuilib.ui.GridDialog;


/**
 * SdkUpdaterChooserDialog presents the user with packages to be
 * installed and license information to act on by accepting or
 * rejecting licenses.
 */
public final class SdkUpdaterChooserDialog extends GridDialog {

    /** Last dialog size for this session. */
    private static Point sLastSize;
    /** Precomputed flag indicating whether the "accept license" radio is checked. */
    private boolean mAcceptSameAllLicense;
    private boolean mInternalLicenseRadioUpdate;

    // UI fields
    private SashForm mSashForm;
    private Composite mPackageRootComposite;
    private TreeViewer mTreeViewPackage;
    private Tree mTreePackage;
    private TreeColumn mTreeColum;
    private StyledText mPackageText;
    private Button mLicenseRadioAccept;
    private Button mLicenseRadioReject;
    private Button mLicenseRadioAcceptLicense;
    private Group mPackageTextGroup;
    private Group mTableGroup;
    private Label mErrorLabel;
    private Set<RemotePackage> unacceptedLicenses = new HashSet<>();

    /** SDK context */
    private final SdkContext mSdkContext;
    /** Set of all packages to be installed with dependency information. */
    private final Set<PackageInfo> mPackages = new TreeSet<>();

    /**
     * Create the dialog.
     *
     * @param parentShell The shell of the parent control which invoked this dialog
     * @param SdkContext SDK context
     * @param updates The update information as a collection of UpdatablePackage objects
     * @param packages All packages to be installed, including those for updates
     */
    public SdkUpdaterChooserDialog(Shell parentShell,
            SdkContext SdkContext,
            Collection<UpdatablePackage> updates,
            List<RemotePackage> newPackages) {
        super(parentShell, 3, false/*makeColumnsEqual*/);
        mSdkContext = SdkContext;
        // Process updates first so it is possible to differentiate between new packages and replacements
        init(updates);
        init(newPackages);
    }

	@Override
    protected boolean isResizable() {
        return true;
    }

    /**
     * Returns the results, i.e. the list of selected new packages to install.
     * <p/>
     * An empty list is returned if cancel was chosen.
     */
    public ArrayList<RemotePackage> getResult() {
        ArrayList<RemotePackage> packageList = new ArrayList<RemotePackage>();

        if (getReturnCode() == Window.OK) {
            for (PackageInfo packageInfo : mPackages) {
                if (packageInfo.isAccepted()) {
                	packageList.add(packageInfo.getNewPackage());
                }
            }
        }
        return packageList;
    }

    /**
     * Create the main content of the dialog.
     * See also {@link #createButtonBar(Composite)} below.
     */
    @Override
    public void createDialogContent(Composite parent) {
        // Sash form
        mSashForm = new SashForm(parent, SWT.NONE);
        mSashForm.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 3, 1));

        // Left part of Sash Form

        mTableGroup = new Group(mSashForm, SWT.NONE);
        mTableGroup.setText("Packages");
        mTableGroup.setLayout(new GridLayout(1, false/*makeColumnsEqual*/));

        mTreeViewPackage = new TreeViewer(mTableGroup, SWT.BORDER | SWT.V_SCROLL | SWT.SINGLE);
        mTreePackage = mTreeViewPackage.getTree();
        mTreePackage.setHeaderVisible(false);
        mTreePackage.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));

        mTreePackage.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent event) {
                onPackageSelected();  //$hide$
            }
            @Override
            public void widgetDefaultSelected(SelectionEvent e) {
                onPackageDoubleClick();
            }
        });

        mTreeColum = new TreeColumn(mTreePackage, SWT.NONE);
        mTreeColum.setWidth(100);
        mTreeColum.setText("Packages");

        // Right part of Sash form

        mPackageRootComposite = new Composite(mSashForm, SWT.NONE);
        mPackageRootComposite.setLayout(new GridLayout(4, false/*makeColumnsEqual*/));
        mPackageRootComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        mPackageTextGroup = new Group(mPackageRootComposite, SWT.NONE);
        mPackageTextGroup.setText("Package Description && License");
        mPackageTextGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 4, 1));
        mPackageTextGroup.setLayout(new GridLayout(1, false/*makeColumnsEqual*/));

        mPackageText = new StyledText(mPackageTextGroup,
                        SWT.MULTI | SWT.READ_ONLY | SWT.WRAP | SWT.V_SCROLL);
        mPackageText.setBackground(
                getParentShell().getDisplay().getSystemColor(SWT.COLOR_WIDGET_BACKGROUND));
        mPackageText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));

        mLicenseRadioAccept = new Button(mPackageRootComposite, SWT.RADIO);
        mLicenseRadioAccept.setText("Accept");
        mLicenseRadioAccept.setToolTipText("Accept this package.");
        mLicenseRadioAccept.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                onLicenseRadioSelected();
            }
        });

        mLicenseRadioReject = new Button(mPackageRootComposite, SWT.RADIO);
        mLicenseRadioReject.setText("Reject");
        mLicenseRadioReject.setToolTipText("Reject this package.");
        mLicenseRadioReject.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                onLicenseRadioSelected();
            }
        });

        Link link = new Link(mPackageRootComposite, SWT.NONE);
        link.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false, 1, 1));
        final String printAction = "Print"; // extracted for NLS, to compare with below.
        link.setText(String.format("<a>Copy to clipboard</a> | <a>%1$s</a>", printAction));
        link.setToolTipText("Copies all text and license to clipboard | Print using system defaults.");
        link.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                super.widgetSelected(e);
                if (printAction.equals(e.text)) {
                    mPackageText.print();
                } else {
                    Point p = mPackageText.getSelection();
                    mPackageText.selectAll();
                    mPackageText.copy();
                    mPackageText.setSelection(p);
                }
            }
        });


        mLicenseRadioAcceptLicense = new Button(mPackageRootComposite, SWT.RADIO);
        mLicenseRadioAcceptLicense.setText("Accept License");
        mLicenseRadioAcceptLicense.setToolTipText("Accept all packages that use the same license.");
        mLicenseRadioAcceptLicense.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                onLicenseRadioSelected();
            }
        });

        mSashForm.setWeights(new int[] {200, 300});
    }

    /**
     * Creates and returns the contents of this dialog's button bar.
     * <p/>
     * This reimplements most of the code from the base class with a few exceptions:
     * <ul>
     * <li>Enforces 3 columns.
     * <li>Inserts a full-width error label.
     * <li>Inserts a help label on the left of the first button.
     * <li>Renames the OK button into "Install"
     * </ul>
     */
    @Override
    protected Control createButtonBar(Composite parent) {
        Composite composite = new Composite(parent, SWT.NONE);
        GridLayout layout = new GridLayout();
        layout.numColumns = 0; // this is incremented by createButton
        layout.makeColumnsEqualWidth = false;
        layout.marginWidth = convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_MARGIN);
        layout.marginHeight = convertVerticalDLUsToPixels(IDialogConstants.VERTICAL_MARGIN);
        layout.horizontalSpacing = convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_SPACING);
        layout.verticalSpacing = convertVerticalDLUsToPixels(IDialogConstants.VERTICAL_SPACING);
        composite.setLayout(layout);
        GridData data = new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1);
        composite.setLayoutData(data);
        composite.setFont(parent.getFont());

        // Error message area
        mErrorLabel = new Label(composite, SWT.NONE);
        mErrorLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));

        // Label at the left of the install/cancel buttons
        Label label = new Label(composite, SWT.NONE);
        label.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        label.setText("[*] Something depends on this package");
        label.setEnabled(false);
        layout.numColumns++;

        // Add the ok/cancel to the button bar.
        createButtonsForButtonBar(composite);

        // the ok button should be an "install" button
        Button button = getButton(IDialogConstants.OK_ID);
        button.setText("Install");

        return composite;
    }

    // -- End of UI, Start of internal logic ----------
    // Hide everything down-below from SWT designer
    //$hide>>$

    @Override
    public void create() {
        super.create();
        // set window title
        getShell().setText("Choose Packages to Install");
        setWindowImage();
        // Automatically accept those with no license
        for (PackageInfo packageInfo : mPackages) {
            RemotePackage remote = packageInfo.getNewPackage();
            License license = remote.getLicense();
            if (license == null)
            	packageInfo.setAccepted(true);
            else {
            	boolean hasLicense = license.checkAccepted(mSdkContext.getLocation(), mSdkContext.getFileOp());
            	if (hasLicense) 
                	packageInfo.setAccepted(true);
            	else
            		unacceptedLicenses.add(remote);
            }
        }
        // Fill the list with the replacement packages
        mTreeViewPackage.setLabelProvider(new NewPackagesLabelProvider());
        mTreeViewPackage.setContentProvider(new NewPackagesContentProvider());
        mTreeViewPackage.setInput(createTreeInput(mPackages));
        mTreeViewPackage.expandAll();
        adjustColumnsWidth();
        // select first item is superfluous
        //onPackageSelected(contents.get(0));
    }

    /**
     * Creates the icon of the window shell.
     */
    private void setWindowImage() {
        String imageName = "android_icon_16.png"; //$NON-NLS-1$
        if (SdkConstants.currentPlatform() == SdkConstants.PLATFORM_DARWIN) {
            imageName = "android_icon_128.png";   //$NON-NLS-1$
        }

        if (mSdkContext != null) {
            ImageFactory imgFactory = mSdkContext.getSdkHelper().getImageFactory();
            if (imgFactory != null) {
                getShell().setImage(imgFactory.getImageByName(imageName));
            }
        }
    }

    /**
     * Adds a listener to adjust the columns width when the parent is resized.
     * <p/>
     * If we need something more fancy, we might want to use this:
     * http://dev.eclipse.org/viewcvs/index.cgi/org.eclipse.swt.snippets/src/org/eclipse/swt/snippets/Snippet77.java?view=co
     */
    private void adjustColumnsWidth() {
        // Add a listener to resize the column to the full width of the table
        ControlAdapter resizer = new ControlAdapter() {
            @Override
            public void controlResized(ControlEvent e) {
                Rectangle r = mTreePackage.getClientArea();
                mTreeColum.setWidth(r.width);
            }
        };
        mTreePackage.addControlListener(resizer);
        resizer.controlResized(null);
    }

    /**
     * Captures the window size before closing this.
     * @see #getInitialSize()
     */
    @Override
    public boolean close() {
        sLastSize = getShell().getSize();
        return super.close();
    }

    /**
     * Tries to reuse the last window size during this session.
     * <p/>
     * Note: the alternative would be to implement {@link #getDialogBoundsSettings()}
     * since the default {@link #getDialogBoundsStrategy()} is to persist both location
     * and size.
     */
    @Override
    protected Point getInitialSize() {
        if (sLastSize != null) {
            return sLastSize;
        } else {
            // Arbitrary values that look good on my screen and fit on 800x600
            return new Point(740, 470);
        }
    }

    /**
     * Callback invoked when a package item is selected in the list.
     */
    private void onPackageSelected() {
        Object item = getSelectedItem();

        // Update mAcceptSameAllLicense : true if all items under the same license are accepted.
        PackageInfo packageInfo = null;
        List<PackageInfo> list = null;
        if (item instanceof PackageInfo) {
        	packageInfo = (PackageInfo) item;

            Object p =
                ((NewPackagesContentProvider) mTreeViewPackage.getContentProvider()).getParent(packageInfo);
            if (p instanceof LicenseEntry) {
                list = ((LicenseEntry) p).getPackageInfoList();
            }
            displayPackageInformation(packageInfo);

        } else if (item instanceof LicenseEntry) {
            LicenseEntry entry = (LicenseEntry) item;
            list = entry.getPackageInfoList();
            displayLicenseInformation(entry);

        } else {
            // Fallback, should not happen.
            displayEmptyInformation();
        }

        // the "Accept License" radio is selected if there's a license with >= 0 items
        // and they are all in "accepted" state.
        mAcceptSameAllLicense = list != null && list.size() > 0;
        if (mAcceptSameAllLicense) {
            assert list != null;
            License lic0 = list.get(0).getNewPackage().getLicense();
            for (PackageInfo packageInfo2 : list) {
                License lic2 = packageInfo2.getNewPackage().getLicense();
                if (packageInfo2.isAccepted() && (lic0 == lic2 || lic0.equals(lic2))) {
                    continue;
                } else {
                    mAcceptSameAllLicense = false;
                    break;
                }
            }
        }

        displayMissingDependency(packageInfo);
        updateLicenceRadios(packageInfo);
    }

    /** Returns the currently selected tree item.
     * @return Either {@link PackageInfo} or {@link LicenseEntry} or null. */
    private Object getSelectedItem() {
        ISelection sel = mTreeViewPackage.getSelection();
        if (sel instanceof IStructuredSelection) {
            Object elem = ((IStructuredSelection) sel).getFirstElement();
            if (elem instanceof PackageInfo || elem instanceof LicenseEntry) {
                return elem;
            }
        }
        return null;
    }

    /**
     * Information displayed when nothing valid is selected.
     */
    private void displayEmptyInformation() {
        mPackageText.setText("Please select a package or a license.");
    }

    /**
     * Updates the package description and license text depending on the selected package.
     * <p/>
     * Note that right now there is no logic to support more than one level of dependencies
     * (e.g. A <- B <- C and A is disabled so C should be disabled; currently C's state depends
     * solely on B's state). We currently don't need this. It would be straightforward to add
     * if we had a need for it, though. This would require changes to {@link PackageInfo} and
     * {@link SdkUpdaterLogic}.
     */
    private void displayPackageInformation(PackageInfo packageInfo) {
        mPackageText.setText("");                   //$NON-NLS-1$
        addSectionTitle("Package Description\n");
        RemotePackage remotePackage = packageInfo.getNewPackage();
        // Add revision if not in path
        addText(getName(remotePackage), "\n\n"); //$NON-NLS-1$
        // Collect remaining details in job as it involves processing package dependencies
		StringBuilder description = new StringBuilder();
		StringBuilder dependencies = new StringBuilder();
        Job job = new Job("Collect package information"){

			@Override
			protected IStatus run(IProgressMonitor arg0) {
				try {
			        LocalPackage localPackage = packageInfo.getReplaced();
			        if (localPackage != null) {
			            AndroidVersion vOld = PackageAnalyser.getAndroidVersion(localPackage);
			            AndroidVersion vNew = PackageAnalyser.getAndroidVersion(remotePackage);
			            boolean showRev = (vOld != null) && (vNew != null);

			            if (showRev && !vOld.equals(vNew)) {
			                // Versions are different, so indicate more than just the revision.
			            	description.append(
			                    String.format("This update will replace API %1$s revision %2$s with API %3$s revision %4$s.\n\n",
			                        vOld.getApiString(), localPackage.getVersion(),
			                        vNew.getApiString(), remotePackage.getVersion()));
			                showRev = false;
			            }
			            if (showRev) {
			            	description.append(
			                    String.format("This update will replace revision %1$s with revision %2$s.\n\n",
			                		localPackage.getVersion(),
			                		remotePackage.getVersion()));
			            }
			        }
			        List<RemotePackage> required = InstallerUtil.computeRequiredPackages(
			                Collections.singletonList(remotePackage), mSdkContext.getPackages(),
			                mSdkContext.getProgressIndicator());
			        if ((required != null && required.size() > 0)) {
			        	// Remove principal and duplicates
			        	Iterator<RemotePackage> iterator = required.iterator();
			            Set<RemotePackage> existenceSet = new HashSet<>();
			            existenceSet.add(remotePackage);
			            List<RemotePackage> filteredRequired = new ArrayList<>();
			            while (iterator.hasNext()) {
			            	RemotePackage requiredPackage = iterator.next();
			            	if (!existenceSet.contains(requiredPackage)) {
			            		{
			            			existenceSet.add(requiredPackage);
			            			filteredRequired.add(requiredPackage);
			            		}
			            	}
			            }
			            // Remove references now existenceSet no longer needed
			            existenceSet.clear();
			        	if ((filteredRequired.size() > 0)) {
				            dependencies.append("Installing this package also requires installing:");
				            for (RemotePackage dependency : filteredRequired) {
				            	dependencies.append(String.format("\n- %1$s", SdkUpdaterChooserDialog.this.getName(dependency)));
				            }
				            dependencies.append("\n\n");
				            /*
				            if (ai.isDependencyFor()) {
				                addText("This package is a dependency for:");
				                for (PackageInfo ai2 : ai.getDependenciesFor()) {
				                    addText(String.format("\n- %1$s",
				                            ai2.getShortDescription()));
				                }
				                addText("\n\n");
				            }
				            */            
			        	}
			        }
				} catch (Exception e) {
					mSdkContext.getSdkLog().error(e, "Error while collecting package information");
				}
				return Status.OK_STATUS;
			}};
		job.setPriority(Job.INTERACTIVE);
		job.addJobChangeListener(new JobChangeAdapter(){
            @Override
            public void done(IJobChangeEvent event) {
            	Display.getDefault().syncExec(new Runnable(){

					@Override
					public void run() {
		            	addText(description.toString());
		            	if (dependencies.length() > 0) {
			                addSectionTitle("Dependencies\n");
			                addText(dependencies.toString());
		            	}
		                addSectionTitle("Archive Size\n");
		          	    long fileSize = remotePackage.getArchive().getComplete().getSize();
		                addText(String.format("Size: %1$s", Utilities.formatFileSize(fileSize)), "\n\n");

		                License license = remotePackage.getLicense();
		                if (license != null) {
		                	addSectionTitle(String.format("License %s:%n", license.getId()));
		                    addText(license.getValue().trim(), "\n\n");
		                }

		                addSectionTitle("Site\n");
		                RepositorySource source = remotePackage.getSource();
		                if (source != null) {
		                    addText(source.getDisplayName());
		                }
					}});
            }
        });
		job.schedule();
    }

    /**
     * Updates the description for a license entry.
     */
    private void displayLicenseInformation(LicenseEntry entry) {
        List<PackageInfo> packageInfoList = entry == null ? null : entry.getPackageInfoList();
        if (packageInfoList == null) {
            // There should not be a license entry without any package in it.
            displayEmptyInformation();
            return;
        }
        assert entry != null;

        mPackageText.setText("");                   //$NON-NLS-1$

        License license = null;
        addSectionTitle("Packages\n");
        for (PackageInfo packageInfo : entry.getPackageInfoList()) {
            RemotePackage remote = packageInfo.getNewPackage();
            if (license == null)
            	license = remote.getLicense();
            addText("- ", remote.getDisplayName(), "\n"); //$NON-NLS-1$ //$NON-NLS-2$
        }

        if (license != null) {
        	addSectionTitle(String.format("License %s:%n", license.getId()));
            addText(license.getValue().trim(), "\n\n");                                   //$NON-NLS-1$
        }
    }

    private void addText(String...string) {
        for (String s : string) {
            mPackageText.append(s);
        }
    }

    private void addSectionTitle(String string) {
        String s = mPackageText.getText();
        int start = (s == null ? 0 : s.length());
        mPackageText.append(string);

        StyleRange sr = new StyleRange();
        sr.start = start;
        sr.length = string.length();
        sr.fontStyle = SWT.BOLD;
        sr.underline = true;
        mPackageText.setStyleRange(sr);
    }

    private void updateLicenceRadios(PackageInfo ai) {
        if (mInternalLicenseRadioUpdate) {
            return;
        }
        mInternalLicenseRadioUpdate = true;

        boolean oneAccepted = false;

        mLicenseRadioAcceptLicense.setSelection(mAcceptSameAllLicense);
        oneAccepted = ai != null && ai.isAccepted();
        mLicenseRadioAccept.setEnabled(ai != null);
        mLicenseRadioReject.setEnabled(ai != null);
        mLicenseRadioAccept.setSelection(oneAccepted);
        mLicenseRadioReject.setSelection(ai != null && ai.isRejected());

        // The install button is enabled if there's at least one package accepted.
        // If the current one isn't, look for another one.
        boolean missing = mErrorLabel.getText() != null && mErrorLabel.getText().length() > 0;
        if (!missing && !oneAccepted) {
            for(PackageInfo ai2 : mPackages) {
                if (ai2.isAccepted()) {
                    oneAccepted = true;
                    break;
                }
            }
        }
        getButton(IDialogConstants.OK_ID).setEnabled(!missing && oneAccepted);
        mInternalLicenseRadioUpdate = false;
    }

    /**
     * Callback invoked when one of the radio license buttons is selected.
     *
     * - accept/refuse: toggle, update item checkbox
     * - accept all: set accept-all, check all items with the *same* license
     */
    private void onLicenseRadioSelected() {
        if (mInternalLicenseRadioUpdate) {
            return;
        }
        mInternalLicenseRadioUpdate = true;

        Object item = getSelectedItem();
        PackageInfo packageInfo = (item instanceof PackageInfo) ? (PackageInfo) item : null;
        boolean needUpdate = true;

        if (!mAcceptSameAllLicense && mLicenseRadioAcceptLicense.getSelection()) {
            // Accept all has been switched on. Mark all packages as accepted

            List<PackageInfo> list = null;
            if (item instanceof LicenseEntry) {
                list = ((LicenseEntry) item).getPackageInfoList();
            } else if (packageInfo != null) {
                Object p = ((NewPackagesContentProvider) mTreeViewPackage.getContentProvider())
                                                                         .getParent(packageInfo);
                if (p instanceof LicenseEntry) {
                    list = ((LicenseEntry) p).getPackageInfoList();
                }
            }

            if (list != null && list.size() > 0) {
                mAcceptSameAllLicense = true;
                for(PackageInfo packageInfo2 : list) {
                    packageInfo2.setAccepted(true);
                    packageInfo2.setRejected(false);
                    unacceptedLicenses.remove(packageInfo2.getNewPackage());
                }
            }

        } else if (packageInfo != null && mLicenseRadioAccept.getSelection()) {
            // Accept only this one
            mAcceptSameAllLicense = false;
            packageInfo.setAccepted(true);
            packageInfo.setRejected(false);
            unacceptedLicenses.remove(packageInfo.getNewPackage());
        } else if (packageInfo != null && mLicenseRadioReject.getSelection()) {
            // Reject only this one
            mAcceptSameAllLicense = false;
            packageInfo.setAccepted(false);
            packageInfo.setRejected(true);
            unacceptedLicenses.add(packageInfo.getNewPackage());
        } else {
            needUpdate = false;
        }

        mInternalLicenseRadioUpdate = false;

        if (needUpdate) {
            if (mAcceptSameAllLicense) {
                mTreeViewPackage.refresh();
            } else {
               mTreeViewPackage.refresh(packageInfo);
               mTreeViewPackage.refresh(
                       ((NewPackagesContentProvider) mTreeViewPackage.getContentProvider()).
                       getParent(packageInfo));
            }
            displayMissingDependency(packageInfo);
            updateLicenceRadios(packageInfo);
        }
    }

    /**
     * Callback invoked when a package item is double-clicked in the list.
     */
    private void onPackageDoubleClick() {
        Object item = getSelectedItem();

        if (item instanceof PackageInfo) {
            PackageInfo packageInfo = (PackageInfo) item;
            boolean wasAccepted = packageInfo.isAccepted();
            packageInfo.setAccepted(!wasAccepted);
            packageInfo.setRejected(wasAccepted);

            // update state
            mAcceptSameAllLicense = false;
            mTreeViewPackage.refresh(packageInfo);
            // refresh parent since its icon might have changed.
            mTreeViewPackage.refresh(
                    ((NewPackagesContentProvider) mTreeViewPackage.getContentProvider()).
                    getParent(packageInfo));

            displayMissingDependency(packageInfo);
            updateLicenceRadios(packageInfo);

        } else if (item instanceof LicenseEntry) {
            mTreeViewPackage.setExpandedState(item, !mTreeViewPackage.getExpandedState(item));
        }
    }

    /**
     * Computes and displays missing dependencies.
     *
     * If there's a selected package, check the dependency for that one.
     * Otherwise display the first missing dependency of any other package.
     */
    private void displayMissingDependency(PackageInfo packageInfo) {
        String error = null;

        try {
            if (packageInfo != null) {
                if (packageInfo.isAccepted()) {
                    // Case where this package is accepted but blocked by another non-accepted one
                    List<RemotePackage> required = InstallerUtil.computeRequiredPackages(
                            Collections.singletonList(packageInfo.getNewPackage()), mSdkContext.getPackages(),
                            mSdkContext.getProgressIndicator());
                    if ((required != null && required.size() > 0)) {
                        for (RemotePackage dependency : required) {
                            if (unacceptedLicenses.contains(dependency)) {
                                error = String.format("This package depends on '%1$s'.",
                                		dependency.getDisplayName());
                                return;
                            }
                        }
                    }
                } else {
                	/*
                    // Case where this package blocks another one when not accepted
                    for (PackageInfo adep : packageInfo.getDependenciesFor()) {
                        // It only matters if the blocked one is accepted
                        if (adep.isAccepted()) {
                            error = String.format("Package '%1$s' depends on this one.",
                                    adep.getShortDescription());
                            return;
                        }
                    }
                    */
                }
            }
/*
            // If there is no missing dependency on the current selection,
            // just find the first missing dependency of any other package.
            for (PackageInfo packageInfo2 : mArchives) {
                if (packageInfo2 == packageInfo) {
                    // We already processed that one above.
                    continue;
                }
                if (packageInfo2.isAccepted()) {
                    // The user requested to install this package.
                    // Check if all its dependencies are met.
                    PackageInfo[] adeps = packageInfo2.getDependsOn();
                    if (adeps != null) {
                        for (PackageInfo adep : adeps) {
                            if (!adep.isAccepted()) {
                                error = String.format("Package '%1$s' depends on '%2$s'",
                                        packageInfo2.getShortDescription(),
                                        adep.getShortDescription());
                                return;
                            }
                        }
                    }
                } else {
                    // The user did not request to install this package.
                    // Check whether this package blocks another one when not accepted.
                    for (PackageInfo adep : packageInfo2.getDependenciesFor()) {
                        // It only matters if the blocked one is accepted
                        // or if it's a local archive that is already installed (these
                        // are marked as implicitly accepted, so it's the same test.)
                        if (adep.isAccepted()) {
                            error = String.format("Package '%1$s' depends on '%2$s'",
                                    adep.getShortDescription(),
                                    packageInfo2.getShortDescription());
                            return;
                        }
                    }
                }
            }
*/            
        } finally {
            mErrorLabel.setText(error == null ? "" : error);        //$NON-NLS-1$
        }
    }


    /**
     * Provides the labels for the tree view.
     * Root branches are {@link LicenseEntry} elements.
     * Leave nodes are {@link PackageInfo} which all have the same license.
     */
    private class NewPackagesLabelProvider extends LabelProvider {
        @Override
        public Image getImage(Object element) {
            if (element instanceof PackageInfo) {
                // Package icon: accepted (green), rejected (red), not set yet (question mark)
                PackageInfo packageInfo = (PackageInfo) element;

                ImageFactory imgFactory = mSdkContext.getSdkHelper().getImageFactory();
                if (imgFactory != null) {
                    if (packageInfo.isAccepted()) {
                        return imgFactory.getImageByName("accept_icon16.png");
                    } else if (packageInfo.isRejected()) {
                        return imgFactory.getImageByName("reject_icon16.png");
                    }
                    return imgFactory.getImageByName("unknown_icon16.png");
                }
                return super.getImage(element);

            } else if (element instanceof LicenseEntry) {
                // License icon: green if all below are accepted, red if all rejected, otherwise
                // no icon.
                ImageFactory imgFactory = mSdkContext.getSdkHelper().getImageFactory();
                if (imgFactory != null) {
                    boolean allAccepted = true;
                    boolean allRejected = true;
                    for (PackageInfo packageInfo : ((LicenseEntry) element).getPackageInfoList()) {
                        allAccepted = allAccepted && packageInfo.isAccepted();
                        allRejected = allRejected && packageInfo.isRejected();
                    }
                    if (allAccepted && !allRejected) {
                        return imgFactory.getImageByName("accept_icon16.png");
                    } else if (!allAccepted && allRejected) {
                        return imgFactory.getImageByName("reject_icon16.png");
                    }
                }
            }
            return null;
        }

        @Override
        public String getText(Object element) {
            if (element instanceof LicenseEntry) {
                return ((LicenseEntry) element).getLicenseRef();

            } else if (element instanceof PackageInfo) {
                PackageInfo packageInfo = (PackageInfo) element;

                String desc = packageInfo.getNewPackage().getDisplayName();

               // if (packageInfo.isDependencyFor()) {
               //     desc += " [*]";
               // }
                return desc;
            }
            assert element instanceof String || element instanceof PackageInfo;
            return null;
        }
    }

    /**
     * Provides the content for the tree view.
     * Root branches are {@link LicenseEntry} elements.
     * Leave nodes are {@link PackageInfo} which all have the same license.
     */
    private class NewPackagesContentProvider implements ITreeContentProvider {
        private List<LicenseEntry> mInput;

        @Override
        public void dispose() {
            // pass
        }

        @SuppressWarnings("unchecked")
        @Override
        public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
            // Input should be the result from createTreeInput.
            if (newInput instanceof List<?> &&
                    ((List<?>) newInput).size() > 0 &&
                    ((List<?>) newInput).get(0) instanceof LicenseEntry) {
                mInput = (List<LicenseEntry>) newInput;
            } else {
                mInput = null;
            }
        }

        @Override
        public boolean hasChildren(Object parent) {
            if (parent instanceof List<?>) {
                // This is the root of the tree.
                return true;

            } else if (parent instanceof LicenseEntry) {
                return ((LicenseEntry) parent).getPackageInfoList().size() > 0;
            }

            return false;
        }

        @Override
        public Object[] getElements(Object parent) {
            return getChildren(parent);
        }

        @Override
        public Object[] getChildren(Object parent) {
            if (parent instanceof List<?>) {
                return ((List<?>) parent).toArray();

            } else if (parent instanceof LicenseEntry) {
                return ((LicenseEntry) parent).getPackageInfoList().toArray();
            }

            return new Object[0];
        }

        @Override
        public Object getParent(Object child) {
            if (child instanceof LicenseEntry) {
                return ((LicenseEntry) child).getRoot();

            } else if (child instanceof PackageInfo && mInput != null) {
                for (LicenseEntry entry : mInput) {
                    if (entry.getPackageInfoList().contains(child)) {
                        return entry;
                    }
                }
            }

            return null;
        }
    }

    /**
     * Represents a branch in the view tree: an entry where all the sub-archive info
     * share the same license. Contains a link to the share root list for convenience.
     */
    private static class LicenseEntry {
        private final List<LicenseEntry> mRoot;
        private final String mLicenseRef;
        private final List<PackageInfo> mPackageInfoList;

        public LicenseEntry(
                @NonNull List<LicenseEntry> root,
                @NonNull String licenseRef,
                @NonNull List<PackageInfo> packageInfoList) {
            mRoot = root;
            mLicenseRef = licenseRef;
            mPackageInfoList = packageInfoList;
        }

        @NonNull
        public List<LicenseEntry> getRoot() {
            return mRoot;
        }

        @NonNull
        public String getLicenseRef() {
            return mLicenseRef;
        }

        @NonNull
        public List<PackageInfo> getPackageInfoList() {
            return mPackageInfoList;
        }
    }

    /**
     * Creates the tree structure based on the given archives.
     * The current structure is to have a branch per license type,
     * with all the archives sharing the same license under it.
     * Elements with no license are left at the root.
     *
     * @param archives The non-null collection of archive info to display. Ideally non-empty.
     * @return A list of {@link LicenseEntry}, each containing a list of {@link PackageInfo}.
     */
    @NonNull
    private List<LicenseEntry> createTreeInput(@NonNull Set<PackageInfo> packageInfoList) {
        // Build an ordered map with all the licenses, ordered by license ref name.
        final String noLicense = "No license";      //NLS

        Comparator<String> comp = new Comparator<String>() {
            @Override
            public int compare(String s1, String s2) {
                boolean first1 = noLicense.equals(s1);
                boolean first2 = noLicense.equals(s2);
                if (first1 && first2) {
                    return 0;
                } else if (first1) {
                    return -1;
                } else if (first2) {
                    return 1;
                }
                return s1.compareTo(s2);
            }
        };

        Map<String, List<PackageInfo>> packageInfoMap = new TreeMap<String, List<PackageInfo>>(comp);

        for (PackageInfo info : packageInfoList) {
            String ref = noLicense;
            License license = info.getNewPackage().getLicense();
            if (license != null) {
                ref = license.getId(); //prettyLicenseRef(license.getLicenseRef());
            }

            List<PackageInfo> list = packageInfoMap.get(ref);
            if (list == null) {
            	packageInfoMap.put(ref, list = new ArrayList<PackageInfo>());
            }
            list.add(info);
        }

        // Transform result into a list
        List<LicenseEntry> licensesList = new ArrayList<LicenseEntry>();
        for (Map.Entry<String, List<PackageInfo>> entry : packageInfoMap.entrySet()) {
            licensesList.add(new LicenseEntry(licensesList, entry.getKey(), entry.getValue()));
        }
        return licensesList;
    }

    private void init(List<RemotePackage> newPackages) {
        Iterator<RemotePackage> iterator = newPackages.iterator();
        while(iterator.hasNext()) {
        	PackageInfo packageInfo = new PackageInfo(iterator.next());
        	if (!mPackages.contains(packageInfo))
        		mPackages.add(packageInfo);
        }
	}

	private void init(Collection<UpdatablePackage> updates) {
        Iterator<UpdatablePackage> iterator = updates.iterator();
        while(iterator.hasNext()) {
        	UpdatablePackage updatable = iterator.next();
            mPackages.add(new PackageInfo(updatable.getRemote(), updatable.getLocal()));
        }
	}
	
	private String getName(RemotePackage remotePackage) {
        // Add revision if not in path
    	String path = remotePackage.getPath();
    	String version = null;
    	int index = path.lastIndexOf(';');
    	if (index != -1) {
    		version = path.substring(index + 1);
    		if (!Character.isDigit(version.charAt(0)))
    			version = null;
    	}
        String packageName = remotePackage.getDisplayName();
        if (version == null)
        	packageName = packageName + " " + remotePackage.getVersion().toShortString();
        return packageName;
	}
	
    /**
     * Reformats the licenseRef to be more human-readable.
     * It's an XML ref and in practice it looks like [oem-]android-[type]-license.
     * If it's not a format we can deal with, leave it alone.
     */
	/*
    private String prettyLicenseRef(String ref) {
        // capitalize every word
        StringBuilder sb = new StringBuilder();
        boolean capitalize = true;
        for (char c : ref.toCharArray()) {
            if (c >= 'a' && c <= 'z') {
                if (capitalize) {
                    c = (char) (c + 'A' - 'a');
                    capitalize = false;
                }
            } else {
                if (c == '-') {
                    c = ' ';
                }
                capitalize = true;
            }
            sb.append(c);
        }

        ref = sb.toString();

        // A few acronyms should stay upper-case
        for (String w : new String[] { "Sdk", "Mips", "Arm" }) {
            ref = ref.replaceAll(w, w.toUpperCase(Locale.US));
        }

        return ref;
    }
*/
    // End of hiding from SWT Designer
    //$hide<<$
}
