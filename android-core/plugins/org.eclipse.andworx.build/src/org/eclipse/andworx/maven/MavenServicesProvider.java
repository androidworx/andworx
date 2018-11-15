/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.eclipse.andworx.maven;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.zip.ZipUnArchiver;
import org.codehaus.plexus.components.io.fileselectors.FileInfo;
import org.codehaus.plexus.components.io.fileselectors.FileSelector;
import org.codehaus.plexus.logging.Logger;
import org.eclipse.aether.metadata.DefaultMetadata;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.metadata.Metadata.Nature;
import org.eclipse.aether.repository.NoLocalRepositoryManagerException;
import org.eclipse.andworx.exception.AndworxException;
import org.eclipse.andworx.repo.ProjectRepository;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.m2e.core.embedder.ICallable;
import org.eclipse.m2e.core.embedder.IMaven;
import org.eclipse.m2e.core.embedder.IMavenExecutionContext;
import org.eclipse.m2e.core.internal.IMavenConstants;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Text;

import com.android.SdkConstants;
import com.google.common.base.Charsets;
import com.google.common.io.CharSink;
import com.google.common.util.concurrent.SettableFuture;

public class MavenServicesProvider implements MavenServices {
	private static String CLASSES_JAR = "classes.jar";
	private static Map<String, String> EMPTY_PROPERTIES = Collections.emptyMap();

	/** Entry point for all Maven functionality in m2e */
    private final IMaven maven;

    /**
     * Construct MavenServicesProvider object
     */
	public MavenServicesProvider(IMaven maven) {
		this.maven = maven;
	}

	/**
	 * Returns POM file name with "xml" extension as specified by m2e library
	 * @return POM file name
	 */
	@Override
	public String getPomFilename() {
		return IMavenConstants.POM_FILE_NAME;
	}

	/**
	 * Create a Maven project wrapper for configuration and resolution of aar and jar dependencies
	 * @param mavenProject Maven project which provides runtime values based on a POM
	 * @return AndworxMavenProject object
	 */
	@Override
	public AndworxMavenProject createAndworxProject(MavenProject mavenProject) {
		return new AndworxMavenProject(mavenProject);
	}

