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
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;

import com.xpn.xwiki.doc.XWikiDocument;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PageTitleCheck}.
 *
 * @version $Id$
 * @since 1.14
 */
@ComponentTest
class PageTitleCheckTest
{
    @InjectMockComponents
    private PageTitleCheck check;

    private XWikiDocument documentWithTitle(String title)
    {
        XWikiDocument document = mock(XWikiDocument.class);
        when(document.getTitle()).thenReturn(title);
        return document;
    }

    @Test
    void checkWhenTitleIsValid()
    {
        assertEquals(0, this.check.check(documentWithTitle("Getting Started")).size());
    }

    @Test
    void checkWhenTitleIsEmpty()
    {
        List<DocumentationViolation> violations = this.check.check(documentWithTitle(""));

        assertEquals(1, violations.size());
        assertEquals("Page title must not be empty.", violations.get(0).getViolationMessage());
        assertEquals("Page title: []", violations.get(0).getViolationContext());
        assertEquals(DocumentationViolationSeverity.ERROR, violations.get(0).getViolationSeverity());
    }

    @Test
    void checkWhenTitleIsNull()
    {
        List<DocumentationViolation> violations = this.check.check(documentWithTitle(null));

        assertEquals(1, violations.size());
        assertEquals("Page title must not be empty.", violations.get(0).getViolationMessage());
        assertEquals(DocumentationViolationSeverity.ERROR, violations.get(0).getViolationSeverity());
    }

    @Test
    void checkWhenTitleContainsReservedWord()
    {
        List<DocumentationViolation> violations = this.check.check(documentWithTitle("Getting Started Tutorial"));

        assertEquals(1, violations.size());
        assertEquals("Page title must not contain documentation-type words "
            + "(explanation, howto, reference, tutorial).",
            violations.get(0).getViolationMessage());
        assertEquals("Page title: [Getting Started Tutorial]", violations.get(0).getViolationContext());
        assertEquals(DocumentationViolationSeverity.ERROR, violations.get(0).getViolationSeverity());
    }

    @Test
    void checkWhenTitleContainsReservedWordHowTo()
    {
        List<DocumentationViolation> violations = this.check.check(documentWithTitle("HowTo Install XWiki"));

        assertEquals(1, violations.size());
        assertEquals(DocumentationViolationSeverity.ERROR, violations.get(0).getViolationSeverity());
    }

    @Test
    void checkWhenTitleContainsReservedWordCaseInsensitive()
    {
        List<DocumentationViolation> violations = this.check.check(documentWithTitle("TUTORIAL on XWiki"));

        assertEquals(1, violations.size());
        assertEquals(DocumentationViolationSeverity.ERROR, violations.get(0).getViolationSeverity());
    }

    @Test
    void checkWhenTitleContainsReservedWordReference()
    {
        List<DocumentationViolation> violations = this.check.check(documentWithTitle("API Reference"));

        assertEquals(1, violations.size());
        assertEquals("Page title: [API Reference]", violations.get(0).getViolationContext());
    }
}
