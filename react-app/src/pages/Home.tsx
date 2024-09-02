import SpikeHead from '../components/SpikeHeadImg';
import { Card } from 'react-bootstrap';

export default function Home() {
    return (
        <>
        <h1 style={{textAlign: 'center'}}>Welcome to Fattore Street!</h1>
            <Card>
                <Card.Title>
                    "By periodically investing in an index fund, for example, the know-nothing investor can actually out-perform most investment professionals.
                    Paradoxically, when ‘dumb’ money acknowledges its limitations, it ceases to be dumb." - Warren Buffett
                </Card.Title>
            </Card>
            <br></br>
            <SpikeHead />
            <h3 style={{textAlign: 'center'}}>
                Spike, the Fattore Street Mascot
            </h3>
        </>
    );
}