package io.xlate.edi.internal.stream.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.xlate.edi.internal.stream.tokenization.CharacterSet;
import io.xlate.edi.internal.stream.tokenization.Dialect;
import io.xlate.edi.internal.stream.tokenization.DialectFactory;
import io.xlate.edi.internal.stream.tokenization.EDIException;
import io.xlate.edi.schema.EDISimpleType;
import io.xlate.edi.stream.EDIStreamValidationError;

public class DateValidatorTest implements ValueSetTester {

    Dialect dialect;

    @BeforeEach
    public void setUp() throws EDIException {
        dialect = DialectFactory.getDialect("UNA");
        CharacterSet chars = new CharacterSet();
        "UNA=*.?^~UNB*UNOA=3*005435656=1*006415160=1*060515=1434*00000000000778~".chars().forEach(c -> dialect.appendHeader(chars, (char) c));
    }

    @Test
    public void testValidateLengthTooShort() {
        EDISimpleType element = mock(EDISimpleType.class);
        when(element.getMinLength()).thenReturn(6L);
        when(element.getMaxLength()).thenReturn(8L);
        when(element.getValueSet()).thenReturn(setOf());
        ElementValidator v = DateValidator.getInstance();
        List<EDIStreamValidationError> errors = new ArrayList<>();
        v.validate(dialect, element, "0901", errors);
        assertEquals(2, errors.size());
        assertEquals(EDIStreamValidationError.DATA_ELEMENT_TOO_SHORT, errors.get(0));
        assertEquals(EDIStreamValidationError.INVALID_DATE, errors.get(1));
    }

    @Test
    public void testValidateInvalidLength() {
        EDISimpleType element = mock(EDISimpleType.class);
        when(element.getMinLength()).thenReturn(6L);
        when(element.getMaxLength()).thenReturn(8L);
        when(element.getValueSet()).thenReturn(setOf());
        ElementValidator v = DateValidator.getInstance();
        List<EDIStreamValidationError> errors = new ArrayList<>();
        v.validate(dialect, element, "0901000", errors); // Length 7
        assertEquals(1, errors.size());
        assertEquals(EDIStreamValidationError.INVALID_DATE, errors.get(0));
    }

    @Test
    public void testValidateInvalidValue() {
        EDISimpleType element = mock(EDISimpleType.class);
        when(element.getMinLength()).thenReturn(6L);
        when(element.getMaxLength()).thenReturn(8L);
        when(element.getValueSet()).thenReturn(setOf());
        ElementValidator v = DateValidator.getInstance();
        List<EDIStreamValidationError> errors = new ArrayList<>();
        v.validate(dialect, element, "AAAA0901", errors);
        assertEquals(1, errors.size());
        assertEquals(EDIStreamValidationError.INVALID_DATE, errors.get(0));
    }

    @Test
    public void testValidateValidValue() {
        EDISimpleType element = mock(EDISimpleType.class);
        when(element.getMinLength()).thenReturn(6L);
        when(element.getMaxLength()).thenReturn(8L);
        when(element.getValueSet()).thenReturn(setOf());
        ElementValidator v = DateValidator.getInstance();
        List<EDIStreamValidationError> errors = new ArrayList<>();
        v.validate(dialect, element, "20190901", errors);
        assertEquals(0, errors.size());
    }

    @Test
    public void testValidateSixDigitDate() {
        EDISimpleType element = mock(EDISimpleType.class);
        when(element.getMinLength()).thenReturn(6L);
        when(element.getMaxLength()).thenReturn(8L);
        when(element.getValueSet()).thenReturn(setOf());
        ElementValidator v = DateValidator.getInstance();
        List<EDIStreamValidationError> errors = new ArrayList<>();
        v.validate(dialect, element, "191201", errors);
        assertEquals(0, errors.size());
    }

    @Test
    public void testValidateSixDigitDatePreviousCentury() {
        EDISimpleType element = mock(EDISimpleType.class);
        when(element.getMinLength()).thenReturn(6L);
        when(element.getMaxLength()).thenReturn(8L);
        when(element.getValueSet()).thenReturn(setOf());
        ElementValidator v = DateValidator.getInstance();
        List<EDIStreamValidationError> errors = new ArrayList<>();
        v.validate(dialect, element, "991231", errors);
        v.validate(dialect, element, "990228", errors);
        assertEquals(0, errors.size());
    }

