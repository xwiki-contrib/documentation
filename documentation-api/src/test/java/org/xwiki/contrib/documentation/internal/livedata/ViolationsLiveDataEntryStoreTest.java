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
package org.xwiki.contrib.documentation.internal.livedata;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.inject.Named;
import javax.inject.Provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.contrib.documentation.internal.DocumentationPages;
import org.xwiki.livedata.LiveData;
import org.xwiki.livedata.LiveDataException;
import org.xwiki.livedata.LiveDataQuery;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.query.QueryException;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.security.authorization.ContextualAuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ViolationsLiveDataEntryStore}.
 *
 * @version $Id$
 */
@ComponentTest
class ViolationsLiveDataEntryStoreTest
{
    private static final String PAGE_NAME = "documentation.install.WebHome";

    private static final DocumentReference PAGE_REFERENCE =
        new DocumentReference("wiki", List.of("documentation", "install"), "WebHome");

    private static final DocumentReference USER_REFERENCE = new DocumentReference("wiki", "XWiki", "User");

    @InjectMockComponents
    private ViolationsLiveDataEntryStore entryStore;

    @MockComponent
    private ViolationsLiveDataQueryHelper queryHelper;

    @MockComponent
    private ContextualAuthorizationManager authorization;

    @MockComponent
    @Named("current")
    private DocumentReferenceResolver<String> resolver;

    @MockComponent
    private Provider<XWikiContext> xcontextProvider;

    private final XWikiContext xcontext = mock(XWikiContext.class);

    private final XWiki xwiki = mock(XWiki.class);

    private final XWikiDocument document = mock(XWikiDocument.class);

    @BeforeEach
    void setUp() throws Exception
    {
        when(this.xcontextProvider.get()).thenReturn(this.xcontext);
        when(this.xcontext.getWiki()).thenReturn(this.xwiki);
        when(this.resolver.resolve(PAGE_NAME)).thenReturn(PAGE_REFERENCE);
        when(this.authorization.hasAccess(Right.VIEW, PAGE_REFERENCE)).thenReturn(true);
        when(this.xwiki.getDocument(PAGE_REFERENCE, this.xcontext)).thenReturn(this.document);
        when(this.xwiki.getURL(PAGE_REFERENCE, "view", this.xcontext)).thenReturn("/view/install");
        when(this.document.getRenderedTitle(Syntax.PLAIN_1_0, this.xcontext)).thenReturn("Install");
        when(this.document.getTitle()).thenReturn("Install");
    }

    @Test
    void getEntriesDisplayTheirOwnViolation() throws Exception
    {
        mockViolation(0, "Page title message", "Error", "pageTitle");
        BaseObject markedViolation = mockViolation(2, "Image message", "Warning", "imageMacroAlt");
        when(markedViolation.getLargeStringValue("falsePositiveReason")).thenReturn("Decorative image");
        when(markedViolation.getLargeStringValue("falsePositiveBy")).thenReturn("wiki:XWiki.User");
        when(markedViolation.getDateValue("falsePositiveDate")).thenReturn(new Date(42L));
        when(this.resolver.resolve("wiki:XWiki.User")).thenReturn(USER_REFERENCE);
        when(this.xwiki.getPlainUserName(USER_REFERENCE, this.xcontext)).thenReturn("User Name");
        when(this.xwiki.getURL(USER_REFERENCE, "view", this.xcontext)).thenReturn("/view/user");
        LiveDataQuery query = new LiveDataQuery();
        when(this.queryHelper.getViolations(query))
            .thenReturn(List.of(new Object[] {PAGE_NAME, 0}, new Object[] {PAGE_NAME, 2}));

        LiveData liveData = this.entryStore.get(query);

        assertEquals(2L, liveData.getCount());
        assertEquals(List.of(
            Map.of("id", PAGE_NAME + "/0", "doc.viewable", true, "doc.title", "Install", "doc.url", "/view/install",
                "message", "Page title message", "context", "Page title message context", "severity", "Error",
                "check", "pageTitle", "falsePositiveReason", ""),
            Map.ofEntries(Map.entry("id", PAGE_NAME + "/2"), Map.entry("doc.viewable", true),
                Map.entry("doc.title", "Install"), Map.entry("doc.url", "/view/install"),
                Map.entry("message", "Image message"), Map.entry("context", "Image message context"),
                Map.entry("severity", "Warning"), Map.entry("check", "imageMacroAlt"),
                Map.entry("falsePositiveReason", "Decorative image"), Map.entry("falsePositiveBy", "User Name"),
                Map.entry("falsePositiveBy_url", "/view/user"), Map.entry("falsePositiveDate", 42L))),
            liveData.getEntries());
    }

