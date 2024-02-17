import { useState, useEffect } from 'react';
import Table from 'react-bootstrap/Table';
import Alert from 'react-bootstrap/Alert';
import AssetRow from './AssetRow'
import { IAsset } from '../interfaces';
import { getAssets } from './AxiosFunctions';

export default function AssetTable({ change, setChange }) {
  const [assets, setAssets] = useState<IAsset[]>([]);
  const [error, setError] = useState("noError");

  useEffect(() => {
    getAssets()
      .then((response) => {
        const APIOptions: IAsset[] = response.data
        setAssets(APIOptions);
        setChange(false)
      }).catch(() => {
        alert("Error")
      })
  }, [change]);

  if (!assets) return null;

  if (assets.length == 0) {
    return (<h3 role="noAssets">You don't own any assets</h3>)
  }

  return (
    <>
      {error === "quote" && <Alert>Error Loading Some Stock Prices</Alert>}
      <Table>
        <thead>
          <tr>
            <th scope="col" role="tickerHeader">Ticker</th>
            <th scope="col">Shares</th>
            <th scope="col">Cost Basis</th>
            <th scope="col">Total Cost Basis</th>
            <th scope="col">Total Market Price</th>
            <th scope="col">Percent Change</th>
            <th scope="col">Buy Date</th>
            <th scope="col">Delete</th>
          </tr>
        </thead>
        <tbody>
          {assets.map((asset, index) => (
            <AssetRow asset={asset} setChange={setChange} setError={setError} index={index} />
          ))}
        </tbody>
      </Table>
    </>

  );
}