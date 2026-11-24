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
package org.xwiki.contrib.documentation.internal;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceResolver;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.BulletedListBlock;
import org.xwiki.rendering.block.LinkBlock;
import org.xwiki.rendering.block.ListBLock;
import org.xwiki.rendering.block.ListItemBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.block.match.ClassBlockMatcher;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.listener.reference.ResourceType;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.parser.ParseException;
import org.xwiki.rendering.parser.Parser;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.rendering.util.ParserUtils;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

/**
 * Displays the Highlights of a documentation page or of a landing page (the {@code highlights} property of its
 * {@code DocApp.Code.DocumentationClass} or {@code DocApp.Code.LandingPageClass} object) as blocks, for
 * {@link DocumentationContentMacro}.
 * <p>
 * Each highlight is a first-level list item holding a link to the highlighted page. When the highlighted page has a
 * summary (the {@code summary} property of its {@code DocApp.Code.DocumentationClass} object) that the current user
 * can view, it's displayed as the description of the highlight, in place of the nested list items of the highlight.
 * Otherwise, the nested list items are displayed, so that a description written on the highlighting page is shown
 * until the highlighted page gets a summary.
 *
 * @version $Id$
 * @since 1.21
 */
@Component(roles = DocumentationHighlightsDisplayer.class)
@Singleton
public class DocumentationHighlightsDisplayer
{
    static final LocalDocumentReference LANDING_PAGE_CLASS =
        new LocalDocumentReference(DocumentationSummaries.CODE_SPACE, "LandingPageClass");

    /**
     * The link types that target a page.
     */
    private static final List<ResourceType> PAGE_RESOURCE_TYPES =
        List.of(ResourceType.DOCUMENT, ResourceType.PAGE, ResourceType.SPACE);

    private static final ParserUtils PARSER_UTILS = new ParserUtils();

    private static final String HIGHLIGHTS = "highlights";

    @Inject
    private Logger logger;

    @Inject
    private DocumentationPropertyDisplayer propertyDisplayer;

    @Inject
    private EntityReferenceResolver<ResourceReference> resourceReferenceResolver;

    @Inject
    private DocumentationSummaries summaries;

    @Inject
    @Named("plain/1.0")
    private Parser plainTextParser;

    /**
     * Displays the Highlights of the given document, executed with the same rights and restrictions as
     * {@code $doc.display('highlights')} executes them, each one described by the summary of the highlighted page
     * when it has one.
     *
     * @param document the document whose Highlights to display, holding a {@code DocApp.Code.DocumentationClass} or
     *     a {@code DocApp.Code.LandingPageClass} object
     * @param context the context of the macro displaying the Highlights
     * @return the executed Highlights, empty if the document has no Highlights
     * @throws MacroExecutionException if the Highlights cannot be displayed
     */
    public List<Block> display(XWikiDocument document, MacroTransformationContext context)
        throws MacroExecutionException
    {
        BaseObject object = document.getXObject(DocumentationSummaries.DOCUMENTATION_CLASS);
        if (object == null) {
            object = document.getXObject(LANDING_PAGE_CLASS);
        }
        if (object == null) {
            return List.of();
        }
        return this.propertyDisplayer.display(document, object, HIGHLIGHTS, "Highlights", context,
            xdom -> describeHighlights(xdom, document.getDocumentReference()));
    }

    /**
     * Describes each highlight of the passed Highlights with the summary of the highlighted page, when it has one.
     *
     * @param xdom the parsed Highlights, changed in place
     * @param documentReference the reference of the document holding the Highlights, against which the relative
     *     references of the highlighted pages are resolved
     */
    void describeHighlights(XDOM xdom, DocumentReference documentReference)
    {
        for (ListBLock list : xdom.<ListBLock>getBlocks(new ClassBlockMatcher(ListBLock.class),
            Block.Axes.DESCENDANT))
        {
            // Only the first-level list items are highlights, the nested ones are their descriptions.
            if (list.getFirstBlock(new ClassBlockMatcher(ListItemBlock.class), Block.Axes.ANCESTOR) == null) {
                for (Block item : list.getChildren()) {
                    describeHighlight(item, documentReference);
                }
            }
        }
    }

    private void describeHighlight(Block highlight, DocumentReference documentReference)
    {
        List<Block> ownContent = new ArrayList<>();
        List<Block> descriptions = new ArrayList<>();
        for (Block child : highlight.getChildren()) {
            if (child instanceof ListBLock) {
                descriptions.add(child);
            } else {
                ownContent.add(child);
            }
        }
        List<Block> summary = getSummary(ownContent, documentReference);
        if (!summary.isEmpty()) {
            descriptions.forEach(highlight::removeBlock);
            highlight.addChild(new BulletedListBlock(List.of(new ListItemBlock(summary))));
        }
    }

    private List<Block> getSummary(List<Block> highlightContent, DocumentReference documentReference)
    {
        for (Block block : highlightContent) {
            LinkBlock link = block.getFirstBlock(new ClassBlockMatcher(LinkBlock.class), Block.Axes.DESCENDANT_OR_SELF);
            // A link with an empty reference (for example to an anchor of the current page) doesn't highlight a page.
            if (link != null && PAGE_RESOURCE_TYPES.contains(link.getReference().getType())
                && !link.getReference().getReference().isEmpty())
            {
                EntityReference linkedReference = this.resourceReferenceResolver.resolve(link.getReference(),
                    EntityType.DOCUMENT, documentReference);
                return linkedReference != null ? getSummary(new DocumentReference(linkedReference)) : List.of();
            }
        }
        return List.of();
    }

    private List<Block> getSummary(DocumentReference highlightedReference)
    {
        String summary = this.summaries.getSummary(highlightedReference);
        if (summary.isEmpty()) {
            return List.of();
        }
        try {
            // The summary is plain text: it's displayed as is, never executed.
            List<Block> blocks = new ArrayList<>(this.plainTextParser.parse(new StringReader(summary)).getChildren());
            PARSER_UTILS.removeTopLevelParagraph(blocks);
            return blocks;
        } catch (ParseException e) {
            this.logger.warn("Failed to parse the summary of the highlighted page [{}]. Root cause: [{}]",
                highlightedReference, ExceptionUtils.getRootCauseMessage(e));
            return List.of();
        }
    }
}
