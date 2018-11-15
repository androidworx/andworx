/*
 * Copyright (C) 2012 The Android Open Source Project
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

package org.eclipse.andworx.ddms.devices;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.andworx.build.AndworxBuildPlugin;
import org.eclipse.andworx.build.AndworxFactory;
import org.eclipse.andworx.context.AndroidEnvironment;
import org.eclipse.andworx.exception.AndworxException;
import org.eclipse.andworx.log.SdkLogger;
import org.eclipse.andworx.sdk.SdkProfile;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.AndroidDebugBridge.IClientChangeListener;
import com.android.ddmlib.AndroidDebugBridge.IDeviceChangeListener;
import com.android.ddmlib.Client;
import com.android.ddmlib.CollectingOutputReceiver;
import com.android.ddmlib.EmulatorConsole;
import com.android.ddmlib.FileListingService;
import com.android.ddmlib.FileListingService.FileEntry;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IDevice.DeviceState;
import com.android.ddmlib.MultiLineReceiver;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.SyncException;
import com.android.ddmlib.SyncService;
import com.android.ddmlib.SyncService.ISyncProgressMonitor;
import com.android.ide.common.process.ProcessException;
import com.google.common.base.Throwables;

/**
 * Monitors connection status of all devices
 */
public class DeviceMonitor implements IClientChangeListener, IDeviceChangeListener {
	
	/** The plug-in ID to use when creating Status objects  */
	public static final String PLUGIN_ID = AndworxBuildPlugin.PLUGIN_ID;
	
	private static final String CONSOLE_PUSH = "Push %s to %s";

	/** Command for switching back to USB connection mode */
	private static final String USB_SWITCH_BACK_COMMAND = "usb"; //$NON-NLS-1$

	/** Argument which indicates the device to apply a certain command */
	private static final String DEVICE_ID_INDICATOR = "-s"; //$NON-NLS-1$

	private static final String DEFAULT_WIRELESS_DEVICE_PROPERTY = "tiwlan0"; //$NON-NLS-1$

	/** Folder located inside the SDK folder containing some sdk tools */
	final static String TOOLS_FOLDER = SdkConstants.FD_TOOLS;

	/** Folder located inside the SDK folder and containing the ADB */
	final static String PLATFORM_TOOLS_FOLDER = SdkConstants.FD_PLATFORM_TOOLS;

	/** adb (android debug bridge) command */
	final static String ADB_COMMAND = "adb"; //$NON-NLS-1$

	/** Command to concatenate with "adb" to have the device shell */
	final static String SHELL_CMD = "shell"; //$NON-NLS-1$

	/** Options to be used with adb to indicate run operation */
	//private static final String AM_CMD = "am"; //$NON-NLS-1$

	/** Command to concatenate with "am" to have an activity executed at the device */
	//private static final String START_CMD = "start"; //$NON-NLS-1$

	/** Parameter for running in debug mode */
	//private static final String ADB_AM_DEBUG = "-D"; //$NON-NLS-1$

	/** Parameter provided before the application package/name */
	//private static final String ADB_AM_NAME = "-n"; //$NON-NLS-1$

	/** Parameter for selecting emulator instance */
	final static String ADB_INSTANCE_PARAMETER = DEVICE_ID_INDICATOR;

	/**
	 * Folder for the SDK.
	 */
	private static final String SDCARD_FOLDER = "sdcard"; //$NON-NLS-1$

	/** Folder for the SDK */
	private static final String MNT_SDCARD_FOLDER = "mnt/sdcard"; //$NON-NLS-1$

	/* TCP/IP */
	private static final String CONNECT_TCPIP_CMD = "connect"; //$NON-NLS-1$

	private static final String DISCONNECT_TCPIP_CMD = "disconnect"; //$NON-NLS-1$

	private static final String TCPIP_CMD = "tcpip"; //$NON-NLS-1$

	private static final String IFCONFIG_CMD = "ifconfig"; //$NON-NLS-1$

	/**
	 * Property from device which represents the wi-fi value to use ipconfig
	 * command.
	 */
	private static final String WIFI_INTERFACE_DEVICE_PROPERTY = "wifi.interface"; //$NON-NLS-1$

	// IP validation
	private static final String ZERO_TO_255_PATTERN = "((\\d)|(\\d\\d)|([0-1]\\d\\d)|(2[0-4]\\d)|(25[0-5]))"; //$NON-NLS-1$

	private static final String IP_PATTERN = "(" + ZERO_TO_255_PATTERN + "\\." //$NON-NLS-1$ //$NON-NLS-2$
			+ ZERO_TO_255_PATTERN + "\\." + ZERO_TO_255_PATTERN + "\\." + ZERO_TO_255_PATTERN //$NON-NLS-1$ //$NON-NLS-2$
			+ ")+"; //$NON-NLS-1$
	private static final String NULL_SERIAL_NUMBER = "%s called with null serial number";

	/**
	 * Defines a stop condition
	 */
	interface IStopCondition {
		public boolean canStop();
	}

	private static class CanStopCondition implements IStopCondition {
		@Override
		public boolean canStop() {
			return true;
		}
	}

	private static SdkLogger logger = SdkLogger.getLogger(DeviceMonitor.class.getName());

    /** Timeout for shell commands in milliseconds */
    private static long SHELL_TIMEOUT = 500;
	/** Duration between device polls in seconds */
	private int POLL_DURATION = 2;
	
	/** Object factory */
	private final AndworxFactory objectFactory;
	
	/**
	 * Map containing all connected devices. It is being kept for us not to
	 * depend on ADT every time we need one, preventing deadlocks.
	 */
	private final Map<String, IDevice> connectedDevices;

	/**
	 * Set containing the serial numbers of the devices completely loaded. A
	 * device is considered completely loaded if it has set property "dev.bootcomplete"
	 */
	private final Set<String> completelyUpDevices;
	/** Device manager - monitors device state */
	private final Devices devices;
	/** Map of client profile data dumps */
	//private final ConcurrentHashMap<Client,HprofData> hprofDataMap;

	private final Map<String, String> avdNameMap;
	private final Object consoleLock;

	/**
	 * Construct DeviceMonitor object
	 * @param devices Device manager - monitors device state 
	 */
	public DeviceMonitor(Devices devices) {
        this.devices = devices;
        //hprofDataMap = new ConcurrentHashMap<>();
        connectedDevices = new ConcurrentHashMap<String, IDevice>();
        completelyUpDevices = new HashSet<String>();
        avdNameMap = new HashMap<String, String>();
        consoleLock = new Object();
        objectFactory = AndworxFactory.instance();
	}

	/**
	 * Start device monitor
	 * @param adb Android Debug Bridge
	 */
    public void start(AndroidDebugBridge adb) {
		if (adb.hasInitialDeviceList()) {
			IDevice[] x = adb.getDevices();
			IDevice[] newDevices = x;
			List<IDevice> oldDevList = new ArrayList<IDevice>(connectedDevices.values());

			for (IDevice newDev : newDevices) {
				String serialNum = newDev.getSerialNumber();
				if (connectedDevices.containsKey(serialNum)) {
					IDevice oldDev = connectedDevices.get(serialNum);
					oldDevList.remove(oldDev);
					if (oldDev.getState().compareTo((newDev).getState()) != 0) {
						if ((newDev).getState() == DeviceState.OFFLINE) {
							deviceDisconnected(newDev);
						} else if ((newDev).getState() == DeviceState.ONLINE) {
							deviceConnected(newDev);
						}
					}
				} else {
					deviceConnected(newDev);
				}
			}
			for (IDevice oldDev : oldDevList) {
				deviceDisconnected(oldDev);
			}
		}
    }

