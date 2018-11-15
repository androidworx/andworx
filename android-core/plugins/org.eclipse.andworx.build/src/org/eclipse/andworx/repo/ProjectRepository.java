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
package org.eclipse.andworx.repo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.Map;
import java.util.Properties;
import java.nio.charset.StandardCharsets;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.internal.impl.SimpleLocalRepositoryManagerFactory;
import org.eclipse.aether.metadata.DefaultMetadata;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.metadata.Metadata.Nature;
import org.eclipse.aether.repository.LocalArtifactRegistration;
import org.eclipse.aether.repository.LocalArtifactRequest;
import org.eclipse.aether.repository.LocalArtifactResult;
import org.eclipse.aether.repository.LocalMetadataRegistration;
import org.eclipse.aether.repository.LocalMetadataRequest;
import org.eclipse.aether.repository.LocalMetadataResult;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.NoLocalRepositoryManagerException;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.andworx.exception.AndworxException;
import org.eclipse.andworx.project.Identity;

/**
 * Implements repository with Maven structure to store expanded AARs
 */
public class ProjectRepository implements LocalRepositoryManager{
	public static final String ARTFACT_KEY = "meta.ArtifactId";
	public static final String GROUP_ID_KEY = "meta.GroupId";
	public static final String VERSION_KEY = "meta.Version";
	public static final String TYPE_KEY = "meta.Type";
	public static final String FILE_KEY = "meta.File";
	public static final String NATURE_KEY = "meta.Nature";
	public static final String REPOSITORY_TYPE = "simple";
	
	/** Location of repository */
	private final File repoLocation;
	/** Default repository manager - does not supprt add operations */
	private final LocalRepositoryManager localRepositoryManager;
	/** Default repository session - only dummy */
	private final DefaultRepositorySystemSession session;

	/**
	 * Construct ProjectRepository object
	 * @param repoLocation Repository location
	 * @throws NoLocalRepositoryManagerException
	 */
	public ProjectRepository(File repoLocation) throws NoLocalRepositoryManagerException {
		this.repoLocation = repoLocation;
		// Open simple type Maven respository
		LocalRepository repository = new LocalRepository(repoLocation, REPOSITORY_TYPE);
		boolean isNew = !repoLocation.exists();
		if (isNew && !repoLocation.mkdirs()) 
			throw new NoLocalRepositoryManagerException(repository, "Error creating repository path");
		// Create session to access repository manager
		session = new DefaultRepositorySystemSession();
		localRepositoryManager = new SimpleLocalRepositoryManagerFactory().newInstance(session, repository);
		// Create/validate metadata file at reopository root
		Metadata metadata= new DefaultMetadata(REPOSITORY_TYPE, Nature.SNAPSHOT);
		if (isNew) {
			LocalMetadataRegistration registration = new LocalMetadataRegistration(metadata);
			add(session, registration);
		} else {
			LocalMetadataRequest request = new LocalMetadataRequest();
			request.setMetadata(metadata);
			LocalMetadataResult result = localRepositoryManager.find(session, request);
			File metaFile = result.getFile();
			if ((metaFile == null) || !metaFile.exists())
				throw new NoLocalRepositoryManagerException(repository, "Registry validation failed at location: " + repoLocation.toString());
		}   
	}

	/**
	 * Returns artifact location for given parameters
	 * @param identity Repository identity
	 * @param classifier Classifier
	 * @param extension Extension
	 * @return File object
	 */
	public File getArtifactPath(Identity identity, String classifier, String extension) {
		Artifact artifact = new Artifact() {

			@Override
			public String getGroupId() {
				return identity.getGroupId();
			}

			@Override
			public String getArtifactId() {
				return identity.getArtifactId();
			}

			@Override
			public String getVersion() {
				return identity.getVersion();
			}

			@Override
			public Artifact setVersion(String version) {
				return null;
			}

			@Override
			public String getBaseVersion() {
				return identity.getVersion();
			}

			@Override
			public boolean isSnapshot() {
				return false;
			}

			@Override
			public String getClassifier() {
				return classifier;
			}

			@Override
			public String getExtension() {
				return extension;
			}

			@Override
			public File getFile() {
				return null;
			}

			@Override
			public Artifact setFile(File file) {
				return null;
			}

			@Override
			public String getProperty(String key, String defaultValue) {
				return null;
			}

			@Override
			public Map<String, String> getProperties() {
				return null;
			}

			@Override
			public Artifact setProperties(Map<String, String> properties) {
				return null;
			}};
		return new File(repoLocation, localRepositoryManager.getPathForLocalArtifact(artifact));
	}

	/**
	 * Returns metadata path for given metadata
	 * @param metadata Metadata
	 * @return File object
	 */
	public File getMetadataPath(Metadata metadata) {
		return new File(repoLocation, localRepositoryManager.getPathForLocalMetadata(metadata));
	}
	
