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
import java.util.Optional;
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
 * Verify that PlantUML diagrams don't set their own theme or colors, since the diagrams of xwiki.org get their styling
 * from the wiki's default diagram style (the "Default Diagram Style" setting of the PlantUML Macro), which only applies
 * to the diagrams that don't set a theme. A diagram is reported when it contains a {@code !theme} line, a color
 * {@code skinparam} (a skinparam whose name contains {@code color}, also inside a {@code skinparam ... { }} block)
 * or a {@code <style>} block. Layout-only directives (e.g. {@code skinparam nodesep 60}) are allowed.
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

    private static final Pattern THEME_PATTERN = Pattern.compile("^!theme\\b.*");

    private static final Pattern STYLE_PATTERN = Pattern.compile("^<style>.*", Pattern.CASE_INSENSITIVE);

    private static final String SKINPARAM = "skinparam";

    private static final String COLOR = "color";

    /**
     * Matches the opening line of a skinparam block, i.e. a {@code skinparam} line, with or without an element name,
     * ending with an opening curly bracket.
     */
    private static final Pattern SKINPARAM_BLOCK_START_PATTERN =
        Pattern.compile("^skinparam(\\s+\\S+)?\\s*\\{$", Pattern.CASE_INSENSITIVE);

    private static final String BLOCK_END = "}";

    @Override
    public List<DocumentationViolation> check(XWikiDocument document)
    {
        List<DocumentationViolation> violations = new ArrayList<>();
        checkContentAndFAQ(document, PLANTUML_MACRO_ID, CHECK_NAME, contentXDOM -> checkXDOM(contentXDOM, violations));

        return violations;
    }

    private void checkXDOM(XDOM xdom, List<DocumentationViolation> violations)
    {
        List<MacroBlock> macroBlocks = xdom.getBlocks(new ClassBlockMatcher(MacroBlock.class), Block.Axes.DESCENDANT);
        for (MacroBlock macroBlock : macroBlocks) {
            if (PLANTUML_MACRO_ID.equals(macroBlock.getId())) {
                findStylingLine(StringUtils.defaultString(macroBlock.getContent())).ifPresent(line ->
                    violations.add(new DocumentationViolation(
                        "PlantUML diagrams must not set a theme or colors: the wiki's default diagram style applies.",
                        String.format("Diagram line : %s", line),
                        DocumentationViolationSeverity.ERROR)));
            }
        }
    }

    private Optional<String> findStylingLine(String content)
    {
        boolean inSkinparamBlock = false;
        for (String rawLine : content.split("\\R")) {
            String line = rawLine.trim();
            if (inSkinparamBlock) {
                if (BLOCK_END.equals(line)) {
                    inSkinparamBlock = false;
                } else if (isColorParameter(line)) {
                    return Optional.of(line);
                }
            } else if (THEME_PATTERN.matcher(line).matches() || STYLE_PATTERN.matcher(line).matches()
                || isColorSkinparam(line))
            {
                return Optional.of(line);
            } else if (SKINPARAM_BLOCK_START_PATTERN.matcher(line).matches()) {
                inSkinparamBlock = true;
            }
        }
        return Optional.empty();
    }

    /**
     * Check if a line is a single-line skinparam whose name contains "color", e.g. {@code skinparam ArrowColor #333}
     * or {@code skinparam component<<Main>>BackgroundColor #fff}.
     *
     * @param line the trimmed diagram line
     * @return {@code true} if the line sets a color skinparam
     */
    private boolean isColorSkinparam(String line)
    {
        String[] words = StringUtils.split(line);
        return words.length > 1 && SKINPARAM.equalsIgnoreCase(words[0]) && isColorName(words[1]);
    }

    /**
     * Check if a line of a skinparam block sets a parameter whose name contains "color", e.g.
     * {@code BorderColor #333}.
     *
     * @param line the trimmed diagram line
     * @return {@code true} if the line sets a color parameter
     */
    private boolean isColorParameter(String line)
    {
        String[] words = StringUtils.split(line);
        return words.length > 0 && isColorName(words[0]);
    }

    private boolean isColorName(String name)
    {
        return StringUtils.containsIgnoreCase(name, COLOR);
    }
}
