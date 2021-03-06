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
package io.xlate.edi.internal.stream.tokenization;

import java.io.InputStream;
import java.nio.CharBuffer;
import java.util.Iterator;
import java.util.List;

import io.xlate.edi.internal.stream.CharArraySequence;
import io.xlate.edi.internal.stream.LocationView;
import io.xlate.edi.internal.stream.StaEDIStreamLocation;
import io.xlate.edi.internal.stream.validation.Validator;
import io.xlate.edi.schema.EDIType;
import io.xlate.edi.schema.Schema;
import io.xlate.edi.stream.EDIStreamEvent;
import io.xlate.edi.stream.EDIStreamValidationError;
import io.xlate.edi.stream.Location;

public class ProxyEventHandler implements EventHandler {

    private final StaEDIStreamLocation location;

    private Schema controlSchema;
    private Validator controlValidator;

    private Validator transactionValidator;

    private boolean transactionSchemaAllowed = false;
    private boolean transaction = false;

    private InputStream binary;
    private CharArraySequence segmentHolder = new CharArraySequence();
    private CharArraySequence elementHolder = new CharArraySequence();

    private EDIStreamEvent[] events = new EDIStreamEvent[99];
    private EDIStreamValidationError[] errorTypes = new EDIStreamValidationError[99];
    private CharBuffer[] eventData = new CharBuffer[99];
    private String[] referenceCodes = new String[99];
    private Location[] locations = new Location[99];
    private int eventCount = 0;
    private int eventIndex = 0;
    private Dialect dialect;

    public ProxyEventHandler(StaEDIStreamLocation location, Schema controlSchema) {
        this.location = location;
        setControlSchema(controlSchema);
    }

    public void setControlSchema(Schema controlSchema) {
        if (controlValidator != null) {
            throw new IllegalStateException("control validator already created");
        }

        this.controlSchema = controlSchema;
        controlValidator = controlSchema != null ? new Validator(controlSchema, null) : null;
    }

    public boolean isTransactionSchemaAllowed() {
        return transactionSchemaAllowed;
    }

    public void setTransactionSchema(Schema transactionSchema) {
        transactionValidator = transactionSchema != null ? new Validator(transactionSchema, controlSchema) : null;
    }

    public void resetEvents() {
        eventCount = 0;
        eventIndex = 0;
    }

    public EDIStreamEvent getEvent() {
        if (hasEvents()) {
            return events[eventIndex];
        }
        return null;
    }

    public CharBuffer getCharacters() {
        if (hasEvents()) {
            return eventData[eventIndex];
        }
        throw new IllegalStateException();
    }

    public boolean hasEvents() {
        return eventIndex < eventCount;
    }

    public boolean nextEvent() {
        if (eventCount < 1) {
            return false;
        }
        return ++eventIndex < eventCount;
    }

    public EDIStreamValidationError getErrorType() {
        return errorTypes[eventIndex];
    }

    public String getReferenceCode() {
        return referenceCodes[eventIndex];
    }

    public Location getLocation() {
        if (hasEvents() && locations[eventIndex] != null) {
            return locations[eventIndex];
        }
        return location;
    }

    public InputStream getBinary() {
        return binary;
    }

    public void setBinary(InputStream binary) {
        this.binary = binary;
    }

    @Override
    public void interchangeBegin(Dialect dialect) {
        this.dialect = dialect;
        enqueueEvent(EDIStreamEvent.START_INTERCHANGE, EDIStreamValidationError.NONE, "", null);
    }

    @Override
    public void interchangeEnd() {
        enqueueEvent(EDIStreamEvent.END_INTERCHANGE, EDIStreamValidationError.NONE, "", null);
    }

    @Override
    public void loopBegin(CharSequence id) {
        if (EDIType.Type.TRANSACTION.toString().equals(id)) {
            transaction = true;
            transactionSchemaAllowed = true;
            enqueueEvent(EDIStreamEvent.START_TRANSACTION, EDIStreamValidationError.NONE, id, null);
        } else if (EDIType.Type.GROUP.toString().equals(id)) {
            enqueueEvent(EDIStreamEvent.START_GROUP, EDIStreamValidationError.NONE, id, null);
        } else {
            enqueueEvent(EDIStreamEvent.START_LOOP, EDIStreamValidationError.NONE, id, null);
        }
    }

