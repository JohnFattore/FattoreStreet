import { useEffect } from "react";
import { useSearchParams } from "react-router-dom";
import { Alert, Card, Col, Container, Form, Row, Table } from "react-bootstrap";
import { Controller, useForm, SubmitHandler } from "react-hook-form";
import * as yup from "yup";
import { yupResolver } from "@hookform/resolvers/yup";
import { useGetSecEdgarDataQuery } from "../functions/api";
import { formatString, pickWinner } from "../functions/helperFunctions";
import { ISECData } from "../interfaces";
import LoadingButton from "../components/LoadingButton";
import StateHandler from "../components/StateHandler";
import { SecuritySearch } from "../components/SecuritySearch";

interface IFormInput {
  tickerA: string;
  tickerB: string;
}

interface IMetric {
  key: keyof ISECData;
  label: string;
  format: "money" | "percent" | "eps" | "text";
  // Which direction counts as stronger; undefined means no winner is shown
  higherIsBetter?: boolean;
}

interface IMetricSection {
  title: string;
  metrics: IMetric[];
}

const sections: IMetricSection[] = [
  {
    title: "TTM Income Statement",
    metrics: [
      { key: "ttmRevenue", label: "Revenue", format: "money" },
      {
        key: "ttmRevenueYoY",
        label: "Revenue YoY Growth",
        format: "percent",
        higherIsBetter: true,
      },
      { key: "ttmGrossProfit", label: "Gross Profit", format: "money" },
      { key: "ttmOperatingIncome", label: "Operating Income", format: "money" },
      { key: "ttmNetIncome", label: "Net Income", format: "money" },
      {
        key: "ttmNetIncomeYoY",
        label: "Net Income YoY Growth",
        format: "percent",
        higherIsBetter: true,
      },
      {
        key: "ttmOperatingCashFlow",
        label: "Operating Cash Flow",
        format: "money",
      },
    ],
  },
  {
    title: "Latest Balance Sheet",
    metrics: [
      { key: "latestAssets", label: "Total Assets", format: "money" },
      { key: "latestLiabilities", label: "Total Liabilities", format: "money" },
      { key: "latestEquity", label: "Total Equity", format: "money" },
      { key: "latestCash", label: "Cash & Equivalents", format: "money" },
      { key: "latestInventory", label: "Inventory", format: "money" },
      { key: "latestEps", label: "Latest EPS", format: "eps" },
      { key: "latestQuarterEnd", label: "Latest Quarter End", format: "text" },
    ],
  },
  {
    title: "Financial Ratios & Metrics",
    metrics: [
      {
        key: "netMargin",
        label: "Net Margin",
        format: "percent",
        higherIsBetter: true,
      },
      {
        key: "grossMargin",
        label: "Gross Margin",
        format: "percent",
        higherIsBetter: true,
      },
      {
        key: "roA",
        label: "Return on Assets (ROA)",
        format: "percent",
        higherIsBetter: true,
      },
      {
        key: "debtToAssets",
        label: "Debt to Assets",
        format: "percent",
        higherIsBetter: false,
      },
      {
        key: "cashToLiabilities",
        label: "Cash to Liabilities",
        format: "percent",
        higherIsBetter: true,
      },
      {
        key: "ocfToNetIncome",
        label: "OCF to Net Income",
        format: "percent",
        higherIsBetter: true,
      },
    ],
  },
];

const schema = yup.object().shape({
  tickerA: yup.string().trim().uppercase().required(),
  tickerB: yup.string().trim().uppercase().required(),
});

function formatMetric(value: string | undefined, format: IMetric["format"]) {
  if (value === undefined || value === null || value === "") {
    return "N/A";
  }
  switch (format) {
    case "money":
      return formatString(Number(value), "money");
    case "eps":
      return `$${value}`;
    default:
      return value;
  }
}

