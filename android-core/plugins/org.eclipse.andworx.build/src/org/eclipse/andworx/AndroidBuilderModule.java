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

import org.eclipse.andworx.build.AndworxIssueReport;
import org.eclipse.andworx.build.AndworxMessageReceiver;
import org.eclipse.andworx.context.AndroidEnvironment;
import org.eclipse.andworx.context.VariantContext;
import org.eclipse.andworx.sdk.SdkInfoWrapper;

import com.android.builder.core.AndroidBuilder;
import com.android.ide.common.process.JavaProcessExecutor;
import com.android.ide.common.process.ProcessExecutor;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.utils.ILogger;

import dagger.Module;
import dagger.Provides;

@Module
public class AndroidBuilderModule {
	
	private final String projectId;
	private final boolean isDebuggable;
	private final VariantContext variantScope;
	private final AndroidEnvironment androidEnvironment;
	
	public AndroidBuilderModule(VariantContext variantScope, AndroidEnvironment androidEnvironment) {
		this.variantScope = variantScope;
		this.projectId = variantScope.getAndworxProject().getName();
		this.isDebuggable = variantScope.getVariantConfiguration().getBuildType().isDebuggable();;
		this.androidEnvironment = androidEnvironment;
	}
	
	@Provides 
	AndroidBuilder provideAndroidBuilder(
			ProcessExecutor processExecutor, 
			JavaProcessExecutor javaProcessExecutor, 
			AndworxIssueReport andworxIssueReport) {
		ILogger logger = andworxIssueReport.getLogger();
		AndroidSdkHandler sdkHandler = androidEnvironment.getAndroidSdkHandler();
		AndroidBuilder androidBuilder = 
			new AndroidBuilder(
				projectId, 
				null, // created by
				processExecutor,
				javaProcessExecutor,
				andworxIssueReport,
				new AndworxMessageReceiver(projectId),
				logger,
				isDebuggable);
		androidBuilder.setTargetInfo(variantScope.getTargetInfo());		
		SdkInfoWrapper sdkInfoWrapper = new SdkInfoWrapper(sdkHandler.getLocation(), logger);
		androidBuilder.setSdkInfo(sdkInfoWrapper.getSdkInfo());
		return androidBuilder;		
	}
	
}
