import Table from 'react-bootstrap/Table';
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
    { name: "Sell Price", type: "money", field: "sellPrice" },
    { name: "Percent Change", type: "percent", field: "percentChange" },
    { name: "S&P 500 Percent Change", type: "percent", field: "snp500PercentChange" },
    { name: "Sell Date", type: "text", field: "sellDate" },
    { name: "View Asset", type: "assetView", field: "ticker" },
]

export default function AssetSoldTable() {
    const { assets, sort } = useSelector((state: RootState) => state.assets);
    const { access } = useSelector((state: RootState) => state.user)
    const dispatch = useDispatch<AppDispatch>();

    const handleSort = (sortColumn: string) => {
        const sortDirection =
            sort.sortColumn === sortColumn && sort.sortDirection === 'asc'
                ? 'desc'
                : 'asc';
        dispatch(setAssetSort({ sortColumn, sortDirection }));
    };

    const assetsSold = assets.filter(item => item.sellDate !== null);

    let headers: JSX.Element[] = []
    for (let i = 0; i < fields.length; i++) {
        headers.push(<th key={i} onClick={() => handleSort(fields[i]["field"])}>{fields[i].name}</th>)
    }

    if (!access) return null

    if (assets.length == 0 && access) return null

    return (
        <>
            <h3>Sold Positions</h3>
            <Table>
                <thead>
                    <tr>
                        {headers}
                    </tr>
                </thead>
                <tbody>
                    {assetsSold.map((asset) => (
                        <AssetRow key={asset.id} asset={asset} fields={fields} />
                    ))}
                </tbody>
            </Table>
        </>

    );
}