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

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.inject.Named;

import org.junit.jupiter.api.Test;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.AttachmentReference;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.listener.reference.AttachmentResourceReference;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ImageMacroRefactoring}.
 *
 * @version $Id$
 */
@ComponentTest
class ImageMacroRefactoringTest
{
    private static final DocumentReference PAGE_REFERENCE = new DocumentReference("wiki", "Space", "Page");

    @InjectMockComponents
    private ImageMacroRefactoring imageMacroRefactoring;

    @MockComponent
    @Named("macro")
    private EntityReferenceResolver<String> macroEntityReferenceResolver;

    @MockComponent
    @Named("compact")
    private EntityReferenceSerializer<String> compactEntityReferenceSerializer;

    @Test
    void replaceAttachmentReferenceWhenReferenceParameterMissing() throws Exception
    {
        MacroBlock block = new MacroBlock("image", Map.of(), false);
        assertEquals(Optional.empty(), this.imageMacroRefactoring.replaceReference(block, PAGE_REFERENCE,
            new AttachmentReference("photo.png", PAGE_REFERENCE),
            new AttachmentReference("logo.png", PAGE_REFERENCE), false));
    }

    @Test
    void replaceAttachmentReferenceWhenReferenceParameterEmpty() throws Exception
    {
        MacroBlock block = new MacroBlock("image", Map.of("reference", ""), false);
        assertEquals(Optional.empty(), this.imageMacroRefactoring.replaceReference(block, PAGE_REFERENCE,
            new AttachmentReference("photo.png", PAGE_REFERENCE),
            new AttachmentReference("logo.png", PAGE_REFERENCE), false));
    }

    @Test
    void replaceAttachmentReferenceWhenAttachmentRenamed() throws Exception
    {
        MacroBlock block = new MacroBlock("image", Map.of("reference", "photo.png", "alt", "A logo"), false);
        AttachmentReference source = new AttachmentReference("photo.png", PAGE_REFERENCE);
        AttachmentReference target = new AttachmentReference("logo.png", PAGE_REFERENCE);

        when(this.macroEntityReferenceResolver.resolve("photo.png", EntityType.ATTACHMENT, block, source))
            .thenReturn(source);
        when(this.compactEntityReferenceSerializer.serialize(target, PAGE_REFERENCE)).thenReturn("logo.png");

        Optional<MacroBlock> result =
            this.imageMacroRefactoring.replaceReference(block, PAGE_REFERENCE, source, target, false);

        assertTrue(result.isPresent());
        assertEquals("logo.png", result.get().getParameter("reference"));
        // The other parameters are preserved.
        assertEquals("A logo", result.get().getParameter("alt"));
        // The original block is left untouched.
        assertEquals("photo.png", block.getParameter("reference"));
    }

    @Test
    void replaceAttachmentReferenceWhenReferenceDoesNotMatch() throws Exception
    {
        MacroBlock block = new MacroBlock("image", Map.of("reference", "photo.png"), false);
        AttachmentReference source = new AttachmentReference("other.png", PAGE_REFERENCE);
        AttachmentReference target = new AttachmentReference("logo.png", PAGE_REFERENCE);

        when(this.macroEntityReferenceResolver.resolve("photo.png", EntityType.ATTACHMENT, block, source))
            .thenReturn(new AttachmentReference("photo.png", PAGE_REFERENCE));

        assertEquals(Optional.empty(),
            this.imageMacroRefactoring.replaceReference(block, PAGE_REFERENCE, source, target, false));
    }

    @Test
    void replaceDocumentReferenceIsAlwaysEmpty() throws Exception
    {
        MacroBlock block = new MacroBlock("image", Map.of("reference", "photo.png"), false);
        assertEquals(Optional.empty(), this.imageMacroRefactoring.replaceReference(block, PAGE_REFERENCE,
            new DocumentReference("wiki", "Space", "Old"), new DocumentReference("wiki", "Space", "New"), false));
    }

    @Test
    void extractReferencesWhenReferenceParameter() throws Exception
    {
        MacroBlock block = new MacroBlock("image", Map.of("reference", "photo.png"), false);

        Set<ResourceReference> references = this.imageMacroRefactoring.extractReferences(block);

        assertEquals(1, references.size());
        ResourceReference reference = references.iterator().next();
        assertEquals(AttachmentResourceReference.class, reference.getClass());
        assertEquals("photo.png", reference.getReference());
    }

    @Test
    void extractReferencesWhenNoReferenceParameter() throws Exception
    {
        MacroBlock block = new MacroBlock("image", Map.of(), false);
        assertTrue(this.imageMacroRefactoring.extractReferences(block).isEmpty());
    }
}
