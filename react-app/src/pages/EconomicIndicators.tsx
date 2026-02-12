import { useMemo } from "react";
import { Row, Col, Alert, Container } from "react-bootstrap";
import GenericLineChart from "../components/GenericLineChart";
import LoadingModal from "../components/LoadingModal";
import { useGetFredDataQuery } from "../functions/api";
import { getErrorMessages } from "../functions/helperFunctions";

export default function EconomicIndicators() {
  const seriesList = [
    { series_id: "DGS10", compute_yoy: false },
    { series_id: "CPIAUCSL", compute_yoy: true },
    { series_id: "UNRATE", compute_yoy: false },
    { series_id: "DTWEXBGS", compute_yoy: true },
    { series_id: "FEDFUNDS", compute_yoy: false },
    { series_id: "GDP", compute_yoy: true },
    { series_id: "MORTGAGE30US", compute_yoy: false },
    { series_id: "MORTGAGE15US", compute_yoy: false },
    { series_id: "DGS2", compute_yoy: false },
    { series_id: "DGS30", compute_yoy: false },
  ];

  const { data, isLoading, error } = useGetFredDataQuery(seriesList);

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
            data={data["CPIAUCSL"]}
            label="Consumer Price Index (YoY)"
            description="A measure of inflation reflecting the change in prices paid by consumers for goods and services."
            strokeColor="#dc3545"
          />
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
            data={data["DTWEXBGS"]}
            label="USD Strength Index (YoY)"
            description="Measures the value of the US Dollar against a basket of currencies of major US trading partners."
            strokeColor="#17a2b8"
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
            data={mortgageOverlay}
            label="Mortgage Rates (15-Year vs 30-Year)"
            description="Comparison of 30-year and 15-year fixed mortgage rates, key drivers of housing affordability and refinancing decisions."
            lines={[
              { dataKey: "30-Year", color: "#6f42c1", name: "30-Year Fixed" },
              { dataKey: "15-Year", color: "#e83e8c", name: "15-Year Fixed" },
            ]}
          />
        </Col>
      </Row>
    </Container>
  );
}
