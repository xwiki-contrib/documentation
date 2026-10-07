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

import java.io.StringReader;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.xwiki.contrib.documentation.DocumentationCheck;
import org.xwiki.contrib.documentation.DocumentationViolation;
import org.xwiki.contrib.documentation.DocumentationViolationSeverity;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.macro.Macro;
import org.xwiki.rendering.macro.MacroContentParser;
import org.xwiki.rendering.macro.MacroId;
import org.xwiki.rendering.macro.MacroManager;
import org.xwiki.rendering.macro.descriptor.ContentDescriptor;
import org.xwiki.rendering.macro.descriptor.MacroDescriptor;
import org.xwiki.rendering.parser.Parser;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.test.annotation.AllComponents;

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
 * Unit tests for {@link ImageGalleryCheck}.
 *
 * @version $Id$
 * @since 1.0
 */
@AllComponents
@OldcoreTest
class ImageGalleryCheckTest
{
    private static final String MESSAGE =
        "Use the Gallery macro when several images are displayed next to each other.";

    @InjectMockitoOldcore
    private MockitoOldcore oldcore;

    private MacroManager macroManager;

    @BeforeEach
    void setUp() throws Exception
    {
        // By default, consider that all macros have a content that is not wiki content (e.g. the Code macro).
        this.macroManager = this.oldcore.getMocker().registerMockComponent(MacroManager.class);
        doReturn(mockMacro(String.class)).when(this.macroManager).getMacro(any());
    }

    private Macro<?> mockMacro(Type contentType)
    {
        Macro<?> macro = mock(Macro.class);
        MacroDescriptor descriptor = mock(MacroDescriptor.class);
        ContentDescriptor contentDescriptor = mock(ContentDescriptor.class);
        when(contentDescriptor.getType()).thenReturn(contentType);
        when(descriptor.getContentDescriptor()).thenReturn(contentDescriptor);
        when(macro.getDescriptor()).thenReturn(descriptor);
        return macro;
    }

    private XDOM parse(String content) throws Exception
    {
        return this.oldcore.getMocker().<Parser>getInstance(Parser.class, Syntax.XWIKI_2_1.toIdString())
            .parse(new StringReader(content));
    }

