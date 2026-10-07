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
import org.xwiki.rendering.block.MacroMarkerBlock;
import org.xwiki.rendering.block.XDOM;

import com.xpn.xwiki.doc.XWikiDocument;

/**
 * Verify that the content doesn't use inline styles (i.e. blocks having a {@code style} parameter, such as
 * {@code (% style="..." %)}), since pages should only contain content and should all look the same. Macro parameters
 * are not concerned.
 *
 * @version $Id$
 * @since 1.15
 */
@Component
@Singleton
@Named("inlineStyle")
public class InlineStyleCheck extends AbstractXDOMDocumentationCheck
{
    private static final String CHECK_NAME = "Inline Style";

    private static final String STYLE_PARAMETER = "style";

    @Override
    public List<DocumentationViolation> check(XWikiDocument document)
    {
        List<DocumentationViolation> violations = new ArrayList<>();
        checkContentAndFAQ(document, null, CHECK_NAME, contentXDOM -> checkXDOM(contentXDOM, violations));

        return violations;
    }

    private void checkXDOM(XDOM xdom, List<DocumentationViolation> violations)
    {
        List<Block> styledBlocks = xdom.getBlocks(block -> !(block instanceof MacroBlock)
            && !(block instanceof MacroMarkerBlock) && block.getParameter(STYLE_PARAMETER) != null,
            Block.Axes.DESCENDANT);
        for (Block styledBlock : styledBlocks) {
            violations.add(new DocumentationViolation("Inline styles should not be used in the content.",
                "Style : " + styledBlock.getParameter(STYLE_PARAMETER),
                DocumentationViolationSeverity.WARNING));
        }
    }
}
