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
package org.eclipse.andworx.modules;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

import org.eclipse.andworx.AndworxConstants;
import org.eclipse.andworx.build.AndroidProjectReader;
import org.eclipse.andworx.build.AndworxContext;
import org.eclipse.andworx.build.AndworxFactory;
import org.eclipse.andworx.model.RepositoryUrl;
import org.eclipse.andworx.polyglot.AndworxBuildParser;
import org.eclipse.andworx.project.AndroidDigest;
import org.eclipse.andworx.project.AndroidProjectOpener;
import org.eclipse.andworx.project.AndroidWizardListener;
import org.eclipse.andworx.project.ProjectProfile;
import org.eclipse.andworx.record.ModelType;
import org.eclipse.andworx.topology.entity.ModelNode.TypedElement;

import com.android.ide.common.xml.ManifestData;

/**
 * Creates a project from build files 
 */
public class ModelProjectReader implements AndroidProjectReader {

	/**
	 * AndroidWizardListener implementation layered between Import Wizard and  Bbuild bundle
	 */
	private class ModelOpenListener implements AndroidWizardListener {

		/** Import Wizard listener. Events are filtered in a multi-module scenario as only the project being imported is to be notified, excluding errors. */
		private final AndroidWizardListener chainListener;
		/** Fatal error event occurred */
		private boolean errorFlagged;
		/** Content of Android manifest.xml. Will be null if non-Android project is parsed. */
		private ManifestData manifestData;
		/** Content of build file in Android project. Will be null if non-Android project is parsed.  */
		private AndroidDigest androidDigest;
		/** Project profile created after dependencies are validated */
		private ProjectProfile projectProfile;
		/** Flag set true if all dependencies are resolved */
		private boolean isProfileResolved;
		/** Manifest file path in text format passed in manifest error event */
		protected String manifestFile;
		

		/**
		 * Create ModelOpenListener object
		 * @param chainListener Import Wizard listener.
		 */
		public ModelOpenListener(AndroidWizardListener chainListener) {
			this.chainListener = chainListener;
			manifestFile = "";
		}
		
		public boolean isErrorFlagged() {
			return errorFlagged;
		}

		public ManifestData getManifestData() {
			return manifestData;
		}

		public AndroidDigest getAndroidDigest() {
			return androidDigest;
		}

		public ProjectProfile getProjectProfile() {
			return projectProfile;
		}

		public boolean isProfileResolved() {
			return isProfileResolved;
		}

		public String getManifestFile() {
			return manifestFile;
		}

		public void clear() {
			manifestData = null;
			projectProfile = null;
			isProfileResolved = false;
			manifestFile = null;
		}
		
		@Override
		public void onManifestParsed(ManifestData manifestData) {
			this.manifestData = manifestData;
		}

		@Override
		public void onConfigParsed(AndroidDigest androidDigest) {
			this.androidDigest = androidDigest;
		}

		@Override
		public void onProfileResolved(ProjectProfile projectProfile, boolean isResolved, String message) {
			this.projectProfile = projectProfile;
			isProfileResolved = isResolved;
		}

		@Override
		public void onError(String message) {
			errorFlagged = true;
			chainListener.onError(message);
		}

		@Override
		public void onNoManifest(String manifestFile) {
			this.manifestFile = manifestFile;
		}

		
	}
	/** Maintains project associations in multi-module projects */
	private final WorkspaceModeller workspaceModeller;
	/** Interface to project import wizard page */
	private final AndroidWizardListener androidWizardListener;
	/** Object factory */
	private final AndworxContext objectFactory;
	private final AndworxBuildParser parser;
	private final ModelBuilder modelBuilder;
	private final ModelParserContext parserContext;

	/**
	 * Construct ModelProjectReader object
	 * @param workspaceModeller Maintains project associations in multi-module projects
	 * @param androidWizardListener Interface to project import wizard page
	 */
	public ModelProjectReader(WorkspaceModeller workspaceModeller, AndroidWizardListener androidWizardListener) {
		this.workspaceModeller = workspaceModeller;
		this.androidWizardListener = androidWizardListener;
		objectFactory = AndworxFactory.getAndworxContext();
		parserContext = new ModelParserContext();
		parser = objectFactory.getAndworxBuildParser(parserContext);
		modelBuilder = new ModelBuilder();
		parser.setChainSyntaxItemReceiver(modelBuilder);
	}

