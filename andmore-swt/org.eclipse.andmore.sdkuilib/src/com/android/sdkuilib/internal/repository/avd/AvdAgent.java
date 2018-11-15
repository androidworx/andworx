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
/**
 * 
 */
package com.android.sdkuilib.internal.repository.avd;

import java.util.Map;

import com.android.sdklib.AndroidVersion;
import com.android.sdklib.SdkVersionInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.sdkuilib.internal.repository.content.PackageType;

/**
 * Contains and AvdInfo object and details extracted from it suitable for application use.
 * Also contains the target assigned to the AvdInfo object to match it's Android version 
 * @author Andrew Bowley
 *
 * 15-11-2017
 */
public class AvdAgent {

	private static final String BLANK = "";
	/** The wrapped AVD Info object */
	private final AvdInfo avd;
	/** The target matching the AVD Info Android version. May be null if AVD needs repair. */
	private IAndroidTarget target;
	/** Target platform package path of AVD which needs repair */
	private String platformPath;
	/** System image package path of AVD which needs repair */
	private String systemImagePath;

	// Information extracted from AVD Info data */
	private String deviceName;
	private String deviceMfctr;
	private String path;
	private SystemImageInfo systemImageInfo;
	private String platformVersion;
	private String versionWithCodename;
	private AndroidVersion androidVersion;
	private PackageType packageType;
	private String vendor;
	private String targetDisplayName;
	private String skin;
	private String sdcard;
	private String snapshot;
	
	/**
	 * Construct an AveAgent object
	 * @param target The target linked to the AVD
	 * @param avd The AVD Info object 
	 */
	public AvdAgent(IAndroidTarget target, AvdInfo avd) {
		this.target = target;
		this.avd = avd;
		path = avd.getDataFolderPath();
		systemImageInfo = new SystemImageInfo(avd);
		vendor = BLANK;
		targetDisplayName = BLANK;
		init();
	}

	/**
	 * Construct an AveAgent object for an AVD requiring repair by the installation one or two packages
	 * @param platformPath Package path of the target platform
	 * @param systemImagePath Package path of the system image
	 * @param avd The AVD Info object 
	 */
	public AvdAgent(String platformPath, String systemImagePath, AvdInfo avd) {
		this.platformPath = platformPath;
		this.systemImagePath = systemImagePath;
		this.avd = avd;
		path = avd.getDataFolderPath();
		systemImageInfo = new SystemImageInfo(avd);
		vendor = BLANK;
		targetDisplayName = BLANK;
	}

	public AvdInfo getAvd() {
		return avd;
	}

	public IAndroidTarget getTarget()
	{
		return target;
	}
	
	public String getDeviceName() {
		return deviceName;
	}

	public String getDeviceMfctr() {
		return deviceMfctr;
	}

	public String getPath() {
		return path;
	}

	public SystemImageInfo getSystemImageInfo() {
		return systemImageInfo;
	}

	public String getPlatformVersion() {
		return platformVersion;
	}

	public String getVersionWithCodename() {
		return versionWithCodename;
	}

	public AndroidVersion getAndroidVersion() {
		return androidVersion;
	}

	public PackageType getPackageType() {
		return packageType;
	}

	public String getVendor() {
		return vendor;
	}

	/**
	 * Returns the full name of the target, possibly including vendor name 
	 * @return String
	 */
	public String getTargetFullName() {
		if (target != null)
			return target.getFullName();
		// If target not available, use suffix of platform path eg. "android-27"
		int pos = platformPath.indexOf(';');
		return platformPath.substring(pos + 1);
	}

	/**
	 * Returns the platform version as a readable string 
	 * @return String
	 */
	public String getTargetVersionName() {
		if (target == null) // No target, so leave blank
			return BLANK;
		String platform = target.getVersionName();
		// The target may return null
		return platform != null ? platform : BLANK;
	}

	/**
	 * Returns target details, which extend to 2 lines for add ons
	 * @return String
	 */
	public String getTargetDisplayName() {
		return targetDisplayName;
	}

	public String getSkin() {
		return skin;
	}

	public String getSdcard() {
		return sdcard;
	}

	public String getSnapshot() {
		return snapshot;
	}

	/**
	 * Returns a more user friendly name of the abi type
	 * @return String
	 */
	public String getPrettyAbiType() {
		return AvdInfo.getPrettyAbiType(avd);
	}

	/** 
	 * Returns target platform package path of AVD which needs repair 
	 * @return String or null if AVD is assigned a target
	 */
	public String getPlatformPath() {
		return platformPath;
	}

	/** 
	 * Returns system image package path of AVD which needs repair 
	 * @return String or null if AVD is assigned a target
	 */
	public String getSystemImagePath() {
		return systemImagePath;
	}

	/**
	 * Extract information from AVD Info object
	 */
	private void init()
	{
        deviceName = avd.getProperties().get(AvdManager.AVD_INI_DEVICE_NAME);
        deviceMfctr = avd.getProperties().get(AvdManager.AVD_INI_DEVICE_MANUFACTURER);
        if (deviceName == null) { // This is a legacy device no longer supported
        	deviceName = BLANK;
        	deviceMfctr = BLANK;
        }
        else if (deviceMfctr == null)
        	deviceMfctr = BLANK;
        // Android version of the sustem image, if available, otherwise AVD Android version
        androidVersion = systemImageInfo.getAndroidVersion();
        // User-friendly description of this version, like "Android 5.1 (Lollipop)",
        // or "Android 6.X (N) Preview".
        versionWithCodename = SdkVersionInfo
                .getVersionWithCodename(androidVersion);
        platformVersion = SdkVersionInfo.getVersionString(androidVersion.getApiLevel());
        if (platformVersion == null) // Above higest known version to Android library
        	platformVersion = androidVersion.getApiString();
        PackageType packageType = systemImageInfo.getPackageType();
        // Vendor only applies to add ons and system images
        if ((packageType == PackageType.system_images) || (packageType == PackageType.add_ons)) {
            vendor = systemImageInfo.getVendor();
        }
        if (target.isPlatform()) {
        	targetDisplayName = String.format("  API: %s", versionWithCodename);
        } else {
            targetDisplayName = 
            	String.format("Target: %s\n" +
                              "        Based on %s)", 
                              target.getFullName(), 
                              target.getParent().getFullName());
        }
        // Some AVD properties that are displayed by AVD editor
        Map<String, String> properties = avd.getProperties();
        skin = properties.get(AvdManager.AVD_INI_SKIN_NAME);
        sdcard = properties.get(AvdManager.AVD_INI_SDCARD_SIZE);
        if (sdcard == null) 
            sdcard = properties.get(AvdManager.AVD_INI_SDCARD_PATH);
        if (sdcard == null)
            sdcard = BLANK;
        snapshot = properties.get(AvdManager.AVD_INI_SNAPSHOT_PRESENT);
        if (snapshot == null) 
        	snapshot = BLANK;
	}
}
