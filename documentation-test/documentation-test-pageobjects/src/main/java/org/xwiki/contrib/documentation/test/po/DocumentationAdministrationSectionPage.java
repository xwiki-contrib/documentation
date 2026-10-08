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
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.xwiki.administration.test.po.AdministrationSectionPage;

/**
 * Represents the "Documentation" section of the wiki Administration, from which the wiki administrators check all the
 * documentation pages.
 *
 * @version $Id$
 */
public class DocumentationAdministrationSectionPage extends AdministrationSectionPage
{
    private static final String SECTION_ID = "documentation";

    private static final By START_BUTTON =
        By.cssSelector("form.docapp-check-all-pages button[value='start']");

    /**
     * The finish message of the check of all pages. The platform job macros render it as a message box, which replaces
     * the progress bar once the check is done.
     */
    private static final By CHECK_ALL_PAGES_MESSAGE = By.cssSelector(".docapp-check-all-pages-status > .box");

    /**
     * Default constructor.
     */
    public DocumentationAdministrationSectionPage()
    {
        super(SECTION_ID);
    }

    /**
     * Goes to the "Documentation" section of the wiki Administration.
     *
     * @return the page object for the section
     */
    public static DocumentationAdministrationSectionPage gotoPage()
    {
        getUtil().gotoPage(getURL(SECTION_ID));
        return new DocumentationAdministrationSectionPage();
    }

    /**
     * Checks all the documentation pages and waits for the check to be done.
     *
     * @return the page object for the section, displaying the result of the check
     */
    public DocumentationAdministrationSectionPage checkAllPages()
    {
        WebElement button = getDriver().findElement(START_BUTTON);
        button.click();
        // The form submission reloads the page, which displays the progress of the check until it's done. Wait for the
        // reload first, since the page may already display the message of a previous check.
        getDriver().waitUntilCondition(ExpectedConditions.stalenessOf(button));
        getDriver().waitUntilElementIsVisible(CHECK_ALL_PAGES_MESSAGE);
        return new DocumentationAdministrationSectionPage();
    }

    /**
     * @return the message telling the result of the last check of all the documentation pages
     */
    public String getCheckAllPagesMessage()
    {
        return getDriver().findElement(CHECK_ALL_PAGES_MESSAGE).getText();
    }
}
