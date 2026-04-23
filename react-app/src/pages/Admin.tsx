import { useState } from 'react';
import { useSelector } from "react-redux";
import { RootState } from '../main';
import { Button, Form, Alert, Spinner, Card } from 'react-bootstrap';
import { useNavigate } from 'react-router-dom';
import LoginRequired from '../components/LoginRequired';
import {
    useAdminAssetLoadMutation,
    useAdminSyncFramesMutation,
    useAdminLoadHistMutation,
    useAdminAdjustPricesMutation,
    useAdminSummarizeFilingsMutation,
    useAdminRefreshIndexMetricsMutation,
    useAdminRebuildIndexesMutation,
} from '../functions/api/springbootApi';

function formatError(error: unknown): string {
    const err = error as { status?: number; data?: unknown };
    if (err.status === 401) {
        return (
            'SEC API returned 401 (unauthorized). If this persists after a fresh login, set Spring Boot ' +
            '`SECRET_KEY` to the same value as Django so JWT signatures match.'
        );
    }
    const { data } = err;
    if (typeof data === 'string' && data.trim()) return data;
    if (data && typeof data === 'object') {
        try {
            return JSON.stringify(data);
        } catch {
            return 'Request failed';
        }
    }
    return 'Request failed';
}

export default function Admin() {
    const { username, access } = useSelector((state: RootState) => state.user)
    const navigate = useNavigate();

    const [assetLoad, assetLoadState] = useAdminAssetLoadMutation();
    const [syncFrames, syncFramesState] = useAdminSyncFramesMutation();
    const [loadHist, loadHistState] = useAdminLoadHistMutation();
    const [adjustPrices, adjustPricesState] = useAdminAdjustPricesMutation();
    const [summarizeFilings, summarizeFilingsState] = useAdminSummarizeFilingsMutation();
    const [refreshIndexMetrics, refreshIndexMetricsState] = useAdminRefreshIndexMetricsMutation();
    const [rebuildIndexes, rebuildIndexesState] = useAdminRebuildIndexesMutation();

    const [loadOverwriteExisting, setLoadOverwriteExisting] = useState(false);
    const [histDays, setHistDays] = useState('252');
    const [adjustTicker, setAdjustTicker] = useState('');
    const [adjustForce, setAdjustForce] = useState(false);
    const [adjustEtfOnly, setAdjustEtfOnly] = useState(false);
    const [adjustEquityOnly, setAdjustEquityOnly] = useState(false);
    const [adjustMinConfidence, setAdjustMinConfidence] = useState('70');
    const [indexMetricsScope, setIndexMetricsScope] = useState<'russell1000' | 'all'>('russell1000');
    const [indexMetricsTicker, setIndexMetricsTicker] = useState('');
    const [indexRebuildCode, setIndexRebuildCode] = useState('');
    const [indexRebuildRefreshMetricsFirst, setIndexRebuildRefreshMetricsFirst] = useState(false);
    const [summaryTicker, setSummaryTicker] = useState('');

    if (!access) {
        return (
            <LoginRequired
                title="Sign in to React Admin"
                message="Log in with your Django account to run Spring Boot SEC jobs. Your access token is sent to the SEC API as Authorization: Bearer."
                buttonText="Sign In"
                defaultShowLogin={true}
                alertClassName="p-4 shadow-sm theme-protected-box"
            />
        );
    }

    if (username !== 'spike') {
        return <h1>Error</h1>;
    }

    return (
        <div className="admin-page">
            <h2>Welcome Spike</h2>
            <div className="mb-3">
                <Button onClick={() => navigate("/react-admin/success-bar")} variant="outline-primary">
                    Open Corporate Action Success Bar
                </Button>
            </div>

            <Card>
                <h5>Spring Boot Admin</h5>
                <p className="text-muted small mb-3">
                    Uses your Django login access token (<code>Authorization: Bearer</code>). Only Django user id 1 can
                    invoke these routes on the SEC API.
                </p>

                {/* Load Tickers */}
                <Card>
                    <h6>Asset Load (with ETF enrichment)</h6>
                    <p>Fetches all US tickers from SEC endpoints and enriches ETF listings with SEC series/class identity data.</p>
                    <p className="text-muted small mb-2">
                        <strong>Affects (springboot DB):</strong>{' '}
                        <code>Asset</code>, <code>Listing</code>
                    </p>
                    <Form.Check
                        className="mb-2"
                        type="switch"
                        label="Overwrite existing resolved ETF identities"
                        checked={loadOverwriteExisting}
                        onChange={(e) => setLoadOverwriteExisting(e.target.checked)}
                    />
                    <Button
                        onClick={() => assetLoad({ overwriteExisting: loadOverwriteExisting })}
                        disabled={assetLoadState.isLoading}
                    >
                        {assetLoadState.isLoading ? <><Spinner size="sm" />Loading...</> : 'Asset Load'}
                    </Button>
                    {assetLoadState.data && <Alert variant="success">{assetLoadState.data}</Alert>}
                    {assetLoadState.error ? <Alert variant="danger">{formatError(assetLoadState.error)}</Alert> : null}
                </Card>

                {/* Sync Frames */}
                <Card>
                    <h6>Sync Frames</h6>
                    <p>Full sync of all SEC EDGAR financial frames (2009 to present).</p>
                    <p className="text-muted small mb-2">
                        <strong>Affects (springboot DB):</strong>{' '}
                        <code>Quarter</code> and related SEC frame data
                    </p>
                    <Button
                        onClick={() => syncFrames()}
                        disabled={syncFramesState.isLoading}
                    >
                        {syncFramesState.isLoading ? <><Spinner size="sm" />Syncing...</> : 'Full Sync'}
                    </Button>
                    {syncFramesState.data && <Alert variant="success">{syncFramesState.data}</Alert>}
                    {syncFramesState.error ? <Alert variant="danger">{formatError(syncFramesState.error)}</Alert> : null}
                </Card>

                {/* Download IEX HIST */}
                <Card>
                    <h6>Download IEX HIST</h6>
                    <p>Download IEX TOPS PCAPs, parse trades, and load OHLCV directly into the database.</p>
                    <p className="text-muted small mb-2">
                        <strong>Affects (springboot DB):</strong> <code>DailyPrice</code>
                    </p>
                    <Form.Group className="mb-2">
                        <Form.Label>Trading days</Form.Label>
                        <Form.Control
                            type="number"
                            value={histDays}
                            onChange={(e) => setHistDays(e.target.value)}
                            style={{ width: '120px' }}
                        />
                    </Form.Group>
                    <Button
                        onClick={() => loadHist({ days: histDays })}
                        disabled={loadHistState.isLoading}
                    >
                        {loadHistState.isLoading ? <><Spinner size="sm" /> Downloading...</> : 'Download HIST'}
                    </Button>
                    {loadHistState.data && <Alert variant="success">{loadHistState.data}</Alert>}
                    {loadHistState.error ? <Alert variant="danger">{formatError(loadHistState.error)}</Alert> : null}
                </Card>

                {/* Adjust Prices */}
                <Card>
                    <h6>Adjust Prices (Splits & Dividends)</h6>
                    <p>Detect splits and dividends from SEC EDGAR and apply adjustment factors to OHLCV prices.</p>
                    <p className="text-muted small mb-2">
                        <strong>Affects (springboot DB):</strong>{' '}
                        <code>CorporateAction</code>, <code>DailyPrice</code>
                    </p>
                    <Form.Group className="mb-2">
                        <Form.Label>Ticker (blank for all)</Form.Label>
                        <Form.Control
                            type="text"
                            placeholder="e.g. AAPL"
                            value={adjustTicker}
                            onChange={(e) => setAdjustTicker(e.target.value)}
                            style={{ width: '140px' }}
                        />
                    </Form.Group>
                    <Form.Check
                        className="mb-2"
                        type="switch"
                        label="Force re-fetch from SEC"
                        checked={adjustForce}
                        onChange={(e) => setAdjustForce(e.target.checked)}
                    />
                    <Form.Check
                        className="mb-2"
                        type="switch"
                        label="ETF only"
                        checked={adjustEtfOnly}
                        onChange={(e) => {
                            const enabled = e.target.checked;
                            setAdjustEtfOnly(enabled);
                            if (enabled) setAdjustEquityOnly(false);
                        }}
                    />
                    <Form.Check
                        className="mb-2"
                        type="switch"
                        label="Equity only"
                        checked={adjustEquityOnly}
                        onChange={(e) => {
                            const enabled = e.target.checked;
                            setAdjustEquityOnly(enabled);
                            if (enabled) setAdjustEtfOnly(false);
                        }}
                    />
                    <Form.Group className="mb-2">
                        <Form.Label>ETF minimum confidence</Form.Label>
                        <Form.Control
                            type="number"
                            value={adjustMinConfidence}
                            onChange={(e) => setAdjustMinConfidence(e.target.value)}
                            style={{ width: '140px' }}
                            min={0}
                            max={100}
                        />
                    </Form.Group>
                    <Button
                        onClick={() => adjustPrices({
                            ticker: adjustTicker.trim() ? adjustTicker.trim().toUpperCase() : undefined,
                            force: adjustForce || undefined,
                            etfOnly: adjustEtfOnly || undefined,
                            equityOnly: adjustEquityOnly || undefined,
                            minConfidence: Number(adjustMinConfidence),
                        })}
                        disabled={adjustPricesState.isLoading}
                    >
                        {adjustPricesState.isLoading ? <><Spinner size="sm" /> Adjusting...</> : 'Adjust Prices'}
                    </Button>
                    {adjustPricesState.data && <Alert variant="success">{adjustPricesState.data}</Alert>}
                    {adjustPricesState.error ? <Alert variant="danger">{formatError(adjustPricesState.error)}</Alert> : null}
                </Card>

                {/* Summarize 10-K Filings */}
                <Card>
                    <h6>Summarize 10-K Filings</h6>
                    <p>Fetch 10-K filings from SEC EDGAR and generate MD&A summaries via the local LLM.</p>
                    <p className="text-muted small mb-2">
                        <strong>Affects (springboot DB):</strong> <code>FilingSummary</code>
                    </p>
                    <Form.Group className="mb-2">
                        <Form.Label>Ticker (blank for all)</Form.Label>
                        <Form.Control
                            type="text"
                            placeholder="e.g. AAPL"
                            value={summaryTicker}
                            onChange={(e) => setSummaryTicker(e.target.value)}
                            style={{ width: '140px' }}
                        />
                    </Form.Group>
                    <Button
                        onClick={() => summarizeFilings({
                            ticker: summaryTicker.trim() ? summaryTicker.trim().toUpperCase() : undefined,
                        })}
                        disabled={summarizeFilingsState.isLoading}
                    >
                        {summarizeFilingsState.isLoading ? <><Spinner size="sm" /> Summarizing...</> : 'Summarize Filings'}
                    </Button>
                    {summarizeFilingsState.data && <Alert variant="success">{summarizeFilingsState.data}</Alert>}
                    {summarizeFilingsState.error ? <Alert variant="danger">{formatError(summarizeFilingsState.error)}</Alert> : null}
                </Card>

                {/* Index metrics + cap-ranked rebuild */}
                <Card>
                    <h6>Refresh index stock metrics</h6>
                    <p>
                        Recompute per-listing market cap, float, and volume fields from IEX daily prices and SEC
                        companyfacts (run after listings and prices exist).
                    </p>
                    <p className="text-muted small mb-2">
                        <strong>Affects (springboot DB):</strong> <code>ListingIndexMetrics</code> (reads{' '}
                        <code>Listing</code>, <code>Asset</code>, <code>DailyPrice</code>)
                    </p>
                    <Form.Group className="mb-2">
                        <Form.Label>Ticker scope</Form.Label>
                        <Form.Select
                            value={indexMetricsScope}
                            onChange={(e) => setIndexMetricsScope(e.target.value as 'russell1000' | 'all')}
                            disabled={indexMetricsTicker.trim().length > 0}
                            style={{ maxWidth: 360 }}
                        >
                            <option value="russell1000">Russell 1000 (IWB holdings file)</option>
                            <option value="all">All listings in DB</option>
                        </Form.Select>
                    </Form.Group>
                    <Form.Group className="mb-2">
                        <Form.Label>Ticker (blank for scope above)</Form.Label>
                        <Form.Control
                            type="text"
                            placeholder="e.g. NFLX"
                            value={indexMetricsTicker}
                            onChange={(e) => setIndexMetricsTicker(e.target.value)}
                            style={{ width: '140px' }}
                        />
                    </Form.Group>
                    <Button
                        onClick={() => refreshIndexMetrics({
                            scope: indexMetricsScope,
                            ticker: indexMetricsTicker.trim() ? indexMetricsTicker.trim().toUpperCase() : undefined,
                        })}
                        disabled={refreshIndexMetricsState.isLoading}
                    >
                        {refreshIndexMetricsState.isLoading ? (
                            <>
                                <Spinner size="sm" /> Refreshing…
                            </>
                        ) : (
                            'Refresh index metrics'
                        )}
                    </Button>
                    {refreshIndexMetricsState.data && <Alert variant="success">{refreshIndexMetricsState.data}</Alert>}
                    {refreshIndexMetricsState.error ? <Alert variant="danger">{formatError(refreshIndexMetricsState.error)}</Alert> : null}
                </Card>

                <Card>
                    <h6>Rebuild cap-ranked indexes</h6>
                    <p>
                        Russell-style float-adjusted cap weights: upserts <code>MarketIndex</code> and replaces{' '}
                        <code>IndexMember</code> rows from <code>ListingIndexMetrics</code>. Choose one index or{' '}
                        <strong>All</strong> to rebuild every configured index (order: <code>FAT100</code>,{' '}
                        <code>FAT1000</code>, <code>FAT50</code>). Not an official FTSE Russell index.
                    </p>
                    <p className="text-muted small mb-2">
                        <strong>Affects (springboot DB):</strong> <code>MarketIndex</code>, <code>IndexMember</code>
                    </p>
                    <Form.Group className="mb-2">
                        <Form.Label>Index code</Form.Label>
                        <Form.Select
                            value={indexRebuildCode}
                            onChange={(e) => setIndexRebuildCode(e.target.value)}
                            style={{ maxWidth: 280 }}
                        >
                            <option value="">All (FAT100, FAT1000, FAT50)</option>
                            <option value="FAT50">FAT50 — Fattore 50</option>
                            <option value="FAT100">FAT100 — Fattore 100</option>
                            <option value="FAT1000">FAT1000 — Fattore 1000</option>
                        </Form.Select>
                    </Form.Group>
                    <Form.Check
                        className="mb-2"
                        type="switch"
                        label="Run refresh index metrics first (same as Refresh index metrics above)"
                        checked={indexRebuildRefreshMetricsFirst}
                        onChange={(e) => setIndexRebuildRefreshMetricsFirst(e.target.checked)}
                    />
                    <Button
                        onClick={() => rebuildIndexes({
                            code: indexRebuildCode.trim() || undefined,
                            refreshMetrics: indexRebuildRefreshMetricsFirst || undefined,
                        })}
                        disabled={rebuildIndexesState.isLoading}
                    >
                        {rebuildIndexesState.isLoading ? (
                            <>
                                <Spinner size="sm" /> Rebuilding…
                            </>
                        ) : (
                            'Rebuild index(es)'
                        )}
                    </Button>
                    {rebuildIndexesState.data && <Alert variant="success">{rebuildIndexesState.data}</Alert>}
                    {rebuildIndexesState.error ? <Alert variant="danger">{formatError(rebuildIndexesState.error)}</Alert> : null}
                </Card>

            </Card>
        </div>
    );
}
