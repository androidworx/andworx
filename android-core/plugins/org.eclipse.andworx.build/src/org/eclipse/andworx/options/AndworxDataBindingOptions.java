/*
 * Copyright (C) 2015 The Android Open Source Project
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
package org.eclipse.andworx.options;


import com.android.builder.model.DataBindingOptions;

/**
* Databinding options.
*/
public class AndworxDataBindingOptions implements DataBindingOptions {
	
   private String version;
   private boolean enabled = false;
   private boolean addDefaultAdapters = true;
   private boolean enabledForTests = false;

   public void setVersion(String version) {
       this.version = version;
   }

   public void setEnabled(boolean enabled) {
       this.enabled = enabled;
   }

   public void setAddDefaultAdapters(boolean addDefaultAdapters) {
       this.addDefaultAdapters = addDefaultAdapters;
   }

   public void setEnabledForTests(boolean enabledForTests) {
       this.enabledForTests = enabledForTests;
   }

   /**
    * The version of data binding to use.
    */
   @Override
   public String getVersion() {
       return version;
   }

   /**
    * Whether to enable data binding.
    */
   @Override
   public boolean isEnabled() {
       return enabled;
   }

   /**
    * Whether to add the default data binding adapters.
    */
   @Override
   public boolean getAddDefaultAdapters() {
       return addDefaultAdapters;
   }

   /**
    * Whether to run data binding code generation for test projects
    */
   @Override
   public boolean isEnabledForTests() {
       return enabledForTests;
   }

}