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

import org.eclipse.andworx.aapt.Aapt2Executor;
import org.eclipse.andworx.build.AndworxMessageReceiver;
import org.eclipse.andworx.build.task.NonNamespacedLinkResourcesTask;
import org.eclipse.andworx.context.VariantContext;
import org.eclipse.andworx.helper.BuildElementFactory;
import org.eclipse.andworx.helper.BuildHelper;
import org.eclipse.andworx.log.SdkLogger;
import org.eclipse.andworx.task.TaskFactory;

import com.android.ide.common.blame.MergingLog;
import com.android.ide.common.blame.MergingLogRewriter;
import com.android.ide.common.blame.ParsingProcessOutputHandler;
import com.android.ide.common.blame.parser.ToolOutputParser;
import com.android.ide.common.blame.parser.aapt.Aapt2OutputParser;
import com.android.ide.common.process.ProcessOutputHandler;

import dagger.Module;
import dagger.Provides;

@Module
public class LinkResourcesModule {

	private static SdkLogger logger = SdkLogger.getLogger(LinkResourcesModule.class.getName());

	private final VariantContext variantScope;

	public LinkResourcesModule(VariantContext variantScope) {
		this.variantScope = variantScope;
	}
	
	@Provides 
	NonNamespacedLinkResourcesTask provideNonNamespacedLinkResourcesTask(Aapt2Executor aapt2Executor, BuildHelper buildHelper, BuildElementFactory buildElementFactory, TaskFactory taskFactory) {
		File blameLogFolder = variantScope.getResourceBlameLogDir();
        MergingLog mergingLog = new MergingLog(blameLogFolder);

        ProcessOutputHandler processOutputHandler =
                new ParsingProcessOutputHandler(
                        new ToolOutputParser(new Aapt2OutputParser(), logger),
                        new MergingLogRewriter(mergingLog::find, new AndworxMessageReceiver("aapt2")));
        aapt2Executor.setDelegate(processOutputHandler);
        return new NonNamespacedLinkResourcesTask(variantScope, aapt2Executor, buildHelper, buildElementFactory, taskFactory);
	}
}
