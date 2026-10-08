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

import org.xwiki.job.DefaultJobStatus;
import org.xwiki.job.DefaultRequest;
import org.xwiki.job.event.status.JobStatus;
import org.xwiki.logging.LoggerManager;
import org.xwiki.observation.ObservationManager;

/**
 * The status of the analysis of all the documentation pages of a wiki, counting the pages it checked, updated and
 * failed to check, so that they can be displayed once the analysis is done.
 *
 * @version $Id$
 * @since 1.16
 */
public class CheckAllPagesJobStatus extends DefaultJobStatus<DefaultRequest>
{
    private int checkedCount;

    private int updatedCount;

    private int failedCount;

    /**
     * @param request the request provided when the job was started
     * @param parentJobStatus the status of the parent job, if any
     * @param observationManager the observation manager component
     * @param loggerManager the logger manager component
     */
    public CheckAllPagesJobStatus(DefaultRequest request, JobStatus parentJobStatus,
        ObservationManager observationManager, LoggerManager loggerManager)
    {
        super(CheckAllPagesJob.JOB_TYPE, request, parentJobStatus, observationManager, loggerManager);
        setCancelable(true);
    }

    /**
     * @return the number of documentation pages checked, including the ones that failed to be checked
     */
    public int getCheckedCount()
    {
        return this.checkedCount;
    }

    /**
     * @return the number of documentation pages whose violations changed, and that got saved
     */
    public int getUpdatedCount()
    {
        return this.updatedCount;
    }

    /**
     * @return the number of documentation pages that failed to be checked
     */
    public int getFailedCount()
    {
        return this.failedCount;
    }

    void incrementCheckedCount()
    {
        this.checkedCount++;
    }

    void incrementUpdatedCount()
    {
        this.updatedCount++;
    }

    void incrementFailedCount()
    {
        this.failedCount++;
    }
}
