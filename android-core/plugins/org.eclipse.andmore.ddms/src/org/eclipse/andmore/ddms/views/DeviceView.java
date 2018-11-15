/*
 * Copyright (C) 2008 The Android Open Source Project
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

package org.eclipse.andmore.ddms.views;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.andmore.base.resources.ImageFactory;
import org.eclipse.andmore.ddms.DdmsPlugin;
import org.eclipse.andmore.ddms.DebugConnectorHandler;
import org.eclipse.andmore.ddms.IClientAction;
import org.eclipse.andmore.ddms.IDebuggerConnector;
import org.eclipse.andworx.ddms.devices.Devices;
import org.eclipse.andworx.ddms.devices.DeviceProfile;
import org.eclipse.andmore.ddms.editors.UiAutomatorViewer;
import org.eclipse.andmore.ddms.i18n.Messages;
import org.eclipse.andworx.build.AndworxFactory;
import org.eclipse.andworx.event.AndworxEvents;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.e4.core.services.events.IEventBroker;
import org.eclipse.e4.ui.internal.workbench.E4Workbench;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.AndroidDebugBridge.IClientChangeListener;
import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.ddmlib.ClientData.MethodProfilingStatus;
import com.android.ddmlib.DdmPreferences;
import com.android.ddmlib.IDevice;
import com.android.ddmuilib.DdmUiPreferences;
import com.android.ddmuilib.DevicesPanel.IUiSelectionListener;
import com.android.ddmuilib.DevicesPanel;
import com.android.ddmuilib.ScreenShotDialog;
import com.android.ddmuilib.ViewLabelProvider;
import com.android.uiautomator.UiAutomatorHelper;
import com.android.uiautomator.UiAutomatorHelper.UiAutomatorException;
import com.android.uiautomator.UiAutomatorHelper.UiAutomatorResult;

public class DeviceView extends ViewPart implements IUiSelectionListener, IClientChangeListener {

	private final static boolean USE_SELECTED_DEBUG_PORT = true;

	public static final String ID = "org.eclipse.andmore.ddms.views.DeviceView"; //$NON-NLS-1$

	private Shell mParentShell;
	private DevicesPanel mDeviceList;
	private final Devices devices;
	private List<IDebuggerConnector> debugConnectors;
	private ViewDebugConnectorHandler debugConnectHandler;
	private IEventBroker eventBroker;

	private Action mResetAdbAction;
	private Action mCaptureAction;
	private Action mViewUiAutomatorHierarchyAction;
//	private Action mSystraceAction;
	private Action mUpdateThreadAction;
	private Action mUpdateHeapAction;
	private Action mGcAction;
	private Action mKillAppAction;
	private Action mDebugAction;
	private Action mTracingAction;

	private ImageDescriptor mTracingStartImage;
	private ImageDescriptor mTracingStopImage;

	private class ViewDebugConnectorHandler implements DebugConnectorHandler {

		private boolean isDisposed;
		
		public void setDisposed(boolean isDisposed) {
			this.isDisposed = isDisposed;
		}

		@Override
		public void onDebugConnectorRequest(IDebuggerConnector debuggerConnector) {
			debugConnectors.add(debuggerConnector);
		}

		@Override
		public boolean isDisposed() {
			return isDisposed;
		}}
	
	public DeviceView() {
		debugConnectHandler = new ViewDebugConnectorHandler();
		debugConnectors = new LinkedList<>();
		// the view is declared with allowMultiple="false" so we can safely do this.
        IEclipseContext serviceContext = E4Workbench.getServiceContext();
        eventBroker = (IEventBroker) serviceContext.get(IEventBroker.class.getName());
        eventBroker.post(AndworxEvents.DEBUG_CONNECTOR_HANDLER, debugConnectHandler);
        devices = AndworxFactory.instance().getDevices();
	}

	@Override
	public void createPartControl(Composite parent) {
		mParentShell = parent.getShell();
		ImageFactory imageFactory = DdmsPlugin.getDefault().getImageFactory();
		mDeviceList = new DevicesPanel(devices, USE_SELECTED_DEBUG_PORT, imageFactory, DdmUiPreferences.getStore());
		mDeviceList.createPanel(parent);
		mDeviceList.addSelectionListener(this);

		DdmsPlugin plugin = DdmsPlugin.getDefault();
		mDeviceList.addSelectionListener(plugin);
		plugin.setListeningState(true);

		mCaptureAction = new Action(Messages.DeviceView_Screen_Capture) {
			@Override
			public void run() {
				ScreenShotDialog dlg = new ScreenShotDialog(DdmsPlugin.getDisplay().getActiveShell());
				dlg.open(mDeviceList.getSelectedDevice());
			}
		};
		mCaptureAction.setToolTipText(Messages.DeviceView_Screen_Capture_Tooltip);
		mCaptureAction.setImageDescriptor(imageFactory.getDescriptorByName("capture.png")); //$NON-NLS-1$

		mViewUiAutomatorHierarchyAction = new Action("Dump View Hierarchy for UI Automator") {
			@Override
			public void run() {
				takeUiAutomatorSnapshot(mDeviceList.getSelectedDevice(), DdmsPlugin.getDisplay().getActiveShell());
			}
		};
		mViewUiAutomatorHierarchyAction.setToolTipText("Dump View Hierarchy for UI Automator");
		mViewUiAutomatorHierarchyAction.setImageDescriptor(DdmsPlugin.getImageDescriptor("icons/uiautomator.png")); //$NON-NLS-1$
/*
		mSystraceAction = new Action("Capture System Wide Trace") {
			@Override
			public void run() {
				launchSystrace(mDeviceList.getSelectedDevice(), DdmsPlugin.getDisplay().getActiveShell());
			}
		};
		mSystraceAction.setToolTipText("Capture system wide trace using Android systrace");
		mSystraceAction.setImageDescriptor(DdmsPlugin.getImageDescriptor("icons/systrace.png")); //$NON-NLS-1$
		mSystraceAction.setEnabled(true);
*/
		mResetAdbAction = new Action(Messages.DeviceView_Reset_ADB) {
			@Override
			public void run() {
				AndroidDebugBridge bridge = AndroidDebugBridge.getBridge();
				if (bridge != null) {
					if (bridge.restart() == false) {
						// get the current Display
						final Display display = DdmsPlugin.getDisplay();

						// dialog box only run in ui thread..
						display.asyncExec(new Runnable() {
							@Override
							public void run() {
								Shell shell = display.getActiveShell();
								MessageDialog.openError(shell, Messages.DeviceView_ADB_Error,
										Messages.DeviceView_ADB_Failed_Restart);
							}
						});
					}
				}
			}
		};
		mResetAdbAction.setToolTipText(Messages.DeviceView_Reset_ADB_Host_Deamon);
		mResetAdbAction.setImageDescriptor(PlatformUI.getWorkbench().getSharedImages()
				.getImageDescriptor(ISharedImages.IMG_OBJS_WARN_TSK));

		mKillAppAction = new Action() {
			@Override
			public void run() {
				mDeviceList.killSelectedClient();
			}
		};

		mKillAppAction.setText(Messages.DeviceView_Stop_Process);
		mKillAppAction.setToolTipText(Messages.DeviceView_Stop_Process_Tooltip);
		mKillAppAction.setImageDescriptor(imageFactory.getDescriptorByName(ViewLabelProvider.ICON_HALT));

		mGcAction = new Action() {
			@Override
			public void run() {
				mDeviceList.forceGcOnSelectedClient();
			}
		};

		mGcAction.setText(Messages.DeviceView_Cause_GC);
		mGcAction.setToolTipText(Messages.DeviceView_Cause_GC_Tooltip);
		mGcAction.setImageDescriptor(imageFactory.getDescriptorByName(ViewLabelProvider.ICON_GC));

		mUpdateHeapAction = new Action(Messages.DeviceView_Update_Heap, IAction.AS_CHECK_BOX) {
			@Override
			public void run() {
				boolean enable = mUpdateHeapAction.isChecked();
				mDeviceList.setEnabledHeapOnSelectedClient(enable);
			}
		};
		mUpdateHeapAction.setToolTipText(Messages.DeviceView_Update_Heap_Tooltip);
		mUpdateHeapAction.setImageDescriptor(imageFactory.getDescriptorByName(ViewLabelProvider.ICON_HEAP));

		mUpdateThreadAction = new Action(Messages.DeviceView_Threads, IAction.AS_CHECK_BOX) {
			@Override
			public void run() {
				boolean enable = mUpdateThreadAction.isChecked();
				mDeviceList.setEnabledThreadOnSelectedClient(enable);
			}
		};
		mUpdateThreadAction.setToolTipText(Messages.DeviceView_Threads_Tooltip);
		mUpdateThreadAction.setImageDescriptor(imageFactory.getDescriptorByName(ViewLabelProvider.ICON_THREAD));

		mTracingAction = new Action() {
			@Override
			public void run() {
				mDeviceList.toggleMethodProfiling();
			}
		};
		mTracingAction.setText(Messages.DeviceView_Start_Method_Profiling);
		mTracingAction.setToolTipText(Messages.DeviceView_Start_Method_Profiling_Tooltip);
		mTracingStartImage = imageFactory.getDescriptorByName(ViewLabelProvider.ICON_TRACING_START);
		mTracingStopImage = imageFactory.getDescriptorByName(ViewLabelProvider.ICON_TRACING_STOP);
		mTracingAction.setImageDescriptor(mTracingStartImage);

		mDebugAction = new Action(Messages.DeviceView_Debug_Process) {
			@Override
			public void run() {
				if (!debugConnectors.isEmpty()) {
					Client currentClient = mDeviceList.getSelectedClient();
					if (currentClient != null) {
						ClientData clientData = currentClient.getClientData();

						// make sure the client can be debugged
						switch (clientData.getDebuggerConnectionStatus()) {
						case ERROR: {
							Display display = DdmsPlugin.getDisplay();
							Shell shell = display.getActiveShell();
							MessageDialog.openError(shell, Messages.DeviceView_Debug_Process_Title,
									Messages.DeviceView_Process_Debug_Already_In_Use);
							return;
						}
						case ATTACHED: {
							Display display = DdmsPlugin.getDisplay();
							Shell shell = display.getActiveShell();
							MessageDialog.openError(shell, Messages.DeviceView_Debug_Process_Title,
									Messages.DeviceView_Process_Already_Being_Debugged);
							return;
						}
						case DEFAULT:
						case WAITING:
							break;
						default:
							break;
						}

						// get the name of the client
						String packageName = clientData.getClientDescription();
						if (packageName != null) {

							// try all connectors till one returns true.
							for (IDebuggerConnector connector : debugConnectors) {
								try {
									if (connector.connectDebugger(packageName,
											currentClient.getDebuggerListenPort(),
											DdmPreferences.getSelectedDebugPort())) {
										return;
									}
								} catch (Throwable t) {
									// ignore, we'll just not use this
									// implementation
								}
							}

							// if we get to this point, then we failed to find a
							// project
							// that matched the application to debug
							Display display = DdmsPlugin.getDisplay();
							Shell shell = display.getActiveShell();
							MessageDialog.openError(shell, Messages.DeviceView_Debug_Process_Title,
									String.format(Messages.DeviceView_Debug_Session_Failed, packageName));
						}
					}
				}
			}
		};
		mDebugAction.setToolTipText(Messages.DeviceView_Debug_Process_Tooltip);
		mDebugAction.setImageDescriptor(imageFactory.getDescriptorByName("debug-attach.png")); //$NON-NLS-1$
		mDebugAction.setEnabled(!debugConnectors.isEmpty());

		placeActions();

		// disabling all action buttons
		selectionChanged(null, null);
