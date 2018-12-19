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
import java.util.HashMap;
import java.util.Map;

import org.eclipse.andworx.functions.Builtin;
import org.eclipse.andworx.project.AndworxParserContext;

/**
 * AndworxParserContext implementation. Stores information on variables, functions and types.
 */
public class ModelParserContext implements AndworxParserContext {
	
	/** Variables */
	private final Map<String,Variable> variableMap;
	/** Functions */
	private final Map<Variable,Function> functionMap;
    /** Types */
	private final Map<String, Class<?>> typeMap;
	/** Root project to be set before parsing begins */
	private File rootProject;

	/**
	 * Create ModelParserContext object
	 */
	public ModelParserContext() {
		typeMap = new HashMap<>();
		variableMap = new HashMap<>();
		functionMap = new HashMap<>();
		loadGradleFunctions();
	}

	/**
	 * Set root project
	 * @param rootProject
	 */
	public void setRootProject(File rootProject) {
		this.rootProject = rootProject;
	}

	/**
	 * Reset to start new parse session
	 */
	public void reset() {
		functionMap.clear();
		variableMap.clear();
		typeMap.clear();
		loadGradleFunctions();
	}
	
	@Override
	public File getRootProject() {
		return rootProject;
	}

	@Override
	public void setType(String name, Class<?> clazz) {
		typeMap.put(name, clazz);
	}

	@Override
	public Class<?> getType(String name) {
		return typeMap.get(name);
	}

	@Override
	public Variable variableInstance(String name) {
		Variable var = new Variable();
		variableMap.put(name, var);
		var.name = name;
		return var;
	}

	@Override
	public boolean hasVariable(String name) {
		return variableMap.containsKey(name);
	}

	@Override
	public Variable getVariable(String name) {
		return variableMap.get(name);
	}

	@Override
	public Function functionInstance(Variable variable, String type, String classname, String methodname, int argCount) {
		Function function = new Function();
		function.variable = variable;
		function.type = type;
		function.classname = classname;
		function.methodname = methodname;
		function.argCount = argCount;
		functionMap.put(variable, function);
		return function;
	}

	@Override
	public Function getFunction(Variable variable) {
		return functionMap.get(variable);
	}

    /**
     * Establish builtin functions
     */
	private void loadGradleFunctions() {
		Variable rootProject = variableInstance("rootProject");
		functionInstance(rootProject, File.class.getName(), Builtin.class.getName(), "file", 1);
	}
}
