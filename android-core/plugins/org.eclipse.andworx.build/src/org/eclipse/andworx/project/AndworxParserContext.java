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

import java.io.File;

/**
 * Parser artifact container
 */
public interface AndworxParserContext {

	/** Variable attributes */
	public static class Variable {
		public String name;
		public Class<?> type;
		public Object value;
	}

	/** Function attributes */
	public static class Function {
		public Variable variable;
		public String type;
		public String classname;
		public String methodname;
		public int argCount;
		public Object[] arguments;
	}

	/**
	 * Returns location of root project
	 * @return File object
	 */
	File getRootProject();
	
	/**
	 * Save type name and class
	 * @param name Full name of class
	 * @param clazz Java Class object
	 */
	void setType(String name, Class<?> clazz);
	
	/**
	 * Returns class identified by name
	 * @param name Full name of class
	 * @return Class object or null if class not found
	 */
	Class<?> getType(String name);
	
	/**
	 * Returns newly created and stored variable 
	 * @param name Variable name
	 * @return Variable object
	 */
	Variable variableInstance(String name);
	
	/** 
	 * Returns flag set true if variable exists with given name 
	 * @param name Variable name
	 * @return boolean
	 */
	boolean hasVariable(String name);
	
	/**
	 * Returns variable identified by name
	 * @param name  Variable name
	 * @return Variable object or null if variable not found
	 */
	Variable getVariable(String name);
	
	/**
	 * Returns newly created and stored function
	 * @param variable Variable to which function value is to be assigned
	 * @param type Return type. Void if void function.
	 * @param classname Class to invoke
	 * @param methodname Method to invoke
	 * @param argCount Number of arguments
	 * @return Function object
	 */
	Function functionInstance(
			 Variable variable,
			 String type,
			 String classname,
			 String methodname,
			 int argCount);
	
	/**
	 * Returns function identified by variable
	 * @param variable Variable to receive value returned by function
	 * @return Function object
	 */
	Function getFunction(Variable variable);
}