/*
		ClientData.setHprofDumpHandler(new HProfHandler(mParentShell));
		AndroidDebugBridge.addClientChangeListener(this);
		ClientData.setMethodProfilingHandler(new MethodProfilingHandler(mParentShell) {
			@Override
			protected void open(String tempPath) {
				if (DdmsPlugin.getDefault().launchTraceview(tempPath) == false) {
					super.open(tempPath);
				}
			}
		});
*/
	}
	

	@Override 
	public void dispose() {
		debugConnectHandler.setDisposed(true);
		if (mDeviceList != null)
			mDeviceList.dispose();
        eventBroker.post(AndworxEvents.DEBUG_CONNECTOR_HANDLER, debugConnectHandler);
	}
	
	private void takeUiAutomatorSnapshot(final IDevice device, final Shell shell) {
		ProgressMonitorDialog dialog = new ProgressMonitorDialog(shell);
		try {
			dialog.run(true, false, new IRunnableWithProgress() {
				@Override
				public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
					UiAutomatorResult result = null;
					try {
						result = UiAutomatorHelper.takeSnapshot(device, monitor);
					} catch (UiAutomatorException e) {
						throw new InvocationTargetException(e);
					} finally {
						monitor.done();
					}

					UiAutomatorViewer.openEditor(result);
				}
			});
		} catch (Exception e) {
			Throwable t = e;
			if (e instanceof InvocationTargetException) {
				t = ((InvocationTargetException) e).getTargetException();
			}
			Status s = new Status(IStatus.ERROR, DdmsPlugin.PLUGIN_ID, "Error obtaining UI hierarchy", t);
			ErrorDialog.openError(shell, "UI Automator", "Unexpected error while obtaining UI hierarchy", s);
		}
	};
