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
 * Verify that PlantUML diagrams use the {@code bluegray} theme, i.e. that the content of each PlantUML macro contains
 * a {@code !theme bluegray} line.
 *
 * @version $Id$
 * @since 1.15
 */
@Component
@Singleton
@Named("plantUMLTheme")
public class PlantUMLThemeCheck extends AbstractXDOMDocumentationCheck
{
    private static final String CHECK_NAME = "PlantUML Theme";

    private static final String PLANTUML_MACRO_ID = "plantuml";

    /**
     * Matches a {@code !theme bluegray} line, optionally followed by a {@code from <location>} clause.
     */
    private static final Pattern BLUEGRAY_THEME_PATTERN = Pattern.compile("^\\s*!theme\\s+bluegray(\\s.*)?$",
        Pattern.MULTILINE);

    @Override
    public List<DocumentationViolation> check(XWikiDocument document)
    {
        List<DocumentationViolation> violations = new ArrayList<>();
        XDOM xdom = document.getXDOM();

        checkXDOM(xdom, violations);
        checkInsideWikiMacros(xdom, document, PLANTUML_MACRO_ID, CHECK_NAME,
            macroXDOM -> checkXDOM(macroXDOM, violations));

        XDOM faqXDOM = parseFAQXDOM(document, xdom, CHECK_NAME);
        if (faqXDOM != null) {
            checkXDOM(faqXDOM, violations);
            checkInsideWikiMacros(faqXDOM, document, PLANTUML_MACRO_ID, CHECK_NAME,
                macroXDOM -> checkXDOM(macroXDOM, violations));
        }

        return violations;
    }

    private void checkXDOM(XDOM xdom, List<DocumentationViolation> violations)
    {
        List<MacroBlock> macroBlocks = xdom.getBlocks(new ClassBlockMatcher(MacroBlock.class), Block.Axes.DESCENDANT);
        for (MacroBlock macroBlock : macroBlocks) {
            String content = StringUtils.defaultString(macroBlock.getContent());
            if (PLANTUML_MACRO_ID.equals(macroBlock.getId()) && !BLUEGRAY_THEME_PATTERN.matcher(content).find()) {
                violations.add(new DocumentationViolation(
                    "PlantUML diagrams must use the bluegray theme (add a '!theme bluegray' line).",
                    String.format("Diagram first line : %s", getFirstLine(content)),
                    DocumentationViolationSeverity.ERROR));
            }
        }
    }

    private String getFirstLine(String content)
    {
        for (String line : content.split("\\R")) {
            if (StringUtils.isNotBlank(line)) {
                return line.trim();
            }
        }
        return "";
    }
}
