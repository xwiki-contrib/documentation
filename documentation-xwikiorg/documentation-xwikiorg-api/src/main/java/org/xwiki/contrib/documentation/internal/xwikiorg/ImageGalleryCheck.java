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

import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.documentation.DocumentationViolation;
import org.xwiki.contrib.documentation.DocumentationViolationSeverity;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.EmptyLinesBlock;
import org.xwiki.rendering.block.ImageBlock;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.NewLineBlock;
import org.xwiki.rendering.block.SpaceBlock;
import org.xwiki.rendering.block.XDOM;

import com.xpn.xwiki.doc.XWikiDocument;

/**
 * Verify that if there are more than 1 image next to each other (i.e. sibling images or image macros separated only
 * by white spaces or new lines), they should be replaced by the Gallery macro.
 * <p>
 * The check works on the parsed XDOM of the content, of the FAQ and of the macros whose content is wiki content, so
 * that images separated by other content, or shown as examples inside macros such as the Code macro, are not reported.
 *
 * @version $Id$
 * @since 1.0
 */
@Component
@Singleton
@Named("imageGallery")
public class ImageGalleryCheck extends AbstractXDOMDocumentationCheck
{
    private static final String CHECK_NAME = "Image Gallery";

    private static final String GALLERY_MACRO_ID = "gallery";

    private static final String IMAGE_MACRO_ID = "image";

    @Override
    public List<DocumentationViolation> check(XWikiDocument document)
    {
        List<DocumentationViolation> violations = new ArrayList<>();
        XDOM xdom = document.getXDOM();

        // Images inside the Gallery macro are meant to be next to each other, thus we don't look inside it.
        checkXDOM(xdom, violations);
        checkInsideWikiMacros(xdom, document, GALLERY_MACRO_ID, CHECK_NAME,
            macroXDOM -> checkXDOM(macroXDOM, violations));

        XDOM faqXDOM = parseFAQXDOM(document, xdom, CHECK_NAME);
        if (faqXDOM != null) {
            checkXDOM(faqXDOM, violations);
            checkInsideWikiMacros(faqXDOM, document, GALLERY_MACRO_ID, CHECK_NAME,
                macroXDOM -> checkXDOM(macroXDOM, violations));
        }

        return violations;
    }

    private void checkXDOM(XDOM xdom, List<DocumentationViolation> violations)
    {
        List<Block> parentBlocks =
            xdom.getBlocks(block -> !block.getChildren().isEmpty(), Block.Axes.DESCENDANT_OR_SELF);
        for (Block parentBlock : parentBlocks) {
            checkSiblings(parentBlock.getChildren(), violations);
        }
    }

    private void checkSiblings(List<Block> siblings, List<DocumentationViolation> violations)
    {
        List<String> imageReferences = new ArrayList<>();
        for (Block sibling : siblings) {
            String imageReference = getImageReference(sibling);
            if (imageReference != null) {
                imageReferences.add(imageReference);
            } else if (!isWhiteSpace(sibling)) {
                reportImages(imageReferences, violations);
                imageReferences.clear();
            }
        }
        reportImages(imageReferences, violations);
    }

    private void reportImages(List<String> imageReferences, List<DocumentationViolation> violations)
    {
        if (imageReferences.size() > 1) {
            violations.add(new DocumentationViolation("Use the Gallery macro when several images are displayed next "
                + "to each other.", String.format("Image references : %s", String.join(", ", imageReferences)),
                DocumentationViolationSeverity.ERROR));
        }
    }

    /**
     * @param block the block to check
     * @return the reference of the image if the block is an image or an Image macro, {@code null} otherwise
     */
    private String getImageReference(Block block)
    {
        String result = null;
        if (block instanceof ImageBlock) {
            result = ((ImageBlock) block).getReference().getReference();
        } else if (block instanceof MacroBlock && IMAGE_MACRO_ID.equals(((MacroBlock) block).getId())) {
            String reference = ((MacroBlock) block).getParameter("reference");
            result = reference == null ? "" : reference;
        }
        return result;
    }

    private boolean isWhiteSpace(Block block)
    {
        return block instanceof SpaceBlock || block instanceof NewLineBlock || block instanceof EmptyLinesBlock;
    }
}
