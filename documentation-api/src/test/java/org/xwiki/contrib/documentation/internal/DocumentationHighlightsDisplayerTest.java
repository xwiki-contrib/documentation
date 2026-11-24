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
package org.xwiki.contrib.documentation.internal;

import java.io.StringReader;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.xwiki.component.util.ReflectionUtils;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.parser.ParseException;
import org.xwiki.rendering.parser.Parser;
import org.xwiki.rendering.renderer.BlockRenderer;
import org.xwiki.rendering.renderer.printer.DefaultWikiPrinter;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.rendering.wiki.WikiModel;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DocumentationHighlightsDisplayer}.
 *
 * @version $Id$
 */
@OldcoreTest
@AllComponents
class DocumentationHighlightsDisplayerTest
{
    private static final DocumentReference PARENT =
        new DocumentReference("wiki", List.of("documentation", "formatting"), "WebHome");

    private static final DocumentReference PRISM =
        new DocumentReference("wiki", List.of("documentation", "formatting", "prism"), "WebHome");

    private static final DocumentReference LOG =
        new DocumentReference("wiki", List.of("documentation", "formatting", "log"), "WebHome");

    @InjectMockitoOldcore
    private MockitoOldcore oldcore;

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    private DocumentationSummaries summaries;

    private DocumentationPropertyDisplayer propertyDisplayer;

    private DocumentationHighlightsDisplayer displayer;

    @BeforeEach
    void setUp() throws Exception
    {
        this.summaries = this.oldcore.getMocker().registerMockComponent(DocumentationSummaries.class);
        when(this.summaries.getSummary(any())).thenReturn("");
        this.propertyDisplayer = this.oldcore.getMocker().registerMockComponent(DocumentationPropertyDisplayer.class);
        this.displayer = this.oldcore.getMocker().getInstance(DocumentationHighlightsDisplayer.class);
        // Wiki references (doc:, page:, etc.) are only parsed as such when a WikiModel is available.
        this.oldcore.getMocker().registerMockComponent(WikiModel.class);
    }

    private String describe(String highlights) throws Exception
    {
        Parser parser = this.oldcore.getMocker().getInstance(Parser.class, "xwiki/2.1");
        XDOM xdom = parser.parse(new StringReader(highlights));

        this.displayer.describeHighlights(xdom, PARENT);

        BlockRenderer renderer = this.oldcore.getMocker().getInstance(BlockRenderer.class, "xwiki/2.1");
        DefaultWikiPrinter printer = new DefaultWikiPrinter();
        renderer.render(xdom, printer);
        return printer.toString();
    }

    @Test
    void describeHighlightsWithTheSummariesOfTheHighlightedPages() throws Exception
    {
        when(this.summaries.getSummary(PRISM)).thenReturn("Make every code macro highlight with Prism.js.");
        when(this.summaries.getSummary(LOG)).thenReturn("Highlight logs, **not** bold {{velocity/}}");

        // Relative and absolute references, with and without label; the summaries are displayed as plain text.
        assertEquals("""
            * [[Prism>>doc:.prism.WebHome]]
            ** Make every code macro highlight with Prism.js.
            * [[documentation.formatting.log.WebHome]]
            ** Highlight logs, ~*~*not~*~* bold ~{~{velocity/}}""",
            describe("* [[Prism>>doc:.prism.WebHome]]\n* [[documentation.formatting.log.WebHome]]"));
    }

    @Test
    void describeHighlightsReplacesTheDescriptionsWhenThePageHasASummary() throws Exception
    {
        when(this.summaries.getSummary(PRISM)).thenReturn("Make every code macro highlight with Prism.js.");

        assertEquals("""
            * [[page:documentation/formatting/prism]]
            ** Make every code macro highlight with Prism.js.
            * [[page:documentation/formatting/log]]
            ** Old description of the log page""",
            describe("""
                * [[page:documentation/formatting/prism]]
                ** Old description
                ** Second one
                * [[page:documentation/formatting/log]]
                ** Old description of the log page"""));
    }

