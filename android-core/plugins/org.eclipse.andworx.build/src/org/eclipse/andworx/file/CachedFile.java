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
package org.eclipse.andworx.file;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import com.android.annotations.NonNull;
import com.android.builder.utils.FileCache;
import com.android.utils.PathUtils;
import com.google.common.base.Preconditions;
import com.google.common.hash.Hashing;
import com.google.common.hash.HashingInputStream;

/**
 * Reference to file placed in Android SDK builder utils file cache
 */
public class CachedFile {
	
	/** Utility interface for opening files using platform */
	private FileResource fileResource;
	/** Command to be provided by the client when using {@link FileCache} */
	private final FileCache.Command command;
	/** Thread-safe path container */
    private final AtomicReference<Path> pathReference;

    /**
     * Construct CachedFile object
     * @param command Command to be provided by the client when using {@link FileCache}
     * @param fileResource Utility for opening files using platform
     */
	public CachedFile(FileCache.Command command, FileResource fileResource) {
		this.command = command;
		this.fileResource = fileResource;
		pathReference = new AtomicReference<Path>(null);
	}

	/**
	 * Returns path to file in cache
	 * @return Path object
	 */
	public Path getPath() {
		return pathReference.get();
	}

	/**  
	 * Returns utility for opening files using platform
	 * @return FileResource object
	 */
	public FileResource getFileResource() {
		return fileResource;
	}

	/**
	 * Returns flag set true if file is cached
	 * @return
	 */
	public boolean isInitialized() {
        return pathReference.get() != null && Files.isRegularFile(pathReference.get());
	}

	/**
	 * Place file in given cache and return path to file in cache
	 * @param fileCache Android SDK builder utils file cache 
	 * @param pluginVersion Input parameter required when using {@link FileCache}
	 * @return Path object
	 * @throws ExecutionException
	 * @throws IOException
	 */
	public Path initialize(FileCache fileCache, String pluginVersion) throws ExecutionException, IOException {
        URL url = fileResource.asUrl();
        Preconditions.checkNotNull(url);
        String fileHash;
        try (HashingInputStream stream =
                new HashingInputStream(Hashing.sha256(), url.openStream())) {
            fileHash = stream.hash().toString();
        }
        FileCache.Inputs inputs =
                new FileCache.Inputs.Builder(command)
                        .putString("pluginVersion", pluginVersion)
                        .putString("jarUrl", url.toString())
                        .putString("fileHash", fileHash)
                        .build();

        File cachedFile =
        		fileCache.createFileInCacheIfAbsent(
                                inputs, file -> copy(url, file.toPath()))
                        .getCachedFile();
        Preconditions.checkNotNull(cachedFile);
        return cachedFile.toPath();
	}

	/**
	 * Set path to file in cache if not set or not valid
	 * @param path Path returned by call to {@link #initialize(FileCache, String)}
	 * @throws IOException
	 */
	public void update(Path path) throws IOException {
        synchronized (pathReference) {
            if (isInitialized()) {
                return;
            }

            if (path == null) { // Not set. Normal file cache not available
            	path = PathUtils.createTmpToRemoveOnShutdown(fileResource.getFileName());
                copy(fileResource.asUrl(), path);
            }
            pathReference.set(path);
        }
	}

	/**
	 * Copy file
	 * @param inputUrl File URL
	 * @param targetPath Destination path
	 * @throws IOException
	 */
    private void copy(@NonNull URL inputUrl, @NonNull Path targetPath)
            throws IOException {
        try (InputStream inputStream = inputUrl.openConnection().getInputStream()) {
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }


}
