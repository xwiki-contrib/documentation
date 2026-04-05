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

import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.documentation.DocumentationCheck;
import org.xwiki.contrib.documentation.DocumentationViolation;
import org.xwiki.contrib.documentation.DocumentationViolationSeverity;

import com.xpn.xwiki.doc.XWikiAttachment;
import com.xpn.xwiki.doc.XWikiDocument;

/**
 * Verify that video attachments on documentation pages use the {@code .webm} format. Any video file with a non-webm
 * extension (e.g. {@code .mp4}, {@code .mov}) triggers an ERROR violation.
 *
 * @version $Id$
 * @since 1.13
 */
@Component
@Singleton
@Named("videoAttachment")
public class VideoAttachmentCheck implements DocumentationCheck
{
    private static final String WEBM_EXTENSION = "webm";

    private static final Set<String> VIDEO_EXTENSIONS = Set.of(
        "mp4", "mov", "avi", "mkv", "flv", "wmv", WEBM_EXTENSION, "ogv", "m4v", "3gp", "ts", "mts", "m2ts"
    );

    @Override
    public List<DocumentationViolation> check(XWikiDocument document)
    {
        List<DocumentationViolation> violations = new ArrayList<>();
        for (XWikiAttachment attachment : document.getAttachmentList()) {
            String filename = attachment.getFilename();
            String extension = getExtension(filename);
            if (VIDEO_EXTENSIONS.contains(extension) && !WEBM_EXTENSION.equals(extension)) {
                violations.add(new DocumentationViolation(
                    "Video attachments must use the \".webm\" format.",
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
        return filename.substring(lastDot + 1).toLowerCase();
    }
}
