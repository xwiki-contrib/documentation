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
 * Unit tests for {@link AttachmentNameCheck}.
 *
 * @version $Id$
 * @since 1.13
 */
@ComponentTest
class AttachmentNameCheckTest
{
    @InjectMockComponents
    private AttachmentNameCheck check;

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
    void checkWhenAttachmentNameIsValidKebabCase()
    {
        assertEquals(0, this.check.check(documentWithAttachments("screenshot.png")).size());
    }

    @Test
    void checkWhenAttachmentNameHasNoExtension()
    {
        assertEquals(0, this.check.check(documentWithAttachments("screenshot")).size());
    }

    @Test
    void checkWhenAttachmentNameHasVersionNumber()
    {
        assertEquals(0, this.check.check(documentWithAttachments("release-1.2.3.pdf")).size());
    }

    @Test
    void checkWhenAttachmentNameHasUppercaseExtension()
    {
        List<DocumentationViolation> violations = this.check.check(documentWithAttachments("screenshot.PNG"));

        assertEquals(1, violations.size());
        assertEquals("Attachment name must follow the kebab-case naming convention "
            + "(lowercase, hyphens instead of spaces or special characters).",
            violations.get(0).getViolationMessage());
        assertEquals("Attachment name: [screenshot.PNG], Expected: [screenshot.png]",
            violations.get(0).getViolationContext());
        assertEquals(DocumentationViolationSeverity.ERROR, violations.get(0).getViolationSeverity());
    }

    @Test
    void checkWhenAttachmentStemHasSpaces()
    {
        List<DocumentationViolation> violations = this.check.check(documentWithAttachments("Installation Guide.png"));

        assertEquals(1, violations.size());
        assertEquals("Attachment name: [Installation Guide.png], Expected: [installation-guide.png]",
            violations.get(0).getViolationContext());
    }

    @Test
    void checkWhenAttachmentStemHasUppercase()
    {
        List<DocumentationViolation> violations = this.check.check(documentWithAttachments("GettingStarted.png"));

        assertEquals(1, violations.size());
        assertEquals("Attachment name: [GettingStarted.png], Expected: [gettingstarted.png]",
            violations.get(0).getViolationContext());
    }

    @Test
    void checkWhenMultipleAttachmentsWithMixedValidity()
    {
        List<DocumentationViolation> violations =
            this.check.check(documentWithAttachments("valid-screenshot.png", "Invalid Screenshot.jpg"));

        assertEquals(1, violations.size());
        assertEquals("Attachment name: [Invalid Screenshot.jpg], Expected: [invalid-screenshot.jpg]",
            violations.get(0).getViolationContext());
    }

    @Test
    void checkWhenAttachmentNameHasAccentedChars()
    {
        List<DocumentationViolation> violations = this.check.check(documentWithAttachments("présentation.png"));

        assertEquals(1, violations.size());
        assertEquals("Attachment name: [présentation.png], Expected: [presentation.png]",
            violations.get(0).getViolationContext());
    }

    @Test
    void checkWhenAttachmentStemContainsStopWord()
    {
        List<DocumentationViolation> violations =
            this.check.check(documentWithAttachments("installation-of-xwiki.png"));

        assertEquals(1, violations.size());
        assertEquals("Attachment name: [installation-of-xwiki.png], Expected: [installation-xwiki.png]",
            violations.get(0).getViolationContext());
    }

    @Test
    void checkWhenAttachmentStemContainsReservedWord()
    {
        List<DocumentationViolation> violations =
            this.check.check(documentWithAttachments("installation-tutorial.png"));

        assertEquals(1, violations.size());
        assertEquals("Attachment name must not contain documentation-type words "
            + "(explanation, howto, reference, tutorial).",
            violations.get(0).getViolationMessage());
        assertEquals("Attachment name: [installation-tutorial.png], Expected: [installation.png]",
            violations.get(0).getViolationContext());
        assertEquals(DocumentationViolationSeverity.WARNING, violations.get(0).getViolationSeverity());
    }

    @Test
    void checkWhenAttachmentWithNoExtensionContainsReservedWord()
    {
        List<DocumentationViolation> violations = this.check.check(documentWithAttachments("tutorial"));

        assertEquals(1, violations.size());
        assertEquals(DocumentationViolationSeverity.WARNING, violations.get(0).getViolationSeverity());
        assertEquals("Attachment name: [tutorial], Expected: []",
            violations.get(0).getViolationContext());
    }
}
