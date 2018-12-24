package org.eclipse.andworx.test.build;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.TimeUnit;

import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.zip.ZipUnArchiver;
import org.codehaus.plexus.logging.Logger;
import org.eclipse.andworx.maven.MavenLog;
import org.eclipse.andworx.sdk.SdkProfile;
import org.eclipse.andworx.test.SdkHolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotView;
import org.eclipse.swtbot.swt.finder.SWTBot;
import org.eclipse.swtbot.swt.finder.exceptions.WidgetNotFoundException;
import org.eclipse.swtbot.swt.finder.junit.SWTBotJunit4ClassRunner;
import org.eclipse.swtbot.swt.finder.waits.DefaultCondition;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.swtbot.swt.finder.widgets.TimeoutException;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchListener;
import org.eclipse.ui.PlatformUI;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(SWTBotJunit4ClassRunner.class)
public class ImportTest {
	private static final String TEST_PROJECT_ZIP = "resources/permissions2.zip";
	private static final String PROJECT_NAME = "com.example.android.system.runtimepermissions.Application";
	private static final String APK_NAME = "com.example.android.system.runtimepermissions.Application.apk";
	
	private static SWTWorkbenchBot swtbot;
	
	private SWTBot eclipseShell;

	private SdkHolder sdkHolder;
	private SdkProfile sdk;
	private File projectLocation;

	@BeforeClass
	public static void initBot() throws InterruptedException {
	        swtbot = new SWTWorkbenchBot();
	}
	 
	private static void closeWelcomePage() {
        for (SWTBotView view : swtbot.views()) {
            if (view.getTitle().equals("Welcome")) {
                 view.close();
            }
        }
	}
	
	@AfterClass
	public static void afterClass() {
		swtbot.resetWorkbench();
	}

	@Before 
	public void setUp() throws InterruptedException {
	    closeWelcomePage();
		// Dismiss desktop error dialog, if presenst
		for (SWTBotShell shell: swtbot.shells()) {
	        try {
				if (shell.getText().contains("Eclipse")) 
					eclipseShell = shell.bot();
				else
					shell.close();
			} catch (WidgetNotFoundException | TimeoutException e) {
				// Ignore
			}
		}
		assert(eclipseShell != null);
		// Switch when no SDK configured
		//sdkHolder = new SdkHolder(true);
		sdkHolder = new SdkHolder();
		sdk = sdkHolder.getCurrentSdk();
		// Allow time for initial views to be rendered
		Thread.sleep(2000);
		try {
			SWTBotShell sdkInstallShell = swtbot.shell("Select Android SDK installation");
			sdkInstallShell.bot().button("Cancel").click();
			swtbot.sleep(200);
		} catch (WidgetNotFoundException e) {
			// Ignore
		}
		// Wait for targets to be loaded
		sdk.getAndroidTargets();
		projectLocation =  com.google.common.io.Files.createTempDir();
		projectLocation.deleteOnExit();
		File permissionsZip = new File(TEST_PROJECT_ZIP);
		final UnArchiver unArchiver = new ZipUnArchiver(permissionsZip) 
        {
            @Override
            protected Logger getLogger()
            {
                return new MavenLog(ImportTest.class.getName());
            }
        };
        unArchiver.setDestDirectory(projectLocation);
        unArchiver.extract();
 	}
	
