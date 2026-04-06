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
import java.util.Locale;

import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.documentation.DocumentationCheck;
import org.xwiki.contrib.documentation.DocumentationViolation;
import org.xwiki.contrib.documentation.DocumentationViolationSeverity;
import org.xwiki.model.reference.LocalDocumentReference;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

/**
 * Verifies that page titles and page names follow verb conventions for each Diataxis documentation type:
 * <ul>
 *   <li>How-To and Tutorial pages must start with an imperative verb (e.g. "Add a user", not "Adding a user"
 *       or "The user guide").</li>
 *   <li>Reference and Explanation pages must not start with a verb form (e.g. "User Management", not
 *       "Managing Users"). Note: only gerund forms (ending in "ing") are detected; base-form imperatives
 *       such as "Add" cannot be reliably caught without NLP POS tagging.</li>
 * </ul>
 *
 * @version $Id$
 * @since 1.16
 */
@Component
@Singleton
@Named("verb")
public class VerbCheck implements DocumentationCheck
{
    private static final LocalDocumentReference DOCUMENTATION_CLASS_REFERENCE =
        new LocalDocumentReference(List.of("DocApp", "Code"), "DocumentationClass");

    private static final String PAGE_TITLE_CONTEXT = "Page title: [%s]";

    private static final String PAGE_NAME_CONTEXT = "Page name: [%s]";

    private static final String HOWTO_TUTORIAL_TITLE_MSG =
        "The title of a How-To or Tutorial page must start with a verb in imperative form "
            + "(e.g. 'Add a user', not 'Adding a user').";

    private static final String HOWTO_TUTORIAL_NAME_MSG =
        "The page name of a How-To or Tutorial page must start with a verb in imperative form "
            + "(e.g. 'add-user', not 'adding-user').";

    private static final String REF_EXPLANATION_TITLE_MSG =
        "The title of a Reference or Explanation page must not start with a verb "
            + "(e.g. 'User Management', not 'Managing Users').";

    private static final String REF_EXPLANATION_NAME_MSG =
        "The page name of a Reference or Explanation page must not start with a verb "
            + "(e.g. 'user-management', not 'managing-users').";

    @Override
    public List<DocumentationViolation> check(XWikiDocument document)
    {
        BaseObject docObject = document.getXObject(DOCUMENTATION_CLASS_REFERENCE);
        if (docObject == null) {
            return List.of();
        }

        String type = docObject.getStringValue("type");
        boolean mustStartWithVerb = "howto".equals(type) || "tutorial".equals(type);
        boolean mustNotStartWithVerb = "reference".equals(type) || "explanation".equals(type);
        if (!mustStartWithVerb && !mustNotStartWithVerb) {
            return List.of();
        }

        List<DocumentationViolation> violations = new ArrayList<>();
        checkTitle(document.getTitle(), mustStartWithVerb, violations);
        checkPageName(document.getDocumentReference().getName(), mustStartWithVerb, violations);
        return violations;
    }

    private void checkTitle(String title, boolean mustStartWithVerb, List<DocumentationViolation> violations)
    {
        if (title == null || title.isBlank()) {
            return;
        }
        String firstWord = title.trim().split("\\s+")[0].toLowerCase(Locale.ROOT);
        if (mustStartWithVerb && isNotImperativeVerb(firstWord)) {
            violations.add(new DocumentationViolation(HOWTO_TUTORIAL_TITLE_MSG,
                String.format(PAGE_TITLE_CONTEXT, title), DocumentationViolationSeverity.WARNING));
        } else if (!mustStartWithVerb && isVerbForm(firstWord)) {
            violations.add(new DocumentationViolation(REF_EXPLANATION_TITLE_MSG,
                String.format(PAGE_TITLE_CONTEXT, title), DocumentationViolationSeverity.WARNING));
        }
    }

    private void checkPageName(String pageName, boolean mustStartWithVerb, List<DocumentationViolation> violations)
    {
        String[] segments = pageName.split("-");
        if (segments.length == 0 || segments[0].isEmpty()) {
            return;
        }
        String firstSegment = segments[0].toLowerCase(Locale.ROOT);
        if (mustStartWithVerb && isNotImperativeVerb(firstSegment)) {
            violations.add(new DocumentationViolation(HOWTO_TUTORIAL_NAME_MSG,
                String.format(PAGE_NAME_CONTEXT, pageName), DocumentationViolationSeverity.WARNING));
        } else if (!mustStartWithVerb && isVerbForm(firstSegment)) {
            violations.add(new DocumentationViolation(REF_EXPLANATION_NAME_MSG,
                String.format(PAGE_NAME_CONTEXT, pageName), DocumentationViolationSeverity.WARNING));
        }
    }

    private boolean isNotImperativeVerb(String word)
    {
        return isVerbForm(word) || KebabNameValidator.STOP_WORDS.contains(word);
    }

    private boolean isVerbForm(String word)
    {
        return word.endsWith("ing");
    }
}
