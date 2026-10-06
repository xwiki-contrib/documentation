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
import org.xwiki.rendering.block.LinkBlock;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.ParagraphBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.listener.reference.ResourceType;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link XWikiOrgURLLinkCheck}.
 *
 * @version $Id$
 */
@AllComponents
@OldcoreTest
class XWikiOrgURLLinkCheckTest
{
    private static final String MESSAGE =
        "Link to a wiki page of xwiki.org using a URL. Use a page reference instead.";

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
        return this.oldcore.getMocker().getInstance(DocumentationCheck.class, "xwikiOrgURLLink");
    }

    private static XDOM createLinkXDOM(String reference, ResourceType type)
    {
        LinkBlock linkBlock = new LinkBlock(Collections.emptyList(), new ResourceReference(reference, type), true);
        return new XDOM(List.of(new ParagraphBlock(List.of(linkBlock))));
    }

    private static void assertViolation(String url, DocumentationViolation violation)
    {
        assertEquals(MESSAGE, violation.getViolationMessage());
        assertEquals("URL : " + url, violation.getViolationContext());
        assertEquals(DocumentationViolationSeverity.ERROR, violation.getViolationSeverity());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://www.xwiki.org/xwiki/bin/view/Documentation/",
        "https://xwiki.org/xwiki/bin/view/Main/#HSection",
        "https://extensions.xwiki.org/xwiki/bin/view/Extension/Some%20Extension/",
        "http://dev.xwiki.org/xwiki/bin/view/Community/DocGuide/?param=value",
        "https://www.XWiki.org/xwiki/wiki/subwiki/view/Main/",
        "https://www.xwiki.org/xwiki/bin/view/Main/A Page With Spaces"
    })
    void checkWhenURLPointsToXWikiOrgWikiPage(String url) throws Exception
    {
        XWikiDocument document = createDocument(createLinkXDOM(url, ResourceType.URL));

        List<DocumentationViolation> violations = getChecker().check(document);

        assertEquals(1, violations.size());
        assertViolation(url, violations.get(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://jira.xwiki.org/browse/XWIKI-12345",
        "https://forum.xwiki.org/t/some-topic/123",
        "https://ci.xwiki.org/job/XWiki/",
        "https://nexus.xwiki.org/nexus/content/groups/public/",
        "https://www.xwiki.org/",
        "https://www.xwiki.org/xwiki/resources/icons/xwiki.png",
        "https://notxwiki.org/xwiki/bin/view/Main/",
        "https://www.xwiki.org.example.com/xwiki/bin/view/Main/",
        "https://github.com/xwiki/xwiki-platform",
        "mailto:someone@xwiki.org",
        "unknown://www.xwiki.org/xwiki/bin/view/Main/"
    })
    void checkWhenURLDoesNotPointToXWikiOrgWikiPage(String url) throws Exception
    {
        XWikiDocument document = createDocument(createLinkXDOM(url, ResourceType.URL));

        assertEquals(0, getChecker().check(document).size());
    }

    @Test
    void checkWhenPageReferenceIsUsed() throws Exception
    {
        XWikiDocument document =
            createDocument(createLinkXDOM("xwiki:Documentation.WebHome", ResourceType.DOCUMENT));

        assertEquals(0, getChecker().check(document).size());
    }

    @Test
    void checkWhenURLIsInsideWikiContentMacro() throws Exception
    {
        MacroManager macroManager = this.oldcore.getMocker().registerMockComponent(MacroManager.class);
        Macro<?> macro = mock(Macro.class);
        MacroDescriptor descriptor = mock(MacroDescriptor.class);
        ContentDescriptor contentDescriptor = mock(ContentDescriptor.class);
        when(contentDescriptor.getType()).thenReturn(Block.LIST_BLOCK_TYPE);
        when(descriptor.getContentDescriptor()).thenReturn(contentDescriptor);
        when(macro.getDescriptor()).thenReturn(descriptor);
        doReturn(macro).when(macroManager).getMacro(new MacroId("info"));

        String url = "https://www.xwiki.org/xwiki/bin/view/Main/";
        MacroContentParser contentParser =
            this.oldcore.getMocker().registerMockComponent(MacroContentParser.class);
        when(contentParser.parse(any(), any(), anyBoolean(), anyBoolean()))
            .thenReturn(createLinkXDOM(url, ResourceType.URL));

        MacroBlock macroBlock = new MacroBlock("info", Map.of(), "[[Main>>" + url + "]]", false);
        XWikiDocument document = createDocument(new XDOM(List.of(macroBlock)));

        List<DocumentationViolation> violations = getChecker().check(document);

        assertEquals(1, violations.size());
        assertViolation(url, violations.get(0));
    }

    @Test
    void checkWhenURLsAreInFAQRelatedAndHighlightsProperties() throws Exception
    {
        String faqURL = "https://www.xwiki.org/xwiki/bin/view/FAQ/";
        String relatedURL = "https://extensions.xwiki.org/xwiki/bin/view/Extension/Related/";
        String highlightsURL = "https://www.xwiki.org/xwiki/bin/view/Highlights/";
        MacroContentParser contentParser =
            this.oldcore.getMocker().registerMockComponent(MacroContentParser.class);
        when(contentParser.parse(eq("faq content"), any(), anyBoolean(), anyBoolean()))
            .thenReturn(createLinkXDOM(faqURL, ResourceType.URL));
        when(contentParser.parse(eq("related content"), any(), anyBoolean(), anyBoolean()))
            .thenReturn(createLinkXDOM(relatedURL, ResourceType.URL));
        when(contentParser.parse(eq("highlights content"), any(), anyBoolean(), anyBoolean()))
            .thenReturn(createLinkXDOM(highlightsURL, ResourceType.URL));

        XWikiDocument document = createDocument(new XDOM(Collections.emptyList()));
        BaseObject docObject = new BaseObject();
        docObject.setXClassReference(new DocumentReference("wiki", List.of("DocApp", "Code"), "DocumentationClass"));
        docObject.setLargeStringValue("faq", "faq content");
        docObject.setLargeStringValue("related", "related content");
        docObject.setLargeStringValue("highlights", "highlights content");
        document.addXObject(docObject);

        List<DocumentationViolation> violations = getChecker().check(document);

        assertEquals(3, violations.size());
        assertViolation(faqURL, violations.get(0));
        assertViolation(relatedURL, violations.get(1));
        assertViolation(highlightsURL, violations.get(2));
    }
}
