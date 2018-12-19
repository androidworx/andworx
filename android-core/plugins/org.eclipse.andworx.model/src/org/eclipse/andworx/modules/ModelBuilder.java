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
package org.eclipse.andworx.modules;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.andworx.log.SdkLogger;
import org.eclipse.andworx.project.AndworxParserContext;
import org.eclipse.andworx.project.SyntaxItemReceiver;
import org.eclipse.andworx.record.ModelType;
import org.eclipse.andworx.topology.entity.RepositoryBean;
import org.eclipse.andworx.topology.entity.ModelNode.TypedElement;

/**
 * Builds module configuration by receiving syntax items from an AST parser
 */
public class ModelBuilder implements SyntaxItemReceiver {

	/** The buildscript block is directed to Gradle itself to support applying plugins */ 
	private static final String BUILD_SCRIPT = "buildscript";
	/** The allprojects block is for global scope project configuration */
	private static final String ALL_PROJECTS = "allprojects";
	private static final String REPOSITORIES = "repositories";
	public static final String MAVEN_CENTRAL_URL = "https://repo.maven.apache.org/maven2/";
	public static final String GOOGLE_URL = "https://dl.google.com/dl/android/maven2/";
	public static final String BINTRAY_JCENTER_URL = "https://jcenter.bintray.com/";
	
	private static SdkLogger logger = SdkLogger.getLogger(ModelBuilder.class.getName());

	private final Map<String, RepositoryBean> repositoryBeanMap;
	
	/**
	 * Construct ModelBuilder object
	 */
	public ModelBuilder() {
		repositoryBeanMap = new HashMap<>();
	}

	public void reset() {
		repositoryBeanMap.clear();
	}
	
	public Collection<? extends ModelType> getModelTypes() {
		if (repositoryBeanMap.size() > 0)
			return Collections.singletonList(ModelType.allProjects);
		return Collections.emptyList();
	}

	public List<TypedElement> getElements() {
		List<TypedElement> elementList = new ArrayList<>();
		for (RepositoryBean bean: repositoryBeanMap.values()) {
			TypedElement typedElement = new TypedElement();
			typedElement.modelType = ModelType.allProjects;
			typedElement.nodeElement = bean;
			elementList.add(typedElement);
		}
		return elementList;
	}
	
	/**
	 * Handle value item 
	 * @param context Parser context
	 * @param path Abstract Syntax Tree path
	 * @param value Value at indicated path
	 */
	@Override
	public void receiveItem(AndworxParserContext context, String path, String value) {
		if (path.startsWith(BUILD_SCRIPT)) {
			path = path.substring(BUILD_SCRIPT.length() + 1);
			configureGlobalScope(path, value, true);
		} else if (path.startsWith(ALL_PROJECTS)) {
			path = path.substring(ALL_PROJECTS.length() + 1);
			configureGlobalScope(path, value, false);
		} 
	}

	@Override
	public void receiveItem(AndworxParserContext context, String path, String key, String value) {
	}

	@Override
	public void receiveItem(AndworxParserContext context, String path, String lhs, String op, String rhs) {
	}

	private void configureGlobalScope(String path, String value, boolean isBuildScript) {
		//System.out.println("Global scope. Is build script = " + isBuildScript);
		if (isBuildScript) // Ignore buildscript block until need found 
			return;
		int pos = path.indexOf('/');
		if (pos != -1) {
			String element = path.substring(0, pos);
			if (element.equals(REPOSITORIES)) {
				path = path.substring(REPOSITORIES.length() + 1);
				pos = path.indexOf('/');
				if (pos != -1) {
				    String name = path.substring(0, pos);
				    String key = path.substring(pos + 1);
			    	// TODO - flatDir
			    	if (key.equals("url"))
			    		createRepoBean(name, value);
			    	else
			    		logger.error(null, "Unsupported repository option ", key);
				} else {
			    	switch (path) {
			    	case "jcenter": createRepoBean(path, BINTRAY_JCENTER_URL); break;
			    	case "google": createRepoBean(path, GOOGLE_URL); break;
			    	case "mavenCentral": createRepoBean(path, MAVEN_CENTRAL_URL); break;
			    	// Maven local is not configurable. Ignore quietly.
			    	case "mavenLocal": break;
			    	default:
			    		logger.error(null, "Unknown default repository %", path);
			    	}
				}
			} else
				logger.warning("Ignoring %s", path);
		}
		System.out.println(path + ": " + value);
	}

	private void createRepoBean(String name, String url) {
		try {
			RepositoryBean bean = new RepositoryBean(name, new URL(url));
			repositoryBeanMap.put(name, bean);
		} catch (MalformedURLException e) {
			logger.error(e, "Invalid repository URL %s", url);
		}
		
	}



}
