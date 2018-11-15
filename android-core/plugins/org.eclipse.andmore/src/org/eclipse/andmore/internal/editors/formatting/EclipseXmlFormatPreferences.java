/*
 * Copyright (C) 2011 The Android Open Source Project
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
package org.eclipse.andmore.internal.editors.formatting;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.ide.common.xml.XmlFormatPreferences;
import com.android.ide.common.xml.XmlAttributeSortOrder;

import org.eclipse.andmore.AndmoreAndroidPlugin;
import org.eclipse.andmore.internal.preferences.AdtPrefs;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.ui.internal.editors.text.EditorsPlugin;
import org.eclipse.ui.texteditor.AbstractDecoratedTextEditorPreferenceConstants;
import org.eclipse.wst.sse.core.internal.provisional.IndexedRegion;
import org.eclipse.wst.xml.core.internal.preferences.XMLCorePreferenceNames;
import org.w3c.dom.Attr;

import java.util.Comparator;

/**
 * Formatting preferences used by the Android XML formatter.
 */
public class EclipseXmlFormatPreferences extends XmlFormatPreferences {
    @VisibleForTesting
    protected EclipseXmlFormatPreferences() {
    }

    /**
     * Creates a new {@link EclipseXmlFormatPreferences} based on the current settings
     * in {@link AdtPrefs}
     *
     * @return an {@link EclipseXmlFormatPreferences} object
     */
    @NonNull
    public static EclipseXmlFormatPreferences create() {
        EclipseXmlFormatPreferences p = new EclipseXmlFormatPreferences();
        AdtPrefs adtPrefs = AndmoreAndroidPlugin.getDefault().getAdtPrefs();

        p.useEclipseIndent = adtPrefs.isUseEclipseIndent();
        p.removeEmptyLines = adtPrefs.isRemoveEmptyLines();
        p.oneAttributeOnFirstLine = adtPrefs.isOneAttributeOnFirstLine();
        p.sortAttributes = adtPrefs.getAttributeSort();
        p.spaceBeforeClose = adtPrefs.isSpaceBeforeClose();

        return p;
    }

    @Override
    @Nullable
    public Comparator<Attr> getAttributeComparator() {
        // Can't just skip sorting; the attribute table moves attributes out of order
        // due to hashing, so sort by node positions
        if (sortAttributes == XmlAttributeSortOrder.NO_SORTING) {
            return EXISTING_ORDER_COMPARATOR;
        }
        return sortAttributes.getAttributeComparator();
    }

    private static final Comparator<Attr> EXISTING_ORDER_COMPARATOR = new Comparator<Attr>() {
        @Override
        public int compare(Attr attr1, Attr attr2) {
            IndexedRegion region1 = (IndexedRegion) attr1;
            IndexedRegion region2 = (IndexedRegion) attr2;

            return region1.getStartOffset() - region2.getStartOffset();
        }
    };

    // The XML module settings do not have a public API. We should replace this with JDT
    // settings anyway since that's more likely what users have configured and want applied
    // to their XML files

    /**
     * Returns the string to use to indent one indentation level
     *
     * @return the string used to indent one indentation level
     */
    @Override
    @SuppressWarnings({
            "restriction"
    })
    public String getOneIndentUnit() {
        if (useEclipseIndent) {
            // Look up Eclipse indent preferences
            // TODO: Use the JDT preferences instead, which make more sense
        	
        	IEclipsePreferences preferences = InstanceScope.INSTANCE.getNode("org.eclipse.wst.xml.core.internal.XMLCorePlugin");;
            int indentationWidth = preferences.getInt(XMLCorePreferenceNames.INDENTATION_SIZE, 2);
            String indentCharPref = preferences.get(XMLCorePreferenceNames.INDENTATION_CHAR, " ");
            boolean useSpaces = XMLCorePreferenceNames.SPACE.equals(indentCharPref);

            StringBuilder indentString = new StringBuilder();
            for (int j = 0; j < indentationWidth; j++) {
                if (useSpaces) {
                    indentString.append(' ');
                } else {
                    indentString.append('\t');
                }
            }
            mOneIndentUnit = indentString.toString();
        }

        return mOneIndentUnit;
    }

    /**
     * Returns the number of spaces used to display a single tab character
     *
     * @return the number of spaces used to display a single tab character
     */
    @Override
    @SuppressWarnings("restriction") // Editor settings
    public int getTabWidth() {
        if (mTabWidth == -1) {
            String key = AbstractDecoratedTextEditorPreferenceConstants.EDITOR_TAB_WIDTH;
            try {
                IPreferenceStore prefs = EditorsPlugin.getDefault().getPreferenceStore();
                mTabWidth = prefs.getInt(key);
            } catch (Throwable t) {
                // Pass: We'll pick a suitable default instead below
            }
            if (mTabWidth <= 0) {
                mTabWidth = 4;
            }
        }

        return mTabWidth;
    }
}
