/*
 * Copyright (C) 2009 The Libphonenumber Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.headuck.phonenumbers;

import com.headuck.phonenumbers.Phonemetadata.PhoneMetadata;
import com.headuck.phonenumbers.Phonenumber.CountryCodeSource;
import com.headuck.phonenumbers.Phonenumber.PhoneNumber;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Utility for international phone numbers. Functionality includes formatting, parsing and
 * validation.
 *
 * <p>If you use this library, and want to be notified about important changes, please sign up to
 * our <a href="http://groups.google.com/group/libphonenumber-discuss/about">mailing list</a>.
 *
 * NOTE: A lot of methods in this class require Region Code strings. These must be provided using
 * ISO 3166-1 two-letter country-code format. These should be in upper-case. The list of the codes
 * can be found here:
 * http://www.iso.org/iso/country_codes/iso_3166_code_lists/country_names_and_code_elements.htm
 *
 * @author Shaopeng Jia
 */
public class PhoneNumberUtilLite {
    // @VisibleForTesting
    static final MetadataLoader DEFAULT_METADATA_LOADER = new MetadataLoader() {
        @Override
        public InputStream loadMetadata(String metadataFileName) {
            return PhoneNumberUtilLite.class.getResourceAsStream(metadataFileName);
        }
    };

    private static final Logger logger = Logger.getLogger(PhoneNumberUtilLite.class.getName());

    /** Flags to use when compiling regular expressions for phone numbers. */
    static final int REGEX_FLAGS = Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE;
    // The minimum and maximum length of the national significant number.
    private static final int MIN_LENGTH_FOR_NSN = 2;
    // The ITU says the maximum length should be 15, but we have found longer numbers in Germany.
    static final int MAX_LENGTH_FOR_NSN = 17;
    // The maximum length of the country calling code.
    static final int MAX_LENGTH_COUNTRY_CODE = 3;
    // We don't allow input strings for parsing to be longer than 250 chars. This prevents malicious
    // input from overflowing the regular-expression engine.
    private static final int MAX_INPUT_STRING_LENGTH = 250;

    // Region-code for the unknown region.
    private static final String UNKNOWN_REGION = "ZZ";

    private static final int NANPA_COUNTRY_CODE = 1;

    // Map of country calling codes that use a mobile token before the area code. One example of when
    // this is relevant is when determining the length of the national destination code, which should
    // be the length of the area code plus the length of the mobile token.
    private static final Map<Integer, String> MOBILE_TOKEN_MAPPINGS;

    // The PLUS_SIGN signifies the international prefix.
    static final char PLUS_SIGN = '+';

    private static final char STAR_SIGN = '*';

    // A map that contains characters that are essential when dialling. That means any of the
    // characters in this map must not be removed from a number when dialling, otherwise the call
    // will not reach the intended destination.
    private static final Map<Character, Character> DIALLABLE_CHAR_MAPPINGS;

    // Only upper-case variants of alpha characters are stored.
    private static final Map<Character, Character> ALPHA_MAPPINGS;

    // For performance reasons, amalgamate both into one map.
    private static final Map<Character, Character> ALPHA_PHONE_MAPPINGS;

    // Separate map of all symbols that we wish to retain when formatting alpha numbers. This
    // includes digits, ASCII letters and number grouping symbols such as "-" and " ".
    private static final Map<Character, Character> ALL_PLUS_NUMBER_GROUPING_SYMBOLS;

    static {
        HashMap<Integer, String> mobileTokenMap = new HashMap<Integer, String>();
        mobileTokenMap.put(52, "1");
        mobileTokenMap.put(54, "9");
        MOBILE_TOKEN_MAPPINGS = Collections.unmodifiableMap(mobileTokenMap);

        // Simple ASCII digits map used to populate ALPHA_PHONE_MAPPINGS and
        // ALL_PLUS_NUMBER_GROUPING_SYMBOLS.
        HashMap<Character, Character> asciiDigitMappings = new HashMap<Character, Character>();
        asciiDigitMappings.put('0', '0');
        asciiDigitMappings.put('1', '1');
        asciiDigitMappings.put('2', '2');
        asciiDigitMappings.put('3', '3');
        asciiDigitMappings.put('4', '4');
        asciiDigitMappings.put('5', '5');
        asciiDigitMappings.put('6', '6');
        asciiDigitMappings.put('7', '7');
        asciiDigitMappings.put('8', '8');
        asciiDigitMappings.put('9', '9');

        HashMap<Character, Character> alphaMap = new HashMap<Character, Character>(40);
        alphaMap.put('A', '2');
        alphaMap.put('B', '2');
        alphaMap.put('C', '2');
        alphaMap.put('D', '3');
        alphaMap.put('E', '3');
        alphaMap.put('F', '3');
        alphaMap.put('G', '4');
        alphaMap.put('H', '4');
        alphaMap.put('I', '4');
        alphaMap.put('J', '5');
        alphaMap.put('K', '5');
        alphaMap.put('L', '5');
        alphaMap.put('M', '6');
        alphaMap.put('N', '6');
        alphaMap.put('O', '6');
        alphaMap.put('P', '7');
        alphaMap.put('Q', '7');
        alphaMap.put('R', '7');
        alphaMap.put('S', '7');
        alphaMap.put('T', '8');
        alphaMap.put('U', '8');
        alphaMap.put('V', '8');
        alphaMap.put('W', '9');
        alphaMap.put('X', '9');
        alphaMap.put('Y', '9');
        alphaMap.put('Z', '9');
        ALPHA_MAPPINGS = Collections.unmodifiableMap(alphaMap);

        HashMap<Character, Character> combinedMap = new HashMap<Character, Character>(100);
        combinedMap.putAll(ALPHA_MAPPINGS);
        combinedMap.putAll(asciiDigitMappings);
        ALPHA_PHONE_MAPPINGS = Collections.unmodifiableMap(combinedMap);

        HashMap<Character, Character> diallableCharMap = new HashMap<Character, Character>();
        diallableCharMap.putAll(asciiDigitMappings);
        diallableCharMap.put(PLUS_SIGN, PLUS_SIGN);
        diallableCharMap.put('*', '*');
        DIALLABLE_CHAR_MAPPINGS = Collections.unmodifiableMap(diallableCharMap);

        HashMap<Character, Character> allPlusNumberGroupings = new HashMap<Character, Character>();
        // Put (lower letter -> upper letter) and (upper letter -> upper letter) mappings.
        for (char c : ALPHA_MAPPINGS.keySet()) {
            allPlusNumberGroupings.put(Character.toLowerCase(c), c);
            allPlusNumberGroupings.put(c, c);
        }
        allPlusNumberGroupings.putAll(asciiDigitMappings);
        // Put grouping symbols.
        allPlusNumberGroupings.put('-', '-');
        allPlusNumberGroupings.put('\uFF0D', '-');
        allPlusNumberGroupings.put('\u2010', '-');
        allPlusNumberGroupings.put('\u2011', '-');
        allPlusNumberGroupings.put('\u2012', '-');
        allPlusNumberGroupings.put('\u2013', '-');
        allPlusNumberGroupings.put('\u2014', '-');
        allPlusNumberGroupings.put('\u2015', '-');
        allPlusNumberGroupings.put('\u2212', '-');
        allPlusNumberGroupings.put('/', '/');
        allPlusNumberGroupings.put('\uFF0F', '/');
        allPlusNumberGroupings.put(' ', ' ');
        allPlusNumberGroupings.put('\u3000', ' ');
        allPlusNumberGroupings.put('\u2060', ' ');
        allPlusNumberGroupings.put('.', '.');
        allPlusNumberGroupings.put('\uFF0E', '.');
        ALL_PLUS_NUMBER_GROUPING_SYMBOLS = Collections.unmodifiableMap(allPlusNumberGroupings);
    }

