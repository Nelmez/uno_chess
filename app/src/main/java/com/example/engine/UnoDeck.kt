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
}
