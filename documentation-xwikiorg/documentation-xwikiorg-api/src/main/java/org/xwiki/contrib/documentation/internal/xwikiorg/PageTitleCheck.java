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
 * Verify that the page title of a documentation page is not empty and does not contain reserved
 * documentation-type words (explanation, howto, reference, tutorial).
 *
 * @version $Id$
 * @since 1.13
 */
@Component
@Singleton
@Named("pageTitle")
public class PageTitleCheck implements DocumentationCheck
{
    private static final String PAGE_TITLE_CONTEXT = "Page title: [%s]";

    @Override
    public List<DocumentationViolation> check(XWikiDocument document)
    {
        List<DocumentationViolation> violations = new ArrayList<>();
        String title = document.getTitle();
        if (title == null || title.isBlank()) {
            violations.add(new DocumentationViolation(
                "Page title must not be empty.",
                String.format(PAGE_TITLE_CONTEXT, ""),
                DocumentationViolationSeverity.ERROR));
        } else if (KebabNameValidator.containsReservedWord(title)) {
            violations.add(new DocumentationViolation(
                "Page title must not contain documentation-type words "
                    + "(explanation, howto, reference, tutorial).",
                String.format(PAGE_TITLE_CONTEXT, title),
                DocumentationViolationSeverity.ERROR));
        }
        return violations;
    }
}