	/**
	 * Create port forward for a given VM
	 * @param serialNumber Android serial number
	 * @param from Port number from
	 * @param to Port number to
	 * @return true is the port forward was successful, false otherwise
	 */
	public boolean createForward(String serialNumber, int from, int to) {
		if (serialNumber == null) {
			logger.error(null, String.format(NULL_SERIAL_NUMBER, "createForward"));
			return false;
		}
		boolean ok = true;
		IDevice device = getDeviceBySerialNumber(serialNumber);
		try {
			device.createForward(from, to);
		} catch (Exception e) {
			logger.error(null, "Error creating forward of device: " //$NON-NLS-1$
					+ serialNumber + " from " + from + " to " + to, e); //$NON-NLS-1$ //$NON-NLS-2$
			ok = false;
		}
		return ok;
	}

	/**
	 * Kill the communication channel
	 * @param serialNumber The serial number of the device to kill
	 */
	public void kill(String serialNumber) {
		if (serialNumber == null) {
			logger.error(null, String.format(NULL_SERIAL_NUMBER, "kill"));
		}
		if (isDeviceOnline(serialNumber)) {
			IDevice deviceToKill = getDeviceBySerialNumber(serialNumber);
			if (deviceToKill != null) {
				synchronized (consoleLock) {
					EmulatorConsole console = EmulatorConsole.getConsole(deviceToKill);
					if (console != null) {
						console.kill();
					}
				}
			}
		}
	}

	/**
	 * Push files to device
	 * @param serialNumber Android device serial number
	 * @param localDir local folder path
	 * @param fileNames files to transfer
	 * @param remoteDir destination folder path
	 * @param timeout timeout for the operation
	 * @param monitor monitor associated with the operation
	 */
	public IStatus pushFiles(String serialNumber, String localDir, Collection<String> fileNames,
			String remoteDir, int timeout, final IProgressMonitor monitor, OutputStream outputStream) {
		return transferFiles(true, serialNumber, localDir, fileNames, remoteDir, timeout, monitor, outputStream);
	}

	/**
	 * Push files to device
	 * @param serialNumber Android device serial number
	 * @param localFiles destination local files
	 * @param remoteFiles remote files to transfer as localFiles to desktop
	 * @param timeout timeout for the operation
	 * @param monitor monitor associated with the operation
	 */
	public IStatus pushFiles(String serialNumber, List<File> localFiles, List<String> remoteFiles, int timeout,
			final IProgressMonitor monitor, OutputStream outputStream) {
		return transferFiles(true, serialNumber, localFiles, remoteFiles, timeout, monitor, outputStream);
	}

	/**
	 * Pull files from device
	 * @param serialNumber Android device serial number
	 * @param localDir local folder path
	 * @param fileNames files to transfer
	 * @param remoteDir destination folder path
	 * @param timeout timeout for the operation
	 * @param monitor monitor associated with the operation
	 */
	public IStatus pullFiles(String serialNumber, String localDir, Collection<String> fileNames,
			String remoteDir, int timeout, final IProgressMonitor monitor, OutputStream outputStream) {
		return transferFiles(false, serialNumber, localDir, fileNames, remoteDir, timeout, monitor, outputStream);
	}

	/**
	 * Pull files from device
	 * @param serialNumber Android device serial number
	 * @param localFiles local files to transfer as remoteFiles to device
	 * @param remoteFiles destination remote files
	 * @param timeout timeout for the operation
	 * @param monitor monitor associated with the operation
	 */
	public IStatus pullFiles(String serialNumber, List<File> localFiles, List<String> remoteFiles, int timeout,
			final IProgressMonitor monitor, OutputStream outputStream) {
		return transferFiles(false, serialNumber, localFiles, remoteFiles, timeout, monitor, outputStream);
	}

	/**
	 * Check if the application is running in the device with specified serial number
	 * @param serialNumber
	 * @param applicationName
	 * @return true if it is running, false otherwise
	 */
	public boolean isApplicationRunning(String serialNumber, String applicationName) {
		if (serialNumber == null) {
			logger.error(null, String.format(NULL_SERIAL_NUMBER, "isApplicationRunning"));
			return false;
		}
		IDevice dev = null;
		boolean running = false;
		dev = connectedDevices.get(serialNumber);
		if (dev != null) {
			running = dev.getClient(applicationName) != null;
		}
		return running;
	}

	/**
	 * Connect to a Remote Device given its IP/Port
	 * @param device the Remote Device Instance
	 * @param host device host (IP)
	 * @param port device port
	 * @param timeout the maximum time allowed to successfully connect to the device
	 * @param monitor  the monitor of the operation
	 * @return the status of the operation
	 * @throws IOException
	 */
	public IStatus connectTcpIp(final DeviceProfile device, String host, String port, int timeout,
			IProgressMonitor monitor) throws IOException {
		SubMonitor subMonitor = SubMonitor.convert(monitor, 1000);

		subMonitor.beginTask("Connecting to device via TCP/IP...", 10);

		IStatus status = Status.OK_STATUS;

		final String serialNumber = devices.getSerialNumberByName(device.getName());
		if (!isDeviceOnline(serialNumber)) // check if it's already connected
		{
			String[] cmd = createConnectTcpIpCommand(host, port);

			status = executeRemoteDevicesCommand(cmd, null, timeout,
					String.format("The timeout was reached while trying to connect to \"%s\" Android remote device.", device.getName()),
					new IStopCondition() {

						@Override
						public boolean canStop() {
							return isDeviceOnline(serialNumber);
						}
					}, subMonitor.newChild(1000));

		}
		subMonitor.worked(1000);
		return status;
	}

	/**
	 * Method which switches the device connection mode from TCP/IP to USB.
	 * @param device {@link DeviceProfile} device to have its connection mode changed.
	 * @param host The IP of the device.
	 * @param port The port in which the TCP/IP connection is established.
	 * @param timeout The maximum time which the switching operation is attempted.
	 * @param monitor  The {@link IProgressMonitor} which this operation is being computed.
	 * @return Returns the {@link IStatus} of this operation.
	 * @throws IOException
	 *             Exception thrown in case something goes wrong while trying to
	 *             switch the device connection mode from TCP/IP to USB.
	 */
	public IStatus switchFromTCPConnectionModeToUSBConnectionMode(final DeviceProfile device, String host,
			String port, int timeout, IProgressMonitor monitor) throws IOException {
		SubMonitor subMonitor = SubMonitor.convert(monitor, 1000);

		subMonitor.beginTask("Switching device connection mode from TCP/IP to USB...", 10);

		IStatus status = Status.OK_STATUS;

		final String serialNumber = devices.getSerialNumberByName(device.getName());
		if (isDeviceOnline(serialNumber)) // check if it's already connected
		{
			String[] cmd = createSwitchToUSBConnectionModeCommand(host, port);

			status = executeRemoteDevicesCommand(cmd, null, timeout,
					String.format("The timeout was reached while trying to switch the Android device %s connection mode from TCP/IP to USB.", device.getName()),
					new IStopCondition() {

						@Override
						public boolean canStop() {
							return isDeviceOnline(serialNumber);
						}
					}, subMonitor.newChild(1000));
		}
		subMonitor.worked(1000);
		return status;
	}

