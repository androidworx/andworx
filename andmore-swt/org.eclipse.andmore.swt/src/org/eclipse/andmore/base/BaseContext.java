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
package org.eclipse.andmore.base;

import org.eclipse.andmore.base.resources.PluginResourceRegistry;
import org.eclipse.e4.core.contexts.IEclipseContext;

/**
 * Context for containing foundation classes available to all bundles
 */
public interface BaseContext {
	/**
	 * Returns a context that can be used to lookup OSGi services
	 * @return IEclipseContext object
	 */
	IEclipseContext getEclipseContext();

	/** Container for plugin ImageDescriptor providers */
	PluginResourceRegistry getPluginResourceRegistry();
	/** Utility to support unit testing requiring JavaProject mocking */
	JavaProjectHelper getJavaProjectHelper();

	/** Singleton instance */
	static final BaseContextContainer container = new BaseContextContainer();
}
