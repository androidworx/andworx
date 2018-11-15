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
package org.eclipse.andworx.gson;

import java.io.IOException;

import org.eclipse.andworx.build.OutputType;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

/** 
 * Gson adpapter for OutputType class 
 */
public class OutputTypeTypeAdapter extends TypeAdapter<OutputType> {

	@Override
	public void write(JsonWriter out, OutputType value) throws IOException {
        out.beginObject();
        out.name("type").value(value.name());
        out.endObject();
	}

	@Override
	public OutputType read(JsonReader reader) throws IOException {
        reader.beginObject();
        if (!reader.nextName().endsWith("type")) {
            throw new IOException("Invalid format");
        }
        String nextString = reader.nextString();
        OutputType outputType = OutputType.valueOf(OutputType.class, nextString);
        reader.endObject();
        return outputType;
	}
}
