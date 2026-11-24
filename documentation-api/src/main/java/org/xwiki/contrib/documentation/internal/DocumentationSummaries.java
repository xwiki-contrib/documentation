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
package org.xwiki.contrib.documentation.internal;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.security.authorization.ContextualAuthorizationManager;
import org.xwiki.security.authorization.Right;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.objects.BaseObject;

/**
 * Gives access to the summary of documentation pages: the {@code summary} property of their
 * {@code DocApp.Code.DocumentationClass} object, one plain-text sentence saying what the page gives beyond its title.
 *
 * @version $Id$
 * @since 1.21
 */
@Component(roles = DocumentationSummaries.class)
@Singleton
public class DocumentationSummaries
{
    /**
     * The space holding the classes of the documentation application.
     */
    static final List<String> CODE_SPACE = List.of("DocApp", "Code");

    static final LocalDocumentReference DOCUMENTATION_CLASS = new LocalDocumentReference(CODE_SPACE,
        "DocumentationClass");

    @Inject
    private Logger logger;

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Inject
    private ContextualAuthorizationManager authorization;

    /**
     * Returns the summary of a documentation page, if the current user can view that page.
     *
     * @param documentReference the reference of the documentation page, for example
     *     {@code xwiki:documentation.xs.user.formatting.WebHome}
     * @return the trimmed summary of the page, or an empty string when the current user can't view the page, when it
     *     doesn't exist, isn't a documentation page, has no summary, or when it can't be loaded (a warning is then
     *     logged)
     */
    public String getSummary(DocumentReference documentReference)
    {
        if (!this.authorization.hasAccess(Right.VIEW, documentReference)) {
            return "";
        }
        return getSummaryWithoutRightCheck(documentReference);
    }

    /**
     * Returns the summary of a documentation page, whatever the rights of the current user. Meant for the
     * documentation checks, whose result is saved with the page and so must not depend on who saves it; never display
     * the returned summary.
     *
     * @param documentReference the reference of the documentation page, for example
     *     {@code xwiki:documentation.xs.user.formatting.WebHome}
     * @return the trimmed summary of the page, or an empty string when it doesn't exist, isn't a documentation page,
     *     has no summary, or when it can't be loaded (a warning is then logged)
     */
    public String getSummaryWithoutRightCheck(DocumentReference documentReference)
    {
        XWikiContext xcontext = this.xcontextProvider.get();
        try {
            BaseObject object =
                xcontext.getWiki().getDocument(documentReference, xcontext).getXObject(DOCUMENTATION_CLASS);
            return object != null ? StringUtils.trimToEmpty(object.getStringValue("summary")) : "";
        } catch (XWikiException e) {
            this.logger.warn("Failed to get the summary of the documentation page [{}]. Root cause: [{}]",
                documentReference, ExceptionUtils.getRootCauseMessage(e));
            return "";
        }
    }
}
