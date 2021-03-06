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

/**
 * Recieves item extracted from a configuration file by an AST parser
 */
public interface SyntaxItemReceiver {
	/**
	 * Handle value item 
	 * @param context Parser context
	 * @param path Abstract Syntax Tree path
	 * @param value Value at indicated path
	 */
	void receiveItem(AndworxParserContext context, String path, String value);
	/**
	 * Handle property item 
	 * @param context Parser context
	 * @param path Abstract Syntax Tree path
	 * @param key Property key
	 * @param value Property value
	 */
	void receiveItem(AndworxParserContext context, String path, String key, String value);
	/**
	 * Handle binary item 
	 * @param context Parser context
	 * @param path Abstract Syntax Tree path
	 * @param lhs Left hand side expression
	 * @param op Binary operator
	 * @param rhs Right hand side expression
	 */
	void receiveItem(AndworxParserContext context, String path, String lhs, String op, String rhs);

}
