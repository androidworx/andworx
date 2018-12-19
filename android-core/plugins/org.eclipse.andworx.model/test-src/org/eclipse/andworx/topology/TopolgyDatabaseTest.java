package org.eclipse.andworx.topology;

import static org.fest.assertions.api.Assertions.assertThat;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.zip.ZipUnArchiver;
import org.codehaus.plexus.logging.Logger;
import org.eclipse.andworx.maven.MavenLog;
import org.eclipse.andworx.modules.ModelBuilder;
import org.eclipse.andworx.modules.WorkspaceModeller;
import org.eclipse.andworx.record.ModelType;
import org.eclipse.andworx.topology.entity.ModelNode;
import org.eclipse.andworx.topology.entity.ModelTypeBean;
import org.eclipse.andworx.topology.entity.ModuleBean;
import org.eclipse.andworx.topology.entity.RepositoryBean;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.google.common.io.Files;

import au.com.cybersearch2.classyapp.ResourceEnvironment;
import au.com.cybersearch2.classyjpa.entity.EntityClassLoader;

public class TopolgyDatabaseTest {

	private static final String TEST_PROJECT_ZIP = "test-src/resources/permissions2.zip";
	
	File dataArea;
	ModelFactory modelFactory;
	private File projectLocation;
	
	@Rule
	public TemporaryFolder temporaryFolder= new TemporaryFolder();
	
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
		modelFactory = createObjectFactory();
		ModelPlugin.setModelFactory(modelFactory);
		modelFactory.startPersistenceService();
	}

	@After
	public void tearDown() throws Exception {
		modelFactory.getPersistenceService().stop();
	}

	@Test
	public void testPersistentService() throws InterruptedException, IOException {
		ModelNode parentNode = new ModelNode("Parent", "Parent node");
    	List<ModelType>  modelTypes = new ArrayList<>();
    	modelTypes.add(ModelType.allProjects);
    	modelTypes.add(ModelType.gradelBuildScript);
    	parentNode.attach(modelTypes);
		ModelNode rootNode = parentNode.getParent();
		List<ModelNode> rootChildren = rootNode.getChildren();
        assertThat(rootChildren).isNotEmpty();
    	assertThat(rootChildren.size()).isEqualTo(1);
    	assertThat(rootChildren.get(0).getName()).isEqualTo("Parent");
    	ModelNode testNode = new ModelNode("under_test", "Under test", parentNode);
    	testNode.attach(Collections.emptyList());
    	ModelNode testParent = testNode.getParent();
    	assertThat(testParent.getName()).isEqualTo(parentNode.getName());
    	assertThat(testParent.getParent().getName()).isEqualTo(ModelNodeBeanFactory.ROOT_NAME);
    	List<ModelNode> children = testParent.getChildren();
    	assertThat(children.size()).isEqualTo(1);
    	assertThat(children.get(0).getName()).isEqualTo(testNode.getName());
    	RepositoryBean repositoryBean = new RepositoryBean("google", new URL("http://maven.google.com"));
    	parentNode.attach(ModelType.allProjects, repositoryBean);
    	ModelTypeBean modelTypeBean = repositoryBean.getModelTypeBean(); 
    	assertThat(modelTypeBean.getModelType()).isEqualTo(ModelType.allProjects);
    	assertThat(modelTypeBean.getNode().getName()).isEqualTo("Parent");
    	WorkspaceModeller workspaceModeller = modelFactory.getWorkspaceModeller();
    	ModelNode nodeUnderTest = workspaceModeller.createModule(projectLocation, 0, new ModelBuilder());
    	assertThat(nodeUnderTest).isNotNull();
    	int typesCount = nodeUnderTest.getModelTypes().size();
    	ModuleBean moduleBean = workspaceModeller.getModule(projectLocation);
    	assertThat(moduleBean).isNotNull();
    	System.out.println(moduleBean.getName());
    	File moduleIdFile = new File(projectLocation, "module_ID");
    	assertThat(moduleIdFile).exists();
    	String result = Files.asCharSource(moduleIdFile, StandardCharsets.UTF_8).read();
    	assertThat("0000000001").isEqualTo(result);
    	System.out.println("Success!");
		
	}

	private ModelFactory createObjectFactory() {
		EntityClassLoader classLoader = new EntityClassLoader() {

			@Override
			public Class<?> loadClass(String name) throws ClassNotFoundException {
				return Thread.currentThread().getContextClassLoader().loadClass(name);
			}};
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
				return null;
			}

			@Override
			public EntityClassLoader getEntityClassLoader() {
				return classLoader;
			}};
		return new DaggerFactory(
						null, 
						resourceEnvironment);
	}

}
