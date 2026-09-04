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
import java.util.function.UnaryOperator;
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
 * Verify that attachment filenames on documentation pages follow the naming conventions. The stem (part before the
 * last {@code .}) must be in kebab-case, must not hold English stop words and must not hold a documentation-type
 * word; the extension (part after the last {@code .}) must contain only lowercase letters and digits. Each rule is
 * reported on its own so that a name is never asked to fix something it does not break.
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

    private static final String ATTACHMENT_NAME_CONTEXT = "Attachment name: [%s], Expected: [%s]";

    private static final String ATTACHMENT_STOP_WORD_CONTEXT =
        "Attachment name: [%s], Stop words: [%s], Expected: [%s]";

    @Override
    public List<DocumentationViolation> check(XWikiDocument document)
    {
        List<DocumentationViolation> violations = new ArrayList<>();
        for (XWikiAttachment attachment : document.getAttachmentList()) {
            checkFilename(attachment.getFilename(), violations);
        }
        return violations;
    }

    private void checkFilename(String filename, List<DocumentationViolation> violations)
    {
        if (!isValidAttachmentName(filename)) {
            violations.add(new DocumentationViolation(
                "Attachment name must follow the kebab-case naming convention "
                    + "(lowercase, hyphens instead of spaces or special characters).",
                String.format(ATTACHMENT_NAME_CONTEXT, filename,
                    rename(filename, KebabNameValidator::toKebab)),
                DocumentationViolationSeverity.ERROR));
            return;
        }
        List<String> stopWords = KebabNameValidator.getStopWords(getStem(filename));
        if (!stopWords.isEmpty()) {
            violations.add(new DocumentationViolation(
                "Attachment name should not contain English stop words (articles, conjunctions, prepositions "
                    + "and auxiliaries), which add length without adding meaning.",
                String.format(ATTACHMENT_STOP_WORD_CONTEXT, filename, String.join(", ", stopWords),
                    rename(filename, KebabNameValidator::removeStopWords)),
                DocumentationViolationSeverity.WARNING));
        }
        if (KebabNameValidator.containsReservedWord(getStem(filename))) {
            violations.add(new DocumentationViolation(
                "Attachment name must not contain documentation-type words "
                    + "(explanation, howto, reference, tutorial).",
                String.format(ATTACHMENT_NAME_CONTEXT, filename,
                    rename(filename, KebabNameValidator::removeReservedWords)),
                DocumentationViolationSeverity.WARNING));
        }
    }

    private boolean isValidAttachmentName(String filename)
    {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1) {
            return KebabNameValidator.isValidKebab(filename);
        }
        String extension = filename.substring(lastDot + 1);
        return KebabNameValidator.isValidKebab(filename.substring(0, lastDot))
            && LOWERCASE_EXTENSION_PATTERN.matcher(extension).matches();
    }

    private String getStem(String filename)
    {
        int lastDot = filename.lastIndexOf('.');
        return lastDot == -1 ? filename : filename.substring(0, lastDot);
    }

    /**
     * @param filename the attachment filename to rewrite
     * @param stemTransformation the transformation to apply to the stem
     * @return the filename with its stem transformed and its extension lowercased
     */
    private String rename(String filename, UnaryOperator<String> stemTransformation)
    {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1) {
            return stemTransformation.apply(filename);
        }
        String extension = filename.substring(lastDot + 1);
        return stemTransformation.apply(filename.substring(0, lastDot)) + "."
            + extension.toLowerCase(Locale.ROOT);
    }
}
