package com.example.engine

import com.example.model.SpecialCardKey
import com.example.model.UnoCard
import com.example.model.UnoCardType
import com.example.model.UnoColor
import kotlin.random.Random

object UnoDeck {

    const val NUMBER_CARDS_COUNT = 16

    fun deckSizeFor(enabledSpecials: Set<SpecialCardKey>): Int {
        return NUMBER_CARDS_COUNT + enabledSpecials.sumOf { it.deckCopies }
    }

    /**
     * Creates the balanced 38-card UNO Chess deck (or a custom subset based on [enabledSpecials]):
     * - Number Cards (1, 2, 3, 4): 4 copies each (Red, Blue, Green, Yellow) = 16 (always enabled)
     * - +1 Revive (Transform 1 pawn into captured piece / spawn pawn): 3 copies
     * - Freeze (Lock 1 enemy non-King piece + 1 move): 2 copies
     * - Shield (Protect 1 own non-King piece from capture + 1 move): 2 copies
     * - Merge (Merge or swap own pieces by material value): 2 copies
     * - Sacrifice (Split 1 own piece into equal-value pieces): 2 copies
     * - Swap Positions (Swap 2 own pieces safely): 2 copies
     * - Clone (Duplicate 1 own Pawn, Knight, or Bishop): 2 copies
     * - Hijack (Move 1 opponent piece): 2 copies
     * - Promote (Upgrade 1 own pawn to Knight or Bishop): 2 copies
     * - Suicide (Eliminate 1 own piece; King only = immediate loss): 2 copies
     * - Wild Card (Choose any enabled playable special card effect): 1 copy
     */
    fun createShuffledDeck(
        random: Random = Random.Default,
        enabledSpecials: Set<SpecialCardKey> = SpecialCardKey.entries.toSet()
    ): List<UnoCard> {
        var nextId = 1
        val cards = mutableListOf<UnoCard>()
        val allColors = UnoColor.entries

        // 4 copies each of 1, 2, 3, 4 styled across Red, Blue, Green, Yellow
        for (moves in 1..4) {
            for (color in allColors) {
                cards.add(
                    UnoCard(
                        id = nextId++,
                        color = color,
                        type = UnoCardType.NumberCard(moves)
                    )
                )
            }
        }

        if (SpecialCardKey.REVIVE in enabledSpecials) {
            listOf(UnoColor.RED, UnoColor.GREEN, UnoColor.BLUE).forEach { color ->
                cards.add(UnoCard(id = nextId++, color = color, type = UnoCardType.RevivePlusTwo))
            }
        }

        if (SpecialCardKey.FREEZE in enabledSpecials) {
            listOf(UnoColor.BLUE, UnoColor.YELLOW).forEach { color ->
                cards.add(UnoCard(id = nextId++, color = color, type = UnoCardType.Freeze))
            }
        }

        if (SpecialCardKey.SHIELD in enabledSpecials) {
            listOf(UnoColor.BLUE, UnoColor.GREEN).forEach { color ->
                cards.add(UnoCard(id = nextId++, color = color, type = UnoCardType.Shield))
            }
        }

        if (SpecialCardKey.MERGE in enabledSpecials) {
            listOf(UnoColor.GREEN, UnoColor.YELLOW).forEach { color ->
                cards.add(UnoCard(id = nextId++, color = color, type = UnoCardType.Merge))
            }
        }

        if (SpecialCardKey.SACRIFICE in enabledSpecials) {
            listOf(UnoColor.RED, UnoColor.BLUE).forEach { color ->
                cards.add(UnoCard(id = nextId++, color = color, type = UnoCardType.Sacrifice))
            }
        }

        if (SpecialCardKey.SWAP in enabledSpecials) {
            listOf(UnoColor.YELLOW, UnoColor.BLUE).forEach { color ->
                cards.add(UnoCard(id = nextId++, color = color, type = UnoCardType.SwapPositions))
            }
        }

        if (SpecialCardKey.CLONE in enabledSpecials) {
            listOf(UnoColor.GREEN, UnoColor.RED).forEach { color ->
                cards.add(UnoCard(id = nextId++, color = color, type = UnoCardType.Clone))
            }
        }

        if (SpecialCardKey.HIJACK in enabledSpecials) {
            listOf(UnoColor.RED, UnoColor.BLUE).forEach { color ->
                cards.add(UnoCard(id = nextId++, color = color, type = UnoCardType.Hijack))
            }
        }

        if (SpecialCardKey.PROMOTE in enabledSpecials) {
            listOf(UnoColor.YELLOW, UnoColor.GREEN).forEach { color ->
                cards.add(UnoCard(id = nextId++, color = color, type = UnoCardType.PawnPromote))
            }
        }

        if (SpecialCardKey.SUICIDE in enabledSpecials) {
            listOf(UnoColor.RED, UnoColor.YELLOW).forEach { color ->
                cards.add(UnoCard(id = nextId++, color = color, type = UnoCardType.Suicide))
            }
        }

        if (SpecialCardKey.WILD in enabledSpecials) {
            cards.add(UnoCard(id = nextId++, color = UnoColor.YELLOW, type = UnoCardType.Wild))
        }

        return cards.shuffled(random)
    }

    data class PityChances(
        val oneMovePercent: Int,
        val twoMovesPercent: Int,
        val threeMovesPercent: Int,
        val fourMovesPercent: Int,
        val naturalDrawPercent: Int
    ) {
        val totalMovePercent: Int
            get() = oneMovePercent + twoMovesPercent + threeMovesPercent + fourMovesPercent
    }

