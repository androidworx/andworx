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
package com.android.sdkuilib.ui;

import org.eclipse.andmore.sdktool.SdkCallAgent;
import org.eclipse.andmore.sdktool.SdkContext;
import org.eclipse.andmore.sdktool.Utilities;
import org.eclipse.andmore.sdktool.Utilities.Compatibility;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.TableItem;

import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.sdkuilib.internal.repository.avd.AvdAgent;
import com.android.sdkuilib.internal.repository.avd.SdkTargets;
import com.android.sdkuilib.internal.repository.avd.SystemImageInfo;
import com.android.sdkuilib.internal.repository.ui.ManagerControls;
import com.android.sdkuilib.internal.widgets.AvdSelector;
import com.android.sdkuilib.internal.widgets.AvdSelector.IAvdFilter;

/**
 * A control to select an Android Virtual Device (AVD)
 * @author Andrew Bowley
 *
 */
public class AvdSelectorWindow implements ManagerControls {

	private final AvdSelector avdSelector;
	private final SdkContext sdkContext;
    private final SdkTargets sdkTargets;
    private SelectionAdapter refreshListener;
    private SelectionAdapter closeListener;
    private Button refreshButton;
    private Button okButton;
	
	public AvdSelectorWindow(Composite parent, SdkCallAgent sdkCallAgent) {
		this(parent, sdkCallAgent, null);
	}
	
	public AvdSelectorWindow(Composite parent, SdkCallAgent sdkCallAgent, IAvdFilter filter) {
		this.sdkContext = sdkCallAgent.getSdkContext();
    	sdkTargets = new SdkTargets(sdkContext);
        Composite composite = new Composite(parent, SWT.NONE);
        GridLayoutBuilder.create(composite).noMargins();
        Group buttonBar = new Group(composite, SWT.NONE);
        GridDataBuilder.create(buttonBar).vCenter().fill().grab();
        GridLayoutBuilder.create(buttonBar).columns(2);
        refreshButton = new Button(buttonBar, SWT.PUSH | SWT.FLAT);
        GridDataBuilder.create(refreshButton).vCenter().wHint(150).hFill().hGrab().hRight();
        refreshButton.setText("Refresh");
        refreshButton.setToolTipText("Reloads the list");
        refreshButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent event) {
                doRefresh(event);
            }
        });
    	okButton = new Button(buttonBar, SWT.PUSH | SWT.FLAT);
        GridDataBuilder.create(okButton).vCenter().wHint(150).hRight();
    	okButton.setText("OK");
    	okButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent event) {
            	doClose(event);
            	parent.getShell().close();
             }
        });
    	parent.getShell().setDefaultButton(okButton);
		avdSelector = new AvdSelector(composite, sdkContext, AvdDisplayMode.SIMPLE_CHECK, (ManagerControls)this);
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
		return avdSelector.setSelection(avd);
	}

    /**
     * Returns the currently selected item. In {@link DisplayMode#SIMPLE_CHECK} mode this will
     * return the {@link AvdInfo} that is checked instead of the list selection.
     *
     * @return The currently selected item or null.
     */
    public AvdInfo getSelected() {
    	AvdAgent avdAgent = avdSelector.getSelected();
    	return avdAgent != null ? avdAgent.getAvd() : null;
    }
    
    /**
     * Sets the table grid layout data.
     *
     * @param heightHint If > 0, the height hint is set to the requested value.
     */
    public void setTableHeightHint(int heightHint) {
    	avdSelector.setTableHeightHint(heightHint);
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
    	avdSelector.setSelectionListener(selectionListener);
    }

    /**
     * Enables the receiver if the argument is true, and disables it otherwise.
     * A disabled control is typically not selectable from the user interface
     * and draws with an inactive or "grayed" look.
     *
     * @param enabled the new enabled state.
     */
    public void setEnabled(boolean enabled) {
    	avdSelector.setEnabled(enabled);
    }
    
    /**
     * Sets a new AVD manager and updates AVD filter parameters
     * This also refreshes the display 
     * @param manager the AVD manager.
     */
    public void setManager(AvdManager manager, IAndroidTarget target, AndroidVersion minApiVersion) {
    	avdSelector.setManager(manager);
    	avdSelector.refresh(false);
    	avdSelector.setFilter(getCompatibilityFilter(target, minApiVersion));
    }

	public void refresh(boolean reload) {
		avdSelector.refresh(reload);
	}
	
	@Override
	public void enableRefresh(boolean isEnabled) {
		refreshButton.setEnabled(isEnabled);
	}

	@Override
	public boolean isRefreshEnabled() {
		return refreshButton.isEnabled();
	}

	@Override
	public void addRefreshListener(int index, SelectionAdapter refreshListener) {
		this.refreshListener = refreshListener;
	}

	@Override
	public void addCloseListener(int index, SelectionAdapter closeListener) {
		this.closeListener = closeListener;
	}
	
	private void doRefresh(SelectionEvent event) {
		if (refreshListener != null)
			refreshListener.widgetSelected(event);
	}

	private void doClose(SelectionEvent event) {
		if (closeListener != null)
			closeListener.widgetSelected(event);
	}

    private IAvdFilter getCompatibilityFilter(IAndroidTarget target, AndroidVersion minApiVersion) {
    	return new IAvdFilter() {
 
	        @Override
	        public void prepare() {
	        }
	
	        @Override
	        public void cleanup() {
	        }
	
	        @Override
	        public boolean accept(AvdAgent avdAgent) {
	        	AvdInfo info = avdAgent.getAvd();
	            Compatibility c =
	            		Utilities.canRun(info, getAndroidTargetFor(info), target, minApiVersion);
	            return (c == Compatibility.NO) ? false : true;
	        }
	    };
    }
    
    private IAndroidTarget getAndroidTargetFor(AvdInfo info) {
        SystemImageInfo systemImageInfo = new SystemImageInfo(info);
        if (systemImageInfo.hasSystemImage())
        	return sdkTargets.getTargetForSysImage(systemImageInfo.getSystemImage());
        return avdSelector.getSdkTargets().getTargetForAndroidVersion(info.getAndroidVersion());
    }

}
