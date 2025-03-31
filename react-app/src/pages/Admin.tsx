import axios from 'axios';
import { useSelector } from "react-redux";
import { RootState } from '../main';
import { Button } from 'react-bootstrap';

const adminPost = async (url: string, token: string) => {
    try {
        await axios.post(import.meta.env.VITE_APP_DJANGO_PORTFOLIO_URL.concat(url, "/"),
            {
                headers: {
                    'Authorization': `Bearer ${token}`,  // Include token from Redux state
                }
            }
        )
        return (`Successfully posted to ${url}`)
    }
    catch (error) {
        throw error;
    }
}

export default function Admin() {
    const { username, access } = useSelector((state: RootState) => state.user)

    if (username != "spike") {
        return (<h1>Error</h1>)
    }

    const urls = ["snp500-price-create",
        "update-cost-basis",
        "asset-info-create",
        "asset-update-info"
    ]
    
    return (
        <>
            <h2>Welcome Spike</h2>
            {urls.map((url) => (
                <Button key={url} onClick ={() => adminPost(url, access)}> {url} </Button>
            ))}
        </>
    );
}