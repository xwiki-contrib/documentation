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

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Provider;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.annotation.InstantiationStrategy;
import org.xwiki.component.descriptor.ComponentInstantiationStrategy;
import org.xwiki.contrib.documentation.DocumentationManager;
import org.xwiki.job.AbstractJob;
import org.xwiki.job.DefaultRequest;
import org.xwiki.job.GroupedJob;
import org.xwiki.job.Job;
import org.xwiki.job.JobGroupPath;
import org.xwiki.job.event.status.JobStatus;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.query.Query;
import org.xwiki.query.QueryManager;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;

/**
 * Performs the documentation analysis of all the documentation pages of a wiki, so that their violations are up to
 * date even when they haven't been saved since the checks changed.
 *
 * @version $Id$
 * @since 1.16
 */
@Component
@Named(CheckAllPagesJob.JOB_TYPE)
@InstantiationStrategy(ComponentInstantiationStrategy.PER_LOOKUP)
public class CheckAllPagesJob extends AbstractJob<DefaultRequest, CheckAllPagesJobStatus>
    implements GroupedJob
{
    /**
     * The job type.
     */
    public static final String JOB_TYPE = "documentation.checkAllPages";

    private static final String QUERY = "select distinct doc.fullName from XWikiDocument doc, BaseObject obj "
        + "where doc.fullName = obj.name and obj.className = :className and doc.translation = 0 "
        + "order by doc.fullName";

    @Inject
    private QueryManager queryManager;

    @Inject
    @Named("current")
    private DocumentReferenceResolver<String> resolver;

    @Inject
    @Named("local")
    private EntityReferenceSerializer<String> localSerializer;

    @Inject
    private DocumentationManager manager;

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    /**
     * @param wiki the identifier of the wiki whose documentation pages to check
     * @return the identifier of the job checking all the documentation pages of the passed wiki, which is also its
     *     group path, so that two checks of the same wiki never run at the same time
     */
    public static List<String> getJobId(String wiki)
    {
        return List.of("documentation", "checkAllPages", wiki);
    }

    @Override
    public String getType()
    {
        return JOB_TYPE;
    }

    @Override
    public JobGroupPath getGroupPath()
    {
        return new JobGroupPath(getRequest().getId());
    }

    @Override
    protected CheckAllPagesJobStatus createNewStatus(DefaultRequest request)
    {
        Job currentJob = this.jobContext.getCurrentJob();
        JobStatus parentJobStatus = currentJob != null ? currentJob.getStatus() : null;
        return new CheckAllPagesJobStatus(request, parentJobStatus, this.observationManager, this.loggerManager);
    }

    @Override
    protected void runInternal() throws Exception
    {
        XWikiContext xcontext = this.xcontextProvider.get();
        WikiReference wikiReference = xcontext.getWikiReference();
        String className = this.localSerializer.serialize(DocumentationPages.DOCUMENTATION_CLASS_REFERENCE);
        List<String> pageNames = this.queryManager.createQuery(QUERY, Query.HQL)
            .bindValue("className", className)
            .setWiki(wikiReference.getName())
            .execute();

        this.progressManager.pushLevelProgress(pageNames.size(), this);
        try {
            for (String pageName : pageNames) {
                if (this.status.isCanceled()) {
                    this.logger.info("Canceled the check of the documentation pages.");
                    break;
                }
                this.progressManager.startStep(this);
                DocumentReference reference = this.resolver.resolve(pageName, wikiReference);
                try {
                    // Clone the document since the analysis modifies it, and the document returned by getDocument()
                    // is shared.
                    XWikiDocument document = xcontext.getWiki().getDocument(reference, xcontext).clone();
                    if (DocumentationPages.isAnalysable(document)) {
                        this.status.incrementCheckedCount();
                        if (this.manager.analyse(document)) {
                            this.status.incrementUpdatedCount();
                            this.logger.info("Updated the violations of [{}].", reference);
                        }
                    }
                } catch (Exception e) {
                    // Keep checking the other pages: one page failing to be checked shouldn't prevent the others from
                    // being checked.
                    this.status.incrementFailedCount();
                    this.logger.error("Failed to check [{}].", reference, e);
                }
                this.progressManager.endStep(this);
            }
        } finally {
            this.progressManager.popLevelProgress(this);
        }

        this.logger.info("Checked [{}] documentation pages: [{}] updated, [{}] failed.", this.status.getCheckedCount(),
            this.status.getUpdatedCount(), this.status.getFailedCount());
    }
}
