package dev.vantage.launcher.game;

import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinReg;
import dev.vantage.common.util.OperatingSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Windows-only tweaks that help the game's frame rate. Every method is a no-op on other systems and never throws:
 * a tweak that cannot be applied is logged and skipped.
 */
public final class WindowsTuning {
	private static final Logger LOGGER = LoggerFactory.getLogger(WindowsTuning.class);
	/** Windows' per-application graphics preference (Settings → System → Display → Graphics). */
	static final String GPU_PREFERENCES_KEY = "Software\\Microsoft\\DirectX\\UserGpuPreferences";
	static final String HIGH_PERFORMANCE = "GpuPreference=2;";
	private static final int ABOVE_NORMAL_PRIORITY_CLASS = 0x00008000;

	private WindowsTuning() {
	}

	public static boolean supported() {
		return OperatingSystem.current() == OperatingSystem.WINDOWS;
	}

	/**
	 * Gives the game process "above normal" CPU priority so background programs disturb it less. Not "high" or
	 * "realtime", which could starve the system.
	 */
	public static void raisePriority(Process process) {
		if (!supported()) {
			return;
		}
		try {
			WinNT.HANDLE handle = Kernel32.INSTANCE.OpenProcess(WinNT.PROCESS_SET_INFORMATION, false, (int) process.pid());
			if (handle == null) {
				LOGGER.warn("Could not open the game process to raise its priority");
				return;
			}
			try {
				if (!Kernel32.INSTANCE.SetPriorityClass(handle, new WinDef.DWORD(ABOVE_NORMAL_PRIORITY_CLASS))) {
					LOGGER.warn("Windows refused to raise the game's priority (error {})", Kernel32.INSTANCE.GetLastError());
				}
			} finally {
				Kernel32.INSTANCE.CloseHandle(handle);
			}
		} catch (RuntimeException | LinkageError e) {
			LOGGER.warn("Could not raise the game's priority: {}", e.toString());
		}
	}

	/**
	 * Sets or clears Windows' "High performance" graphics preference for the Java executable that runs the game, so
	 * laptops with two GPUs render on the dedicated one. Only values Vantage itself set are removed again.
	 */
	public static void preferHighPerformanceGpu(Path javaExecutable, boolean enable) {
		if (!supported() || javaExecutable == null) {
			return;
		}
		String exe = gameExecutable(javaExecutable).toAbsolutePath().toString();
		try {
			boolean present = Advapi32Util.registryKeyExists(WinReg.HKEY_CURRENT_USER, GPU_PREFERENCES_KEY)
				&& Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, GPU_PREFERENCES_KEY, exe);
			if (enable) {
				if (present && HIGH_PERFORMANCE.equals(Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, GPU_PREFERENCES_KEY, exe))) {
					return;
				}
				Advapi32Util.registryCreateKey(WinReg.HKEY_CURRENT_USER, GPU_PREFERENCES_KEY);
				Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, GPU_PREFERENCES_KEY, exe, HIGH_PERFORMANCE);
				LOGGER.info("Windows graphics preference set to high performance for {}", exe);
			} else if (present && HIGH_PERFORMANCE.equals(Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, GPU_PREFERENCES_KEY, exe))) {
				Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, GPU_PREFERENCES_KEY, exe);
				LOGGER.info("Windows graphics preference for {} removed", exe);
			}
		} catch (RuntimeException | LinkageError e) {
			LOGGER.warn("Could not change the Windows graphics preference: {}", e.toString());
		}
	}

	/** Minecraft runs with javaw.exe; the preference must be set for the exact executable that opens the window. */
	static Path gameExecutable(Path javaExecutable) {
		String name = javaExecutable.getFileName().toString();
		if ("java.exe".equalsIgnoreCase(name)) {
			Path javaw = javaExecutable.resolveSibling("javaw.exe");
			return javaw.toFile().isFile() ? javaw : javaExecutable;
		}
		return javaExecutable;
	}
}
