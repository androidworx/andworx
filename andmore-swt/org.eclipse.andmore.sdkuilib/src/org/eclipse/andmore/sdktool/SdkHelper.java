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

import java.util.ArrayList;

import org.eclipse.andmore.base.resources.ImageFactory;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.swt.graphics.Image;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.sdkuilib.repository.ISdkChangeListener;
import com.android.utils.ILogger;

/**
 * SdkHelper is an image factory and broadcasts package installation events
 * @author Andrew Bowley
 *
 * 30-12-2017
 */
public class SdkHelper {

	private ILogger logger;
    /** The current {@link ImageFactory}. */
    private ImageFactory mImageFactory;
    /** Flag to remember one or more packages have been installed. Reset on {@link #broadcastOnSdkReload(ILogger)} called. */ 
    private boolean isReloadPending;

    private final ArrayList<ISdkChangeListener> mListeners = new ArrayList<ISdkChangeListener>();

    public SdkHelper(ILogger logger) {
    	this.logger = logger;
    }
    
    public boolean isReloadPending() {
		return isReloadPending;
	}

	/** Adds a listener ({@link ISdkChangeListener}) that is notified when the SDK is reloaded. */
    public void addListeners(ISdkChangeListener listener) {
        if (mListeners.contains(listener) == false) {
            mListeners.add(listener);
        }
    }

    /** Removes a listener ({@link ISdkChangeListener}) that is notified when the SDK is reloaded. */
    public void removeListener(ISdkChangeListener listener) {
        mListeners.remove(listener);
    }

    /**
     * Safely invoke all the registered {@link ISdkChangeListener#onSdkReload()}.
     * This can be called from any thread.
     */
    public void broadcastOnSdkReload(ILogger logger) {
       isReloadPending = false;
       if (!mListeners.isEmpty()) {
    	   runJob(new Runnable() {
                @Override
                public void run() {
                    for (ISdkChangeListener listener : mListeners) {
                        try {
                            listener.onSdkReload();
                        } catch (Throwable t) {
                        	logger.error(t, "Error while broadcasting SDK reload");
                        }
                    }
                }
            });
        }
    }

    /**
     * Safely invoke all the registered {@link ISdkChangeListener#preInstallHook()}.
     * This can be called from any thread.
     */
    public void broadcastPreInstallHook(ILogger logger) {
        if (!mListeners.isEmpty()) {
        	runJob(new Runnable() {
                @Override
                public void run() {
                    for (ISdkChangeListener listener : mListeners) {
                        try {
                            listener.preInstallHook();
                        } catch (Throwable t) {
                        	logger.error(t, "Error while broadcasting SDK pre install");
                        }
                    }
                }
            });
        }
    }

    /**
     * Safely invoke all the registered {@link ISdkChangeListener#postInstallHook()}.
     * This can be called from any thread.
     */
    public void broadcastPostInstallHook(ILogger logger) {
        if (!mListeners.isEmpty()) {
        	isReloadPending = true;
            runJob(new Runnable() {
                @Override
                public void run() {
                    for (ISdkChangeListener listener : mListeners) {
                        try {
                            listener.postInstallHook();
                        } catch (Throwable t) {
                        	logger.error(t, "Error while broadcasting SDK post install");
                        }
                    }
                }
            });
        }
    }

	public void setImageFactory(ImageFactory imageFactory) {
    	mImageFactory = imageFactory;
    }
    
    /**
     * Returns image factory.
     * @return ImageFactory object
     */
    public ImageFactory getImageFactory() {
        return mImageFactory;
    }

    /**
	 * @param logger the logger to set
	 */
	public void setLogger(ILogger logger) {
		this.logger = logger;
	}

	/**
     * Loads an image given its filename (with its extension).
     * Might return null if the image cannot be loaded.  <br/>
     * The image is cached. Successive calls will return the <em>same</em> object. <br/>
     * The image is automatically disposed when {@link ImageFactory} is disposed.
     *
     * @param imageName The filename (with extension) of the image to load.
     * @return A new or existing {@link Image}. The caller must NOT dispose the image. 
     * The returned image can be null if the expected file is missing.
     */
    @Nullable
    public Image getImageByName(@NonNull String imageName) {
        return mImageFactory != null ? mImageFactory.getImageByName(imageName, imageName, null) : null;
    }

    /**
     * Runs given rask asynchronously in Job
     * @param task The task
     */
    private void runJob(Runnable task) {
		Job job = new Job("SDK Helper task"){

			@Override
			protected IStatus run(IProgressMonitor arg0) {
				try {
					task.run();
				} catch (Exception e) {
					logger.error(e,"Error while running SDK Helper task");
				}
				return Status.OK_STATUS;
			}};
		job.setPriority(Job.BUILD);
		job.schedule();
	}

}