    @Override
    public void loopEnd(CharSequence id) {
        if (EDIType.Type.TRANSACTION.toString().equals(id)) {
            transaction = false;
            enqueueEvent(EDIStreamEvent.END_TRANSACTION, EDIStreamValidationError.NONE, id, null);
        } else if (EDIType.Type.GROUP.toString().equals(id)) {
            enqueueEvent(EDIStreamEvent.END_GROUP, EDIStreamValidationError.NONE, id, null);
        } else {
            enqueueEvent(EDIStreamEvent.END_LOOP, EDIStreamValidationError.NONE, id, null);
        }
    }

    @Override
    public void segmentBegin(char[] text, int start, int length) {
        segmentHolder.set(text, start, length);

        Validator validator = validator();

        if (validator != null) {
            validator.validateSegment(this, segmentHolder);
        }

        if (exitTransaction(segmentHolder)) {
            transaction = false;
            validator().validateSegment(this, segmentHolder);
        }

        enqueueEvent(EDIStreamEvent.START_SEGMENT, EDIStreamValidationError.NONE, segmentHolder, null, null);
    }

    boolean exitTransaction(CharSequence tag) {
        return transaction && !transactionSchemaAllowed && controlSchema != null
                && controlSchema.containsSegment(tag.toString());
    }

    @Override
    public void segmentEnd() {
        if (validator() != null) {
            validator().validateSyntax(this, this, location, false);
        }

        enqueueEvent(EDIStreamEvent.END_SEGMENT, EDIStreamValidationError.NONE, segmentHolder, null, null);
        transactionSchemaAllowed = false;
    }

    @Override
    public void compositeBegin(boolean isNil) {
        String code = null;

        if (validator() != null && !isNil) {
            boolean invalid = !validator().validCompositeOccurrences(location);

            if (invalid) {
                List<EDIStreamValidationError> errors = validator().getElementErrors();

                for (EDIStreamValidationError error : errors) {
                    enqueueEvent(error.getCategory(), error, "", null);
                }
            } else {
                code = validator().getCompositeReferenceCode();
            }
        }

        enqueueEvent(EDIStreamEvent.START_COMPOSITE, EDIStreamValidationError.NONE, "", code);
    }

    @Override
    public void compositeEnd(boolean isNil) {
        if (validator() != null && !isNil) {
            validator().validateSyntax(this, this, location, true);
        }
        enqueueEvent(EDIStreamEvent.END_COMPOSITE, EDIStreamValidationError.NONE, "", null);
    }

    @Override
    public void elementData(char[] text, int start, int length) {
        boolean derivedComposite = false;
        Location savedLocation = null;
        String code = null;

        elementHolder.set(text, start, length);

        if (validator() != null) {
            final boolean composite = location.getComponentPosition() > -1;
            boolean valid = validator().validateElement(dialect, location, elementHolder);
            derivedComposite = !composite && validator().isComposite();

            code = validator().getElementReferenceNumber();

            if (!valid) {
                /*
                 * Process element-level errors before possibly starting a
                 * composite or reporting other data-related errors.
                 */
                List<EDIStreamValidationError> errors = validator().getElementErrors();
                Iterator<EDIStreamValidationError> cursor = errors.iterator();

                if (derivedComposite) {
                    savedLocation = new LocationView(location);
                }

                while (cursor.hasNext()) {
                    EDIStreamValidationError error = cursor.next();

                    switch (error) {
                    case TOO_MANY_DATA_ELEMENTS:
                    case TOO_MANY_REPETITIONS:
                        enqueueEvent(error.getCategory(),
                                     error,
                                     elementHolder,
                                     code,
                                     savedLocation);
                        cursor.remove();
                        //$FALL-THROUGH$
                    default:
                        continue;
                    }
                }
            }

            if (derivedComposite && text != null/* Not an empty composite */) {
                this.compositeBegin(length == 0);
                location.incrementComponentPosition();
                savedLocation = new LocationView(location);
            }

            if (!valid) {
                List<EDIStreamValidationError> errors = validator().getElementErrors();
                savedLocation = new LocationView(location);

                for (EDIStreamValidationError error : errors) {
                    enqueueEvent(error.getCategory(),
                                 error,
                                 elementHolder,
                                 code,
                                 savedLocation);
                }
            }
        }

        if (text != null && (!derivedComposite || length > 0) /* Not an inferred element */) {
            enqueueEvent(EDIStreamEvent.ELEMENT_DATA,
                         EDIStreamValidationError.NONE,
                         elementHolder,
                         code,
                         savedLocation);
        }

        if (derivedComposite && text != null /* Not an empty composite */) {
            this.compositeEnd(length == 0);
            location.clearComponentPosition();
        }
    }

