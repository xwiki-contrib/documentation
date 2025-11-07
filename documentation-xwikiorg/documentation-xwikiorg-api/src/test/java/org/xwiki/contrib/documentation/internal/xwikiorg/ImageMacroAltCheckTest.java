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
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.test.annotation.AllComponents;
import org.xwiki.test.junit5.mockito.InjectMockComponents;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.test.MockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.InjectMockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.OldcoreTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link ImageMacroAltCheck}.
 *
 * @version $Id$
 * @since 1.6
 */
@AllComponents
@OldcoreTest
class ImageMacroAltCheckTest
{
    @InjectMockComponents
    private ImageMacroAltCheck checker;

    @InjectMockitoOldcore
    private MockitoOldcore oldcore;

    @Test
    void checkWhenImageMacroIsMissingAltText() throws Exception
    {
        String input = "{{image reference='test.png'/}}";
        XWikiDocument document = new XWikiDocument(new DocumentReference("wiki", "space", "page"));
        document.setSyntax(Syntax.XWIKI_2_1);
        document.setContent(input);
        this.oldcore.getSpyXWiki().saveDocument(document, this.oldcore.getXWikiContext());

        List<DocumentationViolation> violations = this.checker.check(document);

        assertEquals(1, violations.size());
        assertEquals("Missing 'alt' parameter usage in the Image macro.",
            violations.get(0).getViolationMessage());
        assertEquals("Image reference : test.png",
            violations.get(0).getViolationContext());
        assertEquals(DocumentationViolationSeverity.WARNING, violations.get(0).getViolationSeverity());
    }

    @Test
    void checkWhenImageMacroIsHavingAltText() throws Exception
    {
        String input = "{{image reference='test.png' alt='test'/}}";
        XWikiDocument document = new XWikiDocument(new DocumentReference("wiki", "space", "page"));
        document.setSyntax(Syntax.XWIKI_2_1);
        document.setContent(input);
        this.oldcore.getSpyXWiki().saveDocument(document, this.oldcore.getXWikiContext());

        List<DocumentationViolation> violations = this.checker.check(document);

        assertEquals(0, violations.size());
    }
}
