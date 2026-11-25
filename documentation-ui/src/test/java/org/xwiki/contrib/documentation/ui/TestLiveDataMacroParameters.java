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
package org.xwiki.contrib.documentation.ui;

/**
 * Parameters of {@link TestLiveDataMacro}: the subset of the {@code liveData} macro parameters that the tests check.
 *
 * @version $Id$
 */
public class TestLiveDataMacroParameters
{
    private String id = "";

    private String sort = "";

    /**
     * @return the identifier of the live data
     */
    public String getId()
    {
        return this.id;
    }

    /**
     * @param id the identifier of the live data
     */
    public void setId(String id)
    {
        this.id = id;
    }

    /**
     * @return the properties the live data is sorted on, e.g. {@code doc.date:desc}
     */
    public String getSort()
    {
        return this.sort;
    }

    /**
     * @param sort the properties the live data is sorted on, e.g. {@code doc.date:desc}
     */
    public void setSort(String sort)
    {
        this.sort = sort;
    }
}
