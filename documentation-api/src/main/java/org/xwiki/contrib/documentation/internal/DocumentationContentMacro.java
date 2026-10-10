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
import org.xwiki.contrib.documentation.internal.DocumentationContentMacroParameters.Field;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.macro.AbstractMacro;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.transformation.MacroTransformationContext;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;

/**
 * Displays the content or the FAQ of the current documentation page as part of the page XDOM (and not as already
 * rendered HTML, as {@code $doc.display()} does), so that the headings they contain are visible to the other macros of
 * the page, such as the {@code toc} macro of {@code DocApp.Code.DocumentationSheet}.
 * <p>
 * Each field is executed with the same rights as {@code $doc.display()} executes it: the content with the rights of
 * the document content author, and the FAQ with the rights of the document (metadata) author, never with the rights of
 * the author of the page calling the macro. The macro only displays fields of the current document, which the current
 * user is already viewing.
 *
 * @version $Id$
 * @since 1.16
 */
@Component
@Named("documentationContent")
@Singleton
public class DocumentationContentMacro extends AbstractMacro<DocumentationContentMacroParameters>
{
    /**
     * The XWiki context key holding the translation of the context document, which is also the document holding the
     * unsaved changes when previewing it.
     */
    private static final String TDOC = "tdoc";

    /**
     * The fields being displayed by the current thread, used to stop a field which contains this macro from displaying
     * itself again, infinitely.
     */
    private final ThreadLocal<Set<String>> fieldsBeingDisplayed = ThreadLocal.withInitial(HashSet::new);

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Inject
    private DocumentationContentDisplayer contentDisplayer;

    @Inject
    private DocumentationFAQDisplayer faqDisplayer;

    @Inject
    private EntityReferenceSerializer<String> entityReferenceSerializer;

    /**
     * Default constructor.
     */
    public DocumentationContentMacro()
    {
        super("Documentation Content",
            "Displays the content or the FAQ of the current documentation page, keeping their headings visible to the "
                + "table of contents.",
            DocumentationContentMacroParameters.class);
        // This macro is only meant to be used by the documentation sheet, so it's not proposed to page authors.
        setDefaultCategories(Set.of(DEFAULT_CATEGORY_INTERNAL));
    }

    @Override
    public boolean supportsInlineMode()
    {
        return false;
    }

    @Override
    public List<Block> execute(DocumentationContentMacroParameters parameters, String content,
        MacroTransformationContext context) throws MacroExecutionException
    {
        XWikiContext xcontext = this.xcontextProvider.get();
        XWikiDocument document = xcontext.getDoc();
        if (document == null) {
            return List.of();
        }

        String fieldKey = this.entityReferenceSerializer.serialize(document.getDocumentReference()) + '/'
            + parameters.getField();
        Set<String> fields = this.fieldsBeingDisplayed.get();
        if (!fields.add(fieldKey)) {
            // The field contains this macro: displaying it again would never end.
            return List.of();
        }
        try {
            if (parameters.getField() == Field.FAQ) {
                return this.faqDisplayer.display(document, context);
            } else {
                return List.of(this.contentDisplayer.display(getContentDocument(document, xcontext), context));
            }
        } finally {
            fields.remove(fieldKey);
            if (fields.isEmpty()) {
                // Don't keep the set in the thread once the outermost field is displayed (threads are pooled).
                this.fieldsBeingDisplayed.remove();
            }
        }
    }

    private XWikiDocument getContentDocument(XWikiDocument document, XWikiContext xcontext)
    {
        // Same document as the one displayed by the AppWithinMinutes content field ($tdoc): the current translation,
        // with its unsaved changes when previewing.
        if (xcontext.get(TDOC) instanceof XWikiDocument tdoc
            && document.getDocumentReference().equals(tdoc.getDocumentReference()))
        {
            return tdoc;
        }
        return document;
    }
}
