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

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.contrib.documentation.DocumentationViolationGroup;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.query.Query;
import org.xwiki.query.QueryManager;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.objects.classes.BaseClass;
import com.xpn.xwiki.test.MockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.InjectMockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.OldcoreTest;
import com.xpn.xwiki.test.reference.ReferenceComponentList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link FalsePositives}.
 *
 * @version $Id$
 */
@OldcoreTest
@ReferenceComponentList
class FalsePositivesTest
{
    private static final WikiReference WIKI_REFERENCE = new WikiReference("wiki");

    private static final DocumentReference VIOLATION_CLASS_REFERENCE =
        new DocumentReference("wiki", List.of("DocApp", "Code"), "DocumentationViolationClass");

    private static final DocumentReference USER_REFERENCE = new DocumentReference("wiki", "XWiki", "User");

    private static final DocumentReference PAGE_REFERENCE =
        new DocumentReference("wiki", List.of("documentation", "install"), "WebHome");

    private static final DocumentReference CHILD_PAGE_REFERENCE =
        new DocumentReference("wiki", List.of("documentation", "install", "child"), "WebHome");

    private static final DocumentReference OTHER_PAGE_REFERENCE =
        new DocumentReference("wiki", List.of("documentation", "other"), "WebHome");

    @InjectMockComponents
    private FalsePositives falsePositives;

    @InjectMockitoOldcore
    private MockitoOldcore oldcore;

    @MockComponent
    private QueryManager queryManager;

    private XWikiContext xcontext;

    @BeforeEach
    void setUp() throws Exception
    {
        this.xcontext = this.oldcore.getXWikiContext();
        this.xcontext.setWikiId(WIKI_REFERENCE.getName());
        this.xcontext.setUserReference(USER_REFERENCE);

        XWikiDocument violationClassDocument = new XWikiDocument(VIOLATION_CLASS_REFERENCE);
        BaseClass violationClass = violationClassDocument.getXClass();
        violationClass.addTextField("message", "Message", 100);
        violationClass.addTextField("check", "Check", 100);
        violationClass.addTextField("severity", "Severity", 100);
        violationClass.addBooleanField("falsePositive", "False Positive", "checkbox");
        violationClass.addUsersField("falsePositiveBy", "Marked By");
        violationClass.addDateField("falsePositiveDate", "Marked On");
        violationClass.addTextAreaField("falsePositiveReason", "Reason", 40, 3);
        this.oldcore.getSpyXWiki().saveDocument(violationClassDocument, this.xcontext);
    }

    @Test
    void markAndUnmark() throws Exception
    {
        savePage(PAGE_REFERENCE, "technicalId", "verb");

        assertTrue(this.falsePositives.mark(PAGE_REFERENCE, 1, "Not about an extension"));

        BaseObject violation = getViolations(PAGE_REFERENCE).get(1);
        assertEquals(1, violation.getIntValue("falsePositive"));
        assertEquals("wiki:XWiki.User", violation.getLargeStringValue("falsePositiveBy"));
        assertNotNull(violation.getDateValue("falsePositiveDate"));
        assertEquals("Not about an extension", violation.getLargeStringValue("falsePositiveReason"));
        assertEquals(0, getViolations(PAGE_REFERENCE).get(0).getIntValue("falsePositive"));
        XWikiDocument document = this.oldcore.getSpyXWiki().getDocument(PAGE_REFERENCE, this.xcontext);
        assertEquals("Marked a documentation violation as a false positive", document.getComment());
        assertEquals(USER_REFERENCE, document.getAuthorReference());

        // Already marked.
        assertFalse(this.falsePositives.mark(PAGE_REFERENCE, 1, null));

        assertTrue(this.falsePositives.unmark(PAGE_REFERENCE, 1));

        violation = getViolations(PAGE_REFERENCE).get(1);
        assertEquals(0, violation.getIntValue("falsePositive"));
        assertEquals("", violation.getLargeStringValue("falsePositiveBy"));
        assertNull(violation.getDateValue("falsePositiveDate"));
        assertEquals("", violation.getLargeStringValue("falsePositiveReason"));

        // Not marked anymore.
        assertFalse(this.falsePositives.unmark(PAGE_REFERENCE, 1));
    }

    @Test
    void markWhenNoSuchViolation() throws Exception
    {
        savePage(PAGE_REFERENCE, "technicalId");

        assertFalse(this.falsePositives.mark(PAGE_REFERENCE, 5, null));
        assertEquals("1.1", this.oldcore.getSpyXWiki().getDocument(PAGE_REFERENCE, this.xcontext).getVersion());
    }

    @Test
    void getScopes()
    {
        // A terminal page is only under the nested pages of its spaces.
        assertEquals(List.of(PAGE_REFERENCE, new DocumentReference("wiki", "documentation", "WebHome")),
            this.falsePositives.getScopes(new DocumentReference("wiki", List.of("documentation", "install"), "page")));
        // A nested page is first itself.
        assertEquals(List.of(CHILD_PAGE_REFERENCE, PAGE_REFERENCE,
            new DocumentReference("wiki", "documentation", "WebHome")),
            this.falsePositives.getScopes(CHILD_PAGE_REFERENCE));
    }

