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

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.AttachmentReference;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.listener.reference.AttachmentResourceReference;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.macro.MacroRefactoring;
import org.xwiki.rendering.macro.MacroRefactoringException;
import org.xwiki.text.StringUtils;

/**
 * Refactoring of the Documentation {@code {{image}}} macro (see {@code DocApp.Code.ImageMacro}). The macro carries the
 * image attachment in its mandatory {@code reference} parameter; this component keeps that parameter in sync when the
 * referenced attachment is renamed or moved, and exposes the referenced attachment for backlinks.
 *
 * @version $Id$
 * @since 1.14
 */
@Component
@Singleton
@Named("image")
public class ImageMacroRefactoring implements MacroRefactoring
{
    private static final String REFERENCE = "reference";

    @Inject
    @Named("macro")
    private EntityReferenceResolver<String> macroEntityReferenceResolver;

    @Inject
    @Named("compact")
    private EntityReferenceSerializer<String> compactEntityReferenceSerializer;

    @Override
    public Optional<MacroBlock> replaceReference(MacroBlock macroBlock, DocumentReference currentDocumentReference,
        DocumentReference sourceReference, DocumentReference targetReference, boolean relative)
        throws MacroRefactoringException
    {
        // The image macro reference always points to an attachment, thus renaming a document has no effect on it (the
        // attachment move triggered by the document rename is handled by the AttachmentReference overload below).
        return Optional.empty();
    }

    @Override
    public Optional<MacroBlock> replaceReference(MacroBlock macroBlock, DocumentReference currentDocumentReference,
        AttachmentReference sourceReference, AttachmentReference targetReference, boolean relative)
        throws MacroRefactoringException
    {
        String reference = macroBlock.getParameter(REFERENCE);
        if (StringUtils.isEmpty(reference)) {
            return Optional.empty();
        }

        // Resolve the reference parameter into an absolute attachment reference. Pass the macro block (for its base
        // document metadata) and the source reference as base so that this works even when the context document is not
        // set (e.g. inside a refactoring job).
        EntityReference resolvedReference =
            this.macroEntityReferenceResolver.resolve(reference, EntityType.ATTACHMENT, macroBlock, sourceReference);

        Optional<MacroBlock> result;
        if (new AttachmentReference(resolvedReference).equals(sourceReference)) {
            MacroBlock newMacroBlock = (MacroBlock) macroBlock.clone();
            newMacroBlock.setParameter(REFERENCE,
                this.compactEntityReferenceSerializer.serialize(targetReference, currentDocumentReference));
            result = Optional.of(newMacroBlock);
        } else {
            result = Optional.empty();
        }
        return result;
    }

    @Override
    public Set<ResourceReference> extractReferences(MacroBlock macroBlock) throws MacroRefactoringException
    {
        String reference = macroBlock.getParameter(REFERENCE);
        if (StringUtils.isEmpty(reference)) {
            return Collections.emptySet();
        }
        return Collections.singleton(new AttachmentResourceReference(reference));
    }
}
