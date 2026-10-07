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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xwiki.contrib.documentation.DocumentationCheck;
import org.xwiki.contrib.documentation.DocumentationViolation;
import org.xwiki.contrib.documentation.DocumentationViolationSeverity;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.macro.Macro;
import org.xwiki.rendering.macro.MacroContentParser;
import org.xwiki.rendering.macro.MacroId;
import org.xwiki.rendering.macro.MacroManager;
import org.xwiki.rendering.macro.descriptor.ContentDescriptor;
import org.xwiki.rendering.macro.descriptor.MacroDescriptor;
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
 * Unit tests for {@link ImageMacroCaptionVersionCheck}.
 *
 * @version $Id$
 */
@AllComponents
@OldcoreTest
class ImageMacroCaptionVersionCheckTest
{
    private static final String MESSAGE =
        "The caption of the Image macro should not indicate the XWiki version in which the screenshot was taken.";

    private static final String IMAGE = "image";

    private static final String REFERENCE = "reference";

    private static final String CAPTION = "caption";

    @InjectMockitoOldcore
    private MockitoOldcore oldcore;

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
        return this.oldcore.getMocker().getInstance(DocumentationCheck.class, "imageMacroCaptionVersion");
    }

    private void assertViolation(DocumentationViolation violation, String expectedContext)
    {
        assertEquals(MESSAGE, violation.getViolationMessage());
        assertEquals(expectedContext, violation.getViolationContext());
        assertEquals(DocumentationViolationSeverity.WARNING, violation.getViolationSeverity());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Screenshot taken in 17.10",
        "Rights UI (16.10.5)",
        "Rights UI in 18.0.0RC1",
        "Rights UI in 18.0.0-rc-1",
        "Rights UI in 17.0M2",
        "Rights UI in 17.10-SNAPSHOT",
        "Rights UI, v17.10.",
        "Rights UI in XWiki 17",
        "Rights UI in xwiki17"
    })
    void checkWhenCaptionContainsVersion(String caption) throws Exception
    {
        MacroBlock imageBlock = new MacroBlock(IMAGE, Map.of(REFERENCE, "rights.png", CAPTION, caption), false);
        XWikiDocument document = createDocument(new XDOM(List.of(imageBlock)));

        List<DocumentationViolation> violations = getChecker().check(document);
        assertEquals(1, violations.size());
        assertViolation(violations.get(0), String.format("Image reference : rights.png, Caption : %s", caption));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "The Rights UI",
        "Step 1 of the wizard",
        "Figure 2: the XWiki administration",
        "Comparison of rights-v2.png and rights-v3.png",
        "Version 1.2a of the file"
    })
    void checkWhenCaptionDoesNotContainVersion(String caption) throws Exception
    {
        MacroBlock imageBlock = new MacroBlock(IMAGE, Map.of(REFERENCE, "rights.png", CAPTION, caption), false);
        XWikiDocument document = createDocument(new XDOM(List.of(imageBlock)));

        assertEquals(0, getChecker().check(document).size());
    }

    @Test
    void checkWhenImageMacroHasNoCaption() throws Exception
    {
        MacroBlock imageBlock = new MacroBlock(IMAGE, Map.of(REFERENCE, "rights.png"), false);
        XWikiDocument document = createDocument(new XDOM(List.of(imageBlock)));

        assertEquals(0, getChecker().check(document).size());
    }

    @Test
    void checkWhenImageMacroHasNoReference() throws Exception
    {
        MacroBlock imageBlock = new MacroBlock(IMAGE, Map.of(CAPTION, "XWiki 17.10"), false);
        XWikiDocument document = createDocument(new XDOM(List.of(imageBlock)));

        List<DocumentationViolation> violations = getChecker().check(document);
        assertEquals(1, violations.size());
        assertViolation(violations.get(0), "Image reference : , Caption : XWiki 17.10");
    }

    @Test
    void checkWhenImageMacroIsInsideWikiContentMacro() throws Exception
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
        MacroBlock imageBlock = new MacroBlock(IMAGE, Map.of(REFERENCE, "foo.png", CAPTION, "In 17.10"), false);
        when(contentParser.parse(any(), any(), anyBoolean(), anyBoolean()))
            .thenReturn(new XDOM(List.of(imageBlock)));

        MacroBlock macroBlock = new MacroBlock("note", Map.of(), "{{image reference='foo.png'/}}", false);
        XWikiDocument document = createDocument(new XDOM(List.of(macroBlock)));

        List<DocumentationViolation> violations = getChecker().check(document);
        assertEquals(1, violations.size());
        assertViolation(violations.get(0), "Image reference : foo.png, Caption : In 17.10");
    }

    @Test
    void checkWhenCaptionContainsVersionInFaqProperty() throws Exception
    {
        MacroContentParser contentParser =
            this.oldcore.getMocker().registerMockComponent(MacroContentParser.class);
        MacroBlock faqImageBlock =
            new MacroBlock(IMAGE, Map.of(REFERENCE, "faq-image.png", CAPTION, "XWiki 16.10"), false);
        when(contentParser.parse(any(), any(), anyBoolean(), anyBoolean()))
            .thenReturn(new XDOM(List.of(faqImageBlock)));

        XWikiDocument document = createDocument(new XDOM(Collections.emptyList()));
        BaseObject faqObj = new BaseObject();
        faqObj.setXClassReference(new DocumentReference("wiki", Arrays.asList("DocApp", "Code"),
            "DocumentationClass"));
        faqObj.setLargeStringValue("faq", "{{image reference='faq-image.png' caption='XWiki 16.10'/}}");
        document.addXObject(faqObj);

        List<DocumentationViolation> violations = getChecker().check(document);
        assertEquals(1, violations.size());
        assertViolation(violations.get(0), "Image reference : faq-image.png, Caption : XWiki 16.10");
    }
}
