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

import org.junit.jupiter.api.Test;
import org.xwiki.contrib.documentation.DocumentationViolation;
import org.xwiki.contrib.documentation.DocumentationViolationSeverity;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link VerbCheck}.
 *
 * @version $Id$
 * @since 1.16
 */
@ComponentTest
class VerbCheckTest
{
    @InjectMockComponents
    private VerbCheck check;

    private XWikiDocument createDocument(String title, String pageName, String type)
    {
        XWikiDocument document = mock(XWikiDocument.class);
        when(document.getTitle()).thenReturn(title);
        when(document.getDocumentReference()).thenReturn(new DocumentReference("wiki", "space", pageName));

        if (type != null) {
            BaseObject docObj = mock(BaseObject.class);
            when(docObj.getStringValue("type")).thenReturn(type);
            when(document.getXObject(any(LocalDocumentReference.class))).thenReturn(docObj);
        } else {
            when(document.getXObject(any(LocalDocumentReference.class))).thenReturn(null);
        }

        return document;
    }

    // --- How-To / Tutorial: must start with imperative verb ---

    @Test
    void checkWhenHowToWithValidTitle()
    {
        assertEquals(0, this.check.check(createDocument("Add a User", "add-user", "howto")).size());
    }

    @Test
    void checkWhenTutorialWithValidTitle()
    {
        assertEquals(0, this.check.check(createDocument("Configure XWiki", "configure-xwiki", "tutorial")).size());
    }

    @Test
    void checkWhenHowToWithGerundTitle()
    {
        List<DocumentationViolation> violations =
            this.check.check(createDocument("Adding a User", "add-user", "howto"));

        assertEquals(1, violations.size());
        assertEquals("The title of a How-To or Tutorial page must start with a verb in imperative form "
            + "(e.g. 'Add a user', not 'Adding a user').", violations.get(0).getViolationMessage());
        assertEquals("Page title: [Adding a User]", violations.get(0).getViolationContext());
        assertEquals(DocumentationViolationSeverity.WARNING, violations.get(0).getViolationSeverity());
    }

    @Test
    void checkWhenHowToWithGerundPageName()
    {
        List<DocumentationViolation> violations =
            this.check.check(createDocument("Add a User", "adding-user", "howto"));

        assertEquals(1, violations.size());
        assertEquals("The page name of a How-To or Tutorial page must start with a verb in imperative form "
            + "(e.g. 'add-user', not 'adding-user').", violations.get(0).getViolationMessage());
        assertEquals("Page name: [adding-user]", violations.get(0).getViolationContext());
        assertEquals(DocumentationViolationSeverity.WARNING, violations.get(0).getViolationSeverity());
    }

    @Test
    void checkWhenHowToWithBothGerunds()
    {
        List<DocumentationViolation> violations =
            this.check.check(createDocument("Adding a User", "adding-user", "howto"));

        assertEquals(2, violations.size());
        assertEquals(DocumentationViolationSeverity.WARNING, violations.get(0).getViolationSeverity());
        assertEquals(DocumentationViolationSeverity.WARNING, violations.get(1).getViolationSeverity());
    }

    @Test
    void checkWhenHowToWithStopWordTitleStarter()
    {
        List<DocumentationViolation> violations =
            this.check.check(createDocument("The User Guide", "user-guide", "howto"));

        assertEquals(1, violations.size());
        assertEquals("Page title: [The User Guide]", violations.get(0).getViolationContext());
    }

    @Test
    void checkWhenHowToWithQuestionWordTitle()
    {
        // "How" is a stop word — title doesn't start with an imperative verb
        List<DocumentationViolation> violations =
            this.check.check(createDocument("How to Install XWiki", "install-xwiki", "howto"));

        assertEquals(1, violations.size());
        assertEquals("Page title: [How to Install XWiki]", violations.get(0).getViolationContext());
    }

    @Test
    void checkWhenHowToWithGerundTitleCaseInsensitive()
    {
        List<DocumentationViolation> violations =
            this.check.check(createDocument("ADDING a User", "add-user", "howto"));

        assertEquals(1, violations.size());
    }

    @Test
    void checkWhenHowToWithWordEndingInThingNotGerund()
    {
        // "something" ends in "thing" not "ing" after stripping — actually "something" ends in "ing"!
        // Use a word that genuinely ends in a non-"ing" suffix
        assertEquals(0, this.check.check(createDocument("Delete something", "delete-something", "howto")).size());
    }

    // --- Reference / Explanation: must not start with verb form ---

    @Test
    void checkWhenReferenceWithValidTitle()
    {
        assertEquals(0,
            this.check.check(createDocument("User Management", "user-management", "reference")).size());
    }

    @Test
    void checkWhenExplanationWithValidTitle()
    {
        assertEquals(0,
            this.check.check(createDocument("XWiki Architecture", "xwiki-architecture", "explanation")).size());
    }

    @Test
    void checkWhenReferenceWithGerundTitle()
    {
        List<DocumentationViolation> violations =
            this.check.check(createDocument("Managing Users", "user-management", "reference"));

        assertEquals(1, violations.size());
        assertEquals("The title of a Reference or Explanation page must not start with a verb "
            + "(e.g. 'User Management', not 'Managing Users').", violations.get(0).getViolationMessage());
        assertEquals("Page title: [Managing Users]", violations.get(0).getViolationContext());
        assertEquals(DocumentationViolationSeverity.WARNING, violations.get(0).getViolationSeverity());
    }

    @Test
    void checkWhenExplanationWithGerundPageName()
    {
        List<DocumentationViolation> violations =
            this.check.check(createDocument("XWiki Architecture", "managing-users", "explanation"));

        assertEquals(1, violations.size());
        assertEquals("The page name of a Reference or Explanation page must not start with a verb "
            + "(e.g. 'user-management', not 'managing-users').", violations.get(0).getViolationMessage());
        assertEquals("Page name: [managing-users]", violations.get(0).getViolationContext());
        assertEquals(DocumentationViolationSeverity.WARNING, violations.get(0).getViolationSeverity());
    }

    @Test
    void checkWhenReferenceWithBothGerunds()
    {
        List<DocumentationViolation> violations =
            this.check.check(createDocument("Managing Users", "managing-users", "reference"));

        assertEquals(2, violations.size());
    }

    // --- Type-independent cases ---

    @Test
    void checkWhenNoDocumentationObject()
    {
        assertEquals(0, this.check.check(createDocument("Adding a User", "adding-user", null)).size());
    }

    @Test
    void checkWhenUnknownType()
    {
        // "landing" is not a recognized Diataxis type — no check applies
        assertEquals(0, this.check.check(createDocument("Adding a User", "adding-user", "landing")).size());
    }
}
