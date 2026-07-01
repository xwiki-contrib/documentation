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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.LoggerFactory;
import org.xwiki.contrib.documentation.DocumentationViolation;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.macro.Macro;
import org.xwiki.rendering.macro.MacroContentParser;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.macro.MacroId;
import org.xwiki.rendering.macro.MacroManager;
import org.xwiki.rendering.macro.descriptor.ContentDescriptor;
import org.xwiki.rendering.macro.descriptor.MacroDescriptor;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.test.LogLevel;
import org.xwiki.test.junit5.LogCaptureExtension;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the uncovered branches of {@link AbstractXDOMDocumentationCheck}: the FAQ parse failure, the
 * {@code skipMacroId} skip, and macros whose content descriptor is {@code null} or not
 * {@link Block#LIST_BLOCK_TYPE}.
 * <p>
 * The base class is abstract, so a minimal concrete subclass ({@link TestableXDOMDocumentationCheck}) is
 * instantiated directly (bypassing component injection) to expose {@code parseFAQXDOM} and
 * {@code checkInsideWikiMacros} to the tests. Its {@code @Inject}-annotated fields are wired manually with mocks
 * since the fields are package-protected.
 *
 * @version $Id$
 * @since 1.14
 */
class AbstractXDOMDocumentationCheckTest
{
    private static final LocalDocumentReference DOCUMENTATION_CLASS_REFERENCE =
        new LocalDocumentReference(List.of("DocApp", "Code"), "DocumentationClass");

    /**
     * Minimal concrete subclass exposing the protected methods of {@link AbstractXDOMDocumentationCheck} under
     * test. It is not used to perform an actual check; {@link #check} is never called by the tests.
     */
    private static final class TestableXDOMDocumentationCheck extends AbstractXDOMDocumentationCheck
    {
        @Override
        public List<DocumentationViolation> check(XWikiDocument document)
        {
            return List.of();
        }

        public XDOM callParseFAQXDOM(XWikiDocument document, XDOM xdom, String checkName)
        {
            return parseFAQXDOM(document, xdom, checkName);
        }

        public List<XDOM> callCheckInsideWikiMacros(XDOM xdom, XWikiDocument document, String skipMacroId,
            String checkName)
        {
            List<XDOM> collected = new ArrayList<>();
            checkInsideWikiMacros(xdom, document, skipMacroId, checkName, collected::add);
            return collected;
        }
    }

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    private final MacroManager macroManager = mock(MacroManager.class);

    private final MacroContentParser contentParser = mock(MacroContentParser.class);

    private TestableXDOMDocumentationCheck createChecker()
    {
        TestableXDOMDocumentationCheck checker = new TestableXDOMDocumentationCheck();
        checker.logger = LoggerFactory.getLogger(TestableXDOMDocumentationCheck.class);
        checker.macroManager = this.macroManager;
        checker.contentParser = this.contentParser;
        return checker;
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

    @Test
    void parseFAQReturnsNullAndWarnsOnParseFailure() throws Exception
    {
        when(this.contentParser.parse(any(), any(), anyBoolean(), anyBoolean()))
            .thenThrow(new MacroExecutionException("parse failed"));

        BaseObject faqObj = mock(BaseObject.class);
        when(faqObj.getLargeStringValue("faq")).thenReturn("{{someMacro/}}");

        XWikiDocument document = mock(XWikiDocument.class);
        when(document.getXObject(DOCUMENTATION_CLASS_REFERENCE)).thenReturn(faqObj);
        when(document.getSyntax()).thenReturn(Syntax.XWIKI_2_1);
        XDOM xdom = new XDOM(List.of());

        XDOM result = createChecker().callParseFAQXDOM(document, xdom, "Some Check");

        assertNull(result);
        assertTrue(this.logCapture.getMessage(0).startsWith("Failed to parse the FAQ content."));
    }

    @Test
    void checkInsideWikiMacrosSkipsSkipMacroId()
    {
        MacroBlock macroBlock = new MacroBlock("note", Map.of(), "some content", false);
        XWikiDocument document = createDocument(new XDOM(List.of(macroBlock)));

        List<XDOM> collected =
            createChecker().callCheckInsideWikiMacros(document.getXDOM(), document, "note", "Some Check");

        assertEquals(0, collected.size());
    }

    @Test
    void checkInsideWikiMacrosSkipsNonListContentMacro() throws Exception
    {
        Macro<?> macro = mock(Macro.class);
        MacroDescriptor descriptor = mock(MacroDescriptor.class);
        ContentDescriptor contentDescriptor = mock(ContentDescriptor.class);
        // Content descriptor type is not LIST_BLOCK_TYPE, so the macro must be skipped.
        when(contentDescriptor.getType()).thenReturn(String.class);
        when(descriptor.getContentDescriptor()).thenReturn(contentDescriptor);
        when(macro.getDescriptor()).thenReturn(descriptor);
        doReturn(macro).when(this.macroManager).getMacro(new MacroId("info"));

        MacroBlock macroBlock = new MacroBlock("info", Map.of(), "some content", false);
        XWikiDocument document = createDocument(new XDOM(List.of(macroBlock)));

        List<XDOM> collected =
            createChecker().callCheckInsideWikiMacros(document.getXDOM(), document, null, "Some Check");

        assertEquals(0, collected.size());
    }
}
