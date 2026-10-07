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
 * Unit tests for {@link PlantUMLThemeCheck}.
 *
 * @version $Id$
 * @since 1.15
 */
@AllComponents
@OldcoreTest
class PlantUMLThemeCheckTest
{
    private static final String MESSAGE =
        "PlantUML diagrams must use the bluegray theme (add a '!theme bluegray' line).";

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
        return this.oldcore.getMocker().getInstance(DocumentationCheck.class, "plantUMLTheme");
    }

    private List<DocumentationViolation> checkContent(String content) throws Exception
    {
        MacroBlock plantUMLBlock = new MacroBlock("plantuml", Map.of(), content, false);
        return getChecker().check(createDocument(new XDOM(List.of(plantUMLBlock))));
    }

    private void assertViolation(DocumentationViolation violation, String expectedContext)
    {
        assertEquals(MESSAGE, violation.getViolationMessage());
        assertEquals(expectedContext, violation.getViolationContext());
        assertEquals(DocumentationViolationSeverity.ERROR, violation.getViolationSeverity());
    }

    @Test
    void checkWhenThemeIsMissing() throws Exception
    {
        List<DocumentationViolation> violations = checkContent("\n  @startuml\nAlice -> Bob\n@enduml");

        assertEquals(1, violations.size());
        assertViolation(violations.get(0), "Diagram first line : @startuml");
    }

    @Test
    void checkWhenAnotherThemeIsUsed() throws Exception
    {
        List<DocumentationViolation> violations = checkContent("!theme cerulean\nAlice -> Bob");

        assertEquals(1, violations.size());
        assertViolation(violations.get(0), "Diagram first line : !theme cerulean");
    }

    @Test
    void checkWhenThemeNameOnlyStartsWithBluegray() throws Exception
    {
        List<DocumentationViolation> violations = checkContent("!theme bluegrayish\nAlice -> Bob");

        assertEquals(1, violations.size());
        assertViolation(violations.get(0), "Diagram first line : !theme bluegrayish");
    }

    @Test
    void checkWhenThemeIsInsideAComment() throws Exception
    {
        List<DocumentationViolation> violations = checkContent("' !theme bluegray\nAlice -> Bob");

        assertEquals(1, violations.size());
        assertViolation(violations.get(0), "Diagram first line : ' !theme bluegray");
    }

    @Test
    void checkWhenContentIsEmpty() throws Exception
    {
        MacroBlock plantUMLBlock = new MacroBlock("plantuml", Map.of(), false);
        List<DocumentationViolation> violations =
            getChecker().check(createDocument(new XDOM(List.of(plantUMLBlock))));

        assertEquals(1, violations.size());
        assertViolation(violations.get(0), "Diagram first line : ");
    }

    @Test
    void checkWhenBluegrayThemeIsUsed() throws Exception
    {
        assertEquals(0, checkContent("!theme bluegray\nAlice -> Bob").size());
        assertEquals(0, checkContent("@startuml\r\n  !theme   bluegray\r\nAlice -> Bob\r\n@enduml").size());
        assertEquals(0, checkContent("!theme bluegray from https://example.org/themes\nAlice -> Bob").size());
    }

    @Test
    void checkWhenPlantUMLMacroIsInsideWikiContentMacro() throws Exception
    {
        MacroManager macroManager = this.oldcore.getMocker().registerMockComponent(MacroManager.class);
        Macro<?> macro = mock(Macro.class);
        MacroDescriptor descriptor = mock(MacroDescriptor.class);
        ContentDescriptor contentDescriptor = mock(ContentDescriptor.class);
        when(contentDescriptor.getType()).thenReturn(Block.LIST_BLOCK_TYPE);
        when(descriptor.getContentDescriptor()).thenReturn(contentDescriptor);
        when(macro.getDescriptor()).thenReturn(descriptor);
        doReturn(macro).when(macroManager).getMacro(new MacroId("info"));

        MacroContentParser contentParser =
            this.oldcore.getMocker().registerMockComponent(MacroContentParser.class);
        MacroBlock plantUMLBlock = new MacroBlock("plantuml", Map.of(), "Alice -> Bob", false);
        when(contentParser.parse(any(), any(), anyBoolean(), anyBoolean()))
            .thenReturn(new XDOM(List.of(plantUMLBlock)));

        MacroBlock macroBlock = new MacroBlock("info", Map.of(), "{{plantuml}}Alice -> Bob{{/plantuml}}", false);
        XWikiDocument document = createDocument(new XDOM(List.of(macroBlock)));

        List<DocumentationViolation> violations = getChecker().check(document);

        assertEquals(1, violations.size());
        assertViolation(violations.get(0), "Diagram first line : Alice -> Bob");
    }

    @Test
    void checkWhenThemeIsMissingInFaqProperty() throws Exception
    {
        MacroContentParser contentParser =
            this.oldcore.getMocker().registerMockComponent(MacroContentParser.class);
        MacroBlock faqPlantUMLBlock = new MacroBlock("plantuml", Map.of(), "Bob -> Alice", false);
        when(contentParser.parse(any(), any(), anyBoolean(), anyBoolean()))
            .thenReturn(new XDOM(List.of(faqPlantUMLBlock)));

        XWikiDocument document = createDocument(new XDOM(Collections.emptyList()));
        BaseObject faqObj = new BaseObject();
        faqObj.setXClassReference(new DocumentReference("wiki", Arrays.asList("DocApp", "Code"),
            "DocumentationClass"));
        faqObj.setLargeStringValue("faq", "{{plantuml}}Bob -> Alice{{/plantuml}}");
        document.addXObject(faqObj);

        List<DocumentationViolation> violations = getChecker().check(document);

        assertEquals(1, violations.size());
        assertViolation(violations.get(0), "Diagram first line : Bob -> Alice");
    }
}
