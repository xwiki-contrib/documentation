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
package org.xwiki.contrib.documentation.test.po;

import org.openqa.selenium.By;
import org.xwiki.test.ui.po.ViewPage;

/**
 * Represents a documentation page in view mode, exposing access to the documentation-specific UI rendered by
 * {@code DocApp.Code.DocumentationSheet} (the on-page validation boxes, the "More" navigation section) and the
 * "Documentation" docextra tab contributed by {@code DocApp.Code.DocumentationDocextra}.
 *
 * @version $Id$
 */
public class DocumentationViewPage extends ViewPage
{
    /**
     * The docextra UI extension id (the {@code name} field of its {@code XWiki.UIExtensionClass} object). The platform
     * {@code docextra.vm} template derives the tab link id ({@code <id>link}) and the content pane id
     * ({@code <id>pane}) from this extension id. The id contains a dot, so it cannot be passed to {@link By#id(String)}
     * (which would treat the dot as a CSS class separator) and we use attribute selectors instead.
     */
    private static final String DOCEXTRA_ID = "documentation.tab";

    private static final By TAB_LINK = By.cssSelector("a[id='" + DOCEXTRA_ID + "link']");

    private static final By TAB_PANE = By.cssSelector("div[id='" + DOCEXTRA_ID + "pane']");

    /**
     * The tab content (the executed docextra UIX) wraps everything in a {@code documentationcontent} div, used to wait
     * for the asynchronous tab load to complete and to read the listed violation messages.
     */
    private static final By TAB_CONTENT = By.id("documentationcontent");

    private static final By ERROR_BOX = By.cssSelector("#xwikicontent .box.errormessage");

    private static final By WARNING_BOX = By.cssSelector("#xwikicontent .box.warningmessage");

    private static final By MORE_SEARCH_FORM = By.cssSelector("#xwikicontent form[action*='DocumentationSearch']");

    private static final By DEPRECATION_BOX = By.cssSelector("#xwikicontent .box.deprecationNotice");

    private static final By RENDERING_ERROR = By.cssSelector("#xwikicontent .xwikirenderingerror");

    /**
     * @return true if the on-page "at least one error" validation box is displayed
     */
    public boolean hasErrorValidationBox()
    {
        return getDriver().hasElementWithoutWaiting(ERROR_BOX);
    }

    /**
     * @return true if the on-page "at least one warning" validation box is displayed
     */
    public boolean hasWarningValidationBox()
    {
        return getDriver().hasElementWithoutWaiting(WARNING_BOX);
    }

    /**
     * @return true if the documentation search form rendered in the "More" navigation section is present
     */
    public boolean hasDocumentationSearchForm()
    {
        return getDriver().hasElementWithoutWaiting(MORE_SEARCH_FORM);
    }

    /**
     * @return true if the standalone deprecation callout box rendered by the Deprecated macro is present
     */
    public boolean hasDeprecationBox()
    {
        return getDriver().hasElementWithoutWaiting(DEPRECATION_BOX);
    }

    /**
     * @return true if a macro rendering error is displayed (e.g. a standalone macro wrongly used inline)
     */
    public boolean hasRenderingError()
    {
        return getDriver().hasElementWithoutWaiting(RENDERING_ERROR);
    }

    /**
     * @return true if the "Documentation" docextra tab is present (it's only shown for documentation pages)
     */
    public boolean hasDocumentationTab()
    {
        return getDriver().hasElementWithoutWaiting(TAB_LINK);
    }

    /**
     * Opens the "Documentation" docextra tab and waits for its (asynchronously loaded) content to be displayed.
     *
     * @return this page object
     */
    public DocumentationViewPage openDocumentationTab()
    {
        getDriver().findElement(TAB_LINK).click();
        getDriver().waitUntilElementIsVisible(TAB_PANE);
        getDriver().waitUntilElementIsVisible(TAB_CONTENT);
        return this;
    }

    /**
     * @return the text of the "Documentation" docextra tab content pane (must call {@link #openDocumentationTab()}
     *         first)
     */
    public String getDocumentationTabContent()
    {
        return getDriver().findElement(TAB_CONTENT).getText();
    }
}
