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

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.documentation.DocumentationCheck;
import org.xwiki.contrib.documentation.DocumentationViolation;
import org.xwiki.contrib.documentation.DocumentationViolationSeverity;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.ImageBlock;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.block.match.ClassBlockMatcher;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.listener.reference.ResourceType;
import org.xwiki.rendering.macro.MacroContentParser;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.rendering.transformation.TransformationContext;

import com.xpn.xwiki.doc.XWikiDocument;

/**
 * Verify that when the Gallery macro is used, all images inside it have the alt parameter specified.
 *
 * @version $Id$
 * @since 1.10
 */
@Component
@Singleton
@Named("galleryMacroAlt")
public class GalleryMacroAltCheck implements DocumentationCheck
{
    @Inject
    private Logger logger;

    @Inject
    private MacroContentParser contentParser;

    @Override
    public List<DocumentationViolation> check(XWikiDocument document)
    {
        List<DocumentationViolation> violations = new ArrayList<>();
        XDOM xdom = document.getXDOM();
        List<MacroBlock> macroBlocks = xdom.getBlocks(new ClassBlockMatcher(MacroBlock.class), Block.Axes.DESCENDANT);
        for (MacroBlock macroBlock : macroBlocks) {
            if ("gallery".equals(macroBlock.getId())) {
                // The gallery macro can be written in any markup syntax. It's using the syntax of the document it
                // is in.
                TransformationContext context = new TransformationContext(xdom, document.getSyntax());
                MacroTransformationContext macroContext = new MacroTransformationContext(context);
                XDOM macroXDOM;
                try {
                    macroXDOM = this.contentParser.parse(macroBlock.getContent(), macroContext, false, false);
                } catch (MacroExecutionException e) {
                    // Failed to parse the content, don't consider that it's a violation. Just log an error and skip
                    // the check
                    this.logger.warn("Failed to parse the content of the gallery macro [{}]. Ignoring Gallery Macro "
                        + "Alt check. Root error cause: [{}]", macroBlock.getContent(),
                        ExceptionUtils.getRootCauseMessage(e));
                    continue;
                }
                // Extract all ImageBlocks from the macro content XDOM, and verify that they have an "alt" parameter
                // specified.
                List<ImageBlock> imageBlocks =
                    macroXDOM.getBlocks(new ClassBlockMatcher(ImageBlock.class), Block.Axes.DESCENDANT);
                for (ImageBlock imageBlock : imageBlocks) {
                    if (imageBlock.getParameter("alt") == null) {
                        violations.add(new DocumentationViolation(
                            "Images inside the Gallery macro should specify an 'alt' parameter.",
                            String.format("Image reference : %s", imageBlock.getReference().getReference()),
                            DocumentationViolationSeverity.WARNING));
                    }
                }
            }
        }
        return violations;
    }
}
