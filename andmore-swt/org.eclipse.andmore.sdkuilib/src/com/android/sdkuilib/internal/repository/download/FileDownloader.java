/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.sdkuilib.internal.repository.download;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.api.Downloader;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.SettingsController;
import com.android.repository.io.FileOp;
import com.android.repository.util.InstallerUtil;
import com.android.utils.Pair;
import com.google.common.io.ByteProcessor;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;

	/**
	 * A {@link Downloader} implementation that uses the old {@link DownloadCache}.
	 */
	public class FileDownloader implements Downloader {

		class ByteStreamer implements ByteProcessor<Void> {
            private double total;
			private long progress;
			private OutputStream outputStream;
			private volatile boolean isCancelled;

			public ByteStreamer(double total, OutputStream outputStream) {
				this.total = total;
				this.outputStream = outputStream;
			}
			
			public double getProgress() {
				return (double)progress / total;
			}

			public void setCancelled(boolean isCancelled) {
				this.isCancelled = isCancelled;
			}

			@Override
			public Void getResult() {
				return null;
			}

			@Override
			public boolean processBytes(byte[] buffer, int offset, int length) throws IOException {
				if (isCancelled)
					return false;
				outputStream.write(buffer, offset, length);
		        progress += length;
				return true;
			}}

	    private final DownloadCache downloadCache;
        private final Map<URL, RemotePackage> packageMap = new HashMap<>();
	    private FileOp fileOp;
	    private ProgressIndicator progressIndicator;

	    private SettingsController mSettingsController;

	    public FileDownloader(@NonNull FileOp fop, @NonNull SettingsController settings) {
	        downloadCache =
	                DownloadCache.inUserHome(fop, DownloadCache.Strategy.FRESH_CACHE, settings);
	        fileOp = fop;
	        mSettingsController = settings;
	    }

	    /**
	     * Register package URL so the achive size is available to measure download progress
	     * @param remotePackage Remote package
	     * @return flage set true if URL is resolved successfully
	     */
	    public boolean registerPackage(RemotePackage remotePackage, ProgressIndicator progressIndicator) {
	        URL url = InstallerUtil.resolveCompleteArchiveUrl(remotePackage, progressIndicator);
	        if (url == null) {
	        	progressIndicator.logWarning(String.format("No compatible archive found for package %s", remotePackage.getDisplayName()));
	            return false;
	        }
	        this.progressIndicator = progressIndicator;
	        packageMap.put(url, remotePackage);
	    	return true;
	    }

	    /**
	     * Clears all package registrations resets small file count
	     */
	    public void reset() {
	    	packageMap.clear();
	    }
	    
	    @Override
	    @Nullable
	    public InputStream downloadAndStream(@NonNull URL url, @NonNull ProgressIndicator indicator)
	            throws IOException {
	    	InputStream inputStream = downloadCache.openCachedUrl(getUrl(url), indicator);
	    	return inputStream;
	    }

	    @Nullable
	    @Override
	    public Path downloadFully(@NonNull URL url, @NonNull ProgressIndicator indicator)
	            throws IOException {
	        File target = File.createTempFile("FileDownloader", null);
	        downloadFully(url, target, null, indicator);
	        return Paths.get(target.toURI());
	    }

	    /**
	     * Download file located at given URL to target location.
	     * @param url The file online location
	     * @param target The location to save the file
	     * @param checksum Checksum
	     * @param notUsed Progress indicator not used and registered one used instead
	     */
	    @Override
	    public void downloadFully(@NonNull URL url, @NonNull File target, @Nullable String checksum,
	            @NonNull ProgressIndicator notUsed) throws IOException {
	    	// Set progress to valid value on unused indicator to stop it reporting an error
	    	notUsed.setFraction(1.0);
	        if (fileOp.exists(target) && checksum != null) {
	            try (InputStream in = new BufferedInputStream(fileOp.newFileInputStream(target))) {
	                if (checksum.equals(Downloader.hash(in, fileOp.length(target), progressIndicator))) {
	                    return;
	                }
	            }
	        }
	        fileOp.mkdirs(target.getParentFile());
	        OutputStream outputStream = fileOp.newFileOutputStream(target);
	        Pair<InputStream, Integer> downloadedResult = downloadCache
	                .openDirectUrl(getUrl(url), progressIndicator);
	        if (downloadedResult.getSecond() == 200) {
	        	RemotePackage remotePackage = packageMap.get(url);
        		boolean success = false;
	        	if (remotePackage != null) {
	        		long total = remotePackage.getArchive().getComplete().getSize();
	        		final ByteStreamer byteStreamer = new ByteStreamer((double)total, outputStream);
	        		// Flag indicator is not indeterminate. This also sets max progress value
	        		progressIndicator.setIndeterminate(false);
	        		Timer timer = new Timer();
		            TimerTask task = new TimerTask(){

						@Override
						public void run() {
							progressIndicator.setFraction(byteStreamer.getProgress());
						}};
				    timer.scheduleAtFixedRate(task , 0, 2000);
	        		try {
	        			ByteStreams.readBytes(downloadedResult.getFirst(), byteStreamer);
	        			success = true;
	        	    } 
	        		finally {
	        	    	timer.cancel();
	        		    Closeables.close(outputStream, !success);
	        		}	
	        	} else {
	        		try {
	        			ByteStreams.copy(downloadedResult.getFirst(), outputStream);
	        			success = true;
	        		}
	        		finally {
	        		    Closeables.close(outputStream, !success);
	        		}	
	        	}
	        }
	    }

	    private String getUrl(@NonNull URL url) {
	        String urlStr = url.toString();
	        if (mSettingsController.getForceHttp()) {
	            urlStr = urlStr.replaceAll("^https://", "http://");
	        }
	        return urlStr;
	    }

}
