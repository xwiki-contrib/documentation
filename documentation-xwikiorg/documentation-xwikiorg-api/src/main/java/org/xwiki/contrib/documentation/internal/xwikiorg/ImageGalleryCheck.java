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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.documentation.DocumentationCheck;
import org.xwiki.contrib.documentation.DocumentationViolation;
import org.xwiki.contrib.documentation.DocumentationViolationSeverity;
import org.xwiki.model.reference.LocalDocumentReference;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

/**
 * Verify that if there are more than 1 image macro next to each other, they should be replaced by the Gallery macro.
 *
 * @version $Id$
 * @since 1.0
 */
@Component
@Singleton
@Named("imageGallery")
public class ImageGalleryCheck implements DocumentationCheck
{
    private static final Pattern PATTERN =
        Pattern.compile(".*(\\{\\{image.*}}[\\s\\n\\r]*\\{\\{image.*}}).*", Pattern.DOTALL);

    private static final LocalDocumentReference DOCUMENTATION_CLASS_REFERENCE =
        new LocalDocumentReference(List.of("DocApp", "Code"), "DocumentationClass");

    @Override
    public List<DocumentationViolation> check(XWikiDocument document)
    {
        List<DocumentationViolation> violations = new ArrayList<>();

        // Check the main document content. This also implicitly covers content inside rendering macros since
        // document.getContent() returns the full raw wiki syntax including all macro bodies.
        checkContent(document.getContent(), violations);

        // Also check the faq property content of the DocumentationClass XObject. The regex-based approach also
        // implicitly covers image macros inside rendering macros within the faq content.
        BaseObject docObject = document.getXObject(DOCUMENTATION_CLASS_REFERENCE);
        if (docObject != null) {
            String faqContent = docObject.getLargeStringValue("faq");
            if (!faqContent.isEmpty()) {
                checkContent(faqContent, violations);
            }
        }

        return violations;
    }

    private void checkContent(String content, List<DocumentationViolation> violations)
    {
        Matcher matcher = PATTERN.matcher(content);
        if (matcher.matches()) {
            violations.add(new DocumentationViolation("Use the Gallery macro when several images are displayed next "
                + "to each other.", matcher.group(1), DocumentationViolationSeverity.ERROR));
        }
    }
}
