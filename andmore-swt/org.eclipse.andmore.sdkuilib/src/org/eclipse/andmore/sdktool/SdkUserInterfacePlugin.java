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

import org.eclipse.andmore.base.resources.ImageFactory;
import org.eclipse.andmore.base.resources.JFaceImageLoader;
import org.eclipse.andmore.base.resources.PluginResourceProvider;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

/**
 * Plugin activator to manage resources such as images
 * @author Andrew Bowley
 *
 */
public class SdkUserInterfacePlugin extends AbstractUIPlugin {

	public static final String PLUGIN_ID = "org.eclipse.andworx.sdkuilib"; //$NON-NLS-1$
	
    private static SdkUserInterfacePlugin instance;
	private ImageFactory imageFactory;

    public SdkUserInterfacePlugin() {
        super();
        instance = this;
    }
  
	public ImageFactory getImageFactory() {
		return imageFactory;
	}
	
    public static SdkUserInterfacePlugin instance()
    {
    	return instance;
    }
    
	/**
	 * Returns an image descriptor for the image file at the given plug-in
	 * relative path
	 */
	public ImageDescriptor getImageDescriptor(String path) {
		return imageDescriptorFromPlugin(PLUGIN_ID, path);
	}

	/**
	 * Starts up this plug-in.
	 * <p>
	 * This method should be overridden in subclasses that need to do something
	 * when this plug-in is started.  Implementors should call the inherited method
	 * at the first possible point to ensure that any system requirements can be met.
	 * </p>
	 * <p>
	 * If this method throws an exception, it is taken as an indication that
	 * plug-in initialization has failed; as a result, the plug-in will not
	 * be activated; moreover, the plug-in will be marked as disabled and
	 * ineligible for activation for the duration.
	 * </p>
	 * <p>
	 * Note 1: This method is automatically invoked by the platform
	 * the first time any code in the plug-in is executed.
	 * </p>
	 * <p>
	 * Note 2: This method is intended to perform simple initialization
	 * of the plug-in environment. The platform may terminate initializers
	 * that do not complete in a timely fashion.
	 * </p>
	 * <p>
	 * Note 3: The class loader typically has monitors acquired during invocation of this method.  It is
	 * strongly recommended that this method avoid synchronized blocks or other thread locking mechanisms,
	 * as this would lead to deadlock vulnerability.
	 * </p>
	 * <p>
	 * Note 4: The supplied bundle context represents the plug-in to the OSGi framework.
	 * For security reasons, it is strongly recommended that this object should not be divulged.
	 * </p>
	 * <p>
	 * Note 5: This method and the {@link #stop(BundleContext)} may be called from separate threads,
	 * but the OSGi framework ensures that both methods will not be called simultaneously.
	 * </p>
	 * <b>Clients must never explicitly call this method.</b>
	 *
	 * @param context the bundle context for this plug-in
	 * @exception Exception if this plug-in did not start up properly
	 * @since 3.0
	 */
	@Override
	public void start(BundleContext context) throws Exception {
		super.start(context);
		imageFactory = new JFaceImageLoader(
			new PluginResourceProvider() {

				@Override
				public ImageDescriptor descriptorFromPath(String imagePath) {
					return getImageDescriptor(imagePath);
				}
			});
	}

	/**
	 * Stops this plug-in.
	 * <p>
	 * This method should be re-implemented in subclasses that need to do something
	 * when the plug-in is shut down.  Implementors should call the inherited method
	 * as late as possible to ensure that any system requirements can be met.
	 * </p>
	 * <p>
	 * Plug-in shutdown code should be robust. In particular, this method
	 * should always make an effort to shut down the plug-in. Furthermore,
	 * the code should not assume that the plug-in was started successfully.
	 * </p>
	 * <p>
	 * Note 1: If a plug-in has been automatically started, this method will be automatically
	 * invoked by the platform when the platform is shut down.
	 * </p>
	 * <p>
	 * Note 2: This method is intended to perform simple termination
	 * of the plug-in environment. The platform may terminate invocations
	 * that do not complete in a timely fashion.
	 * </p>
	 * <p>
	 * Note 3: The supplied bundle context represents the plug-in to the OSGi framework.
	 * For security reasons, it is strongly recommended that this object should not be divulged.
	 * </p>
	 * <p>
	 * Note 4: This method and the {@link #start(BundleContext)} may be called from separate threads,
	 * but the OSGi framework ensures that both methods will not be called simultaneously.
	 * </p>
	 * <b>Clients must never explicitly call this method.</b>
	 *
	 * @param context the bundle context for this plug-in
	 * @exception Exception if this method fails to shut down this plug-in
	 * @since 3.0
	 */
	@Override
	public void stop(BundleContext context) throws Exception {
		imageFactory.dispose();
		super.stop(context);
	}


}
