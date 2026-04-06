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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.xwiki.contrib.documentation.DocumentationCheck;
import org.xwiki.contrib.documentation.DocumentationViolation;
import org.xwiki.contrib.documentation.DocumentationViolationSeverity;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.HeaderBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.listener.HeaderLevel;
import org.xwiki.rendering.macro.MacroContentParser;
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
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link FAQEntryCountCheck}.
 *
 * @version $Id$
 */
@AllComponents
@OldcoreTest
class FAQEntryCountCheckTest
{
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
        return this.oldcore.getMocker().getInstance(DocumentationCheck.class, "faqEntryCount");
    }

    private BaseObject createFAQObject(String faqContent)
    {
        BaseObject faqObj = new BaseObject();
        faqObj.setXClassReference(
            new DocumentReference("wiki", Arrays.asList("DocApp", "Code"), "DocumentationClass"));
        faqObj.setLargeStringValue("faq", faqContent);
        return faqObj;
    }

    private XDOM xdomWithHeaders(int count)
    {
        List<Block> headers = IntStream.range(0, count)
            .mapToObj(i -> new HeaderBlock(Collections.emptyList(), HeaderLevel.LEVEL1))
            .collect(Collectors.toList());
        return new XDOM(headers);
    }

    /**
     * Build a string with exactly {@code lineCount} lines by joining "line" repeated {@code lineCount} times with
     * newlines.
     */
    private static String faqWithLines(int lineCount)
    {
        return IntStream.range(0, lineCount).mapToObj(i -> "line").collect(Collectors.joining("\n"));
    }

    @Test
    void checkWhenNoDocumentationObject() throws Exception
    {
        XWikiDocument document = createDocument(new XDOM(Collections.emptyList()));

        assertEquals(0, getChecker().check(document).size());
    }

    @Test
    void checkWhenFAQIsEmpty() throws Exception
    {
        XWikiDocument document = createDocument(new XDOM(Collections.emptyList()));
        document.addXObject(createFAQObject(""));

        assertEquals(0, getChecker().check(document).size());
    }

    @Test
    void checkWhenFAQHasFiveEntries() throws Exception
    {
        MacroContentParser contentParser =
            this.oldcore.getMocker().registerMockComponent(MacroContentParser.class);
        when(contentParser.parse(any(), any(), anyBoolean(), anyBoolean())).thenReturn(xdomWithHeaders(5));

        XWikiDocument document = createDocument(new XDOM(Collections.emptyList()));
        document.addXObject(createFAQObject("= Q1 =\n= Q2 =\n= Q3 =\n= Q4 =\n= Q5 ="));

        assertEquals(0, getChecker().check(document).size());
    }

    @Test
    void checkWhenFAQHasSixEntries() throws Exception
    {
        MacroContentParser contentParser =
            this.oldcore.getMocker().registerMockComponent(MacroContentParser.class);
        when(contentParser.parse(any(), any(), anyBoolean(), anyBoolean())).thenReturn(xdomWithHeaders(6));

        XWikiDocument document = createDocument(new XDOM(Collections.emptyList()));
        // 6 lines, so no line-count violation; only an entry-count violation
        document.addXObject(createFAQObject("= Q1 =\n= Q2 =\n= Q3 =\n= Q4 =\n= Q5 =\n= Q6 ="));

        List<DocumentationViolation> violations = getChecker().check(document);

        assertEquals(1, violations.size());
        assertEquals(
            "There are more than 5 FAQ entries in this page. This probably indicates that some documentation "
                + "pages should be added.",
            violations.get(0).getViolationMessage());
        assertEquals("", violations.get(0).getViolationContext());
        assertEquals(DocumentationViolationSeverity.WARNING, violations.get(0).getViolationSeverity());
    }

    @Test
    void checkWhenFAQHasFifteenLines() throws Exception
    {
        MacroContentParser contentParser =
            this.oldcore.getMocker().registerMockComponent(MacroContentParser.class);
        when(contentParser.parse(any(), any(), anyBoolean(), anyBoolean())).thenReturn(xdomWithHeaders(0));

        XWikiDocument document = createDocument(new XDOM(Collections.emptyList()));
        document.addXObject(createFAQObject(faqWithLines(25)));

        assertEquals(0, getChecker().check(document).size());
    }

    @Test
    void checkWhenFAQHasSixteenLines() throws Exception
    {
        MacroContentParser contentParser =
            this.oldcore.getMocker().registerMockComponent(MacroContentParser.class);
        when(contentParser.parse(any(), any(), anyBoolean(), anyBoolean())).thenReturn(xdomWithHeaders(0));

        XWikiDocument document = createDocument(new XDOM(Collections.emptyList()));
        document.addXObject(createFAQObject(faqWithLines(26)));

        List<DocumentationViolation> violations = getChecker().check(document);

        assertEquals(1, violations.size());
        assertEquals(
            "There are more than 25 lines in the FAQ of this page. This probably indicates that some "
                + "documentation pages should be added.",
            violations.get(0).getViolationMessage());
        assertEquals("", violations.get(0).getViolationContext());
        assertEquals(DocumentationViolationSeverity.WARNING, violations.get(0).getViolationSeverity());
    }

    @Test
    void checkWhenFAQExceedsBothLimits() throws Exception
    {
        MacroContentParser contentParser =
            this.oldcore.getMocker().registerMockComponent(MacroContentParser.class);
        when(contentParser.parse(any(), any(), anyBoolean(), anyBoolean())).thenReturn(xdomWithHeaders(6));

        XWikiDocument document = createDocument(new XDOM(Collections.emptyList()));
        // 26 lines and 6 headings → both violations
        document.addXObject(createFAQObject(faqWithLines(26)));

        List<DocumentationViolation> violations = getChecker().check(document);

        assertEquals(2, violations.size());
        assertEquals(DocumentationViolationSeverity.WARNING, violations.get(0).getViolationSeverity());
        assertEquals(DocumentationViolationSeverity.WARNING, violations.get(1).getViolationSeverity());
    }
}
