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
package org.eclipse.andworx.control;

import java.util.ArrayList;
import java.util.List;

/**
 * FileFilter
 * Used with standard file selection dialog to control what files are shown
 */
public class FileFilter
{
    /** 
     * The file extensions the dialog will use to filter the files it shows.
     * <p>
     * The strings are platform specific. For example, on
     * some platforms, an extension filter string is typically
     * of the form "*.extension", where "*.*" matches all files.
     * Semicolon is used as a separator, e.g. "*.jpg;*.png".
     * </p>
     * <p>
     * Note: On Mac, setting the file extension filter affects how
     * app bundles are treated by the dialog. When a filter extension 
     * having the app extension (.app) is selected, bundles are treated 
     * as files. For all other extension filters, bundles are treated 
     * as directories. When no filter extension is set, bundles are 
     * treated as files.
     * </p>
     */
    List<String> extensions;
    /**
     * The names that describe the filter extensions
     * which the dialog will use to filter the files it shows.
     * <p>
     * Each name is a user-friendly short description shown for
     * its corresponding filter. The <code>names</code> array must
     * be the same length as the <code>extensions</code> array.
     * </p>
     */
    List<String> names;

    /**
     * Construct FileFilter object
     */
    public FileFilter()
    {
        extensions = new ArrayList<String>();
        names = new ArrayList<String>();        
    }

    /**
     * Add a named file type eg. <code>addName("PKCS12", "pfx", "p12")</code>
     * @param name File type
     * @param extensionArray Extensions associated with file type
     */
    public void addName(String name, String... extensionArray)
    {
        StringBuilder nameBuilder = new StringBuilder(name);
        StringBuilder extBuilder = new StringBuilder();
        nameBuilder.append('(');
        boolean firstTime = true;
        for (String ext: extensionArray)
        {
            if (firstTime)
                firstTime = false;
            else
            {
                extBuilder.append(';');
                nameBuilder.append(',');
            }
            extBuilder.append("*.").append(ext);
            nameBuilder.append("*.").append(ext);
        }
        nameBuilder.append(')');
        extensions.add(extBuilder.toString());
        names.add(nameBuilder.toString());
    }

    /**
     * Returns extension array for use as <code>FileDialog.setFilterExtensions()</code> argument
     * @return array of extensions
     */
    public String[] getExtensions()
    {
        return extensions.toArray(new String[extensions.size()]);
    }

    /**
      * Returns name array for use as <code>FileDialog.setFilterNames()</code> argument
     * @return array of names
     */
    public String[] getNames()
    {
        return names.toArray(new String[names.size()]);
    }
}
