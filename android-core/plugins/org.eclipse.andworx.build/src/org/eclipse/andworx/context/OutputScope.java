/*
 * Copyright (C) 2017 The Android Open Source Project
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
package org.eclipse.andworx.context;

import java.util.List;

import com.android.build.VariantOutput.OutputType;

import com.android.annotations.NonNull;
import com.android.ide.common.build.ApkData;

/**
 * Information about expected Outputs from a build.
 *
 * <p>This will either contain:
 *
 * <ul>
 *   <li>A single main APK
 *   <li>Multiple full APKs when the {@code multiOutputPolicy} is {@link
 *       MultiOutputPolicy#MULTI_APK}
 *   <li>A single main APK with multiple split APKs (when the {@code multiOutputPolicy} is {@link
 *       MultiOutputPolicy#SPLITS}
 * </ul>
 */
public interface OutputScope {
    /**
     * Returns the enabled splits for this variant. A split can be disabled due to build
     * optimization.
     *
     * @return list of splits to process for this variant.
     */
    @NonNull
    public List<ApkData> getApkDatas();

    /** 
     * Returns main APK data 
     * @return ApkData object
     */
    @NonNull
    public ApkData getMainSplit();
 
    /**
     * Returns a list of ApkData objects selected by given output type
     * @param outputType OutputType enum
     * @return list of ApkData objects
     */
    @NonNull
    public List<ApkData> getSplitsByType(OutputType outputType);
}
