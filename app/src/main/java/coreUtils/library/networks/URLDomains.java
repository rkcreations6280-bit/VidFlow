package coreUtils.library.networks;

/**
 * Utility class providing a comprehensive collection of top-level domains (TLDs) and
 * country code top-level domains (ccTLDs) used for network communications and URL parsing.
 * <p>
 * This class serves as a centralized repository of domain extensions that are commonly
 * encountered when parsing, validating, or processing URLs in network-related operations.
 * The constants provided here are particularly useful for:
 * </p>
 * <ul>
 *   <li><b>URL Validation:</b> Checking if a domain has a valid and recognized TLD</li>
 *   <li><b>Domain Parsing:</b> Extracting the TLD portion from a fully qualified domain name</li>
 *   <li><b>Link Processing:</b> Identifying domain types for categorization or filtering</li>
 *   <li><b>Content Filtering:</b> Determining if a URL belongs to specific domain categories</li>
 * </ul>
 * <p>
 * The domain list is maintained as a static final array for memory efficiency and
 * fast iteration. All entries are stored in lowercase with a leading dot (".")
 * prefix to facilitate easy matching and parsing operations.
 * </p>
 * <p>
 * This class is designed to be used statically and should not be instantiated.
 * All constants are public, static, and final, ensuring thread-safety and
 * immutability across the application.
 * </p>
 *
 * <p><b>Note:</b> The domain list is comprehensive but may not be exhaustive.
 * New TLDs are regularly added by ICANN and may require manual updates to this list.</p>
 *
 * @see java.net.URL
 * @see java.net.URI
 */
public class URLDomains {

    /**
     * A comprehensive list of top-level domains (TLDs) and country code top-level domains (ccTLDs).
     * <p>
     * This array contains over 300 domain extensions including:
     * </p>
     * <ul>
     *   <li>Generic TLDs (gTLDs) like .com, .net, .org, .io, .biz, .info</li>
     *   <li>Sponsored TLDs (sTLDs) like .aero, .asia, .cat, .coop, .jobs, .museum, .post, .travel, .xxx</li>
     *   <li>Country code TLDs (ccTLDs) for all recognized countries and territories</li>
     *   <li>Composite domains such as .com.au, .co.uk, .gov.in, .edu domains, etc.</li>
     * </ul>
     * <p>
     * Each entry in this array follows these conventions:
     * </p>
     * <ul>
     *   <li>Starts with a dot (.) to facilitate domain matching</li>
     *   <li>Written in lowercase for consistent comparison</li>
     *   <li>Does not include the protocol (http://, https://) or subdomain portions</li>
     * </ul>
     * <p>
     * This list is frequently used in URL validation logic to verify that a domain name
     * ends with a recognized and legitimate TLD. It can also be used for parsing URLs
     * to extract the domain extension or to categorize domains by their TLD type.
     * </p>
     *
     * <p><b>Performance Note:</b> For optimal performance when searching this array,
     * consider using a {@link java.util.HashSet} for O(1) lookups if performing
     * repeated validations on many URLs.</p>
     *
     * @see #TOP_LEVEL_DOMAINS
     */
    public static final String[] TOP_LEVEL_DOMAINS = {
            ".com", ".net", ".org", ".io", ".co", ".biz", ".info", ".mobi", ".name", ".pro",
            ".tel", ".travel", ".xxx", ".aero", ".asia", ".cat", ".coop", ".jobs", ".museum",
            ".post", ".ac", ".ad", ".ae", ".af", ".ag", ".ai", ".al", ".am", ".ao", ".aq",
            ".at", ".au", ".aw", ".ax", ".az", ".ba", ".bb", ".bd", ".be", ".bf", ".bg", ".bh",
            ".bi", ".bj", ".bl", ".bm", ".bn", ".bo", ".bq", ".br", ".bs", ".bt", ".bv", ".bw",
            ".by", ".bz", ".ca", ".cc", ".cd", ".cf", ".cg", ".ch", ".ci", ".ck", ".cl", ".cm",
            ".cn", ".co", ".cr", ".cu", ".cv", ".cw", ".cx", ".cy", ".cz", ".de", ".dj", ".dk",
            ".dm", ".do", ".dz", ".ec", ".ee", ".eg", ".eh", ".er", ".es", ".et", ".eu", ".fi",
            ".fj", ".fk", ".fm", ".fo", ".fr", ".ga", ".gb", ".gd", ".ge", ".gf", ".gg", ".gh",
            ".gi", ".gl", ".gm", ".gn", ".gp", ".gq", ".gr", ".gt", ".gu", ".gw", ".gy", ".hk",
            ".hm", ".hn", ".hr", ".ht", ".hu", ".id", ".ie", ".il", ".im", ".in", ".io", ".iq",
            ".ir", ".is", ".it", ".je", ".jm", ".jo", ".jp", ".ke", ".kg", ".kh", ".ki", ".km",
            ".kn", ".kp", ".kr", ".kw", ".ky", ".kz", ".la", ".lb", ".lc", ".li", ".lk", ".lr",
            ".ls", ".lt", ".lu", ".lv", ".ly", ".ma", ".mc", ".md", ".me", ".mf", ".mg", ".mh",
            ".mk", ".ml", ".mm", ".mn", ".mo", ".mp", ".mq", ".mr", ".ms", ".mt", ".mu", ".mv",
            ".mw", ".mx", ".my", ".mz", ".na", ".nc", ".ne", ".nf", ".ng", ".ni", ".nl", ".no",
            ".np", ".nr", ".nu", ".nz", ".om", ".pa", ".pe", ".pf", ".pg", ".ph", ".pk", ".pl",
            ".pm", ".pn", ".pr", ".ps", ".pt", ".pw", ".py", ".qa", ".re", ".ro", ".rs", ".ru",
            ".rw", ".sa", ".sb", ".sc", ".sd", ".se", ".sg", ".sh", ".si", ".sj", ".sk", ".sl",
            ".sm", ".sn", ".so", ".sr", ".ss", ".st", ".su", ".sv", ".sx", ".sy", ".sz", ".tc",
            ".td", ".tf", ".tg", ".th", ".tj", ".tk", ".tl", ".tm", ".tn", ".to", ".tr", ".tt",
            ".tv", ".tw", ".tz", ".ua", ".ug", ".uk", ".us", ".uy", ".uz", ".va", ".vc", ".ve",
            ".vg", ".vi", ".vn", ".vu", ".wf", ".ws", ".ye", ".yt", ".za", ".zm", ".zw", ".com.au",
            ".com.br", ".com.cn", ".com.fr", ".com.de", ".com.in", ".com.jp", ".com.mx", ".com.ru",
            ".com.uk", ".gov.au", ".gov.br", ".gov.cn", ".gov.fr", ".gov.de", ".gov.in", ".gov.jp",
            ".gov.mx", ".gov.ru", ".gov.uk", ".co.uk", ".co.in", ".co.jp", ".co.za", ".co.nz",
            ".co.il", ".co.kr", ".eu", ".asia", ".africa", ".edu", ".gov", ".mil", ".ar", ".as",
    };
}