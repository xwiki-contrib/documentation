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

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.transformation.MacroTransformationContext;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

/**
 * Displays the FAQ of a documentation page (the {@code faq} property of its {@code DocApp.Code.DocumentationClass}
 * object) as blocks, for {@link DocumentationContentMacro}.
 *
 * @version $Id$
 * @since 1.16
 */
@Component(roles = DocumentationFAQDisplayer.class)
@Singleton
public class DocumentationFAQDisplayer
{
    private static final LocalDocumentReference DOCUMENTATION_CLASS =
        new LocalDocumentReference(List.of("DocApp", "Code"), "DocumentationClass");

    @Inject
    private DocumentationPropertyDisplayer propertyDisplayer;

    /**
     * Displays the FAQ of the given document, executed with the same rights and restrictions as
     * {@code $doc.display('faq')} executes it.
     *
     * @param document the document whose FAQ to display
     * @param context the context of the macro displaying the FAQ
     * @return the executed FAQ, empty if the document has no FAQ
     * @throws MacroExecutionException if the FAQ cannot be displayed
     */
    public List<Block> display(XWikiDocument document, MacroTransformationContext context)
        throws MacroExecutionException
    {
        BaseObject object = document.getXObject(DOCUMENTATION_CLASS);
        if (object == null) {
            return List.of();
        }
        return this.propertyDisplayer.display(document, object, "faq", "FAQ", context);
    }
}