	/**
	 * Get the wireless ip from the connected handset
	 * @param serialNumber
	 * @param monitor
	 * @return the ip or null if not possible to retrieve it
	 */
	public String getWirelessIPfromHandset(String serialNumber, IProgressMonitor monitor) {
		String handset_wireless_ip = null;
		IDevice device = null;
		if (serialNumber == null) {
			logger.error(null, String.format(NULL_SERIAL_NUMBER, "getWirelessIOfromHandset"));
			return "";
		}
		device = connectedDevices.get(serialNumber);
		if (device != null) {
			// get the wi-fi name for executing the ipconfig command
			String wifiProperty = device.getProperty(WIFI_INTERFACE_DEVICE_PROPERTY);
			if (wifiProperty == null) {
				wifiProperty = DEFAULT_WIRELESS_DEVICE_PROPERTY;
			}
			// execute ipconfig command
			Collection<String> answers = executeShellCmd(serialNumber, IFCONFIG_CMD + " " + wifiProperty, SHELL_TIMEOUT, monitor); //$NON-NLS-1$

			// Success message - for example
			// [tiwlan0: ip 192.168.0.174 mask 255.255.255.0 flags [up broadcast
			// running multicast]]

			if (answers != null) {
				String result = answers.toString();
				if (result != null) {
					// splits the result of the shell command and gets the third
					// position
					// that should be the IP number
					String[] result_splited = result.split(" "); //$NON-NLS-1$
					if (result_splited.length >= 3) {
						// check whether there is an IP
						Pattern pattern = Pattern.compile(IP_PATTERN);
						Matcher matcher = pattern.matcher(result);
						if (matcher.find()) {
							handset_wireless_ip = result_splited[2];
						}
					}
				}
			}
		}
		return handset_wireless_ip;
	}

	/**
	 * Switch adb connection mode of an specific device to TCPIP
	 * @param deviceName name of the handset instance
	 * @param host wireless ip of the handset instance
	 * @param port  number of the port to be using during the connection
	 * @param timeout the maximum time allowed to successfully connect to the device
	 * @param monitor the monitor of the operation
	 * @return the status of the operation
	 * @throws IOException
	 *             Exception thrown in case there are problems switching the
	 *             device.
	 */
	public IStatus switchUSBtoTcpIp(String deviceName, final String serialNumber, String port, int timeout,
			IProgressMonitor monitor) throws IOException {
		SubMonitor subMonitor = SubMonitor.convert(monitor, 1000);

		subMonitor.beginTask("Switching device connection mode from USB to TCP/IP...", 10);
		if (serialNumber == null) {
			logger.error(null, String.format(NULL_SERIAL_NUMBER, "switchUSBtoTcpIp"));
			return Status.CANCEL_STATUS;
		}
		IStatus status = Status.OK_STATUS;

		if (isDeviceOnline(serialNumber)) {
			String[] cmd = createSwitchToTcpIpCommand(serialNumber, port);

			status = executeRemoteDevicesCommand(cmd, null, timeout,
					String.format("The timeout was reached while trying to switch the connection mode of \"%s\" to TCP/IP.", deviceName),
					new IStopCondition() {

						@Override
						public boolean canStop() {
							return isDeviceOffline(serialNumber);
						}
					}, subMonitor.newChild(1000));
		}
		monitor.worked(1000);
		return status;
	}

	/**
	 * Disconnect from a Remote Device given its IP/Port
	 * @param device the Remote Device Instance
	 * @param host device host (IP)
	 * @param port device port
	 * @param timeout the maximum time allowed to successfully disconnect from the device
	 * @param monitor the monitor of the operation
	 * @return the status of the operation
	 * @throws IOException
	 */
	public IStatus disconnectTcpIp(final DeviceProfile device, String host, String port, int timeout,
			IProgressMonitor monitor) throws IOException {
		IStatus status = Status.OK_STATUS;

		final String serialNumber = devices.getSerialNumberByName(device.getName());
		if (isDeviceOnline(serialNumber)) // check if it's already disconnected
		{
			String[] cmd = createDisconnectTcpIpCommand(host, port);

			status = executeRemoteDevicesCommand(cmd, null, timeout,
					String.format("The timeout was reached while trying to switch the connection mode of \"%s\" to TCP/IP.", device.getName()),
					new IStopCondition() {

						@Override
						public boolean canStop() {
							return !isDeviceOnline(serialNumber);
						}
					}, monitor);
		}
		return status;
	}

	public Collection<String> getRunningApplications(String serialNumber) {
		Collection<String> apps = new ArrayList<String>();
		if (serialNumber != null) {
			IDevice dev = getDeviceBySerialNumber(serialNumber);
			if (dev != null) {
				Client[] clients = dev.getClients();
				if ((clients != null) && (clients.length > 0)) {
					for (Client c : clients) {
						apps.add(c.getClientData().getClientDescription());
					}
				}
			}
		}
		return apps;
	}

	/**
	 * Check if a device identified by the serial number has a mounted SDCard
	 * @param serialNumber the serial number
	 * @return true if the device has a SDCard
	 * @throws IOException
	 */
	public boolean hasSDCard(String serialNumber) throws IOException {
		if (serialNumber == null) {
			logger.error(null, String.format(NULL_SERIAL_NUMBER, "hasSDCard"));
			return false;
		}
		boolean hasSdCard = false;
		File tempSdCardFile = File.createTempFile("SDcheck", ".tmp"); //$NON-NLS-1$ //$NON-NLS-2$
		boolean tempCopiedOnSdCardFile = pushFileToDevice(serialNumber, SDCARD_FOLDER, tempSdCardFile);

		if (tempCopiedOnSdCardFile) {
			// trying to write on /sdcard folder (it works for phones previous
			// from Platform 2.2)
			if (!deleteFileFromDevice(serialNumber, tempSdCardFile.getName(), SDCARD_FOLDER)) {
				logger.error(null, "DDMSFacade: Could not delete tempfile from /sdcard when checking if card is enabled"); //$NON-NLS-1$
			}
			hasSdCard = true;
			tempSdCardFile.delete();
		} else {

			File tempMntFile = File.createTempFile("SDcheck", ".tmp"); //$NON-NLS-1$ //$NON-NLS-2$
			boolean tempCopiedOnMntFile = pushFileToDevice(serialNumber, MNT_SDCARD_FOLDER, tempSdCardFile);

			if (tempCopiedOnMntFile) {
				// trying to write on /mnt/sdcard folder (it works for phones
				// since Platform 2.2)
				if (!deleteFileFromDevice(serialNumber, tempMntFile.getName(), MNT_SDCARD_FOLDER)) {
					logger.error(null, "DDMSFacade: Could not delete tempfile from /mnt/sdcard when checking if card is enabled"); //$NON-NLS-1$
				}
				hasSdCard = true;
				tempMntFile.delete();
			}

		}
		return hasSdCard;
	}

	/**
	 * Execute an app in the Device
	 * 
	 * @param serialNumber
	 *            Serial number of the device where to execute the command
	 * @param remoteCommand
	 *            command to be executed remotely on the Device
	 * @param monitor
	 *            monitor associated with the operation
	 * 
	 * @return The lines read from the command output
	 * 
	 * @throws IOException
	 */
	public Collection<String> execRemoteApp(String serialNumber, String remoteCommand,
			final IProgressMonitor monitor) throws IOException {
		return executeShellCmd(serialNumber, remoteCommand, SHELL_TIMEOUT, monitor);
	}

