package com.garan.threshold.core.friction

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class TypedIntentionSessionTest {

    @Test
    fun blankPhrase_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException::class.java) {
            TypedIntentionSession("")
        }
        assertThrows(IllegalArgumentException::class.java) {
            TypedIntentionSession("   ")
        }
    }

    @Test
    fun typingMatchingCharacters_advancesPrefixAndCompletes() {
        val session = TypedIntentionSession("I need to focus")

        assertThat(session.matchedCount).isEqualTo(0)
        assertThat(session.isComplete).isFalse()
        assertThat(session.matchedText).isEmpty()
        assertThat(session.remainingText).isEqualTo("I need to focus")

        // 1. Correct letter 'I'
        val res1 = session.typeChar('I')
        assertThat(res1).isEqualTo(TypedIntentionSession.MatchResult.Matched)
        assertThat(session.matchedCount).isEqualTo(1)
        assertThat(session.matchedText).isEqualTo("I")
        assertThat(session.remainingText).isEqualTo(" need to focus")

        // 2. Case-insensitivity: typing lowercase ' ' and 'n'
        session.typeChar(' ')
        val res3 = session.typeChar('N') // target is lowercase 'n'
        assertThat(res3).isEqualTo(TypedIntentionSession.MatchResult.Matched)
        assertThat(session.matchedCount).isEqualTo(3)
        assertThat(session.matchedText).isEqualTo("I n")
    }

    @Test
    fun lockTumbler_rejectsMismatchedCharacterWithoutAdvancing() {
        val session = TypedIntentionSession("Target")

        // First character is 'T'. User types 'X'.
        val result = session.typeChar('X')
        assertThat(result).isEqualTo(TypedIntentionSession.MatchResult.Mismatch)
        assertThat(session.matchedCount).isEqualTo(0)
        assertThat(session.matchedText).isEmpty()

        // Typing correct 't' (lowercase) succeeds
        val matchResult = session.typeChar('t')
        assertThat(matchResult).isEqualTo(TypedIntentionSession.MatchResult.Matched)
        assertThat(session.matchedCount).isEqualTo(1)

        // Second char is 'a'. User types 'b'.
        val mismatch2 = session.typeChar('b')
        assertThat(mismatch2).isEqualTo(TypedIntentionSession.MatchResult.Mismatch)
        assertThat(session.matchedCount).isEqualTo(1)
        assertThat(session.matchedText).isEqualTo("T")
    }

    @Test
    fun completingEntirePhrase_returnsCompletedResult() {
        val session = TypedIntentionSession("Do it")

        session.typeChar('d')
        session.typeChar('o')
        session.typeChar(' ')
        session.typeChar('i')
        val lastResult = session.typeChar('t')

        assertThat(lastResult).isEqualTo(TypedIntentionSession.MatchResult.Completed)
        assertThat(session.isComplete).isTrue()
        assertThat(session.matchedText).isEqualTo("Do it")
        assertThat(session.remainingText).isEmpty()

        // Additional typing once complete returns Completed without error
        assertThat(session.typeChar('x')).isEqualTo(TypedIntentionSession.MatchResult.Completed)
    }

    @Test
    fun spaceDoubleTapSanitization_acceptsPeriodWhenSpaceExpected() {
        val session = TypedIntentionSession("A B")

        session.typeChar('a')
        // Next expected char is space ' '. Android keyboard sent period '.' on double space.
        val spaceResult = session.typeChar('.')
        assertThat(spaceResult).isEqualTo(TypedIntentionSession.MatchResult.Matched)
        assertThat(session.matchedText).isEqualTo("A ")
    }

    @Test
    fun backspace_decrementsMatchedCountUntilZero() {
        val session = TypedIntentionSession("Focus")

        session.typeChar('f')
        session.typeChar('o')
        assertThat(session.matchedCount).isEqualTo(2)

        assertThat(session.backspace()).isTrue()
        assertThat(session.matchedCount).isEqualTo(1)
        assertThat(session.matchedText).isEqualTo("F")

        assertThat(session.backspace()).isTrue()
        assertThat(session.matchedCount).isEqualTo(0)

        // Backspacing at index 0 returns false
        assertThat(session.backspace()).isFalse()
        assertThat(session.matchedCount).isEqualTo(0)
    }
}
