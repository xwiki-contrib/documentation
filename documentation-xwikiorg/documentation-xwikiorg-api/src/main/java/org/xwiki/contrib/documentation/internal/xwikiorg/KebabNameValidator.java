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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

/**
 * Utility for validating and transforming kebab-style names used for pages and attachments.
 * <p>
 * Three independent naming rules are exposed, each with its own predicate and its own transformation, so that a
 * caller can report one without implying the others:
 * <ul>
 *   <li><strong>kebab-case shape</strong> — {@link #isValidKebab(String)} / {@link #toKebab(String)}: lowercase
 *       letters, digits and single inner hyphens, plus dots between digits so that version numbers such as
 *       {@code 1.2.3} are preserved.</li>
 *   <li><strong>stop words</strong> — {@link #getStopWords(String)} / {@link #removeStopWords(String)}: English
 *       function words that add length without adding meaning.</li>
 *   <li><strong>reserved words</strong> — {@link #containsReservedWord(String)} /
 *       {@link #removeReservedWords(String)}: documentation-type (Diataxis) words.</li>
 * </ul>
 * The shape rules of {@link #toKebab(String)} come from {@code SlugEntityNameValidation} in XWiki Platform
 * 18.1.0+ (without the configurable options), copied here because this extension must support XWiki &lt; 18.1.0.
 * The only deliberate divergence is the underscore, which that class keeps and which is not kebab-case, so it is
 * turned into a hyphen here.
 *
 * @version $Id$
 * @since 1.13
 */
public final class KebabNameValidator
{
    /**
     * Stop words to remove from page and attachment names (English function words that add no semantic value to a
     * kebab-case name). Each entry must be lowercase.
     * <p>
     * Words whose removal would change what a name means are deliberately absent: negations ("no", "not",
     * "cannot", …) and words of direction or relation ("above", "below", "before", "after", "up", "down", …). A
     * name such as {@code cannot-restore} describes the opposite of {@code restore}, so no naming rule may ask an
     * author to shorten one into the other.
     */
    static final Set<String> STOP_WORDS = Set.of(
        "a", "about", "again", "all", "am", "an", "and", "any", "are", "as", "at", "be", "because", "been",
        "being", "both", "but", "by", "could", "did", "do", "does", "doing", "during", "each", "few", "for",
        "from", "further", "had", "has", "have", "having", "he", "hed", "hes", "her", "here", "heres", "hers",
        "herself", "him", "himself", "his", "how", "hows", "i", "im", "ive", "if", "in", "is", "it", "its",
        "itself", "lets", "me", "more", "most", "my", "myself", "of", "on", "once", "only", "or", "other",
        "ought", "our", "ours", "ourselves", "own", "same", "she", "shes", "should", "so", "some", "such",
        "than", "that", "thats", "the", "their", "theirs", "them", "themselves", "then", "there", "theres",
        "these", "they", "theyd", "theyll", "theyre", "theyve", "this", "those", "to", "too", "until", "very",
        "was", "we", "wed", "were", "weve", "what", "whats", "when", "whens", "where", "wheres", "which",
        "while", "who", "whos", "whom", "why", "whys", "with", "would", "you", "youd", "youll", "youre",
        "youve", "your", "yours", "yourself", "yourselves"
    );

    /**
     * Documentation-type (Diataxis) words that should not appear as segments in page or attachment names. Their
     * presence triggers a violation (see {@link #containsReservedWord(String)}), and they are stripped by
     * {@link #removeReservedWords(String)}.
     * <p>
     * Note: "howto" is also matched when written as the two consecutive segments "how" and "to" (e.g. "How to",
     * "How-to" or {@code how-to}), see {@link #HOW_TO_SEGMENTS}.
     */
    static final Set<String> RESERVED_WORDS = Set.of("explanation", "howto", "reference", "tutorial");

    private static final String REPLACEMENT_CHARACTER = "-";

    /**
     * The kebab form of "How to" and "How-to".
     */
    private static final String HOW_TO = "how-to";

    /**
     * The consecutive segments of {@link #HOW_TO}, which stand for the {@link #HOWTO} reserved word.
     */
    private static final List<String> HOW_TO_SEGMENTS = List.of(HOW_TO.split(REPLACEMENT_CHARACTER));

    /**
     * The "howto" reserved word.
     */
    private static final String HOWTO = HOW_TO.replace(REPLACEMENT_CHARACTER, StringUtils.EMPTY);

    private static final Pattern DASH_PATTERN = Pattern.compile("-+");

    /**
     * Anything that a kebab name cannot contain at all. Dots are excluded from this pattern because they are
     * legal between digits; {@link #ISOLATED_DOT_PATTERN} deals with the others.
     */
    private static final Pattern NONKEBAB_PATTERN = Pattern.compile("[^a-z0-9.]");

    /**
     * A dot that is not surrounded by digits, i.e. one that is not part of a version number such as {@code 1.0}.
     */
    private static final Pattern ISOLATED_DOT_PATTERN = Pattern.compile("(?<!\\d)\\.|\\.(?!\\d)");

