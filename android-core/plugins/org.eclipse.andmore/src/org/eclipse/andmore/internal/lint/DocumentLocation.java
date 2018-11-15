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
package org.eclipse.andmore.internal.lint;

import java.io.File;

import org.eclipse.andmore.AndmoreAndroidPlugin;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.wst.sse.core.internal.provisional.IndexedRegion;
import org.eclipse.wst.sse.core.internal.provisional.text.IStructuredDocument;

import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.DefaultPosition;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Position;

/**
 * Adapts Eclipse document and region to Lint client API Location
 */
public class DocumentLocation extends Location {

	private IndexedRegion region;
	
	/**
	 * Construct DocumentLocation object
	 * @param file Document location on file system
	 * @param document Document
	 * @param region Region
	 */
	public DocumentLocation(File file, IStructuredDocument document, IndexedRegion region) {
        super(file, getStart(document, region), getEnd(region));
        this.region = region;
 	}

    public IndexedRegion getRegion() {
		return region;
	}

	public Handle getHandle()
    {
        return new Handle(){

            private Object clientData;

            @Override
            public Location resolve()
            {
                return DocumentLocation.this;
            }

            @Override
            public void setClientData(@Nullable Object clientData) {
                this.clientData = clientData;
            }

            @Override
            @Nullable
            public Object getClientData() {
                return clientData;
            }
        };
    }

    private static Position getStart(IStructuredDocument document, IndexedRegion region) {
        int line = -1;
        int column = -1;
        int offset = region.getStartOffset();

        if (region instanceof org.w3c.dom.Text && document != null) {
            // For text nodes, skip whitespace prefix, if any
            for (int i = offset;
                    i < region.getEndOffset() && i < document.getLength(); i++) {
                try {
                    char c = document.getChar(i);
                    if (!Character.isWhitespace(c)) {
                        offset = i;
                        break;
                    }
                } catch (BadLocationException e) {
                    break;
                }
            }
       }
       if (document != null && offset < document.getLength()) {
            line = document.getLineOfOffset(offset);
            column = -1;
            try {
                int lineOffset = document.getLineOffset(line);
                column = offset - lineOffset;
            } catch (BadLocationException e) {
                AndmoreAndroidPlugin.log(e, null);
            }
        }
        return new DefaultPosition(line, column, offset);
   }

   private static Position getEnd(IndexedRegion region) {
       return new DefaultPosition(-1, -1, region.getEndOffset());
   }

}
