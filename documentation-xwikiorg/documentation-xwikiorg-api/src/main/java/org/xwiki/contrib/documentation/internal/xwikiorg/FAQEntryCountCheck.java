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

import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.documentation.DocumentationViolation;
import org.xwiki.contrib.documentation.DocumentationViolationSeverity;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.HeaderBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.block.match.ClassBlockMatcher;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

/**
 * Verify that documentation pages do not have more than 5 FAQ entries, and that the FAQ content does not exceed 15
 * lines. Having more than 5 FAQ entries or 15 lines likely indicates that some content should be moved to dedicated
 * documentation pages.
 *
 * @version $Id$
 * @since 1.15
 */
@Component
@Singleton
@Named("faqEntryCount")
public class FAQEntryCountCheck extends AbstractXDOMDocumentationCheck
{
    private static final int MAX_FAQ_ENTRIES = 5;

    private static final int MAX_FAQ_LINES = 15;

    private static final String CHECK_NAME = "FAQ Entry Count";

    @Override
    public List<DocumentationViolation> check(XWikiDocument document)
    {
        BaseObject docObject = document.getXObject(DOCUMENTATION_CLASS_REFERENCE);
        if (docObject == null) {
            return List.of();
        }
        String faqContent = docObject.getLargeStringValue("faq");
        if (faqContent.isEmpty()) {
            return List.of();
        }

        List<DocumentationViolation> violations = new ArrayList<>();

        long lineCount = faqContent.lines().count();
        if (lineCount > MAX_FAQ_LINES) {
            violations.add(new DocumentationViolation(
                "There are more than 15 lines in the FAQ of this page. This probably indicates that some "
                    + "documentation pages should be added.",
                "", DocumentationViolationSeverity.WARNING));
        }

        XDOM faqXDOM = parseFAQXDOM(document, document.getXDOM(), CHECK_NAME);
        if (faqXDOM != null) {
            List<HeaderBlock> headers =
                faqXDOM.getBlocks(new ClassBlockMatcher(HeaderBlock.class), Block.Axes.DESCENDANT);
            if (headers.size() > MAX_FAQ_ENTRIES) {
                violations.add(new DocumentationViolation(
                    "There are more than 5 FAQ entries in this page. This probably indicates that some documentation "
                        + "pages should be added.",
                    "", DocumentationViolationSeverity.WARNING));
            }
        }

        return violations;
    }
}
