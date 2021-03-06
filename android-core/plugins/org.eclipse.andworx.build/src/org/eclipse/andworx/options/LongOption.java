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

package org.eclipse.andworx.options;

import com.android.annotations.NonNull;

public enum LongOption implements Option<Long> {
    DEPRECATED_NDK_COMPILE_LEASE("android.deprecatedNdkCompileLease")
    ;

    @NonNull private final String propertyName;

    LongOption(@NonNull String propertyName) {
        this.propertyName = propertyName;
    }

    @NonNull
    public final String getPropertyName() {
        return propertyName;
    }

    public final Long getDefaultValue() {
        return null;
    }

    public final Long parse(@NonNull Object value) {
        if (value instanceof CharSequence) {
            try {
                return Long.parseLong(value.toString());
            } catch (NumberFormatException ignored) {
                // Throws below.
            }
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        throw new IllegalArgumentException(
                "Cannot parse project property "
                        + this.getPropertyName()
                        + "='"
                        + value
                        + "' of type '"
                        + value.getClass()
                        + "' as long.");
    }

}