    @Test
    void getGroups() throws Exception
    {
        // The empty check is the one of a violation stored before the check was recorded.
        savePage(PAGE_REFERENCE, "technicalId", "verb", "");
        savePage(CHILD_PAGE_REFERENCE, "technicalId");
        savePage(OTHER_PAGE_REFERENCE, "technicalId");
        this.falsePositives.mark(CHILD_PAGE_REFERENCE, 0, null);
        DocumentReference otherChildPage =
            new DocumentReference("wiki", List.of("documentation", "install", "child2"), "WebHome");
        savePage(otherChildPage, "technicalId");
        mockQuery("documentation.install.WebHome", "documentation.install.child.WebHome",
            "documentation.install.child2.WebHome", "documentation.other.WebHome");

        List<DocumentationViolationGroup> groups = this.falsePositives.getGroups(PAGE_REFERENCE);

        assertEquals(3, groups.size());
        // The violations marked as false positives and the ones of the pages not under the passed page are ignored.
        assertEquals("technicalId", groups.get(0).getCheck());
        assertEquals("Message of technicalId", groups.get(0).getMessage());
        assertEquals("Warning", groups.get(0).getSeverity());
        assertEquals(2, groups.get(0).getViolationCount());
        assertEquals(List.of(PAGE_REFERENCE, otherChildPage), groups.get(0).getPages());
        assertEquals("verb", groups.get(1).getCheck());
        assertEquals("", groups.get(2).getCheck());
        assertEquals("Message of ", groups.get(2).getMessage());
    }

    @Test
    void markAllWhenTerminalPage() throws Exception
    {
        // A terminal page with the same name as the nested page, and the nested page itself.
        DocumentReference terminalPage = new DocumentReference("wiki", "documentation", "install");
        savePage(terminalPage, "technicalId");
        savePage(PAGE_REFERENCE, "technicalId");
        mockQuery("documentation.install", "documentation.install.WebHome");

        // Only the terminal page is marked, since there's no page under a terminal page.
        assertEquals(1, this.falsePositives.markAll("technicalId", "Message of technicalId", terminalPage, null));
        assertEquals(1, getViolations(terminalPage).get(0).getIntValue("falsePositive"));
        assertEquals(0, getViolations(PAGE_REFERENCE).get(0).getIntValue("falsePositive"));
    }

    @Test
    void markAll() throws Exception
    {
        savePage(PAGE_REFERENCE, "technicalId", "verb", "technicalId");
        savePage(CHILD_PAGE_REFERENCE, "technicalId");
        savePage(OTHER_PAGE_REFERENCE, "technicalId");
        mockQuery("documentation.install.WebHome", "documentation.install.child.WebHome",
            "documentation.other.WebHome");

        assertEquals(3, this.falsePositives.markAll("technicalId", "Message of technicalId", PAGE_REFERENCE,
            "Not about an extension"));

        List<BaseObject> violations = getViolations(PAGE_REFERENCE);
        assertEquals(1, violations.get(0).getIntValue("falsePositive"));
        assertEquals(0, violations.get(1).getIntValue("falsePositive"));
        assertEquals(1, violations.get(2).getIntValue("falsePositive"));
        assertEquals("Marked documentation violations as false positives",
            this.oldcore.getSpyXWiki().getDocument(PAGE_REFERENCE, this.xcontext).getComment());
        assertEquals(1, getViolations(CHILD_PAGE_REFERENCE).get(0).getIntValue("falsePositive"));
        // The other page isn't located under the passed page.
        assertEquals(0, getViolations(OTHER_PAGE_REFERENCE).get(0).getIntValue("falsePositive"));
        // Only the violations with the passed message are marked.
        assertEquals(0, this.falsePositives.markAll("technicalId", "Other message", OTHER_PAGE_REFERENCE, null));
    }

    private void mockQuery(String... pageNames) throws Exception
    {
        Query query = mock(Query.class);
        when(this.queryManager.createQuery(anyString(), anyString())).thenReturn(query);
        when(query.bindValue("className", "DocApp.Code.DocumentationViolationClass")).thenReturn(query);
        when(query.setWiki("wiki")).thenReturn(query);
        when(query.execute()).thenReturn(List.of(pageNames));
    }

    private void savePage(DocumentReference reference, String... checks) throws Exception
    {
        XWikiDocument document = new XWikiDocument(reference);
        for (String check : checks) {
            BaseObject violation = document.newXObject(VIOLATION_CLASS_REFERENCE, this.xcontext);
            violation.setStringValue("message", "Message of " + check);
            violation.setStringValue("check", check);
            violation.setStringValue("severity", "Warning");
        }
        this.oldcore.getSpyXWiki().saveDocument(document, this.xcontext);
    }

    private List<BaseObject> getViolations(DocumentReference reference) throws Exception
    {
        return this.oldcore.getSpyXWiki().getDocument(reference, this.xcontext).getXObjects(VIOLATION_CLASS_REFERENCE);
    }
}
