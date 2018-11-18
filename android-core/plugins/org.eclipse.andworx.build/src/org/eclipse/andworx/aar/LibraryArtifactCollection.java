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
package org.eclipse.andworx.aar;


import static com.android.SdkConstants.EXT_AAR;
import static com.android.SdkConstants.FN_ANDROID_MANIFEST_XML;
import static com.android.SdkConstants.FN_RESOURCE_TEXT;
import static org.eclipse.andworx.api.attributes.AndroidArtifacts.ArtifactType.SYMBOL_LIST_WITH_PACKAGE_NAME;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.eclipse.andworx.api.attributes.AndroidArchiveSet;
import org.eclipse.andworx.api.attributes.AndroidArtifacts.ArtifactType;
import org.eclipse.andworx.build.AndworxFactory;
import org.eclipse.andworx.api.attributes.ArtifactCollection;
import org.eclipse.andworx.exception.AndworxException;
import org.eclipse.andworx.file.FileManager;
import org.eclipse.andworx.log.SdkLogger;
import org.eclipse.andworx.repo.ProjectRepository;

import com.android.SdkConstants;
import com.android.ide.common.res2.MergingException;
import com.android.ide.common.res2.ResourceSet;
import com.android.ide.common.symbols.SymbolIo;
import com.android.utils.ILogger;

/**
 * Container for resource sets and SYMBOL_LIST_WITH_PACKAGE_NAME artifacts
 */
public class LibraryArtifactCollection extends ArtifactCollection {

	/** Remembers meta files found while navigating a directory tree */
	private class MetaFiles {
		public Path manifest;
		public Path rTxt;
	}
	
	/** Logger may be provided */
	private final ILogger logger;
    /** File manager to store meta files */
	private final FileManager fileManager;
	/** Resolved files local file system locations */
	protected Set<File> fileSet;
	/** For each AAR, maps manifest and r.txt to package-aware-r.txt generated file */
	protected Map<File,MetaFiles> metaFilesMap;
	/** List of resource sets collected by scanning all artifact locations */
    private List<ResourceSet> resourceSetList ;
    /** Maps ArtifactType to AndroidArchiveSe */
    private AarArtifacts aarArtifacts;
    /** Flag set true if validation is enabled */
    private boolean validateEnabled;

	/**
	 * Construct a LibraryArtifactCollection object
	 * @param name Name of project which owns this collection
	 * @param validateEnabled Flag set true if validation is enabled
	 * @param logger Optional logger 
	 */
	public LibraryArtifactCollection(String name, boolean validateEnabled, ILogger logger) {
		super(name);
		this.validateEnabled = validateEnabled;
		this.logger = logger;
		if (logger == null)
			logger = SdkLogger.getLogger(LibraryArtifactCollection.class.getName());
		this.fileManager = AndworxFactory.instance().getFileManager();
		fileSet = Collections.emptySet();
		resourceSetList = new ArrayList<>();
		aarArtifacts = new AarArtifacts(false);
		metaFilesMap = new TreeMap<>();
	}

	/**
	 * Add resources to this collection from given exploded AAR
	 * @param explodedAar Expanded AAR location
	 * @throws MergingException
	 */
	public void add(File explodedAar) throws MergingException {
		try {
			aarArtifacts.addExplodedAar(explodedAar);
			ResourceSet resourceSet = addResource(explodedAar);
			resourceSet.loadFromFiles(logger);
			if (!resourceSet.isEmpty())
				resourceSetList.add(resourceSet);
		} catch (IOException e) {
			throw new AndworxException("Error installing artfact " + explodedAar.getPath(), e);
		}
		if (fileSet.isEmpty())
			fileSet = new TreeSet<File>();
		fileSet.add(explodedAar);
	}

