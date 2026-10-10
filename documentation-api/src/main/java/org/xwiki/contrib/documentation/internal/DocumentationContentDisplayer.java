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

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.display.internal.DocumentDisplayer;
import org.xwiki.display.internal.DocumentDisplayerParameters;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.rendering.block.MetaDataBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.listener.MetaData;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.transformation.MacroTransformationContext;

import com.xpn.xwiki.doc.XWikiDocument;

/**
 * Displays the content of a documentation page as blocks, for {@link DocumentationContentMacro}.
 *
 * @version $Id$
 * @since 1.16
 */
@Component(roles = DocumentationContentDisplayer.class)
@Singleton
public class DocumentationContentDisplayer
{
    @Inject
    @Named("content")
    private DocumentDisplayer documentDisplayer;

    @Inject
    private EntityReferenceSerializer<String> entityReferenceSerializer;

    /**
     * Displays the content of the given document, executed with the rights of its content author.
     *
     * @param document the document whose content to display
     * @param context the context of the macro displaying the content
     * @return the executed content
     * @throws MacroExecutionException if the content cannot be displayed
     */
    public MetaDataBlock display(XWikiDocument document, MacroTransformationContext context)
        throws MacroExecutionException
    {
        // Same parameters as $services.display.content(), used by the AppWithinMinutes content field: the content is
        // executed in an isolated execution context, where the displayed document is the context document, so that it
        // runs with the rights of its own content author.
        DocumentDisplayerParameters displayParameters = new DocumentDisplayerParameters();
        displayParameters.setExecutionContextIsolated(true);
        displayParameters.setContentTranslated(true);
        displayParameters.setTransformationContextRestricted(context.getTransformationContext().isRestricted());
        displayParameters.setTargetSyntax(context.getTransformationContext().getTargetSyntax());
        if (context.getXDOM() != null) {
            // Generate the ids of the headings and images with the id generator of the page, so that they don't
            // collide with the ids of the rest of the page.
            displayParameters.setIdGenerator(context.getXDOM().getIdGenerator());
        }

        XDOM result;
        try {
            result = this.documentDisplayer.display(document, displayParameters);
        } catch (Exception e) {
            throw new MacroExecutionException(
                String.format("Failed to display the content of document [%s]", document.getDocumentReference()), e);
        }

        return wrap(result, this.entityReferenceSerializer.serialize(document.getDocumentReference()));
    }

    /**
     * @param xdom the blocks to wrap
     * @param source the serialized reference of the document the blocks come from
     * @return the blocks, wrapped in a block indicating where they come from, so that the relative references they
     *     contain are resolved against that document
     */
    static MetaDataBlock wrap(XDOM xdom, String source)
    {
        MetaDataBlock metadata = new MetaDataBlock(xdom.getChildren(), xdom.getMetaData());
        metadata.getMetaData().addMetaData(MetaData.SOURCE, source);
        metadata.getMetaData().addMetaData(MetaData.BASE, source);
        return metadata;
    }
}