    // Regular expression of acceptable punctuation found in phone numbers. This excludes punctuation
    // found as a leading character only.
    // This consists of dash characters, white space characters, full stops, slashes,
    // square brackets, parentheses and tildes. It also includes the letter 'x' as that is found as a
    // placeholder for carrier information in some phone numbers. Full-width variants are also
    // present.
    static final String VALID_PUNCTUATION = "-x\u2010-\u2015\u2212\u30FC\uFF0D-\uFF0F " +
            "\u00A0\u00AD\u200B\u2060\u3000()\uFF08\uFF09\uFF3B\uFF3D.\\[\\]/~\u2053\u223C\uFF5E";

    private static final String DIGITS = "\\p{Nd}";
    // We accept alpha characters in phone numbers, ASCII only, upper and lower case.
    private static final String VALID_ALPHA =
            Arrays.toString(ALPHA_MAPPINGS.keySet().toArray()).replaceAll("[, \\[\\]]", "") +
                    Arrays.toString(ALPHA_MAPPINGS.keySet().toArray()).toLowerCase().replaceAll("[, \\[\\]]", "");
    static final String PLUS_CHARS = "+\uFF0B";
    static final Pattern PLUS_CHARS_PATTERN = Pattern.compile("[" + PLUS_CHARS + "]+");
    private static final Pattern CAPTURING_DIGIT_PATTERN = Pattern.compile("(" + DIGITS + ")");


    // We use this pattern to check if the phone number has at least three letters in it - if so, then
    // we treat it as a number where some phone-number digits are represented by letters.
    private static final Pattern VALID_ALPHA_PHONE_PATTERN = Pattern.compile("(?:.*?[A-Za-z]){3}.*");

    // Regular expression of viable phone numbers. This is location independent. Checks we have at
    // least three leading digits, and only valid punctuation, alpha characters and
    // digits in the phone number. Does not include extension data.
    // The symbol 'x' is allowed here as valid punctuation since it is often used as a placeholder for
    // carrier codes, for example in Brazilian phone numbers. We also allow multiple "+" characters at
    // the start.
    // Corresponds to the following:
    // [digits]{minLengthNsn}|
    // plus_sign*(([punctuation]|[star])*[digits]){3,}([punctuation]|[star]|[digits]|[alpha])*
    //
    // The first reg-ex is to allow short numbers (two digits long) to be parsed if they are entered
    // as "15" etc, but only if there is no punctuation in them. The second expression restricts the
    // number of digits to three or more, but then allows them to be in international form, and to
    // have alpha-characters and punctuation.
    //
    // Note VALID_PUNCTUATION starts with a -, so must be the first in the range.
    private static final String VALID_PHONE_NUMBER =
            DIGITS + "{" + MIN_LENGTH_FOR_NSN + "}" + "|" +
                    "[" + PLUS_CHARS + "]*+(?:[" + VALID_PUNCTUATION + STAR_SIGN + "]*" + DIGITS + "){3,}[" +
                    VALID_PUNCTUATION + STAR_SIGN + VALID_ALPHA + DIGITS + "]*";


    // Removed ext pattern
    private static final Pattern VALID_PHONE_NUMBER_PATTERN =
            Pattern.compile(VALID_PHONE_NUMBER, REGEX_FLAGS);

    private static PhoneNumberUtilLite instance = null;

    public static final String REGION_CODE_FOR_NON_GEO_ENTITY = "001";

    /**
     * Type of phone numbers.
     */
    public enum PhoneNumberType {
        FIXED_LINE,
        MOBILE,
        // In some regions (e.g. the USA), it is impossible to distinguish between fixed-line and
        // mobile numbers by looking at the phone number itself.
        FIXED_LINE_OR_MOBILE,
        // Freephone lines
        TOLL_FREE,
        PREMIUM_RATE,
        // The cost of this call is shared between the caller and the recipient, and is hence typically
        // less than PREMIUM_RATE calls. See // http://en.wikipedia.org/wiki/Shared_Cost_Service for
        // more information.
        SHARED_COST,
        // Voice over IP numbers. This includes TSoIP (Telephony Service over IP).
        VOIP,
        // A personal number is associated with a particular person, and may be routed to either a
        // MOBILE or FIXED_LINE number. Some more information can be found here:
        // http://en.wikipedia.org/wiki/Personal_Numbers
        PERSONAL_NUMBER,
        PAGER,
        // Used for "Universal Access Numbers" or "Company Numbers". They may be further routed to
        // specific offices, but allow one number to be used for a company.
        UAN,
        // Used for "Voice Mail Access Numbers".
        VOICEMAIL,
        // A phone number is of type UNKNOWN when it does not fit any of the known patterns for a
        // specific region.
        UNKNOWN
    }

    /**
     * Possible outcomes when testing if a PhoneNumber is possible.
     */
    public enum ValidationResult {
        IS_POSSIBLE,
        INVALID_COUNTRY_CODE,
        TOO_SHORT,
        TOO_LONG,
    }

    // A source of metadata for different regions.
    private final MetadataSource metadataSource;

    // A mapping from a country calling code to the region codes which denote the region represented
    // by that country calling code. In the case of multiple regions sharing a calling code, such as
    // the NANPA regions, the one indicated with "isMainCountryForCode" in the metadata should be
    // first.
    private final Map<Integer, List<String>> countryCallingCodeToRegionCodeMap;

    // The set of regions that share country calling code 1.
    // There are roughly 26 regions.
    // We set the initial capacity of the HashSet to 35 to offer a load factor of roughly 0.75.
    private final Set<String> nanpaRegions = new HashSet<String>(35);

    // A cache for frequently used region-specific regular expressions.
    // The initial capacity is set to 100 as this seems to be an optimal value for Android, based on
    // performance measurements.
    private final RegexCache regexCache = new RegexCache(100);

    // The set of regions the library supports.
    // There are roughly 240 of them and we set the initial capacity of the HashSet to 320 to offer a
    // load factor of roughly 0.75.
    private final Set<String> supportedRegions = new HashSet<String>(320);

    // The set of county calling codes that map to the non-geo entity region ("001"). This set
    // currently contains < 12 elements so the default capacity of 16 (load factor=0.75) is fine.
    private final Set<Integer> countryCodesForNonGeographicalRegion = new HashSet<Integer>();

    /**
     * This class implements a singleton, the constructor is only visible to facilitate testing.
     */
    // @VisibleForTesting
    PhoneNumberUtilLite(MetadataSource metadataSource,
                        Map<Integer, List<String>> countryCallingCodeToRegionCodeMap) {
        this.metadataSource = metadataSource;
        this.countryCallingCodeToRegionCodeMap = countryCallingCodeToRegionCodeMap;
        for (Map.Entry<Integer, List<String>> entry : countryCallingCodeToRegionCodeMap.entrySet()) {
            List<String> regionCodes = entry.getValue();
            // We can assume that if the county calling code maps to the non-geo entity region code then
            // that's the only region code it maps to.
            if (regionCodes.size() == 1 && REGION_CODE_FOR_NON_GEO_ENTITY.equals(regionCodes.get(0))) {
                // This is the subset of all country codes that map to the non-geo entity region code.
                countryCodesForNonGeographicalRegion.add(entry.getKey());
            } else {
                // The supported regions set does not include the "001" non-geo entity region code.
                supportedRegions.addAll(regionCodes);
            }
        }
        // If the non-geo entity still got added to the set of supported regions it must be because
        // there are entries that list the non-geo entity alongside normal regions (which is wrong).
        // If we discover this, remove the non-geo entity from the set of supported regions and log.
        if (supportedRegions.remove(REGION_CODE_FOR_NON_GEO_ENTITY)) {
            logger.log(Level.WARNING, "invalid metadata " +
                    "(country calling code was mapped to the non-geo entity as well as specific region(s))");
        }
        nanpaRegions.addAll(countryCallingCodeToRegionCodeMap.get(NANPA_COUNTRY_CODE));
    }

