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

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
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
import org.xwiki.rendering.macro.MacroLookupException;
import org.xwiki.rendering.macro.MacroManager;
import org.xwiki.rendering.macro.descriptor.ContentDescriptor;
import org.xwiki.rendering.macro.descriptor.MacroDescriptor;
import org.xwiki.rendering.macro.script.ScriptMacro;
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
import static org.mockito.Mockito.withSettings;

/**
 * Unit tests for {@link ScriptMacroCheck}.
 *
 * @version $Id$
 * @since 1.15
 */
@AllComponents
@OldcoreTest
class ScriptMacroCheckTest
{
    private static final String MESSAGE = "Avoid script macros in documentation: show an example result with a "
        + "screenshot instead of generating it, since a generated example could break over time.";

    @InjectMockitoOldcore
    private MockitoOldcore oldcore;

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    private MacroManager macroManager;

    @BeforeEach
    void setUp() throws Exception
    {
        this.macroManager = this.oldcore.getMocker().registerMockComponent(MacroManager.class);
        registerMacro("velocity", true, String.class);
        registerMacro("groovy", true, String.class);
        registerMacro("python", true, String.class);
        registerMacro("script", true, String.class);
        registerMacro("code", false, String.class);
        registerMacro("info", false, Block.LIST_BLOCK_TYPE);
    }

