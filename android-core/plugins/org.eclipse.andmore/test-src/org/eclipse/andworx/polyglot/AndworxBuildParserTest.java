package org.eclipse.andworx.polyglot;

import static org.mockito.Mockito.*;

import java.io.File;
import java.io.IOException;

import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.zip.ZipUnArchiver;
import org.codehaus.plexus.logging.Logger;
import org.eclipse.andworx.context.AndroidEnvironment;
import org.eclipse.andworx.file.FileManager;
import org.eclipse.andworx.maven.MavenLog;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import com.android.builder.model.SigningConfig;
import com.android.builder.signing.DefaultSigningConfig;

public class AndworxBuildParserTest {

	private static final String TEST_PROJECT_ZIP = "test-src/resources/permissions2.zip";

	private File projectLocation;

	@Mock
	AndroidEnvironment androidEnvironment;
	@Mock
	FileManager fileManager;
	@Mock
	SigningConfig defaultConfig;
	
    @Rule public MockitoRule mockitoRule = MockitoJUnit.rule(); 

	@Before 
	public void setUp() throws InterruptedException {
		projectLocation =  com.google.common.io.Files.createTempDir();
		projectLocation.deleteOnExit();
		File permissionsZip = new File(TEST_PROJECT_ZIP);
		final UnArchiver unArchiver = new ZipUnArchiver(permissionsZip) 
        {
            @Override
            protected Logger getLogger()
            {
                return new MavenLog(AndworxBuildParserTest.class.getName());
            }
        };
        unArchiver.setDestDirectory(projectLocation);
        unArchiver.extract();
        projectLocation = new File(projectLocation, "permissions");
		File keystoreFile = new File("/home/andrew/.android", "debug.keystore");
        when(androidEnvironment.getDefaultDebugSigningConfig()).thenReturn(DefaultSigningConfig.debugSigningConfig(keystoreFile));
	}
	
	@Test
	public void testParseGradleBuildFile() throws IOException {
		//AndroidConfigurationBuilder receiver = new AndroidConfigurationBuilder(fileManager,projectLocation, androidEnvironment);
		//AndworxBuildParser parser = new AndworxBuildParser(new File(projectLocation, "build.gradle"), receiver);
		//parser.parse();
	}
}