    /**
     * Checks to see if the string of characters could possibly be a phone number at all. At the
     * moment, checks to see that the string begins with at least 2 digits, ignoring any punctuation
     * commonly found in phone numbers.
     * This method does not require the number to be normalized in advance - but does assume that
     * leading non-number symbols have been removed, such as by the method extractPossibleNumber.
     *
     * @param number  string to be checked for viability as a phone number
     * @return        true if the number could be a phone number of some sort, otherwise false
     */
    // @VisibleForTesting
    static boolean isViablePhoneNumber(String number) {
        if (number.length() < MIN_LENGTH_FOR_NSN) {
            return false;
        }
        Matcher m = VALID_PHONE_NUMBER_PATTERN.matcher(number);
        return m.matches();
    }

    /**
     * Normalizes a string of characters representing a phone number. This performs the following
     * conversions:
     *   Punctuation is stripped.
     *   For ALPHA/VANITY numbers:
     *   Letters are converted to their numeric representation on a telephone keypad. The keypad
     *       used here is the one defined in ITU Recommendation E.161. This is only done if there are
     *       3 or more letters in the number, to lessen the risk that such letters are typos.
     *   For other numbers:
     *   Wide-ascii digits are converted to normal ASCII (European) digits.
     *   Arabic-Indic numerals are converted to European numerals.
     *   Spurious alpha characters are stripped.
     *
     * @param number  a string of characters representing a phone number
     * @return        the normalized string version of the phone number
     */
    static String normalize(String number) {
        Matcher m = VALID_ALPHA_PHONE_PATTERN.matcher(number);
        if (m.matches()) {
            return normalizeHelper(number, ALPHA_PHONE_MAPPINGS, true);
        } else {
            return normalizeDigitsOnly(number);
        }
    }

    /**
     * Normalizes a string of characters representing a phone number. This is a wrapper for
     * normalize(String number) but does in-place normalization of the StringBuilder provided.
     *
     * @param number  a StringBuilder of characters representing a phone number that will be
     *     normalized in place
     */
    static void normalize(StringBuilder number) {
        String normalizedNumber = normalize(number.toString());
        number.replace(0, number.length(), normalizedNumber);
    }

    /**
     * Normalizes a string of characters representing a phone number. This converts wide-ascii and
     * arabic-indic numerals to European numerals, and strips punctuation and alpha characters.
     *
     * @param number  a string of characters representing a phone number
     * @return        the normalized string version of the phone number
     */
    public static String normalizeDigitsOnly(String number) {
        return normalizeDigits(number, false /* strip non-digits */).toString();
    }

    static StringBuilder normalizeDigits(String number, boolean keepNonDigits) {
        StringBuilder normalizedDigits = new StringBuilder(number.length());
        for (char c : number.toCharArray()) {
            int digit = Character.digit(c, 10);
            if (digit != -1) {
                normalizedDigits.append(digit);
            } else if (keepNonDigits) {
                normalizedDigits.append(c);
            }
        }
        return normalizedDigits;
    }

    /**
     * Normalizes a string of characters representing a phone number. This strips all characters which
     * are not diallable on a mobile phone keypad (including all non-ASCII digits).
     *
     * @param number  a string of characters representing a phone number
     * @return        the normalized string version of the phone number
     */
    static String normalizeDiallableCharsOnly(String number) {
        return normalizeHelper(number, DIALLABLE_CHAR_MAPPINGS, true /* remove non matches */);
    }


    /**
     * Normalizes a string of characters representing a phone number by replacing all characters found
     * in the accompanying map with the values therein, and stripping all other characters if
     * removeNonMatches is true.
     *
     * @param number                     a string of characters representing a phone number
     * @param normalizationReplacements  a mapping of characters to what they should be replaced by in
     *                                   the normalized version of the phone number
     * @param removeNonMatches           indicates whether characters that are not able to be replaced
     *                                   should be stripped from the number. If this is false, they
     *                                   will be left unchanged in the number.
     * @return  the normalized string version of the phone number
     */
    private static String normalizeHelper(String number,
                                          Map<Character, Character> normalizationReplacements,
                                          boolean removeNonMatches) {
        StringBuilder normalizedNumber = new StringBuilder(number.length());
        for (int i = 0; i < number.length(); i++) {
            char character = number.charAt(i);
            Character newDigit = normalizationReplacements.get(Character.toUpperCase(character));
            if (newDigit != null) {
                normalizedNumber.append(newDigit);
            } else if (!removeNonMatches) {
                normalizedNumber.append(character);
            }
            // If neither of the above are true, we remove this character.
        }
        return normalizedNumber.toString();
    }

    /**
     * Sets or resets the PhoneNumberUtilLite singleton instance. If set to null, the next call to
     * {@code getInstance()} will load (and return) the default instance.
     */
    // @VisibleForTesting
    static synchronized void setInstance(PhoneNumberUtilLite util) {
        instance = util;
    }

    /**
     * Convenience method to get a list of what regions the library has metadata for.
     */
    public Set<String> getSupportedRegions() {
        return Collections.unmodifiableSet(supportedRegions);
    }

    /**
     * Convenience method to get a list of what global network calling codes the library has metadata
     * for.
     */
    public Set<Integer> getSupportedGlobalNetworkCallingCodes() {
        return Collections.unmodifiableSet(countryCodesForNonGeographicalRegion);
    }

    /**
     * Gets a {@link PhoneNumberUtilLite} instance to carry out international phone number formatting,
     * parsing, or validation. The instance is loaded with phone number metadata for a number of most
     * commonly used regions.
     *
     * <p>The {@link PhoneNumberUtilLite} is implemented as a singleton. Therefore, calling getInstance
     * multiple times will only result in one instance being created.
     *
     * @return a PhoneNumberUtilLite instance
     */
    public static synchronized PhoneNumberUtilLite getInstance() {
        if (instance == null) {
            setInstance(createInstance(DEFAULT_METADATA_LOADER));
        }
        return instance;
    }

    /**
     * Create a new {@link PhoneNumberUtilLite} instance to carry out international phone number
     * formatting, parsing, or validation. The instance is loaded with all metadata by
     * using the metadataSource specified.
     *
     * This method should only be used in the rare case in which you want to manage your own
     * metadata loading. Calling this method multiple times is very expensive, as each time
     * a new instance is created from scratch. When in doubt, use {@link #getInstance}.
     *
     * @param metadataSource Customized metadata source. This should not be null.
     * @return a PhoneNumberUtilLite instance
     */
    public static PhoneNumberUtilLite createInstance(MetadataSource metadataSource) {
        if (metadataSource == null) {
            throw new IllegalArgumentException("metadataSource could not be null.");
        }
        return new PhoneNumberUtilLite(metadataSource,
                CountryCodeToRegionCodeMap.getCountryCodeToRegionCodeMap());
    }

    /**
     * Create a new {@link PhoneNumberUtilLite} instance to carry out international phone number
     * formatting, parsing, or validation. The instance is loaded with all metadata by
     * using the metadataLoader specified.
     *
     * This method should only be used in the rare case in which you want to manage your own
     * metadata loading. Calling this method multiple times is very expensive, as each time
     * a new instance is created from scratch. When in doubt, use {@link #getInstance}.
     *
     * @param metadataLoader Customized metadata loader. This should not be null.
     * @return a PhoneNumberUtilLite instance
     */
    public static PhoneNumberUtilLite createInstance(MetadataLoader metadataLoader) {
        if (metadataLoader == null) {
            throw new IllegalArgumentException("metadataLoader could not be null.");
        }
        return createInstance(new SingleFileMetadataSourceImpl(metadataLoader));
    }

    /**
     * Tests whether a phone number has a geographical association. It checks if the number is
     * associated to a certain region in the country where it belongs to. Note that this doesn't
     * verify if the number is actually in use.
     *
     * A similar method is implemented as PhoneNumberOfflineGeocoder.canBeGeocoded, which performs a
     * looser check, since it only prevents cases where prefixes overlap for geocodable and
     * non-geocodable numbers. Also, if new phone number types were added, we should check if this
     * other method should be updated too.
     */
    boolean isNumberGeographical(PhoneNumber phoneNumber) {
        PhoneNumberType numberType = getNumberType(phoneNumber);
        // TODO: Include mobile phone numbers from countries like Indonesia, which has some
        // mobile numbers that are geographical.
        return numberType == PhoneNumberType.FIXED_LINE ||
                numberType == PhoneNumberType.FIXED_LINE_OR_MOBILE;
    }

