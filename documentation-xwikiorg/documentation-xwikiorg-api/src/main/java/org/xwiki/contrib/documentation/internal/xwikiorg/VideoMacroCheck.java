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
import org.xwiki.contrib.documentation.DocumentationViolation;
import org.xwiki.contrib.documentation.DocumentationViolationSeverity;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.block.match.ClassBlockMatcher;

import com.xpn.xwiki.doc.XWikiDocument;

/**
 * Verify that documentation pages with video attachments use the {@code {{embed}}} macro instead of the
 * {@code {{video}}} macro. If a video file attachment (e.g., {@code .mp4}, {@code .webm}) is present and the
 * {@code video} macro is used in the content, an ERROR violation is raised.
 *
 * @version $Id$
 * @since 1.14
 */
@Component
@Singleton
@Named("videoMacro")
public class VideoMacroCheck extends AbstractXDOMDocumentationCheck
{
    private static final String VIDEO_MACRO_ID = "video";

    private static final Set<String> VIDEO_EXTENSIONS = Set.of(
        "mp4", "mov", "avi", "mkv", "flv", "wmv", "webm", "ogv", "m4v", "3gp", "ts", "mts", "m2ts"
    );

    @Override
    public List<DocumentationViolation> check(XWikiDocument document)
    {
        boolean hasVideoAttachment = document.getAttachmentList().stream()
            .anyMatch(attachment -> isVideoFile(attachment.getFilename()));

        if (!hasVideoAttachment) {
            return List.of();
        }

        List<DocumentationViolation> violations = new ArrayList<>();
        XDOM xdom = document.getXDOM();

        checkXDOM(xdom, violations);
        checkInsideWikiMacros(xdom, document, VIDEO_MACRO_ID, "Video Macro",
            macroXDOM -> checkXDOM(macroXDOM, violations));

        return violations;
    }

    private void checkXDOM(XDOM xdom, List<DocumentationViolation> violations)
    {
        List<MacroBlock> macroBlocks =
            xdom.getBlocks(new ClassBlockMatcher(MacroBlock.class), Block.Axes.DESCENDANT);
        for (MacroBlock macroBlock : macroBlocks) {
            if (VIDEO_MACRO_ID.equals(macroBlock.getId())) {
                violations.add(new DocumentationViolation(
                    "Use the Embed macro instead of the Video macro.",
                    String.format("Macro parameters: [%s]", macroBlock.getParameters()),
                    DocumentationViolationSeverity.ERROR));
            }
        }
    }

    private boolean isVideoFile(String filename)
    {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1) {
            return false;
        }
        return VIDEO_EXTENSIONS.contains(filename.substring(lastDot + 1).toLowerCase());
    }
}
