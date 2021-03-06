package io.xlate.edi.internal.stream.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.xlate.edi.internal.stream.tokenization.CharacterSet;
import io.xlate.edi.internal.stream.tokenization.Dialect;
import io.xlate.edi.internal.stream.tokenization.DialectFactory;
import io.xlate.edi.internal.stream.tokenization.EDIException;
import io.xlate.edi.schema.EDISimpleType;
import io.xlate.edi.stream.EDIStreamValidationError;

public class NumericValidatorTest implements ValueSetTester {

    Dialect dialect;

    @BeforeEach
    public void setUp() throws EDIException {
        dialect = DialectFactory.getDialect("UNA");
        CharacterSet chars = new CharacterSet();
        "UNA=*.?^~UNB*UNOA=3*005435656=1*006415160=1*060515=1434*00000000000778~".chars().forEach(c -> dialect.appendHeader(chars, (char) c));
    }

    @Test
    public void testValidateInvalidNegative() {
        assertEquals(-2, NumericValidator.getInstance().validate(dialect, "20-"));
    }

    @Test
    public void testValidateValidNegative() {
        assertEquals(2, NumericValidator.getInstance().validate(dialect, "-20"));
    }

    @Test
    public void testValidateLengthTooShort() {
        EDISimpleType element = mock(EDISimpleType.class);
        when(element.getMinLength()).thenReturn(5L);
        when(element.getMaxLength()).thenReturn(10L);
        when(element.getValueSet()).thenReturn(setOf());
        ElementValidator v = NumericValidator.getInstance();
        List<EDIStreamValidationError> errors = new ArrayList<>();
        v.validate(dialect, element, "1234", errors);
        assertEquals(1, errors.size());
        assertEquals(EDIStreamValidationError.DATA_ELEMENT_TOO_SHORT, errors.get(0));
    }

    @Test
    public void testValidateLengthTooLong() {
        EDISimpleType element = mock(EDISimpleType.class);
        when(element.getMinLength()).thenReturn(5L);
        when(element.getMaxLength()).thenReturn(10L);
        when(element.getValueSet()).thenReturn(setOf());
        ElementValidator v = NumericValidator.getInstance();
        List<EDIStreamValidationError> errors = new ArrayList<>();
        v.validate(dialect, element, "12345678901", errors);
        assertEquals(1, errors.size());
        assertEquals(EDIStreamValidationError.DATA_ELEMENT_TOO_LONG, errors.get(0));
    }

    @Test
    public void testValidateInvalidCharacter() {
        EDISimpleType element = mock(EDISimpleType.class);
        when(element.getMinLength()).thenReturn(5L);
        when(element.getMaxLength()).thenReturn(10L);
        when(element.getValueSet()).thenReturn(setOf());
        ElementValidator v = NumericValidator.getInstance();
        List<EDIStreamValidationError> errors = new ArrayList<>();
        v.validate(dialect, element, "1234F", errors);
        assertEquals(1, errors.size());
        assertEquals(EDIStreamValidationError.INVALID_CHARACTER_DATA, errors.get(0));
    }

    @Test
    public void testFormatValueTooLong() {
        EDISimpleType element = mock(EDISimpleType.class);
        when(element.getMinLength()).thenReturn(4L);
        when(element.getMaxLength()).thenReturn(5L);
        ElementValidator v = NumericValidator.getInstance();
        StringBuilder output = new StringBuilder();
        try {
            v.format(dialect, element, "123456", output);
            fail("Exception was expected:" + output.toString());
        } catch (EDIException e) {
            assertEquals("EDIE005", e.getMessage().subSequence(0, 7));
        }
    }

    @Test
    public void testFormatInvalidCharacterData() {
        EDISimpleType element = mock(EDISimpleType.class);
        when(element.getMinLength()).thenReturn(4L);
        when(element.getMaxLength()).thenReturn(5L);
        ElementValidator v = NumericValidator.getInstance();
        StringBuilder output = new StringBuilder();
        try {
            v.format(dialect, element, "1234F", output);
            fail("Exception was expected: " + output.toString());
        } catch (EDIException e) {
            assertEquals("EDIE004", e.getMessage().subSequence(0, 7));
        }
    }

    @Test
    public void testFormatValidNumber() throws EDIException {
        EDISimpleType element = mock(EDISimpleType.class);
        when(element.getMinLength()).thenReturn(4L);
        when(element.getMaxLength()).thenReturn(8L);
        when(element.getValueSet()).thenReturn(setOf());
        ElementValidator v = NumericValidator.getInstance();
        StringBuilder output = new StringBuilder();
        v.format(dialect, element, "1234", output);
        assertEquals("1234", output.toString());
    }

    @Test
    public void testFormatValidNumberPadded() throws EDIException {
        EDISimpleType element = mock(EDISimpleType.class);
        when(element.getMinLength()).thenReturn(6L);
        when(element.getMaxLength()).thenReturn(8L);
        when(element.getValueSet()).thenReturn(setOf());
        ElementValidator v = NumericValidator.getInstance();
        StringBuilder output = new StringBuilder();
        v.format(dialect, element, "123", output);
        assertEquals("000123", output.toString());
    }
}