    /**
     * Helper function to check region code is not unknown or null.
     */
    private boolean isValidRegionCode(String regionCode) {
        return regionCode != null && supportedRegions.contains(regionCode);
    }

    /**
     * Helper function to check the country calling code is valid.
     */
    private boolean hasValidCountryCallingCode(int countryCallingCode) {
        return countryCallingCodeToRegionCodeMap.containsKey(countryCallingCode);
    }

    private PhoneMetadata getMetadataForRegionOrCallingCode(
            int countryCallingCode, String regionCode) {
        return REGION_CODE_FOR_NON_GEO_ENTITY.equals(regionCode)
                ? getMetadataForNonGeographicalRegion(countryCallingCode)
                : getMetadataForRegion(regionCode);
    }

    /**
     * Returns true if a number is from a region whose national significant number couldn't contain a
     * leading zero, but has the italian_leading_zero field set to true.
     */
    private boolean hasUnexpectedItalianLeadingZero(PhoneNumber number) {
        return number.isItalianLeadingZero() && !isLeadingZeroPossible(number.getCountryCode());
    }

    /**
     * Gets the national significant number of the a phone number. Note a national significant number
     * doesn't contain a national prefix or any formatting.
     *
     * @param number  the phone number for which the national significant number is needed
     * @return  the national significant number of the PhoneNumber object passed in
     */
    public String getNationalSignificantNumber(PhoneNumber number) {
        // If leading zero(s) have been set, we prefix this now. Note this is not a national prefix.
        StringBuilder nationalNumber = new StringBuilder();
        if (number.isItalianLeadingZero()) {
            char[] zeros = new char[number.getNumberOfLeadingZeros()];
            Arrays.fill(zeros, '0');
            nationalNumber.append(new String(zeros));
        }
        nationalNumber.append(number.getNationalNumber());
        return nationalNumber.toString();
    }


    String getNumberDescByType(PhoneMetadata metadata, PhoneNumberType type) {
        switch (type) {
            case PREMIUM_RATE:
                return metadata.premiumRate;
            case TOLL_FREE:
                return metadata.tollFree;
            case MOBILE:
                return metadata.mobile;
            case FIXED_LINE:
            case FIXED_LINE_OR_MOBILE:
                return metadata.fixedLine;
            case SHARED_COST:
                return metadata.sharedCost;
            case VOIP:
                return metadata.voip;
            case PERSONAL_NUMBER:
                return metadata.personalNumber;
            case PAGER:
                return metadata.pager;
            case UAN:
                return metadata.uan;
            case VOICEMAIL:
                return metadata.voicemail;
            default:
                return metadata.generalDesc;
        }
    }

    /**
     * Gets the type of a phone number.
     *
     * @param number  the phone number that we want to know the type
     * @return  the type of the phone number
     */
    public PhoneNumberType getNumberType(PhoneNumber number) {
        String regionCode = getRegionCodeForNumber(number);
        PhoneMetadata metadata = getMetadataForRegionOrCallingCode(number.getCountryCode(), regionCode);
        if (metadata == null) {
            return PhoneNumberType.UNKNOWN;
        }
        String nationalSignificantNumber = getNationalSignificantNumber(number);
        return getNumberTypeHelper(nationalSignificantNumber, metadata);
    }

    private PhoneNumberType getNumberTypeHelper(String nationalNumber, PhoneMetadata metadata) {
        if (!isNumberMatchingDesc(nationalNumber, metadata.generalDesc)) {
            return PhoneNumberType.UNKNOWN;
        }
        if (isNumberMatchingDesc(nationalNumber, metadata.premiumRate)) {
            return PhoneNumberType.PREMIUM_RATE;
        }
        if (isNumberMatchingDesc(nationalNumber, metadata.tollFree)) {
            return PhoneNumberType.TOLL_FREE;
        }
        if (isNumberMatchingDesc(nationalNumber, metadata.sharedCost)) {
            return PhoneNumberType.SHARED_COST;
        }
        if (isNumberMatchingDesc(nationalNumber, metadata.voip)) {
            return PhoneNumberType.VOIP;
        }
        if (isNumberMatchingDesc(nationalNumber, metadata.personalNumber)) {
            return PhoneNumberType.PERSONAL_NUMBER;
        }
        if (isNumberMatchingDesc(nationalNumber, metadata.pager)) {
            return PhoneNumberType.PAGER;
        }
        if (isNumberMatchingDesc(nationalNumber, metadata.uan)) {
            return PhoneNumberType.UAN;
        }
        if (isNumberMatchingDesc(nationalNumber, metadata.voicemail)) {
            return PhoneNumberType.VOICEMAIL;
        }

        boolean isFixedLine = isNumberMatchingDesc(nationalNumber, metadata.fixedLine);
        if (isFixedLine) {
            if (metadata.sameMobileAndFixedLinePattern) {
                return PhoneNumberType.FIXED_LINE_OR_MOBILE;
            } else if (isNumberMatchingDesc(nationalNumber, metadata.mobile)) {
                return PhoneNumberType.FIXED_LINE_OR_MOBILE;
            }
            return PhoneNumberType.FIXED_LINE;
        }
        // Otherwise, test to see if the number is mobile. Only do this if certain that the patterns for
        // mobile and fixed line aren't the same.
        if (!metadata.sameMobileAndFixedLinePattern &&
                isNumberMatchingDesc(nationalNumber, metadata.mobile)) {
            return PhoneNumberType.MOBILE;
        }
        return PhoneNumberType.UNKNOWN;
    }

    /**
     * Returns the metadata for the given region code or {@code null} if the region code is invalid
     * or unknown.
     */
    PhoneMetadata getMetadataForRegion(String regionCode) {
        if (!isValidRegionCode(regionCode)) {
            return null;
        }
        return metadataSource.getMetadataForRegion(regionCode);
    }

    PhoneMetadata getMetadataForNonGeographicalRegion(int countryCallingCode) {
        if (!countryCallingCodeToRegionCodeMap.containsKey(countryCallingCode)) {
            return null;
        }
        return metadataSource.getMetadataForNonGeographicalRegion(countryCallingCode);
    }


    boolean isNumberMatchingDesc(String nationalNumber, String numberDesc) {
        if (numberDesc == null) return false;
        Matcher nationalNumberPatternMatcher =
                regexCache.getPatternForRegex(numberDesc)
                        .matcher(nationalNumber);
        return nationalNumberPatternMatcher.matches();
    }

    /**
     * Tests whether a phone number matches a valid pattern. Note this doesn't verify the number
     * is actually in use, which is impossible to tell by just looking at a number itself.
     *
     * @param number       the phone number that we want to validate
     * @return  a boolean that indicates whether the number is of a valid pattern
     */
    public boolean isValidNumber(PhoneNumber number) {
        String regionCode = getRegionCodeForNumber(number);
        return isValidNumberForRegion(number, regionCode);
    }