// A raw Table rather than SortableTable: the rows are a fixed metric list, so
// sorting them means nothing (the static label/value exception in the rule)
function ComparisonTable({ a, b }: { a: ISECData; b: ISECData }) {
  return (
    <>
      {sections.map((section) => (
        <Card key={section.title}>
          <Card.Header as="h5" className="bg-dark text-white">
            {section.title}
          </Card.Header>
          <Card.Body>
            <Table hover size="sm" responsive>
              <thead>
                <tr>
                  <th>Metric</th>
                  <th>{a.ticker}</th>
                  <th>{b.ticker}</th>
                </tr>
              </thead>
              <tbody>
                {section.metrics.map((metric) => {
                  const winner = pickWinner(
                    a[metric.key],
                    b[metric.key],
                    metric.higherIsBetter,
                  );
                  return (
                    <tr key={metric.key}>
                      <td>
                        <strong>{metric.label}</strong>
                      </td>
                      <td
                        className={
                          winner === "a" ? "text-success fw-bold" : undefined
                        }
                      >
                        {formatMetric(a[metric.key], metric.format)}
                      </td>
                      <td
                        className={
                          winner === "b" ? "text-success fw-bold" : undefined
                        }
                      >
                        {formatMetric(b[metric.key], metric.format)}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </Table>
          </Card.Body>
        </Card>
      ))}
    </>
  );
}

export function CompanyComparison() {
  const [searchParams, setSearchParams] = useSearchParams();
  // Normalize here too, so a hand-edited URL behaves like a form submission:
  // the endpoint rejects lowercase tickers, and one ticker gets one cache entry
  const tickerA = searchParams.get("a")?.trim().toUpperCase() ?? "";
  const tickerB = searchParams.get("b")?.trim().toUpperCase() ?? "";

  const {
    control,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<IFormInput>({
    resolver: yupResolver(schema),
    defaultValues: { tickerA, tickerB },
  });
  // defaultValues only apply on mount; keep the inputs in step with the URL on
  // back/forward or a nav click to a bare /compare
  useEffect(() => {
    reset({ tickerA, tickerB });
  }, [tickerA, tickerB, reset]);
  const onSubmit: SubmitHandler<IFormInput> = (data) => {
    setSearchParams({ a: data.tickerA, b: data.tickerB });
  };

  const queryA = useGetSecEdgarDataQuery(tickerA, { skip: !tickerA });
  const queryB = useGetSecEdgarDataQuery(tickerB, { skip: !tickerB });
  const ready = Boolean(tickerA && tickerB);

  return (
    <div className="company-comparison-page">
      <Container>
        <h1>Compare Companies</h1>
        <p>
          Side-by-side SEC EDGAR fundamentals. The stronger value is highlighted
          where a metric has a clear better direction.
        </p>

        <Form onSubmit={handleSubmit(onSubmit)}>
          <Row>
            <Col md={5}>
              <Controller
                name="tickerA"
                control={control}
                render={({ field }) => (
                  <SecuritySearch
                    value={field.value}
                    onChange={field.onChange}
                    onSelect={(result) => field.onChange(result.ticker)}
                    ariaLabel="First ticker"
                    placeholder="First ticker or company name"
                    includeFunds={false}
                  />
                )}
              />
            </Col>
            <Col md={5}>
              <Controller
                name="tickerB"
                control={control}
                render={({ field }) => (
                  <SecuritySearch
                    value={field.value}
                    onChange={field.onChange}
                    onSelect={(result) => field.onChange(result.ticker)}
                    ariaLabel="Second ticker"
                    placeholder="Second ticker or company name"
                    includeFunds={false}
                  />
                )}
              />
            </Col>
            <Col md={2}>
              <LoadingButton label="Compare" loading={false} />
            </Col>
          </Row>
          {(errors.tickerA || errors.tickerB) && (
            <Alert variant="danger">Error: Enter two tickers to compare</Alert>
          )}
        </Form>

        {ready && (
          <StateHandler
            isLoading={queryA.isLoading || queryB.isLoading}
            errors={[queryA.error, queryB.error]}
            content={
              queryA.data &&
              queryB.data && <ComparisonTable a={queryA.data} b={queryB.data} />
            }
          />
        )}

        <Alert variant="info">
          This data is fetched directly from the SEC EDGAR database via the
          Fattore Street API.
        </Alert>
      </Container>
    </div>
  );
}
