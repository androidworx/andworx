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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.andworx.build.AndworxBuildPlugin;
import org.eclipse.andworx.build.AndworxFactory;
import org.eclipse.andworx.event.AndworxEvents;
import org.eclipse.andworx.log.SdkLogger;
import org.eclipse.andworx.registry.ProjectRegistry;
import org.eclipse.andworx.registry.ProjectState;
import org.eclipse.andworx.sdk.SdkProfile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.e4.core.services.events.IEventBroker;

import com.android.builder.model.ApiVersion;
import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;

/**
 * Creates and manages device profiles for the purpose of launching applications
 */
public class Devices {
	private static String WARN_DEVICE_INCOMPATIBLE = "The selected device instance has an API level %s higher than the project API level %s";
	private static String ERR_EMULATOR_INCOMPATIBLE = "The selected device instance is not compatible. Its Device target is %s while the Project target is %s";
	private static final String NULL_SERIAL_NUMBER = "%s called with null serial number";

	private static SdkLogger logger = SdkLogger.getLogger(Devices.class.getName());
	// Allow 3 minutea for the emulator to start
	private static final long TIMEOUT_SECS = 180L;
	
	/** Connected devices mapped by name */
	private final Map<String, IDevice> connectedDevices;
	/** Device profiles mapped by name */
	private final Map<String, DeviceProfile>  deviceProfiles;
	/** Device names mapped by serial number */
	private final Map<String, String> nameMap;
	/** Device start listeners mapped by name */
	private final Map<String, DeviceStartListener> deviceStartMap;
	/** Device start listeners mapped by name */
	private final Map<String, LaunchStartListener> launchStartMap;
	/* List of clients waiting for the debugger */
	public final List<Client> waitingDebugger;
    /** Object factory */
	private final AndworxFactory objectFactory;
    private final IEventBroker eventBroker;

	/**
	 * Construct a DeviceManager object
	 */
	public Devices(AndworxFactory objectFactory) {
		this.objectFactory = objectFactory;
		connectedDevices = new ConcurrentHashMap<>();
		deviceProfiles = new ConcurrentHashMap<>(); 
		nameMap = new ConcurrentHashMap<>();
		deviceStartMap = new ConcurrentHashMap<>();
		launchStartMap = new ConcurrentHashMap<>();
        waitingDebugger = new ArrayList<Client>();
        IEclipseContext eclipseContext = objectFactory.getEclipseContext();
        eventBroker = (IEventBroker) eclipseContext.get(IEventBroker.class.getName());
	}

	/**
	 * Returns number of seconds to wait for the emulator to start
	 * @return long 
	 */
	public long getMaxStartTime() {
		return TIMEOUT_SECS;
	}

