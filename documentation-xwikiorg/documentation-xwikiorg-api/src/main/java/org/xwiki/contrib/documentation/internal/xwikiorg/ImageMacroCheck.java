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
import org.xwiki.rendering.macro.MacroContentParser;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.macro.MacroId;
import org.xwiki.rendering.macro.MacroLookupException;
import org.xwiki.rendering.macro.MacroManager;
import org.xwiki.rendering.macro.descriptor.ContentDescriptor;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.rendering.transformation.TransformationContext;

import com.xpn.xwiki.doc.XWikiDocument;

/**
 * Verify that documentation pages are not using the image syntax (i.e., they should use the image or gallery macros).
 *
 * @version $Id$
 * @since 1.0
 */
@Component
@Singleton
@Named("imageMacro")
public class ImageMacroCheck implements DocumentationCheck
{
    private static final String ROOT_ERROR_CAUSE = "Root error cause: [{}]";

    @Inject
    private Logger logger;

    @Inject
    private MacroManager macroManager;

    @Inject
    private MacroContentParser contentParser;

    @Override
    public List<DocumentationViolation> check(XWikiDocument document)
    {
        List<DocumentationViolation> violations = new ArrayList<>();

        // Check for violations inside the document's main content.
        XDOM xdom = document.getXDOM();
        checkXDOM(xdom, violations);

        // Also, check inside macros that contain wiki markup.
        List<MacroBlock> macroBlocks =
            xdom.getBlocks(new ClassBlockMatcher(MacroBlock.class), Block.Axes.DESCENDANT);
        for (MacroBlock macroBlock : macroBlocks) {
            try {
                ContentDescriptor contentDescriptor = this.macroManager.getMacro(new MacroId(macroBlock.getId()))
                    .getDescriptor().getContentDescriptor();
                if (contentDescriptor != null && Block.LIST_BLOCK_TYPE.equals(contentDescriptor.getType())) {
                    TransformationContext context = new TransformationContext(xdom, document.getSyntax());
                    MacroTransformationContext macroContext = new MacroTransformationContext(context);
                    XDOM macroXDOM = this.contentParser.parse(macroBlock.getContent(), macroContext, false, false);
                    checkXDOM(macroXDOM, violations);
                }
            } catch (MacroLookupException e) {
                this.logger.warn("Failed to look up macro [{}]. Ignoring Image Macro check inside it. "
                    + ROOT_ERROR_CAUSE, macroBlock.getId(), ExceptionUtils.getRootCauseMessage(e));
            } catch (MacroExecutionException e) {
                this.logger.warn("Failed to parse the content of macro [{}]. Ignoring Image Macro check inside it. "
                    + ROOT_ERROR_CAUSE, macroBlock.getId(), ExceptionUtils.getRootCauseMessage(e));
            }
        }

        return violations;
    }

    private void checkXDOM(XDOM xdom, List<DocumentationViolation> violations)
    {
        List<ImageBlock> imageBlocks = xdom.getBlocks(new ClassBlockMatcher(ImageBlock.class), Block.Axes.DESCENDANT);
        for (ImageBlock imageBlock : imageBlocks) {
            violations.add(new DocumentationViolation("Use the Image macro instead.",
                String.format("Image reference : %s", imageBlock.getReference().getReference()),
                DocumentationViolationSeverity.ERROR));
        }
    }
}
