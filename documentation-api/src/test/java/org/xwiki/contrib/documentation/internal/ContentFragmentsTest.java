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

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import jakarta.inject.Named;
import jakarta.inject.Provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.xwiki.contrib.documentation.DocumentationException;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.parser.ContentParser;
import org.xwiki.rendering.parser.ParseException;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.security.authorization.ContextualAuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.test.LogLevel;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ContentFragments}.
 *
 * @version $Id$
 */
@ComponentTest
class ContentFragmentsTest
{
    private static final DocumentReference FRAGMENTS = new DocumentReference("wiki", "Space", "Fragments");

    private static final String NAME = "fragment-name";

    private static final String REFERENCE = "Space.Fragments";

    private static final String MACRO = "{{fragment reference=\"Space.Fragments\" name=\"fragment-name\"/}}";

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    @InjectMockComponents
    private ContentFragments contentFragments;

    @MockComponent
    private Provider<XWikiContext> xcontextProvider;

    @MockComponent
    @Named("macro")
    private DocumentReferenceResolver<String> macroDocumentReferenceResolver;

    @MockComponent
    private ContentParser contentParser;

    @MockComponent
    private ContextualAuthorizationManager authorization;

    private XWikiContext xcontext;

    private XWiki xwiki;

    @BeforeEach
    void setUp()
    {
        this.xcontext = mock(XWikiContext.class);
        when(this.xcontextProvider.get()).thenReturn(this.xcontext);
        this.xwiki = mock(XWiki.class);
        when(this.xcontext.getWiki()).thenReturn(this.xwiki);
        when(this.authorization.hasAccess(eq(Right.VIEW), any())).thenReturn(true);
    }

    @Test
    void getUsages() throws Exception
    {
        // The page holding the fragment uses it, without reference.
        MacroBlock ownMacro = fragmentMacro(null);
        XWikiDocument fragmentsDocument = mockDocument(FRAGMENTS, new XDOM(List.of(ownMacro)));
        when(this.macroDocumentReferenceResolver.resolve("", ownMacro, FRAGMENTS)).thenReturn(FRAGMENTS);

        // A page using the fragment inside the content of another macro.
        DocumentReference inMacroContent = new DocumentReference("wiki", "Space", "InMacroContent");
        mockDocument(inMacroContent, new XDOM(List.of(new MacroBlock("info", Map.of(), MACRO, false))));
        mockFragmentMacroParsing(inMacroContent);

        // A page using the fragment in the wiki content of an object, next to properties which aren't wiki content.
        DocumentReference inObject = new DocumentReference("wiki", "Space", "InObject");
        XWikiDocument inObjectDocument = mockDocument(inObject, new XDOM(List.of()));
        BaseObject object = new BaseObject();
        object.setIntValue("number", 1);
        object.setLargeStringValue("description", "No macro");
        object.setLargeStringValue("faq", MACRO);
        when(inObjectDocument.getXObjects()).thenReturn(Map.of(FRAGMENTS, Arrays.asList(null, object)));
        mockFragmentMacroParsing(inObject);

        // A page using another fragment of the same page, and a page whose content isn't parsed.
        DocumentReference otherFragment = new DocumentReference("wiki", "Space", "OtherFragment");
        mockDocument(otherFragment, new XDOM(List.of(new MacroBlock("fragment", Map.of("name", "other"), false))));
        DocumentReference unparsed = new DocumentReference("wiki", "Space", "Unparsed");
        mockDocument(unparsed, null);

        // A page using the fragment that the current user cannot view.
        DocumentReference denied = new DocumentReference("wiki", "Space", "Denied");
        when(this.authorization.hasAccess(Right.VIEW, denied)).thenReturn(false);

        when(fragmentsDocument.getBackLinkedReferences(this.xcontext))
            .thenReturn(List.of(inMacroContent, inObject, otherFragment, unparsed, denied, FRAGMENTS));

        assertEquals(List.of(FRAGMENTS, inMacroContent, inObject), this.contentFragments.getUsages(FRAGMENTS, NAME));
        verify(this.xwiki, never()).getDocument(denied, this.xcontext);
    }