	/**
	 * Configure dependency AARs. For each archive, an expanded copy is created and stored in a dedicated Maven repository.
	 * @param mavenProject A Maven project wrapper for configuration and resolution of aar and jar dependencies
	 * @param repositoryLocation Location of the library projects repository
	 * @return ProjectRepository object
	 */
	@Override
	public ProjectRepository configureLibraries(
			AndworxMavenProject mavenProject, 
			File repositoryLocation) {
        final SettableFuture<ProjectRepository> actualResult = SettableFuture.create();
		Job job = new Job("Configure libraries") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
		        try { 
		        	actualResult.set(doConfigureLibraries(mavenProject, repositoryLocation));
		        	return Status.OK_STATUS;
		        } catch (AndworxException e) {
		            actualResult.setException(e);
		            return Status.CANCEL_STATUS;
		        } catch (Exception e) {
		            actualResult.setException(e);
		            return Status.CANCEL_STATUS;
		        }
			}};
		job.schedule();
        try {
			return actualResult.get();
		} catch (InterruptedException e) {
        	throw new AndworxException("Library configuration interrupted");
		} catch (ExecutionException e) {
        	throw new AndworxException("Error while configuring libraries", e.getCause());
		}
	}

    /**
     * Build and return a Maven project object from a POM file
     * @param pomXml POM
     * @return MavenProject object
     */
	@Override
	public MavenProject readMavenProject(File pomXml) {
        ICallable<MavenExecutionResult> mavenExecution = 
    	new ICallable<MavenExecutionResult>() {
            public MavenExecutionResult call(IMavenExecutionContext context, IProgressMonitor innerMonitor) throws CoreException {
				ProjectBuildingRequest configuration = context.newProjectBuildingRequest();
		        configuration.setResolveDependencies(true);
                return maven.readMavenProject(pomXml, configuration);
            }};
		try {            
	        IMavenExecutionContext context = maven.createExecutionContext();
	        MavenExecutionResult result = context.execute(mavenExecution, new NullProgressMonitor());
		    if (result.hasExceptions() || (result.getProject() == null)) {
		    	String message = "Error translating pom " + pomXml.getName();
		    	if (result.hasExceptions())
		    		throw new AndworxException(message, result.getExceptions().get(0));
		    	else // Null result is not expected without exceptions, so this  probably is not needed
		            throw new AndworxException(message);
		    }
		    return result.getProject();
		} catch (CoreException e) {
            throw new AndworxException(pomXml.toString(), e);
		}
	}

    /**
     * Create a POM file from a given model 
     * @param pomFile The file to create. If one already exists, it will be deleted
     * @param model Project model
     */
	@Override
	public void createMavenModel(File pomFile, Model model) {
        final SettableFuture<ProjectRepository> actualResult = SettableFuture.create();
		Job job = new Job("Configure libraries") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
		        try { 
				    if (pomFile.exists()) {
				        pomFile.delete();
				    }
			        ByteArrayOutputStream buf = new ByteArrayOutputStream();
			        maven.writeModel(model, buf);
			        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
			        documentBuilderFactory.setNamespaceAware(false);
			        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
			        Document document = documentBuilder.parse(new ByteArrayInputStream(buf.toByteArray()));
			        Element documentElement = document.getDocumentElement();
			        // Add modelVersion element which is strangely missing
			        Element modelVersion = document.createElement("modelVersion");
			        Text textNode = document.createTextNode("4.0.0");
			        modelVersion.appendChild(textNode);
					documentElement.appendChild(modelVersion);
			        TransformerFactory transfac = TransformerFactory.newInstance();
			        Transformer trans = transfac.newTransformer();
			        trans.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes"); //$NON-NLS-1$
			        buf.reset();
			        trans.transform(new DOMSource(document), new StreamResult(buf));
			        CharSink sink = com.google.common.io.Files.asCharSink(pomFile, Charsets.UTF_8);
			        sink.write(buf.toString());
		        	actualResult.set(null);
		        	return Status.OK_STATUS;
		        } catch (Exception e) {
		            actualResult.setException(e);
		            return Status.CANCEL_STATUS;
		        }
			}};
		job.schedule();
        try {
			actualResult.get();
		} catch (Exception e) {
        	throw new AndworxException("Error creating file " + pomFile.getAbsolutePath(), e);
		}
	}

	/**
	 * Configure repository containing expanded dependency AARs. 
	 * @param mavenProject
	 * @param repositoryLocation
	 * @return ProjectRepository object
	 * @throws NoLocalRepositoryManagerException
	 * @throws IOException
	 */
    private ProjectRepository doConfigureLibraries(
    		AndworxMavenProject mavenProject,
			File repositoryLocation) throws NoLocalRepositoryManagerException, IOException {
       	// Create a local repository manager to store library projects
		ProjectRepository projectRepository = new ProjectRepository(repositoryLocation);
        for (MavenDependency dependency : mavenProject.getLibraryDependencies()) {
            // Creates a new metadata for the groupId:artifactId:version level with the specific type and nature.
        	Metadata metadata = new DefaultMetadata(
        			dependency.getGroupId(), 
        			dependency.getArtifactId(), 
        			dependency.getVersion(), 
        			SdkConstants.EXT_AAR, // Set type to expected packaging type of "aar" 
        			Nature.RELEASE_OR_SNAPSHOT,
        			EMPTY_PROPERTIES, 
                    null);
        	// If metadata exists in repository, the library project exists
	        File matadataPath = projectRepository.getMetadataPath(metadata);
	        if (!matadataPath.exists()) {
	        	// Resolve aar and associated pom artifacts
	            Artifact aarArtifact = dependency.getArtifact();
	            Artifact pomArtifact = resolve(dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion(), "pom",
	            		mavenProject.getProject().getRemoteArtifactRepositories());
	            // Record source pom location in metadata
	            metadata = metadata.setFile(pomArtifact.getFile());
	        	projectRepository.addMetaData(metadata);
	        	// Expand aar into new repository archive location
    			expandArchive(aarArtifact, matadataPath.getParentFile());
	        }
        }
		return projectRepository;
    }

    /**
     * Resolves specified artifact from specified remote repositories.
     * @param groupId
     * @param artifactId
     * @param version
     * @param type
     * @param artifactRepositories Artifact respositories of Maven project
     * @return Artifact resolved artifact
    */
	private Artifact resolve(String groupId, String artifactId, String version, String type,
            List<ArtifactRepository> artifactRepositories) {
        try {
            return maven.resolve(groupId, artifactId, version, type, null, artifactRepositories,
                    new NullProgressMonitor());
        } catch (CoreException e) {
            throw new AndworxException("Error resolving artifact", e);
        }
    }

    /**
     * Expands AAR into specified target location. The classes.jar is relocated to libs folder and 
     * renamed to artifact ID.
     * @param aarArtifact Artifact object
     * @param target Path to repository location 
     * @throws IOException if error occurs copying a file or creating a directory
     */
    private void expandArchive(Artifact aarArtifact, File target) throws IOException {
        final UnArchiver unArchiver = new ZipUnArchiver(aarArtifact.getFile()) 
        {
            @Override
            protected Logger getLogger()
            {
                return new MavenLog(MavenServicesProvider.class.getName());
            }
        };
  
    	boolean[] hasClassesJar = new boolean[] { false };
        final FileSelector exclusionFilter = new FileSelector()
        {
            @Override
            public boolean isSelected( FileInfo fileInfo) throws IOException
            {
            	boolean isClassesJar = fileInfo.getName().equals(CLASSES_JAR);
            	if (isClassesJar)
            		hasClassesJar[0] = true;
                return !isClassesJar;
            }
        };

        unArchiver.setDestDirectory(target);
        unArchiver.setFileSelectors(new FileSelector[] {exclusionFilter});
        try
        {
            unArchiver.extract();
        }
        catch (ArchiverException e)
        {
            throw new AndworxException("Error expanding " + aarArtifact.getFile().getAbsolutePath(), e);
        }
        if (hasClassesJar[0]) {
            final Path zipPath = Paths.get(aarArtifact.getFile().toURI());
            final URI uri = URI.create("jar:" + zipPath.toUri());
            FileSystem zipAsFileSystem = FileSystems.newFileSystem(uri, Collections.emptyMap());
            try {
    			Iterable<Path> roots = zipAsFileSystem.getRootDirectories();
    			Iterator<Path> iterator = roots.iterator();
    			if (iterator.hasNext()) {
     				Path root = iterator.next();
    		        Files.walk(root).forEach(item -> {
    			       	if (!item.equals(root)) {
    						String filename = item.getFileName().toString();
    						if (CLASSES_JAR.equals(filename)) {
        						Path source = root.resolve(filename);
        						{
	    							Path dest = target.toPath().resolve("libs/" + aarArtifact.getArtifactId() + ".jar");
	    							try {
										Files.copy(source, dest);
										if (!hasClasses(dest)) {
											Files.delete(dest);
											Files.delete(target.toPath().resolve("libs"));
										}
											
									} catch (IOException e) {
										throw new AndworxException("Error copying classes.jar to " + dest.toString(), e);
									}
        						}
    						}
    			       	}
    		        });
    			}
            } finally {	
    			quietlyClose(zipAsFileSystem);
            }
        }
    }

    /**
     * Returns flag set true if archive at specified location contains a root file named "classes.jar"
     * @param jarFile
     * @return
     */
    private boolean hasClasses(Path jarFile) {
		ZipFile zipFile = null;
        try { 
    	   zipFile = new ZipFile(jarFile.toFile());

    	    Enumeration<? extends ZipEntry> entries = zipFile.entries();

    	    while (entries.hasMoreElements()){
    	        ZipEntry entry = entries.nextElement();
    	        if (entry.getName().endsWith(".class"))
    	        	return true;
    	    }
        } catch (ZipException e) {
			return false;
		} catch (IOException e) {
			return false;
		} finally {	
			if (zipFile != null)
				try {
					zipFile.close();
				} catch (IOException ignore) {
				}
        }
		return false;
	}

    /**
     * Quietly close the archive
     * @param fs
     */
	private void quietlyClose(FileSystem fs) {
    	try { fs.close(); } catch (IOException ignore) {};
	}


}
