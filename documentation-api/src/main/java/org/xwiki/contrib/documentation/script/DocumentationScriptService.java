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

import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.context.concurrent.ContextStoreManager;
import org.xwiki.contrib.documentation.DocumentationException;
import org.xwiki.contrib.documentation.DocumentationManager;
import org.xwiki.contrib.documentation.DocumentationViolationGroup;
import org.xwiki.contrib.documentation.internal.CheckAllPagesJob;
import org.xwiki.contrib.documentation.internal.FalsePositives;
import org.xwiki.index.IndexException;
import org.xwiki.job.DefaultRequest;
import org.xwiki.job.Job;
import org.xwiki.job.JobException;
import org.xwiki.job.JobExecutor;
import org.xwiki.job.JobStatusStore;
import org.xwiki.job.event.status.CancelableJobStatus;
import org.xwiki.job.event.status.JobStatus;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.script.service.ScriptService;
import org.xwiki.security.authorization.AccessDeniedException;
import org.xwiki.security.authorization.ContextualAuthorizationManager;
import org.xwiki.security.authorization.Right;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;

/**
 * Scripting APIs for Documentation analysis.
 *
 * @version $Id$
 * @since 1.0
 */
@Component
@Singleton
@Named("documentation")
public class DocumentationScriptService implements ScriptService
{
    /**
     * The parts of the context that the analysis of all pages runs with: the wiki and the user, but also the locale and
     * the request URL, used by the checks that render or link to content.
     */
    private static final List<String> JOB_CONTEXT_ENTRIES = List.of("wiki", "user", "author", "locale",
        "request.base", "request.url", "request.contextpath", "request.wiki");

    @Inject
    private DocumentationManager manager;

    @Inject
    private ContextualAuthorizationManager authorization;

    @Inject
    private JobExecutor jobExecutor;

    @Inject
    private JobStatusStore jobStatusStore;

    @Inject
    private ContextStoreManager contextStore;

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Inject
    private FalsePositives falsePositives;



    /**
     * @param document the document on which to perform the documentation analysis
     * @return true if the violations of the document changed, in which case the document has been saved
     * @throws IndexException if an error occurs while indexing the document when it's executing synchronously
     */
    public boolean analyse(XWikiDocument document) throws IndexException
    {
        return this.manager.analyse(document);
    }

    /**
     * @return true if the current user can check all the documentation pages of the current wiki, i.e. has the Admin
     *     right on it
     * @since 1.16
     */
    public boolean canCheckAllPages()
    {
        return this.authorization.hasAccess(Right.ADMIN, getCurrentWikiReference());
    }

    /**
     * Starts the analysis of all the documentation pages of the current wiki, in the background. The analysis saves
     * each page whose violations changed, which is why it's restricted to the wiki administrators.
     *
     * @return the status of the analysis: of the one already running for the current wiki if there's one, otherwise
     *     of the one just started
     * @throws AccessDeniedException if the current user doesn't have the Admin right on the current wiki
     * @throws JobException if the analysis fails to start
     * @since 1.16
     */
    public JobStatus checkAllPages() throws AccessDeniedException, JobException
    {
        WikiReference wikiReference = getCurrentWikiReference();
        this.authorization.checkAccess(Right.ADMIN, wikiReference);

        List<String> jobId = CheckAllPagesJob.getJobId(wikiReference.getName());
        Job job = this.jobExecutor.getJob(jobId);
        if (job == null || job.getStatus().getState() == JobStatus.State.FINISHED) {
            DefaultRequest request = new DefaultRequest();
            request.setId(jobId);
            try {
                request.setContext(this.contextStore.save(JOB_CONTEXT_ENTRIES));
            } catch (ComponentLookupException e) {
                throw new JobException("Failed to save the context of the analysis of all documentation pages", e);
            }
            job = this.jobExecutor.execute(CheckAllPagesJob.JOB_TYPE, request);
        }
        return job.getStatus();
    }

    /**
     * Cancels the analysis of all the documentation pages of the current wiki, if one is running. The pages already
     * checked keep their updated violations.
     *
     * @return true if an analysis was running and has been asked to stop, false if there was none to cancel
     * @throws AccessDeniedException if the current user doesn't have the Admin right on the current wiki
     * @since 1.16
     */
    public boolean cancelCheckAllPages() throws AccessDeniedException
    {
        WikiReference wikiReference = getCurrentWikiReference();
        this.authorization.checkAccess(Right.ADMIN, wikiReference);

        Job job = this.jobExecutor.getJob(CheckAllPagesJob.getJobId(wikiReference.getName()));
        if (job != null && job.getStatus() instanceof CancelableJobStatus status && status.isCancelable()
            && status.getState() != JobStatus.State.FINISHED)
        {
            status.cancel();
            return true;
        }
        return false;
    }

