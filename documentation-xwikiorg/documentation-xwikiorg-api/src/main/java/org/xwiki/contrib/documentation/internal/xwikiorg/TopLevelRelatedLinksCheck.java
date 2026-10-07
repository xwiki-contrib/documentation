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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.documentation.DocumentationViolation;
import org.xwiki.contrib.documentation.DocumentationViolationSeverity;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.SpaceReference;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.LinkBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.block.match.ClassBlockMatcher;
import org.xwiki.rendering.listener.reference.ResourceReference;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

/**
 * Verify that the Related field of a top-level documentation page (see {@link TopLevelPageCheck}) links to the
 * top-level pages covering the same topic for the other target audiences, when they exist. The page covering the same
 * topic for another audience is the top-level page with the same name, in the same section, under that audience.
 *
 * @version $Id$
 * @since 1.15
 */
@Component
@Singleton
@Named("topLevelRelatedLinks")
public class TopLevelRelatedLinksCheck extends AbstractXDOMDocumentationCheck
{
    private static final String CHECK_NAME = "Top-Level Related Links";

    private static final String RELATED_PROPERTY = "related";

    @Inject
    private EntityReferenceResolver<ResourceReference> resourceReferenceResolver;

    @Inject
    @Named("local")
    private EntityReferenceSerializer<String> localSerializer;

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Override
    public List<DocumentationViolation> check(XWikiDocument document)
    {
        DocumentReference documentReference = document.getDocumentReference();
        if (!TopLevelPageCheck.isTopLevelPage(documentReference)) {
            return List.of();
        }

        List<DocumentReference> counterparts = getExistingCounterparts(documentReference);
        if (counterparts.isEmpty()) {
            return List.of();
        }

        Set<DocumentReference> linkedReferences = new HashSet<>();
        BaseObject docObject = document.getXObject(DOCUMENTATION_CLASS_REFERENCE);
        if (docObject != null && StringUtils.isNotBlank(docObject.getLargeStringValue(RELATED_PROPERTY))) {
            XDOM relatedXDOM =
                parseXPropertyXDOM(document, document.getXDOM(), RELATED_PROPERTY, "Related", CHECK_NAME);
            if (relatedXDOM == null) {
                // The Related field could not be parsed (a warning has been logged): its links are unknown.
                return List.of();
            }
            collectLinkedReferences(relatedXDOM, documentReference, linkedReferences);
            checkInsideWikiMacros(relatedXDOM, document, null, CHECK_NAME,
                macroXDOM -> collectLinkedReferences(macroXDOM, documentReference, linkedReferences));
        }

        return counterparts.stream()
            .filter(counterpart -> !linkedReferences.contains(counterpart))
            .map(counterpart -> new DocumentationViolation(
                "The Related field of a top-level page must link to the top-level pages covering the same "
                    + "topic for the other target audiences.",
                String.format("Missing link: [%s]", this.localSerializer.serialize(counterpart)),
                DocumentationViolationSeverity.ERROR))
            .toList();
    }

    /**
     * @return the existing top-level pages with the same name, in the same section, under the other audiences
     */
    private List<DocumentReference> getExistingCounterparts(DocumentReference documentReference)
    {
        SpaceReference topicSpace = documentReference.getLastSpaceReference();
        SpaceReference audienceSpace = (SpaceReference) topicSpace.getParent();
        EntityReference sectionSpace = audienceSpace.getParent();

        XWikiContext xcontext = this.xcontextProvider.get();
        List<DocumentReference> counterparts = new ArrayList<>();
        for (String audience : TopLevelPageCheck.AUDIENCES) {
            if (!audience.equals(audienceSpace.getName())) {
                DocumentReference counterpart = new DocumentReference(documentReference.getName(),
                    new SpaceReference(topicSpace.getName(), new SpaceReference(audience, sectionSpace)));
                if (exists(counterpart, xcontext)) {
                    counterparts.add(counterpart);
                }
            }
        }
        return counterparts;
    }

    private boolean exists(DocumentReference reference, XWikiContext xcontext)
    {
        try {
            return xcontext.getWiki().exists(reference, xcontext);
        } catch (XWikiException e) {
            this.logger.warn("Failed to check if the page [{}] exists. Ignoring it in the {} check. "
                + ROOT_ERROR_CAUSE, reference, CHECK_NAME, ExceptionUtils.getRootCauseMessage(e));
            return false;
        }
    }

    private void collectLinkedReferences(XDOM xdom, DocumentReference documentReference,
        Set<DocumentReference> linkedReferences)
    {
        List<LinkBlock> linkBlocks = xdom.getBlocks(new ClassBlockMatcher(LinkBlock.class), Block.Axes.DESCENDANT);
        for (LinkBlock linkBlock : linkBlocks) {
            ResourceReference reference = linkBlock.getReference();
            if (RelatedChildPageCheck.PAGE_RESOURCE_TYPES.contains(reference.getType())) {
                EntityReference linkedReference =
                    this.resourceReferenceResolver.resolve(reference, EntityType.DOCUMENT, documentReference);
                if (linkedReference != null) {
                    linkedReferences.add(new DocumentReference(linkedReference));
                }
            }
        }
    }
}
