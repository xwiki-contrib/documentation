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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.documentation.DocumentationException;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.match.ClassBlockMatcher;
import org.xwiki.rendering.parser.ContentParser;
import org.xwiki.rendering.parser.MissingParserException;
import org.xwiki.rendering.parser.ParseException;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.security.authorization.ContextualAuthorizationManager;
import org.xwiki.security.authorization.Right;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.objects.BaseProperty;

/**
 * Finds the content fragments, i.e. the {@code DocApp.Code.ContentFragmentClass} objects holding wiki content meant to
 * be reused in several documentation pages with the {@code fragment} macro, and the pages using them.
 *
 * @version $Id$
 * @since 1.18
 */
@Component(roles = ContentFragments.class)
@Singleton
public class ContentFragments
{
    /**
     * The id of the macro displaying a content fragment.
     */
    public static final String MACRO_ID = "fragment";

    /**
     * The class of the objects holding the content fragments.
     */
    public static final LocalDocumentReference FRAGMENT_CLASS =
        new LocalDocumentReference(List.of("DocApp", "Code"), "ContentFragmentClass");

    /**
     * The property holding the name of a content fragment, unique in the page holding it, which is also the parameter
     * of the {@code fragment} macro holding the name of the content fragment to display.
     */
    public static final String NAME_PROPERTY = "name";

    /**
     * The property holding the wiki content of a content fragment.
     */
    public static final String CONTENT_PROPERTY = "content";

    /**
     * The parameter of the {@code fragment} macro holding the reference of the page holding the content fragment.
     */
    public static final String REFERENCE_PARAMETER = "reference";

    /**
     * Used to find the wiki content that may contain {@code fragment} macros without parsing all of it.
     */
    private static final String MACRO_MARKER = "{{" + MACRO_ID;

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Inject
    @Named("macro")
    private DocumentReferenceResolver<String> macroDocumentReferenceResolver;

    @Inject
    private ContentParser contentParser;

    @Inject
    private ContextualAuthorizationManager authorization;

    @Inject
    private Logger logger;

    /**
     * @param document the document holding the content fragment
     * @param name the name of the content fragment
     * @return the object holding the content fragment, {@code null} if the document has no content fragment with that
     *     name
     */
    public BaseObject getFragment(XWikiDocument document, String name)
    {
        for (BaseObject object : document.getXObjects(FRAGMENT_CLASS)) {
            // Removed objects leave null entries.
            if (object != null && name.equals(object.getStringValue(NAME_PROPERTY))) {
                return object;
            }
        }
        return null;
    }

    /**
     * @param reference the reference parameter of a {@code fragment} macro, {@code null} or empty for the document
     *     containing the macro
     * @param macroBlock the {@code fragment} macro
     * @param baseReference the document against which to resolve the reference when the macro isn't located inside a
     *     block indicating the document it comes from, {@code null} for the current document
     * @return the reference of the document holding the content fragment displayed by the macro
     */
    public DocumentReference getFragmentDocumentReference(String reference, MacroBlock macroBlock,
        DocumentReference baseReference)
    {
        String representation = StringUtils.defaultString(reference);
        return baseReference != null
            ? this.macroDocumentReferenceResolver.resolve(representation, macroBlock, baseReference)
            : this.macroDocumentReferenceResolver.resolve(representation, macroBlock);
    }

