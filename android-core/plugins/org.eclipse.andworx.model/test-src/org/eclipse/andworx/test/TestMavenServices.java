package org.eclipse.andworx.test;

import java.io.File;

import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.eclipse.andworx.maven.AndworxMavenProject;
import org.eclipse.andworx.maven.MavenServices;
import org.eclipse.andworx.repo.ProjectRepository;

public class TestMavenServices implements MavenServices {
	private static final String GROUP_ID = "com.android.example";
	private static final String ARTIFACT_ID = "permissions";
	private static final String VERSION = "1.0.0-SNAPSHOT";

	@Override
	public String getPomFilename() {
		return "pom.xml";
	}

	@Override
	public AndworxMavenProject createAndworxProject(MavenProject mavenProject) {
		return new AndworxMavenProject(mavenProject);
	}

	@Override
	public void createMavenModel(File pomFile, Model model) {

	}

	@Override
	public MavenProject readMavenProject(File pomXml) {
		Model model = new Model();
		model.setArtifactId(ARTIFACT_ID);
		model.setGroupId(GROUP_ID);
		model.setVersion(VERSION);
		model.setPackaging("apk");
		MavenProject mavenProject = new MavenProject(model);
		return mavenProject;
	}

	@Override
	public ProjectRepository configureLibraries(AndworxMavenProject mavenProject, File repositoryLocation) {
		return null;
	}

}
