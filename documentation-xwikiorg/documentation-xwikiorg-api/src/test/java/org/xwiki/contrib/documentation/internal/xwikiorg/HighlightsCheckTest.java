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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.xwiki.contrib.documentation.DocumentationCheck;
import org.xwiki.contrib.documentation.DocumentationViolation;
import org.xwiki.contrib.documentation.DocumentationViolationSeverity;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryManager;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.macro.MacroContentParser;
import org.xwiki.rendering.macro.MacroExecutionException;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link HighlightsCheck}.
 *
 * @version $Id$
 */
@AllComponents
@OldcoreTest
class HighlightsCheckTest
{
    private static final String LINK_MESSAGE = "A highlight must hold a link to the highlighted page.";

    private static final String DESCRIPTION_MESSAGE = "A highlight must have exactly one nested list item, "
        + "holding a one-line description of the highlighted page.";

    private static final String THRESHOLD_MESSAGE = "Highlights are recommended for pages with more than 15 child "
        + "pages, to guide readers to the most important ones.";

    private static final DocumentReference NESTED_REFERENCE =
        new DocumentReference("wiki", List.of("documentation", "xs", "dev", "front-end"), "WebHome");

    @InjectMockitoOldcore
    private MockitoOldcore oldcore;

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    private QueryManager queryManager;

    @BeforeEach
    void setUp() throws Exception
    {
        this.queryManager = this.oldcore.getMocker().registerMockComponent(QueryManager.class);
    }

    private XWikiDocument createDocument(String highlightsContent)
    {
        return createDocument(new DocumentReference("wiki", "space", "page"), highlightsContent);
    }

    private XWikiDocument createNestedDocument(String highlightsContent)
    {
        return createDocument(NESTED_REFERENCE, highlightsContent);
    }

    private XWikiDocument createDocument(DocumentReference reference, String highlightsContent)
    {
        XWikiDocument document = new XWikiDocument(reference)
        {
            @Override
            public XDOM getXDOM()
            {
                return new XDOM(Collections.emptyList());
            }

            @Override
            public Syntax getSyntax()
            {
                return Syntax.XWIKI_2_1;
            }
        };
        if (highlightsContent != null) {
            BaseObject docObj = new BaseObject();
            docObj.setXClassReference(
                new DocumentReference("wiki", Arrays.asList("DocApp", "Code"), "DocumentationClass"));
            docObj.setLargeStringValue("highlights", highlightsContent);
            document.addXObject(docObj);
        }
        return document;
    }

    private DocumentationCheck getChecker() throws Exception
    {
        return this.oldcore.getMocker().getInstance(DocumentationCheck.class, "highlights");
    }

    @SuppressWarnings("unchecked")
    private Query mockChildPageCount(long count) throws Exception
    {
        Query query = mock(Query.class);
        when(this.queryManager.createQuery(any(String.class), eq(Query.HQL))).thenReturn(query);
        when(query.bindValue(any(String.class), any())).thenReturn(query);
        when(query.setWiki(any())).thenReturn(query);
        when(query.execute()).thenReturn(List.of(count));
        return query;
    }

    private void assertThresholdWarning(DocumentationViolation violation, long childPageCount)
    {
        assertEquals(THRESHOLD_MESSAGE, violation.getViolationMessage());
        assertEquals("Child pages: [" + childPageCount + "]", violation.getViolationContext());
        assertEquals(DocumentationViolationSeverity.WARNING, violation.getViolationSeverity());
    }

    private static String highlights(int count)
    {
        return IntStream.rangeClosed(1, count)
            .mapToObj(i -> "* [[Page " + i + ">>doc:Space.Page" + i + "]]\n** Description " + i)
            .collect(Collectors.joining("\n"));
    }

    private void assertWarning(DocumentationViolation violation, String message, String highlight)
    {
        assertEquals(message, violation.getViolationMessage());
        assertEquals("Highlight : " + highlight, violation.getViolationContext());
        assertEquals(DocumentationViolationSeverity.WARNING, violation.getViolationSeverity());
    }

    @Test
    void checkWhenNoDocumentationObject() throws Exception
    {
        assertEquals(0, getChecker().check(createDocument(null)).size());
    }

    @Test
    void checkWhenHighlightsIsEmpty() throws Exception
    {
        assertEquals(0, getChecker().check(createDocument("")).size());
    }

    @Test
    void checkWhenSixValidHighlights() throws Exception
    {
        assertEquals(0, getChecker().check(createDocument(highlights(6))).size());
    }

    @Test
    void checkWhenMoreThanSixHighlights() throws Exception
    {
        List<DocumentationViolation> violations = getChecker().check(createDocument(highlights(7)));

        assertEquals(1, violations.size());
        assertEquals("There are 7 highlights in this page, while a maximum of 6 is allowed.",
            violations.get(0).getViolationMessage());
        assertEquals("", violations.get(0).getViolationContext());
        assertEquals(DocumentationViolationSeverity.ERROR, violations.get(0).getViolationSeverity());
    }

