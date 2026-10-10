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

import org.xwiki.model.EntityType;
import org.xwiki.model.reference.LocalDocumentReference;

import com.xpn.xwiki.doc.XWikiDocument;

/**
 * Decides which pages are documentation pages to analyse, so that the analysis done on save and the analysis of all
 * pages select the same pages.
 *
 * @version $Id$
 * @since 1.16
 */
public final class DocumentationPages
{
    /**
     * The space holding the application pages.
     */
    public static final String APPLICATION_SPACE = "DocApp";

    private static final List<String> CODE_SPACES = List.of(APPLICATION_SPACE, "Code");

    /**
     * The xclass marking a page as a documentation page.
     */
    public static final LocalDocumentReference DOCUMENTATION_CLASS_REFERENCE =
        new LocalDocumentReference(CODE_SPACES, "DocumentationClass");

    /**
     * The xclass of the violations stored in a documentation page.
     */
    public static final LocalDocumentReference VIOLATION_CLASS_REFERENCE =
        new LocalDocumentReference(CODE_SPACES, "DocumentationViolationClass");

    private DocumentationPages()
    {
        // Utility class.
    }

    /**
     * @param document the document to test
     * @return true if the document is a documentation page to analyse: it holds a {@code DocumentationClass} xobject
     *     and it's not located in the application space (which holds the documentation templates, that mustn't get
     *     violation xobjects)
     */
    public static boolean isAnalysable(XWikiDocument document)
    {
        return document.getXObject(DOCUMENTATION_CLASS_REFERENCE) != null
            && !APPLICATION_SPACE.equals(
                document.getDocumentReference().extractFirstReference(EntityType.SPACE).getName());
    }
}