    /**
     * Tests whether a phone number is valid for a certain region. Note this doesn't verify the number
     * is actually in use, which is impossible to tell by just looking at a number itself. If the
     * country calling code is not the same as the country calling code for the region, this
     * immediately exits with false. After this, the specific number pattern rules for the region are
     * examined. This is useful for determining for example whether a particular number is valid for
     * Canada, rather than just a valid NANPA number.
     * Warning: In most cases, you want to use {@link #isValidNumber} instead. For example, this
     * method will mark numbers from British Crown dependencies such as the Isle of Man as invalid for
     * the region "GB" (United Kingdom), since it has its own region code, "IM", which may be
     * undesirable.
     *
     * @param number       the phone number that we want to validate
     * @param regionCode   the region that we want to validate the phone number for
     * @return  a boolean that indicates whether the number is of a valid pattern
     */
    public boolean isValidNumberForRegion(PhoneNumber number, String regionCode) {
        int countryCode = number.getCountryCode();
        PhoneMetadata metadata = getMetadataForRegionOrCallingCode(countryCode, regionCode);
        if ((metadata == null) ||
                (!REGION_CODE_FOR_NON_GEO_ENTITY.equals(regionCode) &&
                        countryCode != getCountryCodeForValidRegion(regionCode))) {
            // Either the region code was invalid, or the country calling code for this number does not
            // match that of the region code.
            return false;
        }
        String nationalSignificantNumber = getNationalSignificantNumber(number);
        return getNumberTypeHelper(nationalSignificantNumber, metadata) != PhoneNumberType.UNKNOWN;
    }

    /**
     * Returns the region where a phone number is from. This could be used for geocoding at the region
     * level.
     *
     * @param number  the phone number whose origin we want to know
     * @return  the region where the phone number is from, or null if no region matches this calling
     *     code
     */
    public String getRegionCodeForNumber(PhoneNumber number) {
        int countryCode = number.getCountryCode();
        List<String> regions = countryCallingCodeToRegionCodeMap.get(countryCode);
        if (regions == null) {
            String numberString = getNationalSignificantNumber(number);
            logger.log(Level.INFO,
                    "Missing/invalid country_code (" + countryCode + ") for number " + numberString);
            return null;
        }
        if (regions.size() == 1) {
            return regions.get(0);
        } else {
            return getRegionCodeForNumberFromRegionList(number, regions);
        }
    }

    private String getRegionCodeForNumberFromRegionList(PhoneNumber number,
                                                        List<String> regionCodes) {
        String nationalNumber = getNationalSignificantNumber(number);
        for (String regionCode : regionCodes) {
            // If leadingDigits is present, use this. Otherwise, do full validation.
            // Metadata cannot be null because the region codes come from the country calling code map.
            PhoneMetadata metadata = getMetadataForRegion(regionCode);
            if (!metadata.leadingDigits.equals("")) {
                if (regexCache.getPatternForRegex(metadata.leadingDigits)
                        .matcher(nationalNumber).lookingAt()) {
                    return regionCode;
                }
            } else if (getNumberTypeHelper(nationalNumber, metadata) != PhoneNumberType.UNKNOWN) {
                return regionCode;
            }
        }
        return null;
    }

    /**
     * Returns the region code that matches the specific country calling code. In the case of no
     * region code being found, ZZ will be returned. In the case of multiple regions, the one
     * designated in the metadata as the "main" region for this calling code will be returned. If the
     * countryCallingCode entered is valid but doesn't match a specific region (such as in the case of
     * non-geographical calling codes like 800) the value "001" will be returned (corresponding to
     * the value for World in the UN M.49 schema).
     */
    public String getRegionCodeForCountryCode(int countryCallingCode) {
        List<String> regionCodes = countryCallingCodeToRegionCodeMap.get(countryCallingCode);
        return regionCodes == null ? UNKNOWN_REGION : regionCodes.get(0);
    }

    /**
     * Returns a list with the region codes that match the specific country calling code. For
     * non-geographical country calling codes, the region code 001 is returned. Also, in the case
     * of no region code being found, an empty list is returned.
     */
    public List<String> getRegionCodesForCountryCode(int countryCallingCode) {
        List<String> regionCodes = countryCallingCodeToRegionCodeMap.get(countryCallingCode);
        return Collections.unmodifiableList(regionCodes == null ? new ArrayList<String>(0)
                : regionCodes);
    }

    /**
     * Returns the country calling code for a specific region. For example, this would be 1 for the
     * United States, and 64 for New Zealand.
     *
     * @param regionCode  the region that we want to get the country calling code for
     * @return  the country calling code for the region denoted by regionCode
     */
    public int getCountryCodeForRegion(String regionCode) {
        if (!isValidRegionCode(regionCode)) {
            logger.log(Level.WARNING,
                    "Invalid or missing region code ("
                            + ((regionCode == null) ? "null" : regionCode)
                            + ") provided.");
            return 0;
        }
        return getCountryCodeForValidRegion(regionCode);
    }

    /**
     * Returns the country calling code for a specific region. For example, this would be 1 for the
     * United States, and 64 for New Zealand. Assumes the region is already valid.
     *
     * @param regionCode  the region that we want to get the country calling code for
     * @return  the country calling code for the region denoted by regionCode
     * @throws IllegalArgumentException if the region is invalid
     */
    private int getCountryCodeForValidRegion(String regionCode) {
        PhoneMetadata metadata = getMetadataForRegion(regionCode);
        if (metadata == null) {
            throw new IllegalArgumentException("Invalid region code: " + regionCode);
        }
        return metadata.countryCode;
    }

    /**
     * Checks whether the country calling code is from a region whose national significant number
     * could contain a leading zero. An example of such a region is Italy. Returns false if no
     * metadata for the country is found.
     */
    boolean isLeadingZeroPossible(int countryCallingCode) {
        PhoneMetadata mainMetadataForCallingCode =
                getMetadataForRegionOrCallingCode(countryCallingCode,
                        getRegionCodeForCountryCode(countryCallingCode));
        if (mainMetadataForCallingCode == null) {
            return false;
        }
        return mainMetadataForCallingCode.leadingZeroPossible;
    }

    /**
     * Convenience wrapper around {@link #isPossibleNumberWithReason}. Instead of returning the reason
     * for failure, this method returns a boolean value.
     * @param number  the number that needs to be checked
     * @return  true if the number is possible
     */
    public boolean isPossibleNumber(PhoneNumber number) {
        return isPossibleNumberWithReason(number) == ValidationResult.IS_POSSIBLE;
    }

    /**
     * Helper method to check a number against a particular pattern and determine whether it matches,
     * or is too short or too long. Currently, if a number pattern suggests that numbers of length 7
     * and 10 are possible, and a number in between these possible lengths is entered, such as of
     * length 8, this will return TOO_LONG.
     */
    private ValidationResult testNumberLengthAgainstPattern(Pattern numberPattern, String number) {
        Matcher numberMatcher = numberPattern.matcher(number);
        if (numberMatcher.matches()) {
            return ValidationResult.IS_POSSIBLE;
        }
        if (numberMatcher.lookingAt()) {
            return ValidationResult.TOO_LONG;
        } else {
            return ValidationResult.TOO_SHORT;
        }
    }

    /**
     * Helper method to check whether a number is too short to be a regular length phone number in a
     * region.
     */
    private boolean isShorterThanPossibleNormalNumber(PhoneMetadata regionMetadata, String number) {
        Pattern possibleNumberPattern = regexCache.getPatternForRegex(
                regionMetadata.generalDescPossible);
        return testNumberLengthAgainstPattern(possibleNumberPattern, number) ==
                ValidationResult.TOO_SHORT;
    }

    /**
     * Check whether a phone number is a possible number. It provides a more lenient check than
     * {@link #isValidNumber} in the following sense:
     *<ol>
     * <li> It only checks the length of phone numbers. In particular, it doesn't check starting
     *      digits of the number.
     * <li> It doesn't attempt to figure out the type of the number, but uses general rules which
     *      applies to all types of phone numbers in a region. Therefore, it is much faster than
     *      isValidNumber.
     * <li> For fixed line numbers, many regions have the concept of area code, which together with
     *      subscriber number constitute the national significant number. It is sometimes okay to dial
     *      the subscriber number only when dialing in the same area. This function will return
     *      true if the subscriber-number-only version is passed in. On the other hand, because
     *      isValidNumber validates using information on both starting digits (for fixed line
     *      numbers, that would most likely be area codes) and length (obviously includes the
     *      length of area codes for fixed line numbers), it will return false for the
     *      subscriber-number-only version.
     * </ol>
     * @param number  the number that needs to be checked
     * @return  a ValidationResult object which indicates whether the number is possible
     */
    public ValidationResult isPossibleNumberWithReason(PhoneNumber number) {
        String nationalNumber = getNationalSignificantNumber(number);
        int countryCode = number.getCountryCode();
        // Note: For Russian Fed and NANPA numbers, we just use the rules from the default region (US or
        // Russia) since the getRegionCodeForNumber will not work if the number is possible but not
        // valid. This would need to be revisited if the possible number pattern ever differed between
        // various regions within those plans.
        if (!hasValidCountryCallingCode(countryCode)) {
            return ValidationResult.INVALID_COUNTRY_CODE;
        }
        String regionCode = getRegionCodeForCountryCode(countryCode);
        // Metadata cannot be null because the country calling code is valid.
        PhoneMetadata metadata = getMetadataForRegionOrCallingCode(countryCode, regionCode);
        Pattern possibleNumberPattern =
                regexCache.getPatternForRegex(metadata.generalDescPossible);
        return testNumberLengthAgainstPattern(possibleNumberPattern, nationalNumber);
    }