	/**
	 * Perform tasks to open an import project. The AndroidWizardListener API notifies the user of progress.
	 * @param buildFile The project build file (either build.andworx or build.gradle)
	 */
	@Override
	public void runOpenTasks(File buildFile) {
		parserContext.reset();
		// Deque to walk directories both forward and backward
        Deque<File> directoryDeque = new ArrayDeque<>();
        // Walk back ancestor directories to root directory
		walkToRootDirectory(buildFile.getParentFile(), directoryDeque);
		// Now walk forward from root project to obtain project context
        Iterator<File> directoryIterator = directoryDeque.descendingIterator();
        int level = 0;
        List<TypedElement> elements = new ArrayList<>();
        try {
	        while (directoryIterator.hasNext()) {
	        	File segment = directoryIterator.next();
	        	if (level == 0)
	        		parserContext.setRootProject(segment);
	        	ModelOpenListener modelOpenListener = new ModelOpenListener(androidWizardListener);
	        	// If WorkspaceModeller does not have a module for this segment, then create one
	        	if (!workspaceModeller.hasModuleForLocation(segment)) {
	        		modelBuilder.reset();
	        		// Read build.andworx, if it exists, in preference to build.gradle.
	        		// This allows for customization without affecting Gradle build
	        		File andworxBuildFile = new File(segment, AndworxConstants.FN_BUILD_ANDWORX);
	        		if (andworxBuildFile.exists()) {
	        			if (!parse(andworxBuildFile, parserContext, modelOpenListener))
							return;
	        		}
	        		else {
	        			File gradleBuildFile = new File(segment, AndworxConstants.FN_BUILD_GRADLE);
	        			if (!parse(gradleBuildFile, parserContext, modelOpenListener))
							return;
	        		}
	        		if (directoryIterator.hasNext()) {
	        			elements.addAll(modelBuilder.getElements());
	        		} else {
	        			// Walked back to project being opened
	        			if (modelOpenListener.getManifestData() == null) 
	        				// No manifest is fatal for segment being opened by the user
	        				androidWizardListener.onNoManifest(modelOpenListener.getManifestFile());
	        			// Apply model elements collected during directory walk
	        			for (TypedElement element: elements) {
	        				if (element.modelType == ModelType.allProjects)
	        					parser.getAndroidDigest().addRepositoryUrl((RepositoryUrl)element.nodeElement);
	        			}
	        		}
	        		workspaceModeller.createModule(segment, level, modelBuilder);
	        	} else {
	        		// Collect model elements previously recorded for this segment
	        		elements.addAll(workspaceModeller.getElementsByLocation(segment));
	        	}
        		++level;
	        }
        } catch (InterruptedException e) {
        	throw new ModelException("Interrupt received", e);
        }
	}

	/**
	 * Parse build file
	 * @param andworxBuildFile Build file
	 * @param parserContext Parser context
	 * @param modelOpenListener Callback for handling results of parsing the build file
	 * @return flag set true if parse was successful
	 */
	private boolean parse(File andworxBuildFile, ModelParserContext parserContext, ModelOpenListener modelOpenListener) {
		AndroidProjectOpener androidProjectOpener = new AndroidProjectOpener(modelOpenListener) {
			@Override
			public void runOpenTasks(File buildFile) {
				parse(buildFile, parser);
			}

		};
		modelOpenListener.clear();
		androidProjectOpener.runOpenTasks(andworxBuildFile);
		return !modelOpenListener.isErrorFlagged();
	}

	/**
	 * Regressively walk directory chain until directory not conataining a build file is encountered
	 * @param segment Directory location at current step in regression
	 * @param directories Container to collect directories
	 */
	private void walkToRootDirectory(File segment, Collection<File> directories) {
		if (workspaceModeller.isProjectLocationValid(segment)) {
			directories.add(segment);
			File parentSegment = segment.getParentFile();
			if (parentSegment != null) 
				walkToRootDirectory(parentSegment, directories);
		}
	}

}
