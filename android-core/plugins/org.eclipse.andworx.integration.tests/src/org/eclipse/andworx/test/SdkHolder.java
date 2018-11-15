package org.eclipse.andworx.test;

import static org.junit.Assert.fail;

import java.io.File;

import org.eclipse.andmore.internal.sdk.Sdk;
import org.eclipse.andworx.build.AndworxFactory;
import org.eclipse.andworx.build.SdkTracker;
import org.eclipse.andworx.event.AndworxEvents;
import org.eclipse.andworx.sdk.AndroidSdkPreferences;
import org.eclipse.andworx.sdk.AndroidSdkValidator;
import org.eclipse.andworx.sdk.QuietSdkValidator;
import org.eclipse.andworx.sdk.SdkListener;
import org.eclipse.andworx.sdk.SdkProfile;
import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.e4.core.services.events.IEventBroker;
import org.eclipse.e4.ui.internal.workbench.E4Workbench;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;

public class SdkHolder {

	private static class TestSdkListener implements SdkListener {
		
		volatile SdkProfile sdkProfile;
		volatile boolean isSdkAvailable = true;
		
		public SdkProfile getCurrentSdk() {
			return sdkProfile;
		}

		public boolean isSdkAvailable() {
			return isSdkAvailable;
		}

		public void setSdkAvailable(boolean isSdkAvailable) {
			this.isSdkAvailable = isSdkAvailable;
			signal();
		}

		@Override
		public void onLoadSdk(SdkProfile sdkProfile) {
			//System.out.println("SDK arrived! " + (sdkProfile != null));
			this.sdkProfile = sdkProfile;
			signal();
		}

		private void signal() {
			synchronized (this) {
				notifyAll();
			}
		}
	}
	
    private volatile SdkProfile sdkProfile;
    private TestSdkListener sdkListener;
    private IEventBroker eventBroker;

    public SdkHolder() {
    	this(true);
    }
    
    public SdkHolder(boolean useAndroidHome) {

    	sdkListener = new TestSdkListener();
    	if (!useAndroidHome)
    		return;
    	IEclipseContext serviceContext = E4Workbench.getServiceContext();
    	eventBroker = (IEventBroker) serviceContext.get(IEventBroker.class.getName());
       	EventHandler eventHandler = new EventHandler() {
			@Override
			public void handleEvent(Event event) {
			   	SdkTracker tracker = AndworxFactory.instance().getSdkTracker();
			   	if (tracker.getSdkProfile() == null) {
			   		// Use ANDROID_HOME if set in enviroment and exists
			   		String androidHome = System.getenv("ANDROID_HOME");
			   		File sdkPath = null;
			   		if (androidHome != null) {
			   			sdkPath = new File(androidHome);
			   			if (sdkPath.exists() && sdkPath.isDirectory()) {
			   				File sdkLocation = sdkPath;
			   				AndroidSdkPreferences prefs = new AndroidSdkPreferences() {

								@Override
								public File getLastSdkPath() {
									return null;
								}

								@Override
								public File getSdkLocation() {
									return sdkLocation;
								}

								@Override
								public void setSdkLocation(File location) {
								}

								@Override
								public boolean isSdkSpecified() {
									return false;
								}

								@Override
								public void addPropertyChangeListener(IPropertyChangeListener listener) {
								}

								@Override
								public String getSdkLocationValue() {
									return null;
								}

								@Override
								public boolean save() {
									return false;
								}};
							AndroidSdkValidator validator = new AndroidSdkValidator(prefs);
							if (validator.checkSdkLocationAndId(sdkLocation, new QuietSdkValidator())) {
								Sdk.loadSdk(sdkLocation.getAbsolutePath());
							}
			   			}
					   	if (tracker.getSdkProfile() == null) {
					   		sdkListener.setSdkAvailable(false);
					   	}
			   		}
			   	}
		        eventBroker.unsubscribe(this);
			}};
	    eventBroker.subscribe(AndworxEvents.ANDWORX_STARTED, eventHandler);
	    eventBroker.subscribe(AndworxEvents.INSTALL_SDK_REQUEST, eventHandler);
    }
    
    public SdkProfile getCurrentSdk() throws InterruptedException {
        if (sdkProfile == null) {
            synchronized(this) {
                if (sdkProfile == null) {
        			//System.out.println("Load CurrentSdk");
                	sdkProfile = doLoadCurrentSdk();
                }
            }
        }
        return sdkProfile;
    }

	/**
	 * Gets the current SDK from ADT, waiting if necessary.
	 * @throws InterruptedException 
	 */
	private SdkProfile doLoadCurrentSdk() throws InterruptedException {
    	SdkTracker tracker = AndworxFactory.instance().getSdkTracker();
    	tracker.addSdkListener(sdkListener);
		SdkProfile sdk = sdkListener.getCurrentSdk();
		if (sdk == null)
			while (sdk == null) {
				synchronized(sdkListener) {
					sdkListener.wait(2000);
				}
				if (!sdkListener.isSdkAvailable())
					fail(SdkProfile.SDK_NOT_AVAILABLE_ERROR);
				sdk = sdkListener.getCurrentSdk();
			}
		System.out.println("Load CurrentSdk complete");
		return sdkListener.getCurrentSdk();	
	}

}