    @Test
    void checkWhenHighlightHasNoLink() throws Exception
    {
        List<DocumentationViolation> violations =
            getChecker().check(createDocument("* Just **some** text\n** Description"));

        assertEquals(1, violations.size());
        assertWarning(violations.get(0), LINK_MESSAGE, "Just some text");
    }

    @Test
    void checkWhenHighlightHasNoDescription() throws Exception
    {
        List<DocumentationViolation> violations =
            getChecker().check(createDocument("* [[Install>>doc:Space.Install]]"));

        assertEquals(1, violations.size());
        assertWarning(violations.get(0), DESCRIPTION_MESSAGE, "Install");
    }

    @Test
    void checkWhenHighlightHasSeveralDescriptions() throws Exception
    {
        List<DocumentationViolation> violations = getChecker().check(
            createDocument("* [[Install>>doc:Space.Install]]\n** First line\n** Second line"));

        assertEquals(1, violations.size());
        assertWarning(violations.get(0), DESCRIPTION_MESSAGE, "Install");
    }

    @Test
    void checkWhenHighlightHasNeitherLinkNorDescription() throws Exception
    {
        List<DocumentationViolation> violations = getChecker().check(createDocument(
            "* [[Install>>doc:Space.Install]]\n** Description\n* Configure"));

        assertEquals(2, violations.size());
        assertWarning(violations.get(0), LINK_MESSAGE, "Configure");
        assertWarning(violations.get(1), DESCRIPTION_MESSAGE, "Configure");
    }

    @Test
    void checkWhenDeeperNestedItems() throws Exception
    {
        // Third-level items neither count as highlights nor as additional descriptions.
        List<DocumentationViolation> violations = getChecker().check(createDocument(
            "* [[Install>>doc:Space.Install]]\n** Description\n*** Detail 1\n*** Detail 2"));

        assertEquals(0, violations.size());
    }

    @Test
    void checkWhenHighlightsParsingFails() throws Exception
    {
        MacroContentParser contentParser =
            this.oldcore.getMocker().registerMockComponent(MacroContentParser.class);
        when(contentParser.parse(any(), any(), anyBoolean(), anyBoolean()))
            .thenThrow(new MacroExecutionException("error"));

        assertEquals(0, getChecker().check(createDocument(highlights(7))).size());
        assertEquals("Failed to parse the Highlights content. Ignoring Highlights check inside it. "
            + "Root error cause: [MacroExecutionException: error]", this.logCapture.getMessage(0));
    }

    @Test
    void checkWhenManyChildPagesAndNoHighlights() throws Exception
    {
        Query query = mockChildPageCount(16);

        List<DocumentationViolation> violations = getChecker().check(createNestedDocument(""));

        assertEquals(1, violations.size());
        assertThresholdWarning(violations.get(0), 16);
        verify(query).bindValue("space", "documentation.xs.dev.front-end");
        verify(query).setWiki("wiki");
    }

    @Test
    void checkWhenManyChildPagesAndBlankHighlights() throws Exception
    {
        mockChildPageCount(24);

        List<DocumentationViolation> violations = getChecker().check(createNestedDocument(" \n "));

        assertEquals(1, violations.size());
        assertThresholdWarning(violations.get(0), 24);
    }

    @Test
    void checkWhenChildPagesAtThreshold() throws Exception
    {
        mockChildPageCount(15);

        assertEquals(0, getChecker().check(createNestedDocument("")).size());
    }

    @Test
    void checkWhenManyChildPagesAndHighlights() throws Exception
    {
        mockChildPageCount(16);

        assertEquals(0, getChecker().check(createNestedDocument(highlights(6))).size());
        verify(this.queryManager, never()).createQuery(any(String.class), any(String.class));
    }

    @Test
    void checkChildPagesWhenNoDocumentationObjectOrTerminalPage() throws Exception
    {
        mockChildPageCount(16);

        assertEquals(0, getChecker().check(createNestedDocument(null)).size());
        assertEquals(0, getChecker().check(createDocument("")).size());
        verify(this.queryManager, never()).createQuery(any(String.class), any(String.class));
    }

    @Test
    void checkWhenChildPageCountFails() throws Exception
    {
        when(this.queryManager.createQuery(any(String.class), eq(Query.HQL)))
            .thenThrow(new QueryException("query failed", null, null));

        assertEquals(0, getChecker().check(createNestedDocument("")).size());
        assertEquals("Failed to count the child pages of [wiki:documentation.xs.dev.front-end.WebHome]. Ignoring "
            + "the Highlights recommendation. Root error cause: [QueryException: query failed]",
            this.logCapture.getMessage(0));
    }
}
