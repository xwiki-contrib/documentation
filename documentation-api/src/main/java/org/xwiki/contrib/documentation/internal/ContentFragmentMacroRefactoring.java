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

import java.util.Optional;
import java.util.Set;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.AttachmentReference;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.listener.reference.DocumentResourceReference;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.macro.MacroRefactoring;

/**
 * Refactoring of the {@code fragment} macro (see {@link ContentFragmentMacro}). The macro carries the page holding the
 * content fragment in its optional {@code reference} parameter; this component keeps that parameter in sync when the
 * page holding the content fragment, or the page containing the macro, is renamed or moved, and exposes the page
 * holding the content fragment for backlinks, which are used to find the pages using a content fragment.
 *
 * @version $Id$
 * @since 1.18
 */
@Component
@Singleton
@Named(ContentFragments.MACRO_ID)
public class ContentFragmentMacroRefactoring implements MacroRefactoring
{
    @Inject
    private ContentFragments contentFragments;

    @Inject
    @Named("compact")
    private EntityReferenceSerializer<String> compactEntityReferenceSerializer;

    @Override
    public Optional<MacroBlock> replaceReference(MacroBlock macroBlock, DocumentReference currentDocumentReference,
        DocumentReference sourceReference, DocumentReference targetReference, boolean relative)
    {
        String reference = macroBlock.getParameter(ContentFragments.REFERENCE_PARAMETER);
        if (StringUtils.isEmpty(reference)) {
            // The content fragment is held by the page containing the macro, and moves with it.
            return Optional.empty();
        }

        // Pass the source reference as base, since the context document may not be set in the refactoring job.
        DocumentReference fragmentReference =
            this.contentFragments.getFragmentDocumentReference(reference, macroBlock, sourceReference);
        DocumentReference newFragmentReference;
        if (fragmentReference.equals(sourceReference)) {
            // The page holding the content fragment has been renamed.
            newFragmentReference = targetReference;
        } else if (!fragmentReference.equals(
            this.contentFragments.getFragmentDocumentReference(reference, macroBlock, targetReference)))
        {
            // The page containing the macro has been renamed and the reference, relative to it, now points elsewhere.
            newFragmentReference = fragmentReference;
        } else {
            return Optional.empty();
        }

        MacroBlock newMacroBlock = (MacroBlock) macroBlock.clone();
        newMacroBlock.setParameter(ContentFragments.REFERENCE_PARAMETER,
            this.compactEntityReferenceSerializer.serialize(newFragmentReference, currentDocumentReference));
        return Optional.of(newMacroBlock);
    }

    @Override
    public Optional<MacroBlock> replaceReference(MacroBlock macroBlock, DocumentReference currentDocumentReference,
        AttachmentReference sourceReference, AttachmentReference targetReference, boolean relative)
    {
        // The macro doesn't reference attachments.
        return Optional.empty();
    }

    @Override
    public Set<ResourceReference> extractReferences(MacroBlock macroBlock)
    {
        String reference = macroBlock.getParameter(ContentFragments.REFERENCE_PARAMETER);
        if (StringUtils.isEmpty(reference)) {
            return Set.of();
        }
        return Set.of(new DocumentResourceReference(reference));
    }
}
