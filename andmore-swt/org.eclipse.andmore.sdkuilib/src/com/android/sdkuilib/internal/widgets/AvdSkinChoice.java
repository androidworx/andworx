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

package com.android.sdkuilib.internal.widgets;

import java.io.File;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.sdkuilib.internal.widgets.AvdCreationPresenter.SkinType;

/*
 * Choice of AVD skin: dynamic, no skin, or one from the target.
 * The 2 "internals" skins (dynamic and no skin) have no path.
 * The target-based skins have a path.
 */
class AvdSkinChoice  implements Comparable<AvdSkinChoice> {

    private final SkinType mType;
    private final String mLabel;
    private final File mPath;

    AvdSkinChoice(@NonNull SkinType type, @NonNull String label) {
        this(type, label, null);
    }

    AvdSkinChoice(@NonNull SkinType type, @NonNull String label, @NonNull File path) {
        mType = type;
        mLabel = label;
        mPath = path;
    }

    @NonNull
    public SkinType getType() {
        return mType;
    }

    @NonNull
    public String getLabel() {
        return mLabel;
    }

    @Nullable
    public File getPath() {
        return mPath;
    }

    public boolean hasPath() {
        return mType == SkinType.FROM_TARGET;
    }

    @Override
    public int compareTo(AvdSkinChoice o) {
        int t = mType.compareTo(o.mType);
        if (t == 0) {
            t = mLabel.compareTo(o.mLabel);
        }
        if (t == 0 && mPath != null && o.mPath != null) {
            t = mPath.compareTo(o.mPath);
        }
        return t;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((mType == null) ? 0 : mType.hashCode());
        result = prime * result + ((mLabel == null) ? 0 : mLabel.hashCode());
        result = prime * result + ((mPath == null) ? 0 : mPath.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof AvdSkinChoice)) {
            return false;
        }
        AvdSkinChoice other = (AvdSkinChoice) obj;
        if (mType != other.mType) {
            return false;
        }
        if (mLabel == null) {
            if (other.mLabel != null) {
                return false;
            }
        } else if (!mLabel.equals(other.mLabel)) {
            return false;
        }
        if (mPath == null) {
            if (other.mPath != null) {
                return false;
            }
        } else if (!mPath.equals(other.mPath)) {
            return false;
        }
        return true;
    }

}