    /**
     * Check whether a phone number is a possible number given a number in the form of a string, and
     * the region where the number could be dialed from. It provides a more lenient check than
     * {@link #isValidNumber}. See {@link #isPossibleNumber(PhoneNumber)} for details.
     *
     * <p>This method first parses the number, then invokes {@link #isPossibleNumber(PhoneNumber)}
     * with the resultant PhoneNumber object.
     *
     * @param number  the number that needs to be checked, in the form of a string
     * @param regionDialingFrom  the region that we are expecting the number to be dialed from.
     *     Note this is different from the region where the number belongs.  For example, the number
     *     +1 650 253 0000 is a number that belongs to US. When written in this form, it can be
     *     dialed from any region. When it is written as 00 1 650 253 0000, it can be dialed from any
     *     region which uses an international dialling prefix of 00. When it is written as
     *     650 253 0000, it can only be dialed from within the US, and when written as 253 0000, it
     *     can only be dialed from within a smaller area in the US (Mountain View, CA, to be more
     *     specific).
     * @return  true if the number is possible
     */
    public boolean isPossibleNumber(String number, String regionDialingFrom) {
        try {
            return isPossibleNumber(parse(number, regionDialingFrom));
        } catch (NumberParseException e) {
            return false;
        }
    }


    // Extracts country calling code from fullNumber, returns it and places the remaining number in
    // nationalNumber. It assumes that the leading plus sign or IDD has already been removed. Returns
    // 0 if fullNumber doesn't start with a valid country calling code, and leaves nationalNumber
    // unmodified.
    int extractCountryCode(StringBuilder fullNumber, StringBuilder nationalNumber) {
        if ((fullNumber.length() == 0) || (fullNumber.charAt(0) == '0')) {
            // Country codes do not begin with a '0'.
            return 0;
        }
        int potentialCountryCode;
        int numberLength = fullNumber.length();
        for (int i = 1; i <= MAX_LENGTH_COUNTRY_CODE && i <= numberLength; i++) {
            potentialCountryCode = Integer.parseInt(fullNumber.substring(0, i));
            if (countryCallingCodeToRegionCodeMap.containsKey(potentialCountryCode)) {
                nationalNumber.append(fullNumber.substring(i));
                return potentialCountryCode;
            }
        }
        return 0;
    }

    /**
     * Tries to extract a country calling code from a number. This method will return zero if no
     * country calling code is considered to be present. Country calling codes are extracted in the
     * following ways:
     * <ul>
     *  <li> by stripping the international dialing prefix of the region the person is dialing from,
     *       if this is present in the number, and looking at the next digits
     *  <li> by stripping the '+' sign if present and then looking at the next digits
     *  <li> by comparing the start of the number and the country calling code of the default region.
     *       If the number is not considered possible for the numbering plan of the default region
     *       initially, but starts with the country calling code of this region, validation will be
     *       reattempted after stripping this country calling code. If this number is considered a
     *       possible number, then the first digits will be considered the country calling code and
     *       removed as such.
     * </ul>
     * It will throw a NumberParseException if the number starts with a '+' but the country calling
     * code supplied after this does not match that of any known region.
     *
     * @param number  non-normalized telephone number that we wish to extract a country calling
     *     code from - may begin with '+'
     * @param defaultRegionMetadata  metadata about the region this number may be from
     * @param nationalNumber  a string buffer to store the national significant number in, in the case
     *     that a country calling code was extracted. The number is appended to any existing contents.
     *     If no country calling code was extracted, this will be left unchanged.
     * @param keepRawInput  true if the country_code_source and preferred_carrier_code fields of
     *     phoneNumber should be populated.
     * @param phoneNumber  the PhoneNumber object where the country_code and country_code_source need
     *     to be populated. Note the country_code is always populated, whereas country_code_source is
     *     only populated when keepCountryCodeSource is true.
     * @return  the country calling code extracted or 0 if none could be extracted
     */
    // @VisibleForTesting
    int maybeExtractCountryCode(String number, PhoneMetadata defaultRegionMetadata,
                                StringBuilder nationalNumber, boolean keepRawInput,
                                PhoneNumber phoneNumber)
            throws NumberParseException {
        if (number.length() == 0) {
            return 0;
        }
        StringBuilder fullNumber = new StringBuilder(number);
        // Set the default prefix to be something that will never match.
        String possibleCountryIddPrefix = "NonMatch";
        if (defaultRegionMetadata != null) {
            possibleCountryIddPrefix = defaultRegionMetadata.internationalPrefix;
        }

        CountryCodeSource countryCodeSource =
                maybeStripInternationalPrefixAndNormalize(fullNumber, possibleCountryIddPrefix);
        if (keepRawInput) {
            phoneNumber.setCountryCodeSource(countryCodeSource);
        }
        if (countryCodeSource != CountryCodeSource.FROM_DEFAULT_COUNTRY) {
            if (fullNumber.length() <= MIN_LENGTH_FOR_NSN) {
                throw new NumberParseException(NumberParseException.ErrorType.TOO_SHORT_AFTER_IDD,
                        "Phone number had an IDD, but after this was not "
                                + "long enough to be a viable phone number.");
            }
            int potentialCountryCode = extractCountryCode(fullNumber, nationalNumber);
            if (potentialCountryCode != 0) {
                phoneNumber.setCountryCode(potentialCountryCode);
                return potentialCountryCode;
            }

            // If this fails, they must be using a strange country calling code that we don't recognize,
            // or that doesn't exist.
            throw new NumberParseException(NumberParseException.ErrorType.INVALID_COUNTRY_CODE,
                    "Country calling code supplied was not recognised.");
        } else if (defaultRegionMetadata != null) {
            // Check to see if the number starts with the country calling code for the default region. If
            // so, we remove the country calling code, and do some checks on the validity of the number
            // before and after.
            int defaultCountryCode = defaultRegionMetadata.countryCode;
            String defaultCountryCodeString = String.valueOf(defaultCountryCode);
            String normalizedNumber = fullNumber.toString();
            if (normalizedNumber.startsWith(defaultCountryCodeString)) {
                StringBuilder potentialNationalNumber =
                        new StringBuilder(normalizedNumber.substring(defaultCountryCodeString.length()));
                Pattern validNumberPattern =
                        regexCache.getPatternForRegex(defaultRegionMetadata.generalDesc);
                // Removed processing of national prefix - not applicable
                //maybeStripNationalPrefixAndCarrierCode(
                //        potentialNationalNumber, defaultRegionMetadata, null /* Don't need the carrier code */);
                Pattern possibleNumberPattern =
                        regexCache.getPatternForRegex(defaultRegionMetadata.generalDescPossible);
                // If the number was not valid before but is valid now, or if it was too long before, we
                // consider the number with the country calling code stripped to be a better result and
                // keep that instead.
                if ((!validNumberPattern.matcher(fullNumber).matches() &&
                        validNumberPattern.matcher(potentialNationalNumber).matches()) ||
                        testNumberLengthAgainstPattern(possibleNumberPattern, fullNumber.toString())
                                == ValidationResult.TOO_LONG) {
                    nationalNumber.append(potentialNationalNumber);
                    if (keepRawInput) {
                        phoneNumber.setCountryCodeSource(CountryCodeSource.FROM_NUMBER_WITHOUT_PLUS_SIGN);
                    }
                    phoneNumber.setCountryCode(defaultCountryCode);
                    return defaultCountryCode;
                }
            }
        }
        // No country calling code present.
        phoneNumber.setCountryCode(0);
        return 0;
    }

