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
package org.eclipse.andmore.internal.build.builders;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.andmore.AndmoreAndroidPlugin;
import org.eclipse.andmore.internal.build.builders.BaseBuilder.AbortBuildException;
import org.eclipse.andworx.build.AndworxContext;
import org.eclipse.andworx.build.AndworxFactory;
import org.eclipse.andworx.build.task.DesugarTask;
import org.eclipse.andworx.context.VariantContext;
import org.eclipse.andworx.core.AndworxVariantConfiguration;
import org.eclipse.andworx.helper.ProjectBuilder;
import org.eclipse.andworx.registry.ProjectState;
import org.eclipse.andworx.task.TaskFactory;
import org.eclipse.andworx.transform.Transform;
import org.eclipse.andworx.transform.TransformAgent;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;

import com.android.build.api.transform.QualifiedContent.ContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.TransformInput;


public class DesugarOp extends TransformAgent implements BuildOp<PostCompilerContext>, Transform {
    
	public DesugarOp() {
	}

	@Override
	public String getName() {
		return ProjectBuilder.DESUGAR;
	}

	@Override
	public boolean execute(PostCompilerContext context) throws CoreException, AbortBuildException, InterruptedException {
		context.clearPipeline();
		IProject project = context.getProject();
        if (context.isDebugLog()) {
            AndmoreAndroidPlugin.log(IStatus.INFO, "%s %s", project.getName(), getName());
        }
        AndworxContext objectFactory = AndworxFactory.instance();
        ProjectState projectState = objectFactory.getProjectState(project);
        ProjectBuilder projectBuilder = projectState.getProjectBuilder();
        VariantContext variantScope = context.getVariantContext();
        AndworxVariantConfiguration variantConfig = variantScope.getVariantConfiguration();
        int minSdk = variantConfig.getMinSdkVersionValue();
        boolean verbose = variantConfig.getBuildType().isDebuggable();
        File outputDir = getOutputRootDir(getName(), variantScope);
        DesugarTask desugarTask = objectFactory.getDesugarTask(context, projectBuilder);
        desugarTask.configure(
        		minSdk,
        		verbose,
        		outputDir,
        		projectState.getBootClasspath(true),
        		this);
        Set<File> files = new HashSet<>();
        List<File> dependencyJars = projectBuilder.getExportClasspath(context.getJavaProject());
        for (File jarFile: dependencyJars)
        	files.add(jarFile);
        Collection<TransformInput> inputs = 
        		Collections.singletonList(createTransformInput(files, Collections.emptySet(), Collections.emptySet()));
		context.setPipelineInput(inputs);
		desugarTask.schedule();
        TaskFactory taskFactory = context.getTaskFactory();
    	synchronized(taskFactory) {
    		taskFactory.wait();
    	}
		return true;
	}

	@Override
	public void commit(PostCompilerContext context) throws IOException {
	}

	@Override
	public String getDescription() {
		return DesugarTask.TASK_NAME;
	}

	@Override
	public Set<? super Scope> getReferencedScopes() {
		return Collections.emptySet();
	}

	@Override
	public Set<ContentType> getInputTypes() {
		return Collections.emptySet();
	}
}