	/**
	 * Collect resources for given exploded AAR
	 * @param explodedAar Expanded AAR location
	 * @return ResourceSet object
	 * @throws IOException
	 */
	private ResourceSet addResource(File explodedAar) throws IOException {
        ResourceSet resourceSet = resourceSetInstance(explodedAar);
		final MetaFiles metafiles = new MetaFiles();
		// Walk project root directory to find "res" folder, manifest and r.txt
		Path root = explodedAar.toPath();
        Files.walk(root).forEach(item -> {
	       	if (!item.equals(root)) {
	       		Path source = null;
				String filename = item.getFileName().toString();
				boolean isResourceDir = filename.startsWith(SdkConstants.FD_RES);
				if (isResourceDir) {
					File sourceFile = root.resolve(filename).toFile();
                	resourceSet.addSource(sourceFile);
				}
				else if (filename.equals(FN_ANDROID_MANIFEST_XML)) {
					source = root.resolve(filename);
					metafiles.manifest = source;
				}
				else if (filename.equals(FN_RESOURCE_TEXT)) {
					source = root.resolve(filename);
					metafiles.rTxt = source;
				}
			}
		});
        if (metafiles.manifest != null) {
    		String artifactPath = name + "/" + SYMBOL_LIST_WITH_PACKAGE_NAME + "/" + getArtifactId(explodedAar, true);
        	Path artifactDir = fileManager.prepareTargetPath(artifactPath);
         	metaFilesMap.put(getPackageAwareR(artifactDir), metafiles);
        }
        return resourceSet;
    }

	/**
	 * Returns artifact ID of library contained in given repository location
	 * @param explodedAar Expanded AAR location
	 * @param includeGroupId Flag set true if groud id to be included
	 * @return artifact ID
	 * @throws IOException
	 */
	private String getArtifactId(File explodedAar, boolean includeGroupId) throws IOException {
		Properties props = getArtifactPropeerties(explodedAar);
		String artifactId = props.getProperty(ProjectRepository.ARTFACT_KEY);
		if (artifactId == null) // This is not expected
			throw new IOException(EXT_AAR + "-local missing artifact property (key = " + ProjectRepository.ARTFACT_KEY + ")");
		if (includeGroupId) {
			String groupId = props.getProperty(ProjectRepository.GROUP_ID_KEY);
			if (groupId == null)
				throw new IOException(EXT_AAR + "-local missing artiface property (key = " + ProjectRepository.GROUP_ID_KEY + ")");
			artifactId = groupId + "." + artifactId;
		}
		return artifactId;
	}

	private Properties getArtifactPropeerties(File explodedAar) throws IOException  {
		File repoMetaFile = new File(explodedAar, EXT_AAR + "-local");
		if (!repoMetaFile.exists())
			throw new FileNotFoundException(repoMetaFile.getAbsolutePath());
		Properties props = new Properties();
		try (InputStream inStream = new FileInputStream(repoMetaFile)) {
		    props.loadFromXML(inStream);
		    return props;
		}
	}
	
	/**
     * Returns the resolved artifacts, performing the resolution if required.
     * This will resolve the artifact metadata and download the artifact files as required.
     * @throws ResolveException On failure to resolve or download any artifact.
     * @return set of File objects
     */
    @Override
    public Set<File> getArtifactFiles() {
    	return fileSet;
    }

    /**
     * Returns list of resource sets
     * @return list of ResourceSet objects
     */
    @Override
	public List<ResourceSet> getResourceSetList() {
		return resourceSetList;
	}

	@Override
	public List<File> getFiles(ArtifactType type) {
		AndroidArchiveSet androidArchiveSet = aarArtifacts.getAndroidArchiveSet(type);
		if (androidArchiveSet == null)
			throw new IllegalArgumentException("Unsupported type in AarTransform: " + type);
		return androidArchiveSet.getFiles();
	}
	
    /**
     * Returns set of artifacts of specified type derived from this artifact collection
     * @param artifactType ArtifactType enum
     * @return set of File objects
     */
    @Override
    public Set<File> getTansformCollection(ArtifactType artifactType) throws IOException {
    	if (artifactType.equals(SYMBOL_LIST_WITH_PACKAGE_NAME))
    		return transformSymbolListWithPackageName();
    	else
    	    return super.getTansformCollection(artifactType);
    }

    /**
     * Generate SymbolTableWithPackage files
     * @return set of File objects
     * @throws IOException
     */
	private Set<File>  transformSymbolListWithPackageName() throws IOException {
		for (Map.Entry<File,MetaFiles> entry: metaFilesMap.entrySet()) {
			MetaFiles metaFiles = entry.getValue();
	        if (Files.exists(metaFiles.manifest) && !entry.getKey().exists()) {
		        // R.txt May not exist in some AARs. e.g. the multidex support library.
		        SymbolIo.writeSymbolTableWithPackage(metaFiles.rTxt, metaFiles.manifest, entry.getKey().toPath());
	        }
		}
        return metaFilesMap.keySet();
	}

