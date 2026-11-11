package com.fattorestreet.sec_api.index;

import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import com.fattorestreet.sec_api.util.TickerFormats;

/**
 * Per-class shares outstanding from a 10-K/10-Q cover page (the filing's XBRL instance).
 *
 * <p>A multi-class filer reports {@code dei:EntityCommonStockSharesOutstanding} once per class, each in a
 * context carrying a {@code StatementClassOfStockAxis} member, and SEC's companyfacts API drops every
 * dimensioned fact. So for Berkshire, Visa or Alphabet companyfacts holds no current share count at all,
 * only an undimensioned figure from before cover pages were tagged per class (Berkshire's is 2011's
 * Class A count). The cover page also tags each listed class's {@code dei:TradingSymbol} with the same
 * class member, which is what ties a share count to a ticker.
 *
 * <p>A filer with one listed class sometimes tags its symbol with no class member while still splitting
 * shares by class (Meta: symbol {@code META}, shares for Class A and the unlisted Class B). That symbol
 * is taken to be Class A when the filing reports one, otherwise the class with the most shares.
 *
 * @param classShares   latest shares outstanding by class member (e.g. {@code us-gaap:CommonClassBMember})
 * @param classBySymbol class member by compact trading symbol ({@code BRKB}), for class-tagged symbols
 * @param plainSymbols  compact trading symbols tagged with no class member
 * @param plainShares   shares outstanding tagged with no class member, or {@code null}
 */
