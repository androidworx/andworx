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

import org.eclipse.andworx.build.task.BuildConfigTask;
import org.eclipse.andworx.build.task.D8Task;
import org.eclipse.andworx.build.task.DesugarTask;
import org.eclipse.andworx.build.task.ManifestMergerTask;
import org.eclipse.andworx.build.task.MergeResourcesTask;
import org.eclipse.andworx.build.task.NonNamespacedLinkResourcesTask;
import org.eclipse.andworx.build.task.PackageApplicationTask;
import org.eclipse.andworx.build.task.PreManifestMergeTask;
import org.eclipse.andworx.config.SecurityController;
import org.eclipse.andworx.context.VariantContext;
import org.eclipse.andworx.file.CacheManager;
import org.eclipse.andworx.file.FileManager;
import org.eclipse.andworx.helper.BuildElementFactory;
import org.eclipse.andworx.helper.BuildHelper;
import org.eclipse.andworx.helper.ProjectBuilder;
import org.eclipse.andworx.jpa.PersistenceService;
import org.eclipse.andworx.maven.MavenServices;
import org.eclipse.andworx.process.java.JavaQueuedProcessor;
import org.eclipse.andworx.project.AndroidConfiguration;
import org.eclipse.andworx.project.AndworxProject;
import org.eclipse.andworx.project.ProjectProfile;
import org.eclipse.andworx.registry.ProjectRegistry;
import org.eclipse.andworx.task.ManifestMergeHandler;
import org.eclipse.andworx.task.TaskFactory;
import org.eclipse.andworx.transform.Pipeline;
import org.eclipse.jdt.core.IJavaProject;

import au.com.cybersearch2.classyjpa.entity.PersistenceWork;
import au.com.cybersearch2.classyjpa.persist.PersistenceContext;
import au.com.cybersearch2.classytask.Executable;

public interface BuildFactory {

	ProjectRegistry getProjectRegistry();
	
	/**
	 * Returns persistence context
	 * @return PersistenceContext object
	 */
	PersistenceContext getPersistenceContext();

	/** 
	 * Returns service which consumes persistence tasks 
	 * @return PersistenceService object
	 */
	PersistenceService getPersistenceService();

	/**
	 * Returns Android configuration persisted using JPA
	 * @return AndroidConfiguration object
	 */
	AndroidConfiguration getAndroidConfiguration();

	SecurityController getSecurityController();
	/**
	 * Returns m2e Maven services
	 * @return MavenServices object
	 */
	MavenServices getMavenServices();

	/**
	 * Returns manager of file cache and bundle files
	 * @return FileManager object
	 */
	FileManager getFileManager();

	CacheManager getCacheManager();
	
	File getBundleFile(String filePath);
	
	BuildElementFactory getBuildElementFactory();

	BuildHelper getBuildHelper();
	
	ProjectBuilder getProjectBuilder(IJavaProject javaProject, ProjectProfile profile);
	
	JavaQueuedProcessor getJavaQueuedProcessor();
	
	TaskFactory getTaskFactory();

    PreManifestMergeTask getPreManifestMergeTask(VariantContext variantScope, File manifestOutputDir);
    
    BuildConfigTask getBuildConfigTask(String manifestPackage, VariantContext variantScope);

    ManifestMergerTask getManifestMergerTask(ManifestMergeHandler manifestMergeHandler);

    MergeResourcesTask getMergeResourcesTask(VariantContext variantScope);
    
    NonNamespacedLinkResourcesTask getNonNamespacedLinkResourcesTask(VariantContext variantScope);
 
    DesugarTask getDesugarTask(Pipeline pipeline, ProjectBuilder projectBuilder);
    
    D8Task getD8Task(Pipeline pipeline, VariantContext variantScope);
  
    PackageApplicationTask getPackageApplicationTask(AndworxProject andworxProject, VariantContext variantScope);
    
	/**
	 * Executes given persistence work and returns object to notify result 
	 * @param persistenceWork
	 * @return Executable object
	 */
	Executable getExecutable(PersistenceWork persistenceWork);

}