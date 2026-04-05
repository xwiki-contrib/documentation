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
    void isValidKebabWhenInvalid()
    {
        // Uppercase letters.
        assertFalse(KebabNameValidator.isValidKebab("Installation"));
        assertFalse(KebabNameValidator.isValidKebab("GettingStarted"));
        // Spaces.
        assertFalse(KebabNameValidator.isValidKebab("getting started"));
        // Non-digit dot.
        assertFalse(KebabNameValidator.isValidKebab("getting.started"));
        // Leading/trailing hyphens.
        assertFalse(KebabNameValidator.isValidKebab("-installation"));
        assertFalse(KebabNameValidator.isValidKebab("installation-"));
        // Consecutive hyphens.
        assertFalse(KebabNameValidator.isValidKebab("getting--started"));
        // Contains a stop word segment.
        assertFalse(KebabNameValidator.isValidKebab("installation-of-xwiki"));
        assertFalse(KebabNameValidator.isValidKebab("getting-started-with-xwiki"));
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
    void toKebabRemovesStopWords()
    {
        // Single stop word is removed, leaving just the meaningful word.
        assertEquals("xwiki", KebabNameValidator.toKebab("the-xwiki"));
        // Multiple consecutive stop words are all removed.
        assertEquals("installation-xwiki", KebabNameValidator.toKebab("installation-of-the-xwiki"));
        // Stop word in the middle is removed.
        assertEquals("installation-xwiki", KebabNameValidator.toKebab("installation-of-xwiki"));
        // All-stop-word name results in an empty string.
        assertEquals("", KebabNameValidator.toKebab("a-the-in"));
    }
}
