/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.eclipse.andworx.helper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StreamTokenizer;

/**
 * Reads file into String stripping out Java comments, indentation and blank lines
 */
public class CommentStrip {

	/**
	 * Strip comments etc from file content
	 * @param textFile Input file
	 * @return processed content
	 * @throws IOException
	 */
	public String contentCommentStrip(File textFile) throws IOException {
        FileReader reader = new FileReader(textFile);
        BufferedReader bufferread = new BufferedReader(reader);
        StreamTokenizer tokenizer = new StreamTokenizer(bufferread);
        tokenizer.resetSyntax();
        StringBuilder builder = new StringBuilder();
        tokenizer.slashSlashComments(true);
        tokenizer.slashStarComments(true);
        tokenizer.quoteChar('\'');
        tokenizer.quoteChar('"');
        boolean isNewline = false;
        while (true) {
        	int tokenType = tokenizer.nextToken();
        	if (tokenType == StreamTokenizer.TT_EOF)
        		break;
        	if (tokenType == StreamTokenizer.TT_EOL) {
        		if (!isNewline) {
        			builder.append('\n');
        			isNewline = true;
        		}
        	} else if (Character.isWhitespace((char)tokenType)) {
        		if (!isNewline)
        			builder.append((char)tokenType);
        	}
        	else if (tokenType == '\'' || tokenType == '"') {
        		isNewline = false;
               	builder.append((char)tokenType).append(tokenizer.sval).append((char)tokenType);
        	}
        	else {
        		isNewline = false;
               	builder.append((char)tokenType);
        	}
        }
        return builder.toString();
	}
}
