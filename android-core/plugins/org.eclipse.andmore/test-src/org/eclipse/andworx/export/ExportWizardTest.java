package org.eclipse.andworx.export;

import static com.android.builder.core.BuilderConstants.RELEASE;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;

import org.eclipse.andmore.AndmoreAndroidConstants;
import org.eclipse.andmore.base.AndworxJob;
import org.eclipse.andmore.base.BaseContext;
import org.eclipse.andmore.base.BasePlugin;
import org.eclipse.andmore.base.JavaProjectHelper;
import org.eclipse.andmore.base.resources.PluginResourceProvider;
import org.eclipse.andmore.base.resources.PluginResourceRegistry;
import org.eclipse.andworx.build.AndworxContext;
import org.eclipse.andworx.build.AndworxFactory;
import org.eclipse.andworx.config.ConfigContext;
import org.eclipse.andworx.config.SecurityController;
import org.eclipse.andworx.context.VariantContext;
import org.eclipse.andworx.core.AndworxVariantConfiguration;
import org.eclipse.andworx.entity.SigningConfigBean;
import org.eclipse.andworx.helper.JavaLabelProvider;
import org.eclipse.andworx.project.AndroidManifestData;
import org.eclipse.andworx.project.AndworxProject;
import org.eclipse.andworx.project.Identity;
import org.eclipse.andworx.project.ProjectProfile;
import org.eclipse.andworx.registry.ProjectRegistry;
import org.eclipse.andworx.registry.ProjectState;
import org.eclipse.andworx.wizards.export.ExportAndroidWizard;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.jobs.IJobFunction;
import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.e4.core.services.events.IEventBroker;
import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.widgets.Shell;
//import org.eclipse.swtbot.swt.finder.exceptions.WidgetNotFoundException;
//import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.ui.IWorkbench;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import com.android.builder.model.SigningConfig;
import com.android.sdklib.repository.AndroidSdkHandler;

public class ExportWizardTest {
    private static final int PROJECT_ID = 7;

    static class TestSecurityController extends SecurityController {
    	public TestSecurityController() {
    		super();
    	}
    	
    	@Override
    	public ConfigContext configContext(ProjectState projectState, SigningConfigBean signingConfigBean) {
    		return new ConfigContext(projectState, signingConfigBean) {
    			ConfigContext superConfigContext =
    					TestSecurityController.super.configContext(projectState, signingConfigBean);
				@Override
				public void update(SigningConfigBean updateBean) {
					superConfigContext.update(updateBean);
				}

				@Override
				public void persist() {
				}};
    	}
    }
 
    static class TestPluginResourceProvider implements PluginResourceProvider {

    	String pluginId;
    	ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

    	public TestPluginResourceProvider(String pluginId) {
    		this.pluginId = pluginId;
    	}
    	
		@Override
		public ImageDescriptor descriptorFromPath(String imagePath) {
			try {
				return imageDescriptorFromPlugin(imagePath);
			} catch (URISyntaxException | MalformedURLException e) {
				e.printStackTrace();
				fail(e.getMessage());
				return null;
			}
        }

	    private ImageDescriptor imageDescriptorFromPlugin(String imagePath) throws URISyntaxException, MalformedURLException {
	    	String path = imagePath;
	    	URL url = classLoader.getResource("");
	    	if (url == null)
	    		throw new IllegalArgumentException("Image path " + imagePath + " not found");
	    	File file = Paths.get(url.toURI()).toFile();
    		File rootfile = file.getParentFile().getParentFile().getParentFile().getParentFile();
    		File imageFile;
	    	if (pluginId.equals(BasePlugin.PLUGIN_ID))
	    		imageFile = new File(rootfile, "andmore-swt/org.eclipse.andmore.swt/" + imagePath);
	    	else
	    		imageFile = new File(rootfile, "android-core/plugins/org.eclipse.andmore/" + imagePath);
	    	return ImageDescriptor.createFromURL(imageFile.toURI().toURL());
	    }
    }
  
