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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
 * Unit tests for {@link ImageAttachmentFormatCheck}.
 *
 * @version $Id$
 */
@ComponentTest
class ImageAttachmentFormatCheckTest
{
    private static final String PNG_MESSAGE = "Image attachments must use the \".png\" format.";

    private static final String GIF_MESSAGE = "Image attachments must use the \".png\" format. Avoid animated GIFs, "
        + "which are difficult to maintain: replace them with several PNGs.";

    @InjectMockComponents
    private ImageAttachmentFormatCheck check;

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
        assertEquals(0, this.check.check(documentWithAttachments()).size());
    }

    @ParameterizedTest
    @ValueSource(strings = { "screenshot.png", "screenshot.PNG", "logo.svg", "demo.webm", "notes.txt", "imagefile" })
    void checkWhenNotReported(String filename)
    {
        assertEquals(0, this.check.check(documentWithAttachments(filename)).size());
    }

    @ParameterizedTest
    @ValueSource(strings = { "photo.jpg", "photo.jpeg", "scan.bmp", "scan.tif", "scan.tiff", "picture.webp",
        "PHOTO.JPG", "scan.TIF" })
    void checkWhenNonPngImage(String filename)
    {
        List<DocumentationViolation> violations = this.check.check(documentWithAttachments(filename));
        assertEquals(1, violations.size());
        assertEquals(PNG_MESSAGE, violations.get(0).getViolationMessage());
        assertEquals(String.format("Attachment name: [%s]", filename), violations.get(0).getViolationContext());
        assertEquals(DocumentationViolationSeverity.ERROR, violations.get(0).getViolationSeverity());
    }

    @Test
    void checkWhenGifImage()
    {
        List<DocumentationViolation> violations = this.check.check(documentWithAttachments("demo.GIF"));
        assertEquals(1, violations.size());
        assertEquals(GIF_MESSAGE, violations.get(0).getViolationMessage());
        assertEquals("Attachment name: [demo.GIF]", violations.get(0).getViolationContext());
        assertEquals(DocumentationViolationSeverity.ERROR, violations.get(0).getViolationSeverity());
    }

    @Test
    void checkWhenMixedAttachments()
    {
        List<DocumentationViolation> violations = this.check.check(
            documentWithAttachments("screenshot.png", "logo.svg", "photo.jpg", "demo.gif", "demo.mp4"));
        assertEquals(2, violations.size());
        assertEquals("Attachment name: [photo.jpg]", violations.get(0).getViolationContext());
        assertEquals(PNG_MESSAGE, violations.get(0).getViolationMessage());
        assertEquals("Attachment name: [demo.gif]", violations.get(1).getViolationContext());
        assertEquals(GIF_MESSAGE, violations.get(1).getViolationMessage());
    }
}
