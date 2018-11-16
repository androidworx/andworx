package org.eclipse.andworx.test;

import org.eclipse.andworx.sdk.SdkProfile;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchListener;
import org.eclipse.ui.PlatformUI;
import org.junit.Before;
import org.junit.Test;

public class UserSessionTest {
	private SdkHolder sdkHolder;
	private SdkProfile sdk;
	
	@Before 
	public void setUp() throws InterruptedException {
		//sdkHolder = new SdkHolder(false); // Use when no SDK configured
		sdkHolder = new SdkHolder();
		sdk = sdkHolder.getCurrentSdk();
		// Wait for targets to be loaded
		sdk.getAndroidTargets();
	}

	@Test 
	public void testUserSession() throws InterruptedException {
        final IWorkbench workbench = PlatformUI.getWorkbench();
        IWorkbenchListener listener = new IWorkbenchListener() {

			@Override
			public boolean preShutdown(IWorkbench workbench, boolean forced) {
				synchronized(this) {
					notifyAll();
				}
				return true;
			}

			@Override
			public void postShutdown(IWorkbench workbench) {
			}};
		workbench.addWorkbenchListener(listener );
		synchronized(listener) {
			listener.wait();
		}
	}
}
