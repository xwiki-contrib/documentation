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

/**
 * A single documentation violation (message and context).
 *
 * @version $Id$
 */
public class DocumentationViolation
{
    private String violationMessage;

    private String violationContext;

    /**
     * @param violationMessage see {@link #getViolationMessage()}
     * @param violationContext  see {@link #getViolationContext()}
     */
    public DocumentationViolation(String violationMessage, String violationContext)
    {
        this.violationContext = violationContext;
        this.violationMessage = violationMessage;
    }

    /**
     * @return the violation message
     */
    public String getViolationMessage()
    {
        return this.violationMessage;
    }

    /**
     * @return the source of the violation and any additional information helping the reader understand where the
     *         violation is located
     */
    public String getViolationContext()
    {
        return this.violationContext;
    }
}
