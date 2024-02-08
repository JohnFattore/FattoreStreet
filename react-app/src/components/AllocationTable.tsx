import React from 'react';
import { getAssets } from './AxiosFunctions';
import Table from 'react-bootstrap/Table';
import AllocationRow from './AllocationRow';
import { IAllocation } from '../interfaces';

export default function AllocationTable() {
    const [assets, setAssets] = React.useState([]);

    // API call for user's owned assets
    React.useEffect(() => {
        getAssets()
            .then((response) => {
                setAssets(response.data);
            })
            .catch(() => {
                alert("Error")
            })
    }, []);

    if (!assets) return null;

    if (assets.length == 0) {
        return (<h3 role='noAssets'>You don't own any assets</h3>)
    }

    const allocations: IAllocation[] = []

    // iterate over all assets, return a list of allocations
    for (let i = 0; i < assets.length; i++) {
        if (allocations.some((allocation) => allocation.ticker === (assets[i] as any).ticker)) {
            (allocations.find(({ ticker }) => ticker == (assets[i] as any).ticker) as IAllocation).shares = (allocations.find(({ ticker }) => ticker == (assets[i] as any).ticker) as IAllocation).shares + parseInt((assets[i] as any).shares)
        }
        else {
            allocations.push({ ticker: (assets[i] as any).ticker, shares: parseInt((assets[i] as any).shares) })
        }
    };

    return (
        <Table>
            <thead>
                <tr>
                    <th scope="col">Ticker</th>
                    <th scope="col">Quantity</th>
                    <th scope="col">Current Price</th>
                </tr>
            </thead>
            <tbody>
                {allocations.map(allocation => (
                    <AllocationRow allocation={allocation} />
                ))}
            </tbody>
        </Table>
    );
}