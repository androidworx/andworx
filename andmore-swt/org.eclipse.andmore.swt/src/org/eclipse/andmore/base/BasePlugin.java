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

import org.eclipse.e4.ui.internal.workbench.E4Workbench;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

public class BasePlugin extends AbstractUIPlugin {

	private static BasePlugin instance;
	/** External BaseContext object - only for unit testing */
	private static BaseContext externalBaseContext;

	public BasePlugin() {
		instance = this;
	}
	
    /**
     * start()
     * @see org.eclipse.ui.plugin.AbstractUIPlugin#start(org.osgi.framework.BundleContext)
     */
    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
		E4Workbench.getServiceContext().set(BaseContext.class, BaseContext.container.getBaseContext());
    }
    
	/*
     * (non-Javadoc)
     *
     * @see org.eclipse.ui.plugin.AbstractUIPlugin#stop(org.osgi.framework.BundleContext)
     */
    @Override
    public void stop(BundleContext context) throws Exception {
        super.stop(context);
    }

    public static BasePlugin instance() {
    	return instance;
    }
    
    public static BaseContext getBaseContext() {
		// Support unit testing for which Eclipse context is not available
		if (instance == null)
			return externalBaseContext != null ? externalBaseContext : BaseContext.container.getBaseContext();
    	return E4Workbench.getServiceContext().get(BaseContext.class);
    }
    
    public static void setBaseContext(BaseContext baseContext) {
		// Support unit testing for which Eclipse context is not available
		if (instance == null)
			externalBaseContext = baseContext;
		else
			throw new UnsupportedOperationException("Context cannot be changed");
    }
    
}
