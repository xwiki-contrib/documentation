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
package org.xwiki.contrib.documentation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.xwiki.model.reference.DocumentReference;

/**
 * The violations of a same rule, i.e. reported by a same check with a same message, that haven't been marked as false
 * positives, along with the pages holding them.
 *
 * @version $Id$
 * @since 1.16
 */
public class DocumentationViolationGroup
{
    private final String check;

    private final String message;

    private final String severity;

    private final List<DocumentReference> pages = new ArrayList<>();

    private int violationCount;

    /**
     * @param check see {@link #getCheck()}
     * @param message see {@link #getMessage()}
     * @param severity see {@link #getSeverity()}
     */
    public DocumentationViolationGroup(String check, String message, String severity)
    {
        this.check = check;
        this.message = message;
        this.severity = severity;
    }

    /**
     * Adds a violation to the group.
     *
     * @param page the page holding the violation
     */
    public void addViolation(DocumentReference page)
    {
        this.violationCount++;
        if (!this.pages.contains(page)) {
            this.pages.add(page);
        }
    }

    /**
     * @return the hint of the check that reported the violations, empty for violations stored before the check was
     *     recorded
     */
    public String getCheck()
    {
        return this.check;
    }

    /**
     * @return the message of the violations, which tells the rule they break
     */
    public String getMessage()
    {
        return this.message;
    }

    /**
     * @return the severity of the violations ({@code Error} or {@code Warning})
     */
    public String getSeverity()
    {
        return this.severity;
    }

    /**
     * @return the pages holding the violations, in the order they were found
     */
    public List<DocumentReference> getPages()
    {
        return Collections.unmodifiableList(this.pages);
    }

    /**
     * @return the number of violations, a page holding several violations of the same rule being counted for each
     */
    public int getViolationCount()
    {
        return this.violationCount;
    }
}