    class TestJavaProjectHelper extends JavaProjectHelper {
    	public IFolder getJavaOutputFolder(IProject project) {
    		return testFolder;
    	}
        public IJavaModel getJavaModel() {
        	return testJavaModel;
        }
        public IJavaProject getJavaProject(IProject project) {
        	return testJavaProject;
        }
        public IJavaProject[] getProjectsByNature(String nature) {
        	return new IJavaProject[] {testJavaProject};
        }
        public ILabelProvider getJavaElementLabelProvider() {
        	return new JavaLabelProvider();
        }
    }
/*
     	when(testJavaProjectHelper.getJavaProject(testProject)).thenReturn(testJavaProject);
    	when(testJavaProjectHelper.getJavaModel()).thenReturn(testJavaModel);
    	when(testJavaProjectHelper.getProjectsByNature(isA(IJavaProject[].class), AndmoreAndroidConstants.NATURE_DEFAULT))
		.thenReturn(new IJavaProject[] {testJavaProject});
    	when(testJavaProjectHelper.getJavaOutputFolder(testProject)).thenReturn(testFolder);
    	when(testJavaProjectHelper.getJavaElementLabelProvider()).thenReturn(new JavaLabelProvider());
 */
	@Mock
	IWorkbench testWorkbench; 
    @Mock
    IProject testProject;
    @Mock
    IJavaProject testJavaProject;
    @Mock
    IPath testPath;
    @Mock
    IFolder testFolder;
    @Mock
    AndworxContext testAndworxContext;
    @Mock 
    VariantContext testVariantContext;
    @Mock
    AndworxVariantConfiguration testVariantConfig;
    @Mock
    ProjectRegistry testProjectRegistry;
    @Mock
    ProjectState testProjectState;
    @Mock
    AndworxProject testAndworxProject;
    @Mock
    IJavaModel testJavaModel;
    @Mock
    ProjectProfile testProfile;
    @Mock
    IEclipseContext testEclipseContext;
    @Mock
    IEventBroker testEventBroker;

    AndroidManifestData testManifestData = new AndroidManifestData();
    SigningConfig signingConfig;
    TestSecurityController securityController;
    TestJavaProjectHelper javaProjectHelper;

    @Rule public MockitoRule mockitoRule = MockitoJUnit.rule(); 

    private static boolean DO_SWT_TEST = false;
	//private static SWTWorkbenchBot swtbot;
	private static Shell shell;

	@BeforeClass
	public static void initBot() throws InterruptedException {
		//Display display = Display.getDefault();
    	shell = null; //new Shell(display);
    	//if (DO_SWT_TEST)
    	//	swtbot = new SWTWorkbenchBot();
	}
	 
	@AfterClass
	public static void afterClass() {
		if (shell != null)
			shell.dispose();
	}

	@Before 
	public void setUp() {
		signingConfig = new SigningConfigBean(RELEASE);
		securityController = new TestSecurityController();
		javaProjectHelper = new TestJavaProjectHelper();
	}
	
    @After
    public void tearDown() throws IOException {
    }
 
    @Test
    public void resourceTest() {
    	ClassLoader classLoader = ExportWizardTest.class.getClassLoader();
    	URL url = classLoader.getResource("");
    	if (url != null)
    		System.out.println(url.toExternalForm());
    }
    
