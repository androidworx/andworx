/*
 * Copyright (C) 2012 The Android Open Source Project
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

package org.eclipse.andmore.internal.editors.layout.configuration;

import org.eclipse.swt.graphics.Image;

import com.android.annotations.NonNull;
import com.android.ide.common.resources.configuration.LocaleQualifier;

/** A language,region pair */
public class Locale {
    /** A special marker region qualifier representing any region */
    public static final LocaleQualifier ANY_LOCALE = new LocaleQualifier(LocaleQualifier.FAKE_VALUE);

    /** A locale which matches any language and region */
    public static final Locale ANY = new Locale(ANY_LOCALE);

    @NonNull
    public final LocaleQualifier locale;

    /**
     * Constructs a new {@linkplain Locale} matching a given language in a given locale.
     *
     * @param language the language
     * @param region the region
     */
    private Locale(@NonNull LocaleQualifier locale) {
        this.locale = locale;
    }

    /**
     * Constructs a new {@linkplain Locale} matching a given language in a given specific locale.
     *
     * @param language the language
     * @param region the region
     * @return a locale with the given language and region
     */
    @NonNull
    public static Locale create(
            @NonNull LocaleQualifier locale) {
        return new Locale(locale);
    }

    /**
     * Returns a flag image to use for this locale
     *
     * @return a flag image, or a default globe icon
     */
    @NonNull
    public Image getFlagImage() {
        Image image = null;
        if (locale.hasFakeValue()) {
        	return FlagManager.getGlobeIcon();
        }
        
        FlagManager icons = FlagManager.get();
        image = icons.getFlag(locale.getLanguage(), locale.getRegion());
        if (image == null) {
            image = FlagManager.getEmptyIcon();
        }
        return image;
    }

    /**
     * Returns true if this locale specifies a specific language. This is true
     * for all locales except {@link #ANY}.
     *
     * @return true if this locale specifies a specific language
     */
    public boolean hasLanguage() {
    	return locale.hasLanguage();
    }

    /**
     * Returns true if this locale specifies a specific region
     *
     * @return true if this locale specifies a region
     */
    public boolean hasRegion() {
    	return locale.hasRegion();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((locale == null) ? 0 : locale.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Locale other = (Locale) obj;
        if (this.locale == null) {
        	return other.locale == null;
        }
        return this.locale.equals(other.locale);
    }

    @Override
    public String toString() {
    	return locale.getValue();
    }

    /**
     * Returns the locale formatted as language-region. If region is not set,
     * language is returned. If language is not set, empty string is returned.
     */
    public String toLocaleId() {
        // Return lang-reg only if both lang and reg are present. Else return
        // lang.
    	return locale.getValue();
    }
}
