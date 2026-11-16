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

import org.xwiki.properties.annotation.PropertyDescription;
import org.xwiki.properties.annotation.PropertyMandatory;
import org.xwiki.properties.annotation.PropertyId;

/**
 * Parameters of the {@link DocumentationPropertyMacro}.
 *
 * @version $Id$
 * @since 1.19
 */
public class DocumentationPropertyMacroParameters
{
    private String className;

    private String property;

    /**
     * @return the reference of the class of the object holding the property to display, relative to the current
     *     wiki, for example {@code DocApp.Code.RenderingMacroClass}
     */
    public String getClassName()
    {
        return this.className;
    }

    /**
     * @param className the reference of the class of the object holding the property to display, relative to the
     *     current wiki, for example {@code DocApp.Code.RenderingMacroClass}
     */
    @PropertyMandatory
    @PropertyId("class")
    @PropertyDescription("The class of the object of the current page holding the property to display, which must be"
        + " a class of the documentation application (in the DocApp.Code space), for example"
        + " DocApp.Code.RenderingMacroClass.")
    public void setClassName(String className)
    {
        this.className = className;
    }

    /**
     * @return the name of the property to display, for example {@code examples}
     */
    public String getProperty()
    {
        return this.property;
    }

    /**
     * @param property the name of the property to display, for example {@code examples}
     */
    @PropertyMandatory
    @PropertyDescription("The name of the property to display, which must be a text area holding wiki content, for "
        + "example examples.")
    public void setProperty(String property)
    {
        this.property = property;
    }
}
