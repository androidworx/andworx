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
package org.eclipse.andworx.repo;

import java.io.File;

import org.apache.maven.artifact.Artifact;
import org.eclipse.andworx.maven.Dependency;
import org.eclipse.andworx.project.Identity;

import com.android.SdkConstants;

	/**
	 * Dependency residing in a repository
	 */
    public class DependencyArtifact implements Dependency {

	    private final Identity identity;
	    private boolean isLibrary;
	    private File path;
	
	    /**
	     * Construct DependencyArtifact from coordinates
	     * @param group
	     * @param artifactId
	     * @param version
	     */
	    public DependencyArtifact(String group, String artifactId, String version) {
	        identity = new Identity(group, artifactId, version);
	    }
	
	    /**
	     * Construct DependencyArtifact from artifact specification
	     * @param artifact
	     */
	    public DependencyArtifact(Artifact artifact) {
	    	identity = new Identity(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
	        isLibrary = SdkConstants.EXT_AAR.equals(artifact.getType().toLowerCase());
	        path = artifact.getFile();
	    }
	
	    @Override
	    public Identity getIdentity() {
	    	return identity;
	    }
	    
	    @Override
	    public boolean isLibrary() {
	        return isLibrary;
	    }
	
	    @Override
	    public File getPath() {
	        return path;
	    }
	
		public void setLibrary(boolean isLibrary) {
			this.isLibrary = isLibrary;
		}
	
		public void setPath(File path) {
			this.path = path;
		}
	
		@Override
		public String toString() {
			return identity.toString();
		}
	
		@Override
		public int hashCode() {
			return identity.hashCode(); 
		}
	
		@Override
		public boolean equals(Object object) {
			if ((object == null) || !(object instanceof DependencyArtifact))
				return false;
			DependencyArtifact other = (DependencyArtifact)object;
			return identity.equals(other.identity);
		}
}
