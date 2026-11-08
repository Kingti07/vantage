package dev.vantage.client.ui.widget;

import dev.vantage.client.render.Render2D;
import dev.vantage.client.render.Text;
import dev.vantage.client.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/** Single-line text input with caret, clipboard support and a placeholder. */
public final class TextField extends Widget {
	private String text = "";
	private final String placeholder;
	private int caret;
	private boolean focused;
	private int maxLength = 128;
	private Consumer<String> onChange = s -> {
	};
	private Runnable onEnter = () -> {
	};

	public TextField(String placeholder) {
		super(0, 0, 160, 18);
		this.placeholder = placeholder;
	}

	public String text() {
		return text;
	}

	public TextField text(String value) {
		this.text = value == null ? "" : value;
		this.caret = this.text.length();
		return this;
	}

	public TextField onChange(Consumer<String> listener) {
		this.onChange = listener;
		return this;
	}

	public TextField onEnter(Runnable action) {
		this.onEnter = action;
		return this;
	}

	public TextField maxLength(int max) {
		this.maxLength = max;
		return this;
	}

	public void focus(boolean value) {
		this.focused = value;
	}

	public boolean isFocused() {
		return focused;
	}

	@Override
	public boolean capturesKeyboard() {
		return focused;
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		Render2D.rounded(g, x, y, width, height, 5, Theme.surface());
		Render2D.roundedBorder(g, x, y, width, height, 5, 1, focused ? Theme.accent() : Theme.border());
		int tx = x + 6;
		int ty = y + (height - 8) / 2;
		g.enableScissor(x + 2, y, x + width - 2, y + height);
		if (text.isEmpty() && !focused) {
			Text.draw(g, placeholder, tx, ty, Theme.FAINT);
		} else {
			int caretX = Text.width(text.substring(0, caret));
			int scroll = Math.max(0, caretX - (width - 14));
			Text.draw(g, text, tx - scroll, ty, Theme.TEXT);
			if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
				g.fill(tx - scroll + caretX, ty - 1, tx - scroll + caretX + 1, ty + 9, Theme.TEXT);
			}
		}
		g.disableScissor();
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		focused = isHovered(mx, my);
		if (focused) {
			int relative = (int) mx - (x + 6);
			caret = text.length();
			for (int i = 0; i <= text.length(); i++) {
				if (Text.width(text.substring(0, i)) >= relative) {
					caret = i;
					break;
				}
			}
		}
		return focused;
	}

	@Override
	public boolean keyPressed(int key, int scancode, int modifiers) {
		if (!focused) {
			return false;
		}
		boolean ctrl = (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
		switch (key) {
			case GLFW.GLFW_KEY_BACKSPACE -> {
				if (caret > 0) {
					set(text.substring(0, caret - 1) + text.substring(caret), caret - 1);
				}
			}
			case GLFW.GLFW_KEY_DELETE -> {
				if (caret < text.length()) {
					set(text.substring(0, caret) + text.substring(caret + 1), caret);
				}
			}
			case GLFW.GLFW_KEY_LEFT -> caret = Math.max(0, caret - 1);
			case GLFW.GLFW_KEY_RIGHT -> caret = Math.min(text.length(), caret + 1);
			case GLFW.GLFW_KEY_HOME -> caret = 0;
			case GLFW.GLFW_KEY_END -> caret = text.length();
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> onEnter.run();
			case GLFW.GLFW_KEY_ESCAPE -> focused = false;
			case GLFW.GLFW_KEY_V -> {
				if (ctrl) {
					insert(Minecraft.getInstance().keyboardHandler.getClipboard());
				}
			}
			case GLFW.GLFW_KEY_C -> {
				if (ctrl) {
					Minecraft.getInstance().keyboardHandler.setClipboard(text);
				}
			}
			case GLFW.GLFW_KEY_A -> {
				if (ctrl) {
					caret = text.length();
				}
			}
			default -> {
				return key != GLFW.GLFW_KEY_ESCAPE;
			}
		}
		return true;
	}

	@Override
	public boolean charTyped(String typed) {
		if (!focused) {
			return false;
		}
		insert(typed);
		return true;
	}

	private void insert(String value) {
		String clean = value.replaceAll("[\\p{Cntrl}]", "");
		if (clean.isEmpty()) {
			return;
		}
		String next = text.substring(0, caret) + clean + text.substring(caret);
		if (next.length() <= maxLength) {
			set(next, caret + clean.length());
		}
	}

	private void set(String value, int newCaret) {
		text = value;
		caret = Math.max(0, Math.min(value.length(), newCaret));
		onChange.accept(text);
	}
}
