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
 * Unit tests for {@link GitHubSCMLinkCheck}.
 *
 * @version $Id$
 */
@AllComponents
@OldcoreTest
class GitHubSCMLinkCheckTest
{
    private static final String MESSAGE = "Link to a file on GitHub using a URL. Use the SCM macro instead.";

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
        return this.oldcore.getMocker().getInstance(DocumentationCheck.class, "gitHubSCMLink");
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
        "https://github.com/xwiki/xwiki-platform/blob/master/pom.xml",
        "https://github.com/xwiki/xwiki-platform/blob/master/xwiki-platform-core/pom.xml#L10-L20",
        "https://github.com/xwiki/xwiki-commons/tree/master/xwiki-commons-core",
        "https://github.com/xwiki/xwiki-rendering/tree/stable-16.10.x",
        "https://github.com/xwiki-contrib/documentation/blob/master/README.md",
        "https://github.com/xwiki-contrib/documentation/tree/master/documentation-api",
        "http://www.github.com/XWiki/xwiki-platform/blob/master/pom.xml",
        "https://GitHub.com/xwiki/xwiki-platform/blob/master/a file with spaces.txt"
    })
    void checkWhenURLPointsToXWikiFileOnGitHub(String url) throws Exception
    {
        XWikiDocument document = createDocument(createLinkXDOM(url, ResourceType.URL));

        List<DocumentationViolation> violations = getChecker().check(document);

        assertEquals(1, violations.size());
        assertViolation(url, violations.get(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://github.com/xwiki/xwiki-platform/commit/0123456789abcdef",
        "https://github.com/xwiki/xwiki-platform/pull/1234",
        "https://github.com/xwiki/xwiki-platform/issues/12",
        "https://github.com/xwiki/xwiki-platform/compare/master...stable-16.10.x",
        "https://github.com/xwiki/xwiki-platform",
        "https://github.com/xwiki",
        "https://github.com/xwiki/xwiki-platform/tree",
        "https://github.com/xwiki//blob/master/pom.xml",
        "https://github.com/someone/some-repo/blob/master/pom.xml",
        "https://github.com/xwikisas/some-repo/blob/master/pom.xml",
        "https://gitlab.com/xwiki/xwiki-platform/blob/master/pom.xml",
        "https://raw.githubusercontent.com/xwiki/xwiki-platform/master/pom.xml",
        "https://www.xwiki.org/xwiki/bin/view/Main/",
        "unknown://github.com/xwiki/xwiki-platform/blob/master/pom.xml"
    })
    void checkWhenURLDoesNotPointToXWikiFileOnGitHub(String url) throws Exception
    {
        XWikiDocument document = createDocument(createLinkXDOM(url, ResourceType.URL));

        assertEquals(0, getChecker().check(document).size());
    }

    @Test
    void checkWhenLinkIsNotAURL() throws Exception
    {
        XWikiDocument document = createDocument(
            createLinkXDOM("https://github.com/xwiki/xwiki-platform/blob/master/pom.xml", ResourceType.DOCUMENT));

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

        String url = "https://github.com/xwiki/xwiki-platform/blob/master/pom.xml";
        MacroContentParser contentParser =
            this.oldcore.getMocker().registerMockComponent(MacroContentParser.class);
        when(contentParser.parse(any(), any(), anyBoolean(), anyBoolean()))
            .thenReturn(createLinkXDOM(url, ResourceType.URL));

        MacroBlock macroBlock = new MacroBlock("info", Map.of(), "[[pom.xml>>" + url + "]]", false);
        XWikiDocument document = createDocument(new XDOM(List.of(macroBlock)));

        List<DocumentationViolation> violations = getChecker().check(document);

        assertEquals(1, violations.size());
        assertViolation(url, violations.get(0));
    }

    @Test
    void checkWhenURLsAreInFAQAndRelatedProperties() throws Exception
    {
        String faqURL = "https://github.com/xwiki/xwiki-platform/blob/master/faq.txt";
        String relatedURL = "https://github.com/xwiki-contrib/documentation/tree/master";
        MacroContentParser contentParser =
            this.oldcore.getMocker().registerMockComponent(MacroContentParser.class);
        when(contentParser.parse(eq("faq content"), any(), anyBoolean(), anyBoolean()))
            .thenReturn(createLinkXDOM(faqURL, ResourceType.URL));
        when(contentParser.parse(eq("related content"), any(), anyBoolean(), anyBoolean()))
            .thenReturn(createLinkXDOM(relatedURL, ResourceType.URL));

        XWikiDocument document = createDocument(new XDOM(Collections.emptyList()));
        BaseObject docObject = new BaseObject();
        docObject.setXClassReference(new DocumentReference("wiki", List.of("DocApp", "Code"), "DocumentationClass"));
        docObject.setLargeStringValue("faq", "faq content");
        docObject.setLargeStringValue("related", "related content");
        document.addXObject(docObject);

        List<DocumentationViolation> violations = getChecker().check(document);

        assertEquals(2, violations.size());
        assertViolation(faqURL, violations.get(0));
        assertViolation(relatedURL, violations.get(1));
    }
}
