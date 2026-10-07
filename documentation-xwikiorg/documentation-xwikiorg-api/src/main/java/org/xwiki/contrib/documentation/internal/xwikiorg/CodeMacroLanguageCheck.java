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

import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.documentation.DocumentationViolation;
import org.xwiki.contrib.documentation.DocumentationViolationSeverity;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.block.match.ClassBlockMatcher;

import com.xpn.xwiki.doc.XWikiDocument;

/**
 * Verify that code macros specify the {@code language} parameter, since otherwise the language has to be inferred
 * from the content, which is slow and often leads to a wrong syntax coloring.
 *
 * @version $Id$
 * @since 1.15
 */
@Component
@Singleton
@Named("codeMacroLanguage")
public class CodeMacroLanguageCheck extends AbstractXDOMDocumentationCheck
{
    private static final String CHECK_NAME = "Code Macro Language";

    private static final String CODE_MACRO_ID = "code";

    private static final String LANGUAGE_PARAMETER = "language";

    private static final int CONTEXT_MAX_LENGTH = 50;

    @Override
    public List<DocumentationViolation> check(XWikiDocument document)
    {
        List<DocumentationViolation> violations = new ArrayList<>();
        checkContentAndFAQ(document, CODE_MACRO_ID, CHECK_NAME, contentXDOM -> checkXDOM(contentXDOM, violations));

        return violations;
    }

    private void checkXDOM(XDOM xdom, List<DocumentationViolation> violations)
    {
        List<MacroBlock> macroBlocks = xdom.getBlocks(new ClassBlockMatcher(MacroBlock.class), Block.Axes.DESCENDANT);
        for (MacroBlock macroBlock : macroBlocks) {
            if (CODE_MACRO_ID.equals(macroBlock.getId()) && StringUtils.isBlank(getLanguage(macroBlock))) {
                violations.add(new DocumentationViolation(
                    "Missing 'language' parameter usage in the Code macro.",
                    String.format("Code content : %s", getContentStart(macroBlock)),
                    DocumentationViolationSeverity.WARNING));
            }
        }
    }

    private String getContentStart(MacroBlock macroBlock)
    {
        return StringUtils.abbreviate(StringUtils.normalizeSpace(StringUtils.defaultString(macroBlock.getContent())),
            CONTEXT_MAX_LENGTH);
    }

    private String getLanguage(MacroBlock macroBlock)
    {
        // Macro parameter names are case-insensitive.
        for (Map.Entry<String, String> entry : macroBlock.getParameters().entrySet()) {
            if (LANGUAGE_PARAMETER.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }
}
