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
 * Unit tests for {@link RelatedLinkLabelCheck}.
 *
 * @version $Id$
 * @since 1.15
 */
@AllComponents
@OldcoreTest
class RelatedLinkLabelCheckTest
{
    private static final String WIKI = "xwiki";

    private static final String VIOLATION_MESSAGE = "Related link labels must follow the format "
        + "'<exact page title> (for <target>)', where the title and the target (User, Administrator or Developer) "
        + "match the linked page.";

    private static final DocumentReference LIKE_REFERENCE =
        new DocumentReference(WIKI, List.of("documentation", "xs", "user", "like"), "WebHome");

    private static final DocumentReference IMPORTER_REFERENCE =
        new DocumentReference(WIKI, List.of("documentation", "xs", "admin", "office-importer"), "WebHome");

    @InjectMockitoOldcore
    private MockitoOldcore oldcore;

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    @BeforeEach
    void setUp() throws Exception
    {
        // Wiki references (doc:, page:, etc.) are only parsed as such when a WikiModel is available.
        this.oldcore.getMocker().registerMockComponent(WikiModel.class);

        saveDocument(LIKE_REFERENCE, "Like", "user");
        saveDocument(IMPORTER_REFERENCE, "Office Importer", "administrator");
    }

    private void saveDocument(DocumentReference reference, String title, String target) throws XWikiException
    {
        XWikiDocument document = new XWikiDocument(reference);
        document.setTitle(title);
        if (target != null) {
            document.addXObject(createDocumentationObject("target", target));
        }
        this.oldcore.getSpyXWiki().saveDocument(document, this.oldcore.getXWikiContext());
    }

    private BaseObject createDocumentationObject(String property, String value)
    {
        BaseObject docObject = new BaseObject();
        docObject.setXClassReference(new DocumentReference(WIKI, List.of("DocApp", "Code"), "DocumentationClass"));
        if ("related".equals(property)) {
            docObject.setLargeStringValue(property, value);
        } else {
            docObject.setStringValue(property, value);
        }
        return docObject;
    }

    private DocumentationCheck getChecker() throws Exception
    {
        return this.oldcore.getMocker().getInstance(DocumentationCheck.class, "relatedLinkLabel");
    }

    private List<DocumentationViolation> check(String... related) throws Exception
    {
        XWikiDocument document =
            new XWikiDocument(new DocumentReference(WIKI, List.of("documentation", "xs", "user"), "current"));
        document.setSyntax(Syntax.XWIKI_2_1);
        if (related.length > 0) {
            document.addXObject(createDocumentationObject("related", String.join("\n", related)));
        }
        return getChecker().check(document);
    }

    private void assertLabelViolation(String label, String expectedLabel, List<DocumentationViolation> violations)
    {
        assertEquals(1, violations.size());
        assertEquals(VIOLATION_MESSAGE, violations.get(0).getViolationMessage());
        assertEquals(String.format("Label: [%s], Expected: [%s]", label, expectedLabel),
            violations.get(0).getViolationContext());
        assertEquals(DocumentationViolationSeverity.WARNING, violations.get(0).getViolationSeverity());
    }

    @Test
    void checkWhenLabelsAreValid() throws Exception
    {
        assertEquals(0, check(
            "* [[Like (for User)>>doc:documentation.xs.user.like.WebHome]]",
            "* [[Office Importer (for Administrator)>>doc:documentation.xs.admin.office-importer.WebHome]]").size());
    }

    @Test
    void checkWhenLabelHasNoTargetQualifier() throws Exception
    {
        assertEquals(0, check(
            "* [[Something else>>doc:documentation.xs.user.like.WebHome]]",
            "* [[doc:documentation.xs.user.like.WebHome]]").size());
    }

    @Test
    void checkWhenTitleCapitalizationDiffers() throws Exception
    {
        assertLabelViolation("Office importer (for Administrator)", "Office Importer (for Administrator)",
            check("* [[Office importer (for Administrator)>>doc:documentation.xs.admin.office-importer.WebHome]]"));
    }

    @Test
    void checkWhenTargetIsNotAnAllowedValue() throws Exception
    {
        assertLabelViolation("Like (for users)", "Like (for User)",
            check("* [[Like (for users)>>doc:documentation.xs.user.like.WebHome]]"));
    }

