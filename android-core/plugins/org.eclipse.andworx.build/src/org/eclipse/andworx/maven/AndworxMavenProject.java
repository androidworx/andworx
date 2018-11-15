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
package org.eclipse.andworx.maven;

import java.util.ArrayList;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;

import com.android.SdkConstants;

/**
 * A Maven project wrapper for configuration and resolution of aar and jar dependencies
 */
public class AndworxMavenProject {

	/** Maven model represented as a Java object */
    private final MavenProject mavenProject;

    /**
     * Construct AndworxMavenProject object
     * @param mavenProject Mavel model as Java object.
     */
    public AndworxMavenProject(
    		MavenProject mavenProject) {
        this.mavenProject = mavenProject;
    }

    /**
     * Returns flag set true if this is a Library project
     * @return boolean
     */
    public boolean isLibrary() {
        String packaging = mavenProject.getPackaging().toLowerCase();
        return SdkConstants.EXT_AAR.equals(packaging);
    }

    /**
     * Returns list of non-runtime dependencies
     * @return Dependency list
     */
    public List<MavenDependency> getNonRuntimeDependencies() {
        List<MavenDependency> list = new ArrayList<>(mavenProject.getArtifacts().size());

        for (Artifact a : mavenProject.getArtifacts()) {
            if (a.getArtifactHandler().isAddedToClasspath()) {
                if (!Artifact.SCOPE_COMPILE.equals(a.getScope()) && !Artifact.SCOPE_RUNTIME.equals(a.getScope())) {
                    list.add(new MavenDependency(a));
                }
            }
        }
        return list;
    }

    /**
     * Returns list of library dependencies
     * @return Dependency list
     */
    public List<MavenDependency> getLibraryDependencies() {
        List<MavenDependency> results = new ArrayList<>(mavenProject.getArtifacts().size());

        for (Artifact a :  mavenProject.getArtifacts()) {
        	MavenDependency dependency = new MavenDependency(a);
            boolean isJarDependency = a.getArtifactHandler().isAddedToClasspath() &&
            		 (Artifact.SCOPE_COMPILE.equals(a.getScope()) || Artifact.SCOPE_RUNTIME.equals(a.getScope()));
            if (isJarDependency || dependency.isLibrary()) {
                results.add(dependency);
            }
        }
        return results;
    }

    /**
     * Returns Maven project
     * @return MavenProject object
     */
	public MavenProject getProject() {
		return mavenProject;
	}

}
