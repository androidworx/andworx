package org.eclipse.andworx.topology;

import static org.fest.assertions.api.Assertions.assertThat;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Locale;

import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.zip.ZipUnArchiver;
import org.codehaus.plexus.logging.Logger;
import org.eclipse.andmore.base.AndworxJob;
import org.eclipse.andmore.base.BaseContext;
import org.eclipse.andmore.base.BasePlugin;
import org.eclipse.andmore.base.JavaProjectHelper;
import org.eclipse.andmore.base.resources.PluginResourceRegistry;
import org.eclipse.andworx.build.AndworxContext;
import org.eclipse.andworx.build.AndworxFactory;
import org.eclipse.andworx.build.BuildConsole;
import org.eclipse.andworx.config.AndroidConfig;
import org.eclipse.andworx.context.AndroidEnvironment;
import org.eclipse.andworx.file.AndroidSourceSet;
import org.eclipse.andworx.file.FileManager;
import org.eclipse.andworx.maven.AndworxMavenProject;
import org.eclipse.andworx.maven.MavenLog;
import org.eclipse.andworx.maven.MavenServices;
import org.eclipse.andworx.model.CodeSource;
import org.eclipse.andworx.modules.ModelParserContext;
import org.eclipse.andworx.modules.ModelProjectReader;
import org.eclipse.andworx.modules.WorkspaceConfiguration;
import org.eclipse.andworx.modules.WorkspaceModeller;
import org.eclipse.andworx.polyglot.AndroidConfigurationBuilder;
import org.eclipse.andworx.polyglot.AndworxBuildParser;
import org.eclipse.andworx.project.AndroidDigest;
import org.eclipse.andworx.project.AndroidProjectOpener;
import org.eclipse.andworx.project.AndroidWizardListener;
import org.eclipse.andworx.project.AndworxParserContext;
import org.eclipse.andworx.project.CreateProfileFunction;
import org.eclipse.andworx.project.ParseBuildFunction;
import org.eclipse.andworx.sdk.SdkProfile;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobFunction;
import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.m2e.core.internal.IMavenConstants;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import com.android.ide.common.xml.ManifestData;
import com.android.sdklib.repository.AndroidSdkHandler;

import au.com.cybersearch2.classyapp.ResourceEnvironment;
import au.com.cybersearch2.classyjpa.entity.EntityClassLoader;

public class AndroidProjectOpenerTest {

	private static final String TEST_PROJECT_ZIP = "test-src/resources/permissions2.zip";

	private static final String GROUP_ID = "com.android.example";

	private static final String ARTIFACT_ID = "permissions";

	private static final String VERSION = "1.0.0-SNAPSHOT";
	
	File dataArea;
	private File projectLocation;
	private File andworxBuildFile;

	@Mock
	AndroidWizardListener androidWizardListener;
    @Mock
    AndworxContext testAndworxContext;
    @Mock
    IProgressMonitor monitor;
    @Mock
    AndworxBuildParser mockParser;
    @Mock 
    AndroidDigest androidDigest;
    @Mock
    AndroidEnvironment androidEnvironment;
    @Mock
    MavenServices mavenServices;
    @Mock
    AndroidConfig androidConfig;
    @Mock
    Model mavenModel;
    @Mock 
    MavenProject mavenProject;
    @Mock
    AndworxMavenProject andworxMavenProject;
    @Mock
    WorkspaceConfiguration workspaceConfiguration;
    @Mock
    FileManager fileManager;
 	
	@Rule
	public TemporaryFolder temporaryFolder= new TemporaryFolder();
    @Rule 
    public MockitoRule mockitoRule = MockitoJUnit.rule();

	private BuildConsole testBuildConsole = new BuildConsole() {

		@Override
		public void logAndPrintError(Throwable exception, String tag, String format, Object... args) {
	        String message = String.format(format, args);
	        System.err.print(message);
	        if (exception != null)
	        	exception.printStackTrace();
		}}; 
	
	@SuppressWarnings("unchecked")
	@Before
	public void setUp() throws Exception {
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
				return null;
			}

			@Override
			public AndworxJob getAndworxJob(String name, IJobFunction jobFunction) {
				return null;
			}

			@Override
			public AndroidSdkHandler getAndroidSdkHandler(File localPath) {
				return BaseContext.container.getBaseContext().getAndroidSdkHandler(localPath);
			}
		};
		BasePlugin.setBaseContext(baseContext);
		AndworxFactory.setAndworxContext(testAndworxContext);
		andworxBuildFile = new File(projectLocation, "Application/build.gradle");
		CodeSource codeSource = CodeSource.manifest;
		when(androidDigest.getSourceFolder(codeSource)).thenReturn(AndroidSourceSet.DEFAULT_PROJECT_ROOT + codeSource.defaultPath);
		when(androidDigest.getAndroidConfig()).thenReturn(androidConfig);
		when(androidDigest.getMavenModel()).thenReturn(mavenModel);
		when(mockParser.getAndroidDigest()).thenReturn(androidDigest);
		when(testAndworxContext.getMavenServices()).thenReturn(mavenServices);
		when(testAndworxContext.getBuildConsole()).thenReturn(testBuildConsole );
		when(androidConfig.getCompileSdkVersion()).thenReturn("android-27");
		when(androidConfig.getBuildToolsVersion()).thenReturn("27.1.3");
		when(mavenServices.getPomFilename()).thenReturn(IMavenConstants.POM_FILE_NAME);
		when(mavenServices.readMavenProject(isA(File.class))).thenReturn(mavenProject);
		when(mavenServices.createAndworxProject(mavenProject)).thenReturn(andworxMavenProject);
		when(mavenProject.getGroupId()).thenReturn(GROUP_ID);
		when(mavenProject.getArtifactId()).thenReturn(ARTIFACT_ID);
		when(mavenProject.getVersion()).thenReturn(VERSION);
		when(mavenProject.getPackaging()).thenReturn("apk");
		when(andworxMavenProject.getLibraryDependencies()).thenReturn(Collections.EMPTY_LIST);
	};
	
	@Test
	public void testAndroidProjectOpenerFunctions() throws IOException {
		ArgumentCaptor<ManifestData> manifestCaptor = ArgumentCaptor.forClass(ManifestData.class);
		doNothing().when(androidWizardListener).onManifestParsed(manifestCaptor.capture());
		ParseBuildFunction parseFunction = new ParseBuildFunction(andworxBuildFile, androidWizardListener, mockParser);
		assertThat(parseFunction.run(monitor)).isEqualTo(Status.OK_STATUS);
		verify(mockParser).parse(andworxBuildFile);
		ManifestData manifestData = manifestCaptor.getValue();
		assertThat(manifestData).isNotNull();
		CreateProfileFunction createProfileFunction = new CreateProfileFunction(androidDigest, androidWizardListener, mavenServices, androidEnvironment);
		assertThat(createProfileFunction.run(monitor)).isEqualTo(Status.OK_STATUS);
	}

	@Test
	public void testAndroidProjectOpener() throws IOException {
		ArgumentCaptor<ManifestData> manifestCaptor = ArgumentCaptor.forClass(ManifestData.class);
		doNothing().when(androidWizardListener).onManifestParsed(manifestCaptor.capture());
		AndroidProjectOpener underTest = new AndroidProjectOpener(androidWizardListener) {

			@Override
			public void runOpenTasks(File buildFile) {
				parse(buildFile, mockParser);
			}};
		underTest.runOpenTasks(andworxBuildFile);
		ManifestData manifestData = manifestCaptor.getValue();
		assertThat(manifestData).isNotNull();
	}
	
	@Test
	public void testModelProjectReader() {
	}

}
