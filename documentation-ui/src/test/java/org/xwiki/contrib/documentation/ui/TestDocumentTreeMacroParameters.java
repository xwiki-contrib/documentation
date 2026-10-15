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
 * Parameters of {@link TestDocumentTreeMacro}: the subset of the {@code documentTree} macro parameters that the
 * tests check.
 *
 * @version $Id$
 */
public class TestDocumentTreeMacroParameters
{
    private String root = "";

    private String openTo = "";

    private String exclusions = "";

    /**
     * @return the root of the tree
     */
    public String getRoot()
    {
        return this.root;
    }

    /**
     * @param root the root of the tree
     */
    public void setRoot(String root)
    {
        this.root = root;
    }

    /**
     * @return the node to open the tree to
     */
    public String getOpenTo()
    {
        return this.openTo;
    }

    /**
     * @param openTo the node to open the tree to
     */
    public void setOpenTo(String openTo)
    {
        this.openTo = openTo;
    }

    /**
     * @return the nodes excluded from the tree
     */
    public String getExclusions()
    {
        return this.exclusions;
    }

    /**
     * @param exclusions the nodes excluded from the tree
     */
    public void setExclusions(String exclusions)
    {
        this.exclusions = exclusions;
    }
}
