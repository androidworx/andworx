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

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.andworx.AndworxConstants;
import org.eclipse.andworx.record.ModelType;
import org.eclipse.andworx.topology.entity.ModelNode;
import org.eclipse.andworx.topology.entity.ModelNode.TypedElement;
import org.eclipse.andworx.topology.entity.ModuleBean;

/**
 * Maintains project associations in multi-module projects
 */
public class WorkspaceModeller {

	public static String[] BUILD_FILES = {
		AndworxConstants.FN_BUILD_ANDWORX,
		AndworxConstants.FN_BUILD_GRADLE
	};

	static public class BuildFilenameFilter implements FilenameFilter {
		@Override
		public boolean accept(File dir, String name) {
			for (String buildFile: BUILD_FILES)
				// Use File matching to cater for file system case sensitivity
				if (buildFile.equals(name))
					return true;
			return false;
		}
	}
	
	private final WorkspaceConfiguration workspaceConfiguration;
	private final ConcurrentHashMap<String,ModuleBean> moduleMap;
	
	public WorkspaceModeller(WorkspaceConfiguration workspaceConfiguration) {
		this.workspaceConfiguration = workspaceConfiguration;
		moduleMap = new ConcurrentHashMap<>();
	}
	
	/**
	 * Returns ModuleBean object for specified project location
	 * @param projectLocation 
	 * @return ModuleBean object
	 */
	public ModuleBean getModule(File projectLocation) throws ModelException {
		if (projectLocation == null)
			throw new IllegalArgumentException("Project location is null");
		StringBuffer messageBuffer = new StringBuffer();
		if (!isProjectLocationValid(projectLocation, messageBuffer)) {
			messageBuffer.insert(0, ' ');
			messageBuffer.insert(0, projectLocation.getAbsolutePath());
			messageBuffer.insert(0, "Project location ");
			throw new ModelException(messageBuffer.toString());
		}
		ModuleBean moduleBean;
		try {
			String key = workspaceConfiguration.getModuleName(projectLocation);
			moduleBean = moduleMap.get(key);
			if (moduleBean == null) {
				moduleBean = workspaceConfiguration.findModule(projectLocation);
				moduleMap.put(key, moduleBean);
				List<ModelNode> nodeChildren = moduleBean.getModelNode().getChildren();
				for (ModelNode modelNode: nodeChildren) {
					ModuleBean childBean = workspaceConfiguration.getModuleBeanByNode(modelNode);
					moduleMap.put(childBean.getName(), childBean); 
				}
				
			}
		} catch (IOException | InterruptedException e) {
			throw new ModelException("Error establishing module for project location " + projectLocation.getAbsolutePath(), e);
		}
		return moduleBean;
	}

	public boolean isProjectLocationValid(File projectLocation, StringBuffer messageBuffer) {
		if (!projectLocation.exists())
			messageBuffer.append("does not exist");
		else if (!projectLocation.isDirectory())
			messageBuffer.append("is not a directory");
		else {
			FilenameFilter filter = new BuildFilenameFilter();
			File[] matches = projectLocation.listFiles(filter);
			if (matches.length == 0)
				messageBuffer.append("requires build file");
		}
		return messageBuffer.length() == 0;
	}
	
	public boolean isProjectLocationValid(File projectLocation) {
		if (!projectLocation.exists())
			return false;
		else if (!projectLocation.isDirectory())
			return false;
		else {
			FilenameFilter filter = new BuildFilenameFilter();
			File[] matches = projectLocation.listFiles(filter);
			if (matches.length == 0)
				return false;
		}
		return true;
	}
	
	/**
	 * Creates module file containing module Id
	 * @param moduleId Module ID
	 * @param moduleLocation Module location on file system
	 * @throws IOException
	 */
	public void createModuleIdFile(int moduleId, File moduleLocation) throws IOException {
		NumberFormat numberFormat = workspaceConfiguration.getModuleIdFormat();
 		File moduleIdFile = new File(moduleLocation, WorkspaceConfiguration.MODULE_ID_FILE);
		if (moduleIdFile.exists())
			moduleIdFile.delete();
		else if (!moduleIdFile.getParentFile().exists() && !moduleIdFile.getParentFile().mkdirs())
			throw new ModelException("Error creating module directory " + moduleIdFile.getParentFile().toString());
        String formatedId = numberFormat.format(moduleId);
        moduleIdFile.setReadOnly();
		Files.write(moduleIdFile.toPath(), formatedId.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW);
	}

	public boolean hasModuleForLocation(File projectLocation) throws InterruptedException {
		return workspaceConfiguration.getLocationCount(projectLocation) > 0L;
	}

	public ModelNode createModule(File projectLocation, int level, ModelBuilder modelBuilder) {
		ModelNode modelNode;
		try {
			String moduleName = workspaceConfiguration.getModuleName(projectLocation);
			List<ModelType>  modelTypes = new ArrayList<>();
			modelTypes.add(ModelType.module);
			modelTypes.addAll(modelBuilder.getModelTypes());
			if (level == 0) {
				modelNode = new ModelNode(moduleName, projectLocation.getName());
			} else {
				ModelNode parentNode = workspaceConfiguration.getModelNodeByLocation(projectLocation.getParentFile());
				modelNode = new ModelNode(moduleName, projectLocation.getName(), parentNode);
			}
			modelNode.attach(modelTypes);
			ModuleBean moduleBean = new ModuleBean(moduleName, projectLocation);
			modelNode.attach(ModelType.module, moduleBean);
			createModuleIdFile(moduleBean.getId(), projectLocation);
		    for (TypedElement typedElement: modelBuilder.getElements())
			    modelNode.attach(typedElement.modelType, typedElement.nodeElement);
		    moduleMap.put(moduleName, moduleBean);
		} catch (IOException | InterruptedException e) {
			throw new ModelException("Error establishing module for project location " + projectLocation.getAbsolutePath(), e);
		}
		return modelNode;
		
	}

	public Collection<TypedElement> getElementsByLocation(File projectLocation) {
	    ModuleBean moduleBean = null;
	    List<TypedElement> elementList = new ArrayList<>();
		try {
			String moduleName = workspaceConfiguration.getModuleName(projectLocation);
			moduleBean = moduleMap.get(moduleName);
			if (moduleBean == null) {
				moduleBean = workspaceConfiguration.findModule(projectLocation);
			}
		} catch (IOException | InterruptedException e) {
			throw new ModelException("Error retieving elements for project location " + projectLocation.getAbsolutePath(), e);
		}
		if (moduleBean != null) {
			try {
				workspaceConfiguration.getElementsByModule(moduleBean, elementList);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				throw new ModelException("Error retieving elements for project location " + projectLocation.getAbsolutePath(), e);
			}
		}
		return elementList;
	}
}
