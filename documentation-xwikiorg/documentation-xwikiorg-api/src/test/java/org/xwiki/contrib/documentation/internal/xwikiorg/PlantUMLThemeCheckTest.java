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
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
        "PlantUML diagrams must not set a theme or colors: the wiki's default diagram style applies.";

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

    static Stream<Arguments> checkWhenDiagramSetsThemeOrColorsSource()
    {
        return Stream.of(
            // A theme is set.
            Arguments.of("\n  @startuml\n  !theme bluegray\nAlice -> Bob\n@enduml", "!theme bluegray"),
            Arguments.of("!theme cerulean from https://example.org/themes\r\nAlice -> Bob",
                "!theme cerulean from https://example.org/themes"),
            // A color skinparam is set, whatever its case and stereotype.
            Arguments.of("skinparam nodesep 60\nskinparam ArrowColor #333\nAlice -> Bob", "skinparam ArrowColor #333"),
            Arguments.of("skinparam backgroundcolor transparent", "skinparam backgroundcolor transparent"),
            Arguments.of("skinparam component<<Main>>BackgroundColor #fff",
                "skinparam component<<Main>>BackgroundColor #fff"),
            // A color is set inside a skinparam block.
            Arguments.of("skinparam component {\n  FontSize 13\n  BorderColor #333\n}\n[A]", "BorderColor #333"),
            Arguments.of("skinparam {\n  BackgroundColor #fff\n}", "BackgroundColor #fff"),
            // A style block is used.
            Arguments.of("<style>\nroot {\n  LineColor #333\n}\n</style>\nAlice -> Bob", "<style>"));
    }

    @ParameterizedTest
    @MethodSource("checkWhenDiagramSetsThemeOrColorsSource")
    void checkWhenDiagramSetsThemeOrColors(String content, String offendingLine) throws Exception
    {
        List<DocumentationViolation> violations = checkContent(content);

        assertEquals(1, violations.size());
        assertViolation(violations.get(0), "Diagram line : " + offendingLine);
    }

    @Test
    void checkWhenContentIsEmpty() throws Exception
    {
        MacroBlock plantUMLBlock = new MacroBlock("plantuml", Map.of(), false);
        List<DocumentationViolation> violations =
            getChecker().check(createDocument(new XDOM(List.of(plantUMLBlock))));

        assertEquals(0, violations.size());
    }

    @Test
    void checkWhenDiagramOnlySetsLayout() throws Exception
    {
        assertEquals(0, checkContent("@startuml\nAlice -> Bob\n@enduml").size());
        assertEquals(0, checkContent("' !theme bluegray\n' skinparam ArrowColor #333\nAlice -> Bob").size());
        assertEquals(0, checkContent("""
            skinparam componentStyle rectangle
            skinparam nodesep 60
            left to right direction
            skinparam shadowing false
            [A] -> [B]""").size());
        assertEquals(0, checkContent("skinparam component {\n  FontSize 13\n}\nBorderColor #333").size());
        // A participant or class named like a color parameter isn't a skinparam.
        assertEquals(0, checkContent("class LineColor\nAlice -> Bob : setColor()").size());
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
        MacroBlock plantUMLBlock = new MacroBlock("plantuml", Map.of(), "!theme bluegray\nAlice -> Bob", false);
        when(contentParser.parse(any(), any(), anyBoolean(), anyBoolean()))
            .thenReturn(new XDOM(List.of(plantUMLBlock)));

        MacroBlock macroBlock =
            new MacroBlock("info", Map.of(), "{{plantuml}}!theme bluegray\nAlice -> Bob{{/plantuml}}", false);
        XWikiDocument document = createDocument(new XDOM(List.of(macroBlock)));

        List<DocumentationViolation> violations = getChecker().check(document);

        assertEquals(1, violations.size());
        assertViolation(violations.get(0), "Diagram line : !theme bluegray");
    }

    @Test
    void checkWhenThemeIsSetInFaqProperty() throws Exception
    {
        MacroContentParser contentParser =
            this.oldcore.getMocker().registerMockComponent(MacroContentParser.class);
        MacroBlock faqPlantUMLBlock = new MacroBlock("plantuml", Map.of(), "!theme bluegray\nBob -> Alice", false);
        when(contentParser.parse(any(), any(), anyBoolean(), anyBoolean()))
            .thenReturn(new XDOM(List.of(faqPlantUMLBlock)));

        XWikiDocument document = createDocument(new XDOM(Collections.emptyList()));
        BaseObject faqObj = new BaseObject();
        faqObj.setXClassReference(new DocumentReference("wiki", Arrays.asList("DocApp", "Code"),
            "DocumentationClass"));
        faqObj.setLargeStringValue("faq", "{{plantuml}}!theme bluegray\nBob -> Alice{{/plantuml}}");
        document.addXObject(faqObj);

        List<DocumentationViolation> violations = getChecker().check(document);

        assertEquals(1, violations.size());
        assertViolation(violations.get(0), "Diagram line : !theme bluegray");
    }
}
