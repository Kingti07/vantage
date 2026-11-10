package dev.vantage.common.version;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Evaluates Fabric-style version predicates as found in {@code fabric.mod.json}:
 * <ul>
 *   <li>{@code *} or empty – any version</li>
 *   <li>{@code 1.21.11}, {@code =1.21.11} – exact</li>
 *   <li>{@code >=1.21.9}, {@code >1.21}, {@code <=1.21.11}, {@code <1.22}</li>
 *   <li>{@code ~1.21.11} – same major.minor, at least the given version</li>
 *   <li>{@code ^1.2.3} – same major, at least the given version</li>
 *   <li>{@code 1.21.x} – x-range</li>
 *   <li>space separated terms are AND-ed: {@code >=1.21.9 <1.22}</li>
 * </ul>
 * A JSON array of predicates (handled by callers via {@link #anyOf}) is OR-ed.
 * <p>
 * Evaluation is three-valued: when a version or predicate cannot be parsed the result is {@link Result#UNKNOWN},
 * so callers never report false incompatibilities for exotic version schemes.
 */
public final class VersionPredicate {
	public enum Result {SATISFIED, UNSATISFIED, UNKNOWN}

	private final List<Term> terms;
	private final String source;

	private VersionPredicate(List<Term> terms, String source) {
		this.terms = terms;
		this.source = source;
	}

	/** Parses a predicate. Returns {@code null} if any term is unparseable. */
	public static VersionPredicate parse(String predicate) {
		String text = predicate == null ? "" : predicate.trim();
		List<Term> terms = new ArrayList<>();
		if (text.isEmpty() || "*".equals(text)) {
			return new VersionPredicate(terms, text);
		}
		for (String part : text.split("\\s+")) {
			Term term = Term.parse(part);
			if (term == null) {
				return null;
			}
			terms.add(term);
		}
		return new VersionPredicate(terms, text);
	}

	public Result test(String version) {
		if (terms.isEmpty()) {
			return Result.SATISFIED;
		}
		SemanticVersion parsed = SemanticVersion.tryParse(version);
		if (parsed == null) {
			return source.equals(version) ? Result.SATISFIED : Result.UNKNOWN;
		}
		for (Term term : terms) {
			if (!term.matches(parsed)) {
				return Result.UNSATISFIED;
			}
		}
		return Result.SATISFIED;
	}

	/** OR-combines several predicates (fabric.mod.json allows an array of alternatives). */
	public static Result anyOf(List<String> predicates, String version) {
		boolean unknown = false;
		for (String predicate : predicates) {
			VersionPredicate parsed = parse(predicate);
			Result result = parsed == null ? Result.UNKNOWN : parsed.test(version);
			if (result == Result.SATISFIED) {
				return Result.SATISFIED;
			}
			unknown |= result == Result.UNKNOWN;
		}
		return unknown ? Result.UNKNOWN : Result.UNSATISFIED;
	}

	@Override
	public String toString() {
		return source.isEmpty() ? "*" : source;
	}

	private record Term(String operator, SemanticVersion version, SemanticVersion upperExclusive) {
		static Term parse(String text) {
			String op = "=";
			for (String candidate : new String[]{">=", "<=", ">", "<", "=", "~", "^"}) {
				if (text.startsWith(candidate)) {
					op = candidate;
					text = text.substring(candidate.length());
					break;
				}
			}
			String lower = text.toLowerCase(Locale.ROOT);
			int wildcard = indexOfWildcard(lower);
			if (wildcard >= 0) {
				// "1.21.x" -> [1.21.0, 1.22.0)
				if (!"=".equals(op)) {
					return null;
				}
				String prefix = lower.substring(0, wildcard);
				if (prefix.endsWith(".")) {
					prefix = prefix.substring(0, prefix.length() - 1);
				}
				if (prefix.isEmpty()) {
					return new Term("*", null, null);
				}
				SemanticVersion base = SemanticVersion.tryParse(prefix);
				if (base == null) {
					return null;
				}
				int n = base.componentCount();
				int[] upper = new int[n];
				for (int i = 0; i < n; i++) {
					upper[i] = base.component(i);
				}
				upper[n - 1]++;
				return new Term("range", base, base.withComponents(upper));
			}
			SemanticVersion version = SemanticVersion.tryParse(text);
			if (version == null) {
				return null;
			}
			return switch (op) {
				case "~" -> new Term("range", version, version.withComponents(version.component(0), version.component(1) + 1));
				case "^" -> new Term("range", version, version.withComponents(version.component(0) + 1));
				default -> new Term(op, version, null);
			};
		}

		private static int indexOfWildcard(String text) {
			String[] parts = text.split("\\.");
			int offset = 0;
			for (String part : parts) {
				if ("x".equals(part) || "*".equals(part)) {
					return offset;
				}
				offset += part.length() + 1;
			}
			return -1;
		}

		boolean matches(SemanticVersion candidate) {
			return switch (operator) {
				case "*" -> true;
				case "=" -> candidate.compareTo(version) == 0;
				case ">=" -> candidate.compareTo(version) >= 0;
				case "<=" -> candidate.compareTo(version) <= 0;
				case ">" -> candidate.compareTo(version) > 0;
				case "<" -> candidate.compareTo(version) < 0;
				case "range" -> candidate.compareTo(version) >= 0 && candidate.compareTo(upperExclusive) < 0;
				default -> false;
			};
		}
	}
}
