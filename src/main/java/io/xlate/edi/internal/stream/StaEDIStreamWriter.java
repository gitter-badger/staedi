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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.xlate.edi.internal.stream.tokenization.CharacterClass;
import io.xlate.edi.internal.stream.tokenization.CharacterSet;
import io.xlate.edi.internal.stream.tokenization.Dialect;
import io.xlate.edi.internal.stream.tokenization.DialectFactory;
import io.xlate.edi.internal.stream.tokenization.EDIException;
import io.xlate.edi.internal.stream.tokenization.EDIFACTDialect;
import io.xlate.edi.internal.stream.tokenization.ElementDataHandler;
import io.xlate.edi.internal.stream.tokenization.State;
import io.xlate.edi.internal.stream.tokenization.ValidationEventHandler;
import io.xlate.edi.internal.stream.validation.Validator;
import io.xlate.edi.schema.Schema;
import io.xlate.edi.stream.EDIOutputFactory;
import io.xlate.edi.stream.EDIStreamConstants.Delimiters;
import io.xlate.edi.stream.EDIStreamEvent;
import io.xlate.edi.stream.EDIStreamException;
import io.xlate.edi.stream.EDIStreamValidationError;
import io.xlate.edi.stream.EDIStreamWriter;

public class StaEDIStreamWriter implements EDIStreamWriter, ElementDataHandler, ValidationEventHandler {

    private static final int LEVEL_INITIAL = 0;
    private static final int LEVEL_INTERCHANGE = 1;
    private static final int LEVEL_SEGMENT = 2;
    private static final int LEVEL_ELEMENT = 3;
    private static final int LEVEL_COMPOSITE = 4;
    private static final int LEVEL_COMPONENT = 5;

    private int level;

    private State state = State.INITIAL;
    private CharacterSet characters = new CharacterSet();

    private final OutputStream stream;
    @SuppressWarnings("unused")
    private final String encoding;
    private final Map<String, Object> properties;
    private Dialect dialect;

    private final StaEDIStreamLocation location;
    private Schema controlSchema;
    private Validator controlValidator;
    private Validator transactionValidator;
    private CharArraySequence dataHolder = new CharArraySequence();
    private List<EDIStreamException> errors = new ArrayList<>();

    private char segmentTerminator;
    private char dataElementSeparator;
    private char componentElementSeparator;
    private char repetitionSeparator;
    private char decimalMark;
    private char releaseIndicator;

    private final boolean prettyPrint;
    private final String lineSeparator;

    public StaEDIStreamWriter(OutputStream stream, String encoding, Map<String, Object> properties) {
        this.stream = stream;
        this.encoding = encoding;
        this.properties = new HashMap<>(properties);
        this.prettyPrint = property(EDIOutputFactory.PRETTY_PRINT);

        if (prettyPrint) {
            lineSeparator = System.getProperty("line.separator");
        } else {
            lineSeparator = null;
        }
        this.location = new StaEDIStreamLocation();
    }

    @SuppressWarnings("unchecked")
    private <T> T property(String key) {
        return (T) properties.get(key);
    }

    private void setupDelimiters() {
        segmentTerminator = getDelimiter(properties, Delimiters.SEGMENT, dialect::getSegmentTerminator);
        dataElementSeparator = getDelimiter(properties, Delimiters.DATA_ELEMENT, dialect::getDataElementSeparator);
        componentElementSeparator = getDelimiter(properties, Delimiters.COMPONENT_ELEMENT, dialect::getComponentElementSeparator);
        repetitionSeparator = getDelimiter(properties, Delimiters.REPETITION, dialect::getRepetitionSeparator);
        decimalMark = getDelimiter(properties, Delimiters.DECIMAL, dialect::getDecimalMark);
        releaseIndicator = getDelimiter(properties, Delimiters.RELEASE, dialect::getReleaseIndicator);
    }

    static char getDelimiter(Map<String, Object> properties, String key, Supplier<Character> defaultSupplier) {
        if (properties.containsKey(key)) {
            return (char) properties.get(key);
        }
        return defaultSupplier.get();
    }

    private static void ensureArgs(int arrayLength, int start, int end) {
        if (start < 0 || start > arrayLength || end > arrayLength) {
            throw new IndexOutOfBoundsException();
        }

        if (end < start) {
            throw new IllegalArgumentException();
        }
    }