    @Test
    void getEntryWithComputedTitle() throws Exception
    {
        mockViolation(1, "message", "Error", "check");
        when(this.document.getTitle()).thenReturn("$services.localization.render('title')");

        Map<String, Object> entry = this.entryStore.get(PAGE_NAME + "/1").orElseThrow();

        assertEquals("Install", entry.get("doc.title"));
        assertEquals("$services.localization.render('title')", entry.get("doc.title_raw"));
    }

    @Test
    void getEntryWhenThePageCannotBeViewed() throws Exception
    {
        when(this.authorization.hasAccess(Right.VIEW, PAGE_REFERENCE)).thenReturn(false);
        LiveDataQuery query = new LiveDataQuery();
        when(this.queryHelper.getViolations(query)).thenReturn(List.<Object[]>of(new Object[] {PAGE_NAME, 0}));

        // The entry doesn't hold the identifier, which holds the page name.
        assertEquals(List.of(Map.of("doc.viewable", false)), this.entryStore.get(query).getEntries());
        assertEquals(Optional.empty(), this.entryStore.get(PAGE_NAME + "/0"));
    }

    @Test
    void getPaginatesTheViolations() throws Exception
    {
        mockViolation(1, "Second", "Error", "check");
        mockViolation(2, "Third", "Error", "check");
        LiveDataQuery query = new LiveDataQuery();
        query.setOffset(1L);
        query.setLimit(2);
        when(this.queryHelper.getViolations(query)).thenReturn(List.of(new Object[] {PAGE_NAME, 0},
            new Object[] {PAGE_NAME, 1}, new Object[] {PAGE_NAME, 2}, new Object[] {PAGE_NAME, 3}));

        LiveData liveData = this.entryStore.get(query);

        assertEquals(4L, liveData.getCount());
        assertEquals(List.of(PAGE_NAME + "/1", PAGE_NAME + "/2"),
            liveData.getEntries().stream().map(entry -> entry.get("id")).toList());

        // An offset beyond the last violation.
        query.setOffset(10L);
        liveData = this.entryStore.get(query);
        assertEquals(4L, liveData.getCount());
        assertTrue(liveData.getEntries().isEmpty());
    }

    @Test
    void getEntryWhenTheViolationDoesNotExist()
        throws Exception
    {
        assertEquals(Optional.empty(), this.entryStore.get(PAGE_NAME + "/3"));
        assertEquals(Optional.empty(), this.entryStore.get(PAGE_NAME));
        assertEquals(Optional.empty(), this.entryStore.get(PAGE_NAME + "/x"));
    }

    @Test
    void getWhenTheQueryFails() throws Exception
    {
        LiveDataQuery query = new LiveDataQuery();
        QueryException exception = new QueryException("error", null, null);
        when(this.queryHelper.getViolations(query)).thenThrow(exception);

        LiveDataException liveDataException = assertThrows(LiveDataException.class, () -> this.entryStore.get(query));

        assertEquals("Failed to list the documentation violations", liveDataException.getMessage());
        assertSame(exception, liveDataException.getCause());
    }

    @Test
    void getWhenThePageCannotBeLoaded() throws Exception
    {
        XWikiException exception = new XWikiException();
        when(this.xwiki.getDocument(PAGE_REFERENCE, this.xcontext)).thenThrow(exception);

        LiveDataException liveDataException =
            assertThrows(LiveDataException.class, () -> this.entryStore.get(PAGE_NAME + "/0"));

        assertTrue(liveDataException.getMessage().startsWith("Failed to load the page"));
        assertSame(exception, liveDataException.getCause());
    }

    private BaseObject mockViolation(int number, String message, String severity, String check)
    {
        BaseObject violation = mock(BaseObject.class);
        when(this.document.getXObject(DocumentationPages.VIOLATION_CLASS_REFERENCE, number)).thenReturn(violation);
        when(violation.getStringValue("message")).thenReturn(message);
        when(violation.getStringValue("context")).thenReturn(message + " context");
        when(violation.getStringValue("severity")).thenReturn(severity);
        when(violation.getStringValue("check")).thenReturn(check);
        when(violation.getLargeStringValue("falsePositiveReason")).thenReturn("");
        when(violation.getLargeStringValue("falsePositiveBy")).thenReturn("");
        return violation;
    }
}