    @Test
    public void testValidateDayAfterMonthEnd() {
        EDISimpleType element = mock(EDISimpleType.class);
        when(element.getMinLength()).thenReturn(6L);
        when(element.getMaxLength()).thenReturn(8L);
        when(element.getValueSet()).thenReturn(setOf());
        ElementValidator v = DateValidator.getInstance();
        List<EDIStreamValidationError> errors = new ArrayList<>();
        v.validate(dialect, element, "00000132", errors);
        v.validate(dialect, element, "00000431", errors);
        v.validate(dialect, element, "00000230", errors);
        v.validate(dialect, element, "00010229", errors);
        assertEquals(4, errors.size());
        IntStream.range(0, 3).forEach(i -> assertEquals(EDIStreamValidationError.INVALID_DATE, errors.get(i)));
    }

    @Test
    public void testValidateFebruaryLeapYears() {
        EDISimpleType element = mock(EDISimpleType.class);
        when(element.getMinLength()).thenReturn(6L);
        when(element.getMaxLength()).thenReturn(8L);
        when(element.getValueSet()).thenReturn(setOf());
        ElementValidator v = DateValidator.getInstance();
        List<EDIStreamValidationError> errors = new ArrayList<>();
        v.validate(dialect, element, "20000229", errors);
        v.validate(dialect, element, "19960229", errors);
        assertEquals(0, errors.size());
        v.validate(dialect, element, "19000229", errors);
        assertEquals(1, errors.size());
        assertEquals(EDIStreamValidationError.INVALID_DATE, errors.get(0));
    }

    @Test
    public void testValidateInvalidMonth() {
        EDISimpleType element = mock(EDISimpleType.class);
        when(element.getMinLength()).thenReturn(6L);
        when(element.getMaxLength()).thenReturn(8L);
        when(element.getValueSet()).thenReturn(setOf());
        ElementValidator v = DateValidator.getInstance();
        List<EDIStreamValidationError> errors = new ArrayList<>();
        v.validate(dialect, element, "20001301", errors);
        assertEquals(1, errors.size());
        assertEquals(EDIStreamValidationError.INVALID_DATE, errors.get(0));
    }

    @Test
    public void testFormatValueTooShort() {
        EDISimpleType element = mock(EDISimpleType.class);
        when(element.getMinLength()).thenReturn(6L);
        when(element.getMaxLength()).thenReturn(8L);
        ElementValidator v = DateValidator.getInstance();
        StringBuilder output = new StringBuilder();
        try {
            v.format(dialect, element, "20000", output);
            fail("Exception was expected");
        } catch (EDIException e) {
            assertTrue(e.getMessage().startsWith("EDIE008"));
        }
    }

    @Test
    public void testFormatValueTooLong() {
        EDISimpleType element = mock(EDISimpleType.class);
        when(element.getMinLength()).thenReturn(6L);
        when(element.getMaxLength()).thenReturn(8L);
        ElementValidator v = DateValidator.getInstance();
        StringBuilder output = new StringBuilder();
        try {
            v.format(dialect, element, "200001011", output);
            fail("Exception was expected");
        } catch (EDIException e) {
            assertTrue(e.getMessage().startsWith("EDIE005"));
        }
    }

    @Test
    public void testFormatInvalidDate() {
        EDISimpleType element = mock(EDISimpleType.class);
        when(element.getMinLength()).thenReturn(6L);
        when(element.getMaxLength()).thenReturn(8L);
        ElementValidator v = DateValidator.getInstance();
        StringBuilder output = new StringBuilder();
        try {
            v.format(dialect, element, "20000100", output);
            fail("Exception was expected");
        } catch (EDIException e) {
            assertTrue(e.getMessage().startsWith("EDIE007"));
        }
    }

    @Test
    public void testFormatValidDate() throws EDIException {
        EDISimpleType element = mock(EDISimpleType.class);
        when(element.getMinLength()).thenReturn(6L);
        when(element.getMaxLength()).thenReturn(8L);
        ElementValidator v = DateValidator.getInstance();
        StringBuilder output = new StringBuilder();
        v.format(dialect, element, "20000101", output);
        assertEquals("20000101", output.toString());
    }
}
