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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.macro.AbstractMacro;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.security.authorization.ContextualAuthorizationManager;
import org.xwiki.security.authorization.Right;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

/**
 * Displays a content fragment: wiki content held by a {@code DocApp.Code.ContentFragmentClass} object of a
 * documentation page, meant to be reused in several pages without creating a (hidden) page only to hold it.
 * <p>
 * The content fragment is displayed as part of the page XDOM, so that its headings are visible to the {@code toc}
 * macro, and is executed with the rights of the author of the page holding it (the author of the object), never with
 * the rights of the author of the page calling the macro.
 *
 * @version $Id$
 * @since 1.18
 */
@Component
@Named(ContentFragments.MACRO_ID)
@Singleton
public class ContentFragmentMacro extends AbstractMacro<ContentFragmentMacroParameters>
{
    /**
     * The content fragments being displayed by the current thread, used to stop a content fragment which displays
     * itself, directly or through other content fragments, from being displayed infinitely.
     */
    private final ThreadLocal<Set<String>> fragmentsBeingDisplayed = ThreadLocal.withInitial(HashSet::new);

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Inject
    private ContentFragments contentFragments;

    @Inject
    private DocumentationPropertyDisplayer propertyDisplayer;

    @Inject
    private ContextualAuthorizationManager authorization;

    @Inject
    private EntityReferenceSerializer<String> entityReferenceSerializer;

    /**
     * Default constructor.
     */
    public ContentFragmentMacro()
    {
        super("Content Fragment",
            "Displays a content fragment, i.e. some content held by a documentation page and reused in several pages.",
            ContentFragmentMacroParameters.class);
        setDefaultCategories(Set.of(DEFAULT_CATEGORY_CONTENT));
    }

    @Override
    public boolean supportsInlineMode()
    {
        return true;
    }

    @Override
    public List<Block> execute(ContentFragmentMacroParameters parameters, String content,
        MacroTransformationContext context) throws MacroExecutionException
    {
        String name = parameters.getName();
        DocumentReference documentReference = this.contentFragments.getFragmentDocumentReference(
            parameters.getReference(), context.getCurrentMacroBlock(), null);
        String serializedReference = this.entityReferenceSerializer.serialize(documentReference);

        XWikiDocument document = getDocument(documentReference, serializedReference);
        BaseObject object = this.contentFragments.getFragment(document, name);
        if (object == null) {
            throw new MacroExecutionException(
                String.format("There's no content fragment named [%s] in page [%s].", name, serializedReference));
        }

        String fragmentKey = serializedReference + '/' + name;
        Set<String> fragments = this.fragmentsBeingDisplayed.get();
        if (!fragments.add(fragmentKey)) {
            throw new MacroExecutionException(String.format(
                "The content fragment [%s] of page [%s] cannot display itself.", name, serializedReference));
        }
        try {
            return this.propertyDisplayer.display(document, object, ContentFragments.CONTENT_PROPERTY,
                String.format("content fragment [%s]", name), context);
        } finally {
            fragments.remove(fragmentKey);
            if (fragments.isEmpty()) {
                // Don't keep the set in the thread once the outermost content fragment is displayed (threads are
                // pooled).
                this.fragmentsBeingDisplayed.remove();
            }
        }
    }

    private XWikiDocument getDocument(DocumentReference documentReference, String serializedReference)
        throws MacroExecutionException
    {
        XWikiContext xcontext = this.xcontextProvider.get();
        XWikiDocument currentDocument = xcontext.getDoc();
        if (currentDocument != null && documentReference.equals(currentDocument.getDocumentReference())) {
            // The current document, which the current user is already viewing, with its unsaved changes when
            // previewing it.
            return currentDocument;
        }

        if (!this.authorization.hasAccess(Right.VIEW, documentReference)) {
            throw new MacroExecutionException(
                String.format("You are not allowed to view the page [%s].", serializedReference));
        }
        try {
            return xcontext.getWiki().getDocument(documentReference, xcontext);
        } catch (XWikiException e) {
            throw new MacroExecutionException(String.format("Failed to load the page [%s].", serializedReference), e);
        }
    }
}
