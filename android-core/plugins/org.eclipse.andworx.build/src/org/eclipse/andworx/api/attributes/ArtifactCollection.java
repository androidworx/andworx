/*
 * Copyright 2016 the original author or authors.
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
package org.eclipse.andworx.api.attributes;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.eclipse.andworx.api.attributes.AndroidArtifacts.ArtifactType;

import com.android.ide.common.res2.ResourceSet;

/**
 * A collection of artifacts resolved for a configuration. The configuration is resolved on demand when
 * the collection is queried.
 */
public class ArtifactCollection {

	protected final String name;
	
	public ArtifactCollection(String name) {
		this.name = name;
	}

	
    public String getName() {
		return name;
	}

	/**
     * A file collection containing the files for all artifacts in this collection.
     * This is primarily useful to wire this artifact collection as a task input.
     */
    public Set<File> getArtifactFiles() {
    	return Collections.emptySet();
    }
    
    public List<ResourceSet> getResourceSetList() {
    	return Collections.emptyList();
    }

    public List<File> getFiles(ArtifactType type) {
    	return Collections.emptyList();
    }
    
    public Set<File> getTansformCollection(ArtifactType artifactType) throws IOException {
    	 throw new UnsupportedOperationException("Transform artifact type " + artifactType + " not supported");
    }
    
    /**
     * Returns the resolved artifacts, performing the resolution if required.
     * This will resolve the artifact metadata and download the artifact files as required.
     *
     * @throws ResolveException On failure to resolve or download any artifact.
     */
    /*
    Set<ResolvedArtifactResult> getArtifacts();
    */
    /**
     * Returns any failures to resolve the artifacts for this collection.
     *
     * @since 4.0
     *
     * @return A collection of exceptions, one for each failure in resolution.
     */
    /*
    Collection<Throwable> getFailures();
    */

}
