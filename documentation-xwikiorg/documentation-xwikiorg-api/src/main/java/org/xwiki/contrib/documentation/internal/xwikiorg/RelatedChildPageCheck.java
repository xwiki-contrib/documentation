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
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.documentation.DocumentationViolation;
import org.xwiki.contrib.documentation.DocumentationViolationSeverity;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceResolver;
import org.xwiki.model.reference.SpaceReference;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.LinkBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.block.match.ClassBlockMatcher;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.listener.reference.ResourceType;

import com.xpn.xwiki.doc.XWikiDocument;

/**
 * Verify that the Related field of a documentation page does not link to direct child pages of the current page,
 * since child pages are automatically listed in the More section.
 *
 * @version $Id$
 * @since 1.15
 */
@Component
@Singleton
@Named("relatedChildPage")
public class RelatedChildPageCheck extends AbstractXDOMDocumentationCheck
{
    private static final String CHECK_NAME = "Related Child Page";

    private static final String DEFAULT_DOCUMENT_NAME = "WebHome";

    /**
     * The link types that target a page. Links to an attachment of a child page, for example, are not links to the
     * child page itself.
     */
    private static final List<ResourceType> PAGE_RESOURCE_TYPES =
        List.of(ResourceType.DOCUMENT, ResourceType.PAGE, ResourceType.SPACE);

    @Inject
    private EntityReferenceResolver<ResourceReference> resourceReferenceResolver;

    @Override
    public List<DocumentationViolation> check(XWikiDocument document)
    {
        List<DocumentationViolation> violations = new ArrayList<>();
        DocumentReference documentReference = document.getDocumentReference();
        // A terminal page cannot have child pages.
        if (!DEFAULT_DOCUMENT_NAME.equals(documentReference.getName())) {
            return violations;
        }
        XDOM relatedXDOM = parseXPropertyXDOM(document, document.getXDOM(), "related", "Related", CHECK_NAME);
        if (relatedXDOM != null) {
            checkLinks(relatedXDOM, documentReference, violations);
            checkInsideWikiMacros(relatedXDOM, document, null, CHECK_NAME,
                macroXDOM -> checkLinks(macroXDOM, documentReference, violations));
        }
        return violations;
    }

    private void checkLinks(XDOM xdom, DocumentReference documentReference, List<DocumentationViolation> violations)
    {
        List<LinkBlock> linkBlocks = xdom.getBlocks(new ClassBlockMatcher(LinkBlock.class), Block.Axes.DESCENDANT);
        for (LinkBlock linkBlock : linkBlocks) {
            ResourceReference reference = linkBlock.getReference();
            if (!PAGE_RESOURCE_TYPES.contains(reference.getType())) {
                continue;
            }
            EntityReference linkedReference =
                this.resourceReferenceResolver.resolve(reference, EntityType.DOCUMENT, documentReference);
            if (linkedReference != null && documentReference.equals(getParentPage(linkedReference))) {
                violations.add(new DocumentationViolation(
                    "The Related field must not link to child pages of the current page, since child pages are "
                        + "automatically listed in the More section.",
                    String.format("Link reference: [%s]", reference.getReference()),
                    DocumentationViolationSeverity.ERROR));
            }
        }
    }

    /**
     * @return the reference of the parent page of the passed document in the nested page hierarchy (i.e. the home
     *     page of the parent space), or {@code null} if it has none
     */
    private DocumentReference getParentPage(EntityReference reference)
    {
        DocumentReference linkedDocumentReference = new DocumentReference(reference);
        SpaceReference spaceReference = linkedDocumentReference.getLastSpaceReference();
        if (DEFAULT_DOCUMENT_NAME.equals(linkedDocumentReference.getName())) {
            EntityReference parent = spaceReference.getParent();
            return parent instanceof SpaceReference
                ? new DocumentReference(DEFAULT_DOCUMENT_NAME, (SpaceReference) parent) : null;
        }
        return new DocumentReference(DEFAULT_DOCUMENT_NAME, spaceReference);
    }
}