	/**
	 * Returns a new ResourceSet named according to given file
	 * @param file Project file
	 * @return
	 */
	private ResourceSet resourceSetInstance(File file) {
        ResourceSet resourceSet =
                new ResourceSet(
                        // TODO - Implement Artifacts with Component IDs
                        file.getName(),
                        null,
                        null,
                        validateEnabled);
        resourceSet.setFromDependency(true);
        return resourceSet;
	}

	/**
	 * Returns SymbolTableWithPackage file
	 * @param rootDir Absolute path of file
	 * @return File object
	 * @throws IOException
	 */
	private File getPackageAwareR(Path rootDir) throws IOException {
        return rootDir.resolve("package-aware-r.txt").toFile();
	}
	
/*  // Method for collecting a resource set from an archive.
    // Preserved in case it is needed in the future
	private void loadArchiveLibrary(File artifact) throws IOException, MergingException {
        final Path zipPath = Paths.get(artifact.toURI());
        final URI uri = URI.create("jar:" + zipPath.toUri());
    	FileSystem zipAsFileSystem = FileSystems.newFileSystem(uri, Collections.emptyMap());
		Iterable<Path> roots = zipAsFileSystem.getRootDirectories();
		Iterator<Path> iterator = roots.iterator();
		if (iterator.hasNext()) {
			ResourceSet resourceSet = addZipResource(index, artifact, iterator.next());
			resourceSet.loadFromFiles(logger);
			if (!resourceSet.isEmpty())
				resourceSetList.add(resourceSet);
		}
		quietlyClose(zipAsFileSystem);
 	}

	private ResourceSet addZipResource(int index, File artifact, Path root) throws IOException {
		String indexName = getIndexName(index, artifact.getName());
        ResourceSet resourceSet = resourceSetInstance(artifact);
    	File tempDir = new File(expandDir, indexName);
    	if (!tempDir.mkdirs()) {
    		throw new AndworxException("Could not create directory: " + tempDir.getPath());
    	}
    	Path tempPath = Paths.get(tempDir.toURI());
		final MetaFiles metafiles = new MetaFiles();
        Files.walk(root).forEach(item -> {
	       	if (!item.equals(root)) {
	       		Path dest = null;
	       		Path source = null;
				String filename = item.getFileName().toString();
				boolean isResourceDir = filename.startsWith(SdkConstants.FD_RES);
				if (isResourceDir) {
					dest = tempPath.resolve(filename);
					source = root.resolve(filename);
                	resourceSet.addSource(dest.toFile());
				}
				else if (filename.equals(FN_ANDROID_MANIFEST_XML)) {
					source = root.resolve(filename);
					dest = tempPath.resolve(filename);
					metafiles.manifest = dest;
				}
				else if (filename.equals(FN_RESOURCE_TEXT)) {
					source = root.resolve(filename);
					dest = tempPath.resolve(filename);
					metafiles.rTxt = dest;
				}
				if ((dest != null) && (source != null)) {
					try {
						Files.copy(source, dest);
						if (isResourceDir)
							copyTree(item, tempPath);
					} catch (IOException e) {
						throw new AndworxException("Error copying " + source.toString(), e);
					}
				}
			}
		});
        if (metafiles.manifest != null) {
        	File outputFile = getPackageAwareR(tempPath);
        	metaFilesMap.put(outputFile, metafiles);
        }
        return resourceSet;
    }

	private void copyTree(Path root, Path tempPath) throws IOException  {
        Files.walk(root).forEach(source -> {
        	if (!source.equals(root)) {
				Path dest = tempPath.resolve(source.toString().substring(1));
				boolean destExists = dest.toFile().exists();
				if (!destExists) {
					try {
						Files.copy(source, dest);
					} catch (IOException e) {
						throw new AndworxException("File copy to " + dest.toString() + " error", e);
					}
				}
				try {
				    BasicFileAttributes attrs = Files.readAttributes(source, BasicFileAttributes.class);
	                if (attrs.isDirectory()) {
	                	copyTree(source, tempPath);
	                }
				} catch (IOException e) {
					throw new AndworxException("Error copying " + source.toString(), e);
				}
        	}
        });
	}
	
    private void quietlyClose(FileSystem fs) {
    	try { fs.close(); } catch (IOException ignore) {};
	}
*/	


}
