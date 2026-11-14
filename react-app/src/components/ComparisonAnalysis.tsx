import { useState } from "react";
import { useSelector } from "react-redux";
import { Alert, Button, Card, Spinner } from "react-bootstrap";
import Markdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { RootState } from "../main";
import { ISECData } from "../interfaces";
import { useGetCompanyComparisonAnalysisQuery } from "../functions/api";
import { getApiErrorMessages } from "../functions/helperFunctions";
import { toAnalysisFigures } from "../functions/analysisFigures";
import LoginRequired from "./LoginRequired";

// Sits below the table and never replaces it: a failed analysis leaves the
// figures in view. Nothing is asked of the model until the reader clicks, since
// each analysis is a paid call (see intent/fundamentals/company-comparison.md).
// The page keys this by ticker pair, so a new pair starts unrequested again.
export default function ComparisonAnalysis({
  a,
  b,
}: {
  a: ISECData;
  b: ISECData;
}) {
  const { access } = useSelector((state: RootState) => state.user);
  const [requested, setRequested] = useState(false);
  const { data, error, isFetching, refetch } =
    useGetCompanyComparisonAnalysisQuery(
      { companies: [toAnalysisFigures(a), toAnalysisFigures(b)] },
      { skip: !access || !requested },
    );

  if (!access) {
    return (
      <LoginRequired
        title="AI Comparison Analysis"
        message="Sign in for a plain-language analysis of these two companies' figures."
        buttonText="Sign In for Analysis"
        defaultShowLogin={false}
      />
    );
  }

  let body: React.ReactNode;
  if (!requested) {
    body = (
      <Button variant="primary" onClick={() => setRequested(true)}>
        Generate Analysis
      </Button>
    );
  } else if (isFetching) {
    body = <Spinner animation="border" aria-label="Writing analysis" />;
  } else if (error) {
    body = (
      <>
        <Alert variant="warning">{getApiErrorMessages(error)[0]}</Alert>
        <Button variant="outline-primary" onClick={() => refetch()}>
          Try Again
        </Button>
      </>
    );
  } else {
    body = <Markdown remarkPlugins={[remarkGfm]}>{data ?? ""}</Markdown>;
  }

  return (
    <Card>
      <Card.Header as="h5" className="bg-dark text-white">
        AI Analysis
      </Card.Header>
      <Card.Body>
        {body}
        <p className="text-muted small mb-0">
          Written by an AI model from the figures above. Not investment advice.
        </p>
      </Card.Body>
    </Card>
  );
}
