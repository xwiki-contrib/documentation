/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.contrib.documentation.internal.xwikiorg;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link KebabNameValidator}.
 *
 * @version $Id$
 * @since 1.13
 */
class KebabNameValidatorTest
{
    @Test
    void isValidKebabWhenValid()
    {
        assertTrue(KebabNameValidator.isValidKebab("installation"));
        assertTrue(KebabNameValidator.isValidKebab("getting-started"));
        assertTrue(KebabNameValidator.isValidKebab("getting-started-123"));
        assertTrue(KebabNameValidator.isValidKebab("page123"));
        // Dots between digits are valid.
        assertTrue(KebabNameValidator.isValidKebab("version1.0"));
        assertTrue(KebabNameValidator.isValidKebab("release-1.2.3"));
    }

    @Test
    void isValidKebabWhenValidAndHoldingStopOrReservedWords()
    {
        // Stop words and documentation-type words are other rules: they do not make a name badly shaped.
        assertTrue(KebabNameValidator.isValidKebab("installation-of-xwiki"));
        assertTrue(KebabNameValidator.isValidKebab("getting-started-with-xwiki"));
        assertTrue(KebabNameValidator.isValidKebab("installation-tutorial"));
    }

    @Test
    void isValidKebabWhenNegationOrDirectionWord()
    {
        // These are valid kebab names and must stay valid: shortening them would invert their meaning.
        assertTrue(KebabNameValidator.isValidKebab("cannot-restore"));
        assertTrue(KebabNameValidator.isValidKebab("not-found"));
        assertTrue(KebabNameValidator.isValidKebab("before-upgrade"));
        assertTrue(KebabNameValidator.isValidKebab("after-upgrade"));
        assertTrue(KebabNameValidator.isValidKebab("move-up"));
        assertTrue(KebabNameValidator.isValidKebab("move-down"));
    }

    @Test
    void isValidKebabWhenInvalid()
    {
        // Uppercase letters.
        assertFalse(KebabNameValidator.isValidKebab("Installation"));
        assertFalse(KebabNameValidator.isValidKebab("GettingStarted"));
        // Spaces.
        assertFalse(KebabNameValidator.isValidKebab("getting started"));
        // Non-digit dot.
        assertFalse(KebabNameValidator.isValidKebab("getting.started"));
        // Underscore.
        assertFalse(KebabNameValidator.isValidKebab("getting_started"));
        // Leading/trailing hyphens.
        assertFalse(KebabNameValidator.isValidKebab("-installation"));
        assertFalse(KebabNameValidator.isValidKebab("installation-"));
        // Consecutive hyphens.
        assertFalse(KebabNameValidator.isValidKebab("getting--started"));
        // Empty name.
        assertFalse(KebabNameValidator.isValidKebab(""));
    }

    @Test
    void toKebabStripsAccents()
    {
        assertEquals("cafe", KebabNameValidator.toKebab("café"));
        assertEquals("resume", KebabNameValidator.toKebab("résumé"));
    }

    @Test
    void toKebabReplacesSpacesAndSpecialChars()
    {
        assertEquals("installation-guide", KebabNameValidator.toKebab("Installation Guide"));
        assertEquals("getting-started", KebabNameValidator.toKebab("getting.started"));
        assertEquals("getting-started", KebabNameValidator.toKebab("getting_started"));
    }

    @Test
    void toKebabConvertsToLowercase()
    {
        assertEquals("installation", KebabNameValidator.toKebab("INSTALLATION"));
        assertEquals("getting-started", KebabNameValidator.toKebab("GETTING-STARTED"));
    }

    @Test
    void toKebabCollapsesConsecutiveHyphens()
    {
        assertEquals("getting-started", KebabNameValidator.toKebab("getting--started"));
        assertEquals("getting-started", KebabNameValidator.toKebab("getting   started"));
    }

    @Test
    void toKebabRemovesLeadingAndTrailingHyphens()
    {
        assertEquals("installation", KebabNameValidator.toKebab("-installation-"));
        assertEquals("installation", KebabNameValidator.toKebab("--installation--"));
    }

    @Test
    void toKebabPreservesDotsOnlyBetweenDigits()
    {
        // Dot between two digits is preserved.
        assertEquals("version1.0", KebabNameValidator.toKebab("version1.0"));
        assertEquals("release-1.2.3", KebabNameValidator.toKebab("release-1.2.3"));
        // Dot NOT between two digits is replaced by a hyphen.
        assertEquals("getting-started", KebabNameValidator.toKebab("getting.started"));
        // First dot (letter-to-digit) is replaced; second dot (digit-to-digit) is preserved.
        assertEquals("version-1.0", KebabNameValidator.toKebab("version.1.0"));
    }

