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

import org.eclipse.andworx.build.FilterDataImpl;

import com.android.build.FilterData;
import com.android.build.VariantOutput;
import com.android.ide.common.build.ApkInfo;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

/** 
 * Gson type adapter for ApkInfo class 
 */
public class ApkInfoAdapter extends TypeAdapter<ApkInfo> {
	
	@Override
	public void write(JsonWriter out, ApkInfo value) throws IOException {
        if (value == null) {
            out.nullValue();
            return;
        }
        out.beginObject();
        out.name("type").value(value.getType().toString());
        out.name("splits").beginArray();
        for (FilterData filter: value.getFilters()) {
            out.beginObject();
            out.name("filterType").value(filter.getFilterType());
            out.name("value").value(filter.getIdentifier());
            out.endObject();
        }
        out.endArray();
        out.name("versionCode").value(value.getVersionCode());
        if (value.getVersionName() != null) {
            out.name("versionName").value(value.getVersionName());
        }
        out.name("enabled").value(value.isEnabled());
        if (value.getFilterName() != null) {
            out.name("filterName").value(value.getFilterName());
        }
        if (value.getOutputFileName() != null) {
            out.name("outputFile").value(value.getOutputFileName());
        }
        out.name("fullName").value(value.getFullName());
        out.name("baseName").value(value.getBaseName());
        out.endObject();
	}

	@Override
	public ApkInfo read(JsonReader reader) throws IOException {
        reader.beginObject();
        String outputType = null;
        Builder<FilterData> filters = ImmutableList.builder();
        int versionCode = 0;
        String versionName = null;
        boolean enabled = true;
        String outputFile = null;
        String fullName = null;
        String baseName = null;
        String filterName = null;
        while (reader.hasNext()) {
          String name = reader.nextName();
          if (name.equals("type")) {
        	  outputType = reader.nextString();
          } else if (name.equals("splits")) {
        		 readFilters(reader, filters);
          } else if (name.equals("versionCode")) {
        	  versionCode = reader.nextInt();
          } else if (name.equals("versionName")) {
        	  versionName = reader.nextString();
          } else if (name.equals("enabled")) {
        	  enabled = reader.nextBoolean();
          } else if (name.equals("outputFile")) {
        	  outputFile = reader.nextString();
          } else if (name.equals("filterName")) {
        	  filterName = reader.nextString();
          } else if (name.equals("baseName")) {
        	  versionName = baseName = reader.nextString();
          } else if (name.equals("fullName")) {
        	  fullName = reader.nextString();
          } else {
            reader.skipValue();
          }
        }
        reader.endObject();

        ImmutableList<FilterData> filterData = filters.build();
        com.android.build.VariantOutput.OutputType apkType = VariantOutput.OutputType.valueOf(outputType);

        return ApkInfo.of(
                apkType,
                filterData,
                versionCode,
                versionName,
                filterName,
                outputFile,
                fullName,
                baseName,
                enabled);
	}

    private void readFilters(JsonReader reader, ImmutableList.Builder<FilterData> filters) throws IOException {

        reader.beginArray();
        while (reader.hasNext()) {
            reader.beginObject();
            VariantOutput.FilterType filterType = null;
            String value = null;
            while (reader.hasNext()) {
                String name = reader.nextName();
                if (name.equals("filterType")) {
                	filterType = VariantOutput.FilterType.valueOf(reader.nextString());
                } else if (name.equals("value")) {
                	value = reader.nextString();
                } else {
                    reader.skipValue();
                }
            }
            if (filterType != null && value != null) {
                filters.add(new FilterDataImpl(filterType, value));
            }
            reader.endObject();
        }
        reader.endArray();
    }
}
