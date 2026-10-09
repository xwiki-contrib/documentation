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

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.xwiki.contrib.documentation.DocumentationCheck;
import org.xwiki.contrib.documentation.DocumentationViolation;
import org.xwiki.contrib.documentation.DocumentationViolationSeverity;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.macro.Macro;
import org.xwiki.rendering.macro.MacroContentParser;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.macro.MacroId;
import org.xwiki.rendering.macro.MacroManager;
import org.xwiki.rendering.macro.descriptor.ContentDescriptor;
import org.xwiki.rendering.macro.descriptor.MacroDescriptor;
import org.xwiki.rendering.syntax.Syntax;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RelatedChildPageCheck}.
 *
 * @version $Id$
 * @since 1.15
 */
@AllComponents
@OldcoreTest
class RelatedChildPageCheckTest
{
    private static final List<String> SPACES = List.of("documentation", "xs", "user", "like");

    private static final String VIOLATION_MESSAGE = "The Related field must not link to child pages of the current "
        + "page, since child pages are automatically listed in the More section.";

    @InjectMockitoOldcore
    private MockitoOldcore oldcore;

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    @BeforeEach
    void setUp() throws Exception
    {
        // Wiki references (doc:, page:, etc.) are only parsed as such when a WikiModel is available.
        this.oldcore.getMocker().registerMockComponent(WikiModel.class);
    }

    private DocumentationCheck getChecker() throws Exception
    {
        return this.oldcore.getMocker().getInstance(DocumentationCheck.class, "relatedChildPage");
    }

    private XWikiDocument createDocument(DocumentReference reference, String related)
    {
        XWikiDocument document = new XWikiDocument(reference);
        document.setSyntax(Syntax.XWIKI_2_1);
        if (related != null) {
            BaseObject docObject = new BaseObject();
            docObject.setXClassReference(new DocumentReference("xwiki", List.of("DocApp", "Code"),
                "DocumentationClass"));
            docObject.setLargeStringValue("related", related);
            document.addXObject(docObject);
        }
        return document;
    }

    private XWikiDocument createDocument(String related)
    {
        return createDocument(new DocumentReference("xwiki", SPACES, "WebHome"), related);
    }

    private void assertChildPageViolation(String expectedReference, List<DocumentationViolation> violations)
    {
        assertEquals(1, violations.size());
        assertEquals(VIOLATION_MESSAGE, violations.get(0).getViolationMessage());
        assertEquals(String.format("Link reference: [%s]", expectedReference),
            violations.get(0).getViolationContext());
        assertEquals(DocumentationViolationSeverity.ERROR, violations.get(0).getViolationSeverity());
    }

    @Test
    void checkWhenRelatedLinksToNestedChildPage() throws Exception
    {
        assertChildPageViolation("documentation.xs.user.like.sub.WebHome", getChecker().check(
            createDocument("* [[Sub (for Users)>>doc:documentation.xs.user.like.sub.WebHome]]")));
    }

    @Test
    void checkWhenRelatedLinksToTerminalChildPage() throws Exception
    {
        assertChildPageViolation("documentation.xs.user.like.page", getChecker().check(
            createDocument("* [[Page (for Users)>>doc:documentation.xs.user.like.page]]")));
    }

    @Test
    void checkWhenRelatedLinksToChildPageWithRelativeReference() throws Exception
    {
        // The child page doesn't exist so the relative reference resolves to the nested child page.
        assertChildPageViolation("sub", getChecker().check(createDocument("* [[Sub (for Users)>>sub]]")));
    }

    @Test
    void checkWhenRelatedLinksToChildPageWithPageReference() throws Exception
    {
        assertChildPageViolation("documentation/xs/user/like/sub", getChecker().check(
            createDocument("* [[Sub (for Users)>>page:documentation/xs/user/like/sub]]")));
    }

    @Test
    void checkWhenRelatedLinksToNonChildPages() throws Exception
    {
        XWikiDocument document = createDocument(String.join("\n",
            "* [[Deep (for Users)>>doc:documentation.xs.user.like.sub.deep.WebHome]]",
            "* [[Other (for Users)>>doc:documentation.xs.user.other.WebHome]]",
            "* [[Other page (for Users)>>doc:documentation.xs.user.other-page]]",
            "* [[Like (for Users)>>doc:documentation.xs.user.like.WebHome]]",
            "* [[Root>>doc:Main.WebHome]]",
            "* [[XWiki>>https://www.xwiki.org]]"));

        assertEquals(0, getChecker().check(document).size());
    }

    @Test
    void checkWhenRelatedLinksToChildPageWithSpaceReference() throws Exception
    {
        assertChildPageViolation("documentation.xs.user.like.sub", getChecker().check(
            createDocument("* [[Sub (for Users)>>space:documentation.xs.user.like.sub]]")));
    }

    @Test
    void checkWhenRelatedLinksToNonPageResources() throws Exception
    {
        // An attachment of a child page is not the child page itself.
        XWikiDocument document = createDocument(String.join("\n",
            "* [[Attachment>>attach:documentation.xs.user.like.sub.WebHome@file.png]]",
            "* [[Mail>>mailto:john@example.org]]"));

        assertEquals(0, getChecker().check(document).size());
    }

    @Test
    void checkWhenCurrentPageIsTerminal() throws Exception
    {
        XWikiDocument document = createDocument(new DocumentReference("xwiki", SPACES, "page"),
            "* [[Sub>>doc:documentation.xs.user.like.page.WebHome]]");

        assertEquals(0, getChecker().check(document).size());
    }

    @Test
    void checkWhenNoRelatedContent() throws Exception
    {
        assertEquals(0, getChecker().check(createDocument(null)).size());
        assertEquals(0, getChecker().check(createDocument("")).size());
    }

    @Test
    void checkWhenRelatedLinksToChildPageInsideWikiContentMacro() throws Exception
    {
        MacroManager macroManager = this.oldcore.getMocker().registerMockComponent(MacroManager.class);
        Macro<?> macro = mock(Macro.class);
        MacroDescriptor descriptor = mock(MacroDescriptor.class);
        ContentDescriptor contentDescriptor = mock(ContentDescriptor.class);
        when(contentDescriptor.getType()).thenReturn(Block.LIST_BLOCK_TYPE);
        when(descriptor.getContentDescriptor()).thenReturn(contentDescriptor);
        when(macro.getDescriptor()).thenReturn(descriptor);
        doReturn(macro).when(macroManager).getMacro(new MacroId("info"));

        XWikiDocument document = createDocument(
            "{{info}}\n[[Sub (for Users)>>doc:documentation.xs.user.like.sub.WebHome]]\n{{/info}}");

        assertChildPageViolation("documentation.xs.user.like.sub.WebHome", getChecker().check(document));
    }

    @Test
    void checkWhenRelatedContentFailsToParse() throws Exception
    {
        MacroContentParser contentParser =
            this.oldcore.getMocker().registerMockComponent(MacroContentParser.class);
        when(contentParser.parse(any(), any(), anyBoolean(), anyBoolean()))
            .thenThrow(new MacroExecutionException("parse failed"));

        assertEquals(0, getChecker().check(createDocument("[[sub]]")).size());
        assertEquals("Failed to parse the Related content. Ignoring Related Child Page check inside it. "
            + "Root error cause: [MacroExecutionException: parse failed]", this.logCapture.getMessage(0));
    }
}
