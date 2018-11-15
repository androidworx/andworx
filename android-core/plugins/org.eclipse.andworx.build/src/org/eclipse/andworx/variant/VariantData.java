/*
 * Copyright (C) 2013 The Android Open Source Project
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
package org.eclipse.andworx.variant;

import java.util.Set;

import org.eclipse.andworx.context.MultiOutputPolicy;
import org.eclipse.andworx.context.OutputScope;
import org.eclipse.andworx.log.SdkLogger;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.dsl.options.Splits;

import android.databinding.tool.LayoutXmlProcessor;

/** 
 * Base data that a variant encapsulates
 * TODO - incorporate splits code into VariantContext
 */
public class VariantData {

    private Set<String> densityFilters;
    private Set<String> languageFilters;
    private Set<String> abiFilters;

    @Nullable
    private LayoutXmlProcessor layoutXmlProcessor;

    /**
     * If true, variant outputs will be considered signed. Only set if you manually set the outputs
     * to point to signed files built by other tasks.
     */
    public boolean outputsAreSigned = false;

    //@NonNull private final OutputScope outputScope;

    //@NonNull private final OutputFactory outputFactory;
    //public VariantOutputFactory variantOutputFactory;

    //private final MultiOutputPolicy multiOutputPolicy;


	public VariantData( /*@NonNull AndroidConfig androidConfig*/) {
		/*
        final Splits splits = androidConfig.getSplits();
        boolean splitsEnabled =
                splits.getDensity().isEnabled()
                        || splits.getAbi().isEnabled()
                        || splits.getLanguage().getEnabled();

        // eventually, this will require a more open ended comparison.
        multiOutputPolicy =
                (androidConfig.getGeneratePureSplits()
                                        || variantConfiguration.getType() == VariantType.FEATURE)
                                && variantConfiguration.getMinSdkVersionValue() >= 21
                        ? MultiOutputPolicy.SPLITS
                        : MultiOutputPolicy.MULTI_APK;

        // warn the user if we are forced to ignore the generatePureSplits flag.
        if (splitsEnabled
                && androidConfig.getGeneratePureSplits()
                && multiOutputPolicy != MultiOutputPolicy.SPLITS) {
           SdkLogger.getLogger(VariantData.class.getName()).warn(
                    String.format("Variant %s, MinSdkVersion %s is too low (<21) "
                                    + "to support pure splits, reverting to full APKs",
                            variantConfiguration.getFullName(),
                            variantConfiguration.getMinSdkVersion().getApiLevel()));
        }
        */
	}

}
