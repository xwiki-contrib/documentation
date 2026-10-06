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

import java.io.StringReader;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.xwiki.contrib.documentation.DocumentationCheck;
import org.xwiki.contrib.documentation.DocumentationViolation;
import org.xwiki.contrib.documentation.DocumentationViolationSeverity;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.macro.Macro;
import org.xwiki.rendering.macro.MacroId;
import org.xwiki.rendering.macro.MacroManager;
import org.xwiki.rendering.macro.descriptor.ContentDescriptor;
import org.xwiki.rendering.macro.descriptor.MacroDescriptor;
import org.xwiki.rendering.parser.Parser;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.test.annotation.AllComponents;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.test.MockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.InjectMockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.OldcoreTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LevelOneHeadingCheck}.
 *
 * @version $Id$
 */
@AllComponents
@OldcoreTest
class LevelOneHeadingCheckTest
{
    private static final String MESSAGE = "Level 1 headings are not allowed in the page content, since the page "
        + "structure fields are already displayed as level 1 headings. Use level 2 headings or lower.";

    @InjectMockitoOldcore
    private MockitoOldcore oldcore;

    private XWikiDocument createDocument(String content) throws Exception
    {
        Parser parser = this.oldcore.getMocker().getInstance(Parser.class, Syntax.XWIKI_2_1.toIdString());
        XDOM xdom = parser.parse(new StringReader(content));
        return new XWikiDocument(new DocumentReference("wiki", "space", "page"))
        {
            @Override
            public XDOM getXDOM()
            {
                return xdom;
            }

            @Override
            public Syntax getSyntax()
            {
                return Syntax.XWIKI_2_1;
            }
        };
    }

    private DocumentationCheck getChecker() throws Exception
    {
        return this.oldcore.getMocker().getInstance(DocumentationCheck.class, "levelOneHeading");
    }

    private void registerWikiContentMacro(String macroId) throws Exception
    {
        MacroManager macroManager = this.oldcore.getMocker().registerMockComponent(MacroManager.class);
        Macro<?> macro = mock(Macro.class);
        MacroDescriptor descriptor = mock(MacroDescriptor.class);
        ContentDescriptor contentDescriptor = mock(ContentDescriptor.class);
        when(contentDescriptor.getType()).thenReturn(Block.LIST_BLOCK_TYPE);
        when(descriptor.getContentDescriptor()).thenReturn(contentDescriptor);
        when(macro.getDescriptor()).thenReturn(descriptor);
        doReturn(macro).when(macroManager).getMacro(new MacroId(macroId));
    }

    @Test
    void checkWhenNoHeading() throws Exception
    {
        assertEquals(0, getChecker().check(createDocument("Some content")).size());
    }

    @Test
    void checkWhenOnlyLowerLevelHeadings() throws Exception
    {
        XWikiDocument document = createDocument("== Level 2 ==\n\n=== Level 3 ===\n\n====== Level 6 ======");

        assertEquals(0, getChecker().check(document).size());
    }

    @Test
    void checkWhenLevelOneHeadings() throws Exception
    {
        XWikiDocument document = createDocument("= First **heading** =\n\n== Level 2 ==\n\n= Second =");

        List<DocumentationViolation> violations = getChecker().check(document);

        assertEquals(2, violations.size());
        assertEquals(MESSAGE, violations.get(0).getViolationMessage());
        assertEquals("Heading : First heading", violations.get(0).getViolationContext());
        assertEquals(DocumentationViolationSeverity.ERROR, violations.get(0).getViolationSeverity());
        assertEquals(MESSAGE, violations.get(1).getViolationMessage());
        assertEquals("Heading : Second", violations.get(1).getViolationContext());
        assertEquals(DocumentationViolationSeverity.ERROR, violations.get(1).getViolationSeverity());
    }

    @Test
    void checkWhenLevelOneHeadingInsideWikiContentMacro() throws Exception
    {
        registerWikiContentMacro("info");
        XWikiDocument document = createDocument("{{info}}\n= Inside =\n\n== Level 2 ==\n{{/info}}");

        List<DocumentationViolation> violations = getChecker().check(document);

        assertEquals(1, violations.size());
        assertEquals(MESSAGE, violations.get(0).getViolationMessage());
        assertEquals("Heading : Inside", violations.get(0).getViolationContext());
        assertEquals(DocumentationViolationSeverity.ERROR, violations.get(0).getViolationSeverity());
    }
}