    private XWikiDocument createDocument(String content) throws Exception
    {
        XDOM xdom = parse(content);
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

    private List<DocumentationViolation> check(String content) throws Exception
    {
        DocumentationCheck checker = this.oldcore.getMocker().getInstance(DocumentationCheck.class, "imageGallery");
        return checker.check(createDocument(content));
    }

    private void assertViolation(String expectedContext, DocumentationViolation violation)
    {
        assertEquals(MESSAGE, violation.getViolationMessage());
        assertEquals(expectedContext, violation.getViolationContext());
        assertEquals(DocumentationViolationSeverity.ERROR, violation.getViolationSeverity());
    }

    static Stream<Arguments> checkWhenImagesNextToEachOtherSource()
    {
        return Stream.of(
            // Image macros.
            Arguments.of("Hello\n\n{{image reference='test1.png'/}}\n {{image reference='test2.png'/}}\n\nworld",
                "test1.png, test2.png"),
            // Standalone image macros, one of them without a reference.
            Arguments.of("{{image reference='test1.png'/}}\n\n{{image/}}\n\n{{image reference='test3.png'/}}",
                "test1.png, , test3.png"),
            // Images.
            Arguments.of("Hello [[image:test1.png]] [[image:test2.png]] world", "test1.png, test2.png"));
    }

    @ParameterizedTest
    @MethodSource("checkWhenImagesNextToEachOtherSource")
    void checkWhenImagesNextToEachOther(String content, String references) throws Exception
    {
        List<DocumentationViolation> violations = check(content);

        assertEquals(1, violations.size());
        assertViolation("Image references : " + references, violations.get(0));
    }

    @Test
    void checkWhenImageMacrosAreNotNextToEachOther() throws Exception
    {
        assertEquals(0, check("{{image reference='test1.png'/}} a {{image reference='test2.png'/}}").size());
    }

    @Test
    void checkWhenImageMacrosAreSeparatedByParagraphEndingWithMacro() throws Exception
    {
        doReturn(mockMacro(Block.LIST_BLOCK_TYPE)).when(this.macroManager).getMacro(new MacroId("info"));
        MacroContentParser contentParser =
            this.oldcore.getMocker().registerMockComponent(MacroContentParser.class);
        XDOM infoXDOM = parse("note");
        when(contentParser.parse(any(), any(), anyBoolean(), anyBoolean())).thenReturn(infoXDOM);

        List<DocumentationViolation> violations = check("""
            {{image reference="a.png" alt="A"/}}

            Some text with a {{info}}note{{/info}}

            {{image reference="b.png" alt="B"/}}""");

        assertEquals(0, violations.size());
    }

    @Test
    void checkWhenImageMacrosAreInsideCodeMacro() throws Exception
    {
        List<DocumentationViolation> violations = check("""
            {{code language="none"}}
            {{image reference="a.png" alt="A"/}}
            {{image reference="b.png" alt="B"/}}
            {{/code}}""");

        assertEquals(0, violations.size());
    }

    @Test
    void checkWhenImagesAreInsideGalleryMacro() throws Exception
    {
        String galleryContent = "[[image:test1.png]]\n[[image:test2.png]]";
        doReturn(mockMacro(Block.LIST_BLOCK_TYPE)).when(this.macroManager).getMacro(new MacroId("gallery"));
        MacroContentParser contentParser =
            this.oldcore.getMocker().registerMockComponent(MacroContentParser.class);
        XDOM galleryXDOM = parse(galleryContent);
        when(contentParser.parse(any(), any(), anyBoolean(), anyBoolean())).thenReturn(galleryXDOM);

        List<DocumentationViolation> violations = check("{{gallery}}\n" + galleryContent + "\n{{/gallery}}");

        assertEquals(0, violations.size());
    }

    @Test
    void checkWhenImageMacrosNextToEachOtherInsideWikiContentMacro() throws Exception
    {
        doReturn(mockMacro(Block.LIST_BLOCK_TYPE)).when(this.macroManager).getMacro(new MacroId("info"));
        MacroContentParser contentParser =
            this.oldcore.getMocker().registerMockComponent(MacroContentParser.class);
        String infoContent = "[[image:test1.png]]\n[[image:test2.png]]";
        XDOM infoXDOM = parse(infoContent);
        when(contentParser.parse(any(), any(), anyBoolean(), anyBoolean())).thenReturn(infoXDOM);

        List<DocumentationViolation> violations = check("{{info}}\n" + infoContent + "\n{{/info}}");

        assertEquals(1, violations.size());
        assertViolation("Image references : test1.png, test2.png", violations.get(0));
    }

    @Test
    void checkWhenImageMacrosNextToEachOtherInFaqProperty() throws Exception
    {
        String faqContent = "{{image reference='test1.png'/}}\n {{image reference='test2.png'/}}";
        MacroContentParser contentParser =
            this.oldcore.getMocker().registerMockComponent(MacroContentParser.class);
        XDOM faqXDOM = parse(faqContent);
        when(contentParser.parse(any(), any(), anyBoolean(), anyBoolean())).thenReturn(faqXDOM);

        XWikiDocument document = createDocument("");
        BaseObject faqObj = new BaseObject();
        faqObj.setXClassReference(new DocumentReference("wiki", Arrays.asList("DocApp", "Code"),
            "DocumentationClass"));
        faqObj.setLargeStringValue("faq", faqContent);
        document.addXObject(faqObj);

        List<DocumentationViolation> violations =
            this.oldcore.getMocker().<DocumentationCheck>getInstance(DocumentationCheck.class, "imageGallery")
                .check(document);

        assertEquals(1, violations.size());
        assertViolation("Image references : test1.png, test2.png", violations.get(0));
    }
}
