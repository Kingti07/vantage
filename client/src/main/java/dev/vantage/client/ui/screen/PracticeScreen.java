package dev.vantage.client.ui.screen;

import dev.vantage.client.VantageClient;
import dev.vantage.client.config.ConfigManager;
import dev.vantage.client.render.Render2D;
import dev.vantage.client.render.Text;
import dev.vantage.client.ui.Theme;
import dev.vantage.client.ui.VScreen;
import dev.vantage.client.ui.widget.Button;
import dev.vantage.client.util.Colors;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Practice tools that run only inside this screen: a 10-second click-speed test and a reaction test, each with a
 * personal best. They never touch the game itself.
 */
public final class PracticeScreen extends VScreen {
	private static final long CPS_TEST_MS = 10_000;

	private enum ReactionState {IDLE, WAITING, GO, RESULT, TOO_EARLY}

	private long cpsStart;
	private int cpsClicks;
	private double cpsResult = -1;

	private ReactionState reaction = ReactionState.IDLE;
	private long reactionGoAt;
	private long reactionResult = -1;

	public PracticeScreen(Screen parent) {
		super("Practice", parent);
	}

	private ConfigManager config() {
		return VantageClient.get().config();
	}

	@Override
	protected void build() {
		add(new Button(width - 120, height - 28, 100, 18, () -> "Done", Button.Style.SECONDARY, this::onClose));
	}

	private int cardWidth() {
		return Math.min(260, (width - 60) / 2);
	}

	private int cardHeight() {
		return Math.min(170, height - 100);
	}

	private int cpsX() {
		return width / 2 - cardWidth() - 10;
	}

	private int reactionX() {
		return width / 2 + 10;
	}

	private int cardY() {
		return 56;
	}

	@Override
	protected boolean onClick(double mx, double my, int button) {
		long now = System.currentTimeMillis();
		if (Render2D.inside(mx, my, cpsX(), cardY(), cardWidth(), cardHeight())) {
			if (cpsStart == 0 || now - cpsStart > CPS_TEST_MS) {
				cpsStart = now;
				cpsClicks = 0;
				cpsResult = -1;
			}
			cpsClicks++;
			return true;
		}
		if (Render2D.inside(mx, my, reactionX(), cardY(), cardWidth(), cardHeight())) {
			switch (reaction) {
				case IDLE, RESULT, TOO_EARLY -> {
					reaction = ReactionState.WAITING;
					reactionGoAt = now + ThreadLocalRandom.current().nextLong(1500, 4000);
				}
				case WAITING -> reaction = ReactionState.TOO_EARLY;
				case GO -> {
					reactionResult = now - reactionGoAt;
					reaction = ReactionState.RESULT;
					double best = config().record("reactionMs");
					if (best <= 0 || reactionResult < best) {
						config().setRecord("reactionMs", reactionResult);
						toast("New personal best: " + reactionResult + " ms");
					}
				}
				default -> {
				}
			}
			return true;
		}
		return super.onClick(mx, my, button);
	}

	@Override
	public void tick() {
		super.tick();
		long now = System.currentTimeMillis();
		if (cpsStart > 0 && cpsResult < 0 && now - cpsStart >= CPS_TEST_MS) {
			cpsResult = cpsClicks / (CPS_TEST_MS / 1000.0);
			if (cpsResult > config().record("cps")) {
				config().setRecord("cps", cpsResult);
				toast(String.format(Locale.ROOT, "New personal best: %.1f CPS", cpsResult));
			}
		}
		if (reaction == ReactionState.WAITING && now >= reactionGoAt) {
			reaction = ReactionState.GO;
			reactionGoAt = now;
		}
	}

	@Override
	protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float delta) {
		header(g, 20, 12, "Practice", "Train your clicking and reactions · only inside this screen, never in a game");
		long now = System.currentTimeMillis();

		// CPS test
		int x = cpsX();
		int y = cardY();
		int w = cardWidth();
		int h = cardHeight();
		boolean running = cpsStart > 0 && cpsResult < 0;
		Render2D.panel(g, x, y, w, h, 8, running ? Colors.withAlpha(Theme.accent(), 60) : Theme.panel(), Theme.border());
		Text.draw(g, "CPS TEST", x + 10, y + 10, Theme.accent());
		String big;
		String small;
		if (running) {
			long left = Math.max(0, CPS_TEST_MS - (now - cpsStart));
			big = Integer.toString(cpsClicks);
			small = String.format(Locale.ROOT, "%.1f s left · keep clicking", left / 1000.0);
		} else if (cpsResult >= 0) {
			big = String.format(Locale.ROOT, "%.1f CPS", cpsResult);
			small = "Click to try again";
		} else {
			big = "Click here";
			small = "10 seconds, as fast as you can";
		}
		Text.drawScaled(g, big, x + (w - Text.width(big) * 2f) / 2f, y + h / 2f - 12, 2f, Theme.TEXT, false, Text.uiFace());
		Text.drawCentered(g, small, x + w / 2, y + h / 2 + 10, Theme.MUTED);
		Text.drawCentered(g, String.format(Locale.ROOT, "Best: %.1f CPS", config().record("cps")), x + w / 2, y + h - 16, Theme.FAINT);

		// Reaction test
		x = reactionX();
		int fill = switch (reaction) {
			case WAITING -> 0xC0A33A3A;
			case GO -> 0xC03DDC97;
			case TOO_EARLY -> 0xC0FFB547;
			default -> Theme.panel();
		};
		Render2D.panel(g, x, y, w, h, 8, fill, Theme.border());
		Text.draw(g, "REACTION TEST", x + 10, y + 10, Theme.accent());
		String title = switch (reaction) {
			case IDLE -> "Click to start";
			case WAITING -> "Wait for green…";
			case GO -> "CLICK!";
			case RESULT -> reactionResult + " ms";
			case TOO_EARLY -> "Too early!";
		};
		String hint = switch (reaction) {
			case IDLE -> "Click as soon as the box turns green";
			case WAITING -> "Do not click yet";
			case GO -> "Now!";
			default -> "Click to try again";
		};
		Text.drawScaled(g, title, x + (w - Text.width(title) * 2f) / 2f, y + h / 2f - 12, 2f, Theme.TEXT, false, Text.uiFace());
		Text.drawCentered(g, hint, x + w / 2, y + h / 2 + 10, Theme.MUTED);
		double best = config().record("reactionMs");
		Text.drawCentered(g, best > 0 ? "Best: " + Math.round(best) + " ms" : "Best: -", x + w / 2, y + h - 16, Theme.FAINT);
	}
}