    /**
     * Strips the IDD from the start of the number if present. Helper function used by
     * maybeStripInternationalPrefixAndNormalize.
     */
    private boolean parsePrefixAsIdd(Pattern iddPattern, StringBuilder number) {
        Matcher m = iddPattern.matcher(number);
        if (m.lookingAt()) {
            int matchEnd = m.end();
            // Only strip this if the first digit after the match is not a 0, since country calling codes
            // cannot begin with 0.
            Matcher digitMatcher = CAPTURING_DIGIT_PATTERN.matcher(number.substring(matchEnd));
            if (digitMatcher.find()) {
                String normalizedGroup = normalizeDigitsOnly(digitMatcher.group(1));
                if (normalizedGroup.equals("0")) {
                    return false;
                }
            }
            number.delete(0, matchEnd);
            return true;
        }
        return false;
    }

    /**
     * Strips any international prefix (such as +, 00, 011) present in the number provided, normalizes
     * the resulting number, and indicates if an international prefix was present.
     *
     * @param number  the non-normalized telephone number that we wish to strip any international
     *     dialing prefix from.
     * @param possibleIddPrefix  the international direct dialing prefix from the region we
     *     think this number may be dialed in
     * @return  the corresponding CountryCodeSource if an international dialing prefix could be
     *     removed from the number, otherwise CountryCodeSource.FROM_DEFAULT_COUNTRY if the number did
     *     not seem to be in international format.
     */
    // @VisibleForTesting
    CountryCodeSource maybeStripInternationalPrefixAndNormalize(
            StringBuilder number,
            String possibleIddPrefix) {
        if (number.length() == 0) {
            return CountryCodeSource.FROM_DEFAULT_COUNTRY;
        }
        // Check to see if the number begins with one or more plus signs.
        Matcher m = PLUS_CHARS_PATTERN.matcher(number);
        if (m.lookingAt()) {
            number.delete(0, m.end());
            // Can now normalize the rest of the number since we've consumed the "+" sign at the start.
            normalize(number);
            return CountryCodeSource.FROM_NUMBER_WITH_PLUS_SIGN;
        }
        // Attempt to parse the first digits as an international prefix.
        Pattern iddPattern = regexCache.getPatternForRegex(possibleIddPrefix);
        normalize(number);
        return parsePrefixAsIdd(iddPattern, number)
                ? CountryCodeSource.FROM_NUMBER_WITH_IDD
                : CountryCodeSource.FROM_DEFAULT_COUNTRY;
    }

