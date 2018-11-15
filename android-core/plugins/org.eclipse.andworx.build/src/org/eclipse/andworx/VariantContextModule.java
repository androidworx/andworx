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
package org.eclipse.andworx;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.aether.repository.NoLocalRepositoryManagerException;
import org.eclipse.andworx.aar.LibraryArtifactCollection;
import org.eclipse.andworx.context.AndroidEnvironment;
import org.eclipse.andworx.context.VariantContext;
import org.eclipse.andworx.core.AndworxVariantConfiguration;
import org.eclipse.andworx.exception.AndworxException;
import org.eclipse.andworx.log.SdkLogger;
import org.eclipse.andworx.maven.Dependency;
import org.eclipse.andworx.model.BuildTypeImpl;
import org.eclipse.andworx.project.AndworxProject;
import org.eclipse.andworx.project.ProjectConfiguration;
import org.eclipse.andworx.project.ProjectProfile;
import org.eclipse.andworx.repo.ProjectRepository;
import org.eclipse.andworx.sdk.SdkProfile;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.builder.core.BuilderConstants;
import com.android.builder.core.DefaultManifestParser;
import com.android.builder.core.ManifestAttributeSupplier;
import com.android.builder.core.VariantType;
import com.android.builder.model.BuildTypeContainer;
import com.android.builder.model.SourceProvider;
import com.android.ide.common.res2.MergingException;

import dagger.Module;
import dagger.Provides;

/**
 * Constructs variant contexts for given project profile
 */
@Module
public class VariantContextModule {

	private static SdkLogger logger = SdkLogger.getLogger(VariantContextModule.class.getName());

	private final AndworxProject andworxProject;
	private final ProjectConfiguration projectConfig;
    /** Contains parsed Manifests */
	private final AndroidEnvironment androidEnvironment;
    private final Map<File, ManifestAttributeSupplier> manifestParserMap;

	public VariantContextModule(AndworxProject andworxProject, ProjectConfiguration projectConfig, AndroidEnvironment androidEnvironment) {
		this.andworxProject = andworxProject;
		this.projectConfig = projectConfig;
		this.androidEnvironment = androidEnvironment;
       	manifestParserMap = new HashMap<>();
	}
	
	@Provides
	Map<String, VariantContext> createVariantContextMap() {
 		AndworxVariantConfiguration.Builder configbuilder = 
				new AndworxVariantConfiguration.VariantConfigurationBuilder();
		SourceProvider sourceSet = projectConfig.getDefaultSourceSet();
		ProjectProfile profile = projectConfig.getProfile();
		LibraryArtifactCollection dependencies = new LibraryArtifactCollection(profile.getIdentity().getArtifactId(), true, logger);
        ProjectRepository projectRepository;
		try {
			projectRepository = new ProjectRepository(androidEnvironment.getRepositoryLocation());
		} catch (NoLocalRepositoryManagerException e) {
			throw new IllegalStateException("Library repository not available");
		}
        for (Dependency dependency: profile.getDependencies()) {
        	File projectDirectory = projectRepository.getMetadataPath(dependency.getIdentity(), SdkConstants.EXT_AAR).getParentFile();
        	try {
				dependencies.add(projectDirectory);
			} catch (MergingException e) {
				throw new AndworxException("Error merging resources in directory " + projectDirectory.getAbsolutePath(), e);
			}
        }
        Map<String, VariantContext> variantContextMap = new HashMap<>();
 		for (BuildTypeContainer container: projectConfig.getBuildTypes()) {
	 		BuildTypeImpl buildType = (BuildTypeImpl)container.getBuildType();
	 		String variantName = buildType.getName();
			AndworxVariantConfiguration variantConfiguration = 
					configbuilder.create(
							projectConfig.getProductFlavorMap().get(BuilderConstants.MAIN),
							sourceSet, 
							getParser(sourceSet.getManifestFile()), 
							buildType, 
							null, 
							VariantType.DEFAULT);
			VariantContext context = 
					new VariantContext(
					        andworxProject, 
							new SdkProfile(androidEnvironment));
			context.putVariantConfiguration(variantName, variantConfiguration);
			context.setDependencies(dependencies);
			variantContextMap.put(variantName, context);
		}
		return variantContextMap;
	}

	/**
	 * Returns manifest attribute supplier for given manifest file 
	 * @param file Manifest file
	 * @return
	 */
    @NonNull
    private ManifestAttributeSupplier getParser(@NonNull File file) {
        return manifestParserMap.computeIfAbsent(file, DefaultManifestParser::new);
    }
    
}
