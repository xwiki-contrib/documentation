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
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xwiki.contrib.documentation.DocumentationCheck;
import org.xwiki.contrib.documentation.DocumentationViolation;
import org.xwiki.contrib.documentation.DocumentationViolationSeverity;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.MacroMarkerBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.macro.Macro;
import org.xwiki.rendering.macro.MacroContentParser;
import org.xwiki.rendering.macro.MacroId;
import org.xwiki.rendering.macro.MacroManager;
import org.xwiki.rendering.macro.descriptor.ContentDescriptor;
import org.xwiki.rendering.macro.descriptor.MacroDescriptor;
import org.xwiki.rendering.parser.Parser;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.test.LogLevel;
import org.xwiki.test.annotation.AllComponents;
import org.xwiki.test.junit5.LogCaptureExtension;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.test.MockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.InjectMockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.OldcoreTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link InlineStyleCheck}.
 *
 * @version $Id$
 */
@AllComponents
@OldcoreTest
class InlineStyleCheckTest
{
    private static final String MESSAGE = "Inline styles should not be used in the content.";

    @InjectMockitoOldcore
    private MockitoOldcore oldcore;

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    private XWikiDocument createDocument(XDOM xdom)
    {
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
        return this.oldcore.getMocker().getInstance(DocumentationCheck.class, "inlineStyle");
    }

    private XDOM parse(String content) throws Exception
    {
        Parser parser = this.oldcore.getMocker().getInstance(Parser.class, Syntax.XWIKI_2_1.toIdString());
        return parser.parse(new StringReader(content));
    }

    private static void assertViolation(String style, DocumentationViolation violation)
    {
        assertEquals(MESSAGE, violation.getViolationMessage());
        assertEquals("Style : " + style, violation.getViolationContext());
        assertEquals(DocumentationViolationSeverity.WARNING, violation.getViolationSeverity());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        // Paragraph
        "(% style=\"color:red\" %)\nSome paragraph",
        // Inline formatting
        "Some (% style=\"color:red\" %)styled(%%) text",
        // Inline formatting inside bold text
        "Some **bold (% style=\"color:red\" %)styled(%%)** text",
        // Group
        "(% style=\"color:red\" %)(((\nSome content\n)))",
        // List
        "(% style=\"color:red\" %)\n* item",
        // List item
        "* (% style=\"color:red\" %)item",
        // Table
        "(% style=\"color:red\" %)\n|a|b",
        // Table cell
        "|(% style=\"color:red\" %)a|b",
        // Heading
        "(% style=\"color:red\" %)\n= Heading =",
        // Link
        "[[label>>https://www.example.com||style=\"color:red\"]]",
        // Image
        "[[image:image.png||alt=\"alt\" style=\"color:red\"]]"
    })
    void checkWhenBlockHasStyle(String content) throws Exception
    {
        XWikiDocument document = createDocument(parse(content));

        List<DocumentationViolation> violations = getChecker().check(document);

        assertEquals(1, violations.size());
        assertViolation("color:red", violations.get(0));
    }

    @Test
    void checkWhenSeveralBlocksHaveStyles() throws Exception
    {
        XWikiDocument document = createDocument(parse(
            "(% style=\"color:red\" %)\nSome paragraph\n\n|(% style=\"width:50%\" %)a|b"));

        List<DocumentationViolation> violations = getChecker().check(document);

        assertEquals(2, violations.size());
        assertViolation("color:red", violations.get(0));
        assertViolation("width:50%", violations.get(1));
    }

    @Test
    void checkWhenNoStyle() throws Exception
    {
        XWikiDocument document = createDocument(parse(
            "(% class=\"box\" %)\nSome paragraph\n\n(% id=\"anchor\" %)\n* item\n\n|a|b"));

        assertEquals(0, getChecker().check(document).size());
    }

    @Test
    void checkWhenMacroHasStyleParameter() throws Exception
    {
        MacroBlock macroBlock = new MacroBlock("box", Map.of("style", "color:red"), "content", false);
        MacroMarkerBlock macroMarkerBlock =
            new MacroMarkerBlock("box", Map.of("style", "color:red"), "content", Collections.emptyList(), false);
        XWikiDocument document = createDocument(new XDOM(List.of(macroBlock, macroMarkerBlock)));

        assertEquals(0, getChecker().check(document).size());
        // The box macro is not registered in this test.
        assertEquals("Failed to look up macro [box]. Ignoring Inline Style check inside it. "
            + "Root error cause: [MacroNotFoundException: No macro [box] could be found.]",
            this.logCapture.getMessage(0));
    }

    @Test
    void checkWhenStyleIsInsideWikiContentMacro() throws Exception
    {
        // Parse before registering the mocks below, which the parser would otherwise use.
        String macroContent = "(% style=\"color:red\" %)\nSome paragraph";
        XDOM macroXDOM = parse(macroContent);

        MacroManager macroManager = this.oldcore.getMocker().registerMockComponent(MacroManager.class);
        Macro<?> macro = mock(Macro.class);
        MacroDescriptor descriptor = mock(MacroDescriptor.class);
        ContentDescriptor contentDescriptor = mock(ContentDescriptor.class);
        when(contentDescriptor.getType()).thenReturn(Block.LIST_BLOCK_TYPE);
        when(descriptor.getContentDescriptor()).thenReturn(contentDescriptor);
        when(macro.getDescriptor()).thenReturn(descriptor);
        doReturn(macro).when(macroManager).getMacro(new MacroId("info"));

        MacroContentParser contentParser =
            this.oldcore.getMocker().registerMockComponent(MacroContentParser.class);
        when(contentParser.parse(any(), any(), anyBoolean(), anyBoolean())).thenReturn(macroXDOM);

        MacroBlock macroBlock = new MacroBlock("info", Map.of(), macroContent, false);
        XWikiDocument document = createDocument(new XDOM(List.of(macroBlock)));

        List<DocumentationViolation> violations = getChecker().check(document);

        assertEquals(1, violations.size());
        assertViolation("color:red", violations.get(0));
    }

    @Test
    void checkWhenStyleIsInFAQProperty() throws Exception
    {
        // Parse before registering the mock below, which the parser would otherwise use.
        String faqContent = "|(% style=\"color:blue\" %)a|b";
        XDOM faqXDOM = parse(faqContent);
        MacroContentParser contentParser =
            this.oldcore.getMocker().registerMockComponent(MacroContentParser.class);
        when(contentParser.parse(any(), any(), anyBoolean(), anyBoolean())).thenReturn(faqXDOM);

        XWikiDocument document = createDocument(new XDOM(Collections.emptyList()));
        BaseObject docObject = new BaseObject();
        docObject.setXClassReference(new DocumentReference("wiki", List.of("DocApp", "Code"), "DocumentationClass"));
        docObject.setLargeStringValue("faq", faqContent);
        document.addXObject(docObject);

        List<DocumentationViolation> violations = getChecker().check(document);

        assertEquals(1, violations.size());
        assertViolation("color:blue", violations.get(0));
    }
}