    @Test
    void toKebabKeepsStopAndReservedWords()
    {
        // toKebab is only about the shape of the name, so no word is dropped and no meaning is lost.
        assertEquals("the-xwiki", KebabNameValidator.toKebab("the-xwiki"));
        assertEquals("cannot-restore", KebabNameValidator.toKebab("Cannot Restore"));
        assertEquals("installation-tutorial", KebabNameValidator.toKebab("Installation Tutorial"));
    }

    @Test
    void toKebabIsAlwaysAValidKebabName()
    {
        for (String name : List.of("Installation Guide", "getting.started", "getting_started", "--installation--",
            "présentation", "version.1.0", "release-1.2.3", "abc1.2x", "1. 2")) {
            assertTrue(KebabNameValidator.isValidKebab(KebabNameValidator.toKebab(name)),
                String.format("[%s] normalised to [%s]", name, KebabNameValidator.toKebab(name)));
        }
    }

    @Test
    void getStopWordsWhenPresent()
    {
        assertEquals(List.of("of"), KebabNameValidator.getStopWords("installation-of-xwiki"));
        assertEquals(List.of("how", "to"), KebabNameValidator.getStopWords("how-to-install"));
        // The name is normalised to kebab first, and each stop word is reported once.
        assertEquals(List.of("the", "and"), KebabNameValidator.getStopWords("The Xwiki and The Rest"));
    }

    @Test
    void getStopWordsWhenAbsent()
    {
        assertEquals(List.of(), KebabNameValidator.getStopWords("installation-guide"));
        // Negations and direction words are not stop words: dropping them would change the meaning.
        assertEquals(List.of(), KebabNameValidator.getStopWords("cannot-restore"));
        assertEquals(List.of(), KebabNameValidator.getStopWords("not-found"));
        assertEquals(List.of(), KebabNameValidator.getStopWords("before-upgrade"));
    }

    @Test
    void stopWordsHoldNoMeaningBearingWord()
    {
        // Locks in the rule stated in the STOP_WORDS javadoc: no negation, no direction or relation word.
        for (String word : List.of("no", "not", "nor", "cannot", "cant", "dont", "isnt", "wont", "above", "below",
            "before", "after", "against", "between", "down", "up", "into", "out", "over", "under", "through",
            "off")) {
            assertFalse(KebabNameValidator.STOP_WORDS.contains(word), String.format("[%s] is a stop word", word));
        }
    }

    @Test
    void removeStopWords()
    {
        // Single stop word is removed, leaving just the meaningful word.
        assertEquals("xwiki", KebabNameValidator.removeStopWords("the-xwiki"));
        // Multiple consecutive stop words are all removed.
        assertEquals("installation-xwiki", KebabNameValidator.removeStopWords("installation-of-the-xwiki"));
        // Stop word in the middle is removed.
        assertEquals("installation-xwiki", KebabNameValidator.removeStopWords("installation-of-xwiki"));
        // All-stop-word name results in an empty string.
        assertEquals("", KebabNameValidator.removeStopWords("a-the-in"));
        // A name without stop words is unchanged, negations and direction words included.
        assertEquals("installation-guide", KebabNameValidator.removeStopWords("installation-guide"));
        assertEquals("cannot-restore", KebabNameValidator.removeStopWords("cannot-restore"));
    }

    @Test
    void containsReservedWordWhenPresent()
    {
        assertTrue(KebabNameValidator.containsReservedWord("installation-tutorial"));
        assertTrue(KebabNameValidator.containsReservedWord("xwiki-reference"));
        assertTrue(KebabNameValidator.containsReservedWord("howto-install"));
        assertTrue(KebabNameValidator.containsReservedWord("explanation-guide"));
        // Case-insensitive: the name is normalised to kebab before checking.
        assertTrue(KebabNameValidator.containsReservedWord("Installation-Tutorial"));
        assertTrue(KebabNameValidator.containsReservedWord("REFERENCE"));
    }

    @Test
    void containsReservedWordWhenAbsent()
    {
        assertFalse(KebabNameValidator.containsReservedWord("installation-guide"));
        assertFalse(KebabNameValidator.containsReservedWord("getting-started"));
        // "how" and "to" are stop words, not reserved words.
        assertFalse(KebabNameValidator.containsReservedWord("how-to-install"));
    }

    @Test
    void removeReservedWords()
    {
        // Single reserved word removed.
        assertEquals("installation", KebabNameValidator.removeReservedWords("installation-tutorial"));
        assertEquals("xwiki", KebabNameValidator.removeReservedWords("xwiki-reference"));
        assertEquals("install", KebabNameValidator.removeReservedWords("howto-install"));
        // Reserved word with uppercase input (normalised first, then stripped).
        assertEquals("installation", KebabNameValidator.removeReservedWords("Installation-Tutorial"));
        // Only the reserved word is removed — the stop word is a separate rule and stays.
        assertEquals("xwiki-of", KebabNameValidator.removeReservedWords("xwiki-reference-of"));
        // Name without reserved words is unchanged.
        assertEquals("installation-guide", KebabNameValidator.removeReservedWords("installation-guide"));
    }
}
