package com.nanobuntv.retro64plus.client;

import com.nanobuntv.retro64plus.net.Payloads;
import com.nanobuntv.retro64plus.platform.Services;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * The Coin Shop's screen (D68): a list of offers like a villager's, each with its item, price in coins and the Power
 * Stars it needs (in red when you're short), your coins and stars at the top, and a Buy button for the selected offer.
 * The server checks and carries out each purchase; the counts here follow from its updates.
 */
public final class ShopScreen extends ModScreen {
    private static final int WIDTH = 248, HEIGHT = 206, ROW = 20, ROWS = 7, LIST_TOP = 28;
    private static final int PANEL = 0xF0201A14, BORDER = 0xFF8A6A3A, ROW_HOVER = 0x40FFFFFF, ROW_SELECTED = 0x60FFD34A;
    private static final int TEXT = 0xFFFFFFFF, SHORT = 0xFFFF5555, PRICE = 0xFFFFE04A;

    private final List<Payloads.Offer> offers;
    private int selected = -1, scroll;
    private Button buy;

    public ShopScreen(List<Payloads.Offer> offers) {
        super(Component.translatable("screen.retro64plus.shop"));
        this.offers = offers;
    }

    private int left() {
        return (width - WIDTH) / 2;
    }

    private int top() {
        return (height - HEIGHT) / 2;
    }

    @Override
    protected void init() {
        buy = addRenderableWidget(Button.builder(Component.translatable("screen.retro64plus.shop.buy"), b -> buy())
                .bounds(left() + WIDTH / 2 - 40, top() + HEIGHT - 28, 80, 20).build());
        updateBuy();
    }

    private void buy() {
        if (selected >= 0 && affordable(offers.get(selected))) {
            Services.NETWORK.sendToServer(new Payloads.ShopBuy(selected));
        }
    }

    private static boolean affordable(Payloads.Offer offer) {
        return ClientCoins.coins() >= offer.price() && ClientCoins.stars() >= offer.stars();
    }

    private void updateBuy() {
        if (buy != null) buy.active = selected >= 0 && selected < offers.size() && affordable(offers.get(selected));
    }

    @Override
    public void tick() {
        updateBuy(); // coins change as purchases go through
    }

    @Override
    protected void drawOver(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = left(), y = top();
        GuiDraw.text(graphics, font, title, x + 8, y + 9, TEXT, true);
        // Your coins and stars, right-aligned.
        String stars = Integer.toString(ClientCoins.stars()), coins = Integer.toString(ClientCoins.coins());
        int right = x + WIDTH - 8;
        right = amount(graphics, stars, CoinArt.GLYPH_STAR, right, y + 8, TEXT) - 8;
        amount(graphics, coins, CoinArt.GLYPH_COIN, right, y + 8, TEXT);

        int listLeft = x + 6, listRight = x + WIDTH - 6;
        Payloads.Offer hovered = null;
        if (offers.isEmpty()) {
            GuiDraw.centered(graphics, font, Component.translatable("screen.retro64plus.shop.empty"), x + WIDTH / 2,
                    y + LIST_TOP + 40, TEXT);
        }
        for (int row = 0; row < ROWS && scroll + row < offers.size(); row++) {
            int i = scroll + row;
            Payloads.Offer offer = offers.get(i);
            int rowTop = y + LIST_TOP + row * ROW;
            boolean hover = mouseX >= listLeft && mouseX < listRight && mouseY >= rowTop && mouseY < rowTop + ROW;
            if (i == selected) graphics.fill(listLeft, rowTop, listRight, rowTop + ROW, ROW_SELECTED);
            else if (hover) graphics.fill(listLeft, rowTop, listRight, rowTop + ROW, ROW_HOVER);
            GuiDraw.item(graphics, font, offer.stack(), listLeft + 2, rowTop + 2);
            GuiDraw.text(graphics, font, offer.stack().getHoverName(), listLeft + 22, rowTop + 6, TEXT, true);
            int priceRight = amount(graphics, Integer.toString(offer.price()), CoinArt.GLYPH_COIN, listRight - 4,
                    rowTop + 6, ClientCoins.coins() >= offer.price() ? PRICE : SHORT);
            if (offer.stars() > 0) {
                amount(graphics, Integer.toString(offer.stars()), CoinArt.GLYPH_STAR, priceRight - 8, rowTop + 6,
                        ClientCoins.stars() >= offer.stars() ? TEXT : SHORT);
            }
            if (hover) hovered = offer;
        }
        if (offers.size() > ROWS) {
            // A scroll bar.
            int barTop = y + LIST_TOP, barHeight = ROWS * ROW;
            int thumb = Math.max(12, barHeight * ROWS / offers.size());
            int thumbTop = barTop + (barHeight - thumb) * scroll / (offers.size() - ROWS);
            graphics.fill(x + WIDTH - 5, thumbTop, x + WIDTH - 3, thumbTop + thumb, BORDER);
        }
        if (hovered != null) GuiDraw.itemTooltip(graphics, font, hovered.stack(), mouseX, mouseY);
    }

    /** Draws "icon number" ending at {@code right}; returns where it starts. */
    private int amount(GuiGraphics graphics, String number, int glyph, int right, int y, int color) {
        int textWidth = font.width(number);
        int start = right - textWidth - 11;
        ResourceLocation texture = CoinArt.texture();
        GuiDraw.push(graphics);
        GuiDraw.translate(graphics, start, y - 1);
        GuiDraw.scale(graphics, 9 / 16f, 9 / 16f);
        GuiDraw.blit(graphics, texture, 0, 0, 16 * glyph, CoinArt.GLYPH_Y, 16, 16, CoinArt.WIDTH, CoinArt.HEIGHT);
        GuiDraw.pop(graphics);
        GuiDraw.text(graphics, font, number, right - textWidth, y, color);
        return start;
    }

    @Override
    protected void drawBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = left(), y = top();
        graphics.fill(x - 1, y - 1, x + WIDTH + 1, y + HEIGHT + 1, BORDER);
        graphics.fill(x, y, x + WIDTH, y + HEIGHT, PANEL);
        graphics.fill(x + 6, y + LIST_TOP - 3, x + WIDTH - 6, y + LIST_TOP - 2, BORDER);
    }

    @Override
    protected boolean click(double mouseX, double mouseY, int button) {
        int listLeft = left() + 6, listRight = left() + WIDTH - 6, listTop = top() + LIST_TOP;
        if (button == 0 && mouseX >= listLeft && mouseX < listRight && mouseY >= listTop
                && mouseY < listTop + ROWS * ROW) {
            int i = scroll + (int) ((mouseY - listTop) / ROW);
            if (i < offers.size()) {
                selected = i;
                updateBuy();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, Math.max(0, offers.size() - ROWS));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** For dev runs: selects an offer and buys it, as the clicks would. */
    void devBuy(int index) {
        selected = index;
        updateBuy();
        buy();
    }
}