	/**
	 * Returns metadata path for metadata derived from repository identity and type
	 * @param identity
	 * @param type
	 * @return File object
	 */
	public File getMetadataPath(Identity identity, String type) {
		Metadata metadata = new Metadata() {

			@Override
			public String getGroupId() {
				return identity.getGroupId();
			}

			@Override
			public String getArtifactId() {
				return identity.getArtifactId();
			}

			@Override
			public String getVersion() {
				return identity.getVersion();
			}

			@Override
			public String getType() {
				return type;
			}

			@Override
			public Nature getNature() {
				return Nature.RELEASE_OR_SNAPSHOT;
			}

			@Override
			public File getFile() {
				return null;
			}

			@Override
			public Metadata setFile(File file) {
				return null;
			}

			@Override
			public String getProperty(String key, String defaultValue) {
				return null;
			}

			@Override
			public Map<String, String> getProperties() {
				return null;
			}

			@Override
			public Metadata setProperties(Map<String, String> properties) {
				return null;
			}};
		return new File(repoLocation, localRepositoryManager.getPathForLocalMetadata(metadata));
	}

	public void addMetaData(Metadata metadata) {
		LocalMetadataRegistration registration = new LocalMetadataRegistration(metadata);
		add(session, registration);
	}

	/**
	 * Returns repository
	 * @return LocalRepository object
	 */
	@Override
	public LocalRepository getRepository() {
		return localRepositoryManager.getRepository();
	}

	/**
	 * Returns path for local artifact
	 * @param artifact
	 * @return path
	 */
	@Override
	public String getPathForLocalArtifact(Artifact artifact) {
		return localRepositoryManager.getPathForLocalArtifact(artifact);
	}

	/**
	 * Remote repository unsupported
	 */
	@Override
	public String getPathForRemoteArtifact(Artifact artifact, RemoteRepository repository, String context) {
		throw new UnsupportedOperationException("This repository is local only");
	}

	/**
	 * Returns path for local metadata
	 * @param metadata
	 */
	@Override
	public String getPathForLocalMetadata(Metadata metadata) {
		return localRepositoryManager.getPathForLocalMetadata(metadata);
	}

	/**
	 * Remote repository unsupported
	 */
	@Override
	public String getPathForRemoteMetadata(Metadata metadata, RemoteRepository repository, String context) {
		throw new UnsupportedOperationException("This repository is local only");
	}

	/**
	 * Find artifact
	 * @param session RepositorySystemSession object
	 * @param request LocalArtifactRequest object
	 */
	@Override
	public LocalArtifactResult find(RepositorySystemSession session, LocalArtifactRequest request) {
		return localRepositoryManager.find(session, request);
	}

	/**
	 * Apply artifact registration
	 * session RepositorySystemSession object
	 * requst LocalArtifactRegistration object
	 */
	@Override
	public void add(RepositorySystemSession session, LocalArtifactRegistration request) {
		Artifact artifact = request.getArtifact();
        File path = new File(localRepositoryManager.getPathForLocalArtifact(artifact));
    	if (!path.getParentFile().exists() && !path.getParentFile().mkdirs())
			throw new AndworxException("Directory error creating path " + path.getName().toString());
        Path destination = path.toPath();
        try {
			Files.copy(artifact.getFile().toPath(), destination);
		} catch (IOException e) {
			throw new AndworxException("Error copying artifact " + artifact.toString(), e);
		}
	}

	/**
	 * Find metatdat
	 * session RepositorySystemSession object
	 * requst LocalMetadataRequest object
	 */
	@Override
	public LocalMetadataResult find(RepositorySystemSession session, LocalMetadataRequest request) {
		return localRepositoryManager.find(session, request);
	}

	/**
	 * Apply metadata registration
	 * session RepositorySystemSession object
	 * requst LocalMetadataRegistration object
	 */
	@Override
	public void add(RepositorySystemSession session, LocalMetadataRegistration request) {
        Metadata metadata = request.getMetadata();
        Properties properties = new Properties();
        String artifactId = metadata.getArtifactId();
        if (!artifactId.isEmpty())
        	properties.setProperty(ARTFACT_KEY, artifactId);
        String groupId = metadata.getGroupId();
        if (!groupId.isEmpty())
        	properties.setProperty(GROUP_ID_KEY, groupId);
        String version = metadata.getVersion();
        if (!version.isEmpty())
        	properties.setProperty(VERSION_KEY, version);
        String type = metadata.getType();
        if (!type.isEmpty())
        	properties.setProperty(TYPE_KEY, type);
        File file = metadata .getFile();
        if ((file != null) && file.exists() && file.isFile())
        	properties.setProperty(FILE_KEY, file.getAbsolutePath());
        Nature nature = metadata.getNature();
        if (nature != null)
        	properties.setProperty(NATURE_KEY, nature.toString());
        Map<String, String> metaProps = metadata.getProperties();
        if ((metaProps != null) && !metaProps.isEmpty())
        	properties.putAll(metaProps);
        File path = new File(repoLocation, getPathForLocalMetadata(metadata));
    	if (!path.getParentFile().exists() && !path.getParentFile().mkdirs())
			throw new AndworxException("Directory error creating path " + path.getName().toString());
        try (OutputStream os = new FileOutputStream(path)) {
        	properties.storeToXML(os, new Date().toString(), StandardCharsets.UTF_8.name());
        } catch (IOException e) {
			throw new AndworxException("Error copying metadata " + metadata.toString(), e);
		}
	}
	
}
