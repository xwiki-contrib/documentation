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
import org.xwiki.contrib.documentation.DocumentationCheck;
import org.xwiki.contrib.documentation.DocumentationViolation;
import org.xwiki.contrib.documentation.DocumentationViolationSeverity;

import com.xpn.xwiki.doc.XWikiDocument;

/**
 * Verify that the page name of a documentation page follows the naming conventions: kebab-case (lowercase letters,
 * digits and hyphens only — no spaces, accented characters, or other special characters), no English stop word, and
 * no documentation-type word. Each rule is reported on its own so that a name is never asked to fix something it
 * does not break.
 *
 * @version $Id$
 * @since 1.13
 */
@Component
@Singleton
@Named("pageName")
public class PageNameCheck implements DocumentationCheck
{
    private static final String PAGE_NAME_CONTEXT = "Page name: [%s], Expected: [%s]";

    private static final String PAGE_STOP_WORD_CONTEXT = "Page name: [%s], Stop words: [%s], Expected: [%s]";

    @Override
    public List<DocumentationViolation> check(XWikiDocument document)
    {
        List<DocumentationViolation> violations = new ArrayList<>();
        String pageName = document.getDocumentReference().getName();
        if ("WebHome".equals(pageName)) {
            pageName = document.getDocumentReference().getLastSpaceReference().getName();
        }
        if (!KebabNameValidator.isValidKebab(pageName)) {
            violations.add(new DocumentationViolation(
                "Page name must follow the kebab-case naming convention "
                    + "(lowercase, hyphens instead of spaces or special characters).",
                String.format(PAGE_NAME_CONTEXT, pageName, KebabNameValidator.toKebab(pageName)),
                DocumentationViolationSeverity.ERROR));
            return violations;
        }
        List<String> stopWords = KebabNameValidator.getStopWords(pageName);
        if (!stopWords.isEmpty()) {
            violations.add(new DocumentationViolation(
                "Page name should not contain English stop words (articles, conjunctions, prepositions and "
                    + "auxiliaries), which add length without adding meaning.",
                String.format(PAGE_STOP_WORD_CONTEXT, pageName, String.join(", ", stopWords),
                    KebabNameValidator.removeStopWords(pageName)),
                DocumentationViolationSeverity.WARNING));
        }
        if (KebabNameValidator.containsReservedWord(pageName)) {
            violations.add(new DocumentationViolation(
                "Page name must not contain documentation-type words "
                    + "(explanation, howto, reference, tutorial).",
                String.format(PAGE_NAME_CONTEXT, pageName, KebabNameValidator.removeReservedWords(pageName)),
                DocumentationViolationSeverity.ERROR));
        }
        return violations;
    }
}
