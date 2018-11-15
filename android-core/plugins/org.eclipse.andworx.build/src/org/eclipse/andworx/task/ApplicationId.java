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
package org.eclipse.andworx.task;

import com.android.annotations.NonNull;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

/**
 * Persisted application id
 */
public class ApplicationId {
	static final String PERSISTED_FILE_NAME = "application-id.json";

    @NonNull private final String applicationId;

    /**
     * Construct ApplicationId object 
     * @param applicationId Application id
     */
    public ApplicationId(@NonNull String applicationId) {
        this.applicationId = applicationId;
    }

    /**
     * Returns applicaition id
     * @return applicaition id
     */
    @NonNull
    public String getApplicationId() {
        return applicationId;
    }

    /**
     * Saves application id to given locatin
     * @param outputDirectory Save location
     * @throws IOException
     */
    public void save(@NonNull File outputDirectory) throws IOException {
        File outputFile = new File(outputDirectory, PERSISTED_FILE_NAME);
        GsonBuilder gsonBuilder = new GsonBuilder();
        Gson gson = gsonBuilder.create();
        Files.asCharSink(outputFile, Charsets.UTF_8).write(gson.toJson(this));
     }

    /**
     * Restores application id from given file
     * @param input File containing the applicaiton id
     * @return ApplicationId object
     * @throws IOException
     */
    @NonNull
    public static ApplicationId load(@NonNull File input) throws IOException {
        if (!input.getName().equals(PERSISTED_FILE_NAME)) {
            throw new FileNotFoundException("No application declaration present.");
        }
        GsonBuilder gsonBuilder = new GsonBuilder();
        Gson gson = gsonBuilder.create();
        try (FileReader fileReader = new FileReader(input)) {
            return gson.fromJson(fileReader, ApplicationId.class);
        }
    }

    @NonNull
    public static File getOutputFile(@NonNull File directory) {
        return new File(directory, PERSISTED_FILE_NAME);
    }

}
