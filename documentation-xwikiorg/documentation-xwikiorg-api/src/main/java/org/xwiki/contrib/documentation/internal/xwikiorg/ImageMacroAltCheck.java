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
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.block.match.ClassBlockMatcher;

import com.xpn.xwiki.doc.XWikiDocument;

/**
 * Verify that image macros are using the {@code alt} parameter for accessbility reasons.
 *
 * @version $Id$
 * @since 1.6
 */
@Component
@Singleton
@Named("imageMacroAlt")
public class ImageMacroAltCheck extends AbstractXDOMDocumentationCheck
{
    private static final String CHECK_NAME = "Image Macro Alt";

    private static final String IMAGE_MACRO_ID = "image";

    @Override
    public List<DocumentationViolation> check(XWikiDocument document)
    {
        List<DocumentationViolation> violations = new ArrayList<>();
        XDOM xdom = document.getXDOM();

        checkXDOM(xdom, violations);
        checkInsideWikiMacros(xdom, document, IMAGE_MACRO_ID, CHECK_NAME,
            macroXDOM -> checkXDOM(macroXDOM, violations));

        XDOM faqXDOM = parseFAQXDOM(document, xdom, CHECK_NAME);
        if (faqXDOM != null) {
            checkXDOM(faqXDOM, violations);
            checkInsideWikiMacros(faqXDOM, document, IMAGE_MACRO_ID, CHECK_NAME,
                macroXDOM -> checkXDOM(macroXDOM, violations));
        }

        return violations;
    }

    private void checkXDOM(XDOM xdom, List<DocumentationViolation> violations)
    {
        List<MacroBlock> macroBlocks = xdom.getBlocks(new ClassBlockMatcher(MacroBlock.class), Block.Axes.DESCENDANT);
        for (MacroBlock macroBlock : macroBlocks) {
            if (IMAGE_MACRO_ID.equals(macroBlock.getId()) && macroBlock.getParameter("alt") == null) {
                violations.add(new DocumentationViolation("Missing 'alt' parameter usage in the Image macro.",
                    String.format("Image reference : %s", macroBlock.getParameter("reference")),
                    DocumentationViolationSeverity.WARNING));
            }
        }
    }
}