    public boolean isBinaryElementLength() {
        return validator() != null && validator().isBinaryElementLength();
    }

    @Override
    public void binaryData(InputStream binaryStream) {
        enqueueEvent(EDIStreamEvent.ELEMENT_DATA_BINARY, EDIStreamValidationError.NONE, "", null);
        setBinary(binaryStream);
    }

    @Override
    public void segmentError(CharSequence token, EDIStreamValidationError error) {
        enqueueEvent(EDIStreamEvent.SEGMENT_ERROR, error, token, null);
    }

    @Override
    public void elementError(final EDIStreamEvent event,
                             final EDIStreamValidationError error,
                             final int element,
                             final int component,
                             final int repetition) {

        StaEDIStreamLocation copy = location.copy();
        copy.setElementPosition(element);
        copy.setElementOccurrence(repetition);
        copy.setComponentPosition(component);

        enqueueEvent(event, error, null, null, copy);
    }

    private Validator validator() {
        // Do not use the transactionValidator in the period where it may be set/mutated by the user
        return transaction && !transactionSchemaAllowed ? transactionValidator : controlValidator;
    }

    private void enqueueEvent(EDIStreamEvent event,
                              EDIStreamValidationError error,
                              CharArraySequence holder,
                              String code,
                              Location savedLocation) {

        if (event == EDIStreamEvent.ELEMENT_OCCURRENCE_ERROR && eventCount > 0
                && events[eventCount] == EDIStreamEvent.START_COMPOSITE) {
            switch (error) {
            case TOO_MANY_DATA_ELEMENTS:
            case TOO_MANY_REPETITIONS:
                /*
                 * We have an element-level error with a pending start
                 * composite event. Move the element error before the start
                 * of the composite.
                 */
                events[eventCount] = events[eventCount - 1];
                eventData[eventCount] = eventData[eventCount - 1];
                errorTypes[eventCount] = errorTypes[eventCount - 1];
                referenceCodes[eventCount] = referenceCodes[eventCount - 1];
                locations[eventCount] = locations[eventCount - 1];

                events[eventCount - 1] = event;
                errorTypes[eventCount - 1] = error;
                eventData[eventCount - 1] = put(eventData[eventCount], holder);
                referenceCodes[eventCount - 1] = code;

                if (savedLocation != null) {
                    locations[eventCount - 1] = savedLocation;
                } else {
                    locations[eventCount - 1] = null;
                }

                eventCount++;
                return;
            default:
                break;
            }
        }

        events[eventCount] = event;
        errorTypes[eventCount] = error;
        eventData[eventCount] = put(eventData[eventCount], holder);
        referenceCodes[eventCount] = code;

        if (savedLocation != null) {
            locations[eventCount] = savedLocation;
        } else {
            locations[eventCount] = null;
        }

        eventCount++;
    }

    private void enqueueEvent(EDIStreamEvent event, EDIStreamValidationError error, CharSequence text, String code) {
        events[eventCount] = event;
        errorTypes[eventCount] = error;
        eventData[eventCount] = put(eventData[eventCount], text);
        referenceCodes[eventCount] = code;
        eventCount++;
    }

    private static CharBuffer put(CharBuffer buffer, CharArraySequence holder) {
        final int length = holder != null ? holder.length() : 50;

        if (buffer == null || buffer.capacity() < length) {
            buffer = CharBuffer.allocate(length);
        }

        buffer.clear();

        if (holder != null && length > 0) {
            holder.putToBuffer(buffer);
        }

        buffer.flip();

        return buffer;
    }

    private static CharBuffer put(CharBuffer buffer, CharSequence text) {
        int length = text.length();

        if (buffer == null || buffer.capacity() < length) {
            buffer = CharBuffer.allocate(length);
        }

        buffer.clear();
        for (int i = 0; i < length; i++) {
            buffer.put(text.charAt(i));
        }
        buffer.flip();

        return buffer;
    }
}
