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
import org.xwiki.rendering.block.LinkBlock;
import org.xwiki.rendering.block.ListBLock;
import org.xwiki.rendering.block.ListItemBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.block.match.ClassBlockMatcher;
import org.xwiki.rendering.renderer.BlockRenderer;
import org.xwiki.rendering.renderer.printer.DefaultWikiPrinter;
import org.xwiki.rendering.renderer.printer.WikiPrinter;

import com.xpn.xwiki.doc.XWikiDocument;

/**
 * Verify the Highlights field of documentation pages: it must not have more than 6 highlights, and each highlight
 * must be a first-level list item holding a link to the highlighted page, with exactly one nested list item holding
 * its one-line description.
 *
 * @version $Id$
 * @since 1.15
 */
@Component
@Singleton
@Named("highlights")
public class HighlightsCheck extends AbstractXDOMDocumentationCheck
{
    private static final int MAX_HIGHLIGHTS = 6;

    private static final String HIGHLIGHTS_PROPERTY = "highlights";

    private static final String CHECK_NAME = "Highlights";

    private static final String CONTEXT_FORMAT = "Highlight : %s";

    @Inject
    @Named("plain/1.0")
    private BlockRenderer plainTextRenderer;

    @Override
    public List<DocumentationViolation> check(XWikiDocument document)
    {
        List<DocumentationViolation> violations = new ArrayList<>();

        XDOM highlightsXDOM =
            parseXPropertyXDOM(document, document.getXDOM(), HIGHLIGHTS_PROPERTY, CHECK_NAME, CHECK_NAME);
        if (highlightsXDOM != null) {
            List<ListItemBlock> highlights = getFirstLevelItems(highlightsXDOM);
            if (highlights.size() > MAX_HIGHLIGHTS) {
                violations.add(new DocumentationViolation(String.format(
                    "There are %s highlights in this page, while a maximum of %s is allowed.", highlights.size(),
                    MAX_HIGHLIGHTS), "", DocumentationViolationSeverity.ERROR));
            }
            for (ListItemBlock highlight : highlights) {
                checkHighlight(highlight, violations);
            }
        }

        return violations;
    }

    private List<ListItemBlock> getFirstLevelItems(XDOM xdom)
    {
        List<ListItemBlock> items = new ArrayList<>();
        List<ListBLock> lists = xdom.getBlocks(new ClassBlockMatcher(ListBLock.class), Block.Axes.DESCENDANT);
        for (ListBLock list : lists) {
            // Only consider lists that are not nested inside another list item.
            if (list.getFirstBlock(new ClassBlockMatcher(ListItemBlock.class), Block.Axes.ANCESTOR) == null) {
                for (Block child : list.getChildren()) {
                    if (child instanceof ListItemBlock) {
                        items.add((ListItemBlock) child);
                    }
                }
            }
        }
        return items;
    }

    private void checkHighlight(ListItemBlock highlight, List<DocumentationViolation> violations)
    {
        List<Block> ownContent = new ArrayList<>();
        int nestedItemCount = 0;
        for (Block child : highlight.getChildren()) {
            if (child instanceof ListBLock) {
                nestedItemCount += child.getBlocks(new ClassBlockMatcher(ListItemBlock.class), Block.Axes.CHILD)
                    .size();
            } else {
                ownContent.add(child);
            }
        }

        String context = String.format(CONTEXT_FORMAT, getText(ownContent));
        if (!hasLink(ownContent)) {
            violations.add(new DocumentationViolation(
                "A highlight must hold a link to the highlighted page.", context,
                DocumentationViolationSeverity.WARNING));
        }
        if (nestedItemCount != 1) {
            violations.add(new DocumentationViolation(
                "A highlight must have exactly one nested list item, holding a one-line description of the "
                    + "highlighted page.", context, DocumentationViolationSeverity.WARNING));
        }
    }

    private boolean hasLink(List<Block> blocks)
    {
        ClassBlockMatcher linkMatcher = new ClassBlockMatcher(LinkBlock.class);
        for (Block block : blocks) {
            if (block.getFirstBlock(linkMatcher, Block.Axes.DESCENDANT_OR_SELF) != null) {
                return true;
            }
        }
        return false;
    }

    private String getText(List<Block> blocks)
    {
        WikiPrinter printer = new DefaultWikiPrinter();
        this.plainTextRenderer.render(blocks, printer);
        return printer.toString().trim();
    }
}
