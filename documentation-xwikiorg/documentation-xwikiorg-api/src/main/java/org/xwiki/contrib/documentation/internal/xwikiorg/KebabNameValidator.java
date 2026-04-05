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

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

/**
 * Utility for validating and transforming kebab-style names used for pages and attachments.
 * <p>
 * This is a temporary copy of the core logic from {@code SlugEntityNameValidation} in XWiki Platform 18.1.0+
 * (without the configurable options), included here because this extension must support XWiki &lt; 18.1.0.
 * It will be removed once the minimum platform version is raised to 18.1.0+.
 * <p>
 * Transformation rules applied in order:
 * <ol>
 *   <li>Strip accents.</li>
 *   <li>Protect dots that appear between two digits (e.g. {@code 1.0}).</li>
 *   <li>Replace all remaining non-word characters with {@code -}.</li>
 *   <li>Restore protected dots.</li>
 *   <li>Convert to lowercase.</li>
 *   <li>Remove stop-word segments (whole hyphen-delimited segments that are stop words).</li>
 *   <li>Collapse consecutive {@code -} into one.</li>
 *   <li>Strip leading/trailing {@code -}.</li>
 * </ol>
 *
 * @version $Id$
 * @since 1.13
 */
public final class KebabNameValidator
{
    /**
     * Stop words to remove from page and attachment names (English function words that add no semantic value to a
     * kebab-case name). Each entry must be lowercase.
     */
    static final Set<String> STOP_WORDS = Set.of(
        "a", "about", "above", "after", "again", "against", "all", "am", "an", "and", "any", "are", "arent", "as",
        "at", "be", "because", "been", "before", "being", "below", "between", "both", "but", "by", "cant", "cannot",
        "could", "couldnt", "did", "didnt", "do", "does", "doesnt", "doing", "dont", "down", "during", "each", "few",
        "for", "from", "further", "had", "hadnt", "has", "hasnt", "have", "havent", "having", "he", "hed", "hes",
        "her", "here", "heres", "hers", "herself", "him", "himself", "his", "how", "hows", "i", "im", "ive", "if",
        "in", "into", "is", "isnt", "it", "its", "itself", "lets", "me", "more", "most", "mustnt", "my", "myself",
        "no", "nor", "not", "of", "off", "on", "once", "only", "or", "other", "ought", "our", "ours", "ourselves",
        "out", "over", "own", "same", "shant", "she", "shes", "should", "shouldnt", "so", "some", "such", "than",
        "that", "thats", "the", "their", "theirs", "them", "themselves", "then", "there", "theres", "these", "they",
        "theyd", "theyll", "theyre", "theyve", "this", "those", "through", "to", "too", "under", "until", "up",
        "very", "was", "wasnt", "we", "wed", "were", "weve", "werent", "what", "whats", "when", "whens", "where",
        "wheres", "which", "while", "who", "whos", "whom", "why", "whys", "with", "wont", "would", "wouldnt", "you",
        "youd", "youll", "youre", "youve", "your", "yours", "yourself", "yourselves"
    );

    private static final String REPLACEMENT_CHARACTER = "-";

    private static final Pattern DASH_PATTERN = Pattern.compile("-+");

    private static final Pattern NONWORD_PATTERN = Pattern.compile("\\W");

    /**
     * Matches a literal dot that is immediately preceded by a digit and immediately followed by a digit, so that
     * version numbers such as {@code 1.0} are preserved through the transformation.
     */
    private static final Pattern DOTSBETWEENDIGITS_PATTERN = Pattern.compile("(?<=\\d)\\.(?=\\d)");

    private static final String PROTECTED_DOT = "__DOT__";

    private KebabNameValidator()
    {
        // Utility class, not meant to be instantiated.
    }

    /**
     * @param name the name to validate
     * @return {@code true} if the name is already a valid kebab-case name (i.e., {@link #toKebab(String)} would
     *     return it unchanged)
     */
    public static boolean isValidKebab(String name)
    {
        return toKebab(name).equals(name);
    }

    /**
     * Transform an arbitrary name to its kebab-case form following the rules described in the class javadoc.
     *
     * @param name the name to transform
     * @return the kebab-case form of the name
     */
    public static String toKebab(String name)
    {
        // 1. Remove accents.
        String result = StringUtils.stripAccents(name);
        // 2. Protect dots between digits so they survive the non-word replacement step.
        result = DOTSBETWEENDIGITS_PATTERN.matcher(result).replaceAll(PROTECTED_DOT);
        // 3. Replace non-word characters (anything that is not [a-zA-Z0-9_]) with a hyphen.
        result = NONWORD_PATTERN.matcher(result).replaceAll(REPLACEMENT_CHARACTER);
        // 4. Restore protected dots.
        result = result.replace(PROTECTED_DOT, ".");
        // 5. Convert to lowercase.
        result = result.toLowerCase(Locale.ROOT);
        // 6. Collapse consecutive hyphens before splitting (prevents empty segments from double hyphens in input).
        result = DASH_PATTERN.matcher(result).replaceAll(REPLACEMENT_CHARACTER);
        // 7. Remove stop-word segments.
        result = removeStopWords(result);
        // 8. Collapse consecutive hyphens again (stop-word removal may produce adjacent hyphens).
        result = DASH_PATTERN.matcher(result).replaceAll(REPLACEMENT_CHARACTER);
        // 9. Remove leading and trailing hyphens.
        result = Strings.CS.removeEnd(result, REPLACEMENT_CHARACTER);
        result = Strings.CS.removeStart(result, REPLACEMENT_CHARACTER);
        return result;
    }

    private static String removeStopWords(String name)
    {
        String[] segments = name.split(REPLACEMENT_CHARACTER);
        StringBuilder filtered = new StringBuilder();
        for (String segment : segments) {
            if (!segment.isEmpty() && !STOP_WORDS.contains(segment)) {
                if (!filtered.isEmpty()) {
                    filtered.append(REPLACEMENT_CHARACTER);
                }
                filtered.append(segment);
            }
        }
        return filtered.toString();
    }
}
