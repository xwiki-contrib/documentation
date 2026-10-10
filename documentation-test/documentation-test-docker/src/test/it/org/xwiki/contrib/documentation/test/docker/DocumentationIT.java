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
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.openqa.selenium.By;
import org.xwiki.administration.test.po.AdministrationSectionPage;
import org.xwiki.contrib.documentation.test.po.DocumentationAdministrationSectionPage;
import org.xwiki.contrib.documentation.test.po.DocumentationViewPage;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.docker.junit5.UITest;
import org.xwiki.test.ui.TestUtils;
import org.xwiki.test.ui.po.ViewPage;
import org.xwiki.test.ui.po.editor.EditPage;
import org.xwiki.test.ui.po.editor.WikiEditPage;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    /**
     * The space holding the test pages. It is in the documentation tree, since documentation pages located anywhere
     * else are reported as an error by the location check.
     */
    private static final List<String> SPACE = List.of("documentation", "xs", "user", "documentation-it");

    private static final String DOC_CLASS = "DocApp.Code.DocumentationClass";

    private static final String UIXP_CLASS = "DocApp.Code.UIXPClass";

    /**
     * The violation-scenario page. It uses a "clean" page name and title (kebab-case, no reserved words, "reference"
     * type so the title needs no leading verb) so that the only violation reported is the one induced by its content,
     * making the on-page box severity predictable.
     */
    private static final String VIOLATION_PAGE = "image-sample";

    private static final String CONFIGURATION_CLASS = "DocApp.Code.DocumentationConfigurationClass";

    private static final String CONFIGURATION_PAGE = "DocApp.Code.DocumentationConfiguration";

    /**
     * The page holding a version macro older than the oldest supported version, once that version is configured.
     */
    private static final String VERSION_PAGE = "version-sample";

    private static final List<String> GUIDE_SPACE = space("guide");

    private static final DocumentReference GUIDE_REFERENCE = new DocumentReference("xwiki", GUIDE_SPACE, "WebHome");

    @BeforeAll
    static void setUp(TestUtils setup)
    {
        setup.loginAsSuperAdmin();

        // Violation scenario page: an Image macro missing its "alt" parameter is a single WARNING to start with. The
        // ordered tests below then edit this same page (raw image syntax -> ERROR, then fixed -> no violation). The
        // Image macro has a "size" parameter since, otherwise, it displays its own error box in the page content.
        setup.deletePage(page(VIOLATION_PAGE));
        setup.createPage(page(VIOLATION_PAGE), "{{image reference=\"foo.png\" size=\"small\"/}}", "Image sample",
            "xwiki/2.1");
        setup.addObject(page(VIOLATION_PAGE), DOC_CLASS, "type", "reference");

        // Structure + navigation page: a How-To with headings in its content and FAQ, a Related section and two
        // documentation children, so that the sheet structure, its table of contents and the "More" navigation section
        // can be asserted on a single page.
        setup.deletePage(GUIDE_REFERENCE, true);
        setup.createPage(GUIDE_SPACE, "WebHome", "== MyContentHeading ==\n\nMyContentText", "Guide", "xwiki/2.1");
        setup.addObject(GUIDE_REFERENCE, DOC_CLASS,
            "type", "howto",
            "faq", "== MyFaqQuestion ==\n\nMyFaqAnswerText",
            "related", "MyRelatedText");
        createChild(setup, "child-a", "Child A", "howto");
        createChild(setup, "child-b", "Child B", "reference");

        // A page WITHOUT a DocumentationClass object: the listener must not analyse it (even though its content would
        // otherwise violate the syntax check).
        setup.deletePage(page("plain-page"));
        setup.createPage(page("plain-page"), "image:foo.png", "Plain page", "xwiki/2.0");
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
        // properties.
        assertTrue(content.contains("Steps"), "Missing the type-specific 'Steps' heading in:\n" + content);
        assertTrue(content.contains("FAQ"), "Missing the 'FAQ' section heading in:\n" + content);
        assertTrue(content.contains("MyFaqAnswerText"), "Missing the FAQ content in:\n" + content);
        assertTrue(content.contains("Related"), "Missing the 'Related' section heading in:\n" + content);
        assertTrue(content.contains("MyRelatedText"), "Missing the Related content in:\n" + content);

        // The table of contents lists the headings of the page content and FAQ, next to the ones of the sheet.
        List<String> tocEntries = viewPage.getTableOfContentsEntries();
        assertTrue(tocEntries.containsAll(List.of("Steps", "MyContentHeading", "FAQ", "MyFaqQuestion")),
            "Missing headings in the table of contents: " + tocEntries);

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
        setup.gotoPage(page(VIOLATION_PAGE));
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
        WikiEditPage editPage = WikiEditPage.gotoPage(page(VIOLATION_PAGE));
        editPage.setContent("image:foo.png");
        editPage.clickSaveAndView();

        setup.gotoPage(page(VIOLATION_PAGE));
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
        WikiEditPage editPage = WikiEditPage.gotoPage(page(VIOLATION_PAGE));
        editPage.setContent("{{image reference=\"foo.png\" size=\"small\" alt=\"A foo\"/}}");
        editPage.clickSaveAndView();

        setup.gotoPage(page(VIOLATION_PAGE));
        DocumentationViewPage viewPage = new DocumentationViewPage();
        assertFalse(viewPage.hasWarningValidationBox(), "The warning box should be gone once the content is fixed");
        assertFalse(viewPage.hasErrorValidationBox(), "No validation box should remain once the content is fixed");
    }

    @Test
    @Order(6)
    void doesNotAnalysePageWithoutDocumentationClass(TestUtils setup)
    {
        setup.gotoPage(page("plain-page"));
        DocumentationViewPage viewPage = new DocumentationViewPage();
        assertFalse(viewPage.hasErrorValidationBox(), "A non-documentation page must not show a validation box");
        assertFalse(viewPage.hasWarningValidationBox(), "A non-documentation page must not show a validation box");
        assertFalse(viewPage.hasDocumentationTab(), "A non-documentation page must not show the Documentation tab");
    }

    @Test
    @Order(7)
    void deprecatedMacroWorksInline(TestUtils setup)
    {
        // The Deprecated macro is independent of the validation flow (no DocumentationClass needed). It is used both
        // inline (inside a sentence) and standalone (its own block) on the same page so that both renderings can be
        // asserted: the inline use must not error and must not produce the callout box, while the standalone use still
        // renders the box.
        String content = """
            A sentence with {{deprecated since="11.10.5" useInstead="the New Feature"}}an old feature{{/deprecated}} \
            used inline.

            {{deprecated since="11.10.5" useInstead="the New Feature"}}
            A standalone block.
            {{/deprecated}}""";
        setup.deletePage(page("deprecated-sample"));
        setup.createPage(page("deprecated-sample"), content, "Deprecated sample", "xwiki/2.1");

        setup.gotoPage(page("deprecated-sample"));
        DocumentationViewPage viewPage = new DocumentationViewPage();

        // Used inline, the macro must not produce the "standalone macro cannot be used inline" rendering error.
        assertFalse(viewPage.hasRenderingError(),
            "The deprecated macro used inline must not produce a rendering error");
        // The standalone usage still renders its callout box (the inline usage renders the badge without it).
        assertTrue(viewPage.hasDeprecationBox(), "The standalone deprecated macro should still render its box");
        String rendered = viewPage.getContent();
        assertTrue(rendered.contains("used inline."), "The inline sentence text should be rendered");
        assertTrue(rendered.contains("Deprecated XWiki 11.10.5. Use the New Feature instead."),
            "The deprecation badge text should be rendered");
    }

    @Test
    @Order(8)
    void rendersDocumentationFieldsWhenPageAlsoHasUixpObject(TestUtils setup)
    {
        // A page can carry both a DocumentationClass object (FAQ/Related/...) and a UIXPClass object (documenting an
        // extension point). The UIXP content extension displays the UIXPClass object's fields, but doing so must not
        // suppress the DocumentationClass FAQ/Related content rendered by the sheet.
        setup.deletePage(page("uixp-page"));
        setup.createPage(page("uixp-page"), "", "UIXP sample", "xwiki/2.1");
        setup.addObject(page("uixp-page"), DOC_CLASS,
            "type", "reference",
            "faq", "MyUixpFaqText",
            "related", "MyUixpRelatedText");
        setup.addObject(page("uixp-page"), UIXP_CLASS, "description", "MyUixpDescriptionText");

        setup.gotoPage(page("uixp-page"));
        String content = new DocumentationViewPage().getContent();

        // The UIXP object's structured content is rendered (replacing the free-form "content" field, by design).
        assertTrue(content.contains("MyUixpDescriptionText"), "Missing the UIXP description content in:\n" + content);
        // Regression guard for OP#116: the DocumentationClass FAQ/Related fields must still be displayed and not be
        // hidden by the UIXP object's presence.
        assertTrue(content.contains("MyUixpFaqText"), "Missing the FAQ content next to the UIXP object in:\n" + content);
        assertTrue(content.contains("MyUixpRelatedText"),
            "Missing the Related content next to the UIXP object in:\n" + content);
    }

    @Test
    @Order(9)
    void rendersTheAuthoredAltTextOnTheImage(TestUtils setup)
    {
        // The Image macro declares an "alt" parameter and ImageMacroAltCheck warns when it's missing, so the authored
        // text has to reach the rendered "alt" attribute (otherwise the renderer falls back to the file name, which
        // tells a screen reader nothing). The text carries the two characters that end a quoted image parameter in the
        // syntax the macro generates, a double quote and a closing square bracket, so it also covers the escaping:
        // unescaped, they truncate the alt text instead of breaking anything visibly.
        String alt = "The \"Cannot restore\" message [1] for a page";
        setup.deletePage(page("alt-sample"));
        setup.createPage(page("alt-sample"),
            "{{image reference=\"foo.png\" size=\"large\" alt=\"The ~\"Cannot restore~\" message [1] for a page\"/}}",
            "Alt sample", "xwiki/2.1");

        setup.gotoPage(page("alt-sample"));
        DocumentationViewPage viewPage = new DocumentationViewPage();
        assertFalse(viewPage.hasRenderingError(), "The Image macro should not produce a rendering error");
        assertEquals(alt, viewPage.getContentImageAlt(),
            "The rendered image should carry the authored alt text, not the image file name");
    }

    @Test
    @Order(10)
    void reportsOldVersionMacrosOnceConfiguredInAdministration(TestUtils setup)
    {
        // As long as the oldest supported version isn't configured, old version macros aren't reported.
        setup.deletePage(page(VERSION_PAGE));
        setup.createPage(page(VERSION_PAGE), "{{version since=\"12.0\"}}\nAn old feature.\n{{/version}}",
            "Version sample", "xwiki/2.1");
        setup.addObject(page(VERSION_PAGE), DOC_CLASS, "type", "reference");
        setup.gotoPage(page(VERSION_PAGE));
        assertFalse(new DocumentationViewPage().hasWarningValidationBox(),
            "No violation expected while the oldest supported version isn't configured");

        // Configure it live, from the Administration (no restart).
        setOldestSupportedVersion("16.10.0");

        // The next save of the page reports the version macro.
        WikiEditPage.gotoPage(page(VERSION_PAGE)).clickSaveAndView();
        setup.gotoPage(page(VERSION_PAGE));
        DocumentationViewPage viewPage = new DocumentationViewPage();
        assertTrue(viewPage.hasWarningValidationBox(), "Expected the on-page warning validation box to be displayed");
        viewPage.openDocumentationTab();
        assertTrue(viewPage.getDocumentationTabContent()
                .contains("Version macro markers for versions older than the supported LTS cycle must be removed."),
            "The Documentation tab should list the old version macro violation message");
    }

    @Test
    @Order(11)
    void recommendsHighlightsOnPagesWithManyChildPages(TestUtils setup)
    {
        // The highlights check counts the child documentation pages with a database query, which the unit tests can
        // only mock. The hub page gets 15 child documentation pages, nested and terminal, plus a child page that is not
        // a documentation page and must not count: being at the threshold, it gets no recommendation.
        List<String> hubSpace = space("hub-sample");
        DocumentReference hubReference = new DocumentReference("xwiki", hubSpace, "WebHome");
        setup.deletePage(hubReference, true);
        for (int i = 1; i <= 8; i++) {
            List<String> childSpace = space("hub-sample", "nested-" + i);
            setup.createPage(childSpace, "WebHome", "", "Nested " + i, "xwiki/2.1");
            setup.addObject(new DocumentReference("xwiki", childSpace, "WebHome"), DOC_CLASS, "type", "reference");
        }
        for (int i = 1; i <= 7; i++) {
            DocumentReference childReference = new DocumentReference("xwiki", hubSpace, "terminal-" + i);
            setup.createPage(childReference, "", "Terminal " + i, "xwiki/2.1");
            setup.addObject(childReference, DOC_CLASS, "type", "reference");
        }
        setup.createPage(new DocumentReference("xwiki", hubSpace, "plain-child"), "", "Plain child", "xwiki/2.1");
        setup.createPage(hubSpace, "WebHome", "", "Hub sample", "xwiki/2.1");
        setup.addObject(hubReference, DOC_CLASS, "type", "reference");

        setup.gotoPage(hubReference);
        assertFalse(new DocumentationViewPage().hasWarningValidationBox(),
            "No recommendation expected for a page with 15 child documentation pages");

        // A 16th child documentation page puts the hub over the threshold, reported on the next save of the hub.
        DocumentReference lastChildReference = new DocumentReference("xwiki", hubSpace, "terminal-8");
        setup.createPage(lastChildReference, "", "Terminal 8", "xwiki/2.1");
        setup.addObject(lastChildReference, DOC_CLASS, "type", "reference");
        WikiEditPage.gotoPage(hubReference).clickSaveAndView();

        setup.gotoPage(hubReference);
        DocumentationViewPage viewPage = new DocumentationViewPage();
        assertTrue(viewPage.hasWarningValidationBox(), "Expected the on-page warning validation box to be displayed");
        viewPage.openDocumentationTab();
        String tabContent = viewPage.getDocumentationTabContent();
        assertTrue(tabContent.contains("Highlights are recommended for pages with more than 15 child pages"),
            "The Documentation tab should list the Highlights recommendation, but got:\n" + tabContent);
        assertTrue(tabContent.contains("Child pages: [16]"),
            "The Documentation tab should give the number of child pages, but got:\n" + tabContent);
    }

    @Test
    @Order(12)
    void checksAllPagesFromTheAdministration(TestUtils setup)
    {
        // A page whose violations are out of date: its old version macro is only reported once the oldest supported
        // version is raised, which happens after the page was last saved.
        setOldestSupportedVersion("12.0");
        DocumentReference stalePage = page("stale-sample");
        setup.deletePage(stalePage);
        setup.createPage(stalePage, "{{version since=\"13.0\"}}\nAn old feature.\n{{/version}}", "Stale sample",
            "xwiki/2.1");
        setup.addObject(stalePage, DOC_CLASS, "type", "reference");
        setOldestSupportedVersion("16.10.0");
        setup.gotoPage(stalePage);
        assertFalse(new DocumentationViewPage().hasWarningValidationBox(),
            "No violation expected before the page is checked again");

        // Checking all pages from the Administration reports the violation, without having to save the page.
        String message = DocumentationAdministrationSectionPage.gotoPage().checkAllPages().getCheckAllPagesMessage();
        assertTrue(message.contains("All documentation pages have been checked."),
            "The check of all pages should succeed, but got:\n" + message);
        setup.gotoPage(stalePage);
        assertTrue(new DocumentationViewPage().hasWarningValidationBox(),
            "Expected the on-page warning validation box once all pages are checked");
    }

    @Test
    @Order(13)
    void linksToGitHubWithTheSCMMacro(TestUtils setup)
    {
        // The SCM macro is provided by the XWiki.org UI. Its parameters are optional and the defaults fill in what a
        // link to a path needs (the "xwiki-platform" project and the "master" branch). It is used inline, as in the
        // documentation pages, with and without a label.
        String content = """
            Links to {{scm/}}, {{scm project="xwiki-rendering"/}}, {{scm path="xwiki-platform-core/pom.xml"/}}, \
            {{scm user="xwiki-contrib" project="documentation" branch="stable-1.x" path="pom.xml"}}a **label**{{/scm}} \
            and {{scm project="xwiki-commons" path="pom.xml" raw="true"/}}.""";
        setup.deletePage(page("scm-sample"));
        setup.createPage(page("scm-sample"), content, "SCM sample", "xwiki/2.1");

        setup.gotoPage(page("scm-sample"));
        DocumentationViewPage viewPage = new DocumentationViewPage();
        assertFalse(viewPage.hasRenderingError(), "The SCM macro should not produce a rendering error");
        List<String> expectedTargets = List.of(
            "https://github.com/xwiki",
            "https://github.com/xwiki/xwiki-rendering",
            "https://github.com/xwiki/xwiki-platform/tree/master/xwiki-platform-core/pom.xml",
            "https://github.com/xwiki-contrib/documentation/tree/stable-1.x/pom.xml",
            "https://raw.githubusercontent.com/xwiki/xwiki-commons/master/pom.xml");
        assertEquals(expectedTargets, viewPage.getContentLinkTargets());
        // Without content, the label is the URL.
        assertEquals("https://github.com/xwiki/xwiki-rendering", viewPage.getContentLinkLabels().get(1));
        assertEquals("a label", viewPage.getContentLinkLabels().get(3));
    }

    @Test
    @Order(14)
    void marksViolationsAsFalsePositives(TestUtils setup) throws Exception
    {
        // A page whose only violation is a warning: an Image macro missing its "alt" parameter.
        List<String> falsePositiveSpace = space("false-positive");
        DocumentReference falsePositivePage = new DocumentReference("xwiki", falsePositiveSpace, "WebHome");
        setup.deletePage(falsePositivePage, true);
        setup.createPage(falsePositivePage, "{{image reference=\"foo.png\"/}}", "Image false positive",
            "xwiki/2.1");
        setup.addObject(falsePositivePage, DOC_CLASS, "type", "reference");

        // Marking the violation as a false positive stops reporting it.
        setup.gotoPage(falsePositivePage);
        DocumentationViewPage viewPage = new DocumentationViewPage().openDocumentationTab()
            .markViolationAsFalsePositive(0, "Decorative image");
        assertFalse(viewPage.hasWarningValidationBox(), "A violation marked as a false positive shouldn't be reported");
        String tabContent = viewPage.getDocumentationTabContent();
        assertTrue(tabContent.contains("Violations marked as false positives (1)"),
            "The Documentation tab should list the violation marked as a false positive, but got:\n" + tabContent);

        // Saving the page again keeps the violation marked, since its check still reports it.
        WikiEditPage editPage = WikiEditPage.gotoPage(falsePositivePage);
        editPage.setContent("{{image reference=\"foo.png\"/}}\n\nSome text.");
        editPage.clickSaveAndView();
        assertFalse(new DocumentationViewPage().hasWarningValidationBox(),
            "The violation should stay marked as a false positive once the page is saved again");

        // Reinstating the violation reports it again.
        viewPage = new DocumentationViewPage().openDocumentationTab().reinstateViolation(0);
        assertTrue(viewPage.hasWarningValidationBox(), "A reinstated violation should be reported again");

        // The violations of the same rule in the pages located under a page can be marked from the violation of that
        // page.
        DocumentReference childPage = createImagePage(setup, space("false-positive", "image-child"), "Image child");
        setup.gotoPage(falsePositivePage);
        viewPage = new DocumentationViewPage().openDocumentationTab()
            .markViolationAsFalsePositive(0, "Decorative images", "This page and all the pages under it");
        assertFalse(viewPage.hasWarningValidationBox(), "The violation of the page should be marked");
        setup.gotoPage(childPage);
        assertFalse(new DocumentationViewPage().hasWarningValidationBox(),
            "The violation of the page located under the page should be marked too");

        // They can also be marked from the Administration, which lists the violations of a page and of the pages
        // located under it, grouped by rule.
        DocumentReference otherChildPage =
            createImagePage(setup, space("false-positive", "image-sibling"), "Image sibling");
        DocumentationAdministrationSectionPage section =
            DocumentationAdministrationSectionPage.gotoPage().showViolations("Image false positive");
        String imageAltMessage = "Missing 'alt' parameter usage in the Image macro.";
        List<String> groupMessages = section.getViolationGroupMessages();
        assertTrue(groupMessages.contains(imageAltMessage),
            "The violation of the other page should be listed, but got:\n" + groupMessages);
        section = section.markFalsePositives(groupMessages.indexOf(imageAltMessage), "Decorative images");
        String message = section.getMarkFalsePositivesMessage();
        assertTrue(message.contains("Violations marked as false positives: 1."),
            "The violation of the other page should be marked, but got:\n" + message);
        assertFalse(section.getViolationGroupMessages().contains(imageAltMessage),
            "No violation of the rule should be left to mark");
        setup.gotoPage(otherChildPage);
        assertFalse(new DocumentationViewPage().hasWarningValidationBox(),
            "The violations marked from the Administration shouldn't be reported");

        // The violations marked as false positives are listed apart from the reported ones.
        String pageName = "documentation-it.false-positive.WebHome";
        assertTrue(getViolationResults(setup, "only").contains(pageName),
            "The violations marked as false positives should list the page");
        assertFalse(getViolationResults(setup, "exclude").contains(pageName),
            "The reported violations shouldn't list the page");
    }

    /**
     * Creates a documentation page whose only violation is a warning: an Image macro missing its "alt" parameter.
     */
    private static DocumentReference createImagePage(TestUtils setup, List<String> space, String title)
    {
        DocumentReference reference = new DocumentReference("xwiki", space, "WebHome");
        setup.deletePage(reference, true);
        setup.createPage(reference, "{{image reference=\"foo.png\"/}}", title, "xwiki/2.1");
        setup.addObject(reference, DOC_CLASS, "type", "reference");
        return reference;
    }

    /**
     * @param falsePositives "only" to get the violations marked as false positives, "exclude" to get the others
     * @return the JSON results of the Live Data listing the documentation violations
     */
    private static String getViolationResults(TestUtils setup, String falsePositives) throws Exception
    {
        return setup.executeAndGetBodyAsString(
            new DocumentReference("xwiki", List.of("DocApp", "Code"), "DocumentationLiveTableResults"),
            Map.of("outputSyntax", "plain", "classname", "DocApp.Code.DocumentationViolationClass", "collist",
                "message", "offset", "1", "limit", "1000", "reqNo", "1", "falsePositives", falsePositives));
    }

    private static void setOldestSupportedVersion(String version)
    {
        // The Administration form is identified by the page holding the ConfigurableClass object, not by the class.
        AdministrationSectionPage section = AdministrationSectionPage.gotoPage("documentation");
        section.getFormContainerElementForClass(CONFIGURATION_PAGE)
            .setFieldValue(By.name(CONFIGURATION_CLASS + "_0_oldestSupportedVersion"), version);
        section.clickSave();
    }

    private static DocumentReference page(String name)
    {
        return new DocumentReference("xwiki", SPACE, name);
    }

    private static List<String> space(String... names)
    {
        return Stream.concat(SPACE.stream(), Stream.of(names)).toList();
    }

    private static void createChild(TestUtils setup, String name, String title, String type)
    {
        List<String> childSpace = space("guide", name);
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
