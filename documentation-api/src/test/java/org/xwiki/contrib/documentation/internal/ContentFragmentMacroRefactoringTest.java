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

import jakarta.inject.Named;

import org.junit.jupiter.api.Test;
import org.xwiki.model.reference.AttachmentReference;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.listener.reference.DocumentResourceReference;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ContentFragmentMacroRefactoring}.
 *
 * @version $Id$
 */
@ComponentTest
class ContentFragmentMacroRefactoringTest
{
    private static final DocumentReference CURRENT = new DocumentReference("wiki", "Space", "Current");

    private static final DocumentReference FRAGMENTS = new DocumentReference("wiki", "Space", "Fragments");

    private static final DocumentReference RENAMED_FRAGMENTS = new DocumentReference("wiki", "Other", "Fragments");

    private static final DocumentReference MOVED_CURRENT = new DocumentReference("wiki", "Other", "Current");

    @InjectMockComponents
    private ContentFragmentMacroRefactoring refactoring;

    @MockComponent
    private ContentFragments contentFragments;

    @MockComponent
    @Named("compact")
    private EntityReferenceSerializer<String> compactSerializer;

    @Test
    void replaceReferenceWhenThePageHoldingTheFragmentIsRenamed()
    {
        MacroBlock macroBlock = macro("Fragments");
        when(this.contentFragments.getFragmentDocumentReference("Fragments", macroBlock, FRAGMENTS))
            .thenReturn(FRAGMENTS);
        when(this.compactSerializer.serialize(RENAMED_FRAGMENTS, CURRENT)).thenReturn("Other.Fragments");

        Optional<MacroBlock> result =
            this.refactoring.replaceReference(macroBlock, CURRENT, FRAGMENTS, RENAMED_FRAGMENTS, false);

        assertEquals("Other.Fragments", result.get().getParameter("reference"));
        assertEquals("fragment-name", result.get().getParameter("name"));
        // The original macro block is left untouched.
        assertEquals("Fragments", macroBlock.getParameter("reference"));
    }

    @Test
    void replaceRelativeReferenceWhenThePageContainingTheMacroIsMoved()
    {
        MacroBlock macroBlock = macro("Fragments");
        when(this.contentFragments.getFragmentDocumentReference("Fragments", macroBlock, CURRENT))
            .thenReturn(FRAGMENTS);
        when(this.contentFragments.getFragmentDocumentReference("Fragments", macroBlock, MOVED_CURRENT))
            .thenReturn(RENAMED_FRAGMENTS);
        when(this.compactSerializer.serialize(FRAGMENTS, MOVED_CURRENT)).thenReturn("Space.Fragments");

        Optional<MacroBlock> result =
            this.refactoring.replaceReference(macroBlock, MOVED_CURRENT, CURRENT, MOVED_CURRENT, true);

        assertEquals("Space.Fragments", result.get().getParameter("reference"));
    }

    @Test
    void keepAbsoluteReferenceWhenThePageContainingTheMacroIsMoved()
    {
        MacroBlock macroBlock = macro("Space.Fragments");
        when(this.contentFragments.getFragmentDocumentReference("Space.Fragments", macroBlock, CURRENT))
            .thenReturn(FRAGMENTS);
        when(this.contentFragments.getFragmentDocumentReference("Space.Fragments", macroBlock, MOVED_CURRENT))
            .thenReturn(FRAGMENTS);

        assertTrue(
            this.refactoring.replaceReference(macroBlock, MOVED_CURRENT, CURRENT, MOVED_CURRENT, true).isEmpty());
    }

    @Test
    void keepFragmentOfTheCurrentPage()
    {
        assertTrue(this.refactoring.replaceReference(macro(null), MOVED_CURRENT, CURRENT, MOVED_CURRENT, true)
            .isEmpty());
    }

    @Test
    void keepReferenceWhenAnAttachmentIsMoved()
    {
        AttachmentReference source = new AttachmentReference("image.png", FRAGMENTS);
        AttachmentReference target = new AttachmentReference("image.png", RENAMED_FRAGMENTS);

        assertTrue(this.refactoring.replaceReference(macro("Fragments"), CURRENT, source, target, false).isEmpty());
    }

    @Test
    void extractReferences()
    {
        assertEquals(Set.of(new DocumentResourceReference("Space.Fragments")),
            this.refactoring.extractReferences(macro("Space.Fragments")));
        assertEquals(Set.of(), this.refactoring.extractReferences(macro(null)));
    }

    private static MacroBlock macro(String reference)
    {
        MacroBlock macroBlock = new MacroBlock("fragment", Map.of("name", "fragment-name"), false);
        if (reference != null) {
            macroBlock.setParameter("reference", reference);
        }
        return macroBlock;
    }
}
