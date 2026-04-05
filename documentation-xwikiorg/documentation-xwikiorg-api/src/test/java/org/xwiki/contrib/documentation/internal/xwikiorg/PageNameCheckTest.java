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
import org.xwiki.contrib.documentation.DocumentationViolation;
import org.xwiki.contrib.documentation.DocumentationViolationSeverity;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;

import com.xpn.xwiki.doc.XWikiDocument;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PageNameCheck}.
 *
 * @version $Id$
 * @since 1.13
 */
@ComponentTest
class PageNameCheckTest
{
    @InjectMockComponents
    private PageNameCheck check;

    private XWikiDocument documentWithPageName(String pageName)
    {
        XWikiDocument document = mock(XWikiDocument.class);
        when(document.getDocumentReference()).thenReturn(new DocumentReference("wiki", "space", pageName));
        return document;
    }

    @Test
    void checkWhenPageNameIsValidKebabCase()
    {
        assertEquals(0, this.check.check(documentWithPageName("getting-started")).size());
    }

    @Test
    void checkWhenPageNameIsSimpleLowercaseWord()
    {
        assertEquals(0, this.check.check(documentWithPageName("installation")).size());
    }

    @Test
    void checkWhenPageNameHasVersionNumber()
    {
        assertEquals(0, this.check.check(documentWithPageName("release-1.2.3")).size());
    }

    @Test
    void checkWhenPageNameHasSpaces()
    {
        List<DocumentationViolation> violations = this.check.check(documentWithPageName("Installation Guide"));

        assertEquals(1, violations.size());
        assertEquals("Page name must follow the kebab-case naming convention "
            + "(lowercase, hyphens instead of spaces or special characters).",
            violations.get(0).getViolationMessage());
        assertEquals("Page name: [Installation Guide], Expected: [installation-guide]",
            violations.get(0).getViolationContext());
        assertEquals(DocumentationViolationSeverity.ERROR, violations.get(0).getViolationSeverity());
    }

    @Test
    void checkWhenPageNameHasUppercase()
    {
        List<DocumentationViolation> violations = this.check.check(documentWithPageName("GettingStarted"));

        assertEquals(1, violations.size());
        assertEquals("Page name: [GettingStarted], Expected: [gettingstarted]",
            violations.get(0).getViolationContext());
    }

    @Test
    void checkWhenPageNameHasDot()
    {
        List<DocumentationViolation> violations = this.check.check(documentWithPageName("getting.started"));

        assertEquals(1, violations.size());
        assertEquals("Page name: [getting.started], Expected: [getting-started]",
            violations.get(0).getViolationContext());
    }

    @Test
    void checkWhenPageNameHasAccentedChars()
    {
        List<DocumentationViolation> violations = this.check.check(documentWithPageName("présentation"));

        assertEquals(1, violations.size());
        assertEquals("Page name: [présentation], Expected: [presentation]",
            violations.get(0).getViolationContext());
    }

    @Test
    void checkWhenPageNameContainsStopWord()
    {
        List<DocumentationViolation> violations = this.check.check(documentWithPageName("installation-of-xwiki"));

        assertEquals(1, violations.size());
        assertEquals("Page name: [installation-of-xwiki], Expected: [installation-xwiki]",
            violations.get(0).getViolationContext());
    }

    @Test
    void checkWhenPageNameContainsReservedWord()
    {
        List<DocumentationViolation> violations = this.check.check(documentWithPageName("installation-tutorial"));

        assertEquals(1, violations.size());
        assertEquals("Page name must not contain documentation-type words "
            + "(explanation, howto, reference, tutorial).",
            violations.get(0).getViolationMessage());
        assertEquals("Page name: [installation-tutorial], Expected: [installation]",
            violations.get(0).getViolationContext());
        assertEquals(DocumentationViolationSeverity.WARNING, violations.get(0).getViolationSeverity());
    }

    @Test
    void checkWhenPageNameIsReservedWordAndInvalidKebab()
    {
        // When the name is both invalid kebab and contains a reserved word, only ERROR is raised.
        List<DocumentationViolation> violations = this.check.check(documentWithPageName("Installation Tutorial"));

        assertEquals(1, violations.size());
        assertEquals(DocumentationViolationSeverity.ERROR, violations.get(0).getViolationSeverity());
        // Expected shows the fully clean name (reserved word "tutorial" also stripped).
        assertEquals("Page name: [Installation Tutorial], Expected: [installation]",
            violations.get(0).getViolationContext());
    }
}
