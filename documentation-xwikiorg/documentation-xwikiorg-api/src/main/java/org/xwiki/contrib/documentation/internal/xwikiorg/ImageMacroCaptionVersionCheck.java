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
import java.util.regex.Pattern;

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
 * Verify that the {@code caption} parameter of image macros doesn't indicate the XWiki version in which the screenshot
 * was taken, i.e. that it contains no version number (e.g. {@code 17.10}, {@code 16.10.5}, {@code 18.0.0RC1}) and no
 * {@code XWiki} word followed by a version (e.g. {@code XWiki 17}).
 *
 * @version $Id$
 * @since 1.15
 */
@Component
@Singleton
@Named("imageMacroCaptionVersion")
public class ImageMacroCaptionVersionCheck extends AbstractXDOMDocumentationCheck
{
    private static final String CHECK_NAME = "Image Macro Caption Version";

    private static final String IMAGE_MACRO_ID = "image";

    /**
     * A version number with at least two numeric parts (e.g. {@code 17.10}, {@code v16.10.5}), optionally followed by
     * a qualifier (e.g. {@code 18.0.0RC1}, {@code 18.0.0-rc-1}, {@code 17.0M2}, {@code 17.10-SNAPSHOT}), or the word
     * {@code XWiki} followed by a number (e.g. {@code XWiki 17}).
     */
    private static final Pattern VERSION_PATTERN = Pattern.compile(
        "(?<![\\w.])v?\\d+(?:\\.\\d+)+(?:-?(?:rc|m|milestone|snapshot)(?:-?\\d+)?)?(?![\\w.]*\\w)"
            + "|\\bxwiki\\s*v?\\d+",
        Pattern.CASE_INSENSITIVE);

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
            if (IMAGE_MACRO_ID.equals(macroBlock.getId())) {
                String caption = macroBlock.getParameter("caption");
                if (caption != null && VERSION_PATTERN.matcher(caption).find()) {
                    String reference = macroBlock.getParameter("reference");
                    violations.add(new DocumentationViolation(
                        "The caption of the Image macro should not indicate the XWiki version in which the "
                            + "screenshot was taken.",
                        String.format("Image reference : %s, Caption : %s", reference == null ? "" : reference,
                            caption),
                        DocumentationViolationSeverity.WARNING));
                }
            }
        }
    }
}
