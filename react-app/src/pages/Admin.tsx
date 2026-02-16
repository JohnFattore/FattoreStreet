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
            </Card>
        </div>
    );
}
