package io.xlate.edi.internal.stream.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import io.xlate.edi.schema.EDISyntaxRule.Type;
import io.xlate.edi.stream.EDIStreamEvent;
import io.xlate.edi.stream.EDIStreamValidationError;

public class SingleSyntaxValidatorTest extends SyntaxValidatorTestBase {

    SingleSyntaxValidator validator;

    @BeforeEach
    public void setUp() {
        validator = (SingleSyntaxValidator) SyntaxValidator.getInstance(Type.SINGLE);
        super.setUp();
    }

    @Test
    public void testValidateExclusionAllUsed() {
        when(syntax.getPositions()).thenReturn(Arrays.asList(1, 3, 4));
        List<UsageNode> children = Arrays.asList(mockUsageNode(true, 1),
                                                 mockUsageNode(false, 2),
                                                 mockUsageNode(true, 3),
                                                 mockUsageNode(true, 4));
        when(structure.getChildren()).thenReturn(children);

        final AtomicInteger count = new AtomicInteger(0);

        doAnswer((Answer<Void>) invocation -> {
            count.incrementAndGet();
            return null;
        }).when(handler)
          .elementError(eq(EDIStreamEvent.ELEMENT_OCCURRENCE_ERROR),
                        any(EDIStreamValidationError.class),
                        any(Integer.class),
                        any(Integer.class),
                        any(Integer.class));

        validator.validate(syntax, structure, handler);
        assertEquals(2, count.get());
    }

    @Test
    public void testValidateExclusionNonAnchorUsed() {
        when(syntax.getPositions()).thenReturn(Arrays.asList(1, 3, 4));
        List<UsageNode> children = Arrays.asList(mockUsageNode(false, 1),
                                                 mockUsageNode(false, 2),
                                                 mockUsageNode(true, 3));
        when(structure.getChildren()).thenReturn(children);
        final AtomicInteger count = new AtomicInteger(0);

        doAnswer((Answer<Void>) invocation -> {
            count.incrementAndGet();
            return null;
        }).when(handler)
          .elementError(eq(EDIStreamEvent.ELEMENT_OCCURRENCE_ERROR),
                        any(EDIStreamValidationError.class),
                        any(Integer.class),
                        any(Integer.class),
                        any(Integer.class));

        validator.validate(syntax, structure, handler);
        assertEquals(0, count.get());
    }

    @Test
    public void testValidateExclusionNoneUsed() {
        when(syntax.getPositions()).thenReturn(Arrays.asList(1, 3, 4));
        List<UsageNode> children = Arrays.asList(mockUsageNode(false, 1),
                                                 mockUsageNode(false, 2),
                                                 mockUsageNode(false, 3)/*,
                                                                        mockUsageNode(false, 4)*/);
        when(structure.getChildren()).thenReturn(children);
        final AtomicInteger count = new AtomicInteger(0);

        doAnswer((Answer<Void>) invocation -> {
            count.incrementAndGet();
            return null;
        }).when(handler)
          .elementError(eq(EDIStreamEvent.ELEMENT_OCCURRENCE_ERROR),
                        eq(EDIStreamValidationError.CONDITIONAL_REQUIRED_DATA_ELEMENT_MISSING),
                        any(Integer.class),
                        any(Integer.class),
                        any(Integer.class));

        validator.validate(syntax, structure, handler);
        assertEquals(3, count.get());
    }
}
