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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.documentation.DocumentationViolation;
import org.xwiki.contrib.documentation.DocumentationViolationSeverity;
import org.xwiki.model.reference.AttachmentReference;
import org.xwiki.model.reference.AttachmentReferenceResolver;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.ImageBlock;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.block.match.ClassBlockMatcher;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.listener.reference.ResourceType;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.rendering.transformation.TransformationContext;

import com.xpn.xwiki.doc.XWikiDocument;

/**
 * Verify that the images displayed with the Image macro or the Gallery macro reference attachments of the current page,
 * since an image referencing an attachment of another page is no longer displayed when that other page is moved or
 * renamed.
 *
 * @version $Id$
 * @since 1.15
 */
@Component
@Singleton
@Named("imageOtherPageAttachment")
public class ImageOtherPageAttachmentCheck extends AbstractXDOMDocumentationCheck
{
    private static final String CHECK_NAME = "Image Other Page Attachment";

    private static final String IMAGE_MACRO_ID = "image";

    private static final String GALLERY_MACRO_ID = "gallery";

    @Inject
    @Named("current")
    private AttachmentReferenceResolver<String> attachmentReferenceResolver;

    @Inject
    @Named("compact")
    private EntityReferenceSerializer<String> compactEntityReferenceSerializer;

    @Override
    public List<DocumentationViolation> check(XWikiDocument document)
    {
        List<DocumentationViolation> violations = new ArrayList<>();
        XDOM xdom = document.getXDOM();
        checkXDOM(xdom, document, violations);
        checkInsideWikiMacros(xdom, document, IMAGE_MACRO_ID, CHECK_NAME,
            macroXDOM -> checkXDOM(macroXDOM, document, violations));

        XDOM faqXDOM = parseFAQXDOM(document, xdom, CHECK_NAME);
        if (faqXDOM != null) {
            checkXDOM(faqXDOM, document, violations);
            checkInsideWikiMacros(faqXDOM, document, IMAGE_MACRO_ID, CHECK_NAME,
                macroXDOM -> checkXDOM(macroXDOM, document, violations));
        }

        return violations;
    }

    private void checkXDOM(XDOM xdom, XWikiDocument document, List<DocumentationViolation> violations)
    {
        List<MacroBlock> macroBlocks = xdom.getBlocks(new ClassBlockMatcher(MacroBlock.class), Block.Axes.DESCENDANT);
        for (MacroBlock macroBlock : macroBlocks) {
            if (IMAGE_MACRO_ID.equals(macroBlock.getId())) {
                checkReference(macroBlock.getParameter("reference"), document, violations);
            } else if (GALLERY_MACRO_ID.equals(macroBlock.getId())) {
                checkGallery(macroBlock, xdom, document, violations);
            }
        }
    }

    private void checkGallery(MacroBlock macroBlock, XDOM xdom, XWikiDocument document,
        List<DocumentationViolation> violations)
    {
        // The gallery macro content uses the syntax of the document it is in.
        TransformationContext context = new TransformationContext(xdom, document.getSyntax());
        MacroTransformationContext macroContext = new MacroTransformationContext(context);
        XDOM macroXDOM;
        try {
            macroXDOM = this.contentParser.parse(macroBlock.getContent(), macroContext, false, false);
        } catch (MacroExecutionException e) {
            this.logger.warn("Failed to parse the content of the gallery macro [{}]. Ignoring {} check inside it. "
                + ROOT_ERROR_CAUSE, macroBlock.getContent(), CHECK_NAME, ExceptionUtils.getRootCauseMessage(e));
            return;
        }
        List<ImageBlock> imageBlocks =
            macroXDOM.getBlocks(new ClassBlockMatcher(ImageBlock.class), Block.Axes.DESCENDANT);
        for (ImageBlock imageBlock : imageBlocks) {
            ResourceReference resourceReference = imageBlock.getReference();
            // Only images pointing to an attachment can belong to another page (e.g. not URLs or icons).
            if (ResourceType.ATTACHMENT.equals(resourceReference.getType())) {
                checkReference(resourceReference.getReference(), document, violations);
            }
        }
    }

    private void checkReference(String reference, XWikiDocument document, List<DocumentationViolation> violations)
    {
        if (StringUtils.isBlank(reference)) {
            return;
        }
        DocumentReference documentReference = document.getDocumentReference();
        AttachmentReference attachmentReference =
            this.attachmentReferenceResolver.resolve(reference, documentReference);
        DocumentReference ownerReference = attachmentReference.getDocumentReference();
        if (!documentReference.equals(ownerReference)) {
            violations.add(new DocumentationViolation(
                "Images should not reference an attachment of another page, since the image is no longer displayed "
                    + "when that page is moved or renamed. Attach the image to the current page instead.",
                String.format("Image reference : %s, Page : %s", reference,
                    this.compactEntityReferenceSerializer.serialize(ownerReference, documentReference)),
                DocumentationViolationSeverity.WARNING));
        }
    }
}