    /**
     * @return the status of the analysis of all the documentation pages of the current wiki: of the one running if
     *     there's one, otherwise of the last one, or {@code null} if there was none
     * @since 1.16
     */
    public JobStatus getCheckAllPagesStatus()
    {
        List<String> jobId = CheckAllPagesJob.getJobId(getCurrentWikiReference().getName());
        Job job = this.jobExecutor.getJob(jobId);
        return job != null ? job.getStatus() : this.jobStatusStore.getJobStatus(jobId);
    }

    /**
     * @return true if the current user can mark the violations of the documentation pages of the current wiki as false
     *     positives, i.e. has the Admin right on it
     * @since 1.16
     */
    public boolean canMarkFalsePositives()
    {
        return this.authorization.hasAccess(Right.ADMIN, getCurrentWikiReference());
    }

    /**
     * Marks a violation of a documentation page as a false positive, so that it's not reported anymore, for as long as
     * the check reports it.
     *
     * @param reference the documentation page holding the violation
     * @param number the number of the violation xobject
     * @param reason why the violation is a false positive, optional
     * @return true if the violation has been marked, false if there's no such violation or if it's already marked
     * @throws AccessDeniedException if the current user doesn't have the Admin right on the wiki of the page
     * @throws DocumentationException if the page fails to be loaded or saved
     * @since 1.16
     */
    public boolean markFalsePositive(DocumentReference reference, int number, String reason)
        throws AccessDeniedException, DocumentationException
    {
        this.authorization.checkAccess(Right.ADMIN, reference.getWikiReference());
        return this.falsePositives.mark(reference, number, reason);
    }

    /**
     * Reinstates a violation of a documentation page that was marked as a false positive, so that it's reported
     * again.
     *
     * @param reference the documentation page holding the violation
     * @param number the number of the violation xobject
     * @return true if the violation has been reinstated, false if there's no such violation or if it isn't marked
     * @throws AccessDeniedException if the current user doesn't have the Admin right on the wiki of the page
     * @throws DocumentationException if the page fails to be loaded or saved
     * @since 1.16
     */
    public boolean unmarkFalsePositive(DocumentReference reference, int number)
        throws AccessDeniedException, DocumentationException
    {
        this.authorization.checkAccess(Right.ADMIN, reference.getWikiReference());
        return this.falsePositives.unmark(reference, number);
    }

    /**
     * @param page a documentation page
     * @return the pages in which the violations of a rule reported in the passed page can be marked as false positives
     *     along with the violations of the pages located under them (see
     *     {@link #markFalsePositives(String, String, DocumentReference, String)}): the passed page when it's a nested
     *     page, then the nested pages it's located under, the closest first
     * @since 1.16
     */
    public List<DocumentReference> getFalsePositiveScopes(DocumentReference page)
    {
        return this.falsePositives.getScopes(page);
    }

    /**
     * Groups the violations not marked as false positives of a documentation page and, when it's a nested page, of the
     * pages located under it, by rule, i.e. by check and message, so that the violations of a rule that doesn't apply
     * to a whole part of the documentation can be marked at once with
     * {@link #markFalsePositives(String, String, DocumentReference, String)}.
     *
     * @param page the page whose violations to group, along with the violations of the pages located under it when
     *     it's a nested page
     * @return the groups of violations, the ones with the most violations first
     * @throws AccessDeniedException if the current user doesn't have the Admin right on the wiki of the page
     * @throws DocumentationException if the pages fail to be found or loaded
     * @since 1.16
     */
    public List<DocumentationViolationGroup> getViolationGroups(DocumentReference page)
        throws AccessDeniedException, DocumentationException
    {
        this.authorization.checkAccess(Right.ADMIN, page.getWikiReference());
        return this.falsePositives.getGroups(page);
    }

    /**
     * Marks as false positives the violations of a rule, i.e. reported by a check with a message, in a documentation
     * page and, when it's a nested page, in the pages located under it. Useful when a rule doesn't apply to a whole
     * part of the documentation.
     *
     * @param check the hint of the check that reported the violations to mark (see
     *     {@link DocumentationViolationGroup#getCheck()})
     * @param message the message of the violations to mark
     * @param page the page whose violations to mark, along with the violations of the pages located under it when it's
     *     a nested page
     * @param reason why the violations are false positives, optional
     * @return the number of violations marked
     * @throws AccessDeniedException if the current user doesn't have the Admin right on the wiki of the page
     * @throws DocumentationException if the pages fail to be found, loaded or saved
     * @throws IllegalArgumentException if the check, the message or the page is missing
     * @since 1.16
     */
    public int markFalsePositives(String check, String message, DocumentReference page, String reason)
        throws AccessDeniedException, DocumentationException
    {
        if (check == null || StringUtils.isBlank(message) || page == null) {
            throw new IllegalArgumentException(
                "The check, the message and the page of the violations to mark are needed");
        }
        this.authorization.checkAccess(Right.ADMIN, page.getWikiReference());
        return this.falsePositives.markAll(check, message, page, reason);
    }

    private WikiReference getCurrentWikiReference()
    {
        return this.xcontextProvider.get().getWikiReference();
    }
}
