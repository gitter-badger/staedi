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
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Base64;
import java.util.Deque;
import java.util.NoSuchElementException;
import java.util.Queue;

import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import io.xlate.edi.stream.EDIStreamEvent;
import io.xlate.edi.stream.EDIStreamException;
import io.xlate.edi.stream.EDIStreamReader;

public class StaEDIXMLStreamReader implements XMLStreamReader {

    private static final Location location = new DefaultLocation();
    private static final QName DUMMY_QNAME = new QName("DUMMY");
    private static final QName INTERCHANGE = new QName("INTERCHANGE");

    private final EDIStreamReader ediReader;
    private final Queue<Integer> eventQueue = new ArrayDeque<>(3);
    private final Queue<QName> elementQueue = new ArrayDeque<>(3);
    private final Deque<QName> elementStack = new ArrayDeque<>();

    private final StringBuilder cdataBuilder = new StringBuilder();
    private char[] cdata;

    public StaEDIXMLStreamReader(EDIStreamReader ediReader) throws XMLStreamException {
        this.ediReader = ediReader;

        if (ediReader.getEventType() == EDIStreamEvent.START_INTERCHANGE) {
            enqueueEvent(EDIStreamEvent.START_INTERCHANGE);
        }
    }

    @Override
    public Object getProperty(String name) {
        if (name == null) {
            throw new IllegalArgumentException("name must not be null");
        }
        return null;
    }

    private boolean isEvent(int... eventTypes) {
    	return Arrays.stream(eventTypes).anyMatch(eventQueue.element()::equals);
    }

    private QName deriveName(QName parent, String hint) {
        String name = hint;

        if (name == null) {
            final io.xlate.edi.stream.Location l = ediReader.getLocation();
            final int componentPosition = l.getComponentPosition();

            if (componentPosition > 0) {
                name = String.format("%s-%d", parent, componentPosition);
            } else {
                name = String.format("%s%02d", parent, l.getElementPosition());
            }
        }

        return new QName(name);
    }

    private void enqueueEvent(int xmlEvent, QName element, boolean remember) {
        eventQueue.add(xmlEvent);
        elementQueue.add(element);

        if (remember) {
            elementStack.addFirst(element);
        }
    }

    private void advanceEvent() {
        eventQueue.remove();
        elementQueue.remove();
    }

    private void enqueueEvent(EDIStreamEvent ediEvent) throws XMLStreamException {
        final QName name;
        cdataBuilder.setLength(0);
        cdata = null;

        switch (ediEvent) {
        case ELEMENT_DATA:
            name = deriveName(elementStack.getFirst(), null);
            enqueueEvent(START_ELEMENT, name, false);
            enqueueEvent(CHARACTERS, DUMMY_QNAME, false);
            enqueueEvent(END_ELEMENT, name, false);
            break;

        case ELEMENT_DATA_BINARY:
            /*
             * This section will read the binary data and Base64 the stream
             * into an XML CDATA section.
             * */
            name = deriveName(elementStack.getFirst(), null);
            enqueueEvent(START_ELEMENT, name, false);
            enqueueEvent(CDATA, DUMMY_QNAME, false);

            // This only will work if using a validation filter!
            InputStream input = ediReader.getBinaryData();
            OutputStream output = Base64.getEncoder().wrap(new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                    cdataBuilder.append((char) b);
                }
            });
            byte[] buffer = new byte[4096];
            int amount;

            try {
                while ((amount = input.read(buffer)) > -1) {
                    output.write(buffer, 0, amount);
                }

                output.close();
            } catch (IOException e) {
                throw new XMLStreamException(e);
            }

            enqueueEvent(END_ELEMENT, name, false);
            break;

        case START_INTERCHANGE:
            enqueueEvent(START_DOCUMENT, DUMMY_QNAME, false);
            enqueueEvent(START_ELEMENT, INTERCHANGE, true);
            break;

        case START_SEGMENT:
            enqueueEvent(START_ELEMENT, new QName(ediReader.getText()), true);
            break;