	public String executeCommand(String[] cmd, OutputStream out) throws IOException {
		return executeCommand(cmd, out, null);
	}

	/**
	 * Execute ADB command
	 * @param cmd Command line arguments as array of strings
	 * @param out Steam to collect process output
	 * @param serialNumber
	 * @return
	 * @throws IOException
	 */
	public String executeCommand(String[] cmd, OutputStream out, String serialNumber) throws IOException {
		String fullCmd = ""; //$NON-NLS-1$
		if (out != null) {
			for (String cmdArg : cmd) {
				fullCmd += cmdArg + " "; //$NON-NLS-1$
			}
			out.write(fullCmd.getBytes());
			out.write("\n".getBytes()); //$NON-NLS-1$
		}

		Runtime r = Runtime.getRuntime();
		Process p = r.exec(cmd);

		String command_results = ""; //$NON-NLS-1$
		InputStream processIn = p.getInputStream();
		final BufferedReader br = new BufferedReader(new InputStreamReader(processIn));
		String line;
		try {
			while ((line = br.readLine()) != null) {
				command_results += line;
				command_results += "\n"; //$NON-NLS-1$
				if (out != null) {
					if (serialNumber != null) {
						out.write((serialNumber + ": ").getBytes()); //$NON-NLS-1$
					}
					out.write(line.getBytes());
					out.write("\n".getBytes()); //$NON-NLS-1$
				}
			}
		} finally {
			br.close();
		}
		return command_results;
	}

	/**
	 * Check if the device is Online (i.e. if it's possible to communicate with
	 * it) Notice it is a verification of the status of the Device which may be
	 * different than the status of the Tml Instance.
	 * @param serialNumber
	 * @return true if the Device is online, false otherwise
	 */
	public boolean isDeviceOnline(String serialNumber) {
		if (serialNumber == null) {
			logger.error(null, String.format(NULL_SERIAL_NUMBER, "isDeviceOnline"));
			return false;
		}
		IDevice device = getDeviceBySerialNumber(serialNumber);
		if ((device == null) || !device.isOnline()) {
			return false;
		}
		return true;
	}

