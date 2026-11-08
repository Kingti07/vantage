package dev.vantage.common.version;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lenient semantic version used for Minecraft versions ("1.21.11") and mod versions ("0.141.6+1.21.11",
 * "2.1.0-beta.3"). Build metadata after '+' is ignored for ordering, as SemVer requires.
 * Any number of numeric components is accepted; missing components compare as zero.
 */
public final class SemanticVersion implements Comparable<SemanticVersion> {
	private final int[] components;
	private final String preRelease; // null when this is a release
	private final String raw;

	private SemanticVersion(int[] components, String preRelease, String raw) {
		this.components = components;
		this.preRelease = preRelease;
		this.raw = raw;
	}

	/** Returns the parsed version, or {@code null} if the string is not a recognisable version. */
	public static SemanticVersion tryParse(String input) {
		if (input == null) {
			return null;
		}
		String text = input.trim();
		if (text.startsWith("v") || text.startsWith("V")) {
			text = text.substring(1);
		}
		int plus = text.indexOf('+');
		if (plus >= 0) {
			text = text.substring(0, plus);
		}
		String pre = null;
		int dash = text.indexOf('-');
		if (dash >= 0) {
			pre = text.substring(dash + 1);
			text = text.substring(0, dash);
			if (pre.isEmpty()) {
				return null;
			}
		}
		if (text.isEmpty()) {
			return null;
		}
		String[] parts = text.split("\\.");
		List<Integer> numbers = new ArrayList<>(parts.length);
		for (String part : parts) {
			if (part.isEmpty() || !part.chars().allMatch(Character::isDigit) || part.length() > 9) {
				return null;
			}
			numbers.add(Integer.parseInt(part));
		}
		int[] components = numbers.stream().mapToInt(Integer::intValue).toArray();
		return new SemanticVersion(components, pre, input.trim());
	}

	public int component(int index) {
		return index < components.length ? components[index] : 0;
	}

	public int componentCount() {
		return components.length;
	}

	public boolean isPreRelease() {
		return preRelease != null;
	}

	public SemanticVersion withComponents(int... newComponents) {
		return new SemanticVersion(newComponents.clone(), null, null);
	}

	@Override
	public int compareTo(SemanticVersion other) {
		int length = Math.max(components.length, other.components.length);
		for (int i = 0; i < length; i++) {
			int cmp = Integer.compare(component(i), other.component(i));
			if (cmp != 0) {
				return cmp;
			}
		}
		if (preRelease == null) {
			return other.preRelease == null ? 0 : 1;
		}
		if (other.preRelease == null) {
			return -1;
		}
		return comparePreRelease(preRelease, other.preRelease);
	}

	private static int comparePreRelease(String a, String b) {
		String[] left = a.split("\\.");
		String[] right = b.split("\\.");
		for (int i = 0; i < Math.min(left.length, right.length); i++) {
			boolean leftNum = left[i].chars().allMatch(Character::isDigit) && !left[i].isEmpty();
			boolean rightNum = right[i].chars().allMatch(Character::isDigit) && !right[i].isEmpty();
			int cmp;
			if (leftNum && rightNum) {
				cmp = Long.compare(Long.parseLong(left[i]), Long.parseLong(right[i]));
			} else if (leftNum) {
				cmp = -1;
			} else if (rightNum) {
				cmp = 1;
			} else {
				cmp = left[i].toLowerCase(Locale.ROOT).compareTo(right[i].toLowerCase(Locale.ROOT));
			}
			if (cmp != 0) {
				return cmp;
			}
		}
		return Integer.compare(left.length, right.length);
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof SemanticVersion other && compareTo(other) == 0;
	}

	@Override
	public int hashCode() {
		int hash = 0;
		for (int i = components.length - 1; i >= 0; i--) {
			if (hash != 0 || components[i] != 0) {
				hash = hash * 31 + components[i];
			}
		}
		return hash * 31 + (preRelease == null ? 0 : preRelease.hashCode());
	}

	@Override
	public String toString() {
		if (raw != null) {
			return raw;
		}
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < components.length; i++) {
			if (i > 0) {
				builder.append('.');
			}
			builder.append(components[i]);
		}
		if (preRelease != null) {
			builder.append('-').append(preRelease);
		}
		return builder.toString();
	}
}
