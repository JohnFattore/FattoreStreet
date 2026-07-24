import { useMemo } from "react";
import { Row, Col, Alert, Container } from "react-bootstrap";
import GenericLineChart from "../components/GenericLineChart";
import LoadingModal from "../components/LoadingModal";
import { useGetFredDataQuery } from "../functions/api";
import { getApiErrorMessages } from "../functions/helperFunctions";

export default function EconomicIndicators() {
  const seriesLabelMap: Record<string, string> = {
    DGS2: "2Y",
    DGS10: "10Y",
    DGS30: "30Y",
    T10Y2Y: "10Y-2Y Spread",
    FEDFUNDS: "Fed Funds",
    CPIAUCSL: "CPI YoY",
    PCEPILFE: "Core PCE YoY",
    T10YIE: "10Y Breakeven",
    UNRATE: "Unemployment",
    ICSA: "Jobless Claims",
    PAYEMS: "Nonfarm Payrolls YoY",
    GDP: "GDP YoY",
    INDPRO: "Industrial Production YoY",
    RSAFS: "Retail Sales YoY",
    UMCSENT: "Consumer Sentiment",
    MORTGAGE30US: "30Y Mortgage",
    MORTGAGE15US: "15Y Mortgage",
    CSUSHPINSA: "Home Prices YoY",
    HOUST: "Housing Starts",
    SP500: "S&P 500",
    VIXCLS: "VIX",
    BAMLH0A0HYM2: "HY Credit Spread",
    DTWEXBGS: "USD Strength YoY",
    M2SL: "M2 YoY",
    WALCL: "Fed Balance Sheet YoY",
  };

  const seriesList = [
    // Interest Rates & Yield Curve
    { seriesId: "DGS2", computeYoy: false },
    { seriesId: "DGS10", computeYoy: false },
    { seriesId: "DGS30", computeYoy: false },
    { seriesId: "T10Y2Y", computeYoy: false },
    { seriesId: "FEDFUNDS", computeYoy: false },
    // Inflation
    { seriesId: "CPIAUCSL", computeYoy: true },
    { seriesId: "PCEPILFE", computeYoy: true },
    { seriesId: "T10YIE", computeYoy: false },
    // Labor Market
    { seriesId: "UNRATE", computeYoy: false },
    { seriesId: "ICSA", computeYoy: false },
    { seriesId: "PAYEMS", computeYoy: true },
    // Economic Growth & Consumer
    { seriesId: "GDP", computeYoy: true },
    { seriesId: "INDPRO", computeYoy: true },
    { seriesId: "RSAFS", computeYoy: true },
    { seriesId: "UMCSENT", computeYoy: false },
    // Housing
    { seriesId: "MORTGAGE30US", computeYoy: false },
    { seriesId: "MORTGAGE15US", computeYoy: false },
    { seriesId: "CSUSHPINSA", computeYoy: true },
    { seriesId: "HOUST", computeYoy: false },
    // Financial Markets
    { seriesId: "SP500", computeYoy: false },
    { seriesId: "VIXCLS", computeYoy: false },
    { seriesId: "BAMLH0A0HYM2", computeYoy: false },
    // Monetary Policy
    { seriesId: "DTWEXBGS", computeYoy: true },
    { seriesId: "M2SL", computeYoy: true },
    { seriesId: "WALCL", computeYoy: true },
  ];

  const { data, isLoading, error } = useGetFredDataQuery(seriesList);

  const formatLatestDate = (date: string) =>
    new Date(`${date}T00:00:00`).toLocaleDateString(undefined, {
      month: "short",
      day: "numeric",
      year: "numeric",
    });

  const formatLatestValue = (value: number) =>
    new Intl.NumberFormat(undefined, {
      maximumFractionDigits: 2,
    }).format(value);

  const getLatestReading = (
    seriesId: string,
    includeLabel = false,
    customLabel?: string,
  ) => {
    if (!data) return undefined;
    const observations = data[seriesId];
    if (!observations || observations.length === 0) return undefined;

    const latestPoint = observations.reduce((latest, current) =>
      current.date > latest.date ? current : latest,
    );

    const reading = `${formatLatestValue(latestPoint.value)} (${formatLatestDate(latestPoint.date)})`;
    if (!includeLabel) return reading;
    const label = customLabel ?? seriesLabelMap[seriesId] ?? seriesId;
    return `${label}: ${reading}`;
  };

  const getLatestReadingGroup = (
    items: { seriesId: string; label?: string }[],
  ) =>
    items
      .map((item) => getLatestReading(item.seriesId, true, item.label))
      .filter(Boolean)
      .join(" · ");

  // Merge 2-year, 10-year, and 30-year treasury yield data by date for overlay chart
  const treasuryOverlay = useMemo(() => {
    if (!data?.["DGS2"] || !data?.["DGS10"] || !data?.["DGS30"]) return null;
    const map = new Map<string, Record<string, string | number>>();
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

  // Merge CPI YoY and Core PCE YoY by date for overlay chart
  const cpiPceOverlay = useMemo(() => {
    if (!data?.["CPIAUCSL"] || !data?.["PCEPILFE"]) return null;
    const map = new Map<string, Record<string, string | number>>();
    for (const pt of data["CPIAUCSL"]) {
      map.set(pt.date, { date: pt.date, CPI: pt.value });
    }
    for (const pt of data["PCEPILFE"]) {
      const existing = map.get(pt.date) ?? { date: pt.date };
      existing["Core PCE"] = pt.value;
      map.set(pt.date, existing);
    }
    return [...map.values()].sort((a, b) => (a.date > b.date ? 1 : -1));
  }, [data]);

  // Merge 30-year and 15-year mortgage data by date for overlay chart
  const mortgageOverlay = useMemo(() => {
    if (!data?.["MORTGAGE30US"] || !data?.["MORTGAGE15US"]) return null;
    const map = new Map<string, Record<string, string | number>>();
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
    return (
      <LoadingModal show={true} message="Fetching Economic Indicators..." />
    );
  }

  if (error || !data) {
    return (
      <Container>
        <Alert variant="danger">
          {error ? getApiErrorMessages(error) : "No data available."}
        </Alert>
      </Container>
    );
  }

  return (
    <Container fluid className="economic-indicators-page theme-bg-quaternary">
      <div>
        <h1>Economic Indicators</h1>
        <p>
          Monitoring key macroeconomic metrics to understand market conditions
          and trends.
        </p>
      </div>

      <Row>
        {/* ── Interest Rates & Yield Curve ── */}
        <Col xs={12}>
          <h4>Interest Rates &amp; Yield Curve</h4>
        </Col>
        <Col xl={4} lg={6} md={12}>
          <GenericLineChart
            data={treasuryOverlay}
            label="Treasury Yields (2Y / 10Y / 30Y)"
            description="Comparison of short-, mid-, and long-term Treasury yields — a key window into the yield curve and economic outlook."
            latestReading={getLatestReadingGroup([
              { seriesId: "DGS2", label: "2Y" },
              { seriesId: "DGS10", label: "10Y" },
              { seriesId: "DGS30", label: "30Y" },
            ])}
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
            latestReading={getLatestReading("T10Y2Y")}
            strokeColor="#e83e8c"
          />
        </Col>
        <Col xl={4} lg={6} md={12}>
          <GenericLineChart
            data={data["FEDFUNDS"]}
            label="Federal Funds Rate"
            description="The interest rate at which depository institutions lend reserve balances to other institutions overnight."
            latestReading={getLatestReading("FEDFUNDS")}
            strokeColor="#fd7e14"
          />
        </Col>

        {/* ── Inflation ── */}
        <Col xs={12}>
          <h4>Inflation</h4>
        </Col>
        <Col xl={4} lg={6} md={12}>
          <GenericLineChart
            data={cpiPceOverlay}
            label="CPI vs Core PCE (YoY)"
            description="Headline CPI and the Fed's preferred Core PCE inflation measure side by side — comparing consumer price changes with the metric that drives monetary policy."
            latestReading={getLatestReadingGroup([
              { seriesId: "CPIAUCSL", label: "CPI YoY" },
              { seriesId: "PCEPILFE", label: "Core PCE YoY" },
            ])}
            lines={[
              { dataKey: "CPI", color: "#dc3545", name: "CPI YoY" },
              { dataKey: "Core PCE", color: "#6f42c1", name: "Core PCE YoY" },
            ]}
          />
        </Col>
        <Col xl={4} lg={6} md={12}>
          <GenericLineChart
            data={data["T10YIE"]}
            label="10-Year Breakeven Inflation Rate"
            description="The market's expected average inflation over the next 10 years, derived from the spread between nominal Treasuries and TIPS."
            latestReading={getLatestReading("T10YIE")}
            strokeColor="#17a2b8"
          />
        </Col>

        {/* ── Labor Market ── */}
        <Col xs={12}>
          <h4>Labor Market</h4>
        </Col>
        <Col xl={4} lg={6} md={12}>
          <GenericLineChart
            data={data["UNRATE"]}
            label="Unemployment Rate"
            description="The percentage of the total labor force that is unemployed and actively seeking employment."
            latestReading={getLatestReading("UNRATE")}
            strokeColor="#6c757d"
          />
        </Col>
        <Col xl={4} lg={6} md={12}>
          <GenericLineChart
            data={data["ICSA"]}
            label="Initial Jobless Claims"
            description="Weekly count of new filings for unemployment insurance — a high-frequency leading indicator that spikes well before recessions."
            latestReading={getLatestReading("ICSA")}
            strokeColor="#dc3545"
          />
        </Col>
        <Col xl={4} lg={6} md={12}>
          <GenericLineChart
            data={data["PAYEMS"]}
            label="Nonfarm Payrolls (YoY)"
            description="Year-over-year change in total nonfarm employment — the headline jobs number watched on the first Friday of every month."
            latestReading={getLatestReading("PAYEMS")}
            strokeColor="#007bff"
          />
        </Col>

        {/* ── Economic Growth & Consumer ── */}
        <Col xs={12}>
          <h4>Economic Growth &amp; Consumer</h4>
        </Col>
        <Col xl={4} lg={6} md={12}>
          <GenericLineChart
            data={data["GDP"]}
            label="US GDP Growth (YoY)"
            description="The total monetary or market value of all the finished goods and services produced within a country's borders."
            latestReading={getLatestReading("GDP")}
            strokeColor="#28a745"
          />
        </Col>
        <Col xl={4} lg={6} md={12}>
          <GenericLineChart
            data={data["INDPRO"]}
            label="Industrial Production (YoY)"
            description="Year-over-year change in output from manufacturing, mining, and utilities — a broad measure of the real economy's production capacity."
            latestReading={getLatestReading("INDPRO")}
            strokeColor="#20c997"
          />
        </Col>
        <Col xl={4} lg={6} md={12}>
          <GenericLineChart
            data={data["RSAFS"]}
            label="Retail Sales (YoY)"
            description="Year-over-year change in advance retail and food services sales — a direct measure of consumer spending strength."
            latestReading={getLatestReading("RSAFS")}
            strokeColor="#fd7e14"
          />
        </Col>
        <Col xl={4} lg={6} md={12}>
          <GenericLineChart
            data={data["UMCSENT"]}
            label="Consumer Sentiment"
            description="University of Michigan Consumer Sentiment Index — a forward-looking gauge of consumer confidence and spending intentions."
            latestReading={getLatestReading("UMCSENT")}
            strokeColor="#6f42c1"
          />
        </Col>

        {/* ── Housing ── */}
        <Col xs={12}>
          <h4>Housing</h4>
        </Col>
        <Col xl={4} lg={6} md={12}>
          <GenericLineChart
            data={mortgageOverlay}
            label="Mortgage Rates (15-Year vs 30-Year)"
            description="Comparison of 30-year and 15-year fixed mortgage rates, key drivers of housing affordability and refinancing decisions."
            latestReading={getLatestReadingGroup([
              { seriesId: "MORTGAGE30US", label: "30Y Fixed" },
              { seriesId: "MORTGAGE15US", label: "15Y Fixed" },
            ])}
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
            latestReading={getLatestReading("CSUSHPINSA")}
            strokeColor="#28a745"
          />
        </Col>
        <Col xl={4} lg={6} md={12}>
          <GenericLineChart
            data={data["HOUST"]}
            label="Housing Starts"
            description="New privately-owned housing units started (thousands of units) — a leading indicator of construction activity and housing supply."
            latestReading={getLatestReading("HOUST")}
            strokeColor="#17a2b8"
          />
        </Col>

        {/* ── Financial Markets ── */}
        <Col xs={12}>
          <h4>Financial Markets</h4>
        </Col>
        <Col xl={4} lg={6} md={12}>
          <GenericLineChart
            data={data["SP500"]}
            label="S&P 500"
            description="The S&P 500 index — a market-capitalization-weighted benchmark of 500 leading US publicly traded companies."
            latestReading={getLatestReading("SP500")}
            strokeColor="#007bff"
          />
        </Col>
        <Col xl={4} lg={6} md={12}>
          <GenericLineChart
            data={data["VIXCLS"]}
            label="VIX (Volatility Index)"
            description="The CBOE Volatility Index — measures the market's expectation of 30-day forward-looking volatility, often called the 'fear gauge.'"
            latestReading={getLatestReading("VIXCLS")}
            strokeColor="#dc3545"
          />
        </Col>
        <Col xl={4} lg={6} md={12}>
          <GenericLineChart
            data={data["BAMLH0A0HYM2"]}
            label="High Yield Credit Spread"
            description="ICE BofA US High Yield Option-Adjusted Spread — the yield premium investors demand over Treasuries to hold riskier corporate bonds. Widens sharply in recessions."
            latestReading={getLatestReading("BAMLH0A0HYM2")}
            strokeColor="#fd7e14"
          />
        </Col>

        {/* ── Monetary Policy ── */}
        <Col xs={12}>
          <h4>Monetary Policy</h4>
        </Col>
        <Col xl={4} lg={6} md={12}>
          <GenericLineChart
            data={data["DTWEXBGS"]}
            label="USD Strength Index (YoY)"
            description="Measures the value of the US Dollar against a basket of currencies of major US trading partners."
            latestReading={getLatestReading("DTWEXBGS")}
            strokeColor="#17a2b8"
          />
        </Col>
        <Col xl={4} lg={6} md={12}>
          <GenericLineChart
            data={data["M2SL"]}
            label="M2 Money Supply (YoY)"
            description="Year-over-year growth in the M2 money supply — a broad measure of money including cash, checking deposits, and easily convertible near-money."
            latestReading={getLatestReading("M2SL")}
            strokeColor="#007bff"
          />
        </Col>
        <Col xl={4} lg={6} md={12}>
          <GenericLineChart
            data={data["WALCL"]}
            label="Fed Balance Sheet (YoY)"
            description="Year-over-year change in the Federal Reserve's total assets — a direct measure of quantitative easing and tightening."
            latestReading={getLatestReading("WALCL")}
            strokeColor="#dc3545"
          />
        </Col>
      </Row>
    </Container>
  );
}
