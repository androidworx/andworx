/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.eclipse.andmore.base.resources;

import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.andmore.base.BasePlugin;
import org.eclipse.jface.resource.ImageDescriptor;

/**
 * Container for plugin ImageDescriptor providers
 */
public class PluginResourceRegistry {

	/** Provider container */
	private ConcurrentHashMap<String, PluginResourceProvider> resouceProviderMap;

	/**
	 * Construct PluginResourceRegistry object
	 */
	public PluginResourceRegistry() {
		super();
		resouceProviderMap = new ConcurrentHashMap<>();
	}

	/**
	 * Put custom provider in container. Note that plugin providers are created and inserted on demand.
	 * @param key Unique name of custom provider
	 * @param provider Plugin resource provider
	 */
	public void putResourceProvider(String key, PluginResourceProvider provider) {
		resouceProviderMap.put(key, provider);
	}

	/**
	 * Returns provider for plugin specified by symbolic name or custom provider by unique name
	 * @param key Provider name
	 * @return
	 */
	public PluginResourceProvider getResourceProvider(String key) {
		PluginResourceProvider resourceProvider = resouceProviderMap.get(key);
		if (resourceProvider == null) {
			synchronized(resouceProviderMap) {
				if (resourceProvider == null) {
					resourceProvider = new PluginResourceProvider() {

						@Override
						public ImageDescriptor descriptorFromPath(String imageFilePath) {
							return BasePlugin.imageDescriptorFromPlugin(key, imageFilePath);
						}};
					resouceProviderMap.put(key, resourceProvider)	;
				}
			}
		}
		return resourceProvider;
	}
	
}