    /**
     * Finds the pages using a content fragment, among the document holding it and the documents linking to it, which
     * the {@code fragment} macros referencing the document do. Only the documents that the current user can view are
     * returned, and the documents linking to it that fail to be loaded are skipped, so that a single broken document
     * doesn't prevent listing the other usages.
     *
     * @param documentReference the document holding the content fragment
     * @param name the name of the content fragment
     * @return the documents using the content fragment in their content or in the wiki content of their objects
     * @throws DocumentationException if the document holding the content fragment or its backlinks cannot be loaded
     */
    public List<DocumentReference> getUsages(DocumentReference documentReference, String name)
        throws DocumentationException
    {
        XWikiContext xcontext = this.xcontextProvider.get();
        Set<DocumentReference> candidates = new LinkedHashSet<>();
        candidates.add(documentReference);
        try {
            candidates.addAll(
                xcontext.getWiki().getDocument(documentReference, xcontext).getBackLinkedReferences(xcontext));
        } catch (XWikiException e) {
            throw new DocumentationException(String.format(
                "Failed to find the pages using the content fragment [%s] of page [%s]", name, documentReference), e);
        }

        return candidates.stream()
            .filter(candidate -> this.authorization.hasAccess(Right.VIEW, candidate)
                && uses(candidate, documentReference, name, xcontext))
            .toList();
    }

    private boolean uses(DocumentReference candidate, DocumentReference fragmentDocumentReference, String name,
        XWikiContext xcontext)
    {
        try {
            return uses(xcontext.getWiki().getDocument(candidate, xcontext), fragmentDocumentReference, name);
        } catch (XWikiException e) {
            this.logger.warn("Failed to load document [{}] while looking for content fragment usages: [{}]", candidate,
                ExceptionUtils.getRootCauseMessage(e));
            return false;
        }
    }

    private boolean uses(XWikiDocument document, DocumentReference fragmentDocumentReference, String name)
    {
        DocumentReference documentReference = document.getDocumentReference();
        if (uses(document.getXDOM(), documentReference, document.getSyntax(), fragmentDocumentReference, name)) {
            return true;
        }
        // The wiki content of the objects, e.g. the FAQ of a documentation page or another content fragment.
        for (List<BaseObject> objects : document.getXObjects().values()) {
            for (BaseObject object : objects) {
                if (object != null
                    && uses(object, documentReference, document.getSyntax(), fragmentDocumentReference, name))
                {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean uses(BaseObject object, DocumentReference documentReference, Syntax syntax,
        DocumentReference fragmentDocumentReference, String name)
    {
        for (Object field : object.getFieldList()) {
            if (field instanceof BaseProperty<?> property && property.getValue() instanceof String value
                && usesInContent(value, documentReference, syntax, fragmentDocumentReference, name))
            {
                return true;
            }
        }
        return false;
    }

    private boolean usesInContent(String content, DocumentReference documentReference, Syntax syntax,
        DocumentReference fragmentDocumentReference, String name)
    {
        if (!content.contains(MACRO_MARKER)) {
            return false;
        }
        try {
            return uses(this.contentParser.parse(content, syntax, documentReference), documentReference, syntax,
                fragmentDocumentReference, name);
        } catch (MissingParserException | ParseException e) {
            this.logger.warn(
                "Failed to parse content of document [{}] while looking for content fragment usages: [{}]",
                documentReference, e.getMessage());
            return false;
        }
    }

    private boolean uses(Block block, DocumentReference documentReference, Syntax syntax,
        DocumentReference fragmentDocumentReference, String name)
    {
        if (block == null) {
            return false;
        }
        for (MacroBlock macroBlock : block.<MacroBlock>getBlocks(new ClassBlockMatcher(MacroBlock.class),
            Block.Axes.DESCENDANT))
        {
            if (isFragmentMacro(macroBlock, documentReference, fragmentDocumentReference, name)
                || (macroBlock.getContent() != null && usesInContent(macroBlock.getContent(), documentReference, syntax,
                    fragmentDocumentReference, name)))
            {
                return true;
            }
        }
        return false;
    }

    private boolean isFragmentMacro(MacroBlock macroBlock, DocumentReference documentReference,
        DocumentReference fragmentDocumentReference, String name)
    {
        return MACRO_ID.equals(macroBlock.getId()) && name.equals(macroBlock.getParameter(NAME_PROPERTY))
            && fragmentDocumentReference.equals(getFragmentDocumentReference(
                macroBlock.getParameter(REFERENCE_PARAMETER), macroBlock, documentReference));
    }
}
