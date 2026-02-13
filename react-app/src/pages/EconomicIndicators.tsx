import { useMemo } from "react";
import { Row, Col, Alert, Container } from "react-bootstrap";
import GenericLineChart from "../components/GenericLineChart";
import LoadingModal from "../components/LoadingModal";
import { useGetFredDataQuery } from "../functions/api";
import { getErrorMessages } from "../functions/helperFunctions";

export default function EconomicIndicators() {
  const seriesList = [
    // Interest Rates & Yield Curve
    { series_id: "DGS2", compute_yoy: false },
    { series_id: "DGS10", compute_yoy: false },
    { series_id: "DGS30", compute_yoy: false },
    { series_id: "T10Y2Y", compute_yoy: false },
    { series_id: "FEDFUNDS", compute_yoy: false },
    // Inflation
    { series_id: "CPIAUCSL", compute_yoy: true },
    { series_id: "PCEPILFE", compute_yoy: true },
    { series_id: "T10YIE", compute_yoy: false },
    // Labor Market
    { series_id: "UNRATE", compute_yoy: false },
    { series_id: "ICSA", compute_yoy: false },
    { series_id: "PAYEMS", compute_yoy: true },
    // Economic Growth & Consumer
    { series_id: "GDP", compute_yoy: true },
    { series_id: "INDPRO", compute_yoy: true },
    { series_id: "RSAFS", compute_yoy: true },
    { series_id: "UMCSENT", compute_yoy: false },
    // Housing
    { series_id: "MORTGAGE30US", compute_yoy: false },
    { series_id: "MORTGAGE15US", compute_yoy: false },
    { series_id: "CSUSHPINSA", compute_yoy: true },
    { series_id: "HOUST", compute_yoy: false },
    // Financial Markets
    { series_id: "SP500", compute_yoy: false },
    { series_id: "VIXCLS", compute_yoy: false },
    { series_id: "BAMLH0A0HYM2", compute_yoy: false },
    // Monetary Policy
    { series_id: "DTWEXBGS", compute_yoy: true },
    { series_id: "M2SL", compute_yoy: true },
    { series_id: "WALCL", compute_yoy: true },
  ];

  const { data, isLoading, error } = useGetFredDataQuery(seriesList);

  // Merge 2-year, 10-year, and 30-year treasury yield data by date for overlay chart
  const treasuryOverlay = useMemo(() => {
    if (!data?.["DGS2"] || !data?.["DGS10"] || !data?.["DGS30"]) return null;
    const map = new Map<string, any>();
    for (const pt of data["DGS2"]) {
      map.set(pt.date, { date: pt.date, "2-Year": pt.value });
    }
    for (const pt of data["DGS10"]) {
      const existing = map.get(pt.date) ?? { date: pt.date };
      existing["10-Year"] = pt.value;
      map.set(pt.date, existing);
    }
    for (const pt of data["DGS30"]) {
      const existing = map.get(pt.date) ?? { date: pt.date };
      existing["30-Year"] = pt.value;
      map.set(pt.date, existing);
    }
    return [...map.values()].sort((a, b) => (a.date > b.date ? 1 : -1));
  }, [data]);

  // Merge CPI YoY, Core PCE YoY, and 10Y Breakeven Inflation by date for overlay chart
  const inflationOverlay = useMemo(() => {
    if (!data?.["CPIAUCSL"] || !data?.["PCEPILFE"] || !data?.["T10YIE"]) return null;
    const map = new Map<string, any>();
    for (const pt of data["CPIAUCSL"]) {
      map.set(pt.date, { date: pt.date, "CPI": pt.value });
    }
    for (const pt of data["PCEPILFE"]) {
      const existing = map.get(pt.date) ?? { date: pt.date };
      existing["Core PCE"] = pt.value;
      map.set(pt.date, existing);
    }
    for (const pt of data["T10YIE"]) {
      const existing = map.get(pt.date) ?? { date: pt.date };
      existing["Breakeven"] = pt.value;
      map.set(pt.date, existing);
    }
    return [...map.values()].sort((a, b) => (a.date > b.date ? 1 : -1));
  }, [data]);

  // Merge 30-year and 15-year mortgage data by date for overlay chart
  const mortgageOverlay = useMemo(() => {
    if (!data?.["MORTGAGE30US"] || !data?.["MORTGAGE15US"]) return null;
    const map = new Map<string, any>();
    for (const pt of data["MORTGAGE30US"]) {
      map.set(pt.date, { date: pt.date, "30-Year": pt.value });
    }
    for (const pt of data["MORTGAGE15US"]) {
      const existing = map.get(pt.date) ?? { date: pt.date };
      existing["15-Year"] = pt.value;
      map.set(pt.date, existing);
    }
    return [...map.values()].sort((a, b) => (a.date > b.date ? 1 : -1));
  }, [data]);


  if (isLoading) {
    return <LoadingModal show={true} message="Fetching Economic Indicators..." />;
  }

  if (error) {
    return (
      <Container>
        <Alert variant="danger">{getErrorMessages(error["data"])}</Alert>
      </Container>
    );
  }

  return (
    <Container fluid className="py-4 px-4 min-vh-100 theme-bg-quaternary">
      <div className="mb-5">
        <h1>Economic Indicators</h1>
        <p>
          Monitoring key macroeconomic metrics to understand market conditions and trends.
        </p>
      </div>

      <Row className="g-5">
        {/* ── Interest Rates & Yield Curve ── */}
        <Col xs={12}>
          <h4 className="mb-0">Interest Rates &amp; Yield Curve</h4>
        </Col>
        <Col xl={4} lg={6} md={12}>
          <GenericLineChart
            data={treasuryOverlay}
            label="Treasury Yields (2Y / 10Y / 30Y)"
            description="Comparison of short-, mid-, and long-term Treasury yields — a key window into the yield curve and economic outlook."
            lines={[
              { dataKey: "2-Year", color: "#20c997", name: "2-Year" },
              { dataKey: "10-Year", color: "#007bff", name: "10-Year" },
              { dataKey: "30-Year", color: "#fd7e14", name: "30-Year" },
            ]}
          />
        </Col>
        <Col xl={4} lg={6} md={12}>
          <GenericLineChart
            data={data["T10Y2Y"]}
            label="Yield Curve Spread (10Y - 2Y)"
            description="The difference between 10-year and 2-year Treasury yields. Inversions (negative values) have preceded every US recession in modern history."
            strokeColor="#e83e8c"
          />
        </Col>
        <Col xl={4} lg={6} md={12}>
          <GenericLineChart
            data={data["FEDFUNDS"]}
            label="Federal Funds Rate"
            description="The interest rate at which depository institutions lend reserve balances to other institutions overnight."
            strokeColor="#fd7e14"
          />
        </Col>

        {/* ── Inflation ── */}
        <Col xs={12}>
          <h4 className="mb-0">Inflation</h4>
        </Col>
        <Col xl={4} lg={6} md={12}>
          <GenericLineChart
            data={inflationOverlay}
            label="Inflation Measures (CPI / Core PCE / Breakeven)"
            description="Headline CPI, the Fed's preferred Core PCE, and the market-implied 10-year breakeven inflation rate — three lenses on price stability."
            lines={[
              { dataKey: "CPI", color: "#dc3545", name: "CPI YoY" },
              { dataKey: "Core PCE", color: "#6f42c1", name: "Core PCE YoY" },
              { dataKey: "Breakeven", color: "#17a2b8", name: "10Y Breakeven" },
            ]}
          />
        </Col>

        {/* ── Labor Market ── */}
        <Col xs={12}>
          <h4 className="mb-0">Labor Market</h4>
        </Col>
        <Col xl={4} lg={6} md={12}>
          <GenericLineChart
            data={data["UNRATE"]}
            label="Unemployment Rate"
            description="The percentage of the total labor force that is unemployed and actively seeking employment."
            strokeColor="#6c757d"
          />
        </Col>
        <Col xl={4} lg={6} md={12}>
          <GenericLineChart
            data={data["ICSA"]}
            label="Initial Jobless Claims"
            description="Weekly count of new filings for unemployment insurance — a high-frequency leading indicator that spikes well before recessions."
            strokeColor="#dc3545"
          />
        </Col>
        <Col xl={4} lg={6} md={12}>
          <GenericLineChart
            data={data["PAYEMS"]}
            label="Nonfarm Payrolls (YoY)"
            description="Year-over-year change in total nonfarm employment — the headline jobs number watched on the first Friday of every month."
            strokeColor="#007bff"
          />
        </Col>

        {/* ── Economic Growth & Consumer ── */}
        <Col xs={12}>
          <h4 className="mb-0">Economic Growth &amp; Consumer</h4>
        </Col>
        <Col xl={4} lg={6} md={12}>
          <GenericLineChart
            data={data["GDP"]}
            label="US GDP Growth (YoY)"
            description="The total monetary or market value of all the finished goods and services produced within a country's borders."
            strokeColor="#28a745"
          />
        </Col>
        <Col xl={4} lg={6} md={12}>
          <GenericLineChart
            data={data["INDPRO"]}
            label="Industrial Production (YoY)"
            description="Year-over-year change in output from manufacturing, mining, and utilities — a broad measure of the real economy's production capacity."
            strokeColor="#20c997"
          />
        </Col>
        <Col xl={4} lg={6} md={12}>
          <GenericLineChart
            data={data["RSAFS"]}
            label="Retail Sales (YoY)"
            description="Year-over-year change in advance retail and food services sales — a direct measure of consumer spending strength."
            strokeColor="#fd7e14"
          />
        </Col>
        <Col xl={4} lg={6} md={12}>
          <GenericLineChart
            data={data["UMCSENT"]}
            label="Consumer Sentiment"
            description="University of Michigan Consumer Sentiment Index — a forward-looking gauge of consumer confidence and spending intentions."
            strokeColor="#6f42c1"
          />
        </Col>

        {/* ── Housing ── */}
        <Col xs={12}>
          <h4 className="mb-0">Housing</h4>
        </Col>
        <Col xl={4} lg={6} md={12}>
          <GenericLineChart
            data={mortgageOverlay}
            label="Mortgage Rates (15-Year vs 30-Year)"
            description="Comparison of 30-year and 15-year fixed mortgage rates, key drivers of housing affordability and refinancing decisions."
            lines={[
              { dataKey: "30-Year", color: "#6f42c1", name: "30-Year Fixed" },
              { dataKey: "15-Year", color: "#e83e8c", name: "15-Year Fixed" },
            ]}
          />
        </Col>
        <Col xl={4} lg={6} md={12}>
          <GenericLineChart
            data={data["CSUSHPINSA"]}
            label="Home Prices (YoY)"
            description="S&P/Case-Shiller U.S. National Home Price Index year-over-year change — the benchmark for tracking residential real estate values."
            strokeColor="#28a745"
          />
        </Col>
        <Col xl={4} lg={6} md={12}>
          <GenericLineChart
            data={data["HOUST"]}
            label="Housing Starts"
            description="New privately-owned housing units started (thousands of units) — a leading indicator of construction activity and housing supply."
            strokeColor="#17a2b8"
          />
        </Col>

        {/* ── Financial Markets ── */}
        <Col xs={12}>
          <h4 className="mb-0">Financial Markets</h4>
        </Col>
        <Col xl={4} lg={6} md={12}>
          <GenericLineChart
            data={data["SP500"]}
            label="S&P 500"
            description="The S&P 500 index — a market-capitalization-weighted benchmark of 500 leading US publicly traded companies."
            strokeColor="#007bff"
          />
        </Col>
        <Col xl={4} lg={6} md={12}>
          <GenericLineChart
            data={data["VIXCLS"]}
            label="VIX (Volatility Index)"
            description="The CBOE Volatility Index — measures the market's expectation of 30-day forward-looking volatility, often called the 'fear gauge.'"
            strokeColor="#dc3545"
          />
        </Col>
        <Col xl={4} lg={6} md={12}>
          <GenericLineChart
            data={data["BAMLH0A0HYM2"]}
            label="High Yield Credit Spread"
            description="ICE BofA US High Yield Option-Adjusted Spread — the yield premium investors demand over Treasuries to hold riskier corporate bonds. Widens sharply in recessions."
            strokeColor="#fd7e14"
          />
        </Col>

        {/* ── Monetary Policy ── */}
        <Col xs={12}>
          <h4 className="mb-0">Monetary Policy</h4>
        </Col>
        <Col xl={4} lg={6} md={12}>
          <GenericLineChart
            data={data["DTWEXBGS"]}
            label="USD Strength Index (YoY)"
            description="Measures the value of the US Dollar against a basket of currencies of major US trading partners."
            strokeColor="#17a2b8"
          />
        </Col>
        <Col xl={4} lg={6} md={12}>
          <GenericLineChart
            data={data["M2SL"]}
            label="M2 Money Supply (YoY)"
            description="Year-over-year growth in the M2 money supply — a broad measure of money including cash, checking deposits, and easily convertible near-money."
            strokeColor="#007bff"
          />
        </Col>
        <Col xl={4} lg={6} md={12}>
          <GenericLineChart
            data={data["WALCL"]}
            label="Fed Balance Sheet (YoY)"
            description="Year-over-year change in the Federal Reserve's total assets — a direct measure of quantitative easing and tightening."
            strokeColor="#dc3545"
          />
        </Col>
      </Row>
    </Container>
  );
}
