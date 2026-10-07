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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.xwiki.contrib.documentation.DocumentationCheck;
import org.xwiki.contrib.documentation.DocumentationViolation;
import org.xwiki.contrib.documentation.DocumentationViolationSeverity;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.macro.Macro;
import org.xwiki.rendering.macro.MacroContentParser;
import org.xwiki.rendering.macro.MacroId;
import org.xwiki.rendering.macro.MacroManager;
import org.xwiki.rendering.macro.descriptor.ContentDescriptor;
import org.xwiki.rendering.macro.descriptor.MacroDescriptor;
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
 * Unit tests for {@link HTMLMacroCheck}.
 *
 * @version $Id$
 * @since 1.15
 */
@AllComponents
@OldcoreTest
class HTMLMacroCheckTest
{
    private static final String MESSAGE = "Avoid using the HTML macro and use XWiki Syntax instead.";

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
        return this.oldcore.getMocker().getInstance(DocumentationCheck.class, "htmlMacro");
    }

    private void assertViolation(DocumentationViolation violation, String expectedContext)
    {
        assertEquals(MESSAGE, violation.getViolationMessage());
        assertEquals(expectedContext, violation.getViolationContext());
        assertEquals(DocumentationViolationSeverity.WARNING, violation.getViolationSeverity());
    }

    @Test
    void checkWhenHTMLMacroIsUsed() throws Exception
    {
        MacroBlock htmlBlock = new MacroBlock("html", Map.of("clean", "false"), "<p>Hello</p>", false);
        XWikiDocument document = createDocument(new XDOM(List.of(htmlBlock)));

        List<DocumentationViolation> violations = getChecker().check(document);

        assertEquals(1, violations.size());
        assertViolation(violations.get(0), "HTML content : <p>Hello</p>");
    }

    @Test
    void checkWhenHTMLMacroHasLongMultilineContent() throws Exception
    {
        MacroBlock htmlBlock = new MacroBlock("html", Map.of(),
            "<table>\n  <tr>\n    <td>Some cell content that is quite long</td>\n  </tr>\n</table>", false);
        XWikiDocument document = createDocument(new XDOM(List.of(htmlBlock)));

        List<DocumentationViolation> violations = getChecker().check(document);

        assertEquals(1, violations.size());
        assertViolation(violations.get(0), "HTML content : <table> <tr> <td>Some cell content that is quit...");
    }

    @Test
    void checkWhenHTMLMacroHasNoContent() throws Exception
    {
        MacroBlock htmlBlock = new MacroBlock("html", Map.of(), false);
        XWikiDocument document = createDocument(new XDOM(List.of(htmlBlock)));

        List<DocumentationViolation> violations = getChecker().check(document);

        assertEquals(1, violations.size());
        assertViolation(violations.get(0), "HTML content : ");
    }

    @Test
    void checkWhenSeveralHTMLMacrosAreUsed() throws Exception
    {
        MacroBlock htmlBlock1 = new MacroBlock("html", Map.of(), "<br/>", false);
        MacroBlock htmlBlock2 = new MacroBlock("html", Map.of(), "<hr/>", false);
        XWikiDocument document = createDocument(new XDOM(List.of(htmlBlock1, htmlBlock2)));

        List<DocumentationViolation> violations = getChecker().check(document);

        assertEquals(2, violations.size());
        assertViolation(violations.get(0), "HTML content : <br/>");
        assertViolation(violations.get(1), "HTML content : <hr/>");
    }

    @Test
    void checkWhenNoHTMLMacroIsUsed() throws Exception
    {
        MacroBlock codeBlock = new MacroBlock("code", Map.of("language", "html"), "<p>Hello</p>", false);
        XWikiDocument document = createDocument(new XDOM(List.of(codeBlock)));

        MacroManager macroManager = this.oldcore.getMocker().registerMockComponent(MacroManager.class);
        Macro<?> macro = mock(Macro.class);
        MacroDescriptor descriptor = mock(MacroDescriptor.class);
        ContentDescriptor contentDescriptor = mock(ContentDescriptor.class);
        when(contentDescriptor.getType()).thenReturn(String.class);
        when(descriptor.getContentDescriptor()).thenReturn(contentDescriptor);
        when(macro.getDescriptor()).thenReturn(descriptor);
        doReturn(macro).when(macroManager).getMacro(new MacroId("code"));

        assertEquals(0, getChecker().check(document).size());
    }

    @Test
    void checkWhenHTMLMacroIsInsideWikiContentMacro() throws Exception
    {
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
        MacroBlock htmlBlock = new MacroBlock("html", Map.of(), "<em>text</em>", false);
        when(contentParser.parse(any(), any(), anyBoolean(), anyBoolean()))
            .thenReturn(new XDOM(List.of(htmlBlock)));

        MacroBlock macroBlock = new MacroBlock("info", Map.of(), "{{html}}<em>text</em>{{/html}}", false);
        XWikiDocument document = createDocument(new XDOM(List.of(macroBlock)));

        List<DocumentationViolation> violations = getChecker().check(document);

        assertEquals(1, violations.size());
        assertViolation(violations.get(0), "HTML content : <em>text</em>");
    }

    @Test
    void checkWhenHTMLMacroIsUsedInFaqProperty() throws Exception
    {
        MacroContentParser contentParser =
            this.oldcore.getMocker().registerMockComponent(MacroContentParser.class);
        MacroBlock faqHTMLBlock = new MacroBlock("html", Map.of(), "<kbd>Ctrl</kbd>", false);
        when(contentParser.parse(any(), any(), anyBoolean(), anyBoolean()))
            .thenReturn(new XDOM(List.of(faqHTMLBlock)));

        XWikiDocument document = createDocument(new XDOM(Collections.emptyList()));
        BaseObject faqObj = new BaseObject();
        faqObj.setXClassReference(new DocumentReference("wiki", Arrays.asList("DocApp", "Code"),
            "DocumentationClass"));
        faqObj.setLargeStringValue("faq", "{{html}}<kbd>Ctrl</kbd>{{/html}}");
        document.addXObject(faqObj);

        List<DocumentationViolation> violations = getChecker().check(document);

        assertEquals(1, violations.size());
        assertViolation(violations.get(0), "HTML content : <kbd>Ctrl</kbd>");
    }
}
