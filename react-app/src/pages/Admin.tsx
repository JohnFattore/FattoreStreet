import { useState } from 'react';
import axios from 'axios';
import { useSelector } from "react-redux";
import { RootState } from '../main';
import { Button, Form, Alert, Spinner, Card } from 'react-bootstrap';

const adminPost = async (url: string, token: string) => {
    try {
        await axios.post(import.meta.env.VITE_APP_DJANGO_PORTFOLIO_URL.concat(url, "/"),
            {
                headers: {
                    'Authorization': `Bearer ${token}`,
                }
            }
        )
        return (`Successfully posted to ${url}`)
    }
    catch (error) {
        throw error;
    }
}

type SyncMode = 'period' | 'year' | 'full';

export default function Admin() {
    const { username, access } = useSelector((state: RootState) => state.user)

    // Shared API key state
    const [apiKey, setApiKey] = useState('');

    // Load Tickers state
    const [loadLoading, setLoadLoading] = useState(false);
    const [loadResult, setLoadResult] = useState<string | null>(null);
    const [loadError, setLoadError] = useState<string | null>(null);

    // Sync Frames state
    const [syncMode, setSyncMode] = useState<SyncMode>('period');
    const [period, setPeriod] = useState('');
    const [year, setYear] = useState('');
    const [syncLoading, setSyncLoading] = useState(false);
    const [syncResult, setSyncResult] = useState<string | null>(null);
    const [syncError, setSyncError] = useState<string | null>(null);

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
            const params: Record<string, string | boolean> = {};
            if (syncMode === 'period') params.period = period;
            else if (syncMode === 'year') params.year = year;
            else params.full = true;

            const res = await axios.get(`${springbootUrl}admin/sync-frames`, {
                headers: { 'X-Admin-Key': apiKey },
                params,
            });
            setSyncResult(typeof res.data === 'string' ? res.data : JSON.stringify(res.data));
        } catch (err: any) {
            setSyncError(err.response?.data || err.message || 'Request failed');
        } finally {
            setSyncLoading(false);
        }
    };

    const djangoUrls = ["snp500-price-create",
        "update-cost-basis",
        "asset-info-create",
        "asset-update-info"
    ]

    return (
        <div className="p-3" style={{ maxWidth: 600 }}>
            <h2>Welcome Spike</h2>

            {/* Django Admin Buttons */}
            <Card className="mb-4 p-3">
                <h5>Django Admin</h5>
                <div className="d-flex flex-wrap gap-2">
                    {djangoUrls.map((url) => (
                        <Button key={url} onClick={() => adminPost(url, access)}>{url}</Button>
                    ))}
                </div>
            </Card>

            {/* Shared API Key */}
            <Card className="mb-4 p-3">
                <h5>Spring Boot Admin</h5>
                <Form.Group className="mb-3">
                    <Form.Label>Admin API Key</Form.Label>
                    <Form.Control
                        type="password"
                        placeholder="Enter admin API key"
                        value={apiKey}
                        onChange={(e) => setApiKey(e.target.value)}
                    />
                </Form.Group>

                {/* Load Tickers */}
                <Card className="mb-3 p-3">
                    <h6>Load Tickers</h6>
                    <p className="text-muted mb-2">Fetches all US tickers from NASDAQ and SEC EDGAR.</p>
                    <Button
                        onClick={handleLoadTickers}
                        disabled={loadLoading || !apiKey}
                    >
                        {loadLoading ? <><Spinner size="sm" className="me-2" />Loading...</> : 'Load Tickers'}
                    </Button>
                    {loadResult && <Alert variant="success" className="mt-2">{loadResult}</Alert>}
                    {loadError && <Alert variant="danger" className="mt-2">{loadError}</Alert>}
                </Card>

                {/* Sync Frames */}
                <Card className="p-3">
                    <h6>Sync Frames</h6>
                    <p className="text-muted mb-2">Sync SEC EDGAR financial frames for equities.</p>
                    <Form.Group className="mb-3">
                        <Form.Label>Mode</Form.Label>
                        <Form.Select
                            value={syncMode}
                            onChange={(e) => setSyncMode(e.target.value as SyncMode)}
                        >
                            <option value="period">Period</option>
                            <option value="year">Year</option>
                            <option value="full">Full Sync</option>
                        </Form.Select>
                    </Form.Group>

                    {syncMode === 'period' && (
                        <Form.Group className="mb-3">
                            <Form.Label>Period</Form.Label>
                            <Form.Control
                                type="text"
                                placeholder="e.g. CY2024Q1"
                                value={period}
                                onChange={(e) => setPeriod(e.target.value)}
                            />
                        </Form.Group>
                    )}

                    {syncMode === 'year' && (
                        <Form.Group className="mb-3">
                            <Form.Label>Year</Form.Label>
                            <Form.Control
                                type="number"
                                placeholder="e.g. 2024"
                                value={year}
                                onChange={(e) => setYear(e.target.value)}
                            />
                        </Form.Group>
                    )}

                    <Button
                        onClick={handleSyncFrames}
                        disabled={
                            syncLoading ||
                            !apiKey ||
                            (syncMode === 'period' && !period) ||
                            (syncMode === 'year' && !year)
                        }
                    >
                        {syncLoading ? <><Spinner size="sm" className="me-2" />Syncing...</> : 'Sync Frames'}
                    </Button>
                    {syncResult && <Alert variant="success" className="mt-2">{syncResult}</Alert>}
                    {syncError && <Alert variant="danger" className="mt-2">{syncError}</Alert>}
                </Card>
            </Card>
        </div>
    );
}