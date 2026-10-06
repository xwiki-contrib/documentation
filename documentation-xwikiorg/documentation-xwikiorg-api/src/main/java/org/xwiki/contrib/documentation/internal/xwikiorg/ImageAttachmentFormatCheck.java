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
import java.util.Set;

import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.documentation.DocumentationCheck;
import org.xwiki.contrib.documentation.DocumentationViolation;
import org.xwiki.contrib.documentation.DocumentationViolationSeverity;

import com.xpn.xwiki.doc.XWikiAttachment;
import com.xpn.xwiki.doc.XWikiDocument;

/**
 * Verify that image attachments on documentation pages use the {@code .png} format. Any raster image file with another
 * extension (e.g. {@code .jpg}, {@code .gif}) triggers an ERROR violation. SVG files are not reported since they're
 * not a screenshot format and are used for logos and icons.
 *
 * @version $Id$
 * @since 1.15
 */
@Component
@Singleton
@Named("imageAttachmentFormat")
public class ImageAttachmentFormatCheck implements DocumentationCheck
{
    private static final String GIF_EXTENSION = "gif";

    private static final Set<String> NON_PNG_IMAGE_EXTENSIONS = Set.of(
        "jpg", "jpeg", GIF_EXTENSION, "bmp", "tif", "tiff", "webp"
    );

    private static final String MESSAGE = "Image attachments must use the \".png\" format.";

    private static final String GIF_MESSAGE = MESSAGE
        + " Avoid animated GIFs, which are difficult to maintain: replace them with several PNGs.";

    @Override
    public List<DocumentationViolation> check(XWikiDocument document)
    {
        List<DocumentationViolation> violations = new ArrayList<>();
        for (XWikiAttachment attachment : document.getAttachmentList()) {
            String filename = attachment.getFilename();
            String extension = getExtension(filename);
            if (NON_PNG_IMAGE_EXTENSIONS.contains(extension)) {
                violations.add(new DocumentationViolation(
                    GIF_EXTENSION.equals(extension) ? GIF_MESSAGE : MESSAGE,
                    String.format("Attachment name: [%s]", filename),
                    DocumentationViolationSeverity.ERROR));
            }
        }
        return violations;
    }

    private String getExtension(String filename)
    {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1) {
            return "";
        }
        return filename.substring(lastDot + 1).toLowerCase(Locale.ROOT);
    }
}
