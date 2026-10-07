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

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TopLevelRelatedLinksCheck}.
 *
 * @version $Id$
 * @since 1.15
 */
@AllComponents
@OldcoreTest
class TopLevelRelatedLinksCheckTest
{
    private static final String WIKI = "xwiki";

    private static final String WEB_HOME = "WebHome";

    private static final String VIOLATION_MESSAGE = "The Related field of a top-level page must link to the "
        + "top-level pages covering the same topic for the other target audiences.";

    private static final DocumentReference USER_LIKE = topLevelReference("xs", "user", "like");

    private static final String ADMIN_LIKE_LINK =
        "* [[Like (for Administrator)>>doc:documentation.xs.admin.like.WebHome]]";

    private static final String DEV_LIKE_LINK = "* [[Like (for Developer)>>doc:documentation.xs.dev.like.WebHome]]";

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

    private static DocumentReference topLevelReference(String section, String audience, String name)
    {
        return new DocumentReference(WIKI, List.of("documentation", section, audience, name), WEB_HOME);
    }

    private void saveDocument(DocumentReference reference) throws XWikiException
    {
        this.oldcore.getSpyXWiki().saveDocument(new XWikiDocument(reference), this.oldcore.getXWikiContext());
    }

    private DocumentationCheck getChecker() throws Exception
    {
        return this.oldcore.getMocker().getInstance(DocumentationCheck.class, "topLevelRelatedLinks");
    }

    private List<DocumentationViolation> check(DocumentReference reference, String... related) throws Exception
    {
        XWikiDocument document = new XWikiDocument(reference);
        document.setSyntax(Syntax.XWIKI_2_1);
        if (related.length > 0) {
            BaseObject docObject = new BaseObject();
            docObject.setXClassReference(
                new DocumentReference(WIKI, List.of("DocApp", "Code"), "DocumentationClass"));
            docObject.setLargeStringValue("related", String.join("\n", related));
            document.addXObject(docObject);
        }
        return getChecker().check(document);
    }

    private void assertMissingLink(String expectedReference, DocumentationViolation violation)
    {
        assertEquals(VIOLATION_MESSAGE, violation.getViolationMessage());
        assertEquals(String.format("Missing link: [%s]", expectedReference), violation.getViolationContext());
        assertEquals(DocumentationViolationSeverity.ERROR, violation.getViolationSeverity());
    }

    @Test
    void checkWhenAllCounterpartsAreLinked() throws Exception
    {
        saveDocument(topLevelReference("xs", "admin", "like"));
        saveDocument(topLevelReference("xs", "dev", "like"));

        assertEquals(0, check(USER_LIKE, ADMIN_LIKE_LINK, DEV_LIKE_LINK).size());
        // A relative page reference resolves to the same page.
        assertEquals(0, check(USER_LIKE, "* [[Like (for Administrator)>>page:../../admin/like]]",
            DEV_LIKE_LINK).size());
    }

    @Test
    void checkWhenCounterpartIsNotLinked() throws Exception
    {
        saveDocument(topLevelReference("xs", "admin", "like"));
        saveDocument(topLevelReference("xs", "dev", "like"));

        List<DocumentationViolation> violations = check(USER_LIKE, ADMIN_LIKE_LINK);

        assertEquals(1, violations.size());
        assertMissingLink("documentation.xs.dev.like.WebHome", violations.get(0));
    }

    @Test
    void checkWhenRelatedIsEmpty() throws Exception
    {
        saveDocument(topLevelReference("xs", "user", "like"));
        saveDocument(topLevelReference("xs", "dev", "like"));

        List<DocumentationViolation> violations = check(topLevelReference("xs", "admin", "like"));

        assertEquals(2, violations.size());
        assertMissingLink("documentation.xs.user.like.WebHome", violations.get(0));
        assertMissingLink("documentation.xs.dev.like.WebHome", violations.get(1));
        assertEquals(2, check(topLevelReference("xs", "admin", "like"), " ").size());
    }

