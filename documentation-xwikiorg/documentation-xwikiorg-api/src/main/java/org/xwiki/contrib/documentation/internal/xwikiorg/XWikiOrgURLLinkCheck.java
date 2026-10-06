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
 * Verify that links to wiki pages of xwiki.org (and of its sub-wikis, e.g. {@code extensions.xwiki.org}) use page
 * references and not URLs, since URLs break when the URL format or the domain change. Links to the non-wiki services
 * of xwiki.org (e.g. {@code jira.xwiki.org} or {@code forum.xwiki.org}) are not reported.
 *
 * @version $Id$
 * @since 1.15
 */
@Component
@Singleton
@Named("xwikiOrgURLLink")
public class XWikiOrgURLLinkCheck extends AbstractXDOMDocumentationCheck
{
    private static final String CHECK_NAME = "XWiki.org URL Link";

    private static final String XWIKI_ORG_HOST = "xwiki.org";

    private static final List<String> WIKI_PATH_PREFIXES = List.of("/xwiki/bin/", "/xwiki/wiki/");

    @Override
    public List<DocumentationViolation> check(XWikiDocument document)
    {
        List<DocumentationViolation> violations = new ArrayList<>();
        XDOM xdom = document.getXDOM();

        checkXDOMAndWikiMacros(xdom, document, violations);
        checkXDOMAndWikiMacros(parseFAQXDOM(document, xdom, CHECK_NAME), document, violations);
        checkXDOMAndWikiMacros(parseXPropertyXDOM(document, xdom, "related", "Related", CHECK_NAME), document,
            violations);
        checkXDOMAndWikiMacros(parseXPropertyXDOM(document, xdom, "highlights", "Highlights", CHECK_NAME), document,
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
            if (ResourceType.URL.equals(reference.getType()) && isXWikiOrgWikiPageURL(reference.getReference())) {
                violations.add(new DocumentationViolation(
                    "Link to a wiki page of xwiki.org using a URL. Use a page reference instead.",
                    String.format("URL : %s", reference.getReference()), DocumentationViolationSeverity.ERROR));
            }
        }
    }

    private boolean isXWikiOrgWikiPageURL(String url)
    {
        try {
            // Note: java.net.URL is used instead of java.net.URI since it's lenient and also accepts URLs containing
            // unencoded characters (e.g. spaces), which are common in links written by hand.
            URL parsedURL = new URL(url);
            String host = parsedURL.getHost().toLowerCase(Locale.ROOT);
            String path = parsedURL.getPath();
            return (host.equals(XWIKI_ORG_HOST) || host.endsWith("." + XWIKI_ORG_HOST))
                && WIKI_PATH_PREFIXES.stream().anyMatch(path::startsWith);
        } catch (MalformedURLException e) {
            // Not a valid URL, thus not a link to a wiki page of xwiki.org.
            return false;
        }
    }
}
