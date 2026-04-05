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
import org.xwiki.rendering.block.ImageBlock;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.listener.reference.ResourceType;
import org.xwiki.rendering.macro.Macro;
import org.xwiki.rendering.macro.MacroContentParser;
import org.xwiki.rendering.macro.MacroId;
import org.xwiki.rendering.macro.MacroLookupException;
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
 * Unit tests for {@link GalleryMacroAltCheck}.
 *
 * @version $Id$
 * @since 1.10
 */
@AllComponents
@OldcoreTest
class GalleryMacroAltCheckTest
{
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
        return this.oldcore.getMocker().getInstance(DocumentationCheck.class, "galleryMacroAlt");
    }

    @Test
    void checkWhenGalleryMacroImageIsMissingAltText() throws Exception
    {
        String input = """
            {{gallery}}
            image:alice.png
            image:http://www.xwiki.org/logo.png
            {{/gallery}}
            """;
        XWikiDocument document = new XWikiDocument(new DocumentReference("wiki", "space", "page"));
        document.setSyntax(Syntax.XWIKI_2_1);
        document.setContent(input);
        this.oldcore.getSpyXWiki().saveDocument(document, this.oldcore.getXWikiContext());

        List<DocumentationViolation> violations = getChecker().check(document);

        assertEquals(2, violations.size());
        assertEquals("Images inside the Gallery macro should specify an 'alt' parameter.",
            violations.get(0).getViolationMessage());
        assertEquals("Image reference : alice.png",
            violations.get(0).getViolationContext());
        assertEquals(DocumentationViolationSeverity.WARNING, violations.get(0).getViolationSeverity());
        assertEquals("Images inside the Gallery macro should specify an 'alt' parameter.",
            violations.get(1).getViolationMessage());
        assertEquals("Image reference : http://www.xwiki.org/logo.png",
            violations.get(1).getViolationContext());
        assertEquals(DocumentationViolationSeverity.WARNING, violations.get(1).getViolationSeverity());
    }

    @Test
    void checkWheGalleryMacroImageIsHavingAltText() throws Exception
    {
        String input = """
            {{gallery}}
            [[image:alice.png||alt="test"]]
            {{/gallery}}
            """;
        XWikiDocument document = new XWikiDocument(new DocumentReference("wiki", "space", "page"));
        document.setSyntax(Syntax.XWIKI_2_1);
        document.setContent(input);
        this.oldcore.getSpyXWiki().saveDocument(document, this.oldcore.getXWikiContext());

        List<DocumentationViolation> violations = getChecker().check(document);

        assertEquals(0, violations.size());
    }

    @Test
    void checkWhenGalleryIsInsideWikiContentMacro() throws Exception
    {
        MacroManager macroManager = this.oldcore.getMocker().registerMockComponent(MacroManager.class);
        Macro<?> noteMacro = mock(Macro.class);
        MacroDescriptor noteDescriptor = mock(MacroDescriptor.class);
        ContentDescriptor noteContentDescriptor = mock(ContentDescriptor.class);
        when(noteContentDescriptor.getType()).thenReturn(Block.LIST_BLOCK_TYPE);
        when(noteDescriptor.getContentDescriptor()).thenReturn(noteContentDescriptor);
        when(noteMacro.getDescriptor()).thenReturn(noteDescriptor);
        doReturn(noteMacro).when(macroManager).getMacro(new MacroId("note"));

        MacroContentParser contentParser =
            this.oldcore.getMocker().registerMockComponent(MacroContentParser.class);

        // First parse() call is for the note macro content -> returns XDOM containing a gallery macro.
        MacroBlock galleryBlock = new MacroBlock("gallery", Map.of(), "image:alice.png", false);
        XDOM noteContentXDOM = new XDOM(List.of(galleryBlock));
        // Second parse() call is for the gallery macro content -> returns XDOM with an ImageBlock without alt.
        ImageBlock imageBlock =
            new ImageBlock(new ResourceReference("alice.png", ResourceType.ATTACHMENT), true);
        XDOM galleryContentXDOM = new XDOM(List.of(imageBlock));
        when(contentParser.parse(any(), any(), anyBoolean(), anyBoolean()))
            .thenReturn(noteContentXDOM)
            .thenReturn(galleryContentXDOM);

        MacroBlock noteBlock = new MacroBlock("note", Map.of(), "{{gallery}}image:alice.png{{/gallery}}", false);
        XWikiDocument document = createDocument(new XDOM(List.of(noteBlock)));

        List<DocumentationViolation> violations = getChecker().check(document);

        assertEquals(1, violations.size());
        assertEquals("Images inside the Gallery macro should specify an 'alt' parameter.",
            violations.get(0).getViolationMessage());
        assertEquals("Image reference : alice.png",
            violations.get(0).getViolationContext());
        assertEquals(DocumentationViolationSeverity.WARNING, violations.get(0).getViolationSeverity());
    }

    @Test
    void checkWhenMacroLookupFails() throws Exception
    {
        MacroManager macroManager = this.oldcore.getMocker().registerMockComponent(MacroManager.class);
        when(macroManager.getMacro(any())).thenThrow(new MacroLookupException("not found"));

        MacroBlock macroBlock = new MacroBlock("note", Map.of(), "{{gallery}}image:alice.png{{/gallery}}", false);
        XWikiDocument document = createDocument(new XDOM(List.of(macroBlock)));

        assertEquals(0, getChecker().check(document).size());
        assertEquals("Failed to look up macro [note]. Ignoring Gallery Macro Alt check inside it. "
            + "Root error cause: [MacroLookupException: not found]", this.logCapture.getMessage(0));
    }

    @Test
    void checkWhenGalleryMacroImageIsMissingAltTextInFaqProperty() throws Exception
    {
        MacroContentParser contentParser =
            this.oldcore.getMocker().registerMockComponent(MacroContentParser.class);

        // First parse() call is for the faq content -> returns XDOM containing a gallery macro.
        MacroBlock galleryBlock = new MacroBlock("gallery", Map.of(), "image:faq-image.png", false);
        XDOM faqXDOM = new XDOM(List.of(galleryBlock));
        // Second parse() call is for the gallery content -> returns XDOM with an ImageBlock without alt.
        ImageBlock imageBlock =
            new ImageBlock(new ResourceReference("faq-image.png", ResourceType.ATTACHMENT), true);
        when(contentParser.parse(any(), any(), anyBoolean(), anyBoolean()))
            .thenReturn(faqXDOM)
            .thenReturn(new XDOM(List.of(imageBlock)));

        XWikiDocument document = createDocument(new XDOM(Collections.emptyList()));
        BaseObject faqObj = new BaseObject();
        faqObj.setXClassReference(new DocumentReference("wiki", Arrays.asList("DocApp", "Code"),
            "DocumentationClass"));
        faqObj.setLargeStringValue("faq", "{{gallery}}image:faq-image.png{{/gallery}}");
        document.addXObject(faqObj);

        List<DocumentationViolation> violations = getChecker().check(document);

        assertEquals(1, violations.size());
        assertEquals("Images inside the Gallery macro should specify an 'alt' parameter.",
            violations.get(0).getViolationMessage());
        assertEquals("Image reference : faq-image.png",
            violations.get(0).getViolationContext());
        assertEquals(DocumentationViolationSeverity.WARNING, violations.get(0).getViolationSeverity());
    }
}
