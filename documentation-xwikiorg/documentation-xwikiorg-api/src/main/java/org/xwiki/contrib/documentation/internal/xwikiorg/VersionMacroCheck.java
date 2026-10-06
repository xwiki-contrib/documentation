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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.configuration.ConfigurationSource;
import org.xwiki.contrib.documentation.DocumentationViolation;
import org.xwiki.contrib.documentation.DocumentationViolationSeverity;
import org.xwiki.extension.version.Version;
import org.xwiki.extension.version.internal.DefaultVersion;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.block.match.ClassBlockMatcher;

import com.xpn.xwiki.doc.XWikiDocument;

/**
 * Verify that {@code {{version}}} macros about XWiki itself (i.e. without a {@code product} parameter or with it set
 * to {@code XWiki}, its default value) don't only
 * reference versions older than the oldest supported XWiki version, defined by the {@code oldestSupportedVersion}
 * property of the documentation configuration (editable in the wiki Administration). When that property isn't set,
 * nothing is checked.
 *
 * @version $Id$
 * @since 1.15
 */
@Component
@Singleton
@Named("versionMacro")
public class VersionMacroCheck extends AbstractXDOMDocumentationCheck
{
    static final String OLDEST_SUPPORTED_VERSION_PROPERTY = "oldestSupportedVersion";

    private static final String CHECK_NAME = "Version Macro";

    private static final String VERSION_MACRO_ID = "version";

    private static final String SINCE_PARAMETER = "since";

    private static final String BEFORE_PARAMETER = "before";

    private static final String PRODUCT_PARAMETER = "product";

    private static final String XWIKI_PRODUCT = "XWiki";

    @Inject
    @Named(DocumentationConfigurationSource.HINT)
    private ConfigurationSource configurationSource;

    @Override
    public List<DocumentationViolation> check(XWikiDocument document)
    {
        List<DocumentationViolation> violations = new ArrayList<>();
        String oldestSupportedVersionString =
            this.configurationSource.getProperty(OLDEST_SUPPORTED_VERSION_PROPERTY, String.class);
        if (StringUtils.isBlank(oldestSupportedVersionString)) {
            return violations;
        }
        Version oldestSupportedVersion = new DefaultVersion(oldestSupportedVersionString.trim());

        XDOM xdom = document.getXDOM();

        // Don't skip the version macro: its content is wiki content that can contain other version macros.
        checkXDOM(xdom, oldestSupportedVersion, violations);
        checkInsideWikiMacros(xdom, document, null, CHECK_NAME,
            macroXDOM -> checkXDOM(macroXDOM, oldestSupportedVersion, violations));

        XDOM faqXDOM = parseFAQXDOM(document, xdom, CHECK_NAME);
        if (faqXDOM != null) {
            checkXDOM(faqXDOM, oldestSupportedVersion, violations);
            checkInsideWikiMacros(faqXDOM, document, null, CHECK_NAME,
                macroXDOM -> checkXDOM(macroXDOM, oldestSupportedVersion, violations));
        }

        return violations;
    }

    private void checkXDOM(XDOM xdom, Version oldestSupportedVersion, List<DocumentationViolation> violations)
    {
        List<MacroBlock> macroBlocks = xdom.getBlocks(new ClassBlockMatcher(MacroBlock.class), Block.Axes.DESCENDANT);
        for (MacroBlock macroBlock : macroBlocks) {
            if (VERSION_MACRO_ID.equals(macroBlock.getId()) && isAboutXWiki(macroBlock)) {
                checkVersionMacro(macroBlock, oldestSupportedVersion, violations);
            }
        }
    }

    private boolean isAboutXWiki(MacroBlock macroBlock)
    {
        // The version macro uses XWiki as product when none is set. Other products (e.g. extensions) have their own
        // release cycle, to which the XWiki LTS cycle doesn't apply.
        String product = macroBlock.getParameter(PRODUCT_PARAMETER);
        return StringUtils.isBlank(product) || XWIKI_PRODUCT.equalsIgnoreCase(product.trim());
    }

    private void checkVersionMacro(MacroBlock macroBlock, Version oldestSupportedVersion,
        List<DocumentationViolation> violations)
    {
        // Same logic as the version macro: "since" is used when set, "before" otherwise.
        String parameterName = SINCE_PARAMETER;
        String versions = macroBlock.getParameter(SINCE_PARAMETER);
        if (StringUtils.isBlank(versions)) {
            parameterName = BEFORE_PARAMETER;
            versions = macroBlock.getParameter(BEFORE_PARAMETER);
        }
        if (StringUtils.isBlank(versions)) {
            return;
        }

        boolean allOlder = true;
        boolean hasVersion = false;
        for (String version : StringUtils.split(versions, ',')) {
            if (StringUtils.isNotBlank(version)) {
                hasVersion = true;
                if (new DefaultVersion(version.trim()).compareTo(oldestSupportedVersion) >= 0) {
                    allOlder = false;
                    break;
                }
            }
        }

        if (hasVersion && allOlder) {
            violations.add(new DocumentationViolation(
                "Version macro markers for versions older than the supported LTS cycle must be removed.",
                String.format("Version macro : %s=%s, oldest supported version : %s", parameterName,
                    versions.trim(), oldestSupportedVersion),
                DocumentationViolationSeverity.WARNING));
        }
    }
}
