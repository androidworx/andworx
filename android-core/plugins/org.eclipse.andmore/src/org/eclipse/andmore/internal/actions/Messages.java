/*******************************************************************************
 * Copyright (c) 2015 Zend Technologies Ltd and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Kaloyan Raev - initial API and implementation
 *******************************************************************************/
package org.eclipse.andmore.internal.actions;

import org.eclipse.osgi.util.NLS;

public class Messages extends NLS {
	private static final String BUNDLE_NAME = "org.eclipse.andmore.internal.actions.messages"; //$NON-NLS-1$
	public static String OpenAndroidPerspectiveAction_dialog_title;
	public static String OpenAndroidPerspectiveAction_error_open_failed;

	static {
		// initialize resource bundle
		NLS.initializeMessages(BUNDLE_NAME, Messages.class);
	}

	private Messages() {
	}
}
