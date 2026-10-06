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
package org.xwiki.contrib.documentation.internal.xwikiorg;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xwiki.contrib.documentation.DocumentationCheck;
import org.xwiki.contrib.documentation.DocumentationViolation;
import org.xwiki.contrib.documentation.DocumentationViolationSeverity;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.ImageBlock;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.listener.reference.ResourceType;
import org.xwiki.rendering.macro.Macro;
import org.xwiki.rendering.macro.MacroContentParser;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.macro.MacroId;
import org.xwiki.rendering.macro.MacroManager;
import org.xwiki.rendering.macro.descriptor.ContentDescriptor;
import org.xwiki.rendering.macro.descriptor.MacroDescriptor;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.test.LogLevel;
import org.xwiki.test.annotation.AllComponents;
import org.xwiki.test.junit5.LogCaptureExtension;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.test.MockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.InjectMockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.OldcoreTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ImageOtherPageAttachmentCheck}.
 *
 * @version $Id$
 */
@AllComponents
@OldcoreTest
class ImageOtherPageAttachmentCheckTest
{
    private static final String MESSAGE = "Images should not reference an attachment of another page, since the "
        + "image is no longer displayed when that page is moved or renamed. Attach the image to the current page "
        + "instead.";

    private static final String IMAGE = "image";

    private static final String REFERENCE = "reference";

    @InjectMockitoOldcore
    private MockitoOldcore oldcore;

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    private XWikiDocument createDocument(XDOM xdom)
    {
        return new XWikiDocument(new DocumentReference("wiki", "space", "page"))
        {
            @Override
            public XDOM getXDOM()
            {
                return xdom;
            }

            @Override
            public Syntax getSyntax()
            {
                return Syntax.XWIKI_2_1;
            }
        };
    }

    private DocumentationCheck getChecker() throws Exception
    {
        return this.oldcore.getMocker().getInstance(DocumentationCheck.class, "imageOtherPageAttachment");
    }

    private void assertViolation(DocumentationViolation violation, String expectedContext)
    {
        assertEquals(MESSAGE, violation.getViolationMessage());
        assertEquals(expectedContext, violation.getViolationContext());
        assertEquals(DocumentationViolationSeverity.WARNING, violation.getViolationSeverity());
    }

    @ParameterizedTest
    @ValueSource(strings = { "test.png", "page@test.png", "space.page@test.png", "wiki:space.page@test.png" })
    void checkWhenImageMacroReferencesCurrentPage(String reference) throws Exception
    {
        MacroBlock imageBlock = new MacroBlock(IMAGE, Map.of(REFERENCE, reference), false);
        XWikiDocument document = createDocument(new XDOM(List.of(imageBlock)));

        assertEquals(0, getChecker().check(document).size());
    }

    @Test
    void checkWhenImageMacroHasNoReference() throws Exception
    {
        MacroBlock imageBlock = new MacroBlock(IMAGE, Map.of(), false);
        XWikiDocument document = createDocument(new XDOM(List.of(imageBlock)));

        assertEquals(0, getChecker().check(document).size());
    }

    @Test
    void checkWhenImageMacroReferencesOtherPage() throws Exception
    {
        MacroBlock imageBlock1 = new MacroBlock(IMAGE, Map.of(REFERENCE, "Other.WebHome@test.png"), false);
        MacroBlock imageBlock2 = new MacroBlock(IMAGE, Map.of(REFERENCE, "sibling@test.png"), false);
        MacroBlock imageBlock3 = new MacroBlock(IMAGE, Map.of(REFERENCE, "otherwiki:space.page@test.png"), false);
        XWikiDocument document = createDocument(new XDOM(List.of(imageBlock1, imageBlock2, imageBlock3)));

        List<DocumentationViolation> violations = getChecker().check(document);
        assertEquals(3, violations.size());
        assertViolation(violations.get(0), "Image reference : Other.WebHome@test.png, Page : Other.WebHome");
        assertViolation(violations.get(1), "Image reference : sibling@test.png, Page : sibling");
        assertViolation(violations.get(2),
            "Image reference : otherwiki:space.page@test.png, Page : otherwiki:space.page");
    }

    /**
     * Register a macro manager for which the gallery macro has no wiki content, so that the check doesn't log a
     * lookup failure for it (the gallery macro isn't available in this test environment).
     */
    private void registerMacroManagerWithoutWikiContentMacros() throws Exception
    {
        MacroManager macroManager = this.oldcore.getMocker().registerMockComponent(MacroManager.class);
        Macro<?> macro = mock(Macro.class);
        when(macro.getDescriptor()).thenReturn(mock(MacroDescriptor.class));
        doReturn(macro).when(macroManager).getMacro(any());
    }