    private void registerMacro(String id, boolean isScriptMacro, Type contentType) throws Exception
    {
        Macro<?> macro = isScriptMacro ? mock(Macro.class, withSettings().extraInterfaces(ScriptMacro.class))
            : mock(Macro.class);
        MacroDescriptor descriptor = mock(MacroDescriptor.class);
        ContentDescriptor contentDescriptor = mock(ContentDescriptor.class);
        when(contentDescriptor.getType()).thenReturn(contentType);
        when(descriptor.getContentDescriptor()).thenReturn(contentDescriptor);
        when(macro.getDescriptor()).thenReturn(descriptor);
        doReturn(macro).when(this.macroManager).getMacro(new MacroId(id));
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

    private DocumentationCheck getChecker() throws Exception
    {
        return this.oldcore.getMocker().getInstance(DocumentationCheck.class, "scriptMacro");
    }

    private void assertViolation(DocumentationViolation violation, String expectedContext)
    {
        assertEquals(MESSAGE, violation.getViolationMessage());
        assertEquals(expectedContext, violation.getViolationContext());
        assertEquals(DocumentationViolationSeverity.WARNING, violation.getViolationSeverity());
    }

    @Test
    void checkWhenVelocityMacroIsUsed() throws Exception
    {
        MacroBlock velocityBlock = new MacroBlock("velocity", Map.of(), "$services.query.xwql('...')", false);
        XWikiDocument document = createDocument(new XDOM(List.of(velocityBlock)));

        List<DocumentationViolation> violations = getChecker().check(document);

        assertEquals(1, violations.size());
        assertViolation(violations.get(0), "Macro : velocity, Content : $services.query.xwql('...')");
    }

    @Test
    void checkWhenSeveralScriptMacrosAreUsed() throws Exception
    {
        MacroBlock groovyBlock = new MacroBlock("groovy", Map.of(), "println 'hello'", false);
        MacroBlock pythonBlock = new MacroBlock("python", Map.of(), "print('hello')", false);
        MacroBlock scriptBlock = new MacroBlock("script", Map.of("language", "groovy"), "1 + 1", false);
        XWikiDocument document = createDocument(new XDOM(List.of(groovyBlock, pythonBlock, scriptBlock)));

        List<DocumentationViolation> violations = getChecker().check(document);

        assertEquals(3, violations.size());
        assertViolation(violations.get(0), "Macro : groovy, Content : println 'hello'");
        assertViolation(violations.get(1), "Macro : python, Content : print('hello')");
        assertViolation(violations.get(2), "Macro : script, Content : 1 + 1");
    }

    @Test
    void checkWhenScriptMacroHasLongMultilineContent() throws Exception
    {
        MacroBlock velocityBlock = new MacroBlock("velocity", Map.of(),
            "#set ($results = $services.query.xwql('from doc.object(XWiki.XWikiUsers) as user').execute())\n"
                + "#foreach ($result in $results)\n  * $result\n#end", false);
        XWikiDocument document = createDocument(new XDOM(List.of(velocityBlock)));

        List<DocumentationViolation> violations = getChecker().check(document);

        assertEquals(1, violations.size());
        assertViolation(violations.get(0),
            "Macro : velocity, Content : #set ($results = $services.query.xwql('from doc...");
    }

    @Test
    void checkWhenScriptMacroHasNoContent() throws Exception
    {
        MacroBlock velocityBlock = new MacroBlock("velocity", Map.of(), false);
        XWikiDocument document = createDocument(new XDOM(List.of(velocityBlock)));

        List<DocumentationViolation> violations = getChecker().check(document);

        assertEquals(1, violations.size());
        assertViolation(violations.get(0), "Macro : velocity, Content : ");
    }

    @Test
    void checkWhenScriptIsShownInsideCodeMacro() throws Exception
    {
        MacroBlock codeBlock = new MacroBlock("code", Map.of("language", "none"),
            "{{velocity}}$xcontext.user{{/velocity}}", false);
        XWikiDocument document = createDocument(new XDOM(List.of(codeBlock)));

        assertEquals(0, getChecker().check(document).size());
    }

    @Test
    void checkWhenMacroDoesNotExist() throws Exception
    {
        when(this.macroManager.getMacro(new MacroId("unknown"))).thenThrow(new MacroLookupException("not found"));
        MacroBlock unknownBlock = new MacroBlock("unknown", Map.of(), "content", false);
        XWikiDocument document = createDocument(new XDOM(List.of(unknownBlock)));

        assertEquals(0, getChecker().check(document).size());
        assertEquals("Failed to look up macro [unknown]. Ignoring Script Macro check inside it. "
            + "Root error cause: [MacroLookupException: not found]", this.logCapture.getMessage(0));
    }

    @Test
    void checkWhenScriptMacroIsInsideWikiContentMacro() throws Exception
    {
        MacroContentParser contentParser =
            this.oldcore.getMocker().registerMockComponent(MacroContentParser.class);
        MacroBlock velocityBlock = new MacroBlock("velocity", Map.of(), "$xcontext.user", false);
        when(contentParser.parse(any(), any(), anyBoolean(), anyBoolean()))
            .thenReturn(new XDOM(List.of(velocityBlock)));

        MacroBlock macroBlock = new MacroBlock("info", Map.of(), "{{velocity}}$xcontext.user{{/velocity}}", false);
        XWikiDocument document = createDocument(new XDOM(List.of(macroBlock)));

        List<DocumentationViolation> violations = getChecker().check(document);

        assertEquals(1, violations.size());
        assertViolation(violations.get(0), "Macro : velocity, Content : $xcontext.user");
    }

    @Test
    void checkWhenScriptMacroIsUsedInFaqProperty() throws Exception
    {
        MacroContentParser contentParser =
            this.oldcore.getMocker().registerMockComponent(MacroContentParser.class);
        MacroBlock faqGroovyBlock = new MacroBlock("groovy", Map.of(), "println xcontext.user", false);
        when(contentParser.parse(any(), any(), anyBoolean(), anyBoolean()))
            .thenReturn(new XDOM(List.of(faqGroovyBlock)));

        XWikiDocument document = createDocument(new XDOM(Collections.emptyList()));
        BaseObject faqObj = new BaseObject();
        faqObj.setXClassReference(new DocumentReference("wiki", Arrays.asList("DocApp", "Code"),
            "DocumentationClass"));
        faqObj.setLargeStringValue("faq", "{{groovy}}println xcontext.user{{/groovy}}");
        document.addXObject(faqObj);

        List<DocumentationViolation> violations = getChecker().check(document);

        assertEquals(1, violations.size());
        assertViolation(violations.get(0), "Macro : groovy, Content : println xcontext.user");
    }
}
