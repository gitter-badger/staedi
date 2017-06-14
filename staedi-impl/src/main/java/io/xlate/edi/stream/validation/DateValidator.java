package io.xlate.edi.stream.validation;

import io.xlate.edi.schema.EDISimpleType;
import io.xlate.edi.stream.internal.EDIException;

import java.io.IOException;
import java.util.Calendar;
import java.util.List;

class DateValidator extends ElementValidator {

	private static final DateValidator singleton = new DateValidator();

	private DateValidator() {}

	static DateValidator getInstance() {
		return singleton;
	}

	@Override
	void validate(
			EDISimpleType element,
			CharSequence value,
			List<Integer> errors) {

		int length = value.length();

		if (!super.validLength(element, length, errors) || length % 2 != 0) {
			errors.add(INVALID_DATE);
		} else if (!validValue(value)) {
			errors.add(INVALID_DATE);
		}
	}

	@Override
	void format(EDISimpleType element, CharSequence value, Appendable result)
			throws EDIException {

		int length = value.length();
		super.checkLength(element, length);

		if (validValue(value)) {
			try {
				result.append(value);
			} catch (IOException e) {
				throw new EDIException(e);
			}
		} else if (length < element.getMinLength()) {
			// TODO: DATA_ELEMENT_TOO_LONG
			throw new EDIException();
		} else {
			// TODO: INVALID_DATE
			throw new EDIException();
		}
	}

	private static boolean validValue(CharSequence value) {
		int length = value.length();
		int dateValue = 0;

		for (int i = 0; i < length; i++) {
			char c = value.charAt(i);
			switch (c) {
			case '0':
			case '1':
			case '2':
			case '3':
			case '4':
			case '5':
			case '6':
			case '7':
			case '8':
			case '9':
				dateValue = dateValue * 10 + Character.digit(c, 10);
				break;
			default:
				return false;
			}
		}

		int[] date = new int[3];

		date[2] = dateValue % 100;
		dateValue /= 100;
		date[1] = dateValue % 100;
		dateValue /= 100;
		date[0] = dateValue;

		/* Add the century if the date is missing it. */
		if (length == 6) {
			// FIXME: add reader property for date window
			int year = Calendar.getInstance().get(Calendar.YEAR);
			int century = year / 100;

			if (date[0] > (year % 100)) {
				date[0] = (century - 1) * 100 + date[0];
			} else {
				date[0] = century * 100 + date[0];
			}
		}

		return dateIsValid(date[0], date[1], date[2]);
	}

	private static boolean dateIsValid(int year, int month, int day) {
		switch (month) {
		case 1:
		case 3:
		case 5:
		case 7:
		case 8:
		case 10:
		case 12:
			return day <= 31;
		case 4:
		case 6:
		case 9:
		case 11:
			return day <= 30;
		case 2:
			return day <= 28 || (isLeapYear(year) && day <= 29);
		default:
			return false;
		}
	}

	private static boolean isLeapYear(int year) {
		return (year % 4 == 0) && (year % 100 != 0 || year % 400 == 0);
	}
}