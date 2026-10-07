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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.documentation.DocumentationViolation;
import org.xwiki.contrib.documentation.DocumentationViolationSeverity;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.HeaderBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.block.match.ClassBlockMatcher;
import org.xwiki.rendering.listener.HeaderLevel;
import org.xwiki.rendering.renderer.BlockRenderer;
import org.xwiki.rendering.renderer.printer.DefaultWikiPrinter;
import org.xwiki.rendering.renderer.printer.WikiPrinter;

import com.xpn.xwiki.doc.XWikiDocument;

/**
 * Verify that the entries of the FAQ field are level 2 headings phrased as questions (i.e. ending with a question
 * mark).
 *
 * @version $Id$
 * @since 1.15
 */
@Component
@Singleton
@Named("faqEntryHeading")
public class FAQEntryHeadingCheck extends AbstractXDOMDocumentationCheck
{
    private static final String CHECK_NAME = "FAQ Entry Heading";

    private static final String CONTEXT_FORMAT = "Heading : %s";

    @Inject
    @Named("plain/1.0")
    private BlockRenderer plainTextRenderer;

    @Override
    public List<DocumentationViolation> check(XWikiDocument document)
    {
        List<DocumentationViolation> violations = new ArrayList<>();

        XDOM faqXDOM = parseFAQXDOM(document, document.getXDOM(), CHECK_NAME);
        if (faqXDOM != null) {
            List<HeaderBlock> headers =
                faqXDOM.getBlocks(new ClassBlockMatcher(HeaderBlock.class), Block.Axes.DESCENDANT);
            for (HeaderBlock header : headers) {
                String text = getText(header);
                if (header.getLevel() != HeaderLevel.LEVEL2) {
                    violations.add(new DocumentationViolation("FAQ entries must be level 2 headings.",
                        String.format(CONTEXT_FORMAT, text), DocumentationViolationSeverity.WARNING));
                } else if (!text.endsWith("?")) {
                    violations.add(new DocumentationViolation(
                        "FAQ entries must be phrased as questions, ending with a question mark.",
                        String.format(CONTEXT_FORMAT, text), DocumentationViolationSeverity.WARNING));
                }
            }
        }

        return violations;
    }

    private String getText(HeaderBlock header)
    {
        WikiPrinter printer = new DefaultWikiPrinter();
        this.plainTextRenderer.render(header.getChildren(), printer);
        return printer.toString().trim();
    }
}
