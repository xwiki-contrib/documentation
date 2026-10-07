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
 * Verify that the page content doesn't contain level 1 headings (including inside macros whose content is wiki
 * content). The page structure fields (Content, FAQ, Highlights, Related) are displayed as level 1 headings, and
 * additional headings must be placed under them as level 2 headings or lower.
 *
 * @version $Id$
 * @since 1.15
 */
@Component
@Singleton
@Named("levelOneHeading")
public class LevelOneHeadingCheck extends AbstractXDOMDocumentationCheck
{
    private static final String CHECK_NAME = "Level 1 Heading";

    @Inject
    @Named("plain/1.0")
    private BlockRenderer plainTextRenderer;

    @Override
    public List<DocumentationViolation> check(XWikiDocument document)
    {
        List<DocumentationViolation> violations = new ArrayList<>();
        XDOM xdom = document.getXDOM();

        checkXDOM(xdom, violations);
        checkInsideWikiMacros(xdom, document, null, CHECK_NAME, macroXDOM -> checkXDOM(macroXDOM, violations));

        return violations;
    }

    private void checkXDOM(XDOM xdom, List<DocumentationViolation> violations)
    {
        List<HeaderBlock> headers = xdom.getBlocks(new ClassBlockMatcher(HeaderBlock.class), Block.Axes.DESCENDANT);
        for (HeaderBlock header : headers) {
            if (header.getLevel() == HeaderLevel.LEVEL1) {
                violations.add(new DocumentationViolation(
                    "Level 1 headings are not allowed in the page content, since the page structure fields are "
                        + "already displayed as level 1 headings. Use level 2 headings or lower.",
                    "Heading : " + getText(header), DocumentationViolationSeverity.ERROR));
            }
        }
    }

    private String getText(HeaderBlock header)
    {
        WikiPrinter printer = new DefaultWikiPrinter();
        this.plainTextRenderer.render(header.getChildren(), printer);
        return printer.toString().trim();
    }
}
