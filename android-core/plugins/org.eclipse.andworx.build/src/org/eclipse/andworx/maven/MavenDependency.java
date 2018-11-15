/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not FilenameFilter filter this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
  http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.eclipse.andworx.maven;

import java.io.File;

import org.apache.maven.artifact.Artifact;

import org.eclipse.andworx.maven.Dependency;
import org.eclipse.andworx.project.Identity;

import com.android.SdkConstants;

public class MavenDependency implements Dependency {

	/** Coordinates used to distingush between artifacts. */
	private final Identity identity;
	/** Mixes artifact definition concepts (groupId, artifactId, version) with dependency information (version range, scope) */
    private final Artifact artifact;

    /**
     * Construct MavenDependency object
     * @param artifact Mixes artifact definition concepts with dependency information 
     */
    public MavenDependency(Artifact artifact) {
        super();
        this.artifact = artifact;
        this.identity = new Identity(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
    }

	public Artifact getArtifact() {
		return artifact;
	}

    public String getArtifactId() {
        return artifact.getArtifactId();
    }

    public String getGroupId() {
        return artifact.getGroupId();
    }

    public String getVersion() {
        return artifact.getVersion();
    }

    public File getPath() {
        return artifact.getFile();
    }

	@Override
	public Identity getIdentity( ) {
		return identity;
	}
	
	@Override
	public boolean isLibrary() {
        return SdkConstants.EXT_AAR.equals(artifact.getType());
	}

    @Override
    public String toString() {
        return artifact.toString();
    }

	@Override
	public int hashCode() {
		return artifact.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if ((obj != null) && (obj instanceof MavenDependency))
			return ((MavenDependency)obj).artifact.equals(artifact);
		return false;
	}
}
