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
package org.eclipse.andmore.base.resources;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.LocalResourceManager;
import org.eclipse.jface.resource.ResourceManager;
import org.eclipse.swt.graphics.Device;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageDataProvider;
import org.eclipse.swt.internal.DPIUtil;
import org.eclipse.swt.widgets.Display;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.utils.ILogger;

/**
 * Loads bundle images 
 * Images are loaded using a path relative to the bundle location.
 * <p/>
 * Instances are mangaged by a JFace resource manager, and thus should never be disposed by the image consumer.
 * Uses a Bundle either directly or indirectly via PluginResourceProvider to locate image files. The  resource manager
 * should be disposed when the bundle is stopped.
 * <p/>
 * Images obtained by name are to be located in directory named by manifest constant ICONS_PATH.
 * @author Andrew Bowley
 *
 * 30-12-2017
 */
@SuppressWarnings("restriction")
public class JFaceImageLoader implements ImageFactory {
	public static final class AutoScaleImageDataProvider implements ImageDataProvider {
		Device device;
		ImageData imageData;
		int currentZoom;
		public AutoScaleImageDataProvider(Device device, ImageData data, int zoom){
			this.device = device;
			this.imageData = data;
			this.currentZoom = zoom;
		}
		@Override
		public ImageData getImageData(int zoom) {
			return DPIUtil.autoScaleImageData(device, imageData, zoom, currentZoom);
		}
	}

    /** Image file location when using {@link #getImageByName(String)} */
    public static String ICONS_PATH = "icons/";

    /** The bundle containing image files. This is null if provider is not null and vice versa. */
    protected Bundle bundle;
    /** Hides a bundle which contains image files. The bundle provides ImageDescriptor objects. */
    protected PluginResourceProvider provider;
    /** Maps keys to generated filter image descriptors */
    protected final Map<String, ImageDescriptor> filterMap = new HashMap<>();
    /** The JFace resource manager which creates and caches images */
    protected ResourceManager resourceManager;
    /** Logger (optional) */
    protected ILogger logger;
	
	/**
     * Construct an ImageLoader object using given UI plugin instance.
     * This object provides imageDescriptorFromPlugin() method
	 * @param bundle
	 */
	public JFaceImageLoader(@NonNull PluginResourceProvider provider)
	{
		this.provider = provider;
		createResourceManager();
	}

	/**
     * Construct an ImageLoader object using given bundle instance 
	 * @param bundle
	 */
	public JFaceImageLoader(@NonNull Bundle bundle)
	{
		this.bundle = bundle;
		createResourceManager();
	}

    /**
     * Construct an ImageLoader object using given class of plugin associated with the bundle
     * @param bundleClass
     */
	public JFaceImageLoader(Class<?> bundleClass)
	{
		this(FrameworkUtil.getBundle(bundleClass));
	}

	/**
	 * Set the logger 
	 * @param logger Android logger
	 */
	public void setLogger(ILogger logger) {
		this.logger = logger;
	}
	
   /**
    * getImageByName 
    * @see org.eclipse.andmore.base.resources.ImageFactory#getImageByName(java.lang.String)
    */
    @Override
	@Nullable
    public Image getImageByName(String imageName) {
        return getImage(ICONS_PATH + imageName);
    }


    /**
     * getImageByName using given key and image editor
	 * @see org.eclipse.andmore.base.resources.ImageFactory#getImageByName(java.lang.String, java.lang.String, org.eclipse.andmore.base.resources.JFaceImageLoader.ImageEditor)
	 */
    @Override
	@Nullable
    public Image getImageByName(String imageName,
                                 String keyName,
                                 ImageEditor imageEditor) {
        if (imageEditor == null) // No imageEditor means just load image. The keyName is irrelevant.
            return getImageByName(imageName);                                    
    	String imagePath = ICONS_PATH + imageName;
    	Image image = null;
    	ImageDescriptor imageDescriptor = descriptorFromPath(imagePath);
     	if (imageDescriptor != null) {
     		// The filter image descriptor is cached in the filter map
     		ImageDescriptor imagefilterDescriptor = filterMap.get(keyName);
     		if (imagefilterDescriptor == null) {
     			// Assume filter input = output
 				imagefilterDescriptor = imageDescriptor;
         		image = getResourceManager().createImage(imageDescriptor);
     			ImageData imageData = imageEditor.edit(image);
     			if (imageData !=  null) {
     				// Create new image from data
					AutoScaleImageDataProvider imageDataProvider = new AutoScaleImageDataProvider(Display.getDefault(), imageData, 100);
     				imagefilterDescriptor = ImageDescriptor.createFromImageDataProvider(imageDataProvider);
     	     		image = resourceManager.createImage(imagefilterDescriptor);
     			}
     			filterMap.put(keyName, imagefilterDescriptor);
     		}
     		else
         		image = getResourceManager().createImage(imagefilterDescriptor);
     	}
    	return image;
    }