    /**
     * Strips any national prefix (such as 0, 1) present in the number provided.
     *
     * @param number  the normalized telephone number that we wish to strip any national
     *     dialing prefix from
     * @param metadata  the metadata for the region that we think this number is from
     * @param carrierCode  a place to insert the carrier code if one is extracted
     * @return true if a national prefix or carrier code (or both) could be extracted.
     */
    /*
    // @VisibleForTesting
    boolean maybeStripNationalPrefixAndCarrierCode(
            StringBuilder number, PhoneMetadata metadata, StringBuilder carrierCode) {
        int numberLength = number.length();
        String possibleNationalPrefix = metadata.nationalPrefixForParsing;
        if (numberLength == 0 || possibleNationalPrefix.length() == 0) {
            // Early return for numbers of zero length.
            return false;
        }
        // Attempt to parse the first digits as a national prefix.
        Matcher prefixMatcher = regexCache.getPatternForRegex(possibleNationalPrefix).matcher(number);
        if (prefixMatcher.lookingAt()) {
            Pattern nationalNumberRule =
                    regexCache.getPatternForRegex(metadata.generalDesc.nationalNumberPattern);
            // Check if the original number is viable.
            boolean isViableOriginalNumber = nationalNumberRule.matcher(number).matches();
            // prefixMatcher.group(numOfGroups) == null implies nothing was captured by the capturing
            // groups in possibleNationalPrefix; therefore, no transformation is necessary, and we just
            // remove the national prefix.
            int numOfGroups = prefixMatcher.groupCount();
            String transformRule = metadata.nationalPrefixTransformRule;
            if (transformRule == null || transformRule.length() == 0 ||
                    prefixMatcher.group(numOfGroups) == null) {
                // If the original number was viable, and the resultant number is not, we return.
                if (isViableOriginalNumber &&
                        !nationalNumberRule.matcher(number.substring(prefixMatcher.end())).matches()) {
                    return false;
                }
                if (carrierCode != null && numOfGroups > 0 && prefixMatcher.group(numOfGroups) != null) {
                    carrierCode.append(prefixMatcher.group(1));
                }
                number.delete(0, prefixMatcher.end());
                return true;
            } else {
                // Check that the resultant number is still viable. If not, return. Check this by copying
                // the string buffer and making the transformation on the copy first.
                StringBuilder transformedNumber = new StringBuilder(number);
                transformedNumber.replace(0, numberLength, prefixMatcher.replaceFirst(transformRule));
                if (isViableOriginalNumber &&
                        !nationalNumberRule.matcher(transformedNumber.toString()).matches()) {
                    return false;
                }
                if (carrierCode != null && numOfGroups > 1) {
                    carrierCode.append(prefixMatcher.group(1));
                }
                number.replace(0, number.length(), transformedNumber.toString());
                return true;
            }
        }
        return false;
    }

    */
    /**
     * Checks to see that the region code used is valid, or if it is not valid, that the number to
     * parse starts with a + symbol so that we can attempt to infer the region from the number.
     * Returns false if it cannot use the region provided and the region cannot be inferred.
     */
    private boolean checkRegionForParsing(String numberToParse, String defaultRegion) {
        if (!isValidRegionCode(defaultRegion)) {
            // If the number is null or empty, we can't infer the region.
            if ((numberToParse == null) || (numberToParse.length() == 0) ||
                    !PLUS_CHARS_PATTERN.matcher(numberToParse).lookingAt()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Parses a string and returns it in proto buffer format. This method will throw a
     * {@link com.headuck.phonenumbers.NumberParseException} if the number is not considered to be
     * a possible number. Note that validation of whether the number is actually a valid number for a
     * particular region is not performed. This can be done separately with {@link #isValidNumber}.
     *
     * @param numberToParse     number that we are attempting to parse. This can contain formatting
     *                          such as +, ( and -, as well as a phone number extension. It can also
     *                          be provided in RFC3966 format.
     * @param defaultRegion     region that we are expecting the number to be from. This is only used
     *                          if the number being parsed is not written in international format.
     *                          The country_code for the number in this case would be stored as that
     *                          of the default region supplied. If the number is guaranteed to
     *                          start with a '+' followed by the country calling code, then
     *                          "ZZ" or null can be supplied.
     * @return                  a phone number proto buffer filled with the parsed number
     * @throws NumberParseException  if the string is not considered to be a viable phone number or if
     *                               no default region was supplied and the number is not in
     *                               international format (does not start with +)
     */
    public PhoneNumber parse(String numberToParse, String defaultRegion)
            throws NumberParseException {
        PhoneNumber phoneNumber = new PhoneNumber();
        parse(numberToParse, defaultRegion, phoneNumber);
        return phoneNumber;
    }

    /**
     * Same as {@link #parse(String, String)}, but accepts mutable PhoneNumber as a parameter to
     * decrease object creation when invoked many times.
     */
    public void parse(String numberToParse, String defaultRegion, PhoneNumber phoneNumber)
            throws NumberParseException {
        parseHelper(numberToParse, defaultRegion, false, true, phoneNumber);
    }

    /**
     * Parses a string and returns it in proto buffer format. This method differs from {@link #parse}
     * in that it always populates the raw_input field of the protocol buffer with numberToParse as
     * well as the country_code_source field.
     *
     * @param numberToParse     number that we are attempting to parse. This can contain formatting
     *                          such as +, ( and -, as well as a phone number extension.
     * @param defaultRegion     region that we are expecting the number to be from. This is only used
     *                          if the number being parsed is not written in international format.
     *                          The country calling code for the number in this case would be stored
     *                          as that of the default region supplied.
     * @return                  a phone number proto buffer filled with the parsed number
     * @throws NumberParseException  if the string is not considered to be a viable phone number or if
     *                               no default region was supplied
     */
    public PhoneNumber parseAndKeepRawInput(String numberToParse, String defaultRegion)
            throws NumberParseException {
        PhoneNumber phoneNumber = new PhoneNumber();
        parseAndKeepRawInput(numberToParse, defaultRegion, phoneNumber);
        return phoneNumber;
    }

    /**
     * Same as{@link #parseAndKeepRawInput(String, String)}, but accepts a mutable PhoneNumber as
     * a parameter to decrease object creation when invoked many times.
     */
    public void parseAndKeepRawInput(String numberToParse, String defaultRegion,
                                     PhoneNumber phoneNumber)
            throws NumberParseException {
        parseHelper(numberToParse, defaultRegion, true, true, phoneNumber);
    }


    /**
     * A helper function to set the values related to leading zeros in a PhoneNumber.
     */
    static void setItalianLeadingZerosForPhoneNumber(String nationalNumber, PhoneNumber phoneNumber) {
        if (nationalNumber.length() > 1 && nationalNumber.charAt(0) == '0') {
            phoneNumber.setItalianLeadingZero(true);
            int numberOfLeadingZeros = 1;
            // Note that if the national number is all "0"s, the last "0" is not counted as a leading
            // zero.
            while (numberOfLeadingZeros < nationalNumber.length() - 1 &&
                    nationalNumber.charAt(numberOfLeadingZeros) == '0') {
                numberOfLeadingZeros++;
            }
            if (numberOfLeadingZeros != 1) {
                phoneNumber.setNumberOfLeadingZeros(numberOfLeadingZeros);
            }
        }
    }

    /**
     * Parses a string and fills up the phoneNumber. This method is the same as the public
     * parse() method, with the exception that it allows the default region to be null, for use by
     * isNumberMatch(). checkRegion should be set to false if it is permitted for the default region
     * to be null or unknown ("ZZ").
     */
    private void parseHelper(String numberToParse, String defaultRegion, boolean keepRawInput,
                             boolean checkRegion, PhoneNumber phoneNumber)
            throws NumberParseException {
        if (numberToParse == null) {
            throw new NumberParseException(NumberParseException.ErrorType.NOT_A_NUMBER,
                    "The phone number supplied was null.");
        } else if (numberToParse.length() > MAX_INPUT_STRING_LENGTH) {
            throw new NumberParseException(NumberParseException.ErrorType.TOO_LONG,
                    "The string supplied was too long to parse.");
        }

        StringBuilder nationalNumber = new StringBuilder(numberToParse);

        if (!isViablePhoneNumber(nationalNumber.toString())) {
            throw new NumberParseException(NumberParseException.ErrorType.NOT_A_NUMBER,
                    "The string supplied did not seem to be a phone number.");
        }

        // Check the region supplied is valid, or that the extracted number starts with some sort of +
        // sign so the number's region can be determined.
        if (checkRegion && !checkRegionForParsing(nationalNumber.toString(), defaultRegion)) {
            throw new NumberParseException(NumberParseException.ErrorType.INVALID_COUNTRY_CODE,
                    "Missing or invalid default region.");
        }

        if (keepRawInput) {
            phoneNumber.setRawInput(numberToParse);
        }
        // Extension parsing removed

        PhoneMetadata regionMetadata = getMetadataForRegion(defaultRegion);
        // Check to see if the number is given in international format so we know whether this number is
        // from the default region or not.
        StringBuilder normalizedNationalNumber = new StringBuilder();
        int countryCode = 0;
        try {
            // TODO: This method should really just take in the string buffer that has already
            // been created, and just remove the prefix, rather than taking in a string and then
            // outputting a string buffer.
            countryCode = maybeExtractCountryCode(nationalNumber.toString(), regionMetadata,
                    normalizedNationalNumber, keepRawInput, phoneNumber);
        } catch (NumberParseException e) {
            Matcher matcher = PLUS_CHARS_PATTERN.matcher(nationalNumber.toString());
            if (e.getErrorType() == NumberParseException.ErrorType.INVALID_COUNTRY_CODE &&
                    matcher.lookingAt()) {
                // Strip the plus-char, and try again.
                countryCode = maybeExtractCountryCode(nationalNumber.substring(matcher.end()),
                        regionMetadata, normalizedNationalNumber,
                        keepRawInput, phoneNumber);
                if (countryCode == 0) {
                    throw new NumberParseException(NumberParseException.ErrorType.INVALID_COUNTRY_CODE,
                            "Could not interpret numbers after plus-sign.");
                }
            } else {
                throw new NumberParseException(e.getErrorType(), e.getMessage());
            }
        }
        if (countryCode != 0) {
            String phoneNumberRegion = getRegionCodeForCountryCode(countryCode);
            if (!phoneNumberRegion.equals(defaultRegion)) {
                // Metadata cannot be null because the country calling code is valid.
                regionMetadata = getMetadataForRegionOrCallingCode(countryCode, phoneNumberRegion);
            }
        } else {
            // If no extracted country calling code, use the region supplied instead. The national number
            // is just the normalized version of the number we were given to parse.
            normalize(nationalNumber);
            normalizedNationalNumber.append(nationalNumber);
            if (defaultRegion != null) {
                countryCode = regionMetadata.countryCode;
                phoneNumber.setCountryCode(countryCode);
            } else if (keepRawInput) {
                phoneNumber.clearCountryCodeSource();
            }
        }
        if (normalizedNationalNumber.length() < MIN_LENGTH_FOR_NSN) {
            throw new NumberParseException(NumberParseException.ErrorType.TOO_SHORT_NSN,
                    "The string supplied is too short to be a phone number.");
        }
        if (regionMetadata != null) {
            // Removed:
            // StringBuilder carrierCode = new StringBuilder();
            StringBuilder potentialNationalNumber = new StringBuilder(normalizedNationalNumber);

            // Removed:
            // maybeStripNationalPrefixAndCarrierCode(potentialNationalNumber, regionMetadata, carrierCode);

            // We require that the NSN remaining after stripping the national prefix and carrier code be
            // of a possible length for the region. Otherwise, we don't do the stripping, since the
            // original number could be a valid short number.
            if (!isShorterThanPossibleNormalNumber(regionMetadata, potentialNationalNumber.toString())) {
                normalizedNationalNumber = potentialNationalNumber;

                // Removed:
                // if (keepRawInput) {
                //     phoneNumber.setPreferredDomesticCarrierCode(carrierCode.toString());
                // }
            }
        }
        int lengthOfNationalNumber = normalizedNationalNumber.length();
        if (lengthOfNationalNumber < MIN_LENGTH_FOR_NSN) {
            throw new NumberParseException(NumberParseException.ErrorType.TOO_SHORT_NSN,
                    "The string supplied is too short to be a phone number.");
        }
        if (lengthOfNationalNumber > MAX_LENGTH_FOR_NSN) {
            throw new NumberParseException(NumberParseException.ErrorType.TOO_LONG,
                    "The string supplied is too long to be a phone number.");
        }
        setItalianLeadingZerosForPhoneNumber(normalizedNationalNumber.toString(), phoneNumber);
        phoneNumber.setNationalNumber(Long.parseLong(normalizedNationalNumber.toString()));
    }



}
