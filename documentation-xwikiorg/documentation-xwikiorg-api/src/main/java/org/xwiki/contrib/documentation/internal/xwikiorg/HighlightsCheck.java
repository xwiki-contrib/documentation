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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.documentation.DocumentationViolation;
import org.xwiki.contrib.documentation.DocumentationViolationSeverity;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryManager;
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
import com.xpn.xwiki.objects.BaseObject;

/**
 * Verify the Highlights field of documentation pages: it should be filled once the page has more than 15 child
 * documentation pages, it must not have more than 6 highlights, and each highlight must be a first-level list item
 * holding a link to the highlighted page, with exactly one nested list item holding its one-line description.
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

    /**
     * Highlights are recommended once a page has more child pages than this.
     */
    private static final int HIGHLIGHTS_THRESHOLD = 15;

    private static final String HIGHLIGHTS_PROPERTY = "highlights";

    private static final String CHECK_NAME = "Highlights";

    private static final String CONTEXT_FORMAT = "Highlight : %s";

    private static final String DEFAULT_DOCUMENT_NAME = "WebHome";

    /**
     * Count the direct child pages that are documentation pages: the nested pages (home pages of the direct child
     * spaces) and the terminal pages of the page's own space. Only documentation pages count, so that technical
     * children don't push a page over the threshold.
     */
    private static final String CHILD_PAGE_COUNT_QUERY = "select count(distinct doc.fullName) "
        + "from XWikiDocument doc, BaseObject obj "
        + "where obj.name = doc.fullName and obj.className = 'DocApp.Code.DocumentationClass' "
        + "and doc.translation = 0 "
        + "and ((doc.space = :space and doc.name <> 'WebHome') "
        + "or (doc.name = 'WebHome' and doc.space in "
        + "(select space.reference from XWikiSpace space where space.parent = :space)))";

    @Inject
    @Named("plain/1.0")
    private BlockRenderer plainTextRenderer;

    @Inject
    private QueryManager queryManager;

    @Inject
    @Named("local")
    private EntityReferenceSerializer<String> localSerializer;

    @Override
    public List<DocumentationViolation> check(XWikiDocument document)
    {
        List<DocumentationViolation> violations = new ArrayList<>();

        BaseObject docObject = document.getXObject(DOCUMENTATION_CLASS_REFERENCE);
        if (docObject != null && StringUtils.isBlank(docObject.getLargeStringValue(HIGHLIGHTS_PROPERTY))) {
            checkChildPageCount(document.getDocumentReference(), violations);
        }

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

    private void checkChildPageCount(DocumentReference documentReference, List<DocumentationViolation> violations)
    {
        // A terminal page cannot have child pages.
        if (!DEFAULT_DOCUMENT_NAME.equals(documentReference.getName())) {
            return;
        }

        try {
            List<Long> result = this.queryManager.<Long>createQuery(CHILD_PAGE_COUNT_QUERY, Query.HQL)
                .bindValue("space", this.localSerializer.serialize(documentReference.getLastSpaceReference()))
                .setWiki(documentReference.getWikiReference().getName())
                .execute();
            long childPageCount = result.get(0);
            if (childPageCount > HIGHLIGHTS_THRESHOLD) {
                violations.add(new DocumentationViolation(String.format(
                    "Highlights are recommended for pages with more than %s child pages, to guide readers to the "
                        + "most important ones.", HIGHLIGHTS_THRESHOLD),
                    String.format("Child pages: [%s]", childPageCount), DocumentationViolationSeverity.WARNING));
            }
        } catch (QueryException e) {
            this.logger.warn("Failed to count the child pages of [{}]. Ignoring the Highlights recommendation. "
                + ROOT_ERROR_CAUSE, documentReference, ExceptionUtils.getRootCauseMessage(e));
        }
    }

    private List<ListItemBlock> getFirstLevelItems(XDOM xdom)
    {
        List<ListItemBlock> items = new ArrayList<>();
        List<ListBLock> lists = xdom.getBlocks(new ClassBlockMatcher(ListBLock.class), Block.Axes.DESCENDANT);
        for (ListBLock list : lists) {
            // Only consider lists that are not nested inside another list item.
            if (list.getFirstBlock(new ClassBlockMatcher(ListItemBlock.class), Block.Axes.ANCESTOR) == null) {
                for (Block child : list.getChildren()) {
                    if (child instanceof ListItemBlock listItemBlock) {
                        items.add(listItemBlock);
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
