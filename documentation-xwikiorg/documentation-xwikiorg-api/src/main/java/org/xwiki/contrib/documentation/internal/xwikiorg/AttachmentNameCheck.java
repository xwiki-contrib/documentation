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

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.documentation.DocumentationCheck;
import org.xwiki.contrib.documentation.DocumentationViolation;
import org.xwiki.contrib.documentation.DocumentationViolationSeverity;

import com.xpn.xwiki.doc.XWikiAttachment;
import com.xpn.xwiki.doc.XWikiDocument;

/**
 * Verify that attachment filenames on documentation pages follow the kebab-case naming convention. The stem (part
 * before the last {@code .}) must be a valid slug, and the extension (part after the last {@code .}) must contain only
 * lowercase letters and digits.
 *
 * @version $Id$
 * @since 1.13
 */
@Component
@Singleton
@Named("attachmentName")
public class AttachmentNameCheck implements DocumentationCheck
{
    private static final Pattern LOWERCASE_EXTENSION_PATTERN = Pattern.compile("[a-z0-9]+");

    @Override
    public List<DocumentationViolation> check(XWikiDocument document)
    {
        List<DocumentationViolation> violations = new ArrayList<>();
        for (XWikiAttachment attachment : document.getAttachmentList()) {
            String filename = attachment.getFilename();
            if (!isValidAttachmentName(filename)) {
                violations.add(new DocumentationViolation(
                    "Attachment name must follow the kebab-case naming convention "
                        + "(lowercase, hyphens instead of spaces or special characters).",
                    String.format("Attachment name: [%s], Expected: [%s]", filename,
                        toExpectedAttachmentName(filename)),
                    DocumentationViolationSeverity.ERROR));
            }
        }
        return violations;
    }

    private boolean isValidAttachmentName(String filename)
    {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1) {
            return KebabNameValidator.isValidKebab(filename);
        }
        String stem = filename.substring(0, lastDot);
        String extension = filename.substring(lastDot + 1);
        return !stem.isEmpty()
            && KebabNameValidator.isValidKebab(stem)
            && LOWERCASE_EXTENSION_PATTERN.matcher(extension).matches();
    }

    private String toExpectedAttachmentName(String filename)
    {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1) {
            return KebabNameValidator.toKebab(filename);
        }
        String stem = filename.substring(0, lastDot);
        String extension = filename.substring(lastDot + 1);
        return KebabNameValidator.toKebab(stem) + "." + extension.toLowerCase();
    }
}
