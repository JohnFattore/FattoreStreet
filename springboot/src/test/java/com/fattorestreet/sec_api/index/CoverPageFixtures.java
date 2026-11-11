package com.fattorestreet.sec_api.index;

/**
 * Builds minimal XBRL instance documents shaped like real 10-K/10-Q cover pages, so tests don't need
 * multi-megabyte filings on disk.
 */
final class CoverPageFixtures {

    static final String CLASS_A = "us-gaap:CommonClassAMember";
    static final String CLASS_B = "us-gaap:CommonClassBMember";

    private CoverPageFixtures() {
    }

    /** One cover-page fact: a share count or a trading symbol, in a context with zero or one class member. */
    record Fact(String element, String value, String member, String date) {

        static Fact shares(String member, long shares) {
            return new Fact("EntityCommonStockSharesOutstanding", Long.toString(shares), member, "2026-10-28");
        }

        static Fact symbol(String member, String symbol) {
            return new Fact("TradingSymbol", symbol, member, "2026-09-30");
        }
    }

    static String instance(Fact... facts) {
        StringBuilder sb = new StringBuilder("""
                <?xml version="1.0" encoding="utf-8"?>
                <xbrli:xbrl xmlns:xbrli="http://www.xbrl.org/2003/instance"
                    xmlns:xbrldi="http://xbrl.org/2006/xbrldi"
                    xmlns:dei="http://xbrl.sec.gov/dei/2024"
                    xmlns:us-gaap="http://fasb.org/us-gaap/2024">
                """);
        for (int i = 0; i < facts.length; i++) {
            Fact f = facts[i];
            sb.append("<xbrli:context id=\"c").append(i).append("\"><xbrli:entity>")
                    .append("<xbrli:identifier scheme=\"http://www.sec.gov/CIK\">1</xbrli:identifier>");
            if (f.member() != null) {
                sb.append("<xbrli:segment><xbrldi:explicitMember dimension=\"us-gaap:StatementClassOfStockAxis\">")
                        .append(f.member()).append("</xbrldi:explicitMember></xbrli:segment>");
            }
            sb.append("</xbrli:entity><xbrli:period><xbrli:instant>").append(f.date())
                    .append("</xbrli:instant></xbrli:period></xbrli:context>\n");
            sb.append("<dei:").append(f.element()).append(" contextRef=\"c").append(i).append("\">")
                    .append(f.value()).append("</dei:").append(f.element()).append(">\n");
        }
        return sb.append("</xbrli:xbrl>\n").toString();
    }
}