	/** TODO - Refactor code to separate GUI part from low-level device part
	 * Dumps a HPROF file based on a client description and a device serial
	 * number
	 * 
	 * @param clientDescription
	 *            A client description of a running application
	 */
	/*
	public IStatus dumpHprofFile(String clientDescription, String serialNumber, IProgressMonitor monitor) {
		IStatus status = Status.OK_STATUS;
		monitor.beginTask("Generating Memory Analysis output", 100);

		// Retrive running apps
		monitor.setTaskName("Getting running applications");
		IDevice dev = getDeviceBySerialNumber(serialNumber);
		Client[] clients = dev.getClients();
		monitor.worked(25);

		// Store the shell
		final Shell[] shell = new Shell[1];

		PlatformUI.getWorkbench().getDisplay().syncExec(new Runnable() {
			@Override
			public void run() {

				shell[0] = new Shell(PlatformUI.getWorkbench().getDisplay());

			}
		});

		AndmoreHProfDumpHandler hprofHandler = new AndmoreHProfDumpHandler(shell[0], monitor);
		monitor.setTaskName("Setting application to analyze");
		hprofHandler.setSelectedApp(clientDescription);
		monitor.worked(25);

		try {
			// Find a client with matching description and dum the HPROF file
			for (Client client : clients) {
				if (client.getClientData().getClientDescription().equals(clientDescription)) {
					// Set our handler as the HprofDumpHandler
					monitor.setTaskName("Dumping HPROF file");
					client.dumpHprof();
					long duration = MAX_WAIT_TIME;
					long ticks = System.currentTimeMillis();
					synchronized (hprofDataMap) {
						do {
							hprofDataMap.wait(duration);
							long now = System.currentTimeMillis();
							duration -= now - ticks;
							ticks = now;
						} while (!hprofDataMap.containsKey(client) && (duration > 100L));
					}
					HprofData hprofData = hprofDataMap.get(client);
					if (hprofData == null) {
						hprofHandler.onEndFailure(client, null);
						status = Status.CANCEL_STATUS;
					}
					else {
						if (hprofData.type == HprofData.Type.DATA)
							hprofHandler.onSuccess(hprofData.data, client);
						else
							hprofHandler.onSuccess(hprofData.filename, client);
					}
					monitor.worked(50);
				}
			}
		} catch (Exception e) {
			// Status not ok
			status = Status.CANCEL_STATUS;
		} finally {
			monitor.done();
		}
		return status;
	}
*/
	@Override
	public void clientChanged(Client client, int changeMask) {
		if (((changeMask & Client.CHANGE_NAME) == Client.CHANGE_NAME)  ||
			((changeMask & Client.CHANGE_DEBUGGER_STATUS) == Client.CHANGE_DEBUGGER_STATUS)) {
			devices.clientChange(client, changeMask);
		}
		
		/*
		if ((changeMask & Client.CHANGE_HPROF) == Client.CHANGE_HPROF) {
			hprofDataMap.put(client, client.getClientData().getHprofData());
			synchronized(hprofDataMap) {
				hprofDataMap.notifyAll();
			}
		}
		*/
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.android.ddmlib.AndroidDebugBridge.IDeviceChangeListener#deviceChanged
	 * (com.android.ddmlib.Device, int)
	 */
	@Override
	public void deviceChanged(IDevice device, int i) {
		if (i == IDevice.CHANGE_STATE) {
			if (logger.isLoggable(Level.INFO))
				logger.info("Device changed: " + device.getSerialNumber() + " "  + getStateString(device)); 
			// Ensure device is registered as connected
			if ((device.getState() == DeviceState.ONLINE) && !connectedDevices.containsKey(device.getSerialNumber())) {
				deviceConnected(device);
			}
			devices.deviceStatusChanged(device);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.android.ddmlib.AndroidDebugBridge.IDeviceChangeListener#deviceConnected
	 * (com.android.ddmlib.Device)
	 */
	@Override
	public void deviceConnected(IDevice device) {
		final String serialNumber = device.getSerialNumber();
		if (logger.isLoggable(Level.FINEST))
			logger.verbose("Device connected: " + serialNumber); //$NON-NLS-1$
		connectedDevices.put(serialNumber, device);
		completeDeviceConnection(device);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.android.ddmlib.AndroidDebugBridge.IDeviceChangeListener#
	 * deviceDisconnected(com.android.ddmlib.Device)
	 */
	@Override
	public void deviceDisconnected(IDevice device) {
		final String serialNumber = device.getSerialNumber();
		if (logger.isLoggable(Level.FINEST))
			logger.verbose("Device disconnected: " + serialNumber); //$NON-NLS-1$
		synchronized (completelyUpDevices) {
			completelyUpDevices.remove(serialNumber);
		}
		if (connectedDevices.remove(serialNumber) != null) {
			devices.deviceDisconnected(device);
			Job job = new Job("Notify device " + serialNumber + " disconnected"){
	
				@Override
				protected IStatus run(IProgressMonitor arg0) {
					// Fire events in worker thread to prevent blocking caller thread
					//AndmoreEventManager.fireEvent(EventType.DEVICE_DISCONNECTED, serialNumber);
					synchronized (avdNameMap) {
						avdNameMap.remove(device.getSerialNumber());
					}
					return Status.OK_STATUS;
				}};
			job.setPriority(Job.BUILD);
			job.schedule();
		}
	}

	/**
	 * Get the service used to transfer files to the Device
	 * @param device Device
	 * @param timeout timeout for the operation
	 * @param monitor monitor associated with the operation
	 * @return The service used to transfer files to the Device
	 * @throws AndworxException
	 */
	private SyncService getSyncService(IDevice device, int timeout, final IProgressMonitor monitor)
			throws AndworxException {

		SyncService service = null;
		long timeoutLimit = System.currentTimeMillis() + timeout;
		do {
			if ((device != null) && (device.isOnline())) {
				try {
					service = device.getSyncService();
				} catch (IOException e) {
					if (logger.isLoggable(Level.FINEST))
						logger.verbose("Couldn't get sync service; cause: " + e.getMessage()); //$NON-NLS-1$
				} catch (com.android.ddmlib.TimeoutException e) {
					if (logger.isLoggable(Level.FINEST))
						logger.verbose("Couldn't get sync service; cause: " + e.getMessage()); //$NON-NLS-1$
				} catch (AdbCommandRejectedException e) {
					if (logger.isLoggable(Level.FINEST))
						logger.verbose("Couldn't get sync service; cause: " + e.getMessage()); //$NON-NLS-1$
				}
			}

			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				// do nothing
			}

			if (monitor.isCanceled()) {
				if (logger.isLoggable(Level.INFO))
					logger.info("Operation canceled by the user"); //$NON-NLS-1$
				return null;
			}

			if (System.currentTimeMillis() > timeoutLimit) {
				logger.error(null, "The emulator was not up within the set timeout"); //$NON-NLS-1$
				throw new AndworxException("Timeout while preparing to transfer files to the Device. " + device); //$NON-NLS-1$
			}
		} while (service == null);

		return service;
	}

	private IStatus transferFiles(boolean isPush, String serialNumber, String localDir,
			Collection<String> fileNames, String remoteDir, int timeout, final IProgressMonitor monitor,
			OutputStream outputStream) {
		List<File> localList = new ArrayList<File>();
		List<String> remoteList = new ArrayList<String>();
		for (String name : fileNames) {
			localList.add(new File(localDir, name));
			remoteList.add(remoteDir + "/" + name); //$NON-NLS-1$
		}
		return transferFiles(isPush, serialNumber, localList, remoteList, timeout, monitor, outputStream);
	}

	private IStatus transferFiles(boolean isPush, String serialNumber, List<File> localFiles,
			List<String> remoteFiles, int timeout, final IProgressMonitor monitor, OutputStream outputStream) {
		if (localFiles.size() != remoteFiles.size()) {
			return new Status(IStatus.ERROR, PLUGIN_ID, "The file lists provided for push or pull operations differ in size. The operation is being aborted.");
		}

		IStatus status = Status.OK_STATUS;
		IDevice device = getDeviceBySerialNumber(serialNumber);

		SyncService service = null;
		try {
			service = getSyncService(device, timeout, monitor);
			if (service == null) {
				status = Status.CANCEL_STATUS;
			} else {
				final ISyncProgressMonitor syncMonitor = new ISyncProgressMonitor() {
					@Override
					public void start(int i) {
						// do nothing
					}

					@Override
					public void stop() {
						// do nothing
					}

					@Override
					public void advance(int i) {
						// do nothing
					}

					@Override
					public boolean isCanceled() {
						return monitor.isCanceled();
					}

					@Override
					public void startSubTask(String s) {
						// do nothing
					}
				};

				FileListingService flService = device.getFileListingService();

				for (int i = 0; i < localFiles.size(); i++) {
					File localFile = localFiles.get(i);
					String remotePath = remoteFiles.get(i);
					String absLocalFile = localFile.getAbsolutePath();

					String resultMessage = null;
					if (isPush) {
						if (logger.isLoggable(Level.FINEST))
							logger.verbose("Push " + absLocalFile + " to " + remotePath); //$NON-NLS-1$ //$NON-NLS-2$
						try {
							service.pushFile(absLocalFile, remotePath, syncMonitor);
						} catch (SyncException e1) {
							if (logger.isLoggable(Level.FINEST))
								logger.verbose("Push result: " + "SyncException occured " + e1.getMessage()); //$NON-NLS-1$ //$NON-NLS-2$
							resultMessage = String.format(CONSOLE_PUSH, absLocalFile, remotePath)
									+ ": " + e1.getLocalizedMessage(); //$NON-NLS-1$
						} catch (FileNotFoundException e1) {
							if (logger.isLoggable(Level.FINEST))
								logger.verbose("Push result: " + "FileNotFoundException occured " //$NON-NLS-1$ //$NON-NLS-2$
									+ e1.getMessage());
							resultMessage = String.format(CONSOLE_PUSH, absLocalFile, remotePath)
									+ ": " + e1.getLocalizedMessage(); //$NON-NLS-1$
						} catch (IOException e1) {
							if (logger.isLoggable(Level.FINEST))
								logger.verbose("Push result: " + "IOException occured " + e1.getMessage()); //$NON-NLS-1$ //$NON-NLS-2$
							resultMessage = String.format(CONSOLE_PUSH, absLocalFile, remotePath)
									+ ": " + e1.getLocalizedMessage(); //$NON-NLS-1$
						} catch (com.android.ddmlib.TimeoutException e1) {
							if (logger.isLoggable(Level.FINEST))
								logger.verbose("Push result: " + "TimeoutException occured " + e1.getMessage()); //$NON-NLS-1$ //$NON-NLS-2$
							resultMessage = String.format(CONSOLE_PUSH, absLocalFile, remotePath)
									+ ": " + e1.getLocalizedMessage(); //$NON-NLS-1$
						}

						if ((outputStream != null) && (resultMessage != null)) {
							try {
								outputStream.write(resultMessage.getBytes());
								outputStream.write('\n');
								outputStream.flush();
							} catch (Exception e) {
								// Do nothing
							}
						}

					} else {
						FileEntry f1 = null;
						FileEntry f2 = null;

						f2 = flService.getRoot();
						flService.getChildren(f2, false, null);
						String[] dirs = remotePath.split("/"); //$NON-NLS-1$

						for (int j = 1; j < (dirs.length - 1); j++) {
							f1 = f2.findChild(dirs[j]);
							flService.getChildren(f1, false, null);
							f2 = f1;
						}

						final FileEntry fileToPull = f2.findChild(dirs[dirs.length - 1]);

						if (fileToPull != null) {
							try {
								service.pullFile(fileToPull, absLocalFile, syncMonitor);
							} catch (FileNotFoundException e) {
								resultMessage = e.getLocalizedMessage();
								if (logger.isLoggable(Level.FINEST))
									logger.verbose("Pull result: " + e.getMessage()); //$NON-NLS-1$
							} catch (SyncException e) {
								resultMessage = e.getLocalizedMessage();
								if (logger.isLoggable(Level.FINEST))
									logger.verbose("Pull result: " + e.getMessage()); //$NON-NLS-1$
							} catch (IOException e) {
								resultMessage = e.getLocalizedMessage();
								if (logger.isLoggable(Level.FINEST))
									logger.verbose("Pull result: " + e.getMessage()); //$NON-NLS-1$
							} catch (com.android.ddmlib.TimeoutException e) {
								resultMessage = e.getLocalizedMessage();
								if (logger.isLoggable(Level.FINEST))
									logger.verbose("Pull result: " + e.getMessage()); //$NON-NLS-1$
							}

							if ((outputStream != null) && (resultMessage != null)) {
								String message = String.format("Pull %s to %s", fileToPull.getFullPath(),
										absLocalFile) + ": " + resultMessage; //$NON-NLS-1$
								try {
									outputStream.write(message.getBytes());
									outputStream.write('\n');
									outputStream.flush();
								} catch (IOException e) {
									// do nothing
								}
							}
						} else {
							resultMessage = String.format("Remote file not found: %s", remotePath);
							if (logger.isLoggable(Level.FINEST))
								logger.verbose("Pull result: File not found " + remotePath); //$NON-NLS-1$
						}
					}

					if (resultMessage != null) {
						status = new Status(IStatus.ERROR, PLUGIN_ID, resultMessage);
					}
					if (syncMonitor.isCanceled()) {
						status = Status.CANCEL_STATUS;
						break;
					}
				}
			}
		} catch (AndworxException e) {
			status = new Status(IStatus.ERROR, PLUGIN_ID, e.getMessage());
		} catch (NullPointerException e1) {
			status = new Status(IStatus.ERROR, PLUGIN_ID, "File not found");
		} finally {
			if (service != null) {
				service.close();
			}
		}
		return status;
	}

	/**
	 * See http://www.javaworld.com/javaworld/jw-12-2000/jw-1229-traps.html to
	 * understand how Process.exec works and its problems
	 * 
	 * @param cmd
	 *            Command to be executed.
	 * @param out
	 *            Output Stream.
	 * @param timeout
	 *            Timeout (secs.)
	 * @param monitor
	 *            {@link IProgressMonitor}
	 * 
	 * @return the {@link IStatus} of this process execution.
	 * 
	 * @throws IOException
	 *             Exception thrown in case there is any problem executing the
	 *             command.
	 */
	private IStatus executeRemoteDevicesCommand(String[] cmd, OutputStream out, int timeout, String timeoutMsg,
			IStopCondition stopCondition, IProgressMonitor monitor) throws IOException {

		IStatus status = Status.OK_STATUS;
		long timeoutLimit = -1;
		if (timeout != 0) {
			timeoutLimit = System.currentTimeMillis() + (timeout * 1000);
		}

		String fullCmd = ""; //$NON-NLS-1$
		for (String cmdArg : cmd) {
			fullCmd += cmdArg + " "; //$NON-NLS-1$
		}
		if (out != null) {
			out.write(fullCmd.getBytes());
			out.write("\n".getBytes()); //$NON-NLS-1$
		}
		Runtime r = Runtime.getRuntime();
		Process p = r.exec(cmd);

		int errorCode = 0;

		// inputStream / errorStream;
		String[] commandResults = new String[] { "", "" //$NON-NLS-1$ //$NON-NLS-2$
		};

		commandResults = readCmdOutputFromStreams(commandResults[0], commandResults[1], p.getInputStream(),
				p.getErrorStream(), out);

		while (!stopCondition.canStop()) {
			if ((monitor != null) && (monitor.isCanceled())) {
				p.destroy();
				return Status.CANCEL_STATUS;
			}

			try {
				errorCode = p.exitValue();
				if (errorCode != 0) {
					break;
				}

			} catch (IllegalThreadStateException e) {
				// Process is still running... Proceed with loop
			}

			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				logger.error(null, "Execute command: thread has been interrupted"); //$NON-NLS-1$
			}

			if (timeout > 0) {
				try {
					testTimeout(timeoutLimit, ((timeoutMsg != null) ? timeoutMsg :"Timeout has been reached"));
				} catch (TimeoutException e) {
					p.destroy();
					if (logger.isLoggable(Level.FINEST))
						logger.verbose("The timeout " + timeout //$NON-NLS-1$
							+ " has been reached when executing the command " + fullCmd); //$NON-NLS-1$
					return new Status(IStatus.ERROR, PLUGIN_ID, e.getMessage(), e);
				}
			}

		}
		commandResults = readCmdOutputFromStreams(commandResults[0], commandResults[1], p.getInputStream(),
				p.getErrorStream(), out);

		if (errorCode != 0) {
			if (logger.isLoggable(Level.FINEST))
				logger.verbose("Command " + cmd + " returned an error code: " + errorCode); //$NON-NLS-1$ //$NON-NLS-2$
			status = new Status(IStatus.ERROR, PLUGIN_ID,
					String.format("The command stopped with error code: %s", errorCode) + "\n" //$NON-NLS-1$
							+ ((!commandResults[1].equals("")) ? commandResults[1] //$NON-NLS-1$
									: commandResults[0]));
		} else {
			status = new Status(IStatus.OK, PLUGIN_ID, commandResults[0]);
		}
		return status;
	}

	/**
	 * @param commandResults
	 * @param errorResults
	 * @param inputStream
	 * @param errorStream
	 * @param out
	 */
	private String[] readCmdOutputFromStreams(String commandResults, String errorResults,
			InputStream inputStream, InputStream errorStream, OutputStream out) {
		String[] results = new String[2];
		String line = ""; //$NON-NLS-1$

		BufferedReader brInput = new BufferedReader(new InputStreamReader(inputStream));
		BufferedReader brError = new BufferedReader(new InputStreamReader(errorStream));

		try {

			// input stream
			if (brInput.ready()) {
				while ((line = brInput.readLine()) != null) {
					commandResults += line;
					commandResults += "\n"; //$NON-NLS-1$
					if (out != null) {
						out.write(line.getBytes());
						out.write("\n".getBytes()); //$NON-NLS-1$
					}
				}
			}

			// error stream
			if (brError.ready()) {
				while ((line = brError.readLine()) != null) {
					errorResults += "\n"; //$NON-NLS-1$
					if (out != null) {
						out.write(line.getBytes());
						out.write("\n".getBytes()); //$NON-NLS-1$
					}
				}
			}
		} catch (IOException e) {
			logger.error(null, "Cannot read command outputs"); //$NON-NLS-1$
		} finally {
			try {
				brInput.close();
				brError.close();
			} catch (IOException e) {
				logger.error(null, "Could not close console stream: " + e.getMessage());
			}
		}
		results[0] = commandResults;
		results[1] = errorResults;
		return results;

	}

	/**
	 * Checks if the timeout limit has reached.
	 * 
	 * @param timeoutLimit
	 *            The system time limit that cannot be overtaken, in
	 *            milliseconds.
	 * @throws StartTimeoutException
	 *             When the system time limit is overtaken.
	 */
	private void testTimeout(long timeoutLimit, String timeoutErrorMessage) throws TimeoutException {
		if (System.currentTimeMillis() > timeoutLimit) {
			throw new TimeoutException(timeoutErrorMessage);
		}
	}

	private File getSdkPath() {
		AndroidEnvironment androidEnv = objectFactory.getAndroidEnvironment();
		if (!androidEnv.isValid())
			throw new AndworxException(SdkProfile.SDK_NOT_AVAILABLE_ERROR);
		return androidEnv.getAndroidSdkHandler().getLocation();
	}
	
	/**
	 * Uses the ADB shell command to remove a file from the device
	 * @param serialNumber
	 * @param fileName
	 * @param sdCardFolder
	 * @return
	 * @throws IOException
	 */
	private boolean deleteFileFromDevice(String serialNumber, String fileName, String folder) throws IOException {

		String command[] = createDeleteFileFromDeviceCommand(serialNumber, fileName, folder);
		IStatus status = executeRemoteDevicesCommand(command, null, 1000, "", new CanStopCondition(), null);
		return status.isOK();
	}

	/**
	 * Uses the ADB shell command to poll package manager
	 * @param serialNumber
	 * @param fileName
	 * @param sdCardFolder
	 * @return
	 * @throws IOException
	 */
	private boolean isPackageManagerReady(String serialNumber) throws ProcessException {

		IDevice device = getDeviceBySerialNumber(serialNumber);
		if (device != null) {
			String response = null;
			try {
				response = executeShellCommand(device, "getprop dev.bootcomplete", false);
			} catch (AdbCommandRejectedException e) {
				// Fail quietly as device may not be ready
			} catch (ProcessException e) {
				e.printStackTrace();
				return false;
			}
	        if ((response != null) && !response.startsWith("1"))
	        	return false;
	        response = null;
			try {
	            response = executeShellCommand(device, "pm get-max-users", false);
			} catch (AdbCommandRejectedException e) {
				// Fail quietly as device may not be ready
			} catch (ProcessException e) {
				if (logger.isLoggable(Level.WARNING)) {
					Throwable cause = e.getCause();
					logger.warning("Shell execute error on device %s\n%s\n%s", serialNumber, cause.getMessage(), Throwables.getStackTraceAsString(cause));
				}
			}
	        return (response != null) && response.startsWith("Maximum supported users:");
		}
		return false;
	}

	/**
	 * 
	 * @param serialNumber
	 * @param sdCardFolder
	 * @param tempFile
	 * @return true if manages to push file into the folder specified on device
	 */
	private boolean pushFileToDevice(String serialNumber, String folder, File file) {
		Collection<String> files = new ArrayList<String>();
		files.add(file.getName());
		Path path = new Path(file.getAbsolutePath());

		IStatus status = pushFiles(serialNumber, path.removeLastSegments(1).toString(), files, folder, 2000,
				new NullProgressMonitor(), null);
		return status.isOK();
	}

	/**
	 * Wait for device to go online
	 * @param device
	 * @param serialNumber
	 */
	private void completeDeviceConnection(IDevice device) {
		final String serialNumber = device.getSerialNumber();
		if (!device.isOnline()) {
			if (logger.isLoggable(Level.FINEST)) 
				logger.verbose("Device %s waiting to go online", serialNumber);
			Timer timer = new Timer();
	        TimerTask task = new TimerTask(){
	    		long duration = devices.getMaxStartTime() * 1000L;

				@Override
				public void run() {
					if (device.isOnline()) {
						deviceChanged(device);		
						this.cancel();
					}
					else if ((duration -= POLL_DURATION * 1000L) <= 0) {
						String message = String.format("Timeout device %s waiting to go online",serialNumber);
						devices.deviceFail(serialNumber, message);
						logger.error(null, message);
						this.cancel();
					}
				}};
				timer.scheduleAtFixedRate(task, POLL_DURATION * 1000L, POLL_DURATION * 1000L);
		}
		deviceChanged(device);	
	}

	/**
	 * Handle device state change while waiting to go online
	 * @param device
	 * @return flag set true if wait is over - either device is online or disconnected
	 */
	private boolean deviceChanged(IDevice device) {
		if (device.getState() == DeviceState.DISCONNECTED) {
			final String serialNumber = device.getSerialNumber();
			String message = String.format("Device %s disconnected ", serialNumber);
			devices.deviceFail(serialNumber, message);
			logger.error(null, message);
			return true;
		} else if (device.isOnline()) {
			if (device.isEmulator() && (device.getAvdName() == null)) {
				waitForAvdName(device);
			} else {
				waitForPackageManager(device);
			}
			return true;
		}
		return false;
	}

	/**
	 * Poll for Packaging Manager available.
	 * Pre-condition: Device is online, and if emulator, AVD name is available
	 * @param device
	 */
   	private void waitForPackageManager(IDevice device) {
		final String serialNumber = device.getSerialNumber();
		if (logger.isLoggable(Level.FINEST)) 
			logger.verbose("Device %s waiting for Package Manager", serialNumber);
		String vmName = null;
		if (device.isEmulator())
			// Note: device.getAvdName() is only valid if the device is online
			vmName = avdNameMap.computeIfAbsent(serialNumber, k -> device.getAvdName());
		else
			vmName = serialNumber;
		devices.deviceConnected(device, vmName);
		Timer timer = new Timer();
        TimerTask task = new TimerTask(){
    		long duration = devices.getMaxStartTime() * 1000L;

			@Override
			public void run() {
				try {
					if (isPackageManagerReady(serialNumber)) {
						devices.deviceReady(serialNumber);
						this.cancel();
					}
					else if ((duration -= POLL_DURATION * 1000L) <= 0) {
						String message = String.format("Timeout device %s waiting for PackageManager", serialNumber);
						devices.deviceFail(serialNumber, message);
						logger.error(null, message);
						this.cancel();
					}
				} catch (ProcessException e) {
					String message = String.format("Device %s shell execute error", serialNumber);
					devices.deviceFail(serialNumber, message);
					logger.error(e, message);
					this.cancel();
				}
			}};
		timer.scheduleAtFixedRate(task, POLL_DURATION * 1000L, POLL_DURATION * 1000L);
	}

  	/**
   	 * Poll for emulator AVD name available.
   	 * Pre-condition: device is online
   	 * @param device
   	 */
   	private void waitForAvdName(IDevice device) {
		final String serialNumber = device.getSerialNumber();
		if (logger.isLoggable(Level.FINEST)) 
			logger.verbose("Device %s waiting for AVD name", serialNumber);
		Timer timer = new Timer();
        TimerTask task = new TimerTask(){
    		long duration = devices.getMaxStartTime() * 1000L;

			@Override
			public void run() {
				if (device.getAvdName() != null) {
					waitForPackageManager(device);
					this.cancel();
				}
				else if ((duration -= POLL_DURATION * 1000L) <= 0) {
					String message = String.format("Timeout waiting for device %s to get AVD name", serialNumber);
					devices.deviceFail(serialNumber, message);
					logger.error(null, message);
					this.cancel();
				}
			}};
		timer.scheduleAtFixedRate(task, POLL_DURATION * 1000L, POLL_DURATION * 1000L);
	}
	
	/**
	 * Get the Device associated with the given serial number
	 * @param serialNumber Serial number of the device to retrieve
	 * @return Device associated with the given serial number
	 */
	private IDevice getDeviceBySerialNumber(String serialNumber) {
		if (serialNumber == null) {
			logger.error(null, String.format(NULL_SERIAL_NUMBER, "getDeviceBySerialNumber"));
			return null;
		}
		return connectedDevices.get(serialNumber);
	}

	/**
	 * Return true if the Device is being shown on the OFFLINE state.
	 * @param serialNumber Device�s serial number.
	 * 
	 * @return <code>true</code> in case the Device if offline,
	 *         <code>false</code> otherwise.
	 */
	private boolean isDeviceOffline(String serialNumber) {
		if (serialNumber == null) {
			logger.error(null, String.format(NULL_SERIAL_NUMBER, "isDeviceOffline"));
			return true;
		}
		IDevice device = getDeviceBySerialNumber(serialNumber);
		return ((device == null) || ((device != null) && device.isOffline()));
	}

	/**
	 * Creates a string with the command that should be called in order to
	 * connect to an IP/Port
	 * @param host device host (IP)
	 * @param port device port
	 * @return the command to be used to connect to an IP/Port
	 */
	private String[] createConnectTcpIpCommand(String host, String port) {
		String hostPort = host + ":" + port; //$NON-NLS-1$
		String cmd[] = { getSdkPath() + PLATFORM_TOOLS_FOLDER + File.separator + ADB_COMMAND, CONNECT_TCPIP_CMD, hostPort };
		return cmd;
	}

	/**
	 * Creates a string with the command switches a device from the TCP/IP
	 * connection mode to the USB connection mode.
	 * @param host  Device host (IP).
	 * @param port Device port.
	 * @return The command to be used to switch back to USB connection mode.
	 */
	private String[] createSwitchToUSBConnectionModeCommand(String host, String port) {
		String hostPort = host + ":" + port; //$NON-NLS-1$
		String cmd[] = { getSdkPath() + PLATFORM_TOOLS_FOLDER + File.separator + ADB_COMMAND, DEVICE_ID_INDICATOR, hostPort,
				USB_SWITCH_BACK_COMMAND };
		return cmd;
	}

	/**
	 * Creates a string with the command that should be called in order to
	 * switch adb connection from USB to TPCIP mode
	 * @param serialNumber device serial number
	 * @param port device port
	 * @return the command to be used to switch adb connection to TCPIP mode
	 */
	private String[] createSwitchToTcpIpCommand(String serialNumber, String port) {
		String cmd[] = { getSdkPath() + PLATFORM_TOOLS_FOLDER + File.separator + ADB_COMMAND, ADB_INSTANCE_PARAMETER,
				serialNumber, TCPIP_CMD, port };
		return cmd;
	}

	/**
	 * Creates a string with the command that should be called to delete a file
	 * from device
	 * @param serialNumber
	 * @param file
	 * @param folder
	 * @return
	 */
	private String[] createDeleteFileFromDeviceCommand(String serialNumber, String file, String folder) {
		// Paranoid check that the tools folder exists
		File sdkPath = getSdkPath();
		File platformToolsFolder = new File(sdkPath + PLATFORM_TOOLS_FOLDER + File.separator);
		if (!platformToolsFolder.exists()) 
			logger.error(null, "Run: Could not find tools folder on " + platformToolsFolder.getAbsolutePath());
		else if (!platformToolsFolder.isDirectory()) 
			logger.error(null, "Run: Invalid tools folder " + platformToolsFolder.getAbsolutePath());
		String cmd[] = { platformToolsFolder.getAbsolutePath() + File.separator + ADB_COMMAND, ADB_INSTANCE_PARAMETER,
				serialNumber, SHELL_CMD, "rm /" + folder + "/" + file
		};
		return cmd;
	}

	/**
	 * Creates a string with the command that should be called in order to
	 * disconnect from an IP/Port
	 * @param host device host (IP)
	 * @param port device port
	 * @return the command to be used to disconnect from an IP/Port
	 */
	private String[] createDisconnectTcpIpCommand(String host, String port) {
		String hostPort = host + ":" + port; //$NON-NLS-1$
		String cmd[] = { getSdkPath() + PLATFORM_TOOLS_FOLDER + File.separator + ADB_COMMAND, DISCONNECT_TCPIP_CMD, hostPort };
		return cmd;
	}

	/**
	 * Execute shell command using default timeout
	 * @param device
	 * @param command
	 * @param rootRequired
	 * @return command response
	 * @throws ProcessException if either ShellCommandUnresponsiveException, IOException, InterruptedException or TimeoutException occurs
	 * @throws AdbCommandRejectedException likey device not ready - try again later
	 */
    @NonNull
    private String executeShellCommand(
            @NonNull IDevice device, @NonNull String command, boolean rootRequired)
            throws ProcessException, AdbCommandRejectedException {
        return executeShellCommand(device, command, rootRequired, SHELL_TIMEOUT);
    }

    /**
     * Exexcute shell command which is expected to return multi-line response
     * @param serialNumber Device serial number
     * @param command
     * @param timeout
     * @param monitor
     * @return String collection
     */
	private Collection<String> executeShellCmd(String serialNumber, final String command, long timeout,
			final IProgressMonitor monitor) {
	    final CountDownLatch latch = new CountDownLatch(1);
	  	final Collection<String> results = new ArrayList<String>();
		IDevice device = getDeviceBySerialNumber(serialNumber);
		if (device != null) {
			try {
				device.executeShellCommand(command, new MultiLineReceiver() {
					@Override
					public boolean isCancelled() {
						return monitor.isCanceled();
					}

					@Override
					public void processNewLines(String[] lines) {
						for (String line : lines) {
							if ((!line.equals("")) && (!line.equals(command))) //$NON-NLS-1$
							{
								results.add(line);
							}
						}
					}
				    /**
				     * Terminates the process. This is called after the last lines have been through {@link
				     * #processNewLines(String[])}.
				     */
					@Override
				    public void done() {
			            latch.countDown();
				    }
				});
	            if (!latch.await(timeout, TimeUnit.MILLISECONDS)) {
	            	logger.error(null, "Failed executing shell command \"" + command + "\".");
	            }
			} catch (Exception e) {
				logger.error(e, "Error executing shell command " + command //$NON-NLS-1$
						+ " at device " + serialNumber, e); //$NON-NLS-1$
			}
		}
		return results;
	}

	/**
	 * Execute shell command
	 * @param device
	 * @param command
	 * @param rootRequired
	 * @param timeout
	 * @return command response
	 * @throws ProcessException if either ShellCommandUnresponsiveException, IOException, InterruptedException or TimeoutException occurs
	 * @throws AdbCommandRejectedException likey device not ready - try again later
	 */
    private static String executeShellCommand(
            @NonNull IDevice device, @NonNull String command, boolean rootRequired, long timeout) throws ProcessException, AdbCommandRejectedException {
        CountDownLatch latch = new CountDownLatch(1);
        CollectingOutputReceiver receiver = new CollectingOutputReceiver(latch);
        try {
            if (rootRequired) {
                device.root();
            }
            device.executeShellCommand(command, receiver);
            if (!latch.await(timeout, TimeUnit.MILLISECONDS)) {
                throw new ProcessException("Failed executing command \"" + command + "\".");
            }
        } catch (ShellCommandUnresponsiveException
                | IOException
                | InterruptedException | com.android.ddmlib.TimeoutException e) {
            throw new ProcessException(
                    "Failed executing command \"" + command + "\".",
                    e);
        }
        return receiver.getOutput();
    }

    /**
     * Returns a display string representing the state of the device.
     * @param d the device
     */
    private static String getStateString(IDevice d) {
        DeviceState deviceState = d.getState();
        if (deviceState == DeviceState.ONLINE) {
            return "Online";
        } else if (deviceState == DeviceState.OFFLINE) {
            return "Offline";
        } else if (deviceState == DeviceState.BOOTLOADER) {
            return "Bootloader";
        }
        return "??";
    }

}
