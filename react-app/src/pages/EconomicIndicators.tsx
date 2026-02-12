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
  ];

  const { data, isLoading, error } = useGetFredDataQuery(seriesList);

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
            data={data["DGS10"]}
            label="10 Year Treasury Yield"
            description="The benchmark for long-term interest rates and a key indicator of economic sentiment."
            strokeColor="#007bff"
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
      </Row>
    </Container>
  );
}
