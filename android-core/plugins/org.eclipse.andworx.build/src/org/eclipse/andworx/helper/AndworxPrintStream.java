/*
 * Copyright (C) 2010 The Android Open Source Project
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

package org.eclipse.andworx.helper;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Calendar;

/**
 * Custom PrintStream has a tag containing data/project info.
 * Additionally, a prefix can be set (and removed) at runtime.
 * Only {@link #println()} is supported.
 */
public class AndworxPrintStream extends PrintStream {
    private String mProject;
    private String mPrefix;

    /**
     * Default constructor with project and output stream.
     * The project is used to get the project name for the output tag.
     *
     * @param project The Project name
     * @param prefix A prefix to be printed before the actual message. Can be null
     * @param stream The Stream
     */
    public AndworxPrintStream(String project, String prefix, OutputStream stream) {
        super(stream);
        mProject = project;
    }

    /**
     * Default constructor with project and output print stream.
     * The project is used to get the project name for the output tag.
     *
     * @param project The Project name
     * @param prefix A prefix to be printed before the actual message. Can be null
     * @param stream The Stream
     */
   public AndworxPrintStream(String project, String prefix, PrintStream stream) {
        super(stream);
        mProject = project;
	}

	/**
     * Updates the value of the prefix.
     * @param prefix
     */
    public void setPrefix(String prefix) {
        mPrefix = prefix;
    }

    /**
     * Prints a String and then terminate the line.  This method behaves as
     * though it invokes <code>{@link #print(String)}</code> and then
     * <code>{@link #println()}</code>.
     *
     * @param message  <code>String</code> to be printed.
     */
    @Override
    public void println(String message) {
        // write the date/project tag first.
        String tag = getMessageTag(mProject != null ? mProject : null);

        print(tag);
        print(" "); //$NON-NLS-1$
        if (mPrefix != null) {
            print(mPrefix);
            print(" "); //$NON-NLS-1$
        }

        // then write the regular message
        super.println(message);
    }

    /**
     * Creates a string containing the current date/time, and the tag.
     * The tag does not end with a whitespace.
     * @param tag The tag associated to the message. Can be null
     * @return The dateTag
     */
    public static String getMessageTag(String tag) {
        Calendar c = Calendar.getInstance();

        if (tag == null) {
            return String.format("[%1$tF %1$tT]", c);
        }
        return String.format("[%1$tF %1$tT - %2$s]", c, tag);
    }

}
