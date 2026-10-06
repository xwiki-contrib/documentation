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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.configuration.ConfigurationSource;
import org.xwiki.configuration.internal.MemoryConfigurationSource;
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
 * Unit tests for {@link VersionMacroCheck}.
 *
 * @version $Id$
 */
@AllComponents
@OldcoreTest
class VersionMacroCheckTest
{
    private static final String VIOLATION_MESSAGE =
        "Version macro markers for versions older than the supported LTS cycle must be removed.";

    @InjectMockitoOldcore
    private MockitoOldcore oldcore;

    private MemoryConfigurationSource configuration;

    private ContentDescriptor versionContentDescriptor;

    @BeforeEach
    void setUp() throws Exception
    {
        this.configuration = new MemoryConfigurationSource();
        this.configuration.setProperty(VersionMacroCheck.OLDEST_SUPPORTED_VERSION_PROPERTY, "16.10.0");
        this.oldcore.getMocker().registerComponent(ConfigurationSource.class, DocumentationConfigurationSource.HINT,
            this.configuration);

        // The version macro is a wiki macro that isn't available in the test environment, so mock the macros. By
        // default their content isn't considered as wiki content, to not have to mock the content parser.
        MacroManager macroManager = this.oldcore.getMocker().registerMockComponent(MacroManager.class);
        Macro<?> macro = mock(Macro.class);
        MacroDescriptor descriptor = mock(MacroDescriptor.class);
        this.versionContentDescriptor = mock(ContentDescriptor.class);
        when(descriptor.getContentDescriptor()).thenReturn(this.versionContentDescriptor);
        when(macro.getDescriptor()).thenReturn(descriptor);
        doReturn(macro).when(macroManager).getMacro(any(MacroId.class));
    }

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

    private List<DocumentationViolation> check(Map<String, String> parameters) throws Exception
    {
        MacroBlock versionBlock = new MacroBlock("version", parameters, "content", false);
        return getChecker().check(createDocument(new XDOM(List.of(versionBlock))));
    }

    private DocumentationCheck getChecker() throws Exception
    {
        return this.oldcore.getMocker().getInstance(DocumentationCheck.class, "versionMacro");
    }

    private void assertViolation(String expectedContext, List<DocumentationViolation> violations)
    {
        assertEquals(1, violations.size());
        assertEquals(VIOLATION_MESSAGE, violations.get(0).getViolationMessage());
        assertEquals(expectedContext, violations.get(0).getViolationContext());
        assertEquals(DocumentationViolationSeverity.WARNING, violations.get(0).getViolationSeverity());
    }

    @Test
    void checkWhenAllSinceVersionsAreOlder() throws Exception
    {
        assertViolation("Version macro : since=12.0, 11.5RC1, oldest supported version : 16.10.0",
            check(Map.of("since", "12.0, 11.5RC1")));
    }

    @Test
    void checkWhenAllBeforeVersionsAreOlder() throws Exception
    {
        assertViolation("Version macro : before=11.10.5, 12.5, oldest supported version : 16.10.0",
            check(Map.of("before", "11.10.5, 12.5")));
    }

    @Test
    void checkWhenRCOfOldestSupportedVersion() throws Exception
    {
        assertViolation("Version macro : since=16.10.0RC1, oldest supported version : 16.10.0",
            check(Map.of("since", "16.10.0RC1")));
    }

    @Test
    void checkWhenOneVersionIsSupported() throws Exception
    {
        assertEquals(0, check(Map.of("since", "15.10.2, 16.10.1")).size());
    }

    @Test
    void checkWhenVersionIsTheOldestSupportedOne() throws Exception
    {
        assertEquals(0, check(Map.of("since", "16.10")).size());
    }

    @Test
    void checkWhenProductIsSet() throws Exception
    {
        assertEquals(0, check(Map.of("product", "Latex", "since", "1.0")).size());
    }

    @Test
    void checkWhenProductIsXWiki() throws Exception
    {
        assertViolation("Version macro : since=12.0, oldest supported version : 16.10.0",
            check(Map.of("product", "XWiki", "since", "12.0")));
        assertViolation("Version macro : since=12.0, oldest supported version : 16.10.0",
            check(Map.of("product", " xwiki ", "since", "12.0")));
    }

    @Test
    void checkWhenNoVersion() throws Exception
    {
        assertEquals(0, check(Map.of()).size());
        assertEquals(0, check(Map.of("since", " , ")).size());
    }

    @Test
    void checkWhenOtherMacro() throws Exception
    {
        MacroBlock otherBlock = new MacroBlock("other", Map.of("since", "12.0"), false);

        assertEquals(0, getChecker().check(createDocument(new XDOM(List.of(otherBlock)))).size());
    }

    @Test
    void checkWhenSinceIsEmptyAndBeforeIsSet() throws Exception
    {
        assertViolation("Version macro : before=12.5, oldest supported version : 16.10.0",
            check(Map.of("since", "", "before", "12.5")));
    }

    @Test
    void checkWhenOldestSupportedVersionIsNotSet() throws Exception
    {
        this.configuration.removeProperty(VersionMacroCheck.OLDEST_SUPPORTED_VERSION_PROPERTY);

        assertEquals(0, check(Map.of("since", "12.0")).size());
    }

    @Test
    void checkWhenVersionMacroIsInsideWikiContentMacro() throws Exception
    {
        when(this.versionContentDescriptor.getType()).thenReturn(Block.LIST_BLOCK_TYPE);

        MacroContentParser contentParser =
            this.oldcore.getMocker().registerMockComponent(MacroContentParser.class);
        MacroBlock innerBlock = new MacroBlock("version", Map.of("since", "12.0"), "inner", false);
        when(contentParser.parse(any(), any(), anyBoolean(), anyBoolean()))
            .thenReturn(new XDOM(List.of(innerBlock)));

        // The outer version macro is supported but contains an unsupported one.
        MacroBlock outerBlock = new MacroBlock("version", Map.of("since", "17.0"),
            "{{version since='12.0'}}inner{{/version}}", false);
        XWikiDocument document = createDocument(new XDOM(List.of(outerBlock)));

        assertViolation("Version macro : since=12.0, oldest supported version : 16.10.0",
            getChecker().check(document));
    }

    @Test
    void checkWhenVersionMacroIsInFaqProperty() throws Exception
    {
        when(this.versionContentDescriptor.getType()).thenReturn(Block.LIST_BLOCK_TYPE);

        // The FAQ contains a supported version macro, which itself contains an unsupported one.
        MacroContentParser contentParser =
            this.oldcore.getMocker().registerMockComponent(MacroContentParser.class);
        MacroBlock faqOuterBlock = new MacroBlock("version", Map.of("since", "17.0"), "inner", false);
        MacroBlock faqInnerBlock = new MacroBlock("version", Map.of("since", "13.4"), "faq", false);
        when(contentParser.parse(any(), any(), anyBoolean(), anyBoolean()))
            .thenReturn(new XDOM(List.of(faqOuterBlock)), new XDOM(List.of(faqInnerBlock)));

        XWikiDocument document = createDocument(new XDOM(Collections.emptyList()));
        BaseObject faqObj = new BaseObject();
        faqObj.setXClassReference(new DocumentReference("wiki", Arrays.asList("DocApp", "Code"),
            "DocumentationClass"));
        faqObj.setLargeStringValue("faq", "{{version since='17.0'}}{{version since='13.4'}}faq{{/version}}"
            + "{{/version}}");
        document.addXObject(faqObj);

        assertViolation("Version macro : since=13.4, oldest supported version : 16.10.0",
            getChecker().check(document));
    }
}
