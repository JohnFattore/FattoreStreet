import { useState } from 'react';
import axios from 'axios';
import { useSelector } from "react-redux";
import { RootState } from '../main';
import { Button, Form, Alert, Spinner, Card } from 'react-bootstrap';

export default function Admin() {
    const { username } = useSelector((state: RootState) => state.user)

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

    if (username != "spike") {
        return (<h1>Error</h1>)
    }

    const springbootUrl = import.meta.env.VITE_APP_SPRINGBOOT_URL;

    const handleLoadTickers = async () => {
        setLoadLoading(true);
        setLoadResult(null);
        setLoadError(null);
        try {
            const res = await axios.get(`${springbootUrl}admin/load`, {
                headers: { 'X-Admin-Key': apiKey },
            });
            setLoadResult(typeof res.data === 'string' ? res.data : JSON.stringify(res.data));
        } catch (err: any) {
            setLoadError(err.response?.data || err.message || 'Request failed');
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
            setSyncError(err.response?.data || err.message || 'Request failed');
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
            setHistError(err.response?.data || err.message || 'Request failed');
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
            const res = await axios.get(`${springbootUrl}admin/adjust-prices`, {
                headers: { 'X-Admin-Key': apiKey },
                params,
                timeout: 0,
            });
            setAdjustResult(typeof res.data === 'string' ? res.data : JSON.stringify(res.data));
        } catch (err: any) {
            setAdjustError(err.response?.data || err.message || 'Request failed');
        } finally {
            setAdjustLoading(false);
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
            setPriceError(err.response?.data || err.message || 'Request failed');
        } finally {
            setPriceLoading(false);
        }
    };

    return (
        <div className="admin-page">
            <h2>Welcome Spike</h2>

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
                    <h6>Load Tickers</h6>
                    <p>Fetches all US tickers from NASDAQ and SEC EDGAR.</p>
                    <Button
                        onClick={handleLoadTickers}
                        disabled={loadLoading || !apiKey}
                    >
                        {loadLoading ? <><Spinner size="sm" />Loading...</> : 'Load Tickers'}
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
                    <Button
                        onClick={handleAdjustPrices}
                        disabled={adjustLoading || !apiKey}
                    >
                        {adjustLoading ? <><Spinner size="sm" /> Adjusting...</> : 'Adjust Prices'}
                    </Button>
                    {adjustResult && <Alert variant="success">{adjustResult}</Alert>}
                    {adjustError && <Alert variant="danger">{adjustError}</Alert>}
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
