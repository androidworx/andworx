package org.eclipse.andworx.modules;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.zip.ZipUnArchiver;
import org.codehaus.plexus.logging.Logger;
import org.eclipse.andmore.base.AndworxJob;
import org.eclipse.andmore.base.BaseContext;
import org.eclipse.andmore.base.BasePlugin;
import org.eclipse.andmore.base.JavaProjectHelper;
import org.eclipse.andmore.base.resources.PluginResourceRegistry;
import org.eclipse.andworx.maven.MavenLog;
import org.eclipse.andworx.project.AndroidWizardListener;
import org.eclipse.andworx.test.TestAndroidSdkPreferences;
import org.eclipse.andworx.test.TestAndworxContext;
import org.eclipse.andworx.test.TestEclipseContext;
import org.eclipse.andworx.topology.DaggerFactory;
import org.eclipse.andworx.topology.ModelFactory;
import org.eclipse.andworx.topology.ModelPlugin;
import org.eclipse.andworx.topology.TopolgyDatabaseTest;
import org.eclipse.core.runtime.jobs.IJobFunction;
import org.eclipse.e4.core.contexts.IEclipseContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.android.sdklib.repository.AndroidSdkHandler;
import com.j256.ormlite.logger.LoggerFactory;
import com.j256.ormlite.logger.LoggerFactory.LogType;

import au.com.cybersearch2.classyapp.ResourceEnvironment;
import au.com.cybersearch2.classyjpa.entity.EntityClassLoader;

public class ModelProjectReaderTest {

	private static final String TEST_PROJECT_ZIP = "test-src/resources/permissions2.zip";
	private static final String GROUP_ID = "com.android.example";
	private static final String ARTIFACT_ID = "permissions";
	private static final String VERSION = "1.0.0-SNAPSHOT";
	

    TestAndworxContext testAndworxContext;
	AndroidWizardListener androidWizardListener;
    IEclipseContext eclipseContext;
    File databaseDirectory;
	
	File dataArea;
	private File projectLocation;
	private File andworxBuildFile;
	ModelFactory modelFactory;
	TestAndroidSdkPreferences androidSdkPreferences;
	EntityClassLoader classLoader = new EntityClassLoader() {

		@Override
		public Class<?> loadClass(String name) throws ClassNotFoundException {
			return Thread.currentThread().getContextClassLoader().loadClass(name);
		}};
	
	@Rule
	public TemporaryFolder temporaryFolder= new TemporaryFolder();
	
	@Before
	public void setUp() throws Exception {

    	// Set ORMLite system property to select local Logger
        System.setProperty(LoggerFactory.LOG_TYPE_SYSTEM_PROPERTY, LogType.LOG4J.name());
		dataArea = temporaryFolder.getRoot();
		File permissionsZip = new File(TEST_PROJECT_ZIP);
		final UnArchiver unArchiver = new ZipUnArchiver(permissionsZip) 
        {
            @Override
            protected Logger getLogger()
            {
                return new MavenLog(TopolgyDatabaseTest.class.getName());
            }
        };
        unArchiver.setDestDirectory(dataArea);
        unArchiver.extract();
        projectLocation = new File(dataArea, "permissions");
    	databaseDirectory = new File(dataArea, ".andworx-model");
        File buildPersistenceXml = new File("test-src/resources/persistence.xml");
        FileInputStream inputStream = new FileInputStream(buildPersistenceXml);
        File tempDir = new File(dataArea, "files/META-INF");
        tempDir.mkdirs();
        buildPersistenceXml = new File(tempDir, "persistence.xml");
        copy(inputStream, buildPersistenceXml.toPath());
        eclipseContext = new TestEclipseContext();
		BaseContext baseContext = new BaseContext() {
       
			@Override
			public PluginResourceRegistry getPluginResourceRegistry() {
				return null;
			}

			@Override
			public JavaProjectHelper getJavaProjectHelper() {
				return null;
			}

			@Override
			public IEclipseContext getEclipseContext() {
				return eclipseContext;
			}

			@Override
			public AndworxJob getAndworxJob(String name, IJobFunction jobFunction) {
				return new AndworxJob(name, jobFunction);
			}

			@Override
			public AndroidSdkHandler getAndroidSdkHandler(File localPath) {
				return BaseContext.container.getBaseContext().getAndroidSdkHandler(localPath);
			}
		};
		BasePlugin.setBaseContext(baseContext);
		androidSdkPreferences = new TestAndroidSdkPreferences();
		testAndworxContext = new TestAndworxContext(null, classLoader, dataArea, androidSdkPreferences);
		testAndworxContext.loadSdk(new File(System.getenv("ANDROID_HOME")));
		testAndworxContext.startPersistenceService();
		andworxBuildFile = new File(projectLocation, "Application/build.gradle");
		modelFactory = createObjectFactory();
		ModelPlugin.setModelFactory(modelFactory);
		modelFactory.getPersistenceContext();
		modelFactory.startPersistenceService();
	}

	@After
	public void tearDown() throws Exception {
		modelFactory.getPersistenceService().stop();
		testAndworxContext.getPersistenceService().stop();
	}

	@Test
	public void testModelProjectReader() throws InterruptedException, IOException {
		// Test requires real parser in order to orchestrate a layered application implementation
		ModelParserContext modelParserContext = new ModelParserContext();
		modelParserContext.setRootProject(projectLocation);
		ModelProjectReader modelProjectReader = new ModelProjectReader(modelFactory.getWorkspaceModeller(), androidWizardListener);
		modelProjectReader.runOpenTasks(andworxBuildFile);
		ModelProjectReader modelProjectReader2 = new ModelProjectReader(modelFactory.getWorkspaceModeller(), androidWizardListener);
		modelProjectReader2.runOpenTasks(andworxBuildFile);
	}

	private ModelFactory createObjectFactory() {
    	//File databaseDirectory = new File(dataArea, ".model-database");
    	ResourceEnvironment resourceEnvironment = new ResourceEnvironment() {

			@Override
			public InputStream openResource(String resourceName) throws IOException {
				return new FileInputStream(new File("META-INF", resourceName));
			}

			@Override
			public Locale getLocale() {
				return Locale.getDefault();
			}

			@Override
			public File getDatabaseDirectory() {
				// Null value signals use memory
				return null; //databaseDirectory;
			}

			@Override
			public EntityClassLoader getEntityClassLoader() {
				return classLoader;
			}};
		return new DaggerFactory(
						null, 
						resourceEnvironment);
	}

	/**
	 * Copy file stream to given destination
	 * @param inputStream Input stream
	 * @param destination Output file
	 * @throws IOException
	 */
	private void copy(InputStream inputStream, Path destination) throws IOException {
		Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
	}
}