    /**
     * A name that is already in kebab-case: lowercase letters and digits, single hyphens between two such
     * characters, and dots only between two digits. Each repetition consumes a whole run of letters and digits, and the
     * quantifiers are possessive, so that a long name doesn't overflow the stack.
     */
    private static final Pattern VALID_KEBAB_PATTERN =
        Pattern.compile("[a-z0-9]++(?:-[a-z0-9]++|(?<=\\d)\\.\\d[a-z0-9]*+)*+");

    private KebabNameValidator()
    {
        // Utility class, not meant to be instantiated.
    }

    /**
     * Tell whether a name is in kebab-case. This is purely about the shape of the name — a valid kebab name may
     * still hold stop words or reserved words, which {@link #getStopWords(String)} and
     * {@link #containsReservedWord(String)} report separately.
     *
     * @param name the name to validate
     * @return {@code true} if the name contains only lowercase letters, digits, single inner hyphens, and dots
     *     between digits
     */
    public static boolean isValidKebab(String name)
    {
        return VALID_KEBAB_PATTERN.matcher(name).matches();
    }

    /**
     * Transform an arbitrary name into kebab-case: strip accents, lowercase, turn everything that is not a
     * lowercase letter, a digit or a dot between digits into a hyphen, then collapse and trim the hyphens. No
     * word is removed, so the meaning of the name is preserved.
     *
     * @param name the name to transform
     * @return the kebab-case form of the name
     */
    public static String toKebab(String name)
    {
        String result = StringUtils.stripAccents(name);
        result = result.toLowerCase(Locale.ROOT);
        result = NONKEBAB_PATTERN.matcher(result).replaceAll(REPLACEMENT_CHARACTER);
        result = ISOLATED_DOT_PATTERN.matcher(result).replaceAll(REPLACEMENT_CHARACTER);
        return trimHyphens(result);
    }

    /**
     * @param name the name to inspect (in any form — it is normalised to kebab first)
     * @return the {@link #STOP_WORDS} appearing as whole segments of the name, in the order they appear and
     *     without duplicates, or an empty list when the name holds none
     */
    public static List<String> getStopWords(String name)
    {
        return getSegments(name, STOP_WORDS);
    }

    /**
     * @param name the name to transform (in any form — it is normalised to kebab first)
     * @return the kebab form of the name with its {@link #STOP_WORDS} segments removed
     */
    public static String removeStopWords(String name)
    {
        return removeSegments(toKebab(name), STOP_WORDS);
    }

    /**
     * @param name the name to check (in any form — it is normalised to kebab first)
     * @return {@code true} if the kebab form of the name contains at least one {@link #RESERVED_WORDS} segment,
     *     the consecutive segments {@code how} and {@code to} counting as the {@code howto} reserved word
     */
    public static boolean containsReservedWord(String name)
    {
        return getReservedWordSegments(name).stream().anyMatch(RESERVED_WORDS::contains);
    }

    /**
     * @param name the name to transform (in any form — it is normalised to kebab first)
     * @return the kebab form of the name with its {@link #RESERVED_WORDS} segments removed, including both
     *     segments of {@code how-to}
     */
    public static String removeReservedWords(String name)
    {
        return removeSegments(String.join(REPLACEMENT_CHARACTER, getReservedWordSegments(name)), RESERVED_WORDS);
    }

    /**
     * @param name the name to split (in any form — it is normalised to kebab first)
     * @return the segments of the kebab form of the name, where each {@code how} immediately followed by
     *     {@code to} is merged into a single {@code howto} segment
     */
    private static List<String> getReservedWordSegments(String name)
    {
        List<String> segments = new ArrayList<>(List.of(toKebab(name).split(REPLACEMENT_CHARACTER)));
        for (int i = 0; i < segments.size() - 1; i++) {
            if (segments.subList(i, i + HOW_TO_SEGMENTS.size()).equals(HOW_TO_SEGMENTS)) {
                segments.subList(i, i + HOW_TO_SEGMENTS.size()).clear();
                segments.add(i, HOWTO);
            }
        }
        return segments;
    }

    private static List<String> getSegments(String name, Set<String> words)
    {
        return Arrays.stream(toKebab(name).split(REPLACEMENT_CHARACTER, -1))
            .filter(words::contains)
            .distinct()
            .collect(Collectors.toCollection(ArrayList::new));
    }

    private static String removeSegments(String name, Set<String> words)
    {
        return Arrays.stream(name.split(REPLACEMENT_CHARACTER))
            .filter(segment -> !segment.isEmpty() && !words.contains(segment))
            .collect(Collectors.joining(REPLACEMENT_CHARACTER));
    }

    private static String trimHyphens(String name)
    {
        String result = DASH_PATTERN.matcher(name).replaceAll(REPLACEMENT_CHARACTER);
        result = Strings.CS.removeEnd(result, REPLACEMENT_CHARACTER);
        return Strings.CS.removeStart(result, REPLACEMENT_CHARACTER);
    }
}
