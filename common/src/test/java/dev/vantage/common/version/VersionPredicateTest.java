package dev.vantage.common.version;

import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.vantage.common.version.VersionPredicate.Result.SATISFIED;
import static dev.vantage.common.version.VersionPredicate.Result.UNKNOWN;
import static dev.vantage.common.version.VersionPredicate.Result.UNSATISFIED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionPredicateTest {
	private static VersionPredicate.Result test(String predicate, String version) {
		return VersionPredicate.anyOf(List.of(predicate), version);
	}

	@Test
	void ordering() {
		assertTrue(SemanticVersion.tryParse("1.21.11").compareTo(SemanticVersion.tryParse("1.21.9")) > 0);
		assertTrue(SemanticVersion.tryParse("1.21").compareTo(SemanticVersion.tryParse("1.21.0")) == 0);
		assertTrue(SemanticVersion.tryParse("1.0.0-beta.2").compareTo(SemanticVersion.tryParse("1.0.0")) < 0);
		assertTrue(SemanticVersion.tryParse("1.0.0-beta.10").compareTo(SemanticVersion.tryParse("1.0.0-beta.2")) > 0);
		assertTrue(SemanticVersion.tryParse("0.141.6+1.21.11").compareTo(SemanticVersion.tryParse("0.141.6")) == 0);
	}

	@Test
	void operators() {
		assertEquals(SATISFIED, test("*", "1.21.11"));
		assertEquals(SATISFIED, test("1.21.11", "1.21.11"));
		assertEquals(UNSATISFIED, test("1.21.10", "1.21.11"));
		assertEquals(SATISFIED, test(">=1.21.9", "1.21.11"));
		assertEquals(UNSATISFIED, test("<1.21.11", "1.21.11"));
		assertEquals(SATISFIED, test(">=1.21.9 <1.22", "1.21.11"));
		assertEquals(UNSATISFIED, test(">=1.21.9 <1.21.11", "1.21.11"));
	}

	@Test
	void tildeCaretAndWildcards() {
		assertEquals(SATISFIED, test("~1.21.10", "1.21.11"));
		assertEquals(UNSATISFIED, test("~1.21.10", "1.22"));
		assertEquals(UNSATISFIED, test("~1.21.11", "1.21.10"));
		assertEquals(SATISFIED, test("^1.2.3", "1.9.0"));
		assertEquals(UNSATISFIED, test("^1.2.3", "2.0.0"));
		assertEquals(SATISFIED, test("1.21.x", "1.21.11"));
		assertEquals(UNSATISFIED, test("1.20.x", "1.21.11"));
	}

	@Test
	void unknownVersionsNeverFailHard() {
		assertEquals(UNKNOWN, test(">=1.0", "mc1.21-custom"));
		assertEquals(SATISFIED, VersionPredicate.anyOf(List.of("1.20.x", "1.21.x"), "1.21.11"));
	}
}
