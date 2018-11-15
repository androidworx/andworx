/*
 * Copyright 2010 the original author or authors.
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
package org.eclipse.andworx.model;

/**
 * A {@code SourceSet} represents a logical group of Java source and resources.
 * <p>
 * See the example below how {@link SourceSet} 'main' is accessed and how the {@link SourceDirectorySet} 'java'
 * is configured to exclude some package from compilation.
 *
 * <pre class='autoTested'>
 * apply plugin: 'java'
 *
 * sourceSets {
 *   main {
 *     java {
 *       exclude 'some/unwanted/package/**'
 *     }
 *   }
 * }
 * </pre>
 */
public interface SourceSet {
    /**
     * The name of the main source set.
     */
    String MAIN_SOURCE_SET_NAME = "main";

    /**
     * The name of the test source set.
     */
    String TEST_SOURCE_SET_NAME = "test";

    /**
     * Returns the name of this source set.
     *
     * @return The name. Never returns null.
     */
    String getName();

}
