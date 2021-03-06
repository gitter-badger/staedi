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

import io.xlate.edi.stream.EDIStreamException;
import io.xlate.edi.stream.Location;

import java.util.HashMap;
import java.util.Map;

public class EDIException extends EDIStreamException {

    private static final long serialVersionUID = -2724168743697298348L;

    public static final Integer MISSING_HANDLER = 1;
    public static final Integer UNSUPPORTED_DIALECT = 2;
    public static final Integer INVALID_STATE = 3;
    public static final Integer INVALID_CHARACTER = 4;
    public static final Integer DATA_ELEMENT_TOO_LONG = 5;
    public static final Integer INVALID_CODE_VALUE = 6;
    public static final Integer INVALID_DATE = 7;
    public static final Integer DATA_ELEMENT_TOO_SHORT = 8;
    public static final Integer INVALID_TIME = 9;

    private static final Map<Integer, String> exceptionMessages = new HashMap<>();

    static {
        exceptionMessages.put(MISSING_HANDLER,
                              "EDIE001 - Missing required handler");
        exceptionMessages.put(UNSUPPORTED_DIALECT,
                              "EDIE002 - Unsupported EDI dialect");
        exceptionMessages.put(INVALID_STATE,
                              "EDIE003 - Invalid processing state");
        exceptionMessages.put(INVALID_CHARACTER,
                              "EDIE004 - Invalid input character");
        exceptionMessages.put(DATA_ELEMENT_TOO_LONG,
                              "EDIE005 - Element exceeds maximum allowed length");
        exceptionMessages.put(INVALID_CODE_VALUE,
                              "EDIE006 - Value not present in code list");
        exceptionMessages.put(INVALID_DATE,
                              "EDIE007 - Invalid date");
        exceptionMessages.put(DATA_ELEMENT_TOO_SHORT,
                              "EDIE008 - Element value less than minimum length");
        exceptionMessages.put(INVALID_TIME,
                              "EDIE009 - Invalid time");
    }

    public EDIException() {
        super();
    }

    public EDIException(String message, Throwable cause) {
        super(message, cause);
    }

    EDIException(Integer id, Throwable cause) {
        super(exceptionMessages.get(id), cause);
    }

    EDIException(Integer id, Throwable cause, String message) {
        super(exceptionMessages.get(id) + message, cause);
    }

    public EDIException(String message) {
        super(message);
    }

    public EDIException(Integer id) {
        super(exceptionMessages.get(id));
    }

    EDIException(Integer id, Location location) {
        super(exceptionMessages.get(id), location);
    }

    EDIException(Integer id, String message, Location location) {
        super(exceptionMessages.get(id) + message, location);
    }

    public EDIException(Integer id, String message) {
        super(exceptionMessages.get(id) + message);
    }

    public EDIException(Throwable cause) {
        super(cause);
    }

}
