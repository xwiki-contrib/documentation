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
import org.xwiki.test.LogLevel;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.rendering.macro.descriptor.ContentDescriptor;
import org.xwiki.rendering.macro.descriptor.MacroDescriptor;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.test.annotation.AllComponents;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectComponentManager;
import org.xwiki.test.mockito.MockitoComponentManager;

import com.xpn.xwiki.doc.XWikiDocument;

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
@ComponentTest
class ImageMacroCheckTest
{
    @InjectComponentManager
    private MockitoComponentManager componentManager;

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    /**
     * Creates a document whose XDOM is the given one, without requiring any rendering component.
     */
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
        return this.componentManager.getInstance(DocumentationCheck.class, "imageMacro");
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
        MacroManager macroManager = this.componentManager.registerMockComponent(MacroManager.class);
        Macro<?> macro = mock(Macro.class);
        MacroDescriptor descriptor = mock(MacroDescriptor.class);
        ContentDescriptor contentDescriptor = mock(ContentDescriptor.class);
        when(contentDescriptor.getType()).thenReturn(Block.LIST_BLOCK_TYPE);
        when(descriptor.getContentDescriptor()).thenReturn(contentDescriptor);
        when(macro.getDescriptor()).thenReturn(descriptor);
        doReturn(macro).when(macroManager).getMacro(new MacroId("note"));

        // Replace MacroContentParser with a mock too, so parsing returns a predictable XDOM
        MacroContentParser contentParser =
            this.componentManager.registerMockComponent(MacroContentParser.class);
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
        MacroManager macroManager = this.componentManager.registerMockComponent(MacroManager.class);
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
        MacroManager macroManager = this.componentManager.registerMockComponent(MacroManager.class);
        when(macroManager.getMacro(any())).thenThrow(new MacroLookupException("not found"));

        MacroBlock macroBlock = new MacroBlock("note", Map.of(), "image:foo.png", false);
        XWikiDocument document = createDocument(new XDOM(List.of(macroBlock)));

        assertEquals(0, getChecker().check(document).size());
        assertEquals("Failed to look up macro [note]. Ignoring Image Macro check inside it. "
            + "Root error cause: [MacroLookupException: not found]", this.logCapture.getMessage(0));
    }
}
