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
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.ui.po.InlinePage;
import org.xwiki.test.ui.po.ViewPage;

/**
 * Represents the edit form of a documentation page, displayed by the documentation sheet in edit mode.
 *
 * @version $Id$
 */
public class DocumentationEditPage extends InlinePage
{
    private static final By FRAGMENT = By.cssSelector(".docapp-fragments .docapp-fragment");

    private static final By ADD_FRAGMENT_BUTTON = By.cssSelector(".docapp-fragment-add");

    private static final By FRAGMENT_NAME = By.cssSelector("input[name$='_name']");

    private static final By FRAGMENT_CONTENT = By.cssSelector("textarea[name$='_content']");

    private static final By FRAGMENT_USAGE = By.cssSelector(".docapp-fragment-usages a");

    private static final By FRAGMENT_REMOVE_CHECKBOX = By.cssSelector("input[name='deletedObjects']");

    /**
     * Opens the edit form of a documentation page.
     *
     * @param reference the documentation page to edit
     * @return the edit form of the page
     */
    public static DocumentationEditPage gotoPage(DocumentReference reference)
    {
        getUtil().gotoPage(reference, "edit", "editor=inline");
        return new DocumentationEditPage();
    }

    /**
     * Adds a content fragment to the page, created when the page is saved.
     *
     * @param name the name of the content fragment
     * @param content the wiki content of the content fragment
     * @return this page
     */
    public DocumentationEditPage addContentFragment(String name, String content)
    {
        int count = getFragments().size();
        // Scroll first, otherwise the floating save bar can hide the button.
        getDriver().scrollTo(getDriver().findElement(ADD_FRAGMENT_BUTTON)).click();
        getDriver().waitUntilCondition(driver -> getFragments().size() > count);
        WebElement fragment = getFragments().get(count);
        fragment.findElement(FRAGMENT_NAME).sendKeys(name);
        fragment.findElement(FRAGMENT_CONTENT).sendKeys(content);
        return this;
    }

    /**
     * @param name the name of a saved content fragment of the page
     * @return the titles of the pages using the content fragment, as listed below it
     */
    public List<String> getContentFragmentUsages(String name)
    {
        return getFragment(name).findElements(FRAGMENT_USAGE).stream().map(WebElement::getText).toList();
    }

    /**
     * Marks a saved content fragment of the page to be removed when the page is saved.
     *
     * @param name the name of the content fragment
     * @return this page
     */
    public DocumentationEditPage removeContentFragment(String name)
    {
        // Scroll first, otherwise the floating save bar can hide the checkbox.
        getDriver().scrollTo(getFragment(name).findElement(FRAGMENT_REMOVE_CHECKBOX)).click();
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected <T extends ViewPage> T createViewPage()
    {
        return (T) new DocumentationViewPage();
    }

    private List<WebElement> getFragments()
    {
        return getDriver().findElementsWithoutWaiting(FRAGMENT);
    }

    private WebElement getFragment(String name)
    {
        return getFragments().stream()
            .filter(fragment -> name.equals(fragment.findElement(FRAGMENT_NAME).getAttribute("value")))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No content fragment named [" + name + "]"));
    }
}
