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
package org.xwiki.contrib.documentation.test.docker;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.xwiki.contrib.documentation.test.po.DocumentationViewPage;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.docker.junit5.UITest;
import org.xwiki.test.ui.TestUtils;
import org.xwiki.test.ui.po.ViewPage;
import org.xwiki.test.ui.po.editor.EditPage;
import org.xwiki.test.ui.po.editor.WikiEditPage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Single end-to-end scenario verifying the documented, user-facing features of the Documentation Application that the
 * unit tests cannot cover: creating pages from the documentation templates, the page structure rendered by the sheet
 * (type heading, FAQ, Related, "More" navigation) and the documentation-validation flow (violations surfaced in the
 * on-page box and the "Documentation" docextra tab, and removed once the content is fixed). It deliberately does NOT
 * re-test the individual checks' logic, which the unit tests already cover.
 * <p>
 * This is a single ordered scenario (one {@code @UITest} class, ordered {@code @Test} methods sharing one XWiki
 * instance and one fixture) so that the (expensive) test setup runs only once.
 *
 * @version $Id$
 */
@UITest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DocumentationIT
{
    private static final String SPACE = "DocumentationIT";

    private static final String DOC_CLASS = "DocApp.Code.DocumentationClass";

    /**
     * The violation-scenario page. It uses a "clean" page name and title (kebab-case, no reserved words, "reference"
     * type so the title needs no leading verb) so that the only violation reported is the one induced by its content,
     * making the on-page box severity predictable.
     */
    private static final String VIOLATION_PAGE = "image-sample";

    private static final List<String> GUIDE_SPACE = List.of(SPACE, "guide");

    private static final DocumentReference GUIDE_REFERENCE = new DocumentReference("xwiki", GUIDE_SPACE, "WebHome");

    @BeforeAll
    static void setUp(TestUtils setup)
    {
        setup.loginAsSuperAdmin();

        // Violation scenario page: an Image macro missing its "alt" parameter is a single WARNING to start with. The
        // ordered tests below then edit this same page (raw image syntax -> ERROR, then fixed -> no violation).
        setup.deletePage(SPACE, VIOLATION_PAGE);
        setup.createPage(SPACE, VIOLATION_PAGE, "{{image reference=\"foo.png\"/}}", "Image sample", "xwiki/2.1");
        setup.addObject(SPACE, VIOLATION_PAGE, DOC_CLASS, "type", "reference");

        // Structure + navigation page: a How-To with FAQ and Related sections and two documentation children, so that
        // both the sheet structure and the "More" navigation section can be asserted on a single page.
        setup.deletePage(GUIDE_REFERENCE, true);
        setup.createPage(GUIDE_SPACE, "WebHome", "", "Guide", "xwiki/2.1");
        setup.addObject(GUIDE_REFERENCE, DOC_CLASS,
            "type", "howto",
            "faq", "MyFaqAnswerText",
            "related", "MyRelatedText");
        createChild(setup, "child-a", "Child A", "howto");
        createChild(setup, "child-b", "Child B", "reference");

        // A page WITHOUT a DocumentationClass object: the listener must not analyse it (even though its content would
        // otherwise violate the syntax check).
        setup.deletePage(SPACE, "plain-page");
        setup.createPage(SPACE, "plain-page", "image:foo.png", "Plain page", "xwiki/2.0");
    }

    @Test
    @Order(1)
    void createsPagesFromDocumentationTemplates(TestUtils setup)
    {
        // Each documentation template sets a documentation type, materialised by the type-specific main content heading
        // rendered by the sheet (Tutorial / Steps / Reference / Explanation).
        assertCreatedFromTemplate(setup, "DocApp.Code.TutorialDocumentationTemplateProvider", "CreateTutorial",
            "Tutorial");
        assertCreatedFromTemplate(setup, "DocApp.Code.HowtoDocumentationTemplateProvider", "CreateHowTo", "Steps");
        assertCreatedFromTemplate(setup, "DocApp.Code.ReferenceDocumentationTemplateProvider", "CreateReference",
            "Reference");
        assertCreatedFromTemplate(setup, "DocApp.Code.ExplanationDocumentationTemplateProvider", "CreateExplanation",
            "Explanation");
    }

    @Test
    @Order(2)
    void rendersDocumentationStructureAndNavigation(TestUtils setup)
    {
        setup.gotoPage(GUIDE_REFERENCE);
        DocumentationViewPage viewPage = new DocumentationViewPage();
        String content = viewPage.getContent();

        // The sheet renders the type-specific heading and the FAQ/Related sections from the DocumentationClass object
        // properties (not from the document body, which the sheet does not display).
        assertTrue(content.contains("Steps"), "Missing the type-specific 'Steps' heading in:\n" + content);
        assertTrue(content.contains("FAQ"), "Missing the 'FAQ' section heading in:\n" + content);
        assertTrue(content.contains("MyFaqAnswerText"), "Missing the FAQ content in:\n" + content);
        assertTrue(content.contains("Related"), "Missing the 'Related' section heading in:\n" + content);
        assertTrue(content.contains("MyRelatedText"), "Missing the Related content in:\n" + content);

        // The "More" navigation section (heading + search form) is rendered once the page has documentation children.
        // The children themselves are listed by an asynchronous Live Data table, not asserted here to keep the test
        // independent of Live Data timing.
        assertTrue(content.contains("More"), "Missing the 'More' navigation section heading in:\n" + content);
        assertTrue(viewPage.hasDocumentationSearchForm(),
            "Missing the documentation search form in the 'More' section");
    }

    @Test
    @Order(3)
    void surfacesWarningViolationInBoxAndTab(TestUtils setup)
    {
        setup.gotoPage(SPACE, VIOLATION_PAGE);
        DocumentationViewPage viewPage = new DocumentationViewPage();

        assertTrue(viewPage.hasWarningValidationBox(), "Expected the on-page warning validation box to be displayed");
        assertFalse(viewPage.hasErrorValidationBox(), "No error box expected for an alt-only warning");

        viewPage.openDocumentationTab();
        assertTrue(viewPage.getDocumentationTabContent().contains("Missing 'alt' parameter usage in the Image macro."),
            "The Documentation tab should list the missing-alt violation message");
    }

    @Test
    @Order(4)
    void surfacesErrorViolationAfterEdit(TestUtils setup)
    {
        // Using the raw image syntax (instead of the Image macro) is an ERROR. Re-saving re-runs the analysis.
        WikiEditPage editPage = WikiEditPage.gotoPage(SPACE, VIOLATION_PAGE);
        editPage.setContent("image:foo.png");
        editPage.clickSaveAndView();

        setup.gotoPage(SPACE, VIOLATION_PAGE);
        DocumentationViewPage viewPage = new DocumentationViewPage();
        assertTrue(viewPage.hasErrorValidationBox(), "Expected the on-page error validation box to be displayed");

        viewPage.openDocumentationTab();
        assertTrue(viewPage.getDocumentationTabContent().contains("Use the Image macro instead."),
            "The Documentation tab should list the raw-image-syntax violation message");
    }

    @Test
    @Order(5)
    void removesViolationsWhenContentIsFixed(TestUtils setup)
    {
        // Fixing the content (a proper Image macro with an alt parameter) makes the analysis remove the violations.
        WikiEditPage editPage = WikiEditPage.gotoPage(SPACE, VIOLATION_PAGE);
        editPage.setContent("{{image reference=\"foo.png\" alt=\"A foo\"/}}");
        editPage.clickSaveAndView();

        setup.gotoPage(SPACE, VIOLATION_PAGE);
        DocumentationViewPage viewPage = new DocumentationViewPage();
        assertFalse(viewPage.hasWarningValidationBox(), "The warning box should be gone once the content is fixed");
        assertFalse(viewPage.hasErrorValidationBox(), "No validation box should remain once the content is fixed");
    }

    @Test
    @Order(6)
    void doesNotAnalysePageWithoutDocumentationClass(TestUtils setup)
    {
        setup.gotoPage(SPACE, "plain-page");
        DocumentationViewPage viewPage = new DocumentationViewPage();
        assertFalse(viewPage.hasErrorValidationBox(), "A non-documentation page must not show a validation box");
        assertFalse(viewPage.hasWarningValidationBox(), "A non-documentation page must not show a validation box");
        assertFalse(viewPage.hasDocumentationTab(), "A non-documentation page must not show the Documentation tab");
    }

    private static void createChild(TestUtils setup, String name, String title, String type)
    {
        List<String> childSpace = List.of(SPACE, "guide", name);
        setup.createPage(childSpace, "WebHome", "Child content", title, "xwiki/2.1");
        setup.addObject(new DocumentReference("xwiki", childSpace, "WebHome"), DOC_CLASS, "type", type);
    }

    /**
     * Creates a page from the given documentation template provider and asserts that the type-specific main content
     * heading is rendered in view mode (which proves the template set the expected documentation type).
     */
    private void assertCreatedFromTemplate(TestUtils setup, String templateProvider, String pageName,
        String expectedHeading)
    {
        // The documentation templates are non-terminal, so the new page is created at Main.<pageName>.WebHome.
        DocumentReference pageReference = new DocumentReference("xwiki", List.of("Main", pageName), "WebHome");
        setup.deletePage(pageReference, true);

        // Drive the create action directly via its URL rather than the create form: the form's parent and name fields
        // are read-only in this XWiki version and the name does not auto-fill from the title. TestUtils.gotoPage adds
        // the required CSRF token. With a (non-terminal) template provider and no "tocreate", the action creates
        // Main.<pageName>.WebHome and redirects to its edit mode.
        setup.gotoPage("Main", "WebHome", "create",
            "spaceReference=Main&name=" + pageName + "&templateprovider=" + templateProvider);
        ViewPage viewPage = new EditPage().clickSaveAndView();

        String content = viewPage.getContent();
        assertTrue(content.contains(expectedHeading),
            String.format("The page created from template [%s] should render the [%s] heading but got:%n%s",
                templateProvider, expectedHeading, content));
    }
}
