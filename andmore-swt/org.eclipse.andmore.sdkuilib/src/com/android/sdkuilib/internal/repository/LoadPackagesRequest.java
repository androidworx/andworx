/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 * 
 */
package com.android.sdkuilib.internal.repository;

import java.util.List;

import com.android.repository.api.ProgressRunner;
import com.android.repository.api.RepoManager.RepoLoadedCallback;

/**
 * Packet containing parameters to call RepoManager load()
 * 
 *
 *     <pre>public abstract void load(long cacheExpirationMs,
            @Nullable List<RepoLoadedCallback> onLocalComplete,
            @Nullable List<RepoLoadedCallback> onSuccess,
            @Nullable List<Runnable> onError,
            @NonNull ProgressRunner runner,
            @Nullable Downloader downloader,
            @Nullable SettingsController settings,
            boolean sync);
        </pre>
 * @author Andrew Bowley
 *
 * 12-11-2017
 */
public class LoadPackagesRequest {

	private ProgressRunner runner;
	private List<RepoLoadedCallback> onLocalComplete;
	private List<RepoLoadedCallback> onSuccess;
	private List<Runnable> onError;
	
	/**
	 * 
	 */
	public LoadPackagesRequest(ProgressRunner runner) {
		this.runner = runner;
	}

	public List<RepoLoadedCallback> getOnLocalComplete() {
		return onLocalComplete;
	}

	public void setOnLocalComplete(List<RepoLoadedCallback> onLocalComplete) {
		this.onLocalComplete = onLocalComplete;
	}

	public List<RepoLoadedCallback> getOnSuccess() {
		return onSuccess;
	}

	public void setOnSuccess(List<RepoLoadedCallback> onSuccess) {
		this.onSuccess = onSuccess;
	}

	public List<Runnable> getOnError() {
		return onError;
	}

	public void setOnError(List<Runnable> onError) {
		this.onError = onError;
	}

	public ProgressRunner getRunner() {
		return runner;
	}

}
