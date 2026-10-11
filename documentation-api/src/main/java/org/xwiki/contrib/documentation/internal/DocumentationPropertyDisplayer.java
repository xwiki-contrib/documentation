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

import java.util.List;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.HeaderBlock;
import org.xwiki.rendering.block.ImageBlock;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.MacroMarkerBlock;
import org.xwiki.rendering.block.MetaDataBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.parser.ContentParser;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.rendering.transformation.TransformationContext;
import org.xwiki.rendering.transformation.TransformationManager;
import org.xwiki.rendering.util.IdGenerator;
import org.xwiki.rendering.util.ParserUtils;
import org.xwiki.security.authorization.AuthorExecutor;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.objects.classes.BaseClass;
import com.xpn.xwiki.objects.classes.TextAreaClass;

/**
 * Displays a text area property of an xobject, holding wiki content, as blocks executed in place in the XDOM of the
 * page calling a macro (and not as already rendered HTML, as {@code $doc.display()} does), so that the headings it
 * contains are visible to the other macros of the page, such as the {@code toc} macro. Used to display the FAQ of a
 * documentation page and the content fragments.
 *
 * @version $Id$
 * @since 1.18
 */
@Component(roles = DocumentationPropertyDisplayer.class)
@Singleton
public class DocumentationPropertyDisplayer
{
    private static final ParserUtils PARSER_UTILS = new ParserUtils();

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Inject
    private ContentParser contentParser;

    @Inject
    private TransformationManager transformationManager;

    @Inject
    private AuthorExecutor authorExecutor;

    @Inject
    private EntityReferenceSerializer<String> entityReferenceSerializer;

    /**
     * Displays a property of an xobject, executed with the same rights and restrictions as
     * {@code $doc.display(property, object)} executes it.
     *
     * @param document the document holding the xobject
     * @param object the xobject holding the property
     * @param property the name of the text area property to display
     * @param label what the property holds (e.g. "FAQ"), used in the error messages
     * @param context the context of the macro displaying the property
     * @return the executed property, empty if the property is empty
     * @throws MacroExecutionException if the property cannot be displayed
     */
    public List<Block> display(XWikiDocument document, BaseObject object, String property, String label,
        MacroTransformationContext context) throws MacroExecutionException
    {
        String value = object.getLargeStringValue(property);
        if (StringUtils.isBlank(value)) {
            return List.of();
        }

        DocumentReference documentReference = document.getDocumentReference();
        XDOM xdom;
        try {
            xdom = this.contentParser.parse(value, document.getSyntax(), documentReference);
        } catch (Exception e) {
            throw new MacroExecutionException(
                String.format("Failed to parse the %s of document [%s]", label, documentReference), e);
        }
        if (context.getXDOM() != null) {
            makeIdsUnique(xdom, context.getXDOM().getIdGenerator());
        }
        if (context.isInline()) {
            // A property made of a single paragraph can be displayed inside a paragraph of the calling page.
            PARSER_UTILS.removeTopLevelParagraph(xdom.getChildren());
        }
        MetaDataBlock metadata =
            DocumentationContentDisplayer.wrap(xdom, this.entityReferenceSerializer.serialize(documentReference));

        // Same restrictions as $doc.display(property, object): the restricted mode of the current rendering, of the
        // document and of the property.
        TransformationContext transformationContext = context.getTransformationContext().clone();
        transformationContext.setRestricted(transformationContext.isRestricted() || document.isRestricted()
            || isRestricted(object.getXClass(this.xcontextProvider.get()), property));

        // Execute the property in place (inside the page XDOM) so that it's executed in the same conditions as the rest
        // of the page, but, like $doc.display(property, object), with the rights of the document (metadata) author
        // since that's the author of the object property.
        MacroBlock macroBlock = context.getCurrentMacroBlock();
        MacroMarkerBlock macroMarker = new MacroMarkerBlock(macroBlock.getId(), macroBlock.getParameters(),
            List.of(metadata), macroBlock.isInline());
        macroBlock.getParent().replaceChild(macroMarker, macroBlock);
        try {
            this.authorExecutor.call(() -> {
                this.transformationManager.performTransformations(metadata, transformationContext);
                return null;
            }, document.getAuthorReference(), documentReference);
        } catch (Exception e) {
            throw new MacroExecutionException(
                String.format("Failed to execute the %s of document [%s]", label, documentReference), e);
        } finally {
            // Put back the macro in the XDOM: the macro transformation replaces it with the returned blocks.
            macroMarker.getParent().replaceChild(macroBlock, macroMarker);
        }

        return List.of(metadata);
    }

    private static boolean isRestricted(BaseClass xclass, String property)
    {
        return xclass != null && xclass.get(property) instanceof TextAreaClass textArea && textArea.isRestricted();
    }

    /**
     * Generates the ids of the headings and images of the property with the id generator of the page, so that they
     * don't collide with the ids of the headings and images of the rest of the page.
     */
    private static void makeIdsUnique(XDOM xdom, IdGenerator idGenerator)
    {
        xdom.getBlocks(block -> {
            if (block instanceof HeaderBlock headerBlock) {
                headerBlock.setId(makeIdUnique(idGenerator, headerBlock.getId()));
            } else if (block instanceof ImageBlock imageBlock) {
                imageBlock.setId(makeIdUnique(idGenerator, imageBlock.getId()));
            }
            return false;
        }, Block.Axes.DESCENDANT);
    }

    private static String makeIdUnique(IdGenerator idGenerator, String id)
    {
        if (StringUtils.isBlank(id)) {
            return id;
        }
        // The generated ids are a prefix letter (e.g. "H" for headings) followed by the text-based part.
        return idGenerator.generateUniqueId(id.substring(0, 1), id.substring(1));
    }
}
