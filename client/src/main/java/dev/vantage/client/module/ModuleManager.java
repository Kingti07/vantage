package dev.vantage.client.module;

import dev.vantage.client.hud.HudModule;
import dev.vantage.client.keybind.Keybind;
import dev.vantage.client.keybind.KeybindManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Registry of all modules. Modules are registered once at start-up; lookups are by id or class. */
public final class ModuleManager {
	private final Map<String, Module> byId = new LinkedHashMap<>();
	private final Map<Class<?>, Module> byClass = new LinkedHashMap<>();
	private final KeybindManager keybinds;

	public ModuleManager(KeybindManager keybinds) {
		this.keybinds = keybinds;
	}

	public <M extends Module> M register(M module) {
		if (byId.containsKey(module.id())) {
			throw new IllegalStateException("Duplicate module id " + module.id());
		}
		module.ensureToggleKeybind();
		byId.put(module.id(), module);
		byClass.put(module.getClass(), module);
		for (Keybind keybind : module.keybinds()) {
			keybinds.register(keybind);
		}
		return module;
	}

	@SuppressWarnings("unchecked")
	public <M extends Module> M get(Class<M> type) {
		return (M) byClass.get(type);
	}

	public Module get(String id) {
		return byId.get(id);
	}

	public List<Module> all() {
		return Collections.unmodifiableList(new ArrayList<>(byId.values()));
	}

	public List<Module> byCategory(Category category) {
		return byId.values().stream().filter(m -> m.category() == category).toList();
	}

	public List<HudModule> hudModules() {
		return byId.values().stream().filter(m -> m instanceof HudModule).map(m -> (HudModule) m).toList();
	}

	public void tick() {
		for (Module module : byId.values()) {
			if (module.isEnabled()) {
				module.onTick();
			}
		}
	}
}
