import AssetForm from '../components/AssetForm';
import { useEffect } from 'react';
import AssetTable from '../components/AssetTable';
import { getAssets } from '../components/axiosFunctions';
import { Row, Col, Accordion, Button } from 'react-bootstrap';
import { useDispatch, useSelector } from "react-redux";
import { AppDispatch, RootState } from '../main';
import YahooFinanceBanner from '../components/YahooFinanceBanner';
import FinnhubBanner from '../components/FinnhubBanner';
import ReinvestDividends from '../components/ReinvestDividends'
import { useNavigate } from "react-router-dom";
import AssetSoldTable from '../components/AssetSoldTable';

export default function Portfolio() {
    const navigate = useNavigate();
    const dispatch = useDispatch<AppDispatch>();
    const { username } = useSelector((state: RootState) => state.user);

    useEffect(() => {
        dispatch(getAssets());
    }, [dispatch])

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
                                <ReinvestDividends />
                            </Accordion.Body>
                        </Accordion.Item>
                    </Accordion>
                </Col>
            </Row>
            <h1>{username ? `${username}'s Portfolio` : 'Portfolio'}</h1>
            <AssetTable/>
            <AssetSoldTable />
            {/*<AllocationTable />*/}
            <Button onClick={() => navigate("/snp500Prices")}>Historical S&P500 Prices</Button>
            <YahooFinanceBanner />
            <FinnhubBanner />
        </>
    );
}