    @Test
    void checkWhenGalleryImagesReferenceOtherPage() throws Exception
    {
        registerMacroManagerWithoutWikiContentMacros();

        // The gallery content is mocked since untyped image references are parsed as attachments only when a wiki
        // model is available, which is not the case in this test environment.
        MacroContentParser contentParser =
            this.oldcore.getMocker().registerMockComponent(MacroContentParser.class);
        ImageBlock currentPageImage =
            new ImageBlock(new ResourceReference("alice.png", ResourceType.ATTACHMENT), true);
        ImageBlock otherPageImage =
            new ImageBlock(new ResourceReference("Other.Page@bob.png", ResourceType.ATTACHMENT), true);
        ImageBlock urlImage =
            new ImageBlock(new ResourceReference("http://www.xwiki.org/logo.png", ResourceType.URL), true);
        when(contentParser.parse(any(), any(), anyBoolean(), anyBoolean()))
            .thenReturn(new XDOM(List.of(currentPageImage, otherPageImage, urlImage)));

        MacroBlock galleryBlock = new MacroBlock("gallery", Map.of(), "...", false);
        MacroBlock imageBlock = new MacroBlock(IMAGE, Map.of(REFERENCE, "Other.Page@carol.png"), false);
        XWikiDocument document = createDocument(new XDOM(List.of(galleryBlock, imageBlock)));

        List<DocumentationViolation> violations = getChecker().check(document);
        assertEquals(2, violations.size());
        assertViolation(violations.get(0), "Image reference : Other.Page@bob.png, Page : Other.Page");
        assertViolation(violations.get(1), "Image reference : Other.Page@carol.png, Page : Other.Page");
    }

    @Test
    void checkWhenGalleryContentFailsToParse() throws Exception
    {
        registerMacroManagerWithoutWikiContentMacros();
        MacroContentParser contentParser =
            this.oldcore.getMocker().registerMockComponent(MacroContentParser.class);
        when(contentParser.parse(any(), any(), anyBoolean(), anyBoolean()))
            .thenThrow(new MacroExecutionException("parse error"));

        MacroBlock galleryBlock = new MacroBlock("gallery", Map.of(), "image:Other.Page@bob.png", false);
        XWikiDocument document = createDocument(new XDOM(List.of(galleryBlock)));

        assertEquals(0, getChecker().check(document).size());
        assertEquals("Failed to parse the content of the gallery macro [image:Other.Page@bob.png]. Ignoring Image "
            + "Other Page Attachment check inside it. Root error cause: [MacroExecutionException: parse error]",
            this.logCapture.getMessage(0));
    }

    @Test
    void checkWhenImagesAreInsideWikiContentMacro() throws Exception
    {
        MacroManager macroManager = this.oldcore.getMocker().registerMockComponent(MacroManager.class);
        Macro<?> macro = mock(Macro.class);
        MacroDescriptor descriptor = mock(MacroDescriptor.class);
        ContentDescriptor contentDescriptor = mock(ContentDescriptor.class);
        when(contentDescriptor.getType()).thenReturn(Block.LIST_BLOCK_TYPE);
        when(descriptor.getContentDescriptor()).thenReturn(contentDescriptor);
        when(macro.getDescriptor()).thenReturn(descriptor);
        doReturn(macro).when(macroManager).getMacro(new MacroId("note"));

        MacroContentParser contentParser =
            this.oldcore.getMocker().registerMockComponent(MacroContentParser.class);
        // First parse() call is for the note macro content -> returns an image macro and a gallery macro.
        MacroBlock imageBlock = new MacroBlock(IMAGE, Map.of(REFERENCE, "Other.Page@foo.png"), false);
        MacroBlock galleryBlock = new MacroBlock("gallery", Map.of(), "image:Other.Page@bar.png", false);
        // Second parse() call is for the gallery macro content.
        ImageBlock galleryImageBlock =
            new ImageBlock(new ResourceReference("Other.Page@bar.png", ResourceType.ATTACHMENT), true);
        when(contentParser.parse(any(), any(), anyBoolean(), anyBoolean()))
            .thenReturn(new XDOM(List.of(imageBlock, galleryBlock)))
            .thenReturn(new XDOM(List.of(galleryImageBlock)));

        MacroBlock noteBlock = new MacroBlock("note", Map.of(), "...", false);
        XWikiDocument document = createDocument(new XDOM(List.of(noteBlock)));

        List<DocumentationViolation> violations = getChecker().check(document);
        assertEquals(2, violations.size());
        assertViolation(violations.get(0), "Image reference : Other.Page@foo.png, Page : Other.Page");
        assertViolation(violations.get(1), "Image reference : Other.Page@bar.png, Page : Other.Page");
    }

    @Test
    void checkWhenImageMacroReferencesOtherPageInFaqProperty() throws Exception
    {
        MacroContentParser contentParser =
            this.oldcore.getMocker().registerMockComponent(MacroContentParser.class);
        MacroBlock faqImageBlock = new MacroBlock(IMAGE, Map.of(REFERENCE, "Other.Page@faq-image.png"), false);
        when(contentParser.parse(any(), any(), anyBoolean(), anyBoolean()))
            .thenReturn(new XDOM(List.of(faqImageBlock)));

        XWikiDocument document = createDocument(new XDOM(Collections.emptyList()));
        BaseObject faqObj = new BaseObject();
        faqObj.setXClassReference(new DocumentReference("wiki", Arrays.asList("DocApp", "Code"),
            "DocumentationClass"));
        faqObj.setLargeStringValue("faq", "{{image reference='Other.Page@faq-image.png'/}}");
        document.addXObject(faqObj);

        List<DocumentationViolation> violations = getChecker().check(document);
        assertEquals(1, violations.size());
        assertViolation(violations.get(0), "Image reference : Other.Page@faq-image.png, Page : Other.Page");
    }
}
