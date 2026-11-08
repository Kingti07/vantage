package dev.vantage.launcher.game;

import dev.vantage.common.util.OperatingSystem;
import dev.vantage.launcher.game.MojangModels.Rule;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Evaluates the allow/disallow rules attached to libraries and arguments in Mojang version JSONs. */
public final class Rules {
	private Rules() {
	}

	public static boolean allows(List<Rule> rules, Map<String, Boolean> features) {
		if (rules == null || rules.isEmpty()) {
			return true;
		}
		boolean allowed = false;
		for (Rule rule : rules) {
			if (matches(rule, features)) {
				allowed = "allow".equals(rule.action());
			}
		}
		return allowed;
	}

	private static boolean matches(Rule rule, Map<String, Boolean> features) {
		if (rule.os() != null) {
			OperatingSystem os = OperatingSystem.current();
			if (rule.os().name() != null && !rule.os().name().equals(os.mojangName())) {
				return false;
			}
			if (rule.os().arch() != null) {
				String arch = System.getProperty("os.arch", "");
				boolean x86 = !OperatingSystem.is64Bit();
				if (rule.os().arch().equals("x86") ? !x86 : !arch.equals(rule.os().arch())) {
					return false;
				}
			}
			if (rule.os().version() != null) {
				try {
					if (!Pattern.compile(rule.os().version()).matcher(System.getProperty("os.version", "")).find()) {
						return false;
					}
				} catch (PatternSyntaxException e) {
					return false;
				}
			}
		}
		if (rule.features() != null) {
			for (Map.Entry<String, Boolean> feature : rule.features().entrySet()) {
				if (features.getOrDefault(feature.getKey(), false) != feature.getValue()) {
					return false;
				}
			}
		}
		return true;
	}
}