/*
	private void launchSystrace(final IDevice device, final Shell parentShell) {
		final File systraceAssets = new File(DdmsPlugin.getPlatformToolsFolder(), "systrace"); //$NON-NLS-1$
		if (!systraceAssets.isDirectory()) {
			MessageDialog.openError(parentShell, "Systrace",
					"Updated version of platform-tools (18.0.1 or greater) is required.\n"
							+ "Please update your platform-tools using SDK Manager.");
			return;
		}

		SystraceVersionDetector detector = new SystraceVersionDetector(device);
		try {
			new ProgressMonitorDialog(parentShell).run(true, false, detector);
		} catch (InvocationTargetException e) {
			MessageDialog.openError(parentShell, "Systrace", "Unexpected error while detecting atrace version: " + e);
			return;
		} catch (InterruptedException e) {
			return;
		}

		final ISystraceOptionsDialog dlg;
		if (detector.getVersion() == SystraceVersionDetector.SYSTRACE_V1) {
			dlg = new SystraceOptionsDialogV1(parentShell);
		} else {
			Client[] clients = device.getClients();
			List<String> apps = new ArrayList<String>(clients.length);
			for (int i = 0; i < clients.length; i++) {
				String name = clients[i].getClientData().getClientDescription();
				if (name != null && !name.isEmpty()) {
					apps.add(name);
				}
			}
			dlg = new SystraceOptionsDialogV2(parentShell, detector.getTags(), apps);
		}

		if (dlg.open() != Window.OK) {
			return;
		}

		final ISystraceOptions options = dlg.getSystraceOptions();

		// set trace tag if necessary:
		// adb shell setprop debug.atrace.tags.enableflags <tag>
		String tag = options.getTags();
		if (tag != null) {
			CountDownLatch setTagLatch = new CountDownLatch(1);
			CollectingOutputReceiver receiver = new CollectingOutputReceiver(setTagLatch);
			try {
				String cmd = "setprop debug.atrace.tags.enableflags " + tag;
				device.executeShellCommand(cmd, receiver);
				setTagLatch.await(5, TimeUnit.SECONDS);
			} catch (Exception e) {
				MessageDialog.openError(parentShell, "Systrace", "Unexpected error while setting trace tags: " + e);
				return;
			}

			String shellOutput = receiver.getOutput();
			if (shellOutput.contains("Error type")) { //$NON-NLS-1$
				throw new RuntimeException(receiver.getOutput());
			}
		}

		// obtain the output of "adb shell atrace <trace-options>" and generate
		// the html file
		ProgressMonitorDialog d = new ProgressMonitorDialog(parentShell);
		try {
			d.run(true, true, new IRunnableWithProgress() {
				@Override
				public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
					boolean COMPRESS_DATA = true;

					monitor.setTaskName("Collecting Trace Information");
					final String atraceOptions = options.getOptions() + (COMPRESS_DATA ? " -z" : "");
					SystraceTask task = new SystraceTask(device, atraceOptions);
					Thread t = new Thread(task, "Systrace Output Receiver");
					t.start();

					// check if the user has cancelled tracing every so often
					while (true) {
						t.join(1000);

						if (t.isAlive()) {
							if (monitor.isCanceled()) {
								task.cancel();
								return;
							}
						} else {
							break;
						}
					}

					if (task.getError() != null) {
						throw new RuntimeException(task.getError());
					}

					monitor.setTaskName("Saving trace information");
					SystraceOutputParser parser = new SystraceOutputParser(COMPRESS_DATA, SystraceOutputParser
							.getJs(systraceAssets), SystraceOutputParser.getCss(systraceAssets), SystraceOutputParser
							.getHtmlPrefix(systraceAssets), SystraceOutputParser.getHtmlSuffix(systraceAssets));

					parser.parse(task.getAtraceOutput());

					String html = parser.getSystraceHtml();
					try {
						Files.write(html.getBytes(), new File(dlg.getTraceFilePath()));
					} catch (IOException e) {
						throw new InvocationTargetException(e);
					}
				}
			});
		} catch (InvocationTargetException e) {
			ErrorDialog.openError(parentShell, "Systrace", "Unable to collect system trace.", new Status(IStatus.ERROR,
					DdmsPlugin.PLUGIN_ID, "Unexpected error while collecting system trace.", e.getCause()));
		} catch (InterruptedException ignore) {
		}
	}
*/
	@Override
	public void setFocus() {
		mDeviceList.setFocus();
	}

	/**
	 * Sent when a new {@link IDevice} and {@link Client} are selected.
	 *
	 * @param selectedDevice
	 *            the selected device. If null, no devices are selected.
	 * @param selectedClient
	 *            The selected client. If null, no clients are selected.
	 */
	@Override
	public void selectionChanged(IDevice selectedDevice, Client selectedClient) {
		// update the buttons
		doSelectionChanged(selectedClient);
		doSelectionChanged(selectedDevice);
	}

	private void doSelectionChanged(Client selectedClient) {
		// update the buttons
		if (selectedClient != null) {
			if (USE_SELECTED_DEBUG_PORT) {
				// set the client as the debug client
				selectedClient.setAsSelectedClient();
			}

			mDebugAction.setEnabled(!debugConnectors.isEmpty());
			mKillAppAction.setEnabled(true);
			mGcAction.setEnabled(true);

			mUpdateHeapAction.setEnabled(true);
			mUpdateHeapAction.setChecked(selectedClient.isHeapUpdateEnabled());

			mUpdateThreadAction.setEnabled(true);
			mUpdateThreadAction.setChecked(selectedClient.isThreadUpdateEnabled());

			ClientData data = selectedClient.getClientData();

			if (data.hasFeature(ClientData.FEATURE_PROFILING)) {
				mTracingAction.setEnabled(true);
				if (data.getMethodProfilingStatus() == MethodProfilingStatus.TRACER_ON
						|| data.getMethodProfilingStatus() == MethodProfilingStatus.SAMPLER_ON) {
					mTracingAction.setToolTipText(Messages.DeviceView_Stop_Method_Profiling_Tooltip);
					mTracingAction.setText(Messages.DeviceView_Stop_Method_Profiling);
					mTracingAction.setImageDescriptor(mTracingStopImage);
				} else {
					mTracingAction.setToolTipText(Messages.DeviceView_Start_Method_Profiling_Tooltip);
					mTracingAction.setImageDescriptor(mTracingStartImage);
					mTracingAction.setText(Messages.DeviceView_Start_Method_Profiling);
				}
			} else {
				mTracingAction.setEnabled(false);
				mTracingAction.setImageDescriptor(mTracingStartImage);
				mTracingAction.setToolTipText(Messages.DeviceView_Start_Method_Profiling_Not_Suported_By_Vm);
				mTracingAction.setText(Messages.DeviceView_Start_Method_Profiling);
			}
		} else {
			if (USE_SELECTED_DEBUG_PORT) {
				// set the client as the debug client
				AndroidDebugBridge bridge = AndroidDebugBridge.getBridge();
				if (bridge != null) {
					bridge.setSelectedClient(null);
				}
			}

			mDebugAction.setEnabled(false);
			mKillAppAction.setEnabled(false);
			mGcAction.setEnabled(false);
			mUpdateHeapAction.setChecked(false);
			mUpdateHeapAction.setEnabled(false);
			mUpdateThreadAction.setEnabled(false);
			mUpdateThreadAction.setChecked(false);
			mTracingAction.setEnabled(false);
			mTracingAction.setImageDescriptor(mTracingStartImage);
			mTracingAction.setToolTipText(Messages.DeviceView_Start_Method_Profiling_Tooltip);
			mTracingAction.setText(Messages.DeviceView_Start_Method_Profiling);
		}

		for (IClientAction a : DdmsPlugin.getDefault().getClientSpecificActions()) {
			a.selectedClientChanged(selectedClient);
		}
	}

	private void doSelectionChanged(IDevice selectedDevice) {
		boolean validDevice = selectedDevice != null;

		mCaptureAction.setEnabled(validDevice);
		mViewUiAutomatorHierarchyAction.setEnabled(validDevice);
		//mSystraceAction.setEnabled(validDevice);
	}

	/**
	 * Place the actions in the ui.
	 */
	private final void placeActions() {
		IActionBars actionBars = getViewSite().getActionBars();

		// first in the menu
		IMenuManager menuManager = actionBars.getMenuManager();
		menuManager.removeAll();
		menuManager.add(mDebugAction);
		menuManager.add(new Separator());
		menuManager.add(mUpdateHeapAction);
		menuManager.add(mGcAction);
		menuManager.add(new Separator());
		menuManager.add(mUpdateThreadAction);
		menuManager.add(mTracingAction);
		menuManager.add(new Separator());
		menuManager.add(mKillAppAction);
		menuManager.add(new Separator());
		menuManager.add(mCaptureAction);
		menuManager.add(new Separator());
		menuManager.add(mViewUiAutomatorHierarchyAction);
		//menuManager.add(new Separator());
		//menuManager.add(mSystraceAction);
		menuManager.add(new Separator());
		menuManager.add(mResetAdbAction);
		for (IClientAction a : DdmsPlugin.getDefault().getClientSpecificActions()) {
			menuManager.add(a.getAction());
		}
		// and then in the toolbar
		IToolBarManager toolBarManager = actionBars.getToolBarManager();
		toolBarManager.removeAll();
		toolBarManager.add(mDebugAction);
		toolBarManager.add(new Separator());
		toolBarManager.add(mUpdateHeapAction);
		toolBarManager.add(mGcAction);
		toolBarManager.add(new Separator());
		toolBarManager.add(mUpdateThreadAction);
		toolBarManager.add(mTracingAction);
		toolBarManager.add(new Separator());
		toolBarManager.add(mKillAppAction);
		toolBarManager.add(new Separator());
		toolBarManager.add(mCaptureAction);
		toolBarManager.add(new Separator());
		toolBarManager.add(mViewUiAutomatorHierarchyAction);
//		toolBarManager.add(new Separator());
//		toolBarManager.add(mSystraceAction);
		for (IClientAction a : DdmsPlugin.getDefault().getClientSpecificActions()) {
			toolBarManager.add(a.getAction());
		}
	}

	@Override
	public void clientChanged(final Client client, int changeMask) {
		if ((changeMask & Client.CHANGE_METHOD_PROFILING_STATUS) == Client.CHANGE_METHOD_PROFILING_STATUS) {
			if (mDeviceList.getSelectedClient() == client) {
				mParentShell.getDisplay().asyncExec(new Runnable() {
					@Override
					public void run() {
						// force refresh of the button enabled state.
						doSelectionChanged(client);
					}
				});
			}
		} /*else if ((changeMask & Client.CHANGE_HPROF) == Client.CHANGE_HPROF) {
			if (mDeviceList.getSelectedClient() == client) {
				HprofData hprofData = client.getClientData().getHprofData();
				if (hprofData == null) {
					hProfHandler.onEndFailure(client, null);
				}
				else {
					if (hprofData.type == HprofData.Type.DATA)
						hProfHandler.onSuccess(hprofData.data, client);
					else
						hProfHandler.onSuccess(hprofData.filename, client);
				}
			}
		}*/
	}
}
