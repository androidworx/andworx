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

import java.util.HashMap;

import org.eclipse.andmore.base.BasePlugin;
import org.eclipse.jface.resource.ImageDescriptor;

public class PluginResourceRegistry {

	private HashMap<String, PluginResourceProvider> resouceProviderMap;
	
	public PluginResourceRegistry() {
		resouceProviderMap = new HashMap<>();
	}
	
	public void putResourceProvider(String key, PluginResourceProvider provider) {
		resouceProviderMap.put(key, provider);
	}
	
	public PluginResourceProvider getResourceProvider(String pluginId) {
		PluginResourceProvider resourceProvider = resouceProviderMap.get(pluginId);
		if (resourceProvider == null) {
			synchronized(resouceProviderMap) {
				if (resourceProvider == null) {
					resourceProvider = new PluginResourceProvider() {

						@Override
						public ImageDescriptor descriptorFromPath(String imageFilePath) {
							return BasePlugin.imageDescriptorFromPlugin(pluginId, imageFilePath);
						}};
					resouceProviderMap.put(pluginId, resourceProvider)	;
				}
			}
		}
		return resourceProvider;
	}
	
}
