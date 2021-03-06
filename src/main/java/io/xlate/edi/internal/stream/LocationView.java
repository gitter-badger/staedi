/*******************************************************************************
 * Copyright 2017 xlate.io LLC, http://www.xlate.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 ******************************************************************************/
package io.xlate.edi.internal.stream;

import io.xlate.edi.stream.Location;

public class LocationView implements Location {

    protected int lineNumber;
    protected int columnNumber;
    protected int characterOffset;
    protected int segmentPosition;
    protected int elementPosition;
    protected int componentPosition;
    protected int elementOccurrence;

    public LocationView(Location source) {
        lineNumber = source.getLineNumber();
        columnNumber = source.getColumnNumber();
        characterOffset = source.getCharacterOffset();
        segmentPosition = source.getSegmentPosition();
        elementPosition = source.getElementPosition();
        componentPosition = source.getComponentPosition();
        elementOccurrence = source.getElementOccurrence();
    }

    protected LocationView() {
        lineNumber = -1;
        columnNumber = -1;
        characterOffset = -1;
        segmentPosition = -1;
        elementPosition = -1;
        componentPosition = -1;
        elementOccurrence = -1;
    }

    @Override
    public int getLineNumber() {
        return lineNumber;
    }

    @Override
    public int getColumnNumber() {
        return columnNumber;
    }

    @Override
    public int getCharacterOffset() {
        return characterOffset;
    }

    @Override
    public int getSegmentPosition() {
        return segmentPosition;
    }

    @Override
    public int getElementPosition() {
        return elementPosition;
    }

    @Override
    public int getComponentPosition() {
        return componentPosition;
    }

    @Override
    public int getElementOccurrence() {
        return elementOccurrence;
    }
}
