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
 * Verify that the page name of a documentation page follows the kebab-case naming convention (lowercase letters,
 * digits, and hyphens only — no spaces, accented characters, or other special characters).
 *
 * @version $Id$
 * @since 1.13
 */
@Component
@Singleton
@Named("pageName")
public class PageNameCheck implements DocumentationCheck
{
    @Override
    public List<DocumentationViolation> check(XWikiDocument document)
    {
        List<DocumentationViolation> violations = new ArrayList<>();
        String pageName = document.getDocumentReference().getName();
        if (!KebabNameValidator.isValidKebab(pageName)) {
            violations.add(new DocumentationViolation(
                "Page name must follow the kebab-case naming convention "
                    + "(lowercase, hyphens instead of spaces or special characters).",
                String.format("Page name: [%s], Expected: [%s]", pageName, KebabNameValidator.toKebab(pageName)),
                DocumentationViolationSeverity.ERROR));
        }
        return violations;
    }
}
