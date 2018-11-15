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
package org.eclipse.andworx.project;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.andworx.build.AndworxFactory;
import org.eclipse.andworx.entity.ProjectBean;
import org.eclipse.andworx.entity.ProjectProfileBean;
import org.eclipse.andworx.maven.Dependency;

import com.android.sdklib.IAndroidTarget;
import com.google.common.collect.ImmutableSet;

import au.com.cybersearch2.classyjpa.EntityManagerLite;

/**
 * Essential project characteristics such as identity, dependencies and Android target
 */
public class ProjectProfile {
	public static final String PACKAGING = "packaging";

	/** Profile JPA entity object containing persisted configuration */
	protected final ProjectProfileBean profileBean;
	/** Project dependencies */
	protected Set<Dependency> dependencies;
	/** Target platform */
	protected IAndroidTarget target;
	
	/**
	 * Construct ProjectProfile object
	 * @param identity Coordinates used to distingush between artifacts
	 */
	public ProjectProfile(Identity identity) {
		profileBean = new ProjectProfileBean(identity.getGroupId(), identity.getArtifactId(), identity.getVersion());
		dependencies = new HashSet<>();
		profileBean.setDependencies(dependencies);
	}

	/**
	 * Construct ProjectProfile object from JPA bean
	 * @param profileBean Profile JPA entity object containing persisted configuration
	 */
	public ProjectProfile(ProjectProfileBean profileBean) {
		this.profileBean = profileBean;
		dependencies = new HashSet<>();
		profileBean.setDependencies(dependencies);
	}

	/**
	 * Returns unique ID of project in the Andworx database
	 * @return int
	 */
	public int getProjectId() {
		return profileBean.getProjectId();
	}
	
	/**
	 * Return repository coordinates of the project = group id, artifact id and version
	 * @return Identity object
	 */
	public Identity getIdentity() {
		return new Identity(profileBean.getGroupId(), profileBean.getArtifactId(), profileBean.getVersion());
	}

	/**
	 * Returns flag set true if this project is a library
	 * @return boolean
	 */
	public boolean isLibrary() {
		return profileBean.isLibrary();
	}

	/**
	 * Set flag indicating if this project is a library
	 * @param isLibrary boolean value
	 */
	public void setLibrary(boolean isLibrary) {
		profileBean.setLibrary(isLibrary);;
	}

	/**
	 * Returns set of project dependencies
	 * @return set of Dependency objects
	 */
	public Set<Dependency> getDependencies() {
		return ImmutableSet.copyOf(dependencies);
	}

	/**
	 * Add project dependency
	 * @param dependency Dependency object
	 */
	public void addDependency(Dependency dependency) {
		synchronized(dependencies) {
			dependencies.add(dependency);
		}
	}
	
	public void removeDependency(Dependency dependency) {
		synchronized(dependencies) {
			dependencies.remove(dependency);
		}
	}

	/**
	 * Returns target platform assigned to the project
	 * @return IAndroidTarget object
	 */
	public IAndroidTarget getTarget() {
		if (target == null) {
        	AndworxFactory objectFactory = AndworxFactory.instance();
			target = objectFactory.getAndroidEnvironment().getAvailableTarget(profileBean.getTargetHash());
		}
		return target;
	}

	/**
	 * Returns target platform hash eg. "android-27"
	 * @return hash
	 */
	public String getTargetHash() {
		return profileBean.getTargetHash();
	}

	/**
	 * Set target hash
	 * @param targetHash Target platform hash eg. "android-27"
	 */
	public void setTargetHash(String targetHash) {
		profileBean.setTargetHash(targetHash);
	}

	/**
	 * Set target platform
	 * @param target IAndroidTarget object 
	 */
	public void setTarget(IAndroidTarget target) {
		profileBean.setTargetHash(target.hashString());
	}

	/**
	 * Returns build tools version
	 * @return version in text format
	 */
	public String getBuildToolsVersion() {
		return profileBean.getBuildToolsVersion();
	}

	/**
	 * Set build tools version
	 * @param buildToolsVersion Version in text format
	 */
	public void setBuildToolsVersion(String buildToolsVersion) {
		profileBean.setBuildToolsVersion(buildToolsVersion);
	}	
	
	/**
	 * Returns deep copy but with given identity
	 * @param identity
	 * @return
	 */
	public ProjectProfile copy(Identity identity) {
		ProjectProfile profileCopy = new ProjectProfile(identity);
		profileCopy.setBuildToolsVersion(getBuildToolsVersion());
		Set<Dependency> dependenciesCopy = new HashSet<>();
		dependenciesCopy.addAll(dependencies);
		profileCopy.dependencies = dependenciesCopy;
		profileCopy.setLibrary(isLibrary());
		profileCopy.setTargetHash(profileBean.getTargetHash());
		return profileCopy;
	}

	/**
	 * Set project bean with given value and persist it. It is valid to do this only in a JPA transaction context.
	 * @param entityManager
	 * @param projectBean
	 */
	public void persist(EntityManagerLite entityManager, ProjectBean projectBean) {
		profileBean.setProjectBean(projectBean);
		entityManager.persist(profileBean);
	}



}
