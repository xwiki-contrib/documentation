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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.documentation.DocumentationViolation;
import org.xwiki.contrib.documentation.DocumentationViolationSeverity;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.LinkBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.block.match.ClassBlockMatcher;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.listener.reference.ResourceType;

import com.xpn.xwiki.doc.XWikiDocument;

/**
 * Verify that links to files or directories of the XWiki repositories on GitHub (i.e. of the {@code xwiki} and
 * {@code xwiki-contrib} organizations) use the SCM macro instead of a URL, so that the SCM can be changed without
 * breaking all the links. Links to commits, pull requests or issues are not files and are not reported.
 *
 * @version $Id$
 * @since 1.15
 */
@Component
@Singleton
@Named("gitHubSCMLink")
public class GitHubSCMLinkCheck extends AbstractXDOMDocumentationCheck
{
    private static final String CHECK_NAME = "GitHub SCM Link";

    private static final Set<String> GITHUB_HOSTS = Set.of("github.com", "www.github.com");

    private static final Set<String> XWIKI_ORGANIZATIONS = Set.of("xwiki", "xwiki-contrib");

    private static final Set<String> FILE_PATH_TYPES = Set.of("blob", "tree");

    @Override
    public List<DocumentationViolation> check(XWikiDocument document)
    {
        List<DocumentationViolation> violations = new ArrayList<>();
        XDOM xdom = document.getXDOM();

        checkXDOMAndWikiMacros(xdom, document, violations);
        checkXDOMAndWikiMacros(parseFAQXDOM(document, xdom, CHECK_NAME), document, violations);
        checkXDOMAndWikiMacros(parseXPropertyXDOM(document, xdom, "related", "Related", CHECK_NAME), document,
            violations);

        return violations;
    }

    private void checkXDOMAndWikiMacros(XDOM xdom, XWikiDocument document, List<DocumentationViolation> violations)
    {
        if (xdom != null) {
            checkXDOM(xdom, violations);
            checkInsideWikiMacros(xdom, document, null, CHECK_NAME, macroXDOM -> checkXDOM(macroXDOM, violations));
        }
    }

    private void checkXDOM(XDOM xdom, List<DocumentationViolation> violations)
    {
        List<LinkBlock> linkBlocks = xdom.getBlocks(new ClassBlockMatcher(LinkBlock.class), Block.Axes.DESCENDANT);
        for (LinkBlock linkBlock : linkBlocks) {
            ResourceReference reference = linkBlock.getReference();
            if (ResourceType.URL.equals(reference.getType()) && isXWikiGitHubFileURL(reference.getReference())) {
                violations.add(new DocumentationViolation(
                    "Link to a file on GitHub using a URL. Use the SCM macro instead.",
                    String.format("URL : %s", reference.getReference()), DocumentationViolationSeverity.ERROR));
            }
        }
    }

    private boolean isXWikiGitHubFileURL(String url)
    {
        try {
            // Note: java.net.URL is used instead of java.net.URI since it's lenient and also accepts URLs containing
            // unencoded characters (e.g. spaces), which are common in links written by hand.
            URL parsedURL = new URL(url);
            if (!GITHUB_HOSTS.contains(parsedURL.getHost().toLowerCase(Locale.ROOT))) {
                return false;
            }
            // Expected path: /<organization>/<repository>/(blob|tree)/<branch>[/<path>]
            String[] segments = parsedURL.getPath().split("/");
            return segments.length >= 5 && XWIKI_ORGANIZATIONS.contains(segments[1].toLowerCase(Locale.ROOT))
                && !segments[2].isEmpty() && FILE_PATH_TYPES.contains(segments[3]);
        } catch (MalformedURLException e) {
            // Not a valid URL, thus not a link to a file on GitHub.
            return false;
        }
    }
}
