import React from 'react';
import axios from 'axios';
import Table from 'react-bootstrap/Table';
import AllocationRow from './AllocationRow';
// import Django URL Context
import { useContext } from 'react';
import { strDjangoURLContext } from '../App';


function AllocationTable() {
    const strDjangoURL = useContext(strDjangoURLContext);

    const [assets, setAssets] = React.useState([]);

    // API call for user's owned assets
    React.useEffect(() => {
        axios.get(strDjangoURL.concat("assets/"), {
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

    // Create dictonary containing tickers and share amounts
    var dictAllocation = {};
    for (let i = 0; i < assets.length; i++) {
        if ((assets[i] as any).ticker_string in dictAllocation) {
            dictAllocation[(assets[i] as any).ticker_string] = dictAllocation[(assets[i] as any).ticker_string] + parseInt((assets[i] as any).shares_number)
        }
        else {
            dictAllocation[(assets[i] as any).ticker_string] = parseInt((assets[i] as any).shares_number)
        }
    };

    interface IAllocation {
        strTicker: string; numShares: number;
    }

    const keys = Object.keys(dictAllocation);
    const values = Object.values(dictAllocation);
    const listAllocations: IAllocation[] = []
    for (let i = 0; i < keys.length; i++) {
        const allocation: IAllocation = { strTicker: keys[i], numShares: values[i] as number };
        listAllocations.push(
            allocation
        )
    }

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
                {listAllocations.map(allocation => (
                    <AllocationRow allocation={allocation} />
                ))}
            </tbody>
        </Table>
    );
}

export default AllocationTable;