    @Test
    void getUsagesWhenTheContentCannotBeParsed() throws Exception
    {
        XWikiDocument fragmentsDocument = mockDocument(FRAGMENTS, new XDOM(List.of()));
        DocumentReference failing = new DocumentReference("wiki", "Space", "Failing");
        mockDocument(failing, new XDOM(List.of(new MacroBlock("info", Map.of(), MACRO, false))));
        when(this.contentParser.parse(MACRO, Syntax.XWIKI_2_1, failing)).thenThrow(new ParseException("error"));
        when(fragmentsDocument.getBackLinkedReferences(this.xcontext)).thenReturn(List.of(failing));

        assertEquals(List.of(), this.contentFragments.getUsages(FRAGMENTS, NAME));
        assertEquals("Failed to parse content of document [wiki:Space.Failing] while looking for content fragment "
            + "usages: [error]", this.logCapture.getMessage(0));
    }

    @Test
    void getUsagesSkipsThePagesFailingToLoad() throws Exception
    {
        MacroBlock ownMacro = fragmentMacro(null);
        XWikiDocument fragmentsDocument = mockDocument(FRAGMENTS, new XDOM(List.of(ownMacro)));
        when(this.macroDocumentReferenceResolver.resolve("", ownMacro, FRAGMENTS)).thenReturn(FRAGMENTS);
        DocumentReference broken = new DocumentReference("wiki", "Space", "Broken");
        when(this.xwiki.getDocument(broken, this.xcontext)).thenThrow(new XWikiException());
        when(fragmentsDocument.getBackLinkedReferences(this.xcontext)).thenReturn(List.of(broken));

        assertEquals(List.of(FRAGMENTS), this.contentFragments.getUsages(FRAGMENTS, NAME));
        assertTrue(this.logCapture.getMessage(0)
            .startsWith("Failed to load document [wiki:Space.Broken] while looking for content fragment usages: "));
    }

    @Test
    void getUsagesWhenThePageHoldingTheFragmentCannotBeLoaded() throws Exception
    {
        when(this.xwiki.getDocument(FRAGMENTS, this.xcontext)).thenThrow(new XWikiException());

        DocumentationException exception =
            assertThrows(DocumentationException.class, () -> this.contentFragments.getUsages(FRAGMENTS, NAME));

        assertEquals("Failed to find the pages using the content fragment [fragment-name] of page "
            + "[wiki:Space.Fragments]", exception.getMessage());
    }

    private XWikiDocument mockDocument(DocumentReference reference, XDOM xdom) throws Exception
    {
        XWikiDocument document = mock(XWikiDocument.class);
        when(document.getDocumentReference()).thenReturn(reference);
        when(document.getSyntax()).thenReturn(Syntax.XWIKI_2_1);
        when(document.getXDOM()).thenReturn(xdom);
        when(document.getXObjects()).thenReturn(Map.of());
        when(this.xwiki.getDocument(reference, this.xcontext)).thenReturn(document);
        return document;
    }

    private void mockFragmentMacroParsing(DocumentReference reference) throws Exception
    {
        MacroBlock macroBlock = fragmentMacro(REFERENCE);
        when(this.contentParser.parse(MACRO, Syntax.XWIKI_2_1, reference)).thenReturn(new XDOM(List.of(macroBlock)));
        when(this.macroDocumentReferenceResolver.resolve(REFERENCE, macroBlock, reference)).thenReturn(FRAGMENTS);
    }

    private static MacroBlock fragmentMacro(String reference)
    {
        MacroBlock macroBlock = new MacroBlock("fragment", Map.of("name", NAME), false);
        if (reference != null) {
            macroBlock.setParameter("reference", reference);
        }
        return macroBlock;
    }
}
