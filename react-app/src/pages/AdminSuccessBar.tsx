import { FormEvent, useMemo, useState } from "react";
import { useDispatch, useSelector } from "react-redux";
import { Alert, Badge, Button, Card, Form, ProgressBar, Spinner, Table } from "react-bootstrap";
import { skipToken } from "@reduxjs/toolkit/query";
import { useNavigate } from "react-router-dom";
import { AppDispatch, RootState } from "../main";
import { useGetAssetDividendsQuery, useGetAssetInfosQuery, useGetAssetPricesQuery, useGetIexDividendsQuery, useGetIexPricesQuery } from "../functions/api";
import { IDividendRow, IIexPrice } from "../interfaces";
import { addTicker, clearError, removeTicker, setValidationError } from "../reducers/adminSuccessBarReducer";

const DIVIDEND_MATCH_WINDOW_DAYS = 3;
const VALUE_MATCH_TOLERANCE = 0.01;
const MIN_COMPARISON_DATE = "2016-01-01";

interface IYfPrice {
  date: string;
  value: number;
}

interface PriceSummary {
  overlappingDates: number;
  within1Pct: number;
  within3Pct: number;
  within5Pct: number;
  within25Pct: number;
  score: number;
}

interface DividendSummary {
  springCount: number;
  yfCount: number;
  matchedCount: number;
  matchedWithinValueTolerance: number;
  score: number | null;
}

const toEpochDay = (date: string): number =>
  Math.floor(new Date(`${date}T00:00:00Z`).getTime() / 86_400_000);

const formatPercent = (value: number | null | undefined): string => {
  if (value == null) return "---";
  return `${value.toFixed(2)}%`;
};

const formatRatio = (value: number, total: number): string =>
  `${value}/${total}`;

const getScoreVariant = (score: number): "success" | "warning" | "danger" => {
  if (score >= 80) return "success";
  if (score >= 60) return "warning";
  return "danger";
};

const calculatePriceSummary = (iexPrices: IIexPrice[], yfPrices: IYfPrice[]): PriceSummary => {
  const iexMap = new Map<string, number>();
  const yfMap = new Map<string, number>();

  iexPrices.forEach((row) => {
    if (row.date < MIN_COMPARISON_DATE) return;
    iexMap.set(row.date, row.adjustedClose ?? row.close);
  });
  yfPrices.forEach((row) => {
    if (row.date < MIN_COMPARISON_DATE) return;
    yfMap.set(row.date, row.value);
  });

  let overlappingDates = 0;
  let within1Pct = 0;
  let within3Pct = 0;
  let within5Pct = 0;
  let within25Pct = 0;

  for (const [date, springValue] of iexMap.entries()) {
    const yfValue = yfMap.get(date);
    if (yfValue == null || springValue === 0) continue;
    const pctDiff = (Math.abs(yfValue - springValue) / Math.abs(springValue)) * 100;
    overlappingDates++;
    if (pctDiff <= 1) within1Pct++;
    if (pctDiff <= 3) within3Pct++;
    if (pctDiff <= 5) within5Pct++;
    if (pctDiff <= 25) within25Pct++;
  }

  const within1Rate = overlappingDates > 0 ? within1Pct / overlappingDates : 0;

  return {
    overlappingDates,
    within1Pct,
    within3Pct,
    within5Pct,
    within25Pct,
    score: within1Rate * 100,
  };
};