    /**
     * getImage using image path
	 * @see org.eclipse.andmore.base.resources.ImageFactory#getImage(java.lang.String)
	 */
    @Override
	public Image getImage(String imagePath) {
    	Image image = null;
    	ImageDescriptor imageDescriptor = descriptorFromPath(imagePath);
     	if (imageDescriptor != null)  {
    		image = getResourceManager().createImage(imageDescriptor);
    		if ((image == null) && (logger != null))
    			logger.error(null, "Image creation failed for image path " + imagePath);
     	}
        return image;
    }

    /**
     * dispose
	 * @see org.eclipse.andmore.base.resources.ImageFactory#dispose()
	 */
    @Override
	public void dispose() {
        // Garbage collect system resources
        if (resourceManager != null) 
        {
            resourceManager.dispose();
            resourceManager = null;
        }
    }
 
	/**
	 * Loads an image given its filename (with its extension) and if not found,
	 * uses supplied {@code ReplacementImager} to create a replacement.
	 * <p/>
	 * @param imageName Filename (with extension) of the image to load.
	 * @param replacementImager Provides replacement image
	 * @return {@link Image}. The caller must NOT dispose the image.
	 */
	@Override
	@Nullable
	public Image getImageByName(String imageName, ReplacementImager replacementImager) {
    	String imagePath = ICONS_PATH + imageName;
    	ImageDescriptor imageDescriptor = descriptorFromPath(imagePath);
    	getResourceManager();
     	if (imageDescriptor == null) {
			ImageDescriptor replacementImageDescriptor = filterMap.get(imagePath);
			if (replacementImageDescriptor == null) {
				AutoScaleImageDataProvider imageDataProvider = new AutoScaleImageDataProvider(Display.getDefault(), replacementImager.create(), 100);
				replacementImageDescriptor = ImageDescriptor.createFromImageDataProvider(imageDataProvider);
 			    filterMap.put(imagePath, replacementImageDescriptor);
			}
			return resourceManager.createImage(replacementImageDescriptor);
		}
		return resourceManager.createImage(imageDescriptor);
	}

	/**
	 * Returns an image descriptor given its filename (with its extension).
	 * Might return null if the image descriptor cannot be loaded.  <br/>
	 * @param imageName The filename (with extension) of the image to load.
	 * @return {@link ImageDescriptor} object or null if the image file is not found.
	 */
	@Override
	@Nullable
	public ImageDescriptor getDescriptorByName(String imageName) {
    	String imagePath = ICONS_PATH + imageName;
    	return descriptorFromPath(imagePath);
	}

	/**
	 * Returns image descriptor for given image path
	 * @param imagePath The image path (relative to bundle location)
	 * @return ImageDescriptor object
	 */
	protected ImageDescriptor descriptorFromPath(String imagePath) {
		if (provider != null) {
			ImageDescriptor descriptor = provider.descriptorFromPath(imagePath);
			if ((logger != null) && (descriptor == null))
	    		logger.error(null, "Image descriptor null for image path: " +imagePath);
			return descriptor;
		}
    	ImageDescriptor imageDescriptor = null;
        // An image descriptor is an object that knows how to create an SWT image.
    	URL url = FileLocator.find(bundle, new Path(imagePath), null);
    	if (url != null) {
    		imageDescriptor = ImageDescriptor.createFromURL(url);
    		if (logger != null) {
    		    if (imageDescriptor != null)
    		    	logger.info("Image file found at " + url.toString());
    		    else
    	    		logger.error(null, "Image descriptor null for URL: " + url.toString());
    		    }
    	}
    	else if (logger != null)
    		logger.error(null, "Image path not found: " + imagePath);
        return imageDescriptor;
    }
    
    /**
     * Returns local Resource Manager, creating it if it does not exist
     * @return ResourceManager object
     */
    protected void createResourceManager() {
        if (resourceManager == null) {
        	// Thread safe creation of resource manager
        	synchronized(this) {
				if (resourceManager == null)  {
					Runnable createLocalResourceManagerTask = new Runnable() {
		                
		                @Override
		                public void run() 
		                {
		                    // getResources() returns the ResourceManager for the current display. 
		                    // May only be called from a UI thread.
		                	ResourceManager resources = JFaceResources.getResources();
		                    resourceManager = new LocalResourceManager(resources);
		                }
		            };
					if (Display.getCurrent() == null)
						Display.getDefault().syncExec(createLocalResourceManagerTask);
					else
						createLocalResourceManagerTask.run();
				}
        	}
        }
     }

    /**
     * Returns local Resource Manager, creating it if it does not exist
     * @return ResourceManager object
     */
    private ResourceManager getResourceManager() {
        if (resourceManager == null) {
        	createResourceManager();
        }
        return resourceManager;
    }

}
