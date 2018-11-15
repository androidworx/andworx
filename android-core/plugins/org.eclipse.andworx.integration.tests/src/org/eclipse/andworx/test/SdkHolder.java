package org.eclipse.andworx.test;

import static org.junit.Assert.fail;

import java.io.File;
import java.util.Timer;
import java.util.TimerTask;

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

	static int TIMEOUT_SECS = 30;
	
	private static class TestSdkListener implements SdkListener {
		
		volatile SdkProfile sdk;

		public SdkProfile getSdk() {
			return sdk;
		}

		public boolean isSdkAvailable() {
			return sdk != null;
		}

		@Override
		public void onLoadSdk(SdkProfile sdk) {
			//System.out.println("SDK arrived! " + (sdkProfile != null));
			this.sdk = sdk;
		}

	}
	
    private volatile SdkProfile sdkProfile;
    private TestSdkListener sdkListener;
    private IEventBroker eventBroker;

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
								sdkListener.onLoadSdk(tracker.setCurrentSdk(sdkLocation.getAbsolutePath()));
							}
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
        	SdkTracker tracker = AndworxFactory.instance().getSdkTracker();
        	tracker.addSdkListener(sdkListener);
            Timer timer = new Timer();
            TimerTask task = new TimerTask() {
            	long duration = 30000L;
            	public void run() {
            		if (sdkListener.isSdkAvailable())
            			signal();
            		else if ((duration -= 1000L) <= 0)
            			signal();
            	}
            	private void signal() {
            		synchronized (this) {
            			notifyAll();
            		}
            	}
            }; 
            timer.scheduleAtFixedRate(task, 1000L, 1000L);
            synchronized (task) {
            	task.wait();
            }
        }
		if (sdkListener.isSdkAvailable())
			sdkProfile = sdkListener.getSdk();
		else
			fail(SdkProfile.SDK_NOT_AVAILABLE_ERROR);
        return sdkProfile;
    }

}