	/**
	 * Handle device connected event
	 * @param device Device object
	 * @param key Device display name
	 */
	public void deviceConnected(IDevice device, String key) {
		if (connectedDevices.containsKey(key))
			return;
		final String serialNumber = device.getSerialNumber();
		// Store device and display name
		connectedDevices.put(serialNumber, device);
		nameMap.put(serialNumber, key);
		if (launchStartMap.containsKey(key))
			return; // Launch in progress
		// Device needs a job to complete registration when the device is ready.
		// Use a listener to notify next device event.
		LaunchStartListener deviceStartListener = new LaunchStartListener();
		deviceStartMap.put(key, deviceStartListener);
		Job launchJob = new Job("Connecting device " + key) {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
		        try { 
					synchronized(deviceStartListener) {
						try {
							if (!deviceStartListener.isStarted())
								deviceStartListener.wait();
						} catch (InterruptedException e) {
							Thread.interrupted();
						}
					}
					if (deviceStartListener.isStarted()) {
						// Create device instance and set target values
						AndroidDevice instance = new AndroidDevice(key, false, Devices.this);
						// The device supplies it's target API as a property
			            String deviceApiLevelString = device.getProperty(IDevice.PROP_BUILD_API_LEVEL);
			            int deviceApiLevel = -1;
			            try {
			                deviceApiLevel = Integer.parseInt(deviceApiLevelString);
			            } catch (NumberFormatException e) {
			                // pass, we'll keep the apiLevel value at -1.
			            }
			            if (deviceApiLevel == -1) {
			            	// Target not available. Default to highest available target fo sake of continuity.
				            SdkProfile sdkProfile = objectFactory.getSdkTracker().getSdkProfile();
				            IAndroidTarget defaultTarget = sdkProfile.getHighestTarget();
			            	deviceApiLevel = defaultTarget.getRevision();
			            	logger.warning("Device %s has no %s property. Defaulting to %s", key, IDevice.PROP_BUILD_API_LEVEL, defaultTarget.toString());
			            }
			            instance.setApiLevel(deviceApiLevel);
			            instance.setTarget(AndroidTargetHash.PLATFORM_HASH_PREFIX + deviceApiLevel);
						deviceProfiles.put(key, instance);
					}
		        } catch (Exception e) {
		        	logger.error(e, "Device %s error", key);
		        	return Status.CANCEL_STATUS;
		        }
				return Status.OK_STATUS;
			}};
			launchJob.schedule();
	}

	/**
	 * Handles device ready event, meaning it has detected PackageManager is running
	 * @param serialNumber Device serial number
	 */
	public void deviceReady(String serialNumber) {
		String name = nameMap.get(serialNumber);
		if (name != null) {
			IDevice device = connectedDevices.get(serialNumber);
			if (device != null) {
				// Post device connected event only when ready to install applications
				eventBroker.post(AndworxEvents.DEVICE_CONNECTED, device);
			}
			// Alert any device start listener of ready state
			DeviceStartListener deviceStartListener = deviceStartMap.remove(name);
			if (deviceStartListener == null)
				deviceStartListener = launchStartMap.remove(name);
			if ((deviceStartListener != null) && (device != null)) {
				deviceStartListener.onDeviceStart(device);
			}
		}
	}

	/**
	 * Handles device status change event
	 * @param device Device object
	 */
	public void deviceStatusChanged(IDevice device) {
		final String serialNumber = device.getSerialNumber();
		// Ignore if not currently connected or is starting
		if (!connectedDevices.containsKey(serialNumber))
			return;
		String instanceName = nameMap.get(serialNumber);
		if ((instanceName == null) ||  deviceStartMap.containsKey(instanceName))
			return;
		eventBroker.post(AndworxEvents.DEVICE_STATE_CHANGE, device);
	}

	/**
	 * Handles device disconnected event
	 * @param device Device object
	 */
	public void deviceDisconnected(IDevice device) {
		final String serialNumber = device.getSerialNumber();
		connectedDevices.remove(serialNumber);
		String instanceName = nameMap.get(serialNumber);
		if (instanceName != null) {
			DeviceStartListener deviceStartListener = deviceStartMap.remove(instanceName);
			if (deviceStartListener == null)
				deviceStartListener = launchStartMap.remove(instanceName);
			if (deviceStartListener != null) {
				deviceStartListener.onError("Device disconnected");
				eventBroker.post(AndworxEvents.DEVICE_DISCONNECTED, device);
			}
		}
	}

	/**
	 * Handle device fail event
	 * @param serialNumber Device serial number
	 * @param message
	 */
	public void deviceFail(String serialNumber, String message) {
		String name = nameMap.get(serialNumber);
		if (name != null) {
			DeviceStartListener deviceStartListener = deviceStartMap.remove(name);
			if (deviceStartListener == null)
				deviceStartListener = launchStartMap.remove(name);
			if (deviceStartListener != null) {
				deviceStartListener.onError(message);
			}
		}
	}

	/**
	 * Synchronously start emulator and return flag to indicate success (true) or fail (false)
	 * @param deviceProfile Emulator device profile
	 * @param deviceStartListener Device start listener to asynchronously detect device is ready
	 * @param shell Parent shell of emulator start dialog
	 * @return boolean
	 */
	public boolean startDevice(LaunchInfo launchInfo, LaunchStartListener deviceStartListener) {
		final String key = launchInfo.getDeviceProfile().getName();
		// Prepare for device to start 
		launchStartMap.put(key, deviceStartListener);
    	boolean success = false;
    	try {
		    success = launchInfo.getLauncher().startEmulator();
    		Timer timer = new Timer();
            TimerTask task = new TimerTask(){

				@Override
				public void run() {
					deviceStartListener.onTimeout(key);
				}};
			timer.schedule(task, TIMEOUT_SECS * 1000L);
    	} catch (Exception e) {
    		logger.error(e, "Start device %s failed", key);
    	}
		if (!success)
			launchStartMap.remove(key);
		return success;
		
	}
	
	/**
	 * Handles device client changed event
	 * @param client Client object
	 * @param changeMask Change flags
	 */
	public void clientChange(Client client, int changeMask) {
		String serialNumber = client.getDevice().getSerialNumber();
		String name = nameMap.get(serialNumber);
		boolean isChangeName = (changeMask & Client.CHANGE_NAME) == Client.CHANGE_NAME;
		boolean isChangeDebuggerStatus = (changeMask & Client.CHANGE_DEBUGGER_STATUS) == Client.CHANGE_DEBUGGER_STATUS;
		if (isChangeName)
			eventBroker.post(AndworxEvents.CHANGE_CLIENT_NAME, client);
		if (isChangeDebuggerStatus)
			eventBroker.post(AndworxEvents.CHANGE_DEBUGGER_STATUS, client);
		if ((name != null) && (isChangeName || isChangeDebuggerStatus)) {
			DeviceProfile deviceProfile = deviceProfiles.get(name);
			if (deviceProfile != null) {
				LaunchInfo launchInfo = deviceProfile.getLaunchInfo();
				if (launchInfo != null) {
					String applicationName = launchInfo.getApplicationName();
					if (applicationName != null) {
						if (isChangeName) {
							String clientAppName = client.getClientData().getClientDescription();
							if (clientAppName != null) {
								Client removeClient = null;
								for (Client waiting : waitingDebugger) {
									int pid = waiting.getClientData().getPid();
									if (pid == client.getClientData().getPid()) {
										client.getDebuggerListenPort();
										removeClient = waiting;
										break;
									}
								}

								if (removeClient != null) {
									waitingDebugger.remove(removeClient);
								}
							}
						} else {
							ClientData clientData = client.getClientData();
							String clientAppName = clientData.getClientDescription();
							if (clientData.getDebuggerConnectionStatus() == ClientData.DebuggerStatus.DEFAULT) {
								if (((applicationName != null))
										&& clientAppName.equals(applicationName.substring(0, applicationName.lastIndexOf(".")))) {
									client.getDebuggerListenPort();
								} else {
									waitingDebugger.add(client);
								}
							}
						}
					}
				}
			}
		}
	}
	
	/**
	 * Retrieves the first occurrence of a IAndroidEmulatorInstance with the
	 * given name provided by any framework.
	 * 
	 * @param name
	 *            of the emulator instance to be retrieved.
	 * @return reference to a IAndroidEmulatorInstance with the given name or a
	 *         null is there are no emulator instance with the given name.
	 */
	public DeviceProfile getInstanceByName(String instanceName) {
        AvdManager avdManager = objectFactory.getAvdManager();
        DeviceProfile deviceProfile = null;
        // Only get valid AVDs
		AvdInfo avdInfo = avdManager.getAvd(instanceName, true);
		if (avdInfo != null) {
			deviceProfile = deviceProfiles.computeIfAbsent(instanceName, k -> new AndroidDevice(k, true, this));
		} else {
			for (String name: nameMap.values())
				if (name.equals(instanceName)) {
					deviceProfile = deviceProfiles.computeIfAbsent(instanceName, k -> new AndroidDevice(k, false, this));
					break;
				}
		}
		if (deviceProfile.getAPILevel() == 1) {
			// New instance
			initialize((AndroidDevice)deviceProfile, avdInfo);
		}
		return deviceProfile;
	}

	/**
	 * Get all devices registered in TmL, sorted using the default comparator
	 * 
	 * @return all devices registered in TmL, sorted using the default
	 *         comparator
	 */
	public Collection<DeviceProfile> getAllDevicesSorted() {
		return getAllDevices(getDefaultComparator());
	}

	/**
	 * Get all devices registered in TmL, sorted using the comparator passed as
	 * a parameter
	 * 
	 * @param comparator
	 *            the comparator that will be used to sort the devices list
	 * @return all devices registered in TmL, sorted using the comparator passed
	 *         as a parameter
	 */
	public Collection<DeviceProfile> getAllDevices(Comparator<DeviceProfile> comparator) {
		Collection<DeviceProfile> sortedDevices = new TreeSet<DeviceProfile>(comparator);
		sortedDevices.addAll(getAllDevices());
		return sortedDevices;
	}

	/**
	 * Get all devices
	 * 
	 * @return all devices
	 */
	public Collection<DeviceProfile> getAllDevices() {
		Collection<DeviceProfile> devicesCollection = 
				new LinkedHashSet<DeviceProfile>(deviceProfiles.values());
		Set<String> existingSet = deviceProfiles.keySet();
        AvdManager avdManager = objectFactory.getAvdManager();
        for (AvdInfo avdInfo: avdManager.getValidAvds()) {
        	String key = avdInfo.getName();
        	if (!existingSet.contains(key)) {
				AndroidDevice deviceProfile = new AndroidDevice(key, true, this);
				initialize(deviceProfile, avdInfo);
				devicesCollection.add(deviceProfile);
        	}
        }
		return devicesCollection;
	}

	/**
	 * Securely get the name of the AVD associated to the given device.
	 * 
	 * @param serialNumber
	 *            The serialNumber of the device that we want the AVD name for
	 * @return the name of the AVD used by the emulator running with the given
	 *         device, or null if the vmname could be retrieved.
	 */
	public String getNameBySerialNumber(String serialNumber) {
		if (serialNumber == null) {
			logger.error(null, String.format(NULL_SERIAL_NUMBER, "getNameBySerialNumber"));
			return "";
		}
		String instanceName = nameMap.get(serialNumber);
		return instanceName;
	}

	/**
	 * Securely get the serial number of the given instance.
	 * 
	 * @param instanceName
	 *            The name of the instance we want the serial number of
	 * @return the serial number of the given instance, or <code>null</code> if
	 *         no instance with the given name is online
	 */
	public String getSerialNumberByName(String instanceName) {
		String serialNumber = null;
		if (instanceName != null) {
			// Using ConcurrentHashMap iterator is safe if used only on a single thread
			for (IDevice dev : connectedDevices.values()) {
				if (instanceName.equals(nameMap.get(dev.getSerialNumber()))) {
					serialNumber = dev.getSerialNumber();
					break;
				}
			}
		}
		return serialNumber;
	}

	public String getDeviceProperty(String serialNumber, String name) {
		IDevice device = connectedDevices.get(serialNumber);
		if (device != null)
			return device.getProperty(name);
		return null;
	}

	/**
	 * Get the Device associated with the given serial number
	 * 
	 * @param serialNumber
	 *            Serial number of the device to retrieve
	 * @return Device associated with the given serial number
	 */
	public IDevice getDeviceBySerialNumber(String serialNumber) {
		if (serialNumber == null) {
			logger.error(null, String.format(NULL_SERIAL_NUMBER, "getDeviceBySerialNumber"));
			return null;
		}
		return connectedDevices.get(serialNumber);
	}

	/**
	 * Returns flag set true if device identified by given serial number is connected
	 * @param serialNumber
	 * @return boolean
	 */
	public boolean isStarted(String serialNumber) {
		return connectedDevices.containsKey(serialNumber);
	}

	/**
	 * Returns flag set true if if device identified by given serial number is an emulator
	 * @param serialNumber
	 * @return boolean
	 */
	public boolean isEmulator(String serialNumber) {
		IDevice device = getDeviceBySerialNumber(serialNumber);
		return device != null ? device.isEmulator() : false;
	}
	
	/**
	 * Filter instances the compatible with the given project
	 * 
	 * @param project
	 *            whose compatible instances need to be retrieved
	 * @return a new collection containing only the instances that are
	 *         compatible with the given project
	 **/
	public Collection<DeviceProfile> filterInstancesByProject(
			Collection<DeviceProfile> allInstances,
			IProject project) {
		Collection<DeviceProfile> filteredInstances = new LinkedList<DeviceProfile>();

		for (DeviceProfile instance : allInstances) {
			IStatus compatible = instance.isEmulator() ? isCompatible(project, instance) : Status.OK_STATUS;
			if ((compatible.getSeverity() != IStatus.ERROR) && (compatible.getSeverity() != IStatus.CANCEL)) {
				filteredInstances.add(instance);
			}
		}
		return filteredInstances;
	}
	
	/**
	 * Check if an instanceName is compatible with some project
	 * 
	 * @param project
	 * @param instanceName
	 * @return {@link IStatus#OK} if fully compatible, {@link IStatus#WARNING}
	 *         if can be compatible and {@link IStatus#ERROR} if not compatible.
	 *         Return <code>Status.CANCEL_STATUS</code> if the instance does not exists
	 */
	public IStatus isCompatible(IProject project, DeviceProfile deviceProfile) {
		IStatus compatible = Status.CANCEL_STATUS;
		int projectAPILevel = getApiVersionNumberForProject(project);
		int minSdkVersion = getMinSdkVersion(project);
		String projectTarget = getTargetNameForProject(project);
		// if the instance is an emulator add the instance only if they have the
		// same target and at least the same APILevel
		int emulatorApi = deviceProfile.getAPILevel();
		String emulatorTarget = deviceProfile.getTarget();

		if (emulatorApi >= minSdkVersion) {
			String deviceProfileName = deviceProfile.getName();
			String deviceProfileBaseTarget = getBaseTarget(deviceProfileName);
			boolean isEmulatorTargetAPlatform = isPlatformTarget(deviceProfile);

			// if they have same target its ok
			if (emulatorTarget.equals(projectTarget)) {
				compatible = Status.OK_STATUS;
			}
			// if the emulator isn't a platform, but the base target is the
			// same as the project, everything is ok
			else if (!isEmulatorTargetAPlatform && deviceProfileBaseTarget.equals(projectTarget)) {
				compatible = Status.OK_STATUS;
			} else {
				compatible = new Status(IStatus.WARNING, AndworxBuildPlugin.PLUGIN_ID, String.format(WARN_DEVICE_INCOMPATIBLE, emulatorApi, projectAPILevel));
			}
		} else {
			compatible = new Status(IStatus.ERROR, AndworxBuildPlugin.PLUGIN_ID, String.format(ERR_EMULATOR_INCOMPATIBLE, emulatorTarget, projectTarget));
		}
		return compatible;
	}

	/**
	 * Get the api version number for a given project
	 * 
	 * @param project
	 *            : the project
	 * @return the api version number or 0 if some error occurs
	 */
	public  int getApiVersionNumberForProject(IProject project) {
		int api = 0;
		IAndroidTarget target = objectFactory.getTarget(project);
		if (target != null) {
			AndroidVersion version = target.getVersion();
			if (version != null) {
				api = version.getApiLevel();
			}
		}
		return api;
	}

	/**
	 * Returns the minimum Sdk Version for a project
	 * @param project
	 * @return int
	 */
	public int getMinSdkVersion(IProject project) {
		ProjectRegistry projectRegistry = objectFactory.getProjectRegistry();
		ProjectState projectState = projectRegistry.getProjectState(project);
		return projectState.getAndworxProject().getDefaultConfig().getProductFlavor().getMinSdkVersion().getApiLevel();
	}

	/**
	 * Return the minimum API version for a project
	 * @param project
	 * @return ApiVersion object
	 */
	public ApiVersion getMinAndroidVersion(IProject project) {
		ProjectRegistry projectRegistry = objectFactory.getProjectRegistry();
		ProjectState projectState = projectRegistry.getProjectState(project);
		return projectState.getAndworxProject().getDefaultConfig().getProductFlavor().getMinSdkVersion();
	}

	/**
	 * Return project's target name
	 * @param project
	 * @return name
	 */
	public  String getTargetNameForProject(IProject project) {
		IAndroidTarget target = objectFactory.getTarget(project);
		return target != null ? target.getName() : "";
	}

	/**
	 * Returns top-most ancestor target of AVD identified by name
	 * @param name
	 * @return
	 */
	public  String getBaseTarget(String name) {
		AvdInfo vmInfo = getValidVm(name);
		return vmInfo != null ? getBaseTarget(vmInfo) : "?";
	}
	
	/**
	 * Returns top-most ancestor target of AVD
	 * @param vmInfo AVD information
	 * @return name
	 */
	public  String getBaseTarget(AvdInfo vmInfo) {
		IAndroidTarget target = objectFactory.getAndroidTargetFor(vmInfo);
		while (!target.isPlatform()) {
			target = target.getParent();
		}
		return target.getName();
	}

	/**
	 * Returns AVD information for AVD identified by name.
	 * @param vmName AVDM name
	 */
	public  AvdInfo getValidVm(String vmName) {
		AvdInfo vmInfo = null;
		AvdManager vmManager = objectFactory.getAvdManager();
		if (vmManager != null) {
			vmInfo = vmManager.getAvd(vmName, true);
		}
		return vmInfo;
	}

	/**
	 * Returns flag set true if given device has a platform target
	 * @param deviceProfile
	 * @return
	 */
	public  boolean isPlatformTarget(DeviceProfile deviceProfile) {
		if (!deviceProfile.isEmulator())
			return true;
		boolean isPlatformTarget = false;
		AvdInfo advInfo = getValidVm(deviceProfile.getName());
		if (advInfo != null) {
			IAndroidTarget target = objectFactory.getAndroidTargetFor(advInfo);
			if (target != null)
				isPlatformTarget = target.isPlatform();
		}
		return isPlatformTarget;
	}

	/**
	 * The default comparator to be used to sort IAndroidEmulatorInstance instances It
	 * considers if the devices are online and after that it compares their
	 * names Online devices shall be placed in the beginning of the list
	 * 
	 * @return a Comparator instance
	 */
	public static Comparator<DeviceProfile> getDefaultComparator() {
		return new Comparator<DeviceProfile>() {
			@Override
			public int compare(DeviceProfile instance1, DeviceProfile instance2) {
				int compareResult;

				String name1 = instance1.getName();
				String name2 = instance2.getName();
				boolean dev1online = instance1.isStarted();
				boolean dev2online = instance2.isStarted();

				if ((dev1online && dev2online) || (!dev1online && !dev2online)) {
					compareResult = name1.compareToIgnoreCase(name2);
				} else if (dev1online) {
					compareResult = -1;
				} else
				// dev2online
				{
					compareResult = 1;
				}
				return compareResult;
			}
		};
	}

	/**
	 * Set API level and target for newly created device 
	 * @param newInstance Android device
	 * @param avdInfo AVD information
	 */
	private void initialize(AndroidDevice newInstance, AvdInfo avdInfo) {
		AndroidVersion androidVersion = avdInfo.getSystemImage().getAndroidVersion();
		newInstance.setApiLevel(androidVersion.getApiLevel());
		newInstance.setTarget(getBaseTarget(avdInfo));
	}
}
