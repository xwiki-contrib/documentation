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

import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.xwiki.administration.test.po.AdministrationSectionPage;
import org.xwiki.test.ui.po.SuggestInputElement;

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

    private static final By FALSE_POSITIVES_PAGE_FORM = By.cssSelector("form.docapp-false-positives-page");

    private static final By VIOLATION_GROUPS = By.cssSelector(".docapp-violation-groups");

    /**
     * The message of a rule is displayed in an inline warning or error box, next to the icon and the screen reader label
     * of its severity.
     */
    private static final By VIOLATION_GROUP_MESSAGES =
        By.cssSelector(".docapp-violation-group-message .box > span:not(.icon-block):not(.sr-only)");

    private static final By MARK_FALSE_POSITIVES_FORMS = By.cssSelector("form.docapp-mark-false-positives");

    private static final By MARK_FALSE_POSITIVES_MESSAGE = By.cssSelector(".docapp-mark-false-positives-done .box");

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

    /**
     * Lists the violations of a documentation page and of the pages located under it, grouped by rule, to mark them as
     * false positives.
     *
     * @param pageTitle the title of the page whose violations to list, picked with the page picker
     * @return the page object for the section, listing the violations
     */
    public DocumentationAdministrationSectionPage showViolations(String pageTitle)
    {
        WebElement form = getDriver().findElement(FALSE_POSITIVES_PAGE_FORM);
        SuggestInputElement pagePicker = new SuggestInputElement(form.findElement(By.id("docappFalsePositivesPage")));
        // Make sure the picker is ready.
        pagePicker.click().waitForSuggestions();
        pagePicker.sendKeys(pageTitle).waitForSuggestions().selectByVisibleText(pageTitle);
        form.findElement(By.cssSelector("button[type='submit']")).click();
        getDriver().waitUntilCondition(ExpectedConditions.stalenessOf(form));
        getDriver().waitUntilElementIsVisible(VIOLATION_GROUPS);
        return new DocumentationAdministrationSectionPage();
    }

    /**
     * @return the messages of the rules whose violations are listed (must call {@link #showViolations(String)} first),
     *     the rules with the most violations first
     */
    public List<String> getViolationGroupMessages()
    {
        return getDriver().findElementsWithoutWaiting(VIOLATION_GROUP_MESSAGES).stream()
            .map(WebElement::getText)
            .toList();
    }

    /**
     * Marks as false positives the listed violations of a rule (must call {@link #showViolations(String)} first), and
     * waits for them to be marked.
     *
     * @param groupIndex the index of the rule among the listed ones, starting at 0
     * @param reason why the violations are false positives
     * @return the page object for the section, displaying how many violations have been marked and listing the
     *     remaining ones
     */
    public DocumentationAdministrationSectionPage markFalsePositives(int groupIndex, String reason)
    {
        WebElement form = getDriver().findElements(MARK_FALSE_POSITIVES_FORMS).get(groupIndex);
        form.findElement(By.name("reason")).sendKeys(reason);
        form.findElement(By.cssSelector("button[type='submit']")).click();
        getDriver().waitUntilCondition(ExpectedConditions.stalenessOf(form));
        getDriver().waitUntilElementIsVisible(MARK_FALSE_POSITIVES_MESSAGE);
        return new DocumentationAdministrationSectionPage();
    }

    /**
     * @return the message telling that no violation is listed (must call {@link #showViolations(String)} first)
     */
    public String getNoViolationMessage()
    {
        return getDriver().findElement(By.cssSelector(".docapp-violation-groups > .box")).getText();
    }

    /**
     * @return the message telling how many violations have been marked as false positives
     */
    public String getMarkFalsePositivesMessage()
    {
        return getDriver().findElement(MARK_FALSE_POSITIVES_MESSAGE).getText();
    }
}
