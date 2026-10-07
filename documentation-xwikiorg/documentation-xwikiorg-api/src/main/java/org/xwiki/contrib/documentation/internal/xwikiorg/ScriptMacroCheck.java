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

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.documentation.DocumentationViolation;
import org.xwiki.contrib.documentation.DocumentationViolationSeverity;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.block.match.ClassBlockMatcher;
import org.xwiki.rendering.macro.MacroId;
import org.xwiki.rendering.macro.MacroLookupException;
import org.xwiki.rendering.macro.script.ScriptMacro;

import com.xpn.xwiki.doc.XWikiDocument;

/**
 * Verify that documentation pages don't use script macros (Velocity, Groovy, Python, Script, etc.): an example result
 * must be shown with a screenshot and not be generated, since a generated example could break over time.
 * <p>
 * A macro is considered to be a script macro when its implementation is a {@link ScriptMacro}, which covers all the
 * script macros (including the JSR-223 ones such as Groovy or Python) whatever their id. Code displayed inside a code
 * macro is not executed and is thus not reported.
 *
 * @version $Id$
 * @since 1.15
 */
@Component
@Singleton
@Named("scriptMacro")
public class ScriptMacroCheck extends AbstractXDOMDocumentationCheck
{
    private static final String CHECK_NAME = "Script Macro";

    private static final int CONTEXT_MAX_LENGTH = 50;

    @Override
    public List<DocumentationViolation> check(XWikiDocument document)
    {
        List<DocumentationViolation> violations = new ArrayList<>();
        checkContentAndFAQ(document, null, CHECK_NAME, contentXDOM -> checkXDOM(contentXDOM, violations));

        return violations;
    }

    private void checkXDOM(XDOM xdom, List<DocumentationViolation> violations)
    {
        List<MacroBlock> macroBlocks = xdom.getBlocks(new ClassBlockMatcher(MacroBlock.class), Block.Axes.DESCENDANT);
        for (MacroBlock macroBlock : macroBlocks) {
            if (isScriptMacro(macroBlock)) {
                violations.add(new DocumentationViolation(
                    "Avoid script macros in documentation: show an example result with a screenshot instead of "
                        + "generating it, since a generated example could break over time.",
                    String.format("Macro : %s, Content : %s", macroBlock.getId(), getContentStart(macroBlock)),
                    DocumentationViolationSeverity.WARNING));
            }
        }
    }

    private boolean isScriptMacro(MacroBlock macroBlock)
    {
        try {
            return this.macroManager.getMacro(new MacroId(macroBlock.getId())) instanceof ScriptMacro;
        } catch (MacroLookupException e) {
            // The macro doesn't exist, it cannot be executed and thus cannot generate anything.
            this.logger.debug("Failed to look up macro [{}]. Not considering it as a script macro.",
                macroBlock.getId(), e);
            return false;
        }
    }

    private String getContentStart(MacroBlock macroBlock)
    {
        return StringUtils.abbreviate(StringUtils.normalizeSpace(StringUtils.defaultString(macroBlock.getContent())),
            CONTEXT_MAX_LENGTH);
    }
}
