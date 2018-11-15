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

package org.eclipse.andmore.sdktool.preferences;

import java.io.File;
import java.security.SecureRandom;

/**
 * Android SDK specification containing location and optional name
 * @author Andrew Bowley
 *
 * 13-12-2017
 */
public class AndroidSdk {
	private static final String BLANK = "";
	private static final String ELIPSIS = "...";
    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();

	/** Android SDK root location. */
	private File sdkLocation;
	/** Name is optional */
	private String name;
	/** Generated 8-character key used to persist these fields */
	private String key;
	/** Flag set true if this SDK is not to be displayed */
	private boolean isHidden;

	/**
	 * Construct AndroidSdk object
	 * @param sdkLocation SDK location contained in File object
	 * @param name Optional name
	 */
	public AndroidSdk(File sdkLocation, String name) {
		this(sdkLocation, name, null);
	}
	
	/**
	 * Construct AndroidSdk object with given key
	 * @param sdkLocation SDK location contained in File object
	 * @param name Optional name
	 */
	public AndroidSdk(File sdkLocation, String name, String key) {
		this.sdkLocation = sdkLocation;
		this.name = name != null ? name : BLANK;
		this.key = key;
	}
	
	/**
	 * Copy constructor
	 * @param androidSdk AndroidSdk object to copy
	 * @param name Optional name
	 */
	public AndroidSdk(AndroidSdk androidSdk) {
		this(androidSdk.getSdkLocation(), androidSdk.getName(), androidSdk.getKey());
		isHidden = androidSdk.isHidden;
	}
	
	public void setSdkLocation(File sdkLocation) {
		this.sdkLocation = sdkLocation;
	}
	
	public File getSdkLocation() {
		return sdkLocation;
	}
	
	public void setName(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	/** 
	 * Returns SDK description containing name and location, trucated if exceeding certain number of segments
	 * @return
	 */
	public String getDescription() {
		return name.isEmpty() ? 
				getTruncatedLocation() : 
				String.format("%s in %s", name, getTruncatedLocation());
	}

	/**
	 * Return SDK description containing name and location 
	 * @return
	 */
	public String getDetails() {
		return name.isEmpty() ? 
				sdkLocation.getAbsolutePath() :
		        String.format("%s in %s", name, sdkLocation.getAbsolutePath());
	}
	
	/**
	 * @return the key
	 */
	public String getKey() {
		return key;
	}

	/**
	 * Sets key if not set
	 * @return the key
	 */
	public String setKey() {
		if (key == null)
			key = generateKey();
		return key;
	}

	/**
	 * @return flage set true if is hidden
	 */
	public boolean isHidden() {
		return isHidden;
	}

	/**
	 * @param isHidden Flag to set
	 */
	public void setHidden(boolean isHidden) {
		this.isHidden = isHidden;
	}

	/**
	 * Returns SDK location shortened with elipsis if it contains more than 5 segments
	 * @return String
	 */
	public String getTruncatedLocation() {
		String path = sdkLocation.getAbsolutePath();
		String[] segments = splitPath(path); 
		// Return no more 5 segments using elipsis to shorten
		if (segments.length <= 5)
			return path;
		StringBuilder builder = new StringBuilder(segments[0]);
		builder.append(File.separator).append(ELIPSIS);
		int pos = segments.length - 6;
		while (pos < segments.length)
			builder.append(File.separator).append(segments[pos--]);
		return builder.toString();
	}

	@Override
	public int hashCode() {
		return name.hashCode() ^ sdkLocation.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if ((obj == null) || !(obj instanceof AndroidSdk))
			return false;
		AndroidSdk other = (AndroidSdk)obj;
		return name.equals(other.name) && sdkLocation.equals(other.sdkLocation);
	}

	/**
	 * Returns path split in segments
	 * @param path The path to split
	 * @return String array
	 */
	private String[] splitPath(String path) {
		// Regex requires backslash to be doubly escaped
		if (File.separator.equals("\\"))
			return path.split("\\\\");
		return path.split(File.separator);
	}

	/**
	 * Returns 8-character random key to reference a single SDK configuraion
	 * @return String
	 */
	private String generateKey() {
	      SecureRandom random = new SecureRandom();
	      byte bytes[] = new byte[4];
	      random.nextBytes(bytes);
		  return bytesToHex(bytes);
	}

    /**
     * Convert a byte array to a hex string
     * @param bytes Byte array
     * @return String
     */
	public static String bytesToHex(byte[] bytes) {
	    char[] hexChars = new char[bytes.length * 2];
	    for ( int j = 0; j < bytes.length; j++ ) {
	        int v = bytes[j] & 0xFF;
	        hexChars[j * 2] = hexArray[v >>> 4];
	        hexChars[j * 2 + 1] = hexArray[v & 0x0F];
	    }
	    return new String(hexChars);
	}
}
