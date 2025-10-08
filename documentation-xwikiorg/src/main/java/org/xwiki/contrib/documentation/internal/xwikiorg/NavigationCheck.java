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
import org.xwiki.model.reference.SpaceReference;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

/**
 * Verify that documentation pages are listed in the documentation navigation panels. The rules used are:
 * <ul>
 *   <li>For pages located under {@code ?.XS.*}:</li>
 *   <ul>
 *     <li>For users: check for an entry in {@code DocApp.Data.NavigationXSForUsers}</li>
 *     <li>For administrators: check for an entry in {@code DocApp.Data.NavigationXSForAdministrators}</li>
 *     <li>For developers: check for an entry in {@code DocApp.Data.NavigationXSForDevelopers}</li>
 *   </ul>
 *   <li>For pages located under {@code ?.Extensions.*}:</li>
 *   <ul>
 *     <li>For users: check for an entry in {@code DocApp.Data.NavigationExtensionsForUsers}</li>
 *     <li>For administrators: check for an entry in {@code DocApp.Data.NavigationExtensionsForAdministrators}</li>
 *     <li>For developers: check for an entry in {@code DocApp.Data.NavigationExtensionsForDevelopers}</li>
 *   </ul>
 * </ul>
 *
 * @version $Id$
 * @since 1.0
 */
@Component
@Singleton
@Named("navigation")
public class NavigationCheck implements DocumentationCheck
{
    private static final String TOPLEVEL_SPACE = "DocApp";

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
            // Find the navigation page depending on the target and on the product being documented.
            // The product is derived from the document reference:
            // - ?.XS.* -> XS
            // - ?.Extensions.* -> Extensions
            String target = object.getStringValue("target");
            String product = extractProduct(document.getDocumentReference(), violations);
            if (product != null) {
                DocumentReference navigationReference = new DocumentReference(
                    document.getDocumentReference().getWikiReference().getName(),
                    List.of(TOPLEVEL_SPACE, "Data"),
                    String.format("Navigation%sFor%ss", StringUtils.capitalize(product),
                        StringUtils.capitalize(target)));

                // Get the content of the navigation page
                XWikiDocument navigationDocument = getNavigationDocument(navigationReference);

                // Check if the current page reference exists in the content
                String referenceString = this.serializer.serialize(document.getDocumentReference());
                if (!navigationDocument.getContent().contains(referenceString)) {
                    violations.add(new DocumentationViolation(String.format("The current page must be listed in the "
                            + "navigation. Please edit [%s] to add it, and add a link to [%s]",
                        this.serializer.serialize(navigationReference), referenceString), "",
                        DocumentationViolationSeverity.ERROR));
                }
            }
        }

        return violations;
    }

    private String extractProduct(DocumentReference documentReference, List<DocumentationViolation> violations)
    {
        // Extract the second space name to find the product.
        List<SpaceReference> spaces = documentReference.getSpaceReferences();
        // If we have less than 3 spaces, then it means we don't have a reference hierarchy that has the product
        // in the name.
        // Example 1 (Valid): Documentation/XS/DocPage1/WebHome -> 3 spaces
        // Example 2 (Invalid): Documentation/DocPage1/WebHome -> 2 spaces
        // Example 3 (Invalid too): SomeSpace/Documentation/DocPage1/WebHome -> 3 spaces but product is not at the
        // expected location and is not matching "XS" or "Extensions".
        boolean isError = false;
        String product = null;
        if (spaces.size() < 3) {
            isError = true;
        } else {
            product = spaces.get(1).getName();
            if (!"XS".equals(product) && !"Extensions".equals(product)) {
                isError = true;
            }
        }
        if (isError) {
            violations.add(new DocumentationViolation(String.format("The current page must be located at a "
                + "reference matching '?.(XS|Extensions).*'. Got [%s]", documentReference),
                "",
                DocumentationViolationSeverity.ERROR));
        }
        return product;
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
