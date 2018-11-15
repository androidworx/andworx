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
package org.eclipse.andworx.polyglot;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Assembles declarations embedded in the build file
 */
public class AndworxBuildAssembler {
	/** Special method name for a class constructor */
	public static final String CTOR = "<init>";
	
	/** Variable attributes */
	private static class Variable {
		public String name;
		public Class<?> type;
		public Object value;
	}

	/** Function attributes */
	private static class Function {
		public Variable variable;
		public String classname;
		public String methodname;
		public int argCount;
		public List<String> arguments;
	}
	
	private final File projectLocation;
	private final Map<String,Variable> variableMap;
	private final Map<Variable,Function> functionMap;

	/** 
	 * Construct AndworxBuildAssembler object
	 * @param projectLocation
	 */
	public AndworxBuildAssembler(File projectLocation) {
		this.projectLocation = projectLocation;
		variableMap = new HashMap<>();
		functionMap = new HashMap<>();
	}
	
	boolean receiveItem(String path, String value) {
		String[] split = path.split("/");
		if (split.length == 3) {
			String variableName = split[0];
			if (variableMap.containsKey(variableName)) {
				Variable variable = variableMap.get(variableName);
				if (variable != null) {
					Function function = functionMap.get(variable);
					if (function != null) {
						if (function.arguments == null)
							function.arguments = new ArrayList<>();
						function.arguments.add(value);
						if (function.argCount == function.arguments.size())
							invoke(function);
					}
				}
				//System.out.println(path + " : " + value);
				return true;
			}
		}
		if (variableMap.containsKey(path)) {
			//System.out.println(path + " : " + value);
			return true;
		}
		return false;
	}
	
	boolean receiveItem(String path, String key, String value) {
		if (variableMap.containsKey(path)) {
			//System.out.println(path + " : " + key + " [" + value + "]");
			return true;
		}
		return false;
	}

	public void receiveDeclaration(String name, String type) {
		Variable var = new Variable();
		variableMap.put(name, var);
		var.name = name;
		if (!type.isEmpty())
			try {
				var.type = Class.forName(type);
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			}
	}

	public void receiveMethod(String path, String type, int argCount) {
		String[] split = path.split("/");
		String variableName = split[0];
		if (variableMap.containsKey(variableName)) {
			Variable variable = variableMap.get(variableName);
			// Check for case this call is a call argument
			Function function = functionMap.get(variable);
			if (function != null) { // No support, so skip
				return;
			}
			String className = split[1];
			String methodName = split[2];
			function = new Function();
			function.variable = variable;
			function.classname = className;
			function.methodname = methodName;
			function.argCount = argCount;
			functionMap.put(variable, function);
			if (argCount == 0)
				invoke(function);
			printFunction(type, function);
		}
	}
	
	public String receiveMap(String path, String name, String key) {
		for (Variable variable: variableMap.values())
			if (variable.name.equals(name)) {
				Properties props = (Properties)variable.value;
				String value = props.getProperty(key);
				//System.out.println(path + "." + name + " = " + value);
				return value;
			}
		//System.out.println(path + "." + name + "[" + key + "]");
		return key;
	}
	
	private void invoke(Function function) {
		if (function.classname.equals("rootProject")) {
			if (function.methodname.equals("file")) {
				Variable variable = function.variable;
				variable.type = Properties.class;
				File propsFile = new File(projectLocation, function.arguments.get(0));
				Properties properties = new Properties();
				try (FileInputStream inStream = new FileInputStream(propsFile)) {
					properties.load(inStream);
					variable.value = properties;
					//properties.list(System.out);
				} catch (IOException e) {
					// TODO - Log error
					e.printStackTrace();
				}
			}
		}
		
	}

	private void printFunction(String type, Function function) {
		/*
		StringBuilder builder = new StringBuilder();
		if (!type.isEmpty())
			builder.append(type).append(' ');
		builder.append(function.variable.name);
		if (!"*".equals(function.classname))
			builder.append(" = ").append(function.classname).append('.').append(function.methodname).append('(').append(function.argCount).append(')');
		else
			builder.append('.').append(function.methodname).append('(').append(function.argCount).append(')');
		System.out.println(builder.toString());
		*/
	}

}
