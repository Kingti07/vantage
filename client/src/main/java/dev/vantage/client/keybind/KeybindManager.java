package dev.vantage.client.keybind;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Central registry for every Vantage keybind. Receives raw key/mouse events from mixins, dispatches them to
 * matching binds, detects conflicts with other Vantage binds and with vanilla/mod key mappings, and persists
 * all bindings in the active profile.
 */
public final class KeybindManager {
	private final Map<String, Keybind> binds = new LinkedHashMap<>();
	/** When set, the next key or mouse press is captured for the keybind editor instead of being dispatched. */
	private java.util.function.Consumer<InputKey> captureListener;

	public Keybind register(Keybind keybind) {
		binds.put(keybind.id(), keybind);
		return keybind;
	}

	public Collection<Keybind> all() {
		return binds.values();
	}

	public Keybind get(String id) {
		return binds.get(id);
	}

	public void capture(java.util.function.Consumer<InputKey> listener) {
		this.captureListener = listener;
	}

	public boolean isCapturing() {
		return captureListener != null;
	}

	/** Raw keyboard event. Returns true when the event was consumed by the keybind editor. */
	public boolean onKey(int key, int action) {
		if (key == GLFW.GLFW_KEY_UNKNOWN) {
			return false;
		}
		return dispatch(InputKey.key(key), action);
	}

	public boolean onMouse(int button, int action) {
		return dispatch(InputKey.mouse(button), action);
	}

	private boolean dispatch(InputKey key, int action) {
		if (captureListener != null && action == GLFW.GLFW_PRESS) {
			var listener = captureListener;
			captureListener = null;
			listener.accept(key);
			return true;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.screen != null || mc.getOverlay() != null) {
			return false;
		}
		if (action == GLFW.GLFW_REPEAT) {
			return false;
		}
		for (Keybind bind : binds.values()) {
			if (bind.matches(key)) {
				if (action == GLFW.GLFW_PRESS) {
					bind.firePress();
				} else if (action == GLFW.GLFW_RELEASE) {
					bind.fireRelease();
				}
			}
		}
		return false;
	}

	/** Human readable list of everything else bound to {@code key}. */
	public List<String> conflicts(Keybind self, InputKey key) {
		List<String> result = new ArrayList<>();
		for (Keybind other : binds.values()) {
			if (other != self && other.matches(key)) {
				result.add(other.name());
			}
		}
		Minecraft mc = Minecraft.getInstance();
		for (KeyMapping mapping : mc.options.keyMappings) {
			if (!mapping.isUnbound() && KeyBindingHelper.getBoundKeyOf(mapping).equals(key.toVanilla())) {
				result.add(net.minecraft.network.chat.Component.translatable(mapping.getName()).getString() + " (Minecraft)");
			}
		}
		return result;
	}

	public boolean hasConflict(Keybind bind) {
		for (InputKey key : bind.keys()) {
			if (!conflicts(bind, key).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	public JsonObject save() {
		JsonObject json = new JsonObject();
		for (Keybind bind : binds.values()) {
			JsonArray keys = new JsonArray();
			bind.keys().forEach(k -> keys.add(k.serialize()));
			json.add(bind.id(), keys);
		}
		return json;
	}

	public void load(JsonObject json) {
		for (Keybind bind : binds.values()) {
			JsonElement element = json == null ? null : json.get(bind.id());
			if (element == null || !element.isJsonArray()) {
				bind.load(bind.defaults());
				continue;
			}
			List<InputKey> keys = new ArrayList<>();
			for (JsonElement entry : element.getAsJsonArray()) {
				InputKey parsed = InputKey.parse(entry.getAsString());
				if (parsed != null) {
					keys.add(parsed);
				}
			}
			bind.load(keys);
		}
	}
}
