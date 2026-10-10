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

/**
 * Parameters of the {@link DocumentationContentMacro}.
 *
 * @version $Id$
 * @since 1.16
 */
public class DocumentationContentMacroParameters
{
    /**
     * The documentation fields that the macro can display.
     */
    public enum Field
    {
        /**
         * The content of the current document.
         */
        CONTENT,

        /**
         * The {@code faq} property of the {@code DocApp.Code.DocumentationClass} object of the current document.
         */
        FAQ
    }

    private Field field;

    /**
     * @return the documentation field to display
     */
    public Field getField()
    {
        return this.field;
    }

    /**
     * @param field the documentation field to display
     */
    @PropertyMandatory
    @PropertyDescription("The documentation field of the current page to display (content or faq).")
    public void setField(Field field)
    {
        this.field = field;
    }
}