    @Test
    void checkWhenNoCounterpartExists() throws Exception
    {
        // Same name, but in another section, or below the audience's top-level pages.
        saveDocument(topLevelReference("extensions", "admin", "like"));
        saveDocument(new DocumentReference(WIKI, List.of("documentation", "xs", "admin", "other", "like"), WEB_HOME));

        assertEquals(0, check(USER_LIKE).size());
    }

    @Test
    void checkWhenLinksAreNotToCounterparts() throws Exception
    {
        saveDocument(topLevelReference("xs", "admin", "like"));

        List<DocumentationViolation> violations = check(USER_LIKE,
            "* [[Like (for Administrator)>>doc:documentation.extensions.admin.like.WebHome]]",
            "* [[Like attachment>>attach:documentation.xs.admin.like.WebHome@like.png]]",
            "* [[Like>>https://www.xwiki.org/xwiki/bin/view/documentation/xs/admin/like/]]");

        assertEquals(1, violations.size());
        assertMissingLink("documentation.xs.admin.like.WebHome", violations.get(0));
    }

    @Test
    void checkWhenPageIsNotTopLevel() throws Exception
    {
        saveDocument(topLevelReference("xs", "admin", "like"));

        assertEquals(0, check(new DocumentReference(WIKI, List.of("documentation", "xs", "user", "like", "page"),
            WEB_HOME)).size());
        assertEquals(0, check(new DocumentReference(WIKI, List.of("documentation", "xs", "user"), "like")).size());
    }

    @Test
    void checkWhenLinkIsInsideWikiContentMacro() throws Exception
    {
        saveDocument(topLevelReference("xs", "admin", "like"));
        MacroManager macroManager = this.oldcore.getMocker().registerMockComponent(MacroManager.class);
        Macro<?> macro = mock(Macro.class);
        MacroDescriptor descriptor = mock(MacroDescriptor.class);
        ContentDescriptor contentDescriptor = mock(ContentDescriptor.class);
        when(contentDescriptor.getType()).thenReturn(Block.LIST_BLOCK_TYPE);
        when(descriptor.getContentDescriptor()).thenReturn(contentDescriptor);
        when(macro.getDescriptor()).thenReturn(descriptor);
        doReturn(macro).when(macroManager).getMacro(new MacroId("info"));

        assertEquals(0, check(USER_LIKE, "{{info}}\n" + ADMIN_LIKE_LINK + "\n{{/info}}").size());
    }

    @Test
    void checkWhenCounterpartExistenceCheckFails() throws Exception
    {
        DocumentReference adminLike = topLevelReference("xs", "admin", "like");
        saveDocument(adminLike);
        doThrow(new XWikiException(0, 0, "exists failed")).when(this.oldcore.getSpyXWiki())
            .exists(eq(adminLike), any(XWikiContext.class));

        assertEquals(0, check(USER_LIKE).size());
        assertEquals("Failed to check if the page [xwiki:documentation.xs.admin.like.WebHome] exists. Ignoring it "
            + "in the Top-Level Related Links check. Root error cause: [XWikiException: Error number 0 in 0: exists "
            + "failed]", this.logCapture.getMessage(0));
    }

    @Test
    void checkWhenRelatedContentFailsToParse() throws Exception
    {
        saveDocument(topLevelReference("xs", "admin", "like"));
        MacroContentParser contentParser =
            this.oldcore.getMocker().registerMockComponent(MacroContentParser.class);
        when(contentParser.parse(any(), any(), anyBoolean(), anyBoolean()))
            .thenThrow(new MacroExecutionException("parse failed"));

        assertEquals(0, check(USER_LIKE, ADMIN_LIKE_LINK).size());
        assertEquals("Failed to parse the Related content. Ignoring Top-Level Related Links check inside it. "
            + "Root error cause: [MacroExecutionException: parse failed]", this.logCapture.getMessage(0));
    }
}
