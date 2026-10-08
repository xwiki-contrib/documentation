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

import jakarta.inject.Named;
import jakarta.inject.Provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.xwiki.contrib.documentation.DocumentationManager;
import org.xwiki.index.IndexException;
import org.xwiki.job.DefaultRequest;
import org.xwiki.job.JobGroupPath;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.query.Query;
import org.xwiki.query.QueryManager;
import org.xwiki.test.LogLevel;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CheckAllPagesJob}.
 *
 * @version $Id$
 */
@ComponentTest
class CheckAllPagesJobTest
{
    private static final WikiReference WIKI_REFERENCE = new WikiReference("wiki");

    private static final String CLASS_NAME = "DocApp.Code.DocumentationClass";

    @InjectMockComponents
    private CheckAllPagesJob job;

    @MockComponent
    private QueryManager queryManager;

    @MockComponent
    @Named("current")
    private DocumentReferenceResolver<String> resolver;

    @MockComponent
    @Named("local")
    private EntityReferenceSerializer<String> localSerializer;

    @MockComponent
    private DocumentationManager manager;

    @MockComponent
    private Provider<XWikiContext> xcontextProvider;

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.INFO);

    private XWikiContext xcontext;

    private XWiki xwiki;

    private Query query;

    @BeforeEach
    void setUp() throws Exception
    {
        this.xcontext = mock(XWikiContext.class);
        this.xwiki = mock(XWiki.class);
        when(this.xcontextProvider.get()).thenReturn(this.xcontext);
        when(this.xcontext.getWiki()).thenReturn(this.xwiki);
        when(this.xcontext.getWikiReference()).thenReturn(WIKI_REFERENCE);

        when(this.localSerializer.serialize(DocumentationPages.DOCUMENTATION_CLASS_REFERENCE)).thenReturn(CLASS_NAME);
        this.query = mock(Query.class);
        when(this.queryManager.createQuery(anyString(), any())).thenReturn(this.query);
        when(this.query.bindValue("className", CLASS_NAME)).thenReturn(this.query);
        when(this.query.setWiki("wiki")).thenReturn(this.query);

        DefaultRequest request = new DefaultRequest();
        request.setId(CheckAllPagesJob.getJobId("wiki"));
        this.job.initialize(request);
    }

    @Test
    void runCountsTheCheckedAndUpdatedPages() throws Exception
    {
        XWikiDocument updated = mockDocument("updated", "space", true);
        XWikiDocument unchanged = mockDocument("unchanged", "space", true);
        when(this.query.execute()).thenReturn(List.of("space.updated", "space.unchanged"));
        when(this.manager.analyse(updated)).thenReturn(true);

        this.job.runInternal();

        verify(this.manager).analyse(updated);
        verify(this.manager).analyse(unchanged);
        assertEquals("Updated the violations of [wiki:space.updated].", this.logCapture.getMessage(0));
        assertEquals("Checked [2] documentation pages: [1] updated, [0] failed.", this.logCapture.getMessage(1));
        assertEquals(2, this.job.getStatus().getCheckedCount());
        assertEquals(1, this.job.getStatus().getUpdatedCount());
        assertEquals(0, this.job.getStatus().getFailedCount());
    }

    @Test
    void runSkipsThePagesThatAreNotAnalysable() throws Exception
    {
        XWikiDocument template = mockDocument("template", DocumentationPages.APPLICATION_SPACE, true);
        XWikiDocument removedObject = mockDocument("removedObject", "space", false);
        when(this.query.execute()).thenReturn(List.of("DocApp.template", "space.removedObject"));

        this.job.runInternal();

        verify(this.manager, never()).analyse(template);
        verify(this.manager, never()).analyse(removedObject);
        assertEquals("Checked [0] documentation pages: [0] updated, [0] failed.", this.logCapture.getMessage(0));
    }

    @Test
    void runKeepsCheckingWhenAPageFails() throws Exception
    {
        XWikiDocument failing = mockDocument("failing", "space", true);
        XWikiDocument working = mockDocument("working", "space", true);
        when(this.query.execute()).thenReturn(List.of("space.failing", "space.working"));
        doThrow(new IndexException("error")).when(this.manager).analyse(failing);

        this.job.runInternal();

        verify(this.manager).analyse(working);
        assertEquals("Failed to check [wiki:space.failing].", this.logCapture.getMessage(0));
        assertEquals("Checked [2] documentation pages: [0] updated, [1] failed.", this.logCapture.getMessage(1));
        assertEquals(1, this.job.getStatus().getFailedCount());
    }

    @Test
    void runStopsWhenCanceled() throws Exception
    {
        XWikiDocument page = mockDocument("page", "space", true);
        when(this.query.execute()).thenReturn(List.of("space.page"));
        assertTrue(this.job.getStatus().isCancelable());

        this.job.getStatus().cancel();
        this.job.runInternal();

        verify(this.manager, never()).analyse(page);
        assertEquals("Canceled the check of the documentation pages.", this.logCapture.getMessage(0));
        assertEquals("Checked [0] documentation pages: [0] updated, [0] failed.", this.logCapture.getMessage(1));
    }

    @Test
    void getTypeAndGroupPath()
    {
        assertEquals("documentation.checkAllPages", this.job.getType());
        assertEquals(new JobGroupPath(List.of("documentation", "checkAllPages", "wiki")), this.job.getGroupPath());
    }

    /**
     * @return the clone of the document that the job analyses
     */
    private XWikiDocument mockDocument(String name, String space, boolean hasDocumentationObject) throws Exception
    {
        DocumentReference reference = new DocumentReference("wiki", space, name);
        when(this.resolver.resolve(space + "." + name, WIKI_REFERENCE)).thenReturn(reference);
        XWikiDocument document = mock(XWikiDocument.class);
        XWikiDocument clone = mock(XWikiDocument.class);
        when(this.xwiki.getDocument(reference, this.xcontext)).thenReturn(document);
        when(document.clone()).thenReturn(clone);
        when(clone.getDocumentReference()).thenReturn(reference);
        when(clone.getXObject(DocumentationPages.DOCUMENTATION_CLASS_REFERENCE))
            .thenReturn(hasDocumentationObject ? mock(BaseObject.class) : null);
        return clone;
    }
}
