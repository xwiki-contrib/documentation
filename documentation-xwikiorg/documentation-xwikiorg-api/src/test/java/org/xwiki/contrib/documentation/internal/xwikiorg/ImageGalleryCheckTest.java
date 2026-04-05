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
import java.util.List;

import org.junit.jupiter.api.Test;
import org.xwiki.contrib.documentation.DocumentationCheck;
import org.xwiki.contrib.documentation.DocumentationViolation;
import org.xwiki.contrib.documentation.DocumentationViolationSeverity;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.annotation.AllComponents;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.test.MockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.InjectMockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.OldcoreTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link ImageGalleryCheck}.
 *
 * @version $Id$
 * @since 1.0
 */
@AllComponents
@OldcoreTest
class ImageGalleryCheckTest
{
    @InjectMockitoOldcore
    private MockitoOldcore oldcore;

    private DocumentationCheck getChecker() throws Exception
    {
        return this.oldcore.getMocker().getInstance(DocumentationCheck.class, "imageGallery");
    }

    @Test
    void checkWhenImageMacrosNextToEachOther() throws Exception
    {
        String input = "Hello\n\n{{image reference='test1.png'}}\n {{image reference='test2.png'}}\n\nworld";

        XWikiDocument document = new XWikiDocument(new DocumentReference("wiki", "space", "page"));
        document.setContent(input);

        List<DocumentationViolation> violations = getChecker().check(document);

        assertEquals(1, violations.size());
        assertEquals("Use the Gallery macro when several images are displayed next to each other.",
            violations.get(0).getViolationMessage());
        assertEquals("{{image reference='test1.png'}}\n {{image reference='test2.png'}}",
            violations.get(0).getViolationContext());
        assertEquals(DocumentationViolationSeverity.ERROR, violations.get(0).getViolationSeverity());
    }

    @Test
    void checkWhenImageMacrosAreNotNextToEachOther() throws Exception
    {
        String input = "{{image reference='test1.png'}} a {{image reference='test2.png'}}";

        XWikiDocument document = new XWikiDocument(new DocumentReference("wiki", "space", "page"));
        document.setContent(input);

        List<DocumentationViolation> violations = getChecker().check(document);

        assertEquals(0, violations.size());
    }

    @Test
    void checkWhenImageMacrosNextToEachOtherInFaqProperty() throws Exception
    {
        String faqContent = "{{image reference='test1.png'}}\n {{image reference='test2.png'}}";

        XWikiDocument document = new XWikiDocument(new DocumentReference("wiki", "space", "page"));
        BaseObject faqObj = new BaseObject();
        faqObj.setXClassReference(new DocumentReference("wiki", Arrays.asList("DocApp", "Code"),
            "DocumentationClass"));
        faqObj.setLargeStringValue("faq", faqContent);
        document.addXObject(faqObj);

        List<DocumentationViolation> violations = getChecker().check(document);

        assertEquals(1, violations.size());
        assertEquals("Use the Gallery macro when several images are displayed next to each other.",
            violations.get(0).getViolationMessage());
        assertEquals("{{image reference='test1.png'}}\n {{image reference='test2.png'}}",
            violations.get(0).getViolationContext());
        assertEquals(DocumentationViolationSeverity.ERROR, violations.get(0).getViolationSeverity());
    }
}