    @Test
    void checkWhenTargetDoesNotMatchLinkedPage() throws Exception
    {
        assertLabelViolation("Office Importer (for User)", "Office Importer (for Administrator)",
            check("* [[Office Importer (for User)>>doc:documentation.xs.admin.office-importer.WebHome]]"));
    }

    @Test
    void checkWhenTextFollowsTargetQualifier() throws Exception
    {
        assertLabelViolation("Like (for User) page", "Like (for User)",
            check("* [[Like (for User) page>>doc:documentation.xs.user.like.WebHome]]"));
    }

    @Test
    void checkWhenLinkedPageDoesNotExist() throws Exception
    {
        assertEquals(0, check("* [[Missing (for Developer)>>doc:documentation.xs.dev.missing.WebHome]]").size());
        assertLabelViolation("Missing (for Dev)", "Missing (for User|Administrator|Developer)",
            check("* [[Missing (for Dev)>>doc:documentation.xs.dev.missing.WebHome]]"));
    }

    @Test
    void checkWhenLinkedPageHasNoDocumentationObjectOrTitle() throws Exception
    {
        saveDocument(new DocumentReference(WIKI, "Main", "Other"), "Other page", null);
        saveDocument(new DocumentReference(WIKI, "Main", "Untitled"), "", "developer");

        assertEquals(0, check("* [[Other page (for Developer)>>doc:Main.Other]]").size());
        assertEquals(0, check("* [[Anything (for Developer)>>doc:Main.Untitled]]").size());
        assertLabelViolation("Other (for Developer)", "Other page (for Developer)",
            check("* [[Other (for Developer)>>doc:Main.Other]]"));
        assertLabelViolation("Anything (for User)", "Anything (for Developer)",
            check("* [[Anything (for User)>>doc:Main.Untitled]]"));
    }

    @Test
    void checkWhenLinkIsExternal() throws Exception
    {
        assertEquals(0, check("* [[XWiki (for User)>>https://www.xwiki.org]]").size());
    }

    @Test
    void checkWhenNoRelatedContent() throws Exception
    {
        assertEquals(0, check().size());
        assertEquals(0, check("").size());
    }

    @Test
    void checkWhenLinkIsInsideWikiContentMacro() throws Exception
    {
        MacroManager macroManager = this.oldcore.getMocker().registerMockComponent(MacroManager.class);
        Macro<?> macro = mock(Macro.class);
        MacroDescriptor descriptor = mock(MacroDescriptor.class);
        ContentDescriptor contentDescriptor = mock(ContentDescriptor.class);
        when(contentDescriptor.getType()).thenReturn(Block.LIST_BLOCK_TYPE);
        when(descriptor.getContentDescriptor()).thenReturn(contentDescriptor);
        when(macro.getDescriptor()).thenReturn(descriptor);
        doReturn(macro).when(macroManager).getMacro(new MacroId("info"));

        assertLabelViolation("like (for User)", "Like (for User)",
            check("{{info}}\n[[like (for User)>>doc:documentation.xs.user.like.WebHome]]\n{{/info}}"));
    }

    @Test
    void checkWhenRelatedContentFailsToParse() throws Exception
    {
        MacroContentParser contentParser =
            this.oldcore.getMocker().registerMockComponent(MacroContentParser.class);
        when(contentParser.parse(any(), any(), anyBoolean(), anyBoolean()))
            .thenThrow(new MacroExecutionException("parse failed"));

        assertEquals(0, check("[[like (for User)>>doc:documentation.xs.user.like.WebHome]]").size());
        assertEquals("Failed to parse the Related content. Ignoring Related Link Label check inside it. "
            + "Root error cause: [MacroExecutionException: parse failed]", this.logCapture.getMessage(0));
    }

    @Test
    void checkWhenLinkedPageFailsToLoad() throws Exception
    {
        doThrow(new XWikiException(0, 0, "load failed")).when(this.oldcore.getSpyXWiki())
            .getDocument(eq(LIKE_REFERENCE), any(XWikiContext.class));

        assertLabelViolation("like (for Dev)", "like (for User|Administrator|Developer)",
            check("[[like (for Dev)>>doc:documentation.xs.user.like.WebHome]]"));
        assertEquals("Failed to load the linked document [xwiki:documentation.xs.user.like.WebHome]. Ignoring its "
            + "title and target in the Related Link Label check. Root error cause: [XWikiException: Error number 0 "
            + "in 0: load failed]", this.logCapture.getMessage(0));
    }
}