    /**
     * Card draw pity system:
     * When a player draws non-move cards in consecutive turns, their chance of drawing
     * a move card (NumberCard) progressively escalates:
     * - Pity 0: Natural deck draw (standard ~42% move cards in a fresh 38-card deck).
     * - Pity 1 (1 non-move card): 2-move chance boosted (25%), total move chance boosted to ~74%.
     * - Pity 2 (e.g. Merge & Revive): 2-move chance is 40% (user requirement), total move chance ~94%.
     * - Pity 3 (3 non-move cards): 3-move (40%) and 4-move (35%) cards dominate (75% combined, 100% move guaranteed).
     * - Pity 4+: 4-move (50%) and 3-move (40%) cards dominate (90% combined, 100% move guaranteed).
     */
    fun getPityChances(pityCount: Int): PityChances = when {
        pityCount <= 0 -> PityChances(
            oneMovePercent = 0,
            twoMovesPercent = 0,
            threeMovesPercent = 0,
            fourMovesPercent = 0,
            naturalDrawPercent = 100
        )
        pityCount == 1 -> PityChances(
            oneMovePercent = 15,
            twoMovesPercent = 25,
            threeMovesPercent = 10,
            fourMovesPercent = 5,
            naturalDrawPercent = 45
        )
        pityCount == 2 -> PityChances(
            oneMovePercent = 10,
            twoMovesPercent = 40,
            threeMovesPercent = 25,
            fourMovesPercent = 15,
            naturalDrawPercent = 10
        )
        pityCount == 3 -> PityChances(
            oneMovePercent = 5,
            twoMovesPercent = 20,
            threeMovesPercent = 40,
            fourMovesPercent = 35,
            naturalDrawPercent = 0
        )
        else -> PityChances(
            oneMovePercent = 0,
            twoMovesPercent = 10,
            threeMovesPercent = 40,
            fourMovesPercent = 50,
            naturalDrawPercent = 0
        )
    }

    /**
     * Rolls whether a pity-boosted move card should be selected, and returns the target move count (1..4)
     * or null if the draw should proceed as a natural deck draw.
     */
    fun rollPityTarget(pityCount: Int, random: Random = Random.Default): Int? {
        if (pityCount <= 0) return null
        val chances = getPityChances(pityCount)
        if (chances.naturalDrawPercent >= 100) return null

        val roll = random.nextInt(100) // 0..99
        var cumulative = 0

        if (pityCount >= 4) {
            cumulative += chances.fourMovesPercent
            if (roll < cumulative) return 4

            cumulative += chances.threeMovesPercent
            if (roll < cumulative) return 3

            cumulative += chances.twoMovesPercent
            if (roll < cumulative) return 2

            cumulative += chances.oneMovePercent
            if (roll < cumulative) return 1
        } else if (pityCount == 3) {
            cumulative += chances.threeMovesPercent
            if (roll < cumulative) return 3

            cumulative += chances.fourMovesPercent
            if (roll < cumulative) return 4

            cumulative += chances.twoMovesPercent
            if (roll < cumulative) return 2

            cumulative += chances.oneMovePercent
            if (roll < cumulative) return 1
        } else {
            cumulative += chances.twoMovesPercent
            if (roll < cumulative) return 2

            cumulative += chances.threeMovesPercent
            if (roll < cumulative) return 3

            cumulative += chances.fourMovesPercent
            if (roll < cumulative) return 4

            cumulative += chances.oneMovePercent
            if (roll < cumulative) return 1
        }

        return null
    }

    /**
     * Draws a card from [drawPile] respecting the player's [pityCount].
     * If pity rolls a target move count, extracts that card from [drawPile] (or reshuffles [discardPile]
     * if the card is only in the discard pile). This guarantees cards are never duplicated or synthesized
     * out of thin air, preserving deck conservation.
     */
    fun drawCardWithPity(
        drawPile: MutableList<UnoCard>,
        discardPile: MutableList<UnoCard>,
        pityCount: Int,
        enabledSpecials: Set<SpecialCardKey>,
        random: Random = Random.Default
    ): UnoCard {
        if (drawPile.isEmpty()) {
            if (discardPile.isEmpty()) {
                drawPile.addAll(createShuffledDeck(random, enabledSpecials))
            } else {
                drawPile.addAll(discardPile.shuffled(random))
                discardPile.clear()
            }
        }

        val targetMoves = rollPityTarget(pityCount, random)
        if (targetMoves != null) {
            val targetIndex = drawPile.indexOfFirst {
                val type = it.type
                type is UnoCardType.NumberCard && type.moves == targetMoves
            }
            if (targetIndex != -1) {
                return drawPile.removeAt(targetIndex)
            }
            val discardTargetIndex = discardPile.indexOfFirst {
                val type = it.type
                type is UnoCardType.NumberCard && type.moves == targetMoves
            }
            if (discardTargetIndex != -1) {
                drawPile.addAll(discardPile.shuffled(random))
                discardPile.clear()
                val reshuffledIndex = drawPile.indexOfFirst {
                    val type = it.type
                    type is UnoCardType.NumberCard && type.moves == targetMoves
                }
                if (reshuffledIndex != -1) {
                    return drawPile.removeAt(reshuffledIndex)
                }
            }
            val fallbackIndex = drawPile.indexOfFirst { it.type is UnoCardType.NumberCard }
            if (fallbackIndex != -1) {
                return drawPile.removeAt(fallbackIndex)
            }
            if (discardPile.any { it.type is UnoCardType.NumberCard }) {
                drawPile.addAll(discardPile.shuffled(random))
                discardPile.clear()
                val reshuffledFallback = drawPile.indexOfFirst { it.type is UnoCardType.NumberCard }
                if (reshuffledFallback != -1) {
                    return drawPile.removeAt(reshuffledFallback)
                }
            }
        }

        return drawPile.removeAt(0)
    }
}
