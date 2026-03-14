import { useState } from 'react';
import axios from 'axios';
import { useSelector } from "react-redux";
import { RootState } from '../main';
import { Button, Form, Alert, Spinner, Card } from 'react-bootstrap';
import { useNavigate } from 'react-router-dom';

export default function Admin() {
    const { username } = useSelector((state: RootState) => state.user)
    const navigate = useNavigate();

    // Shared API key state
    const [apiKey, setApiKey] = useState('');

    // Load Tickers state
    const [loadLoading, setLoadLoading] = useState(false);
    const [loadResult, setLoadResult] = useState<string | null>(null);
    const [loadError, setLoadError] = useState<string | null>(null);

    // Sync Frames state
    const [syncLoading, setSyncLoading] = useState(false);
    const [syncResult, setSyncResult] = useState<string | null>(null);
    const [syncError, setSyncError] = useState<string | null>(null);

    // Load Prices state
    const [priceLoading, setPriceLoading] = useState(false);
    const [priceResult, setPriceResult] = useState<string | null>(null);
    const [priceError, setPriceError] = useState<string | null>(null);

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

    // Summarize Filings state
    const [summaryLoading, setSummaryLoading] = useState(false);
    const [summaryResult, setSummaryResult] = useState<string | null>(null);
    const [summaryError, setSummaryError] = useState<string | null>(null);
    const [summaryTicker, setSummaryTicker] = useState('');

    if (username != "spike") {
        return (<h1>Error</h1>)
    }

    const springbootUrl = import.meta.env.VITE_APP_SPRINGBOOT_URL;

    const normalizeError = (err: any): string => {
        const data = err?.response?.data;
        if (typeof data === 'string' && data.trim()) return data;
        if (data && typeof data === 'object') {
            try {
                return JSON.stringify(data);
            } catch {
                return 'Request failed';
            }
        }
        if (typeof err?.message === 'string' && err.message.trim()) return err.message;
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
                res = await axios.get(`${springbootUrl}admin/asset-load`, {
                    headers: { 'X-Admin-Key': apiKey },
                    params,
                    timeout: 0,
                });
            } catch (assetLoadErr: any) {
                // Backward-compatible fallback while older backend instances may still expose /admin/load.
                if (assetLoadErr?.response?.status === 404) {
                    res = await axios.get(`${springbootUrl}admin/load`, {
                        headers: { 'X-Admin-Key': apiKey },
                        timeout: 0,
                    });
                } else {
                    throw assetLoadErr;
                }
            }
            setLoadResult(typeof res.data === 'string' ? res.data : JSON.stringify(res.data));
        } catch (err: any) {
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
            const res = await axios.get(`${springbootUrl}admin/sync-frames`, {
                headers: { 'X-Admin-Key': apiKey },
            });
            setSyncResult(typeof res.data === 'string' ? res.data : JSON.stringify(res.data));
        } catch (err: any) {
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
            const res = await axios.get(`${springbootUrl}admin/load-hist`, {
                headers: { 'X-Admin-Key': apiKey },
                params: { days: histDays },
                timeout: 0,
            });
            setHistResult(typeof res.data === 'string' ? res.data : JSON.stringify(res.data));
        } catch (err: any) {
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
            const res = await axios.get(`${springbootUrl}admin/adjust-prices`, {
                headers: { 'X-Admin-Key': apiKey },
                params,
                timeout: 0,
            });
            setAdjustResult(typeof res.data === 'string' ? res.data : JSON.stringify(res.data));
        } catch (err: any) {
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
            const res = await axios.get(`${springbootUrl}admin/summarize-filings`, {
                headers: { 'X-Admin-Key': apiKey },
                params,
                timeout: 0,
            });
            setSummaryResult(typeof res.data === 'string' ? res.data : JSON.stringify(res.data));
        } catch (err: any) {
            setSummaryError(normalizeError(err));
        } finally {
            setSummaryLoading(false);
        }
    };

    const handleLoadPrices = async () => {
        setPriceLoading(true);
        setPriceResult(null);
        setPriceError(null);
        try {
            const res = await axios.get(`${springbootUrl}admin/load-prices`, {
                headers: { 'X-Admin-Key': apiKey },
            });
            setPriceResult(typeof res.data === 'string' ? res.data : JSON.stringify(res.data));
        } catch (err: any) {
            setPriceError(normalizeError(err));
        } finally {
            setPriceLoading(false);
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
                <Form.Group>
                    <Form.Label>Admin API Key</Form.Label>
                    <Form.Control
                        type="password"
                        placeholder="Enter admin API key"
                        value={apiKey}
                        onChange={(e) => setApiKey(e.target.value)}
                    />
                </Form.Group>

                {/* Load Tickers */}
                <Card>
                    <h6>Asset Load (with ETF enrichment)</h6>
                    <p>Fetches all US tickers from SEC endpoints and enriches ETF listings with SEC series/class identity data.</p>
                    <Form.Check
                        className="mb-2"
                        type="switch"
                        label="Overwrite existing resolved ETF identities"
                        checked={loadOverwriteExisting}
                        onChange={(e) => setLoadOverwriteExisting(e.target.checked)}
                    />
                    <Button
                        onClick={handleLoadTickers}
                        disabled={loadLoading || !apiKey}
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
                    <Button
                        onClick={handleSyncFrames}
                        disabled={syncLoading || !apiKey}
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
                        disabled={histLoading || !apiKey}
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
                        disabled={adjustLoading || !apiKey}
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
                        disabled={summaryLoading || !apiKey}
                    >
                        {summaryLoading ? <><Spinner size="sm" /> Summarizing...</> : 'Summarize Filings'}
                    </Button>
                    {summaryResult && <Alert variant="success">{summaryResult}</Alert>}
                    {summaryError && <Alert variant="danger">{summaryError}</Alert>}
                </Card>

                {/* Load IEX Prices (CSV) */}
                <Card>
                    <h6>Load IEX Prices (CSV)</h6>
                    <p>Ingest pre-generated IEX daily OHLCV CSV files from the server data directory.</p>
                    <Button
                        onClick={handleLoadPrices}
                        disabled={priceLoading || !apiKey}
                    >
                        {priceLoading ? <><Spinner size="sm" />Loading...</> : 'Load Prices'}
                    </Button>
                    {priceResult && <Alert variant="success">{priceResult}</Alert>}
                    {priceError && <Alert variant="danger">{priceError}</Alert>}
                </Card>

            </Card>
        </div>
    );
}
