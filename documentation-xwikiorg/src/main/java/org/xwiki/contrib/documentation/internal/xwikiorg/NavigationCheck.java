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
package org.xwiki.contrib.documentation.internal.xwikiorg;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.documentation.DocumentationCheck;
import org.xwiki.contrib.documentation.DocumentationException;
import org.xwiki.contrib.documentation.DocumentationViolation;
import org.xwiki.contrib.documentation.DocumentationViolationSeverity;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.LocalDocumentReference;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

/**
 * Verify that documentation pages are listed in the documentation navigation panels.
 *
 * @version $Id$
 * @since 1.0
 */
@Component
@Singleton
@Named("navigation")
public class NavigationCheck implements DocumentationCheck
{
    private static final String TOPLEVEL_SPACE = "Documentation";

    private static final LocalDocumentReference DOCUMENTATION_CLASS_REFERENCE =
        new LocalDocumentReference(List.of(TOPLEVEL_SPACE, "Code"), "DocumentationClass");

    @Inject
    private Logger logger;

    @Inject
    @Named("local")
    private EntityReferenceSerializer<String> serializer;

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Override
    public List<DocumentationViolation> check(XWikiDocument document) throws DocumentationException
    {
        List<DocumentationViolation> violations = new ArrayList<>();

        BaseObject object = document.getXObject(DOCUMENTATION_CLASS_REFERENCE);
        if (object != null) {
            // Find the navigation page depending on the target panel
            String target = object.getStringValue("target");
            DocumentReference navigationReference = new DocumentReference(
                document.getDocumentReference().getWikiReference().getName(),
                List.of(TOPLEVEL_SPACE, "Data"),
                String.format("NavigationFor%ss", StringUtils.capitalize(target)));

            // Get the content of the navigation page
            XWikiDocument navigationDocument = getNavigationDocument(navigationReference);

            // Check if the current page reference exists in the content
            String referenceString = this.serializer.serialize(document.getDocumentReference());
            if (!navigationDocument.getContent().contains(referenceString)) {
                violations.add(new DocumentationViolation(String.format(
                    "The current page must be listed in the navigation. Please edit [%s] to add it.",
                        this.serializer.serialize(navigationReference)), "", DocumentationViolationSeverity.ERROR));
            }
        }

        return violations;
    }

    private XWikiDocument getNavigationDocument(DocumentReference navigationReference)
        throws DocumentationException
    {
        XWikiDocument navigationDocument;
        try {
            XWikiContext xcontext = this.xcontextProvider.get();
            navigationDocument = xcontext.getWiki().getDocument(navigationReference, xcontext);
        } catch (Exception e) {
            throw new DocumentationException(String.format("Failed to retrieve the navigation document at [%s].",
                navigationReference), e);
        }

        return navigationDocument;
    }
}
