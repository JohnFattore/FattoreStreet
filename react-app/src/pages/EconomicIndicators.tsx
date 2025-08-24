import { Row, Col, Spinner, Alert } from "react-bootstrap";
import GenericLineChart from "../components/GenericLineChart";
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

  const {data, isLoading, error} = useGetFredDataQuery(seriesList)

  if (isLoading) {
      return <Spinner animation="border" />
  }

  if (error)
    return (
      <Alert variant="danger">{getErrorMessages(error["data"])}</Alert>
    );

  return (
    <>
      <Row>
        <Col>
          <GenericLineChart data={data["DGS10"]} label={"10 Year Treasury Yield"} />
        </Col>
        <Col>
          <GenericLineChart data={data["CPIAUCSL"]} label={"CPI"} />
        </Col>
        <Col>
          <GenericLineChart data={data["UNRATE"]} label={"Unemployment Rate"} />
        </Col>
      </Row>
      <Row>
        <Col>
          <GenericLineChart data={data["DTWEXBGS"]} label={"USD Strength Index Year over Year"} />
        </Col>
        <Col>
          <GenericLineChart data={data["FEDFUNDS"]} label={"Federal Funds Rate"} />
        </Col>
        <Col>
          <GenericLineChart data={data["GDP"]} label={"US GDP Year over Year"} />
        </Col>
      </Row>
    </>
  );
}
