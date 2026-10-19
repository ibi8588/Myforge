package forge.game.staticability;

import forge.card.mana.ManaAtom;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

public class StaticAbilityDevotion {

    /**
     * Collects the devotion increases that apply to a player.
     * Each entry is {color mask, amount}. A static without Color$ applies to every color
     * (e.g. Altar of the Pantheon); with Color$ (e.g. "White,Blue") it only applies when the
     * devotion being counted includes at least one of those colors.
     */
    public static int[][] getDevotionMods(final Player player) {
        final java.util.List<int[]> mods = new java.util.ArrayList<>();
        final Game game = player.getGame();
        for (final Card ca : game.getCardsIn(ZoneType.STATIC_ABILITIES_SOURCE_ZONES)) {
            for (final StaticAbility stAb : ca.getStaticAbilities()) {
                if (!stAb.checkConditions(StaticAbilityMode.Devotion)) {
                    continue;
                }
                if (!stAb.matchesValidParam("ValidPlayer", player)) {
                    continue;
                }
                int value = Integer.parseInt(stAb.getParamOrDefault("Value", "1"));
                int mask = ManaAtom.ALL_MANA_COLORS;
                if (stAb.hasParam("Color")) {
                    mask = 0;
                    for (final String col : stAb.getParam("Color").split(",")) {
                        mask |= ManaAtom.fromName(col.trim());
                    }
                }
                mods.add(new int[] {mask, value});
            }
        }
        return mods.toArray(new int[0][]);
    }

    /** Total devotion increase regardless of color (kept for compatibility). */
    public static int getDevotionMod(final Player player) {
        int i = 0;
        for (final int[] mod : getDevotionMods(player)) {
            i += mod[1];
        }
        return i;
    }
}
