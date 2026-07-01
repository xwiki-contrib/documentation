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
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;

import com.xpn.xwiki.doc.XWikiDocument;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SyntaxCheck}.
 *
 * @version $Id$
 * @since 1.14
 */
@ComponentTest
class SyntaxCheckTest
{
    @InjectMockComponents
    private SyntaxCheck check;

    private XWikiDocument documentWithSyntax(Syntax syntax)
    {
        XWikiDocument document = mock(XWikiDocument.class);
        when(document.getSyntax()).thenReturn(syntax);
        return document;
    }

    @Test
    void checkWhenSyntaxIsXWiki21()
    {
        assertEquals(0, this.check.check(documentWithSyntax(Syntax.XWIKI_2_1)).size());
    }

    @Test
    void checkWhenSyntaxIsNotXWiki21()
    {
        List<DocumentationViolation> violations = this.check.check(documentWithSyntax(Syntax.XWIKI_2_0));

        assertEquals(1, violations.size());
        assertEquals("Syntax must be 'xwiki/2.1' for documentation pages.", violations.get(0).getViolationMessage());
        assertEquals("", violations.get(0).getViolationContext());
        assertEquals(DocumentationViolationSeverity.ERROR, violations.get(0).getViolationSeverity());
    }

    @Test
    void checkWhenSyntaxIsNull()
    {
        List<DocumentationViolation> violations = this.check.check(documentWithSyntax(null));

        assertEquals(1, violations.size());
        assertEquals(DocumentationViolationSeverity.ERROR, violations.get(0).getViolationSeverity());
    }
}
