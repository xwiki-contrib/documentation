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

import java.util.ArrayList;
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
import org.xwiki.rendering.macro.MacroLookupException;
import org.xwiki.rendering.macro.MacroManager;
import org.xwiki.rendering.macro.descriptor.ContentDescriptor;
import org.xwiki.rendering.macro.descriptor.MacroDescriptor;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.test.LogLevel;
import org.xwiki.test.annotation.AllComponents;
import org.xwiki.test.junit5.LogCaptureExtension;

import com.xpn.xwiki.doc.XWikiAttachment;
import com.xpn.xwiki.doc.XWikiDocument;
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
 * Unit tests for {@link VideoMacroCheck}.
 *
 * @version $Id$
 * @since 1.14
 */
@AllComponents
@OldcoreTest
class VideoMacroCheckTest
{
    @InjectMockitoOldcore
    private MockitoOldcore oldcore;

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    private XWikiDocument createDocument(XDOM xdom, String... attachmentFilenames)
    {
        List<XWikiAttachment> attachments = new ArrayList<>();
        for (String filename : attachmentFilenames) {
            XWikiAttachment attachment = mock(XWikiAttachment.class);
            when(attachment.getFilename()).thenReturn(filename);
            attachments.add(attachment);
        }

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

            @Override
            public List<XWikiAttachment> getAttachmentList()
            {
                return attachments;
            }
        };
    }

    private DocumentationCheck getChecker() throws Exception
    {
        return this.oldcore.getMocker().getInstance(DocumentationCheck.class, "videoMacro");
    }

    @Test
    void checkWhenNoAttachments() throws Exception
    {
        XWikiDocument document = createDocument(new XDOM(Collections.emptyList()));

        assertEquals(0, getChecker().check(document).size());
    }

    @Test
    void checkWhenNoVideoAttachment() throws Exception
    {
        MacroBlock videoMacro = new MacroBlock("video", Map.of("file", "demo.webm"), false);
        XWikiDocument document = createDocument(new XDOM(List.of(videoMacro)), "screenshot.png");

        assertEquals(0, getChecker().check(document).size());
    }

    @Test
    void checkWhenVideoAttachmentButNoVideoMacro() throws Exception
    {
        XWikiDocument document = createDocument(new XDOM(Collections.emptyList()), "demo.mp4");

        assertEquals(0, getChecker().check(document).size());
    }

    @Test
    void checkWhenMp4AttachmentAndVideoMacro() throws Exception
    {
        MacroBlock videoMacro = new MacroBlock("video", Map.of("file", "demo.mp4"), false);
        XWikiDocument document = createDocument(new XDOM(List.of(videoMacro)), "demo.mp4");

        List<DocumentationViolation> violations = getChecker().check(document);

        assertEquals(1, violations.size());
        assertEquals("Use the Embed macro instead of the Video macro.", violations.get(0).getViolationMessage());
        assertEquals(DocumentationViolationSeverity.ERROR, violations.get(0).getViolationSeverity());
    }

    @Test
    void checkWhenWebmAttachmentAndVideoMacro() throws Exception
    {
        MacroBlock videoMacro = new MacroBlock("video", Map.of("file", "demo.webm"), false);
        XWikiDocument document = createDocument(new XDOM(List.of(videoMacro)), "demo.webm");

        List<DocumentationViolation> violations = getChecker().check(document);

        assertEquals(1, violations.size());
        assertEquals("Use the Embed macro instead of the Video macro.", violations.get(0).getViolationMessage());
        assertEquals(DocumentationViolationSeverity.ERROR, violations.get(0).getViolationSeverity());
    }

    @Test
    void checkWhenUppercaseExtensionAndVideoMacro() throws Exception
    {
        MacroBlock videoMacro = new MacroBlock("video", Map.of("file", "demo.MP4"), false);
        XWikiDocument document = createDocument(new XDOM(List.of(videoMacro)), "demo.MP4");

        List<DocumentationViolation> violations = getChecker().check(document);

        assertEquals(1, violations.size());
        assertEquals("Use the Embed macro instead of the Video macro.", violations.get(0).getViolationMessage());
    }

    @Test
    void checkWhenMultipleVideoMacros() throws Exception
    {
        MacroBlock videoMacro1 = new MacroBlock("video", Map.of("file", "demo1.mp4"), false);
        MacroBlock videoMacro2 = new MacroBlock("video", Map.of("file", "demo2.webm"), false);
        XWikiDocument document = createDocument(new XDOM(List.of(videoMacro1, videoMacro2)), "demo1.mp4");

        List<DocumentationViolation> violations = getChecker().check(document);

        assertEquals(2, violations.size());
    }

    @Test
    void checkWhenVideoMacroInsideWikiMacro() throws Exception
    {
        MacroManager macroManager = this.oldcore.getMocker().registerMockComponent(MacroManager.class);
        Macro<?> macro = mock(Macro.class);
        MacroDescriptor descriptor = mock(MacroDescriptor.class);
        ContentDescriptor contentDescriptor = mock(ContentDescriptor.class);
        when(contentDescriptor.getType()).thenReturn(Block.LIST_BLOCK_TYPE);
        when(descriptor.getContentDescriptor()).thenReturn(contentDescriptor);
        when(macro.getDescriptor()).thenReturn(descriptor);
        doReturn(macro).when(macroManager).getMacro(new MacroId("note"));

        MacroContentParser contentParser =
            this.oldcore.getMocker().registerMockComponent(MacroContentParser.class);
        MacroBlock videoMacro = new MacroBlock("video", Map.of("file", "demo.mp4"), false);
        when(contentParser.parse(any(), any(), anyBoolean(), anyBoolean()))
            .thenReturn(new XDOM(List.of(videoMacro)));

        MacroBlock noteBlock = new MacroBlock("note", Map.of(), "{{video file=\"demo.mp4\"/}}", false);
        XWikiDocument document = createDocument(new XDOM(List.of(noteBlock)), "demo.mp4");

        List<DocumentationViolation> violations = getChecker().check(document);

        assertEquals(1, violations.size());
        assertEquals("Use the Embed macro instead of the Video macro.", violations.get(0).getViolationMessage());
        assertEquals(DocumentationViolationSeverity.ERROR, violations.get(0).getViolationSeverity());
    }

    @Test
    void checkWhenMacroLookupFails() throws Exception
    {
        MacroManager macroManager = this.oldcore.getMocker().registerMockComponent(MacroManager.class);
        when(macroManager.getMacro(any())).thenThrow(new MacroLookupException("not found"));

        MacroBlock noteBlock = new MacroBlock("note", Map.of(), "{{video file=\"demo.mp4\"/}}", false);
        XWikiDocument document = createDocument(new XDOM(List.of(noteBlock)), "demo.mp4");

        assertEquals(0, getChecker().check(document).size());
        assertEquals("Failed to look up macro [note]. Ignoring Video Macro check inside it. "
            + "Root error cause: [MacroLookupException: not found]", this.logCapture.getMessage(0));
    }
}
