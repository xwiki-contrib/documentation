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
import java.util.Set;
import java.util.regex.Pattern;

import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.documentation.DocumentationCheck;
import org.xwiki.contrib.documentation.DocumentationViolation;
import org.xwiki.contrib.documentation.DocumentationViolationSeverity;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.LocalDocumentReference;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

/**
 * Verify the rules specific to top-level documentation pages, i.e. the pages located right under an audience page
 * ({@code documentation.<xs|extensions>.<user|admin|dev>.<page>}): they must be of Explanation type, and their title
 * must have at most 3 words and no parenthetical qualifier.
 *
 * @version $Id$
 * @since 1.15
 */
@Component
@Singleton
@Named("topLevelPage")
public class TopLevelPageCheck implements DocumentationCheck
{
    private static final LocalDocumentReference DOCUMENTATION_CLASS_REFERENCE =
        new LocalDocumentReference(List.of("DocApp", "Code"), "DocumentationClass");

    private static final Set<String> SECTIONS = Set.of("xs", "extensions");

    private static final Set<String> AUDIENCES = Set.of("user", "admin", "dev");

    private static final int TOP_LEVEL_DEPTH = 4;

    private static final int MAX_TITLE_WORDS = 3;

    private static final Pattern PARENTHETICAL_QUALIFIER = Pattern.compile("\\(.*\\)");

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private static final String PAGE_TITLE_CONTEXT = "Page title: [%s]";

    @Override
    public List<DocumentationViolation> check(XWikiDocument document)
    {
        List<DocumentationViolation> violations = new ArrayList<>();
        if (!isTopLevelPage(document.getDocumentReference())) {
            return violations;
        }

        BaseObject docObject = document.getXObject(DOCUMENTATION_CLASS_REFERENCE);
        String type = docObject != null ? docObject.getStringValue("type") : "";
        if (!"explanation".equals(type)) {
            violations.add(new DocumentationViolation(
                "Top-level documentation pages must be of Explanation type, for consistency.",
                String.format("Type: [%s]", type),
                DocumentationViolationSeverity.ERROR));
        }

        String title = document.getTitle();
        if (title != null && !title.isBlank()) {
            if (WHITESPACE.split(title.trim()).length > MAX_TITLE_WORDS) {
                violations.add(new DocumentationViolation(
                    String.format("Top-level page titles must be concise: %s words maximum.", MAX_TITLE_WORDS),
                    String.format(PAGE_TITLE_CONTEXT, title),
                    DocumentationViolationSeverity.ERROR));
            }
            if (PARENTHETICAL_QUALIFIER.matcher(title).find()) {
                violations.add(new DocumentationViolation(
                    "Top-level page titles must not use parenthetical qualifiers.",
                    String.format(PAGE_TITLE_CONTEXT, title),
                    DocumentationViolationSeverity.ERROR));
            }
        }
        return violations;
    }

    private boolean isTopLevelPage(DocumentReference reference)
    {
        List<String> spaces =
            reference.getSpaceReferences().stream().map(EntityReference::getName).toList();
        if (!"WebHome".equals(reference.getName()) || spaces.size() != TOP_LEVEL_DEPTH) {
            return false;
        }
        return "documentation".equals(spaces.get(0)) && SECTIONS.contains(spaces.get(1))
            && AUDIENCES.contains(spaces.get(2));
    }
}