	@Test public void testImportPermissionsProject() throws InterruptedException {
		/*
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
		*/
		assertNotNull(sdk);
		//AdtStartupService adtStartupService = AdtStartupService.instance();
		eclipseShell.menu("File").menu("Import...").click();
		swtbot.tree().getTreeItem("Android").expand();
		swtbot.tree().getTreeItem("Android").getNode("Import Android project").select();
		swtbot.button("Next >").click();
		swtbot.checkBox("Copy project into workspace").click();
		File application = new File(projectLocation, "permissions/Application");
		swtbot.textWithLabel("Project Directory:").setText(application.getAbsolutePath());
		swtbot.button("Refresh").click();
	    // Import may take a long time if a lot of dependencies are to be downloaded
	    int timeout = 300000;
	    // check every second, if the condition is fulfilled
	    int interval = 2000;
	    swtbot.waitUntil(new DefaultCondition() {

		        @Override
		        public boolean test() throws Exception {
		            return bot.button("Finish").isEnabled();
		        }

		        @Override
		        public String getFailureMessage() {
		            return "Timeout waiting for import to complete";
		        }
		    }, timeout, interval);
	    swtbot.button("Finish").click();
	    swtbot.sleep(5000);
		
		//swtbot.sleep(500);
	    //swtbot.viewByTitle("Package Explorer");
		//System.out.println("Exit workbench to complete test");
		//swtbot.menu("Window").menu("Show View").menu("Other...").click();
		//swtbot.tree().getTreeItem("General").expand();
		//swtbot.tree().getTreeItem("General").getNode("Progress").select();
		//swtbot.button("Open").click();
		//swtbot.viewByTitle("Progress");
		if (!waitForBuild())
			fail("APK not created");
		//synchronized(adtStartupService) {
		//	adtStartupService.wait();
		//}
	}

	private boolean waitForBuild() throws InterruptedException {
		IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();
		IProject project = workspaceRoot.getProject(PROJECT_NAME);
		if (project == null)
			fail("Project not found in workspace");
		if (!projectCreated(project)) {
			
			IWorkspace workspace = workspaceRoot.getWorkspace();
			   IResourceChangeListener changeListener = new IResourceChangeListener() {
			      public void resourceChanged(IResourceChangeEvent event) {
			    	  IResource resource = event.getResource() ;
			    	  if (resource != null) {
			    		  if (PROJECT_NAME.equals(resource.getProject().getName()) &&
			    		     resource.getName().equals("bin"))
			    			  synchronized(this) {
			    				  notifyAll();
			    			  }
			    	  }
			      }
			   };
			   workspace.addResourceChangeListener(changeListener);
			   synchronized (changeListener) {
				   changeListener.wait(30000);
			   }
			   workspace.removeResourceChangeListener(changeListener);
			   if (!projectCreated(project)) {
					//fail("Timed out waiting for project creation");
				   waitForShutdown();
			   }
		}
		IPath projectPath = project.getFile("bin").getLocation();
		if (projectPath == null)
			fail("Project bin file not found");
		File outputFile = projectPath.toFile();
        WatchService watchService = null;
        boolean apkCreated = false;
        try {
        	watchService  = FileSystems.getDefault().newWatchService();
            WatchKey watchKey = outputFile.toPath().register(watchService, StandardWatchEventKinds.ENTRY_CREATE);
            // Wait up to 2 minutes for the output files to be created
            while (!apkCreated && (watchKey = watchService.poll(2, TimeUnit.MINUTES)) != null) {
                for (WatchEvent<?> event : watchKey.pollEvents()) {
                	final Kind<?> kind = event.kind();
                	if (kind == StandardWatchEventKinds.OVERFLOW)
                		continue;
                    Path newPath = (Path) event.context();
                    if (APK_NAME.equals(newPath.getFileName().toString())) {
                    	apkCreated = true;
                    	break;
                    }
                    else if (!Files.isDirectory(newPath.toAbsolutePath())) {
                    	System.out.println("New output file: " + newPath.getFileName().toString());
                    }
                }
                if (!watchKey.reset())
                	break;
            }
        } catch (Exception e) {
        	e.printStackTrace();
        	fail();
        } finally {
        	if (watchService != null)
				try {
					watchService.close();
				} catch (IOException e) {
			}
        }
        return apkCreated;
	}

	private boolean projectCreated(IProject project) {
		return project.exists() && (project.getFile("bin").getLocation() != null);
	}

	private void waitForShutdown() throws InterruptedException {
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
