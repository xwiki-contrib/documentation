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
package org.xwiki.contrib.documentation.script;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import javax.inject.Provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.context.concurrent.ContextStoreManager;
import org.xwiki.contrib.documentation.DocumentationManager;
import org.xwiki.job.DefaultRequest;
import org.xwiki.job.Job;
import org.xwiki.job.JobException;
import org.xwiki.job.JobExecutor;
import org.xwiki.job.JobStatusStore;
import org.xwiki.job.Request;
import org.xwiki.job.event.status.CancelableJobStatus;
import org.xwiki.job.event.status.JobStatus;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.security.authorization.AccessDeniedException;
import org.xwiki.security.authorization.ContextualAuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DocumentationScriptService}.
 *
 * @version $Id$
 */
@ComponentTest
class DocumentationScriptServiceTest
{
    private static final WikiReference WIKI_REFERENCE = new WikiReference("wiki");

    private static final List<String> JOB_ID = List.of("documentation", "checkAllPages", "wiki");

    @InjectMockComponents
    private DocumentationScriptService scriptService;

    @MockComponent
    private DocumentationManager manager;

    @MockComponent
    private ContextualAuthorizationManager authorization;

    @MockComponent
    private JobExecutor jobExecutor;

    @MockComponent
    private JobStatusStore jobStatusStore;

    @MockComponent
    private ContextStoreManager contextStore;

    @MockComponent
    private Provider<XWikiContext> xcontextProvider;

    @BeforeEach
    void setUp()
    {
        XWikiContext xcontext = mock(XWikiContext.class);
        when(this.xcontextProvider.get()).thenReturn(xcontext);
        when(xcontext.getWikiReference()).thenReturn(WIKI_REFERENCE);
    }

    @Test
    void analyse() throws Exception
    {
        XWikiDocument document = mock(XWikiDocument.class);
        when(this.manager.analyse(document)).thenReturn(true);

        assertTrue(this.scriptService.analyse(document));
    }

    @Test
    void canCheckAllPages()
    {
        when(this.authorization.hasAccess(Right.ADMIN, WIKI_REFERENCE)).thenReturn(true);

        assertTrue(this.scriptService.canCheckAllPages());
    }

    @Test
    void canCheckAllPagesWhenNotAdmin()
    {
        assertFalse(this.scriptService.canCheckAllPages());
    }

    @Test
    void checkAllPagesWhenNotAdmin() throws Exception
    {
        doThrow(AccessDeniedException.class).when(this.authorization).checkAccess(Right.ADMIN, WIKI_REFERENCE);

        assertThrows(AccessDeniedException.class, () -> this.scriptService.checkAllPages());

        verify(this.jobExecutor, never()).execute(anyString(), any());
    }

    @Test
    void checkAllPagesStartsTheJob() throws Exception
    {
        Map<String, Serializable> context = Map.of("wiki", "wiki");
        when(this.contextStore.save(anyCollection())).thenReturn(context);
        JobStatus status = mock(JobStatus.class);
        Job job = mock(Job.class);
        when(job.getStatus()).thenReturn(status);
        when(this.jobExecutor.execute(eq("documentation.checkAllPages"), any())).thenReturn(job);

        assertSame(status, this.scriptService.checkAllPages());

        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(this.jobExecutor).execute(eq("documentation.checkAllPages"), requestCaptor.capture());
        assertEquals(JOB_ID, requestCaptor.getValue().getId());
        assertEquals(context, ((DefaultRequest) requestCaptor.getValue()).getContext());
    }

    @Test
    void checkAllPagesWhenAlreadyRunning() throws Exception
    {
        JobStatus status = mockJob(JobStatus.State.RUNNING);

        assertSame(status, this.scriptService.checkAllPages());

        verify(this.jobExecutor, never()).execute(anyString(), any());
    }

    @Test
    void checkAllPagesWhenTheLastRunIsFinished() throws Exception
    {
        mockJob(JobStatus.State.FINISHED);
        JobStatus newStatus = mock(JobStatus.class);
        Job newJob = mock(Job.class);
        when(newJob.getStatus()).thenReturn(newStatus);
        when(this.jobExecutor.execute(eq("documentation.checkAllPages"), any())).thenReturn(newJob);

        assertSame(newStatus, this.scriptService.checkAllPages());
    }

    @Test
    void checkAllPagesWhenTheContextCannotBeSaved() throws Exception
    {
        when(this.contextStore.save(anyCollection())).thenThrow(ComponentLookupException.class);

        JobException exception = assertThrows(JobException.class, () -> this.scriptService.checkAllPages());

        assertEquals("Failed to save the context of the analysis of all documentation pages", exception.getMessage());
    }

    @Test
    void cancelCheckAllPages() throws Exception
    {
        CancelableJobStatus status = mockCancelableJob(JobStatus.State.RUNNING);

        assertTrue(this.scriptService.cancelCheckAllPages());

        verify(status).cancel();
    }

    @Test
    void cancelCheckAllPagesWhenFinished() throws Exception
    {
        CancelableJobStatus status = mockCancelableJob(JobStatus.State.FINISHED);

        assertFalse(this.scriptService.cancelCheckAllPages());

        verify(status, never()).cancel();
    }

    @Test
    void cancelCheckAllPagesWhenNeverRun() throws Exception
    {
        assertFalse(this.scriptService.cancelCheckAllPages());
    }

    @Test
    void cancelCheckAllPagesWhenNotAdmin() throws Exception
    {
        CancelableJobStatus status = mockCancelableJob(JobStatus.State.RUNNING);
        doThrow(AccessDeniedException.class).when(this.authorization).checkAccess(Right.ADMIN, WIKI_REFERENCE);

        assertThrows(AccessDeniedException.class, () -> this.scriptService.cancelCheckAllPages());

        verify(status, never()).cancel();
    }

    @Test
    void getCheckAllPagesStatusWhenRunning()
    {
        JobStatus status = mockJob(JobStatus.State.RUNNING);

        assertSame(status, this.scriptService.getCheckAllPagesStatus());
    }

    @Test
    void getCheckAllPagesStatusOfTheLastRun()
    {
        JobStatus status = mock(JobStatus.class);
        when(this.jobStatusStore.getJobStatus(JOB_ID)).thenReturn(status);

        assertSame(status, this.scriptService.getCheckAllPagesStatus());
    }

    @Test
    void getCheckAllPagesStatusWhenNeverRun()
    {
        assertNull(this.scriptService.getCheckAllPagesStatus());
    }

    private CancelableJobStatus mockCancelableJob(JobStatus.State state)
    {
        CancelableJobStatus status = mock(CancelableJobStatus.class);
        when(status.getState()).thenReturn(state);
        when(status.isCancelable()).thenReturn(true);
        Job job = mock(Job.class);
        when(job.getStatus()).thenReturn(status);
        when(this.jobExecutor.getJob(JOB_ID)).thenReturn(job);
        return status;
    }

    private JobStatus mockJob(JobStatus.State state)
    {
        JobStatus status = mock(JobStatus.class);
        when(status.getState()).thenReturn(state);
        Job job = mock(Job.class);
        when(job.getStatus()).thenReturn(status);
        when(this.jobExecutor.getJob(JOB_ID)).thenReturn(job);
        return status;
    }
}