    @Test
    public void testExportDialog() throws Exception {
		//when(testJavaProjectHelper.getJavaOutputFolder(isA(IProject.class))).thenReturn(testFolder);
		PluginResourceRegistry resourceRegistry = new PluginResourceRegistry();
		setPluginResourceProvider(resourceRegistry, AndmoreAndroidConstants.PLUGIN_ID);
		setPluginResourceProvider(resourceRegistry, BasePlugin.PLUGIN_ID);
		BaseContext baseContext = new BaseContext() {

			@Override
			public PluginResourceRegistry getPluginResourceRegistry() {
				return resourceRegistry;
			}

			@Override
			public JavaProjectHelper getJavaProjectHelper() {
				return javaProjectHelper;
			}

			@Override
			public IEclipseContext getEclipseContext() {
				return null;
			}

			@Override
			public AndworxJob getAndworxJob(String name, IJobFunction jobFunction) {
				return null;
			}

			@Override
			public AndroidSdkHandler getAndroidSdkHandler(File localPath) {
				return null;
			}};
		BasePlugin.setBaseContext(baseContext);
		AndworxFactory.setAndworxContext(testAndworxContext);
		when(testProject.getName()).thenReturn("Permissions");
    	when(testProject.hasNature(JavaCore.NATURE_ID)).thenReturn(true);
    	when(testProject.hasNature(AndmoreAndroidConstants.NATURE_DEFAULT)).thenReturn(true);
    	when(testProject.getReferencedProjects()).thenReturn(new IProject[0]);
    	when(testProject.exists()).thenReturn(true);
    	when(testProject.isOpen()).thenReturn(true);
    	when(testProject.getType()).thenReturn(IResource.PROJECT);
    	testManifestData.debuggable = Boolean.FALSE;
    	when(testVariantConfig.getSigningConfig()).thenReturn(signingConfig);
    	when(testVariantContext.getVariantConfiguration()).thenReturn(testVariantConfig);
    	when(testAndworxContext.getPluginResourceRegistry()).thenReturn(resourceRegistry);
    	when(testAndworxContext.getJavaProjectHelper()).thenReturn(javaProjectHelper);
    	when(testEclipseContext.get(IEventBroker.class.getName())).thenReturn(testEventBroker);
    	when(testAndworxContext.getEclipseContext()).thenReturn(testEclipseContext);
    	when(testAndworxProject.parseManifest()).thenReturn(testManifestData);
    	when(testAndworxProject.getContext(RELEASE)).thenReturn(testVariantContext);
    	when(testJavaProject.getElementName()).thenReturn("Permissions");
    	when(testJavaProject.getOutputLocation()).thenReturn(testPath);
    	when(testJavaProject.getProject()).thenReturn(testProject);
    	when(testProjectState.getAndworxProject()).thenReturn(testAndworxProject);
    	when(testProjectState.getProfile()).thenReturn(testProfile);
    	when(testProjectRegistry.getProjectState(testProject)).thenReturn(testProjectState);
    	when(testAndworxContext.getProjectState(testProject)).thenReturn(testProjectState);
    	when(testAndworxContext.getProjectRegistry()).thenReturn(testProjectRegistry);
    	when(testProfile.getIdentity()).thenReturn(new Identity("com.android.example", "permissions", "1.0.0"));
    	when(testProfile.getProjectId()).thenReturn(PROJECT_ID);
    	when(testAndworxContext.getSecurityController()).thenReturn(securityController);
    	when(testJavaModel.getJavaProjects()).thenReturn(new IJavaProject[] {testJavaProject});
    	when(testJavaModel.getJavaProject("Permissions")).thenReturn(testJavaProject);
		ExportAndroidWizard exportAndroidPage = new ExportAndroidWizard();
		IStructuredSelection selection = new IStructuredSelection() {

			@Override
			public boolean isEmpty() {
				return false;
			}

			@Override
			public Object getFirstElement() {
				return testProject;
			}

			@SuppressWarnings("rawtypes")
			@Override
			public Iterator iterator() {
				return null;
			}

			@Override
			public int size() {
				return 0;
			}

			@Override
			public Object[] toArray() {
				// TODO Auto-generated method stub
				return null;
			}

			@SuppressWarnings("rawtypes")
			@Override
			public List toList() {
				// TODO Auto-generated method stub
				return null;
			}};
		exportAndroidPage.init(testWorkbench, selection);
		WizardDialog wizardDialog = new WizardDialog(shell, exportAndroidPage);
		/*
    	if (DO_SWT_TEST) {
			wizardDialog.setBlockOnOpen(false);
			wizardDialog.open();
			try {
				SWTBotShell activeShell = swtbot.activeShell();
				boolean seeShell = activeShell != null;
				System.out.println("SWTBot sees active shell = " + seeShell);
				if (seeShell) {
					//System.out.println("\"" + activeShell.getText() + "\"");
					activeShell.bot().button("Cancel").click();
				}
			} catch (WidgetNotFoundException e) {
				e.printStackTrace();
			}
			swtbot.sleep(1000);
			if (wizardDialog.getReturnCode() != Window.CANCEL)
				wizardDialog.close();
    	}
    	else {*/
			wizardDialog.open();
	    	/*
     		Display display = Display.getDefault();
    		IShellProvider shellProvider = new IShellProvider() {

				@Override
				public Shell getShell() {
					return shell;
				}};
			SigningConfigBean signingConfig = getSigningConfigBean(RELEASE);
			String title = String.format("Configure %s Signing Information", signingConfig.getName());
			SecurityController securityController = new SecurityController();
			SigningConfigDialog signingConfigDialog = new SigningConfigDialog(shellProvider, title, signingConfig, securityController);
			display.syncExec(new Runnable() {
	
				@Override
				public void run() {
					signingConfigDialog.open();
				}});
    	*/
    	//}
    }
    
	private SigningConfigBean getSigningConfigBean(String name) {
    	return new SigningConfigBean(name);
    }
    
    private void setPluginResourceProvider(PluginResourceRegistry resourceRegistry, String pluginId) {
		resourceRegistry.putResourceProvider(pluginId, new TestPluginResourceProvider(pluginId));
	}
}
