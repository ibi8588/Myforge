package forge.game.ability.effects;

import java.util.Map;

import forge.game.Game;
import forge.game.ability.AbilityKey;
import forge.game.ability.SpellAbilityEffect;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardFactory;
import forge.game.card.CardZoneTable;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.util.Lang;

/**
 * Combine two or more cards into one permanent.
 *
 * "Combine two cards (Its power is equal to their total power, its toughness is equal to
 * their total toughness, and it has their names, mana costs, types, text boxes, etc.)"
 *
 * Uses the same merged-permanent machinery as mutate, so when the combined permanent
 * leaves the battlefield each card goes to the new zone separately.
 *
 * Parameters:
 *   Defined$   the cards to combine (e.g. Remembered, Targeted). Cards may be on the
 *              battlefield already or in another zone (they're put onto the battlefield).
 *   Tapped$    True to put the combined permanent onto the battlefield tapped.
 */
public class CombineEffect extends SpellAbilityEffect {

    @Override
    protected String getStackDescription(SpellAbility sa) {
        final CardCollection cards = getDefinedCardsOrTargeted(sa);
        return "Combine " + Lang.joinHomogenous(cards) + ".";
    }

    @Override
    public void resolve(SpellAbility sa) {
        final Card source = sa.getHostCard();
        final Game game = source.getGame();
        final Player activator = sa.getActivatingPlayer();

        final CardCollection cards = new CardCollection();
        for (final Card c : getDefinedCardsOrTargeted(sa)) {
            final Card gameCard = game.getCardState(c, null);
            // only permanent cards can be combined; skip cards already merged into something else
            if (gameCard == null || !gameCard.isPermanent()
                    || gameCard.isMerged() || cards.contains(gameCard)) {
                continue;
            }
            cards.add(gameCard);
        }
        if (cards.isEmpty()) {
            return;
        }

        final CardZoneTable triggerList = CardZoneTable.getSimultaneousInstance(sa);
        final Map<AbilityKey, Object> moveParams = AbilityKey.newMap();
        AbilityKey.addCardZoneTableParams(moveParams, triggerList);

        // Prefer a card already on the battlefield as the base so it doesn't re-enter.
        Card base = null;
        for (final Card c : cards) {
            if (c.isInPlay()) {
                base = c;
                break;
            }
        }
        final boolean baseInPlay = base != null;
        if (base == null) {
            base = cards.size() == 1 ? cards.getFirst()
                    : activator.getController().chooseSingleEntityForEffect(cards, sa,
                    "Choose the card shown on top of the combined permanent", false, null);
        }

        if (cards.size() > 1) {
            if (!base.hasMergedCard()) {
                base.addMergedCard(base);
            }
            for (final Card c : cards) {
                if (c == base) {
                    continue;
                }
                c.setMergedToCard(base);
                base.addMergedCard(c);
                // keep any cards merged into this one (e.g. a mutated pile) together
                if (c.hasMergedCard()) {
                    for (final Card inner : c.getMergedCards()) {
                        if (inner != c && !base.getMergedCards().contains(inner)) {
                            inner.setMergedToCard(base);
                            base.addMergedCard(inner);
                        }
                    }
                    c.removeMutatedStates();
                    c.clearMergedCards();
                    c.setCombined(false);
                }
            }
            base.setCombined(true);

            // build the combined characteristics before anything enters, so
            // "when this enters" abilities from every card see the combined permanent
            base.removeMutatedStates();
            final long ts = game.getNextTimestamp();
            base.setMutatedTimestamp(ts);
            base.addCloneState(CardFactory.getMutatedCloneStates(base, sa), ts);

            for (final Card c : base.getMergedCards()) {
                if (c == base || c.isInZone(ZoneType.Merged)) {
                    continue;
                }
                game.getAction().moveTo(c.getOwner().getZone(ZoneType.Merged), c, sa);
                c.setTapped(base.isTapped());
            }
        }

        if (!baseInPlay) {
            if (sa.hasParam("Tapped")) {
                base.setTapped(true);
            }
            base = game.getAction().moveToPlay(base, activator, sa, moveParams);
        } else {
            // re-register triggers now that the permanent has new text
            game.getTriggerHandler().clearActiveTriggers(base, null);
            game.getTriggerHandler().registerActiveTrigger(base, false);
        }

        for (final Card c : base.getMergedCards()) {
            if (c != base) {
                c.setTapped(base.isTapped());
            }
        }
        base.updateStateForView();
        base.updateTokenView();

        if (sa.hasParam("RememberCombined")) {
            source.addRemembered(base);
        }

        triggerList.triggerChangesZoneAll(game, sa);
    }
}
