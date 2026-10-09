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
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.documentation.DocumentationViolation;
import org.xwiki.contrib.documentation.DocumentationViolationSeverity;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceResolver;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.LinkBlock;
import org.xwiki.rendering.block.SpaceBlock;
import org.xwiki.rendering.block.SpecialSymbolBlock;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.block.match.ClassBlockMatcher;
import org.xwiki.rendering.listener.reference.ResourceReference;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

/**
 * Verify that the labels of the links of the Related field that hold a target qualifier follow the
 * {@code <exact page title> (for <target>)} format, where the title is the exact title of the linked page and the
 * target is the audience of the linked page (User, Administrator or Developer). When the title of the linked page
 * already ends with its target qualifier, the label is that title.
 *
 * @version $Id$
 * @since 1.15
 */
@Component
@Singleton
@Named("relatedLinkLabel")
public class RelatedLinkLabelCheck extends AbstractXDOMDocumentationCheck
{
    private static final String CHECK_NAME = "Related Link Label";

    private static final String TARGET_PREFIX = "(for ";

    private static final Map<String, String> TARGETS =
        Map.of("user", "User", "administrator", "Administrator", "developer", "Developer");

    @Inject
    private EntityReferenceResolver<ResourceReference> resourceReferenceResolver;

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Override
    public List<DocumentationViolation> check(XWikiDocument document)
    {
        List<DocumentationViolation> violations = new ArrayList<>();
        XDOM relatedXDOM = parseXPropertyXDOM(document, document.getXDOM(), "related", "Related", CHECK_NAME);
        if (relatedXDOM != null) {
            checkLinks(relatedXDOM, document.getDocumentReference(), violations);
            checkInsideWikiMacros(relatedXDOM, document, null, CHECK_NAME,
                macroXDOM -> checkLinks(macroXDOM, document.getDocumentReference(), violations));
        }
        return violations;
    }

    private void checkLinks(XDOM xdom, DocumentReference documentReference, List<DocumentationViolation> violations)
    {
        List<LinkBlock> linkBlocks = xdom.getBlocks(new ClassBlockMatcher(LinkBlock.class), Block.Axes.DESCENDANT);
        for (LinkBlock linkBlock : linkBlocks) {
            String label = getLabel(linkBlock).trim();
            int targetIndex = label.lastIndexOf(TARGET_PREFIX);
            if (targetIndex < 0) {
                continue;
            }
            String labelTitle = label.substring(0, targetIndex).trim();
            String labelTarget = getLabelTarget(label.substring(targetIndex));

            XWikiDocument linkedDocument = getLinkedDocument(linkBlock.getReference(), documentReference);
            String expectedTitle = getTitle(linkedDocument, labelTitle);
            String expectedTarget = getTarget(linkedDocument, labelTarget);
            String expectedLabel = getExpectedLabel(expectedTitle, expectedTarget);
            if (!expectedLabel.equals(label)) {
                violations.add(new DocumentationViolation(
                    "Related link labels must follow the format '<exact page title> (for <target>)', where the "
                        + "title and the target (User, Administrator or Developer) match the linked page.",
                    String.format("Label: [%s], Expected: [%s]", label, expectedLabel),
                    DocumentationViolationSeverity.WARNING));
            }
        }
    }

    private String getExpectedLabel(String title, String target)
    {
        String targetQualifier = TARGET_PREFIX + target + ')';
        // A title can already end with the target qualifier, to disambiguate it from same-topic pages for other
        // targets (e.g. "All Bundled Rendering Macros (for User)"), in which case the title is the full label.
        if (title.endsWith(targetQualifier)) {
            return title;
        }
        return String.format("%s %s", title, targetQualifier);
    }

    private String getLabelTarget(String targetQualifier)
    {
        for (String target : TARGETS.values()) {
            if (targetQualifier.equals(TARGET_PREFIX + target + ')')) {
                return target;
            }
        }
        return null;
    }

    private String getLabel(LinkBlock linkBlock)
    {
        StringBuilder builder = new StringBuilder();
        for (Block block : linkBlock.getBlocks(block -> block instanceof WordBlock || block instanceof SpaceBlock
            || block instanceof SpecialSymbolBlock, Block.Axes.DESCENDANT))
        {
            if (block instanceof WordBlock wordBlock) {
                builder.append(wordBlock.getWord());
            } else if (block instanceof SpaceBlock) {
                builder.append(' ');
            } else {
                builder.append(((SpecialSymbolBlock) block).getSymbol());
            }
        }
        return builder.toString();
    }

    private XWikiDocument getLinkedDocument(ResourceReference reference, DocumentReference documentReference)
    {
        EntityReference linkedReference =
            this.resourceReferenceResolver.resolve(reference, EntityType.DOCUMENT, documentReference);
        if (linkedReference != null) {
            XWikiContext xcontext = this.xcontextProvider.get();
            try {
                XWikiDocument linkedDocument =
                    xcontext.getWiki().getDocument(new DocumentReference(linkedReference), xcontext);
                if (!linkedDocument.isNew()) {
                    return linkedDocument;
                }
            } catch (XWikiException e) {
                this.logger.warn("Failed to load the linked document [{}]. Ignoring its title and target in the {} "
                    + "check. " + ROOT_ERROR_CAUSE, linkedReference, CHECK_NAME, ExceptionUtils.getRootCauseMessage(e));
            }
        }
        return null;
    }

    private String getTitle(XWikiDocument linkedDocument, String defaultTitle)
    {
        if (linkedDocument != null) {
            String title = linkedDocument.getTitle();
            if (title != null && !title.isBlank()) {
                return title.trim();
            }
        }
        return defaultTitle;
    }

    private String getTarget(XWikiDocument linkedDocument, String defaultTarget)
    {
        if (linkedDocument != null) {
            BaseObject docObject = linkedDocument.getXObject(DOCUMENTATION_CLASS_REFERENCE);
            if (docObject != null) {
                String target = TARGETS.get(docObject.getStringValue("target"));
                if (target != null) {
                    return target;
                }
            }
        }
        return defaultTarget != null ? defaultTarget : "User|Administrator|Developer";
    }
}
