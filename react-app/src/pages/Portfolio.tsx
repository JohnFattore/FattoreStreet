import AssetForm from '../components/AssetForm';
import { useEffect } from 'react';
import AssetTable from '../components/AssetTable';
import { getAssets } from '../components/axiosFunctions';
import LoginForm from '../components/LoginForm';
import { Row, Col, Accordion } from 'react-bootstrap';
import { useDispatch } from "react-redux";
import { AppDispatch } from '../main';
import AllocationTable from '../components/AllocationTable';

export default function Portfolio() {
    const dispatch = useDispatch<AppDispatch>();
    //const { assets, loading, error } = useSelector((state: RootState) => state.assets);

    useEffect(() => {
        dispatch(getAssets());
    }, [dispatch])

    return (
        <>
            <h3>Add Assets</h3>
            <Row>
            <Col>
                    <AssetForm />
                </Col>
                <Col>
                    <Accordion defaultActiveKey="1">
                        <Accordion.Item eventKey="0">
                            <Accordion.Header>Login</Accordion.Header>
                            <Accordion.Body>
                                <LoginForm setMessage={console.log} />
                            </Accordion.Body>
                        </Accordion.Item>
                    </Accordion>
                </Col>
            </Row>
            <h1 role="assetTableHeader">User's Portfolio</h1>
            <AssetTable />
            <h3>Allocation</h3>
            <AllocationTable />
        </>
    );
}

/*

*/