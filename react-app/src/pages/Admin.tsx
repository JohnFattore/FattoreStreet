import { useState } from 'react';
import axios from 'axios';
import { useSelector } from "react-redux";
import { RootState } from '../main';
import { Button, Form, Alert, Spinner, Card } from 'react-bootstrap';
import { useNavigate } from 'react-router-dom';
import LoginRequired from '../components/LoginRequired';
import { springAdminApi } from '../functions/springAdminApi';

export default function Admin() {
    const { username, access } = useSelector((state: RootState) => state.user)
    const navigate = useNavigate();

    // Load Tickers state
    const [loadLoading, setLoadLoading] = useState(false);
    const [loadResult, setLoadResult] = useState<string | null>(null);
    const [loadError, setLoadError] = useState<string | null>(null);

    // Sync Frames state
    const [syncLoading, setSyncLoading] = useState(false);
    const [syncResult, setSyncResult] = useState<string | null>(null);
    const [syncError, setSyncError] = useState<string | null>(null);

    // Load HIST state
    const [histLoading, setHistLoading] = useState(false);
    const [histResult, setHistResult] = useState<string | null>(null);
    const [histError, setHistError] = useState<string | null>(null);
    const [histDays, setHistDays] = useState('252');

    // Adjust Prices state
    const [adjustLoading, setAdjustLoading] = useState(false);
    const [adjustResult, setAdjustResult] = useState<string | null>(null);
    const [adjustError, setAdjustError] = useState<string | null>(null);
    const [adjustTicker, setAdjustTicker] = useState('');
    const [adjustForce, setAdjustForce] = useState(false);
    const [adjustEtfOnly, setAdjustEtfOnly] = useState(false);
    const [adjustEquityOnly, setAdjustEquityOnly] = useState(false);
    const [adjustMinConfidence, setAdjustMinConfidence] = useState('70');

    const [loadOverwriteExisting, setLoadOverwriteExisting] = useState(false);

    // Index metrics (ListingIndexMetrics) state
    const [indexMetricsLoading, setIndexMetricsLoading] = useState(false);
    const [indexMetricsResult, setIndexMetricsResult] = useState<string | null>(null);
    const [indexMetricsError, setIndexMetricsError] = useState<string | null>(null);

    // Cap-ranked index rebuild (MarketIndex + IndexMember): POST .../rebuild optional ?code=
    const [indexRebuildCode, setIndexRebuildCode] = useState('');
    const [indexRebuildLoading, setIndexRebuildLoading] = useState(false);
    const [indexRebuildResult, setIndexRebuildResult] = useState<string | null>(null);
    const [indexRebuildError, setIndexRebuildError] = useState<string | null>(null);
    const [indexRebuildRefreshMetricsFirst, setIndexRebuildRefreshMetricsFirst] = useState(false);

    // Summarize Filings state
    const [summaryLoading, setSummaryLoading] = useState(false);
    const [summaryResult, setSummaryResult] = useState<string | null>(null);
    const [summaryError, setSummaryError] = useState<string | null>(null);
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

    const normalizeError = (err: unknown): string => {
        if (axios.isAxiosError(err)) {
            if (err.response?.status === 401) {
                return (
                    'SEC API returned 401 (unauthorized). If this persists after a fresh login, set Spring Boot ' +
                    '`SECRET_KEY` to the same value as Django so JWT signatures match.'
                );
            }
            const data = err.response?.data;
            if (typeof data === 'string' && data.trim()) return data;
            if (data && typeof data === 'object') {
                try {
                    return JSON.stringify(data);
                } catch {
                    return 'Request failed';
                }
            }
            if (err.message?.trim()) return err.message;
            return 'Request failed';
        }
        if (err instanceof Error && err.message.trim()) return err.message;
        return 'Request failed';
    };

    const handleLoadTickers = async () => {
        setLoadLoading(true);
        setLoadResult(null);
        setLoadError(null);
        try {
            const params: Record<string, string> = {};
            if (loadOverwriteExisting) params.overwriteExisting = 'true';
            let res;
            try {
                res = await springAdminApi.get('admin/asset-load', {
                    params,
                    timeout: 0,
                });
            } catch (assetLoadErr: unknown) {
                // Backward-compatible fallback while older backend instances may still expose /admin/load.
                if (axios.isAxiosError(assetLoadErr) && assetLoadErr.response?.status === 404) {
                    res = await springAdminApi.get('admin/load', {
                        timeout: 0,
                    });
                } else {
                    throw assetLoadErr;
                }
            }
            setLoadResult(typeof res.data === 'string' ? res.data : JSON.stringify(res.data));
        } catch (err: unknown) {
            setLoadError(normalizeError(err));
        } finally {
            setLoadLoading(false);
        }
    };

    const handleSyncFrames = async () => {
        setSyncLoading(true);
        setSyncResult(null);
        setSyncError(null);
        try {
            const res = await springAdminApi.get('admin/sync-frames');
            setSyncResult(typeof res.data === 'string' ? res.data : JSON.stringify(res.data));
        } catch (err: unknown) {
            setSyncError(normalizeError(err));
        } finally {
            setSyncLoading(false);
        }
    };

    const handleLoadHist = async () => {
        setHistLoading(true);
        setHistResult(null);
        setHistError(null);
        try {
            const res = await springAdminApi.get('admin/load-hist', {
                params: { days: histDays },
                timeout: 0,
            });
            setHistResult(typeof res.data === 'string' ? res.data : JSON.stringify(res.data));
        } catch (err: unknown) {
            setHistError(normalizeError(err));
        } finally {
            setHistLoading(false);
        }
    };

    const handleAdjustPrices = async () => {
        setAdjustLoading(true);
        setAdjustResult(null);
        setAdjustError(null);
        try {
            const params: Record<string, string> = {};
            if (adjustTicker.trim()) params.ticker = adjustTicker.trim().toUpperCase();
            if (adjustForce) params.force = 'true';
            if (adjustEtfOnly) params.etfOnly = 'true';
            if (adjustEquityOnly) params.equityOnly = 'true';
            const minConfidence = Number(adjustMinConfidence);
            if (!Number.isNaN(minConfidence) && minConfidence >= 0) {
                params.minConfidence = String(minConfidence);
            }
            const res = await springAdminApi.get('admin/adjust-prices', {
                params,
                timeout: 0,
            });
            setAdjustResult(typeof res.data === 'string' ? res.data : JSON.stringify(res.data));
        } catch (err: unknown) {
            setAdjustError(normalizeError(err));
        } finally {
            setAdjustLoading(false);
        }
    };

    const handleSummarizeFilings = async () => {
        setSummaryLoading(true);
        setSummaryResult(null);
        setSummaryError(null);
        try {
            const params: Record<string, string> = {};
            if (summaryTicker.trim()) params.ticker = summaryTicker.trim().toUpperCase();
            const res = await springAdminApi.get('admin/summarize-filings', {
                params,
                timeout: 0,
            });
            setSummaryResult(typeof res.data === 'string' ? res.data : JSON.stringify(res.data));
        } catch (err: unknown) {
            setSummaryError(normalizeError(err));
        } finally {
            setSummaryLoading(false);
        }
    };

    const handleRefreshIndexMetrics = async () => {
        setIndexMetricsLoading(true);
        setIndexMetricsResult(null);
        setIndexMetricsError(null);
        try {
            const res = await springAdminApi.post('admin/indexes/refresh-stocks', {}, { timeout: 0 });
            setIndexMetricsResult(typeof res.data === 'string' ? res.data : JSON.stringify(res.data));
        } catch (err: unknown) {
            setIndexMetricsError(normalizeError(err));
        } finally {
            setIndexMetricsLoading(false);
        }
    };

    const handleRebuildIndexes = async () => {
        setIndexRebuildLoading(true);
        setIndexRebuildResult(null);
        setIndexRebuildError(null);
        try {
            const params: Record<string, string> = {};
            if (indexRebuildRefreshMetricsFirst) params.refreshMetrics = 'true';
            const c = indexRebuildCode.trim();
            if (c) params.code = c;
            const res = await springAdminApi.post('admin/indexes/rebuild', {}, { params, timeout: 0 });
            setIndexRebuildResult(typeof res.data === 'string' ? res.data : JSON.stringify(res.data));
        } catch (err: unknown) {
            setIndexRebuildError(normalizeError(err));
        } finally {
            setIndexRebuildLoading(false);
        }
    };

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
                        onClick={handleLoadTickers}
                        disabled={loadLoading}
                    >
                        {loadLoading ? <><Spinner size="sm" />Loading...</> : 'Asset Load'}
                    </Button>
                    {loadResult && <Alert variant="success">{loadResult}</Alert>}
                    {loadError && <Alert variant="danger">{loadError}</Alert>}
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
                        onClick={handleSyncFrames}
                        disabled={syncLoading}
                    >
                        {syncLoading ? <><Spinner size="sm" />Syncing...</> : 'Full Sync'}
                    </Button>
                    {syncResult && <Alert variant="success">{syncResult}</Alert>}
                    {syncError && <Alert variant="danger">{syncError}</Alert>}
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
                        onClick={handleLoadHist}
                        disabled={histLoading}
                    >
                        {histLoading ? <><Spinner size="sm" /> Downloading...</> : 'Download HIST'}
                    </Button>
                    {histResult && <Alert variant="success">{histResult}</Alert>}
                    {histError && <Alert variant="danger">{histError}</Alert>}
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
                        onClick={handleAdjustPrices}
                        disabled={adjustLoading}
                    >
                        {adjustLoading ? <><Spinner size="sm" /> Adjusting...</> : 'Adjust Prices'}
                    </Button>
                    {adjustResult && <Alert variant="success">{adjustResult}</Alert>}
                    {adjustError && <Alert variant="danger">{adjustError}</Alert>}
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
                        onClick={handleSummarizeFilings}
                        disabled={summaryLoading}
                    >
                        {summaryLoading ? <><Spinner size="sm" /> Summarizing...</> : 'Summarize Filings'}
                    </Button>
                    {summaryResult && <Alert variant="success">{summaryResult}</Alert>}
                    {summaryError && <Alert variant="danger">{summaryError}</Alert>}
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
                    <Button
                        onClick={handleRefreshIndexMetrics}
                        disabled={indexMetricsLoading}
                    >
                        {indexMetricsLoading ? (
                            <>
                                <Spinner size="sm" /> Refreshing…
                            </>
                        ) : (
                            'Refresh index metrics'
                        )}
                    </Button>
                    {indexMetricsResult && <Alert variant="success">{indexMetricsResult}</Alert>}
                    {indexMetricsError && <Alert variant="danger">{indexMetricsError}</Alert>}
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
                    <Button onClick={handleRebuildIndexes} disabled={indexRebuildLoading}>
                        {indexRebuildLoading ? (
                            <>
                                <Spinner size="sm" /> Rebuilding…
                            </>
                        ) : (
                            'Rebuild index(es)'
                        )}
                    </Button>
                    {indexRebuildResult && <Alert variant="success">{indexRebuildResult}</Alert>}
                    {indexRebuildError && <Alert variant="danger">{indexRebuildError}</Alert>}
                </Card>

            </Card>
        </div>
    );
}
