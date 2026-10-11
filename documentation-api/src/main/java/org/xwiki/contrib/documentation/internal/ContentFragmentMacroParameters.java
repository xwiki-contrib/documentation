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

import org.xwiki.model.reference.DocumentReference;
import org.xwiki.properties.annotation.PropertyDescription;
import org.xwiki.properties.annotation.PropertyDisplayType;
import org.xwiki.properties.annotation.PropertyMandatory;

/**
 * Parameters of the {@link ContentFragmentMacro}.
 *
 * @version $Id$
 * @since 1.18
 */
public class ContentFragmentMacroParameters
{
    private String name;

    private String reference;

    /**
     * @return the name of the content fragment to display
     */
    public String getName()
    {
        return this.name;
    }

    /**
     * @param name the name of the content fragment to display
     */
    @PropertyMandatory
    @PropertyDescription("The name of the content fragment to display.")
    public void setName(String name)
    {
        this.name = name;
    }

    /**
     * @return the reference of the page holding the content fragment, empty for the current page
     */
    public String getReference()
    {
        return this.reference;
    }

    /**
     * @param reference the reference of the page holding the content fragment, empty for the current page
     */
    @PropertyDescription("The page holding the content fragment. Leave empty for the current page.")
    @PropertyDisplayType(DocumentReference.class)
    public void setReference(String reference)
    {
        this.reference = reference;
    }
}