const calculateDividendSummary = (
  springDividends: IDividendRow[],
  yfDividends: IDividendRow[]
): DividendSummary => {
  const sortedSpring = springDividends
    .filter((row) => row.date >= MIN_COMPARISON_DATE)
    .sort((a, b) => a.date.localeCompare(b.date));
  const sortedYf = yfDividends
    .filter((row) => row.date >= MIN_COMPARISON_DATE)
    .sort((a, b) => a.date.localeCompare(b.date));

  const usedYfIndexes = new Set<number>();
  let matchedCount = 0;
  let matchedWithinValueTolerance = 0;

  for (const springRow of sortedSpring) {
    const springDay = toEpochDay(springRow.date);
    let bestIndex = -1;
    let bestGap = Number.MAX_SAFE_INTEGER;

    for (let i = 0; i < sortedYf.length; i++) {
      if (usedYfIndexes.has(i)) continue;
      const gap = Math.abs(toEpochDay(sortedYf[i].date) - springDay);
      if (gap > DIVIDEND_MATCH_WINDOW_DAYS) continue;
      if (gap < bestGap) {
        bestGap = gap;
        bestIndex = i;
      }
    }

    if (bestIndex >= 0) {
      usedYfIndexes.add(bestIndex);
      matchedCount++;
      if (Math.abs(sortedYf[bestIndex].value - springRow.value) <= VALUE_MATCH_TOLERANCE) {
        matchedWithinValueTolerance++;
      }
    }
  }

  if (sortedSpring.length === 0 && sortedYf.length === 0) {
    return {
      springCount: 0,
      yfCount: 0,
      matchedCount,
      matchedWithinValueTolerance,
      score: null,
    };
  }

  const coverageBase = Math.max(sortedSpring.length, sortedYf.length, 1);
  const coverageRate = matchedCount / coverageBase;
  const valueAgreementRate = matchedCount > 0 ? matchedWithinValueTolerance / matchedCount : 0;
  const score = (coverageRate * 0.6 + valueAgreementRate * 0.4) * 100;

  return {
    springCount: sortedSpring.length,
    yfCount: sortedYf.length,
    matchedCount,
    matchedWithinValueTolerance,
    score,
  };
};

function TickerSuccessRow({ ticker, assetType }: { ticker: string; assetType?: string }) {
  const springPrices = useGetIexPricesQuery(ticker);
  const yfPrices = useGetAssetPricesQuery(ticker);
  const springDividends = useGetIexDividendsQuery(ticker);
  const yfDividends = useGetAssetDividendsQuery(ticker);
  const dispatch = useDispatch<AppDispatch>();
  const navigate = useNavigate();

  const isLoading =
    springPrices.isLoading ||
    yfPrices.isLoading ||
    springDividends.isLoading ||
    yfDividends.isLoading;
  const hasError = springPrices.error || yfPrices.error || springDividends.error || yfDividends.error;

  const priceSummary = useMemo(
    () =>
      calculatePriceSummary(
        springPrices.data?.prices ?? [],
        (yfPrices.data ?? []) as IYfPrice[]
      ),
    [springPrices.data, yfPrices.data]
  );

  const dividendSummary = useMemo(
    () => calculateDividendSummary(springDividends.data?.dividends ?? [], yfDividends.data ?? []),
    [springDividends.data, yfDividends.data]
  );

  const combinedScore = useMemo(() => {
    return priceSummary.score;
  }, [priceSummary.score]);

  return (
    <tr>
      <td>{ticker}</td>
      <td>{assetType ?? "Unknown"}</td>
      <td>
        {isLoading ? (
          <Spinner size="sm" />
        ) : hasError ? (
          <span className="text-danger">Error</span>
        ) : (
          <>
            <div>Overlap: {priceSummary.overlappingDates}</div>
            <div className="small text-muted">
              1%: {formatRatio(priceSummary.within1Pct, priceSummary.overlappingDates)} | 3%:{" "}
              {formatRatio(priceSummary.within3Pct, priceSummary.overlappingDates)} | 5%:{" "}
              {formatRatio(priceSummary.within5Pct, priceSummary.overlappingDates)} | 25%:{" "}
              {formatRatio(priceSummary.within25Pct, priceSummary.overlappingDates)}
            </div>
            <div>
              <Badge bg={getScoreVariant(priceSummary.score)}>{formatPercent(priceSummary.score)}</Badge>
            </div>
          </>
        )}
      </td>
      <td>
        {isLoading ? (
          <Spinner size="sm" />
        ) : hasError ? (
          <span className="text-danger">Error</span>
        ) : dividendSummary.score == null ? (
          <span className="text-muted">No dividend data</span>
        ) : (
          <>
            <div>
              Matches: {formatRatio(dividendSummary.matchedCount, Math.max(dividendSummary.springCount, dividendSummary.yfCount))}
            </div>
            <div className="small text-muted">
              Value-aligned: {formatRatio(dividendSummary.matchedWithinValueTolerance, Math.max(dividendSummary.matchedCount, 1))}
            </div>
            <div>
              <Badge bg={getScoreVariant(dividendSummary.score)}>{formatPercent(dividendSummary.score)}</Badge>
            </div>
          </>
        )}
      </td>
      <td style={{ minWidth: "220px" }}>
        {isLoading ? (
          <Spinner size="sm" />
        ) : hasError ? (
          <span className="text-danger">Error</span>
        ) : (
          <>
            <ProgressBar
              now={combinedScore}
              variant={getScoreVariant(combinedScore)}
              label={formatPercent(combinedScore)}
            />
            <div className="small text-muted mt-1">Based on within-1% adjusted-price matches only</div>
          </>
        )}
      </td>
      <td>
        <div className="d-flex gap-2">
          <Button
            variant="outline-primary"
            size="sm"
            onClick={() => navigate(`/iex-prices/${ticker}`)}
          >
            IEX View
          </Button>
          <Button
            variant="outline-danger"
            size="sm"
            onClick={() => dispatch(removeTicker(ticker))}
          >
            Remove
          </Button>
        </div>
      </td>
    </tr>
  );
}

