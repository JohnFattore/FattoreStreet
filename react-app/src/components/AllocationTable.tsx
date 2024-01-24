import React from 'react';
import axios from 'axios';
import Table from 'react-bootstrap/Table';
import AllocationRow from './AllocationRow';
// import Django URL Context
import { useContext } from 'react';
import { ENVContext } from './ENVContext';

function AllocationTable() {
    const ENV = useContext(ENVContext);

    const [assets, setAssets] = React.useState([]);

    // API call for user's owned assets
    React.useEffect(() => {
        axios.get(ENV.djangoURL.concat("assets/"), {
            headers: {
                'Authorization': ' Bearer '.concat(sessionStorage.getItem("token") as string)
            },
        })
            .then((response) => {
                // get response data
                setAssets(response.data);
            })
    }, []);

    if (!assets) return null;

    if (assets.length == 0) {
        return (<h3>You don't own any assets</h3>)
      }

    interface IAllocation {
        ticker: string; shares: number;
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

export default AllocationTable;
