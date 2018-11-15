/*
 * Copyright (C) 2018 The Android Open Source Project
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
package org.eclipse.andmore.internal.build;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.ILaunchConfiguration;

public interface Launcher {
	/**
	 * Launches given configuration in RUN mode by delegating to
	 * this configuration's launch configuration delegate, and returns the
	 * resulting launch.
	 * @param config Launch configuration
	 * @return the resulting launch
	 * @exception CoreException if this method fails. Reasons include:<ul>
	 * <li>unable to instantiate the underlying launch configuration delegate</li>
	 * <li>the launch fails (in the delegate)</code>
	 * </ul>
	 */
	int launch(final ILaunchConfiguration config) throws DebugException, CoreException;

}
