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

import java.util.List;
import java.util.function.Consumer;

import javax.inject.Inject;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.xwiki.contrib.documentation.DocumentationCheck;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.rendering.block.Block;
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
import com.xpn.xwiki.objects.BaseObject;

/**
 * Base class for XWiki.org documentation checks that operate on parsed XDOM content, including macro bodies and the
 * FAQ property of the DocumentationClass XObject.
 *
 * @version $Id$
 * @since 1.13
 */
public abstract class AbstractXDOMDocumentationCheck implements DocumentationCheck
{
    protected static final String ROOT_ERROR_CAUSE = "Root error cause: [{}]";

    protected static final LocalDocumentReference DOCUMENTATION_CLASS_REFERENCE =
        new LocalDocumentReference(List.of("DocApp", "Code"), "DocumentationClass");

    @Inject
    protected Logger logger;

    @Inject
    protected MacroManager macroManager;

    @Inject
    protected MacroContentParser contentParser;

    /**
     * Parse the {@code faq} property of the DocumentationClass XObject attached to the given document and return its
     * XDOM. Returns {@code null} if the object or property is absent, or if parsing fails (in which case a warning is
     * logged).
     *
     * @param document the document to inspect
     * @param xdom the document's main XDOM, used as parsing context
     * @param checkName a human-readable check name used in warning messages (e.g. {@code "Image Macro"})
     * @return the parsed XDOM of the faq property, or {@code null} if there's no FAQ xproperty or parsing fails
     */
    protected XDOM parseFAQXDOM(XWikiDocument document, XDOM xdom, String checkName)
    {
        BaseObject docObject = document.getXObject(DOCUMENTATION_CLASS_REFERENCE);
        if (docObject != null) {
            String faqContent = docObject.getLargeStringValue("faq");
            if (!faqContent.isEmpty()) {
                try {
                    TransformationContext context = new TransformationContext(xdom, document.getSyntax());
                    MacroTransformationContext macroContext = new MacroTransformationContext(context);
                    return this.contentParser.parse(faqContent, macroContext, false, false);
                } catch (MacroExecutionException e) {
                    this.logger.warn("Failed to parse the FAQ content. Ignoring {} check inside it. "
                        + ROOT_ERROR_CAUSE, checkName, ExceptionUtils.getRootCauseMessage(e));
                }
            }
        }
        return null;
    }

    /**
     * Iterate over all macros in the given XDOM that have wiki-markup content (i.e. whose content descriptor type is
     * {@link Block#LIST_BLOCK_TYPE}), parse each one's body, and pass the resulting XDOM to {@code consumer}.
     * <p>
     * Macros whose ID equals {@code skipMacroId} are skipped (pass {@code null} to skip nothing). Lookup or parse
     * failures are logged as warnings and the macro is skipped.
     *
     * @param xdom the XDOM to scan for macros
     * @param document the enclosing document (used to obtain syntax for parsing)
     * @param skipMacroId macro ID to skip, or {@code null} to process all macros
     * @param checkName a human-readable check name used in warning messages
     * @param consumer called with the parsed XDOM of each qualifying macro body
     */
    protected void checkInsideWikiMacros(XDOM xdom, XWikiDocument document, String skipMacroId,
        String checkName, Consumer<XDOM> consumer)
    {
        List<MacroBlock> macroBlocks =
            xdom.getBlocks(new ClassBlockMatcher(MacroBlock.class), Block.Axes.DESCENDANT);
        for (MacroBlock macroBlock : macroBlocks) {
            if (skipMacroId != null && skipMacroId.equals(macroBlock.getId())) {
                continue;
            }
            try {
                ContentDescriptor contentDescriptor = this.macroManager.getMacro(new MacroId(macroBlock.getId()))
                    .getDescriptor().getContentDescriptor();
                if (contentDescriptor != null && Block.LIST_BLOCK_TYPE.equals(contentDescriptor.getType())) {
                    TransformationContext context = new TransformationContext(xdom, document.getSyntax());
                    MacroTransformationContext macroContext = new MacroTransformationContext(context);
                    XDOM macroXDOM = this.contentParser.parse(macroBlock.getContent(), macroContext, false, false);
                    consumer.accept(macroXDOM);
                }
            } catch (MacroLookupException e) {
                this.logger.warn("Failed to look up macro [{}]. Ignoring {} check inside it. "
                    + ROOT_ERROR_CAUSE, macroBlock.getId(), checkName, ExceptionUtils.getRootCauseMessage(e));
            } catch (MacroExecutionException e) {
                this.logger.warn("Failed to parse the content of macro [{}]. Ignoring {} check inside it. "
                    + ROOT_ERROR_CAUSE, macroBlock.getId(), checkName, ExceptionUtils.getRootCauseMessage(e));
            }
        }
    }
}
