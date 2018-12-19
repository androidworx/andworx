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

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Properties;

import org.eclipse.andworx.exception.AndworxException;
import org.eclipse.andworx.functions.Builtin;
import org.eclipse.andworx.project.AndworxParserContext;
import org.eclipse.andworx.project.AndworxParserContext.Function;
import org.eclipse.andworx.project.AndworxParserContext.Variable;

/**
 * Assembles parser syntax item into parser context
 */
public class AndworxBuildAssembler {
	/** Special method name for a class constructor */
	public static final String CTOR = "<init>";
	
	/** 
	 * Construct AndworxBuildAssembler object
	 */
	public AndworxBuildAssembler() {
	}

	/**
	 * Invoke a method call and return result
	 * @param context Parser context
	 * @param type Retuurn type
	 * @param methodClass Class to invoke or empty string for constructor call 
	 * @param name Method to invoke
	 * @param args Call parameters
	 * @return Object
	 */
	public Object invokeMethod(
			AndworxParserContext context, 
			String type, 
			String methodClass, 
			String name, 
			Object[] args) {
		if (methodClass.isEmpty()) { // Constructor call
			Class<?> clazz = context.getType(type);
			if (clazz == null)
				throw new AndworxException("Build file parser can not resolve type " + type);
			try {
				if (args.length == 0)
					return clazz.newInstance();
				else {
					Class<?>[] paramClasses = new Class[args.length];
					for (int index = 0; index < args.length; ++index)
						paramClasses[index] = args[index].getClass();
					Constructor<?> constructor = clazz.getConstructor(paramClasses);
					return constructor.newInstance(args);
				}
			} catch (InstantiationException | IllegalAccessException | NoSuchMethodException | SecurityException | IllegalArgumentException | InvocationTargetException e) {
				throw new AndworxException("Build file parser can not instantiate type " + type, e);
			}
		}
		if (context.hasVariable(methodClass)) { // Variable or function invocation
			Variable variable = context.getVariable(methodClass);
			Function function = context.getFunction(variable);
			if (function == null) {
				// TODO - Method call by reflection
				if ((variable.value instanceof Properties) && name.equals("load")) {
					// Special case for variable containing a properties object
					Properties props = (Properties) variable.value;
					try {
						props.load((InputStream)args[0]);
					} catch (IOException e) {
						throw new AndworxException("Error loading properties file", e);
					}
				}
				return null;
			} else {
				function.arguments = args;
				return invoke(context, function);
			}
		}
		return null;
	}

	/**
	 * Returns value from map contained in a variable
	 * @param context Parser context
	 * @param name Variable name
	 * @param key Key associated with required value
	 * @return
	 */
    public String receiveMap(AndworxParserContext context, String name, String key) {
		Variable variable = context.getVariable(name);
		if (variable != null) {
			Properties props = (Properties)variable.value;
			String value = props.getProperty(key);
			//System.out.println(path + "." + name + " = " + value);
			return value;
		}
		//System.out.println(path + "." + name + "[" + key + "]");
		return key;
	}

    private Object invoke(AndworxParserContext context, Function function) {
    	if (function.classname.equals(Builtin.class.getName())) {
    		Builtin builtin = new Builtin(context);
    		Object result = builtin.invoke(function);
    		function.variable.value = result;
    		return result;
    	}
    	return null;
	}

}