public record CoverPageShares(
        Map<String, ClassShares> classShares,
        Map<String, String> classBySymbol,
        List<String> plainSymbols,
        Long plainShares) {

    /** A filing with no usable cover-page facts. */
    public static final CoverPageShares EMPTY = new CoverPageShares(Map.of(), Map.of(), List.of(), null);

    private static final String CLASS_AXIS_SUFFIX = "StatementClassOfStockAxis";
    private static final String CLASS_A_MEMBER = "us-gaap:CommonClassAMember";
    private static final String XBRLI_NS = "http://www.xbrl.org/2003/instance";
    private static final String DEI_NS_PREFIX = "http://xbrl.sec.gov/dei/";
    /**
     * JDK 24+ secure-processing limits that the predefined less-than and greater-than references in XBRL
     * text-block facts (whole notes as escaped HTML) exceed. Raised to a bound well past the largest
     * instance seen (Berkshire's, about 8 MB) rather than removed.
     */
    private static final Map<String, String> RAISED_ENTITY_LIMITS = Map.of(
            "jdk.xml.maxGeneralEntitySizeLimit", String.valueOf(64 * 1024 * 1024),
            "jdk.xml.totalEntitySizeLimit", String.valueOf(64 * 1024 * 1024),
            "jdk.xml.entityExpansionLimit", String.valueOf(16 * 1024 * 1024));

    public CoverPageShares {
        classShares = Map.copyOf(classShares);
        classBySymbol = Map.copyOf(classBySymbol);
        plainSymbols = List.copyOf(plainSymbols);
    }

    public record ClassShares(long shares, LocalDate asOf) {
    }

    /** A listed class: its compact trading symbol and shares outstanding. */
    public record ListedClass(String compactSymbol, long shares) {
    }

    /** Whether the filing splits shares outstanding by class. */
    public boolean hasClassShares() {
        return !classShares.isEmpty();
    }

    /**
     * Shares outstanding of the class a listing ticker trades as, or {@code null} when the cover page
     * doesn't name that ticker. Matches on the compact form, since filers write {@code BRK.B}, {@code BFB}
     * or {@code LEN.B} where the SEC ticker index writes {@code BRK-B}, {@code BF-B} and {@code LEN-B}.
     */
    public Long sharesFor(String listingTicker) {
        String key = TickerFormats.compact(listingTicker);
        if (key == null) {
            return null;
        }
        String member = classBySymbol.get(key);
        if (member == null && plainSymbols.contains(key)) {
            member = plainSymbolClass().orElse(null);
            if (member == null) {
                return plainShares;
            }
        }
        ClassShares cs = member != null ? classShares.get(member) : null;
        return cs != null ? cs.shares() : null;
    }

    /** Every class the cover page lists a trading symbol for, with its shares outstanding. */
    public List<ListedClass> listedClasses() {
        List<ListedClass> out = new ArrayList<>();
        classBySymbol.forEach((symbol, member) -> {
            ClassShares cs = classShares.get(member);
            if (cs != null) {
                out.add(new ListedClass(symbol, cs.shares()));
            }
        });
        for (String symbol : plainSymbols) {
            Long shares = sharesFor(symbol);
            if (shares != null) {
                out.add(new ListedClass(symbol, shares));
            }
        }
        out.sort(Comparator.comparing(ListedClass::compactSymbol));
        return out;
    }

    private Optional<String> plainSymbolClass() {
        if (classShares.isEmpty()) {
            return Optional.empty();
        }
        if (classShares.containsKey(CLASS_A_MEMBER)) {
            return Optional.of(CLASS_A_MEMBER);
        }
        return classShares.entrySet().stream()
                .max(Comparator.comparingLong((Map.Entry<String, ClassShares> e) -> e.getValue().shares())
                        .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey);
    }

    /**
     * Parses an XBRL instance document. Contexts with any dimension other than the class-of-stock axis
     * (a legal-entity member, say) are ignored, as are symbols for classes the filing reports no shares
     * for, such as listed notes and preferred stock.
     */
    public static CoverPageShares parse(String instanceXml) throws XMLStreamException {
        if (instanceXml == null || instanceXml.isBlank()) {
            return EMPTY;
        }
        Map<String, ContextInfo> contexts = new HashMap<>();
        List<Fact> shareFacts = new ArrayList<>();
        List<Fact> symbolFacts = new ArrayList<>();

        // Filing XML is untrusted input: no DTDs, no external entities (XXE). Configured here, in the
        // method that creates the reader, which is where FindSecBugs looks for it.
        // The JDK's own implementation, not whatever StAX provider is on the classpath: the jdk.xml limits
        // below are JDK properties, and another provider (Woodstox) would reject them.
        XMLInputFactory factory = XMLInputFactory.newDefaultFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        // With DTDs off no custom entity can be declared, so these limits only ever count the predefined
        // ones; at the JDK defaults Berkshire's 10-Q fails to parse.
        RAISED_ENTITY_LIMITS.forEach(factory::setProperty);
        XMLStreamReader r = factory.createXMLStreamReader(new StringReader(instanceXml));
        try {
            String contextId = null;
            List<String[]> dims = null;
            LocalDate date = null;
            while (r.hasNext()) {
                int ev = r.next();
                if (ev == XMLStreamConstants.START_ELEMENT) {
                    String ns = r.getNamespaceURI();
                    String local = r.getLocalName();
                    if (XBRLI_NS.equals(ns) && "context".equals(local)) {
                        contextId = r.getAttributeValue(null, "id");
                        dims = new ArrayList<>();
                        date = null;
                    } else if (contextId != null && "explicitMember".equals(local)) {
                        String dim = r.getAttributeValue(null, "dimension");
                        dims.add(new String[] {dim, r.getElementText().trim()});
                    } else if (contextId != null && XBRLI_NS.equals(ns)
                            && ("instant".equals(local) || "endDate".equals(local))) {
                        date = LocalDate.parse(r.getElementText().trim());
                    } else if (ns != null && ns.startsWith(DEI_NS_PREFIX)
                            && ("EntityCommonStockSharesOutstanding".equals(local) || "TradingSymbol".equals(local))) {
                        String ref = r.getAttributeValue(null, "contextRef");
                        String text = r.getElementText().trim();
                        if (ref != null && !text.isEmpty()) {
                            ("TradingSymbol".equals(local) ? symbolFacts : shareFacts).add(new Fact(ref, text));
                        }
                    }
                } else if (ev == XMLStreamConstants.END_ELEMENT && contextId != null
                        && XBRLI_NS.equals(r.getNamespaceURI()) && "context".equals(r.getLocalName())) {
                    contexts.put(contextId, ContextInfo.of(dims, date));
                    contextId = null;
                }
            }
        } finally {
            r.close();
        }

        Map<String, ClassShares> classShares = new LinkedHashMap<>();
        Long plainShares = null;
        LocalDate plainSharesDate = null;
        for (Fact f : shareFacts) {
            ContextInfo c = contexts.get(f.contextRef());
            if (c == null || !c.usable()) {
                continue;
            }
            long shares;
            try {
                shares = new BigDecimal(f.value()).longValue();
            } catch (NumberFormatException e) {
                // One malformed fact loses that class, not the whole cover page.
                continue;
            }
            if (c.classMember() == null) {
                if (plainSharesDate == null || (c.date() != null && c.date().isAfter(plainSharesDate))) {
                    plainShares = shares;
                    plainSharesDate = c.date();
                }
                continue;
            }
            ClassShares prior = classShares.get(c.classMember());
            if (prior == null || (c.date() != null && (prior.asOf() == null || c.date().isAfter(prior.asOf())))) {
                classShares.put(c.classMember(), new ClassShares(shares, c.date()));
            }
        }

        Map<String, String> classBySymbol = new HashMap<>();
        List<String> plainSymbols = new ArrayList<>();
        for (Fact f : symbolFacts) {
            ContextInfo c = contexts.get(f.contextRef());
            if (c == null || !c.usable()) {
                continue;
            }
            String symbol = TickerFormats.compact(f.value().toUpperCase(Locale.ROOT));
            if (c.classMember() == null) {
                plainSymbols.add(symbol);
            } else if (classShares.containsKey(c.classMember())) {
                classBySymbol.put(symbol, c.classMember());
            }
        }
        return new CoverPageShares(classShares, classBySymbol, plainSymbols, plainShares);
    }

    private record Fact(String contextRef, String value) {
    }

    /**
     * @param classMember the class-of-stock member, or {@code null} when the context has no dimensions
     * @param usable      {@code false} when the context carries a dimension other than the class axis
     */
    private record ContextInfo(String classMember, LocalDate date, boolean usable) {
        static ContextInfo of(List<String[]> dims, LocalDate date) {
            String member = null;
            for (String[] d : dims) {
                if (d[0] == null || !d[0].endsWith(CLASS_AXIS_SUFFIX) || member != null) {
                    return new ContextInfo(null, date, false);
                }
                member = d[1];
            }
            return new ContextInfo(member, date, true);
        }
    }
}