        case START_GROUP:
        case START_TRANSACTION:
        case START_LOOP:
            name = deriveName(elementStack.getFirst(), ediReader.getText());
            enqueueEvent(START_ELEMENT, name, true);
            break;

        case START_COMPOSITE:
            name = deriveName(elementStack.getFirst(), ediReader.getReferenceCode());
            enqueueEvent(START_ELEMENT, name, true);
            break;

        case END_INTERCHANGE:
            enqueueEvent(END_ELEMENT, elementStack.removeFirst(), false);
            enqueueEvent(END_DOCUMENT, DUMMY_QNAME, false);
            break;

        case END_GROUP:
        case END_TRANSACTION:
        case END_LOOP:
        case END_SEGMENT:
        case END_COMPOSITE:
            enqueueEvent(END_ELEMENT, elementStack.removeFirst(), false);
            break;

        case SEGMENT_ERROR:
            throw new XMLStreamException(String.format("Segment %s has error %s", ediReader.getText(), ediReader.getErrorType()));

        default:
            throw new IllegalStateException("Unknown state: " + ediEvent);
        }
    }

    private void requireCharacters() {
        if (!isCharacters()) {
            throw new IllegalStateException("Text only available for CHARACTERS");
        }
    }

    @Override
    public int next() throws XMLStreamException {
        if (!eventQueue.isEmpty()) {
            advanceEvent();
        }

        if (eventQueue.isEmpty()) {
            try {
                enqueueEvent(ediReader.next());
            } catch (EDIStreamException | NoSuchElementException e) {
                throw new XMLStreamException(e);
            }
        }

        return getEventType();
    }

    @Override
    public void require(int type, String namespaceURI, String localName) throws XMLStreamException {
        final int currentType = getEventType();

        if (currentType != type) {
            throw new XMLStreamException("Current type " + currentType + " does not match required type " + type);
        }

        if (localName != null && hasName()) {
            final QName name = getName();
            final String currentLocalPart = name.getLocalPart();

            if (!localName.equals(currentLocalPart)) {
                throw new XMLStreamException("Current localPart " + currentLocalPart + " does not match required localName " + localName);
            }
        }

        if (namespaceURI != null) {
            throw new XMLStreamException("Current namespace '' does not match namespaceURI " + namespaceURI);
        }
    }

    static void streamException(String message) throws XMLStreamException {
        throw new XMLStreamException(message);
    }

    @Override
    public String getElementText() throws XMLStreamException {
        if (ediReader.getEventType() != EDIStreamEvent.ELEMENT_DATA) {
            streamException("Element text only available for simple element");
        }

        if (getEventType() != START_ELEMENT) {
            streamException("Element text only available on START_ELEMENT");
        }

        int eventType = next();

        if (eventType != CHARACTERS) {
            streamException("Unexpected event type: " + eventType);
        }

        final String text = getText();
        eventType = next();

        if (eventType != END_ELEMENT) {
            streamException("Unexpected event type after text " + eventType);
        }

        return text;
    }

    @Override
    public int nextTag() throws XMLStreamException {
        int eventType;

        do {
            eventType = next();
        } while (eventType != START_ELEMENT && eventType != END_ELEMENT);

        return eventType;
    }

    @Override
    public boolean hasNext() throws XMLStreamException {
        try {
            return ediReader.hasNext();
        } catch (EDIStreamException e) {
            throw new XMLStreamException(e);
        }
    }

    @Override
    public void close() throws XMLStreamException {
        try {
            ediReader.close();
        } catch (IOException e) {
            throw new XMLStreamException(e);
        }
    }

    @Override
    public String getNamespaceURI(String prefix) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isStartElement() {
        return isEvent(START_ELEMENT);
    }

    @Override
    public boolean isEndElement() {
        return isEvent(END_ELEMENT);
    }

    @Override
    public boolean isCharacters() {
        return isEvent(CHARACTERS, CDATA);
    }

    @Override
    public boolean isWhiteSpace() {
        return false;
    }

    @Override
    public String getAttributeValue(String namespaceURI, String localName) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getAttributeCount() {
        return 0;
    }

    @Override
    public QName getAttributeName(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getAttributeNamespace(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getAttributeLocalName(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getAttributePrefix(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getAttributeType(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getAttributeValue(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isAttributeSpecified(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getNamespaceCount() {
        return 0;
    }

    @Override
    public String getNamespacePrefix(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getNamespaceURI(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public NamespaceContext getNamespaceContext() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getEventType() {
        return eventQueue.isEmpty() ? -1 : eventQueue.element();
    }

    @Override
    public String getText() {
        requireCharacters();

        if (cdataBuilder.length() > 0) {
        	if (cdata == null) {
                cdata = new char[cdataBuilder.length()];
                cdataBuilder.getChars(0, cdataBuilder.length(), cdata, 0);
            }

            return new String(cdata);
        }
        return ediReader.getText();
    }

    @Override
    public char[] getTextCharacters() {
        requireCharacters();

        if (cdataBuilder.length() > 0) {
            if (cdata == null) {
                cdata = new char[cdataBuilder.length()];
                cdataBuilder.getChars(0, cdataBuilder.length(), cdata, 0);
            }

            return cdata;
        }
        return ediReader.getTextCharacters();
    }

    @Override
    public int getTextCharacters(int sourceStart,
                                 char[] target,
                                 int targetStart,
                                 int length) throws XMLStreamException {

        requireCharacters();

        if (cdataBuilder.length() > 0) {
            if (cdata == null) {
                cdata = new char[cdataBuilder.length()];
                cdataBuilder.getChars(0, cdataBuilder.length(), cdata, 0);
            }

            if (targetStart < 0) {
                throw new IndexOutOfBoundsException("targetStart < 0");
            }
            if (targetStart > target.length) {
                throw new IndexOutOfBoundsException("targetStart > target.length");
            }
            if (length < 0) {
                throw new IndexOutOfBoundsException("length < 0");
            }
            if (targetStart + length > target.length) {
                throw new IndexOutOfBoundsException("targetStart + length > target.length");
            }

            System.arraycopy(cdata, sourceStart, target, targetStart, length);
            return length;
        }
        return ediReader.getTextCharacters(sourceStart, target, targetStart, length);
    }

    @Override
    public int getTextStart() {
        requireCharacters();

        if (cdataBuilder.length() > 0) {
            return 0;
        }
        return ediReader.getTextStart();
    }

    @Override
    public int getTextLength() {
        requireCharacters();

        if (cdataBuilder.length() > 0) {
            return cdataBuilder.length();
        }
        return ediReader.getTextLength();
    }

    @Override
    public String getEncoding() {
        return null;
    }

    @Override
    public boolean hasText() {
        return isCharacters();
    }

    @Override
    public Location getLocation() {
        return location;
    }

    @Override
    public QName getName() {
        if (hasName()) {
            return elementQueue.element();
        }
        throw new IllegalStateException("Text only available for START_ELEMENT or END_ELEMENT");
    }

    @Override
    public String getLocalName() {
        return getName().getLocalPart();
    }

    @Override
    public boolean hasName() {
        return isStartElement() || isEndElement();
    }

    @Override
    public String getNamespaceURI() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getPrefix() {
        return null;
    }

    @Override
    public String getVersion() {
        return null;
    }

    @Override
    public boolean isStandalone() {
        return false;
    }

    @Override
    public boolean standaloneSet() {
        return false;
    }

    @Override
    public String getCharacterEncodingScheme() {
        return null;
    }

    @Override
    public String getPITarget() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getPIData() {
        throw new UnsupportedOperationException();
    }

    private static class DefaultLocation implements Location {
        @Override
        public int getLineNumber() {
            return -1;
        }

        @Override
        public int getColumnNumber() {
            return -1;
        }

        @Override
        public int getCharacterOffset() {
            return -1;
        }

        @Override
        public String getPublicId() {
            return null;
        }

        @Override
        public String getSystemId() {
            return null;
        }
    }
}