export default function AdminSuccessBar() {
  const dispatch = useDispatch<AppDispatch>();
  const { username } = useSelector((state: RootState) => state.user);
  const { tickers, error } = useSelector((state: RootState) => state.adminSuccessBar);
  const [tickerInput, setTickerInput] = useState("");
  const assetInfoArg: string[] | typeof skipToken = tickers.length > 0 ? tickers : skipToken;
  const { data: assetInfos } = useGetAssetInfosQuery(assetInfoArg);

  if (username !== "spike") {
    return <h1>Error</h1>;
  }

  const handleAddTicker = (event: FormEvent) => {
    event.preventDefault();
    const normalized = tickerInput.trim().toUpperCase();
    if (!normalized) {
      dispatch(setValidationError("Ticker is required."));
      return;
    }
    dispatch(addTicker(normalized));
    if (/^[A-Z][A-Z0-9.-]*$/.test(normalized) && !tickers.includes(normalized)) {
      setTickerInput("");
    }
  };

  return (
    <div className="admin-page">
      <h3>Corporate Action Success Bar</h3>
      <p className="text-muted">
        Compare Spring Boot adjusted prices/dividends against yfinance by ticker.
        Success percentage is based only on within-1% adjusted-price matches.
      </p>

      <Card className="p-3 mb-3">
        <Form onSubmit={handleAddTicker} className="d-flex gap-2 align-items-end flex-wrap">
          <Form.Group>
            <Form.Label>Manual ticker list</Form.Label>
            <Form.Control
              placeholder="e.g. AAPL or VOO"
              value={tickerInput}
              onChange={(e) => {
                setTickerInput(e.target.value);
                if (error) dispatch(clearError());
              }}
            />
          </Form.Group>
          <Button type="submit">Add Ticker</Button>
        </Form>
        {error && (
          <Alert className="mt-3 mb-0" variant="danger">
            {error}
          </Alert>
        )}
      </Card>

      <Card className="p-3">
        <Table responsive hover>
          <thead>
            <tr>
              <th>Ticker</th>
              <th>Type</th>
              <th>Price Overview</th>
              <th>Dividend Overview</th>
              <th>Combined Success</th>
              <th>Action</th>
            </tr>
          </thead>
          <tbody>
            {tickers.map((ticker) => (
              <TickerSuccessRow
                key={ticker}
                ticker={ticker}
                assetType={assetInfos?.[ticker]?.type}
              />
            ))}
            {tickers.length === 0 && (
              <tr>
                <td colSpan={6}>No tickers added yet.</td>
              </tr>
            )}
          </tbody>
        </Table>
      </Card>
    </div>
  );
}
