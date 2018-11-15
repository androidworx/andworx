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
package org.eclipse.andmore.sdktool;

import org.eclipse.andmore.base.resources.IEditorIconFactory;
import org.eclipse.andmore.base.resources.ImageFactory;
import org.eclipse.swt.graphics.Image;

import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.utils.ILogger;

/**
 * SdkCallAgent mediates between application and UI layer
 * @author Andrew Bowley
 *
 */
public class SdkCallAgent {
	public static final int NO_TOOLS_MSG = 0;
	public static final int TOOLS_MSG_UPDATED_FROM_ADT = 1;
	public static final int TOOLS_MSG_UPDATED_FROM_SDKMAN = 2;

	/** SDK context */
	private final SdkContext sdkContext;
	/** Persistent logger */
	private final ILogger consoleLogger;
	/** Factory to generate icons for Android Editors (optional) */
	private IEditorIconFactory iconEditorFactory;

	/**
	 * Construct SdkCallAgent object to mediate between application and UI layer
     * @param sdkHandler SDK handler
     * @param consoleLogger Console logger to persist all messages
	 */
	public SdkCallAgent(
            AndroidSdkHandler sdkHandler,
            ILogger consoleLogger)
	{
		this.sdkContext = new SdkContext(sdkHandler);
		sdkContext.setSdkLogger(consoleLogger);
		this.consoleLogger = consoleLogger;
	}

	/**
	 * Construct SdkCallAgent object to mediate between application and UI layer requiring an icon factory
     * @param sdkHandler SDK handler
     * @param iconEditorFactory Icon factory to provide editor icons
     * @param consoleLogger Console logger to persist all messages
	 */
	public SdkCallAgent(
            AndroidSdkHandler sdkHandler,
            IEditorIconFactory iconEditorFactory,
            ILogger consoleLogger)
	{
		this(sdkHandler, consoleLogger);
		this.iconEditorFactory = iconEditorFactory;
	}

	/**
	 * Returns SDK context containing Android SDK Handler
	 * @return SdkContext object
	 */
	public SdkContext getSdkContext() {
		SdkHelper helper = sdkContext.getSdkHelper();
		// The helper requires image factory to be set post construction
		if (helper.getImageFactory() == null)
			helper.setImageFactory(getImageLoader());
		return sdkContext;
	}

	/**
	 * Returns editor icon factory
	 * @return IEditorIconFactory which will be a dummy if editor icon factory not provided in constructor
	 */
	public IEditorIconFactory getEditorIconFactory() {
		if (iconEditorFactory ==null)
			// Icon factory not set. Do not throw exception, but handle gracefully.
			return new IEditorIconFactory(){

				@Override
				public Image getColorIcon(String osName, int color) {
					// Return generic image to avoid NPE
					return sdkContext.getSdkHelper().getImageByName("nopkg_icon_16.png");
				}};//
		return iconEditorFactory;
	}
	
	/**
	 * Set image loader if not already set. Override to source images elsewhere
	 */
	public ImageFactory getImageLoader()
	{
		SdkUserInterfacePlugin sdkPlugin = SdkUserInterfacePlugin.instance();
		return sdkPlugin != null ? sdkPlugin.getImageFactory() : null;
 	}
	
	/**
	 * Call completeOperations() in a finally clause to ensure pending notifications are triggered
	 */
	public void completeSdkOperations()
	{
		SdkHelper helper = sdkContext.getSdkHelper();
		if (sdkContext.isSdkLocationChanged() || helper.isReloadPending())
			helper.broadcastOnSdkReload(consoleLogger);
		
	}
}
