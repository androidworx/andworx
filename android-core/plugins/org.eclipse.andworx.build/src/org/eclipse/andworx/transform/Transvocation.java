/*
 * Copyright (C) 2015 The Android Open Source Project
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
package org.eclipse.andworx.transform;

import java.util.Collection;

import com.android.annotations.Nullable;
import com.android.build.api.transform.SecondaryInput;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformOutputProvider;
import com.google.common.collect.ImmutableList;

/**
 * Aggregation of transform invocation inputs
 */
public class Transvocation {

    Collection<TransformInput> inputs;
    Collection<TransformInput> referencedInputs;
    Collection<SecondaryInput> secondaryInputs = ImmutableList.of();
    TransformOutputProvider transformOutputProvider;
    boolean isIncremental = false;

    /**
     * Construct Transvocation object
     * @param inputs TransformInput inputs
     * @param referencedInputs Referenced TransformInput inpuuts
     * @param transformOutputProvider Transform Output Provider
     */
    public Transvocation (
	    Collection<TransformInput> inputs,
	    Collection<TransformInput> referencedInputs,
	    @Nullable TransformOutputProvider transformOutputProvider) {
		this.inputs = ImmutableList.copyOf(inputs);
		this.referencedInputs = ImmutableList.copyOf(referencedInputs);
		this.transformOutputProvider = transformOutputProvider;
	}

	public Collection<TransformInput> getInputs() {
		return inputs;
	}

	public TransformOutputProvider getOutputProvider() {
		return transformOutputProvider;	
	}

	public Collection<TransformInput> getReferencedInputs() {
		return referencedInputs;
	}

	public Collection<SecondaryInput> getSecondaryInputs() {
		return secondaryInputs;
	}

	public boolean isIncremental() {
		return isIncremental;
	}

}
