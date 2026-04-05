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
 * Unit tests for {@link ImageMacroCheck}.
 *
 * @version $Id$
 */
@AllComponents
@OldcoreTest
class ImageMacroCheckTest
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
        return this.oldcore.getMocker().getInstance(DocumentationCheck.class, "imageMacro");
    }

    @Test
    void checkWhenNoImage() throws Exception
    {
        XWikiDocument document = createDocument(new XDOM(Collections.emptyList()));

        assertEquals(0, getChecker().check(document).size());
    }

    @Test
    void checkWhenDirectImageSyntaxIsUsed() throws Exception
    {
        ImageBlock imageBlock =
            new ImageBlock(new ResourceReference("foo.png", ResourceType.ATTACHMENT), true);
        XWikiDocument document = createDocument(new XDOM(List.of(imageBlock)));

        List<DocumentationViolation> violations = getChecker().check(document);

        assertEquals(1, violations.size());
        assertEquals("Use the Image macro instead.", violations.get(0).getViolationMessage());
        assertEquals("Image reference : foo.png", violations.get(0).getViolationContext());
        assertEquals(DocumentationViolationSeverity.ERROR, violations.get(0).getViolationSeverity());
    }

    @Test
    void checkWhenImageIsInsideMacroWithWikiContent() throws Exception
    {
        // Replace MacroManager with a mock before the checker singleton is created
        MacroManager macroManager = this.oldcore.getMocker().registerMockComponent(MacroManager.class);
        Macro<?> macro = mock(Macro.class);
        MacroDescriptor descriptor = mock(MacroDescriptor.class);
        ContentDescriptor contentDescriptor = mock(ContentDescriptor.class);
        when(contentDescriptor.getType()).thenReturn(Block.LIST_BLOCK_TYPE);
        when(descriptor.getContentDescriptor()).thenReturn(contentDescriptor);
        when(macro.getDescriptor()).thenReturn(descriptor);
        doReturn(macro).when(macroManager).getMacro(new MacroId("note"));

        // Replace MacroContentParser with a mock too, so parsing returns a predictable XDOM
        MacroContentParser contentParser =
            this.oldcore.getMocker().registerMockComponent(MacroContentParser.class);
        ImageBlock imageBlock =
            new ImageBlock(new ResourceReference("foo.png", ResourceType.ATTACHMENT), true);
        when(contentParser.parse(any(), any(), anyBoolean(), anyBoolean()))
            .thenReturn(new XDOM(List.of(imageBlock)));

        MacroBlock macroBlock = new MacroBlock("note", Map.of(), "image:foo.png", false);
        XWikiDocument document = createDocument(new XDOM(List.of(macroBlock)));

        List<DocumentationViolation> violations = getChecker().check(document);

        assertEquals(1, violations.size());
        assertEquals("Use the Image macro instead.", violations.get(0).getViolationMessage());
        assertEquals("Image reference : foo.png", violations.get(0).getViolationContext());
        assertEquals(DocumentationViolationSeverity.ERROR, violations.get(0).getViolationSeverity());
    }

    @Test
    void checkWhenImageIsInsideMacroWithoutWikiContent() throws Exception
    {
        MacroManager macroManager = this.oldcore.getMocker().registerMockComponent(MacroManager.class);
        Macro<?> macro = mock(Macro.class);
        MacroDescriptor descriptor = mock(MacroDescriptor.class);
        ContentDescriptor contentDescriptor = mock(ContentDescriptor.class);
        when(contentDescriptor.getType()).thenReturn(String.class);
        when(descriptor.getContentDescriptor()).thenReturn(contentDescriptor);
        when(macro.getDescriptor()).thenReturn(descriptor);
        doReturn(macro).when(macroManager).getMacro(new MacroId("note"));

        MacroBlock macroBlock = new MacroBlock("note", Map.of(), "image:foo.png", false);
        XWikiDocument document = createDocument(new XDOM(List.of(macroBlock)));

        assertEquals(0, getChecker().check(document).size());
    }

    @Test
    void checkWhenMacroLookupFails() throws Exception
    {
        MacroManager macroManager = this.oldcore.getMocker().registerMockComponent(MacroManager.class);
        when(macroManager.getMacro(any())).thenThrow(new MacroLookupException("not found"));

        MacroBlock macroBlock = new MacroBlock("note", Map.of(), "image:foo.png", false);
        XWikiDocument document = createDocument(new XDOM(List.of(macroBlock)));

        assertEquals(0, getChecker().check(document).size());
        assertEquals("Failed to look up macro [note]. Ignoring Image Macro check inside it. "
            + "Root error cause: [MacroLookupException: not found]", this.logCapture.getMessage(0));
    }

    @Test
    void checkWhenImageIsInRenderingMacroInsideFaqProperty() throws Exception
    {
        // Mock MacroManager to say "note" has wiki content.
        MacroManager macroManager = this.oldcore.getMocker().registerMockComponent(MacroManager.class);
        Macro<?> macro = mock(Macro.class);
        MacroDescriptor descriptor = mock(MacroDescriptor.class);
        ContentDescriptor contentDescriptor = mock(ContentDescriptor.class);
        when(contentDescriptor.getType()).thenReturn(Block.LIST_BLOCK_TYPE);
        when(descriptor.getContentDescriptor()).thenReturn(contentDescriptor);
        when(macro.getDescriptor()).thenReturn(descriptor);
        doReturn(macro).when(macroManager).getMacro(new MacroId("note"));

        // First parse() call is for the faq content -> returns XDOM containing a note macro.
        // Second parse() call is for the note content -> returns XDOM with an ImageBlock.
        MacroContentParser contentParser =
            this.oldcore.getMocker().registerMockComponent(MacroContentParser.class);
        MacroBlock noteBlock = new MacroBlock("note", Map.of(), "image:faq-image.png", false);
        ImageBlock imageBlock =
            new ImageBlock(new ResourceReference("faq-image.png", ResourceType.ATTACHMENT), true);
        when(contentParser.parse(any(), any(), anyBoolean(), anyBoolean()))
            .thenReturn(new XDOM(List.of(noteBlock)))
            .thenReturn(new XDOM(List.of(imageBlock)));

        XWikiDocument document = createDocument(new XDOM(Collections.emptyList()));
        BaseObject faqObj = new BaseObject();
        faqObj.setXClassReference(new DocumentReference("wiki", Arrays.asList("DocApp", "Code"),
            "DocumentationClass"));
        faqObj.setLargeStringValue("faq", "{{note}}image:faq-image.png{{/note}}");
        document.addXObject(faqObj);

        List<DocumentationViolation> violations = getChecker().check(document);

        assertEquals(1, violations.size());
        assertEquals("Use the Image macro instead.", violations.get(0).getViolationMessage());
        assertEquals("Image reference : faq-image.png", violations.get(0).getViolationContext());
        assertEquals(DocumentationViolationSeverity.ERROR, violations.get(0).getViolationSeverity());
    }

    @Test
    void checkWhenImageIsInFaqProperty() throws Exception
    {
        // Mock MacroContentParser to return an XDOM with an ImageBlock when parsing the faq content.
        MacroContentParser contentParser =
            this.oldcore.getMocker().registerMockComponent(MacroContentParser.class);
        ImageBlock imageBlock =
            new ImageBlock(new ResourceReference("faq-image.png", ResourceType.ATTACHMENT), true);
        when(contentParser.parse(any(), any(), anyBoolean(), anyBoolean()))
            .thenReturn(new XDOM(List.of(imageBlock)));

        // Create a document with an empty main XDOM and a DocumentationClass XObject with faq content.
        XWikiDocument document = createDocument(new XDOM(Collections.emptyList()));
        BaseObject faqObj = new BaseObject();
        faqObj.setXClassReference(new DocumentReference("wiki", Arrays.asList("DocApp", "Code"),
            "DocumentationClass"));
        faqObj.setLargeStringValue("faq", "image:faq-image.png");
        document.addXObject(faqObj);

        List<DocumentationViolation> violations = getChecker().check(document);

        assertEquals(1, violations.size());
        assertEquals("Use the Image macro instead.", violations.get(0).getViolationMessage());
        assertEquals("Image reference : faq-image.png", violations.get(0).getViolationContext());
        assertEquals(DocumentationViolationSeverity.ERROR, violations.get(0).getViolationSeverity());
    }
}