    private void ensureState(State s) {
        if (this.state != s) {
            throw new IllegalStateException();
        }
    }

    private void ensureLevel(int l) {
        if (this.level != l) {
            throw new IllegalStateException();
        }
    }

    private void ensureLevelAtLeast(int l) {
        if (this.level < l) {
            throw new IllegalStateException();
        }
    }

    private void ensureLevelBetween(int min, int max) {
        if (this.level < min || this.level > max) {
            throw new IllegalStateException();
        }
    }

    @Override
    public Object getProperty(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Name must not be null");
        }
        return properties.get(name);
    }

    @Override
    public void close() throws EDIStreamException {
        flush();
        // Do not close the stream
    }

    @Override
    public void flush() throws EDIStreamException {
        try {
            stream.flush();
        } catch (IOException e) {
            throw new EDIStreamException(e);
        }
    }

    @Override
    public void setControlSchema(Schema controlSchema) {
        ensureLevel(LEVEL_INITIAL);
        this.controlSchema = controlSchema;
        controlValidator = controlSchema != null ? new Validator(controlSchema, null) : null;
    }

    @Override
    public void setTransactionSchema(Schema transactionSchema) {
        transactionValidator = transactionSchema != null ? new Validator(transactionSchema, controlSchema) : null;
    }

    private Validator validator() {
        // Prefer the transaction schema/validator when set
        return transactionValidator != null ? transactionValidator : controlValidator;
    }

    private void write(int output) throws EDIStreamException {
        CharacterClass clazz;

        clazz = characters.getClass(output);

        if (clazz == CharacterClass.INVALID) {
            StringBuilder message = new StringBuilder();
            message.append("Invalid character: 0x");
            message.append(Integer.toHexString(output));
            throw new EDIException(message.toString());
        }

        state = state.transition(clazz);

        switch (state) {
        case HEADER_TAG_I:
        case HEADER_TAG_S:
        case HEADER_TAG_U:
        case HEADER_TAG_N:
            //case HEADER_TAG_A:
        case INTERCHANGE_CANDIDATE:
        case HEADER_DATA:
        case HEADER_ELEMENT_END:
        case HEADER_COMPONENT_END:
            if (dialect.appendHeader(characters, (char) output)) {
                if (dialect.isConfirmed()) {
                    switch (state) {
                    case HEADER_DATA:
                        state = State.TAG_SEARCH;
                        break;
                    case HEADER_ELEMENT_END:
                        state = State.ELEMENT_END;
                        break;
                    case HEADER_COMPONENT_END:
                        state = State.COMPONENT_END;
                        break;
                    default:
                        throw new IllegalStateException("Confirmed at state " + state);
                    }
                }
            } else {
                throw new EDIException("Unexpected header character: '" + (char) output + "'");
            }
            break;
        case INVALID:
            throw new EDIException(String.format("Invalid state: %s; output 0x%04X", state, output));
        default:
            break;
        }

        try {
            stream.write(output);
        } catch (IOException e) {
            throw new EDIStreamException(e);
        }

        location.incrementOffset();
    }

    @Override
    public EDIStreamWriter startInterchange() throws EDIStreamException {
        ensureLevel(LEVEL_INITIAL);
        ensureState(State.INITIAL);
        level = LEVEL_INTERCHANGE;
        return this;
    }

    @Override
    public EDIStreamWriter endInterchange() throws EDIStreamException {

        ensureLevel(LEVEL_INTERCHANGE);
        level = LEVEL_INITIAL;
        return this;
    }

    @Override
    public EDIStreamWriter writeStartSegment(String name) throws EDIStreamException {
        ensureLevel(LEVEL_INTERCHANGE);
        validate(validator -> validator.validateSegment(this, name));

        if (state == State.INITIAL) {
            dialect = DialectFactory.getDialect(name);
            setupDelimiters();
            writeString(name);

            if (dialect instanceof EDIFACTDialect && "UNA".equals(name)) {
                write(this.componentElementSeparator);
                write(this.dataElementSeparator);
                write(this.decimalMark);
                write(this.releaseIndicator);
                write(this.repetitionSeparator);
            }
        } else {
            writeString(name);
        }

        level = LEVEL_SEGMENT;
        location.incrementSegmentPosition();

        return this;
    }

    private void writeString(String value) throws EDIStreamException {
        for (int i = 0, m = value.length(); i < m; i++) {
            write(value.charAt(i));
        }
    }

    @Override
    public EDIStreamWriter writeEndSegment() throws EDIStreamException {
        ensureLevelAtLeast(LEVEL_SEGMENT);
        validate(validator -> validator.validateSyntax(this, this, location, false));

        if (state == State.ELEMENT_DATA_BINARY) {
            state = State.ELEMENT_END_BINARY;
        }

        write(this.segmentTerminator);
        if (prettyPrint) {
            writeString(lineSeparator);
        }
        level = LEVEL_INTERCHANGE;
        location.clearSegmentLocations();

        return this;
    }

    @Override
    public EDIStreamWriter writeStartElement() throws EDIStreamException {
        ensureLevel(LEVEL_SEGMENT);
        write(this.dataElementSeparator);
        level = LEVEL_ELEMENT;
        updateLocation(state, location);
        return this;
    }

    @Override
    public EDIStreamWriter writeStartElementBinary() throws EDIStreamException {
        writeStartElement();
        state = State.ELEMENT_DATA_BINARY;
        return this;
    }

    @Override
    public EDIStreamWriter endElement() throws EDIStreamException {
        ensureLevelAtLeast(LEVEL_ELEMENT);
        level = LEVEL_SEGMENT;

        if (state == State.ELEMENT_DATA_BINARY) {
            state = State.ELEMENT_END_BINARY;
        }

        return this;
    }

    @Override
    public EDIStreamWriter startComponent() throws EDIStreamException {
        ensureLevelBetween(LEVEL_ELEMENT, LEVEL_COMPOSITE);

        if (state == State.ELEMENT_DATA_BINARY) {
            throw new IllegalStateException();
        }

        if (LEVEL_COMPOSITE == level) {
            write(this.componentElementSeparator);
        }

        level = LEVEL_COMPONENT;
        updateLocation(state, location);
        return this;
    }

    @Override
    public EDIStreamWriter endComponent() throws EDIStreamException {
        ensureLevel(LEVEL_COMPONENT);
        level = LEVEL_COMPOSITE;
        location.clearComponentPosition();
        return this;
    }

    @Override
    public EDIStreamWriter writeRepeatElement() throws EDIStreamException {
        ensureLevelAtLeast(LEVEL_SEGMENT);
        write(this.repetitionSeparator);
        level = LEVEL_ELEMENT;
        return this;
    }

    @Override
    public EDIStreamWriter writeElement(CharSequence text) throws EDIStreamException {
        writeStartElement();
        validate(validator -> validator.validateElement(dialect, location, text));
        writeElementData(text);
        endElement();
        return this;
    }

    @Override
    public EDIStreamWriter writeElement(char[] text, int start, int end) throws EDIStreamException {
        writeStartElement();

        validate(validator -> {
            dataHolder.set(text, start, start + end);
            validator.validateElement(dialect, location, dataHolder);
        });

        writeElementData(text, start, end);
        endElement();
        return this;
    }

    @Override
    public EDIStreamWriter writeEmptyElement() throws EDIStreamException {
        writeStartElement();

        validate(validator -> {
            dataHolder.set(null, 0, 0);
            validator.validateElement(dialect, location, dataHolder);
        });

        endElement();
        return this;
    }

    @Override
    public EDIStreamWriter writeComponent(CharSequence text) throws EDIStreamException {
        startComponent();

        validate(validator -> {
            validator.validateElement(dialect, location, text);
        });

        writeElementData(text);
        endComponent();
        return this;
    }

    @Override
    public EDIStreamWriter writeComponent(char[] text, int start, int end) throws EDIStreamException {
        startComponent();

        validate(validator -> {
            dataHolder.set(text, start, start + end);
            validator.validateElement(dialect, location, dataHolder);
        });

        writeElementData(text, start, end);
        endComponent();
        return this;
    }

    @Override
    public EDIStreamWriter writeEmptyComponent() throws EDIStreamException {
        startComponent();

        validate(validator -> {
            dataHolder.set(null, 0, 0);
            validator.validateElement(dialect, location, dataHolder);
        });

        endComponent();
        return this;
    }

    @Override
    public EDIStreamWriter writeElementData(CharSequence text) throws EDIStreamException {
        ensureLevelAtLeast(LEVEL_ELEMENT);
        for (int i = 0, m = text.length(); i < m; i++) {
            char curr = text.charAt(i);
            if (characters.isDelimiter(curr)) {
                throw new IllegalArgumentException("Value contains separator");
            }
            write(curr);
        }
        return this;
    }

    @Override
    public EDIStreamWriter writeElementData(char[] text, int start, int end) throws EDIStreamException {
        ensureLevelAtLeast(LEVEL_ELEMENT);
        ensureArgs(text.length, start, end);

        for (int i = 0, m = text.length; i < m; i++) {
            char curr = text[i];
            if (characters.isDelimiter(curr)) {
                throw new IllegalArgumentException("Value contains separator");
            }
            write(curr);
        }

        return this;
    }

    @Override
    public EDIStreamWriter writeBinaryData(InputStream binaryStream) throws EDIStreamException {
        ensureLevel(LEVEL_ELEMENT);
        ensureState(State.ELEMENT_DATA_BINARY);
        int input;

        try {
            while ((input = binaryStream.read()) != -1) {
                stream.write(input);
            }
        } catch (IOException e) {
            throw new EDIStreamException(e);
        }

        return this;
    }

    @Override
    public EDIStreamWriter writeBinaryData(byte[] binary, int start, int end) throws EDIStreamException {
        ensureLevel(LEVEL_ELEMENT);
        ensureState(State.ELEMENT_DATA_BINARY);
        ensureArgs(binary.length, start, end);

        for (int i = start; i < end; i++) {
            write(binary[i]);
        }

        return this;
    }

    @Override
    public EDIStreamWriter writeBinaryData(ByteBuffer binary) throws EDIStreamException {
        ensureLevel(LEVEL_ELEMENT);
        ensureState(State.ELEMENT_DATA_BINARY);

        while (binary.hasRemaining()) {
            write(binary.get());
        }

        return this;
    }

    @Override
    public void binaryData(InputStream binary) {
        // TODO Auto-generated method stub

    }

    @Override
    public void elementData(char[] text, int start, int length) {
        // TODO Auto-generated method stub

    }

    @Override
    public void loopBegin(CharSequence id) {
        // TODO Auto-generated method stub

    }

    @Override
    public void loopEnd(CharSequence id) {
        // TODO Auto-generated method stub

    }

    @Override
    public void elementError(EDIStreamEvent event,
                             EDIStreamValidationError error,
                             int element,
                             int component,
                             int repetition) {
        errors.add(new EDIStreamException("Element Error"));
    }

    @Override
    public void segmentError(CharSequence token, EDIStreamValidationError error) {
        errors.add(new EDIStreamException("Segment Error: " + token + "; " + error));
    }

    private void validate(Consumer<Validator> command) throws EDIStreamException {
        Validator validator = validator();

        if (validator != null) {
            command.accept(validator);

            if (!errors.isEmpty()) {
                EDIStreamException e = errors.get(0);
                errors.clear();
                throw e;
            }
        }
    }

    private static void updateLocation(State state, StaEDIStreamLocation location) {
        if (state == State.ELEMENT_REPEAT) {
            if (location.isRepeated()) {
                location.incrementElementOccurrence();
            } else {
                location.setElementOccurrence(1);
            }
            location.setRepeated(true);
        } else if (location.isRepeated()) {
            if (state != State.COMPONENT_END) {
                /*
                 * Only increment the position if we have not yet started
                 * the composite - i.e, only a single component is present.
                 */
                if (location.getComponentPosition() < 1) {
                    location.incrementElementOccurrence();
                }

                location.setRepeated(false);
            }
        } else {
            location.setElementOccurrence(1);
        }

        switch (state) {
        case COMPONENT_END:
        case HEADER_COMPONENT_END:
            location.incrementComponentPosition();
            break;

        default:
            if (location.getComponentPosition() > 0) {
                location.incrementComponentPosition();
            } else if (location.getElementOccurrence() == 1) {
                location.incrementElementPosition();
            }
            break;
        }
    }
}
