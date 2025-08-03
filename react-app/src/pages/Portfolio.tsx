import AssetForm from '../components/AssetForm';
import AssetTable from '../components/AssetTable';
import { Row, Col, Accordion } from 'react-bootstrap';
import { useSelector } from "react-redux";
import { RootState } from '../main';
import YahooFinanceBanner from '../components/YahooFinanceBanner';
import FinnhubBanner from '../components/FinnhubBanner';
import AdjustedCostBasis from '../components/AdjustedCostBasis'
import AssetSoldTable from '../components/AssetSoldTable';

export default function Portfolio() {
    const { username } = useSelector((state: RootState) => state.user);
    return (
        <>
            <Row>
                <Col>
                    <AssetForm />
                </Col>
                <Col>
                    <Accordion defaultActiveKey="0">
                        <Accordion.Item eventKey="2">
                            <Accordion.Header>Historical Stock Prices</Accordion.Header>
                            <Accordion.Body>
                                <AdjustedCostBasis />
                            </Accordion.Body>
                        </Accordion.Item>
                    </Accordion>
                </Col>
            </Row>
            <h1>{username ? `${username}'s Portfolio` : 'Portfolio'}</h1>
            <AssetTable/>
            <AssetSoldTable />
            <YahooFinanceBanner />
            <FinnhubBanner />
        </>
    );
}