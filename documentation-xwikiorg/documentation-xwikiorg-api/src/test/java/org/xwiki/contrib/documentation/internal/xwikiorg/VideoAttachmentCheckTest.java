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
import java.util.List;

import org.junit.jupiter.api.Test;
import org.xwiki.contrib.documentation.DocumentationViolation;
import org.xwiki.contrib.documentation.DocumentationViolationSeverity;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;

import com.xpn.xwiki.doc.XWikiAttachment;
import com.xpn.xwiki.doc.XWikiDocument;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link VideoAttachmentCheck}.
 *
 * @version $Id$
 * @since 1.13
 */
@ComponentTest
class VideoAttachmentCheckTest
{
    @InjectMockComponents
    private VideoAttachmentCheck check;

    private XWikiDocument documentWithAttachments(String... filenames)
    {
        XWikiDocument document = mock(XWikiDocument.class);
        List<XWikiAttachment> attachments = new ArrayList<>();
        for (String filename : filenames) {
            XWikiAttachment attachment = mock(XWikiAttachment.class);
            when(attachment.getFilename()).thenReturn(filename);
            attachments.add(attachment);
        }
        when(document.getAttachmentList()).thenReturn(attachments);
        return document;
    }

    @Test
    void checkWhenNoAttachments()
    {
        XWikiDocument document = mock(XWikiDocument.class);
        when(document.getAttachmentList()).thenReturn(List.of());

        assertEquals(0, this.check.check(document).size());
    }

    @Test
    void checkWhenWebmVideo()
    {
        assertEquals(0, this.check.check(documentWithAttachments("demo.webm")).size());
    }

    @Test
    void checkWhenNonVideoAttachment()
    {
        assertEquals(0, this.check.check(documentWithAttachments("screenshot.png")).size());
    }

    @Test
    void checkWhenMp4Video()
    {
        List<DocumentationViolation> violations = this.check.check(documentWithAttachments("demo.mp4"));

        assertEquals(1, violations.size());
        assertEquals("Video attachments must use the \".webm\" format.", violations.get(0).getViolationMessage());
        assertEquals("Attachment name: [demo.mp4]", violations.get(0).getViolationContext());
        assertEquals(DocumentationViolationSeverity.ERROR, violations.get(0).getViolationSeverity());
    }

    @Test
    void checkWhenMovVideo()
    {
        List<DocumentationViolation> violations = this.check.check(documentWithAttachments("demo.mov"));

        assertEquals(1, violations.size());
        assertEquals("Attachment name: [demo.mov]", violations.get(0).getViolationContext());
    }

    @Test
    void checkWhenAviVideo()
    {
        List<DocumentationViolation> violations = this.check.check(documentWithAttachments("demo.avi"));

        assertEquals(1, violations.size());
        assertEquals("Attachment name: [demo.avi]", violations.get(0).getViolationContext());
    }

    @Test
    void checkWhenUppercaseExtension()
    {
        List<DocumentationViolation> violations = this.check.check(documentWithAttachments("demo.MP4"));

        assertEquals(1, violations.size());
        assertEquals("Attachment name: [demo.MP4]", violations.get(0).getViolationContext());
    }

    @Test
    void checkWhenMixedAttachments()
    {
        List<DocumentationViolation> violations =
            this.check.check(documentWithAttachments("screenshot.png", "valid.webm", "invalid.mp4", "other.mov"));

        assertEquals(2, violations.size());
        assertEquals("Attachment name: [invalid.mp4]", violations.get(0).getViolationContext());
        assertEquals("Attachment name: [other.mov]", violations.get(1).getViolationContext());
    }

    @Test
    void checkWhenAttachmentHasNoExtension()
    {
        assertEquals(0, this.check.check(documentWithAttachments("videofile")).size());
    }
}
