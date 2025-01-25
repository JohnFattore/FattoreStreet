import AssetForm from '../components/AssetForm';
import { useEffect } from 'react';
import AssetTable from '../components/AssetTable';
import { getAssets } from '../components/axiosFunctions';
import { Row, Col, Accordion } from 'react-bootstrap';
import { useDispatch, useSelector } from "react-redux";
import { AppDispatch, RootState } from '../main';
import AllocationTable from '../components/AllocationTable';
import YahooFinanceBanner from '../components/YahooFinanceBanner';
import FinnhubBanner from '../components/FinnhubBanner';
import ReinvestDividends from '../components/ReinvestDividends'

export default function Portfolio() {
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
            <AssetTable />
            <AllocationTable />
            <YahooFinanceBanner />
            <FinnhubBanner />
        </>
    );
}