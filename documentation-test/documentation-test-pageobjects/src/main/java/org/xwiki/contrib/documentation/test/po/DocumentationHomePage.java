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
package org.xwiki.contrib.documentation.test.po;

import java.util.List;

import org.xwiki.livedata.test.po.LiveDataElement;
import org.xwiki.livedata.test.po.TableLayoutElement;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.ui.po.ViewPage;

/**
 * The home page of the Documentation Application, listing the documentation pages, their reviews and their
 * violations.
 *
 * @version $Id$
 */
public class DocumentationHomePage extends ViewPage
{
    private static final DocumentReference REFERENCE = new DocumentReference("xwiki", List.of("DocApp"), "WebHome");

    /**
     * @return the home page of the Documentation Application, once loaded
     */
    public static DocumentationHomePage gotoPage()
    {
        getUtil().gotoPage(REFERENCE);
        return new DocumentationHomePage();
    }

    /**
     * @return the table listing the reported documentation violations, once loaded
     */
    public TableLayoutElement getViolations()
    {
        return getTable("allDeveloperExplanationsPages");
    }

    /**
     * @return the table listing the documentation violations marked as false positives, once loaded
     */
    public TableLayoutElement getFalsePositives()
    {
        return getTable("documentationFalsePositives");
    }

    private TableLayoutElement getTable(String id)
    {
        TableLayoutElement table = new LiveDataElement(id).getTableLayout();
        table.waitUntilReady(false);
        return table;
    }
}
