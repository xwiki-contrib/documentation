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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.documentation.DocumentationCheck;
import org.xwiki.contrib.documentation.DocumentationViolation;
import org.xwiki.contrib.documentation.DocumentationViolationSeverity;

import com.xpn.xwiki.doc.XWikiDocument;

/**
 * Verify that if there are more than 1 image macro next to each other, they should be replaced by the Gallery macro.
 *
 * @version $Id$
 * @since 1.0
 */
@Component
@Singleton
@Named("imageGallery")
public class ImageGalleryCheck implements DocumentationCheck
{
    private static final Pattern PATTERN =
        Pattern.compile(".*(\\{\\{image.*}}[\\s\\n\\r]*\\{\\{image.*}}).*", Pattern.DOTALL);

    @Override
    public List<DocumentationViolation> check(XWikiDocument document)
    {
        List<DocumentationViolation> violations = new ArrayList<>();
        Matcher matcher = PATTERN.matcher(document.getContent());
        if (matcher.matches()) {
            violations.add(new DocumentationViolation("Use the Gallery macro when several images are displayed next "
                + "to each other.", matcher.group(1), DocumentationViolationSeverity.ERROR));
        }
        return violations;
    }
}
