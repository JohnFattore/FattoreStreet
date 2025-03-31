import Table from 'react-bootstrap/Table';
import { Alert } from 'react-bootstrap';
import { useSelector, useDispatch } from "react-redux";
import { AppDispatch, RootState } from '../main';
import { setAssetSort } from '../reducers/assetReducer';
import AssetRow from './AssetRow';

// could add real vs nominal returns
const fields = [
    { name: "Ticker", type: "text", field: "ticker" },
    { name: "Shares", type: "amount", field: "shares" },
    { name: "Buy Date", type: "text", field: "buyDate" },
    { name: "Cost Basis", type: "money", field: "costBasis" },
    { name: "Current Price", type: "money", field: "currentPrice" },
    { name: "Percent Change", type: "percent", field: "percentChange" },
    { name: "S&P 500 Percent Change", type: "percent", field: "snp500PercentChange" },
    { name: "View Asset", type: "assetView", field: "ticker" },
]

export default function AssetTable() {
    const { assets, error, sort, loading } = useSelector((state: RootState) => state.assets);
    const { access, username } = useSelector((state: RootState) => state.user)
    const dispatch = useDispatch<AppDispatch>();

    const assetsOwned = assets.filter(item => item.sellDate === null);

    const handleSort = (sortColumn: string) => {
        const sortDirection =
            sort.sortColumn === sortColumn && sort.sortDirection === 'asc'
                ? 'desc'
                : 'asc';
        dispatch(setAssetSort({ sortColumn, sortDirection }));
    };

    let headers: JSX.Element[] = []
    for (let i = 0; i < fields.length; i++) {
        headers.push(<th key={i} onClick={() => handleSort(fields[i]["field"])}>{fields[i].name}</th>)
    }

    if (!access) {
        return (<></>)
    }

    if (assets.length == 0 && access && !loading) {
        return (<Alert>{username.concat(" has no assets")}</Alert>)
    }

    if (loading) {
        return (<Alert>Loading...</Alert>)
    }

    return (
        <>
            {error && <Alert variant="danger">{error}</Alert>}
            <Table>
                <thead>
                    <tr>
                        {headers}
                    </tr>
                </thead>
                <tbody>
                    {assetsOwned.map((asset) => (
                        <AssetRow key={asset.id} asset={asset} fields={fields} />
                    ))}
                </tbody>
            </Table>
        </>
    );
}