    @Test
    void describeHighlightsWithUntypedRelativeLink() throws Exception
    {
        // The syntax shown in the edit help of the Highlights: a child page of the highlighting page, which, like the
        // rendered link, resolves to the nested page when no terminal page of that name exists.
        when(this.summaries.getSummary(
            new DocumentReference("wiki", List.of("documentation", "formatting", "Page1"), "WebHome")))
            .thenReturn("Summary of page 1");

        assertEquals("* [[Page 1>>Page1]]\n** Summary of page 1", describe("* [[Page 1>>Page1]]"));
    }

    @Test
    void describeHighlightsKeepsAnchorLinks() throws Exception
    {
        // A link to an anchor of the current page doesn't highlight a page, so it isn't described by a summary.
        when(this.summaries.getSummary(PARENT)).thenReturn("Summary of the current page");

        assertEquals("* [[Usage>>||anchor=\"HUsage\"]]\n** Description",
            describe("* [[Usage>>||anchor=\"HUsage\"]]\n** Description"));
    }

    @Test
    void describeHighlightsKeepsHighlightsWithoutPageLink() throws Exception
    {
        assertEquals("* Just text\n** Description\n* [[https://www.xwiki.org]]",
            describe("* Just text\n** Description\n* [[https://www.xwiki.org]]"));
    }

    @Test
    void describeHighlightsIgnoresNestedLists() throws Exception
    {
        when(this.summaries.getSummary(any())).thenReturn("Summary");

        // The links of the descriptions aren't highlights.
        assertEquals("Text\n\n* [[page:documentation/formatting/prism]]\n** Summary",
            describe("""
                Text

                * [[page:documentation/formatting/prism]]
                ** See [[page:documentation/formatting/log]]"""));
    }

    @Test
    void describeHighlightsWhenTheSummaryCannotBeParsed() throws Exception
    {
        when(this.summaries.getSummary(PRISM)).thenReturn("Summary");
        Parser plainParser = mock(Parser.class);
        when(plainParser.parse(any())).thenThrow(new ParseException("error"));
        ReflectionUtils.setFieldValue(this.displayer, "plainTextParser", plainParser);

        assertEquals("* [[page:documentation/formatting/prism]]\n** Description",
            describe("* [[page:documentation/formatting/prism]]\n** Description"));
        assertEquals("Failed to parse the summary of the highlighted page [wiki:documentation.formatting.prism"
            + ".WebHome]. Root cause: [ParseException: error]", this.logCapture.getMessage(0));
    }

    @Test
    void displayTheHighlightsOfADocumentationPage() throws Exception
    {
        assertDisplay(DocumentationSummaries.DOCUMENTATION_CLASS);
    }

    @Test
    void displayTheHighlightsOfALandingPage() throws Exception
    {
        assertDisplay(DocumentationHighlightsDisplayer.LANDING_PAGE_CLASS);
    }

    @Test
    void displayNothingWithoutDocumentationOrLandingPageObject() throws Exception
    {
        assertEquals(List.of(), this.displayer.display(new XWikiDocument(PARENT),
            mock(MacroTransformationContext.class)));
    }

    @SuppressWarnings("unchecked")
    private void assertDisplay(LocalDocumentReference classReference) throws Exception
    {
        when(this.summaries.getSummary(PRISM)).thenReturn("Summary");
        XWikiDocument document = new XWikiDocument(PARENT);
        BaseObject object = new BaseObject();
        object.setXClassReference(classReference);
        document.addXObject(object);
        MacroTransformationContext context = mock(MacroTransformationContext.class);
        List<Block> result = List.of(new WordBlock("result"));
        ArgumentCaptor<Consumer<XDOM>> preparer = ArgumentCaptor.forClass(Consumer.class);
        when(this.propertyDisplayer.display(same(document), same(object), eq("highlights"), eq("Highlights"),
            same(context), preparer.capture())).thenReturn(result);

        assertEquals(result, this.displayer.display(document, context));

        // The Highlights are described with the summaries before being executed.
        Parser parser = this.oldcore.getMocker().getInstance(Parser.class, "xwiki/2.1");
        XDOM xdom = parser.parse(new StringReader("* [[page:documentation/formatting/prism]]"));
        preparer.getValue().accept(xdom);
        verify(this.summaries).getSummary(PRISM);
    }
}
